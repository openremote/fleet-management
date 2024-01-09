package org.openremote.manager.custom;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.netty.buffer.ByteBuf;
import io.netty.handler.codec.mqtt.MqttQoS;
import org.apache.activemq.artemis.spi.core.protocol.RemotingConnection;
import org.keycloak.KeycloakSecurityContext;
import org.openremote.container.timer.TimerService;
import org.openremote.container.util.UniqueIdentifierGenerator;
import org.openremote.manager.asset.AssetProcessingService;
import org.openremote.manager.asset.AssetStorageService;
import org.openremote.manager.datapoint.AssetDatapointService;
import org.openremote.manager.mqtt.MQTTHandler;
import org.openremote.manager.mqtt.Topic;
import org.openremote.manager.security.ManagerIdentityService;
import org.openremote.manager.security.ManagerKeycloakIdentityProvider;
import org.openremote.model.Container;
import org.openremote.model.asset.Asset;
import org.openremote.model.asset.AssetFilter;
import org.openremote.model.attribute.*;
import org.openremote.model.custom.*;
import org.openremote.model.datapoint.ValueDatapoint;
import org.openremote.model.query.AssetQuery;
import org.openremote.model.query.filter.AttributePredicate;
import org.openremote.model.query.filter.NumberPredicate;
import org.openremote.model.query.filter.ParentPredicate;
import org.openremote.model.query.filter.RealmPredicate;
import org.openremote.model.syslog.SyslogCategory;
import org.openremote.model.teltonika.*;
import org.openremote.model.value.AttributeDescriptor;
import org.openremote.model.value.ValueType;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Timestamp;
import java.text.MessageFormat;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import static org.openremote.model.syslog.SyslogCategory.API;
import static org.openremote.model.value.MetaItemType.*;

public class TeltonikaMQTTHandler extends MQTTHandler {

    //TODO: Maybe move this to Models?
    protected static class TeltonikaDevice {
        String clientId;
        String commandTopic;

        public TeltonikaDevice(Topic topic) {
            this.clientId = topic.getTokens().get(1);
            this.commandTopic = String.format("%s/%s/teltonika/%s/commands",
                topicRealm(topic),
                this.clientId,
                topic.getTokens().get(3));
        }
    }

    // Ideally, these should be in the Configuration assets, but because I cannot reboot a handler, I cannot change the topics to which the handler handles/subscribes to.

    private static final String TELTONIKA_DEVICE_RECEIVE_TOPIC = "data";
    private static final String TELTONIKA_DEVICE_SEND_TOPIC = "commands";
    private static final String TELTONIKA_DEVICE_TOKEN = "teltonika";
    private static final String TELTONIKA_DEVICE_SEND_COMMAND_ATTRIBUTE_NAME = "sendToDevice";
    private static final String TELTONIKA_DEVICE_RECEIVE_COMMAND_ATTRIBUTE_NAME = "response";

    private static final Logger LOG = SyslogCategory.getLogger(API, TeltonikaMQTTHandler.class);

    protected AssetStorageService assetStorageService;
    protected AssetProcessingService assetProcessingService;
    protected AssetDatapointService AssetDatapointService;
    protected TimerService timerService;
    protected Path DeviceParameterPath;

    protected final ConcurrentMap<String, TeltonikaDevice> connectionSubscriberInfoMap = new ConcurrentHashMap<>();



    /**
     * Indicates if this handler will handle the specified topic; independent of whether it is a publish or subscribe.
     * Should generally check the third token (index 2) onwards unless {@link #handlesTopic} has been overridden.
     *
     */
    @Override
    protected boolean topicMatches(Topic topic) {
        return TELTONIKA_DEVICE_TOKEN.equalsIgnoreCase(topicTokenIndexToString(topic, 2)) && getConfig().getEnabled();
    }

    private TeltonikaConfiguration getConfig() {
        List<TeltonikaConfigurationAsset> masterAssets = assetStorageService.findAll(
                        new AssetQuery()
                                .types(TeltonikaConfigurationAsset.class)
                                .realm(new RealmPredicate("master"))

                )
                .stream()
                .map(asset -> (TeltonikaConfigurationAsset) asset)
                .toList();

        if (masterAssets.size() != 1) {
            getLogger().severe("More than 1 Master Teltonika configurations found! Shutting down.");
        }

        List<TeltonikaModelConfigurationAsset> modelAssets = assetStorageService.findAll(
                        new AssetQuery()
                                .types(TeltonikaModelConfigurationAsset.class)
                                .realm(new RealmPredicate("master"))
                                .parents(new ParentPredicate(masterAssets.get(0).getId()))
                )
                .stream()
                .map(asset -> (TeltonikaModelConfigurationAsset) asset)
                .toList();

        return new TeltonikaConfiguration(masterAssets.get(0), modelAssets);

    }

    @Override
    public void start(Container container) throws Exception {
        super.start(container);
        getLogger().info("Starting Teltonika MQTT Handler");
        ManagerIdentityService identityService = container.getService(ManagerIdentityService.class);
        assetStorageService = container.getService(AssetStorageService.class);
        assetProcessingService = container.getService(AssetProcessingService.class);
        AssetDatapointService = container.getService(AssetDatapointService.class);
        timerService = container.getService(TimerService.class);
        DeviceParameterPath = container.isDevMode() ? Paths.get("../deployment/manager/fleet/FMC003.json") : Paths.get("/deployment/manager/fleet/FMC003.json");
        if (!identityService.isKeycloakEnabled()) {
            isKeycloak = false;
        } else {
            isKeycloak = true;
            identityProvider = (ManagerKeycloakIdentityProvider) identityService.getIdentityProvider();
        }
        getLogger().warning("Anonymous MQTT connections are allowed, only for the Teltonika Telematics devices, until auto-provisioning is fully implemented or until Teltonika Telematics devices allow user-defined username and password MQTT login.");




        List<Asset<?>> assets = assetStorageService.findAll(new AssetQuery().types(TeltonikaModelConfigurationAsset.class));

        if(assets.isEmpty()) {
            getLogger().severe("No Teltonika configuration assets found! Creating defaults...");

            initializeConfigurationAssets();

            getLogger().info("Created default configuration");
        }


        // Internal Subscription for the command attribute
        clientEventService.addInternalSubscription(
            AttributeEvent.class,
            null,
            this::handleAttributeMessage);

        //Internal Subscription for the Asset Configuration

        clientEventService.addInternalSubscription(
                AttributeEvent.class,
                null,
                this::handleAssetConfigurationChange
        );
    }

    private void handleAssetConfigurationChange(AttributeEvent attributeEvent) {
//        throw new NotImplementedException();
        AssetFilter<AttributeEvent> eventFilter = buildConfigurationAssetFilter();

        if(eventFilter.apply(attributeEvent) == null) return;

        Asset<?> asset = assetStorageService.find(attributeEvent.getAttributeRef().getId());
//        if (asset.getType() == )

        if(Objects.equals(attributeEvent.getAttributeName(), TeltonikaModelConfigurationAsset.PARAMETER_MAP.getName())) return;

        if (Objects.equals(attributeEvent.getAttributeName(), TeltonikaModelConfigurationAsset.PARAMETER_DATA.getName())){
            TeltonikaParameter[] newParamList = (TeltonikaParameter[]) attributeEvent.getValue().orElseThrow();
            if(newParamList.length == 0) return;
            getLogger().info("Model map configuration event: " + Arrays.toString(newParamList));
            TeltonikaModelConfigurationAsset modelAsset = (TeltonikaModelConfigurationAsset) asset;
            modelAsset = modelAsset.setParameterData(newParamList);
            AttributeEvent modificationEvent = new AttributeEvent(
                    asset.getId(),
                    TeltonikaModelConfigurationAsset.PARAMETER_MAP,
                    modelAsset.getParameterMap()
            );
//            LOG.info("Publishing to client inbound queue: " + attribute.getName());
            assetProcessingService.sendAttributeEvent(modificationEvent);

        }
    }

    /**
     * Creates a filter for the AttributeEvents for all attributes of both Teltonika Configuration assets and Teltonika
     * Model configuration assets.
     *
     * @return Attribute filter for all {@code TeltonikaConfigurationAsset} and {@code TeltonikaModelConfigurationAsset}.
     */
    private AssetFilter<AttributeEvent> buildConfigurationAssetFilter(){

        TeltonikaConfiguration config = getConfig();
        config.getChildModelIDs();


        List<TeltonikaModelConfigurationAsset> modelAssets = config.getModelAssets();

        TeltonikaConfigurationAsset masterAsset = config.getMasterAsset();


        List<Asset<?>> allIds = new ArrayList<>(modelAssets);
        allIds.add(masterAsset);

        AssetFilter<AttributeEvent> event = new AssetFilter<>();
        event.setAssetIds((allIds.stream().map(Asset::getId).toArray(String[]::new)));
        return event;
    }

    /**
     * Creates a filter for the AttributeEvents that could send a command to a Teltonika Device.
     *
     * @return AssetFilter of CarAssets that have both {@value TELTONIKA_DEVICE_RECEIVE_COMMAND_ATTRIBUTE_NAME} and
     * {@value TELTONIKA_DEVICE_SEND_COMMAND_ATTRIBUTE_NAME} as attributes.
     */
    private AssetFilter<AttributeEvent> buildCommandAssetFilter(){
        List<Asset<?>> assetsWithAttribute = assetStorageService
            .findAll(new AssetQuery().types(CarAsset.class)
                .attributeNames(TELTONIKA_DEVICE_SEND_COMMAND_ATTRIBUTE_NAME));
        List<String> listOfCarAssetIds = assetsWithAttribute.stream()
            .map(Asset::getId)
            .toList();

        AssetFilter<AttributeEvent> event = new AssetFilter<>();
        event.setAssetIds(listOfCarAssetIds.toArray(new String[0]));
        event.setAttributeNames(TELTONIKA_DEVICE_SEND_COMMAND_ATTRIBUTE_NAME);
        return event;
    }

    private void handleAttributeMessage(AttributeEvent event) {

        AssetFilter<AttributeEvent> eventFilter = buildCommandAssetFilter();

        if(eventFilter.apply(event) == null) return;

        // If this is not an AttributeEvent that updates a TELTONIKA_DEVICE_SEND_COMMAND_ATTRIBUTE_NAME field, ignore
        if (!Objects.equals(event.getAttributeName(), TELTONIKA_DEVICE_SEND_COMMAND_ATTRIBUTE_NAME)) return;
        //Find the asset in question
        CarAsset asset = assetStorageService.find(event.getAssetId(), CarAsset.class);

        // Double check, remove later, sanity checks
        if(asset.hasAttribute(TELTONIKA_DEVICE_SEND_COMMAND_ATTRIBUTE_NAME)){
            if(Objects.equals(event.getAssetId(), asset.getId())){

                //Get the IMEI of the device
                Optional<Attribute<String>> imei;
                String imeiString;
                try {
                    imei = asset.getAttribute(CarAsset.IMEI);
                    if(imei.isEmpty()) throw new Exception();
                    if(imei.get().getValue().isEmpty()) throw new Exception();
                    imeiString = imei.get().getValue().get();

                }catch (Exception e){
                    getLogger().warning("This Asset does not contain an IMEI! Can't send message!");
                    return;
                }

                // Get the device subscription information, and even if it's subscribed
                TeltonikaMQTTHandler.TeltonikaDevice deviceInfo = connectionSubscriberInfoMap.get(imeiString);
                //If it's null, the device is not subscribed, leave
                if(deviceInfo == null) {
                    getLogger().info(String.format("Device %s is not subscribed to topic, not posting message",
                        imeiString));
                    // If it is subscribed, check that the attribute's value is not empty, and send the command
                } else{
                    if(event.getValue().isPresent()){
                        sendCommandToTeltonikaDevice((String)event.getValue().get(), deviceInfo);
                        getLogger().fine("MQTT Message fired");
                    }
                    else{
                        getLogger().warning("Attribute "+TELTONIKA_DEVICE_SEND_COMMAND_ATTRIBUTE_NAME+" was empty");
                    }
                }
            }
        }
    }

    /**
     * Sends a Command to the {@link TeltonikaMQTTHandler.TeltonikaDevice} in the correct format.
     *
     * @param command string of the command, without preformatting.
     * List of valid commands can be found in Teltonika's website.
     * @param device A {@link TeltonikaMQTTHandler.TeltonikaDevice} that is currently subscribed, to which to send the message to.
     */
    private void sendCommandToTeltonikaDevice(String command, TeltonikaMQTTHandler.TeltonikaDevice device) {
        mqttBrokerService.publishMessage(device.commandTopic, Map.of("CMD", command), MqttQoS.EXACTLY_ONCE);
    }

    @Override
    protected Logger getLogger() {
        return LOG;
    }
    @Override
    public boolean checkCanSubscribe(RemotingConnection connection, KeycloakSecurityContext securityContext, Topic topic) {
        // Skip standard checks
        if (!canSubscribe(connection, securityContext, topic)) {
            getLogger().warning("Cannot subscribe to this topic, topic=" + topic + ", connection" + connection);
            return false;
        }
        return true;
    }

    /**
     * Checks if the Subscribing client should be allowed to subscribe to the topic that is handled by this Handler.
     * For Teltonika device endpoints, we need the fourth token (Index 3) to be a valid IMEI number.
     * We do that by checking using IMEIValidator. If IMEI checking is false, then skip the check.
     */
    // To be removed when auto-provisioning works
    @Override
    public boolean canSubscribe(RemotingConnection connection, KeycloakSecurityContext securityContext, Topic topic) {
        if(topic.getTokens().size() < 5){
            getLogger().warning(MessageFormat.format("Topic {0} is not a valid Topic. Please use a valid Topic.", topic.getString()));
            return false;
        }
        long imeiValue;
        try{
            imeiValue = Long.parseLong(topic.getTokens().get(3));
        }catch (NumberFormatException e){
            getLogger().warning(MessageFormat.format("IMEI {0} is not a valid IMEI value. Please use a valid IMEI value.", topic.getTokens().get(3)));
            return false;
        }
        //TODO: fix the boolean expression that is commented out below

//        if(!topic.getTokens().get(2).equalsIgnoreCase(TELTONIKA_DEVICE_TOKEN)) return false;
//        if(!IMEIValidator.isValidIMEI(imeiValue)) return false;
//        if(Objects.equals(topic.getTokens().get(4), TELTONIKA_DEVICE_RECEIVE_TOPIC)) return true;
//        if(Objects.equals(topic.getTokens().get(4), TELTONIKA_DEVICE_SEND_TOPIC)) return true;

//        return true;


        return Objects.equals(topic.getTokens().get(2), TELTONIKA_DEVICE_TOKEN) &&
            (getConfig().getCheckForImei() ? IMEIValidator.isValidIMEI(imeiValue) : true) &&
            (
                Objects.equals(topic.getTokens().get(4), TELTONIKA_DEVICE_RECEIVE_TOPIC) ||
                    Objects.equals(topic.getTokens().get(4), TELTONIKA_DEVICE_SEND_TOPIC)
            );
    }

    /**
     * Overrides MQTTHandler.checkCanPublish for this specific Handler,
     * until secure Authentication and Auto-provisioning
     * of Teltonika Devices is created.
     * To be removed after implementation is complete.
     */
    @Override
    public boolean checkCanPublish(RemotingConnection connection, KeycloakSecurityContext securityContext, Topic topic) {
        return canPublish(connection,securityContext, topic);
    }

    @Override
    public boolean canPublish(RemotingConnection connection, KeycloakSecurityContext securityContext, Topic topic) {
        getLogger().finer("Teltonika device will publish to Topic "+topic.toString()+" to transmit payload");
        return true;
    }

    public void onSubscribe(RemotingConnection connection, Topic topic) {
        getLogger().info("CONNECT: Device "+topic.getTokens().get(1)+" connected to topic "+topic+".");
        connectionSubscriberInfoMap.put(topic.getTokens().get(3), new  TeltonikaMQTTHandler.TeltonikaDevice(topic));
    }

    @Override
    public void onUnsubscribe(RemotingConnection connection, Topic topic) {
        getLogger().info("DISCONNECT: Device "+topic.getTokens().get(1)+" disconnected from topic "+topic+".");
        connectionSubscriberInfoMap.remove(topic.getTokens().get(3));
    }

    /**
     * Get the set of topics this handler wants to subscribe to for incoming publish messages; messages that match
     * these topics will be passed to {@link #onPublish}.
     * The listener topics are defined as <code>{realmID}/{userID}/{@value TELTONIKA_DEVICE_TOKEN}/{IMEI}/{@value TELTONIKA_DEVICE_RECEIVE_TOPIC}</code>
     */
    @Override
    public Set<String> getPublishListenerTopics() {
        return Set.of(
            TOKEN_SINGLE_LEVEL_WILDCARD + "/" + TOKEN_SINGLE_LEVEL_WILDCARD + "/" +
                TELTONIKA_DEVICE_TOKEN + "/" + TOKEN_SINGLE_LEVEL_WILDCARD + "/" + TELTONIKA_DEVICE_RECEIVE_TOPIC,
            TOKEN_SINGLE_LEVEL_WILDCARD + "/" + TOKEN_SINGLE_LEVEL_WILDCARD + "/" +
                TELTONIKA_DEVICE_TOKEN + "/" + TOKEN_SINGLE_LEVEL_WILDCARD + "/" + TELTONIKA_DEVICE_SEND_TOPIC
        );
    }

    @Override
    public void onPublish(RemotingConnection connection, Topic topic, ByteBuf body) {
        //TODO: How bad/easy would it be to emit events for when a new payload comes in with the attributes parsed?
        String payloadContent = body.toString(StandardCharsets.UTF_8);
        String deviceImei = topic.getTokens().get(3);
        String realm = topic.getTokens().get(0);

        String deviceUuid = UniqueIdentifierGenerator.generateId(deviceImei);

        Asset<?> asset = assetStorageService.find(deviceUuid);
        try {
            AttributeMap attributes;
            try{
                attributes = getAttributesFromPayload(payloadContent);


            }catch (JsonProcessingException e) {
                getLogger().severe("Failed to getAttributesFromPayload");
                getLogger().severe(e.toString());
                throw e;
            }
//            Attribute<String> payloadAttribute = new Attribute<>("payload", ValueType.TEXT, payloadContent);
//            payloadAttribute.addMeta(new MetaItem<>(MetaItemType.STORE_DATA_POINTS, true));
//            attributes.add(payloadAttribute);




            if (asset == null) {
                try{
                    createNewAsset(deviceUuid, deviceImei, realm, attributes, topic, connection);
                } catch (Exception e){
                    getLogger().severe("Failed to CreateNewAsset(deviceUuid, deviceImei, realm, attributes);");
                    getLogger().severe(e.toString());
                    throw e;
                }
            }
            else {
                //Check state of Teltonika AVL ID 250 for FMC003, "Trip".
//            Optional<Attribute<?>> sessionAttr = assetChangedTripState(new AttributeRef(asset.getId(), "250"));
                // We want the state where the attribute 250 (Trip) is set to true.
                // TODO: Figure out a way to create this through the UI.
                AttributePredicate pred = new AttributePredicate("250", new NumberPredicate((double) 1, AssetQuery.Operator.EQUALS));

                try{
                    if (asset.getAttributes().get("250").isEmpty()) {
                        getLogger().warning("The new value is empty!");
                        throw new Exception();
                    } else if (attributes.get("250").isEmpty()) {
                        getLogger().warning("The old value is empty!");
                        throw new Exception();
                    }
                    Attribute<?> prevValue = asset.getAttributes().get("250").get();
                    Attribute<?> newValue = attributes.get("250").get();
                    AttributeRef ref = new AttributeRef(asset.getId(), "250");
                    Optional<Attribute<?>> sessionAttr = assetChangedTripState(prevValue, newValue, pred, ref);

                    if (sessionAttr.isPresent()) {
                        getLogger().warning("New AssetStateDuration");
                        Attribute<?> session = sessionAttr.get();
                        session.addOrReplaceMeta(
                            new MetaItem<>(STORE_DATA_POINTS, true),
                            new MetaItem<>(RULE_STATE, true),
                            new MetaItem<>(READ_ONLY, true)
                        );
                        // Maybe set this to session.endTime?
                        attributes.get(CarAsset.LAST_CONTACT).ifPresent(date -> {
                            session.setTimestamp(date.getValue().get().getTime());
                        });
                        attributes.add(session);

                    }
                }catch (Exception e){
                    getLogger().severe("Could not parse Asset State Duration data");
                    getLogger().severe(e.toString());
                }
                try{
                    updateAsset(asset, attributes, topic, connection);
                }catch (Exception e){
                    getLogger().severe("Failed to UpdateAsset(asset, attributes, topic, connection)");
                    getLogger().severe(e.toString());
                    throw e;
                }
            }

        } catch (Exception e){
            getLogger().warning("Could not parse Teltonika device Payload.");
            getLogger().warning(e.toString());
//            getLogger().warning(e.fi);
        }
        // Check if asset was found
    }

    /**
     * Creates a new asset with the correct "hashed" Asset ID, its IMEI,
     * in the realm the MQTT message of the device submitted,
     * and the parsed list of attributes.
     * //TODO: Add {@value TELTONIKA_DEVICE_SEND_COMMAND_ATTRIBUTE_NAME }, {@value TELTONIKA_DEVICE_RECEIVE_COMMAND_ATTRIBUTE_NAME} Attributes when creating Asset
     *
     * @param newDeviceId The ID of the device's Asset.
     * @param newDeviceImei The IMEI of the device. If passed to
     *                      {@link UniqueIdentifierGenerator#generateId(String)},
     *                      it should always return {@code newDeviceId}.
     * @param realm The realm to create the Asset in.
     * @param attributes The attributes to insert in the Asset.
     */
    private void createNewAsset(String newDeviceId, String newDeviceImei, String realm, AttributeMap attributes, Topic topic, RemotingConnection connection) {

        CarAsset testAsset = new CarAsset("Teltonika Asset "+newDeviceImei)
            .setRealm(realm)
                .setModelNumber(getConfig().getDefaultModelNumber())
            .setId(newDeviceId);
        testAsset.getAttribute(CarAsset.LOCATION).ifPresentOrElse(attr -> attr.addMeta(new MetaItem<>(STORE_DATA_POINTS, true)), () -> {getLogger().warning("Couldn't find CarAsset.LOCATION");});

        testAsset.getAttributes().add(new Attribute<>(CarAsset.IMEI, newDeviceImei));


        //Now that the asset is created and IMEI is set, pull the packet timestamp, and then
        //set each of the asset's attributes to have that timestamp.

        attributes.get(CarAsset.LAST_CONTACT).ifPresent(dateAttr -> {
            dateAttr.getValue().ifPresent(dateVal -> {
                testAsset.setCreatedOn(dateVal);
                testAsset.getAttributes().forEach(attribute -> {
                    attribute.setTimestamp(dateVal.getTime());
                });
                attributes.forEach(attribute -> {
                    attribute.setTimestamp(dateVal.getTime());
                });
            });
        });



        updateAsset(testAsset, attributes, topic, connection);
    }

    public class TeltonikaParameterId {
        public String customId;
        public int id;
        TeltonikaParameterId(String customId){
            this.customId = customId;
        }

        TeltonikaParameterId(int id){
            this.id = id;
        }
    }

    /**
     * Returns list of attributes depending on the Teltonika JSON Payload.
     * Uses the logic and results from parsing the Teltonika Parameter IDs.
     *
     * @param payloadContent Payload coming from Teltonika device
     * @return Map of {@link Attribute}s to be assigned to the {@link Asset}.
     */
    private AttributeMap getAttributesFromPayload(String payloadContent) throws JsonProcessingException {


        HashMap<String, TeltonikaParameter> params = new HashMap<>();
        ObjectMapper mapper = new ObjectMapper();
        try {
            // Parse file with Parameter details

            // Add each element to the HashMap, with the key being the unique parameter ID and the parameter
            // being the value
            TeltonikaConfiguration config = getConfig();

            //Cast keys to String
            params = config.getParameterMap().get(config.getDefaultModelNumber()).entrySet().stream().collect(Collectors.toMap(
                    kvp -> kvp.getKey().toString(),
                    Map.Entry::getValue,
                    (existing, replacement) -> existing,
                    HashMap::new
            ));

            getLogger().info("Parsed "+params.size()+" Teltonika Parameters");

        } catch (Exception e) {
            getLogger().warning("Could not parse the Teltonika Parameter file");
            getLogger().info(e.toString());
        }

        // Add the custom parameters (pr, alt, ang, sat, sp, evt)
        Map<String, TeltonikaParameter> customParams = Map.of(
            "pr", new TeltonikaParameter(-1, "Priority", String.valueOf(1), "Unsigned", String.valueOf(0), String.valueOf(4), String.valueOf(1), "-", "0: Low - 1: High - 2: Panic", "all", "Permanent I/O Elements"),
            "alt", new TeltonikaParameter(-1, "Altitude", "2", "Signed", "-1000", "+3000", "1", "m", "meters above sea level", "all", "Permanent I/O Elements"),
            "ang", new TeltonikaParameter(-1, "Angle", "2", "Signed", "-360", "+460", "1", "deg", "degrees from north pole", "all", "Permanent I/O Elements"),
            "sat", new TeltonikaParameter(-1, "Satellites", "1", "Unsigned", "0", "1000", "1", "-", "number of visible satellites", "all", "Permanent I/O Elements"),
            "sp", new TeltonikaParameter(-1, "Speed", "2", "Signed", "0", "1000", "1", "km/h", "speed calculated from satellites", "all", "Permanent I/O Elements"),
            "evt", new TeltonikaParameter(-1, "Event Triggered", "2", "Signed", "1", "10000", "1", "-", "Parameter ID which generated this payload", "all", "Permanent I/O Elements")

        );
        params.putAll(customParams);

        //Parameters parsed, time to understand the payload
        TeltonikaDataPayload payload;
        try {
            payload = mapper.readValue(payloadContent, TeltonikaDataPayload.class);


            AttributeMap attributeMap;
            try{
                attributeMap = payload.state.getAttributes(params, new AttributeMap(), getLogger());
            }catch (Exception e){
                getLogger().severe("Failed to payload.state.GetAttributes");
                getLogger().severe(e.toString());
                throw e;
            }
            return attributeMap;
        } catch (Exception e) {
            //If the payload wasn't parsed, then it means that it is either a response to a command,
            //or it's genuinely a wrong payload.

            mapper = new ObjectMapper();
            TeltonikaResponsePayload response = mapper.readValue(payloadContent, TeltonikaResponsePayload.class);
            AttributeMap map = new AttributeMap();
            // TODO: Do we want to remove the value from TELTONIKA_DEVICE_SEND_COMMAND_ATTRIBUTE_NAME?
            map.addAll(new Attribute<>
                (
                    new AttributeDescriptor<>(TELTONIKA_DEVICE_RECEIVE_COMMAND_ATTRIBUTE_NAME, ValueType.TEXT),
                    response.rsp
                )
            );
            return map;
        }

    }


    /**
     * Returns an {@code Optional<Attribute<AssetStateDuration>>}, that {@code ifPresent()}, represents
     * the Duration for which the predicate returned true.
     *
     * @param previousValue The old attribute state (Or the latest datapoint that exists)
     * @param newValue The new attribute value
     * @param pred A Predicate that describes the state change
     * @param ref An AttributeRef that describes which asset and attribute this pertains to.
     * @return An Optional Attribute of type AssetStateDuration that represents the Duration for which the predicate returned true.
     */
    //TODO: Change this to only use an AttributeRef and a Predicate.
    private Optional<Attribute<?>> assetChangedTripState(Attribute<?> previousValue, Attribute<?> newValue, AttributePredicate pred, AttributeRef ref) {
        //We will first check if the predicate fails for the new value, and then check if the predicate is true for the previous value.
        //In that way, we know that the state change happened between the new and previous values.

        if (newValue.getValue().isEmpty()) {
            getLogger().warning("The new value is empty!");
            return Optional.empty();
        } else if (previousValue.getValue().isEmpty()) {
            getLogger().warning("The old value is empty!");
            return Optional.empty();
        }

        //TODO: Understand what happens with negate(), does it change states to the object itself?
        boolean newValueTest =      pred.value.asPredicate(timerService::getCurrentTimeMillis).test(newValue        .getValue().get());
        boolean previousValueTest = pred.value.asPredicate(timerService::getCurrentTimeMillis).test(previousValue   .getValue().get());
        //If the predicate fails, then no changes need to happen.

        // newValue is not 1, previousValue == 1
        if(!(!newValueTest && previousValueTest)) {
            return Optional.empty();
        }

        // Grab all datapoints (To be replaced by AssetDatapointValueQuery)
        // For optimization: Maybe pull the datapoints from the endTime of the previous AssetStateDuration.

        ArrayList<ValueDatapoint> list = new ArrayList<>(AssetDatapointService.getDatapoints(ref));

        // If there are no historical data found, add some first
        if(list.isEmpty()) return Optional.empty();

        //What we do now is, we will try to figure out the latest datapoint where the predicate fails, before the newValue.
        //This means that, the state change took place between the datapoint we just found and its next one.

        //Find the first datapoint that passes the negated predicate

        ValueDatapoint<?> StateChangeAssetDatapoint = null;

        try {
            for (int i = 0; i < list.size()-1; i++) {
                // Not using Object.equals, but Datapoint.equals

                ValueDatapoint<?> currentDp = list.get(i);
                ValueDatapoint<?> theVeryPreviousDp = list.get(i+1);

                //            So, if the currentDp passes the predicate,
                boolean currentDpTest = pred.value.asPredicate(timerService::getCurrentTimeMillis).test(currentDp.getValue());
                //            and if the very previous one (NEXT one in the array and PREVIOUS in the time dimension)
                //            FAILS the predicate,
                boolean previousDpTest = pred.value.asPredicate(timerService::getCurrentTimeMillis).test(theVeryPreviousDp.getValue());
                //            A state change happened where the state we are looking for was turned on.
                //            We want the currentDp.


                if(currentDpTest && !previousDpTest){
                    StateChangeAssetDatapoint = currentDp;
                    break;
                }
            }

            if (StateChangeAssetDatapoint != null){
                if (!pred.value.asPredicate(timerService::getCurrentTimeMillis).test(StateChangeAssetDatapoint.getValue())){
                    throw new Exception("Found state change datapoint failed predicate");
                }
            }else{
                throw new Exception("Couldn't find asset state change value");
            }

        }catch (Exception e){
            getLogger().warning(e.getMessage());
            return Optional.empty();
        }

        //TODO: Issue #1156 (https://github.com/openremote/openremote/issues/1156)
        //BUG: The Timestamp of an asset datapoint is NOT attribute.getTimestamp()!
        //This makes it much harder to relate datapoints to relate a duration to the time-distance between two datapoints.
        //To make this easier, we are going to use some time-padding of 1 second from the gap. It's going to be sufficient to cover some edge-cases.

        //The AssetStateDuration will have a startTime of the found state-change variable, and the endDate will be the previousValue's timestamp, that being the end of the state duration

        if(previousValue.getTimestamp().isEmpty()){
            getLogger().warning("previousValue's timestamp is empty!");
            return Optional.empty();
        }

        //TODO: Convert this into a static field to be used everywhere
        Attribute<?> tripAttr = new Attribute<>("LastTripStartedAndEndedAt", CustomValueTypes.ASSET_STATE_DURATION, new AssetStateDuration(
            new Timestamp(StateChangeAssetDatapoint.getTimestamp()),
            new Timestamp(previousValue.getTimestamp().get())
        ));

        tripAttr.addMeta(
                new MetaItem<>(STORE_DATA_POINTS, true),
                new MetaItem<>(RULE_STATE, true),
                new MetaItem<>(READ_ONLY, true)
        );


        return Optional.of(tripAttr);
    }

    private String getParameterFileString() {
        try {
            return Files.readString(DeviceParameterPath);
        } catch (IOException e) {
            getLogger().warning("Couldn't find FMC003.json, couldn't parse parameters");
            throw new RuntimeException(e);
        }
    }

    /**
     * Updates the {@link Asset} passed, with the {@link AttributeMap} passed.
     * TODO: I have a very big feeling that the way this is done is wrong.
     *
     * @param asset The asset to be updated.
     * @param attributes The attributes to be upserted to the Attribute.
     * @param topic The topic to which the MQTT payload was sent.
     * @param connection The connection on which the payload was sent.
     */
    private void updateAsset(Asset<?> asset, AttributeMap attributes, Topic topic, RemotingConnection connection) {

        // Make sure that there is a lastContact attribute, even if it's a response to a command from the device.
//        Optional<Attribute<Date>> timestampAttrOptional = Optional.empty();
//
//        if(attributes.containsKey(TELTONIKA_DEVICE_RECEIVE_COMMAND_ATTRIBUTE_NAME) && attributes.size() == 1){
//            Date timestamp = new Date(TimerService.Clock.REAL.getCurrentTimeMillis());
//            attributes.add(new Attribute<Date>("lastContact", ValueType.DATE_AND_TIME, timestamp, timestamp.getTime()));
//        }
//
//
//        timestampAttrOptional = attributes.get(new AttributeDescriptor<Date>("lastContact", ValueType.DATE_AND_TIME));
//
//        if (timestampAttrOptional.isEmpty()) {
//            getLogger().warning("Could not figure out current packet timestamp");
//            throw new NullPointerException("Could not find current packet timestamp");
//        }
//
//        Attribute<Date> packetTime = timestampAttrOptional.get();
//
//        if (packetTime.getValue().isEmpty()) {
//            getLogger().warning("Current packet timestamp is empty");
//            throw new NullPointerException("Current packet timestamp is empty");
//        }
//
//        long packetTimestamp = packetTime.getValue().get().getTime();
//
//        attributes.forEach(attribute -> attribute.setTimestamp(packetTimestamp));

        String imei = asset.getAttribute(CarAsset.IMEI).toString();

        getLogger().info("Updating CarAsset with IMEI "+imei);
        //OBD details: Prot:6,VIN:WVGZZZ1TZBW068095,TM:15,CNT:19,ST:DATA REQUESTING,P1:0xBE3EA813,P2:0xA005B011,P3:0xFED00400,P4:0x0,MIL:0,DTC:0,ID3,Hdr:7E8,Phy:0
        //Find which attributes need to be updated and which attributes need to be just reminded of updating.

        AttributeMap nonExistingAttributes = new AttributeMap();
        AttributeMap existingAttributes = new AttributeMap();

        attributes.forEach( attribute ->  {
            if (asset.getAttribute(attribute.getName()).isPresent()) {
                existingAttributes.add(attribute);
            } else {
                nonExistingAttributes.add(attribute);
            }
        });

        //First merge, then update existing attributes
        asset.addAttributes(nonExistingAttributes.stream().toArray(Attribute[]::new));
        if(!nonExistingAttributes.isEmpty()){
            assetStorageService.merge(asset);
        }

        existingAttributes.forEach(attribute -> attribute.getTimestamp().ifPresent(timestamp -> {
            AttributeEvent attributeEvent = new AttributeEvent(asset.getId(), attribute.getName(), attribute.getValue().get(), timestamp);
//            LOG.info("Publishing to client inbound queue: " + attribute.getName());
            assetProcessingService.sendAttributeEvent(attributeEvent);
        }));
    }

    private void initializeConfigurationAssets(){
        // Create initial configuration
        TeltonikaConfigurationAsset rootConfig = new TeltonikaConfigurationAsset("Teltonika Device Configuration");
        TeltonikaModelConfigurationAsset fmc003 = new TeltonikaModelConfigurationAsset("FMC003");

        rootConfig.setEnabled(true);
        rootConfig.setCheckForImei(false);
        rootConfig.setDefaultModelNumber("FMC003");

        fmc003.setModelNumber("FMC003");
        ObjectMapper mapper = new ObjectMapper();
        try {
            TeltonikaParameter[] params = mapper.readValue(getParameterFileString(), TeltonikaParameter[].class);
            fmc003.setParameterData(params);

        } catch (JsonProcessingException e) {
            throw new RuntimeException("Could not parse Teltonika Parameter JSON file");
        }

        rootConfig.setRealm("master");
        fmc003.setRealm("master");

         rootConfig = assetStorageService.merge(rootConfig);

         fmc003.setParent(rootConfig);

         fmc003 = assetStorageService.merge(fmc003);

    }
}

//Attribute exists, needs to be updated
//            asset.getAttribute(attribute.getName()).ifPresentOrElse(attr -> {
//                existingAttributes.add(attribute);
//            }, () -> {
//                nonExistingAttributes.add(attribute);
//            });

