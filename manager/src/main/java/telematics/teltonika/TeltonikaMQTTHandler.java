package telematics.teltonika;

import com.fasterxml.jackson.core.JsonProcessingException;
import io.netty.buffer.ByteBuf;
import io.netty.handler.codec.mqtt.MqttQoS;
import org.apache.activemq.artemis.spi.core.protocol.RemotingConnection;
import org.keycloak.KeycloakSecurityContext;
import org.openremote.container.timer.TimerService;
import org.openremote.model.util.UniqueIdentifierGenerator;
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
import org.openremote.model.asset.AssetTypeInfo;
import org.openremote.model.attribute.*;
import org.openremote.model.custom.*;
import org.openremote.model.datapoint.ValueDatapoint;
import org.openremote.model.query.AssetQuery;
import org.openremote.model.query.filter.*;
import org.openremote.model.syslog.SyslogCategory;
import org.openremote.model.teltonika.*;
import org.openremote.model.util.ValueUtil;
import org.openremote.model.value.*;
import telematics.teltonika.helpers.TeltonikaConfigurationFactory;

import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
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

import static org.openremote.model.syslog.SyslogCategory.API;
import static org.openremote.model.value.MetaItemType.*;

public class TeltonikaMQTTHandler extends MQTTHandler {

    public static final Class<? extends  Asset<VehicleAsset>> TELTONIKA_DEVICE_ASSET_CLASS = CarAsset.class;
    public static AssetTypeInfo TELTONIKA_DEVICE_ASSET_INFO = null;

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

    private static final Logger LOG = SyslogCategory.getLogger(API, TeltonikaMQTTHandler.class);

    protected AssetStorageService assetStorageService;
    protected AssetProcessingService assetProcessingService;
    protected AssetDatapointService AssetDatapointService;
    protected TimerService timerService;
    protected Path DeviceParameterPath;

    protected final ConcurrentMap<String, TeltonikaDevice> connectionSubscriberInfoMap = new ConcurrentHashMap<>();

    private TeltonikaConfiguration config;
    private TeltonikaConfiguration getConfig() {
        try{
            return TeltonikaConfiguration.getInstance();
        } catch (NullPointerException e) {
            TeltonikaConfiguration tempConfig = TeltonikaConfigurationFactory.createConfiguration(assetStorageService, timerService, getParameterFileString());
            TeltonikaConfiguration.setInstance(tempConfig);
            config = tempConfig;
        }
        return config;
    }

    /**
     * Indicates if this handler will handle the specified topic; independent of whether it is a published message
     * or a subscription.
     * Should generally check the third token (index 2) onwards unless {@link #handlesTopic} has been overridden.
     *
     */
    @Override
    protected boolean topicMatches(Topic topic) {
        return TELTONIKA_DEVICE_TOKEN.equalsIgnoreCase(topicTokenIndexToString(topic, 2)) && getConfig().getEnabled();
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
        TELTONIKA_DEVICE_ASSET_INFO = ValueUtil.getAssetInfo(TELTONIKA_DEVICE_ASSET_CLASS).orElseThrow();
        DeviceParameterPath = container.isDevMode() ? Paths.get("../deployment/manager/fleet/FMC003.json") : Paths.get("/deployment/manager/fleet/FMC003.json");
        config = TeltonikaConfigurationFactory.createConfiguration(assetStorageService, timerService, getParameterFileString());
        if (!identityService.isKeycloakEnabled()) {
            isKeycloak = false;
        } else {
            isKeycloak = true;
            identityProvider = (ManagerKeycloakIdentityProvider) identityService.getIdentityProvider();
        }
        getLogger().warning("Anonymous MQTT connections are allowed, only for the Teltonika Telematics devices, until auto-provisioning is fully implemented or until Teltonika Telematics devices allow user-defined username and password MQTT login.");

        List<Asset<?>> assets = assetStorageService.findAll(new AssetQuery().types(TeltonikaModelConfigurationAsset.class));

        if(assets.isEmpty()) {
            getLogger().severe("No Teltonika configuration assets found! Exiting...");
            throw new NullPointerException();
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

        Asset<?> asset = assetStorageService.find(attributeEvent.getRef().getId());
//        if (asset.getType() == )

        if(Objects.equals(attributeEvent.getName(), TeltonikaModelConfigurationAsset.PARAMETER_MAP.getName())) {
            // This means that the parameter map has been updated, hence the new ModelConfigurationAsset is valid.
            // Hence, the configuration is now out of date, so we need to update it.
            TeltonikaConfigurationFactory.refreshInstance(assetStorageService, timerService, getParameterFileString());
        }
        if (Objects.equals(attributeEvent.getName(), TeltonikaModelConfigurationAsset.PARAMETER_DATA.getName())){
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

        List<TeltonikaModelConfigurationAsset> modelAssets = getConfig().getModelAssets();

        TeltonikaConfigurationAsset masterAsset = getConfig().getMasterAsset();


        List<Asset<?>> allIds = new ArrayList<>(modelAssets);
        allIds.add(masterAsset);

        AssetFilter<AttributeEvent> event = new AssetFilter<>();
        event.setAssetIds((allIds.stream().map(Asset::getId).toArray(String[]::new)));
        return event;
    }

    /**
     * Creates a filter for the AttributeEvents that could send a command to a Teltonika Device.
     *
     * @return AssetFilter of VehicleAssets that have both {@code getConfig().getResponseAttribute().getName()} and
     * {@code getConfig().getCommandAttribute().getValue().orElse("sendToDevice")} as attributes.
     */
    private AssetFilter<AttributeEvent> buildCommandAssetFilter(){

        List<Asset<?>> assetsWithAttribute = assetStorageService
            .findAll(new AssetQuery().types(VehicleAsset.class)
                .attributeNames(getConfig().getCommandAttribute().getValue().orElse("sendToDevice")));
        List<String> listOfVehicleAssetIds = assetsWithAttribute.stream()
            .map(Asset::getId)
            .toList();

        AssetFilter<AttributeEvent> event = new AssetFilter<>();
        event.setAssetIds(listOfVehicleAssetIds.toArray(new String[0]));
        event.setAttributeNames(getConfig().getCommandAttribute().getValue().orElse("sendToDevice"));
        return event;
    }

    private void handleAttributeMessage(AttributeEvent event) {

        AssetFilter<AttributeEvent> eventFilter = buildCommandAssetFilter();

        if(eventFilter.apply(event) == null) return;

        // If this is not an AttributeEvent that updates a getConfig().getCommandAttribute().getValue().orElse("sendToDevice") field, ignore
        if (!Objects.equals(event.getName(), getConfig().getCommandAttribute().getValue().orElse("sendToDevice"))) return;
        //Find the asset in question
        VehicleAsset asset = assetStorageService.find(event.getId(), VehicleAsset.class);

        // Double check, remove later, sanity checks
        if(asset.hasAttribute(getConfig().getCommandAttribute().getValue().orElse("sendToDevice"))){
            if(Objects.equals(event.getId(), asset.getId())){

                //Get the IMEI of the device
                Optional<Attribute<String>> imei;
                String imeiString;
                try {
                    imei = asset.getAttribute(VehicleAsset.IMEI);
                    if(imei.isEmpty()) throw new Exception();
                    if(imei.get().getValue().isEmpty()) throw new Exception();
                    imeiString = imei.get().getValue().get();

                }catch (Exception e){
                    getLogger().warning("This Asset does not contain an IMEI! Can't send message!");
                    return;
                }

                // Get the device subscription information, and even if it's subscribed
                TeltonikaDevice deviceInfo = connectionSubscriberInfoMap.get(imeiString);
                //If it's null, the device is not subscribed, leave
                if(deviceInfo == null) {
                    getLogger().info(String.format("Device %s is not subscribed to topic, not posting message",
                        imeiString));
//                    throw new Exception("Device is not connected to server");
                    // If it is subscribed, check that the attribute's value is not empty, and send the command
                } else{
                    if(event.getValue().isPresent()){
                        sendCommandToTeltonikaDevice((String)event.getValue().get(), deviceInfo);
                        getLogger().fine("MQTT Message fired");
                    }
                    else{
                        getLogger().warning("Attribute "+getConfig().getCommandAttribute().getValue().orElse("sendToDevice")+" was empty");
                    }
                }
            }
        }
    }

    /**
     * Sends a Command to the {@link TeltonikaDevice} in the correct format.
     *
     * @param command string of the command, without preformatting.
     * List of valid commands can be found in Teltonika's website.
     * @param device A {@link TeltonikaDevice} that is currently subscribed, to which to send the message to.
     */
    private void sendCommandToTeltonikaDevice(String command, TeltonikaDevice device) {
        publishMessage(device.commandTopic, Map.of("CMD", command), MqttQoS.EXACTLY_ONCE);
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

        connectionSubscriberInfoMap.put(topic.getTokens().get(3), new TeltonikaDevice(topic));
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
//
//    private long startTimestamp = 0;
//    private long endTimestamp = 0;
    @Override
    public void onPublish(RemotingConnection connection, Topic topic, ByteBuf body) {
//        startTimestamp = System.currentTimeMillis();
	    ITeltonikaPayload payload = null;
        String deviceImei = topic.getTokens().get(3);

        String deviceUuid = UniqueIdentifierGenerator.generateId(deviceImei);

        Asset<VehicleAsset> asset = assetStorageService.find(deviceUuid, VehicleAsset.class);
        String deviceModelNumber = asset != null
                ? asset.getAttribute(VehicleAsset.MODEL_NUMBER).orElseThrow().getValue().orElse(getConfig().getDefaultModelNumber())
                : getConfig().getDefaultModelNumber();
        if (deviceModelNumber == null){
            getLogger().fine("Device Model Number is null, setting to default");
            deviceModelNumber = getConfig().getDefaultModelNumber();
        }
        try {
            payload = TeltonikaPayloadFactory.getPayload(body.toString(StandardCharsets.UTF_8), deviceModelNumber);
        } catch (JsonProcessingException e) {
            getLogger().severe(e.toString());
            return;
        }
        String realm = topic.getTokens().get(0);
        String clientId = topic.getTokens().get(1);

        try {
            AttributeMap attributes;
            try{
                Map<TeltonikaParameterData, Object> data = payload.getAttributesFromPayload(getConfig(), timerService);
                attributes = payload.getAttributes(data, getConfig(), getLogger(), TELTONIKA_DEVICE_ASSET_INFO.getAttributeDescriptors());
            }catch (JsonProcessingException e) {
                getLogger().severe("Failed to getAttributesFromPayload");
                getLogger().severe(e.toString());
                throw e;
            }

            //Create MQTTClientId Attribute
            try{
                Attribute<String> clientIdAttribute = new Attribute<>("ClientId", ValueType.TEXT, clientId);
                clientIdAttribute.setTimestamp(timerService.getCurrentTimeMillis());

                attributes.add(clientIdAttribute);
            }catch (Exception e){
                getLogger().severe("Failed to create Client ID Attribute");
            }


            //TODO: If specified in configuration, store payloads (if it WAS a data payload)
            try{
                if(getConfig().getStorePayloads().getValue().orElseThrow() && payload instanceof TeltonikaDataPayload){
                    Attribute<?> payloadAttribute = new Attribute<>("payload", CustomValueTypes.TELTONIKA_PAYLOAD, new TeltonikaDataPayloadModel(((TeltonikaDataPayload) payload).getState()));
                    payloadAttribute.addMeta(new MetaItem<>(STORE_DATA_POINTS, true));
                    payloadAttribute.setTimestamp(attributes.get(VehicleAsset.LAST_CONTACT).orElseThrow().getValue().orElseThrow().getTime());
                    attributes.add(payloadAttribute);
                }
            }catch (Exception ignored){}



            if (asset == null) {
                try{
                    createNewAsset(deviceUuid, deviceImei, realm, attributes);
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

                //Any Class that implements `ValuePredicate` can be used here.
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

                    Optional<Attribute<?>> sessionAttr = assetChangedTripState(prevValue, newValue, pred.value, ref);

                    if (sessionAttr.isPresent()) {
                        getLogger().warning("New AssetStateDuration");
                        Attribute<?> session = sessionAttr.get();
                        session.addOrReplaceMeta(
                            new MetaItem<>(STORE_DATA_POINTS, true),
                            new MetaItem<>(RULE_STATE, true),
                            new MetaItem<>(READ_ONLY, true)
                        );
                        // Maybe set this to session.endTime?
                        attributes.get(VehicleAsset.LAST_CONTACT).flatMap(Attribute::getValue)
                                .ifPresent(val -> session.setTimestamp(val.getTime()));
                        attributes.add(session);

                    }
                }catch (Exception e){
                    getLogger().severe("Could not parse Asset State Duration data");
                    getLogger().severe(e.toString());
                }
                try{
                    updateAsset(asset, attributes);
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
     * @param newDeviceId The ID of the device's Asset.
     * @param newDeviceImei The IMEI of the device. If passed to
     *                      {@link UniqueIdentifierGenerator#generateId(String)},
     *                      it should always return {@code newDeviceId}.
     * @param realm The realm to create the Asset in.
     * @param attributes The attributes to insert in the Asset.
     */
    private void createNewAsset(String newDeviceId, String newDeviceImei, String realm, AttributeMap attributes) {

        Asset<? extends VehicleAsset> newAsset = null;
        try {
            Constructor<? extends Asset<VehicleAsset>> constructor = TELTONIKA_DEVICE_ASSET_CLASS.getConstructor(String.class);
		    newAsset = constructor.newInstance("Teltonika Asset " + newDeviceImei);
	    } catch (NoSuchMethodException e) {
		    getLogger().severe("Constructor for "+ TELTONIKA_DEVICE_ASSET_CLASS.getSimpleName() +" not found with parameter String");
            return;
	    } catch (InvocationTargetException e) {
            getLogger().severe("Constructor for "+ TELTONIKA_DEVICE_ASSET_CLASS.getSimpleName() +" threw an exception");
            return;
	    } catch (InstantiationException e) {
		    getLogger().severe("The Class" + TELTONIKA_DEVICE_ASSET_CLASS.getSimpleName() + " is abstract or an interface, and cannot be instantiated.");
            return;
	    } catch (IllegalAccessException e) {
            getLogger().severe("The Constructor for " + TELTONIKA_DEVICE_ASSET_CLASS.getSimpleName() + " is not accessible (Could be private, or in general we do not have the needed access modifiers).");
            return;
	    }

	    newAsset = newAsset
            .setRealm(realm)
            .setModelNumber(getConfig().getDefaultModelNumber())
            .setId(newDeviceId);

        newAsset.getAttribute(VehicleAsset.LOCATION).ifPresentOrElse(
                attr -> attr.addMeta(new MetaItem<>(STORE_DATA_POINTS, true)),
                () -> getLogger().warning("Couldn't find "+TELTONIKA_DEVICE_ASSET_CLASS.getSimpleName()+".LOCATION")
        );

        newAsset.getAttributes().add(new Attribute<>(VehicleAsset.IMEI, newDeviceImei));

        // Create Command and Response Attributes
        Attribute<String> command = new Attribute<>(new AttributeDescriptor<>(getConfig().getCommandAttribute().getValue().orElse("sendToDevice"), ValueType.TEXT), "");
        newAsset.getAttributes().add(command);
//        Attribute<String> response = new Attribute<>(new AttributeDescriptor<>(getConfig().getResponseAttribute().getValue().orElse("sendToDevice"), ValueType.TEXT), "");
//        newAsset.getAttributes().add(response);


        //Now that the asset is created and IMEI is set, pull the packet timestamp, and then
        //set each of the asset's attributes to have that timestamp.

        Asset<? extends  Asset<VehicleAsset>> finalNewAsset = newAsset;
        attributes.get(VehicleAsset.LAST_CONTACT).flatMap(Attribute::getValue).ifPresent(dateVal -> {
            finalNewAsset.setCreatedOn(dateVal);
            finalNewAsset.getAttributes().forEach(attribute -> attribute.setTimestamp(dateVal.getTime()));
            attributes.forEach(attribute -> attribute.setTimestamp(dateVal.getTime()));
        });

        updateAsset(finalNewAsset, attributes);
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
    private Optional<Attribute<?>> assetChangedTripState(Attribute<?> previousValue, Attribute<?> newValue, ValuePredicate pred, AttributeRef ref) {
        //We will first check if the predicate fails for the new value, and then check if the predicate is true for the previous value.
        //In that way, we know that the state change happened between the new and previous values.

        if (newValue.getValue().isEmpty()) {
            getLogger().warning("The new value is empty!");
            return Optional.empty();
        } else if (previousValue.getValue().isEmpty()) {
            getLogger().warning("The old value is empty!");
            return Optional.empty();
        }

        boolean newValueTest =      pred.asPredicate(timerService::getCurrentTimeMillis).test(newValue        .getValue().get());
        boolean previousValueTest = pred.asPredicate(timerService::getCurrentTimeMillis).test(previousValue   .getValue().get());
        //If the predicate fails, then no changes need to happen.

        // newValue is not 1, previousValue == 1
        if(!(!newValueTest && previousValueTest)) {
            return Optional.empty();
        }

        // Grab all data-points (To be replaced by AssetDatapointValueQuery)
        // For optimization: Maybe pull the data-points from the endTime of the previous AssetStateDuration.

        ArrayList<ValueDatapoint> list = new ArrayList<>(AssetDatapointService.getDatapoints(ref));


        // If there are no historical data found, add some first
        if(list.isEmpty()) {
            list.add(
                    new ValueDatapoint(
                                    timerService.getCurrentTimeMillis(),
                                    new AssetStateDuration(
                                            new Timestamp(previousValue.getTimestamp().get()),
                                            new Timestamp(newValue.getTimestamp().get())
                                    )
                    )
            );
        }

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
                boolean currentDpTest = pred.asPredicate(timerService::getCurrentTimeMillis).test(currentDp.getValue());
                //            and if the very previous one (NEXT one in the array and PREVIOUS in the time dimension)
                //            FAILS the predicate,
                boolean previousDpTest = pred.asPredicate(timerService::getCurrentTimeMillis).test(theVeryPreviousDp.getValue());
                //            A state change happened where the state we are looking for was turned on.
                //            We want the currentDp.


                if(currentDpTest && !previousDpTest){
                    StateChangeAssetDatapoint = currentDp;
                    break;
                }
            }

            if (StateChangeAssetDatapoint != null){
                if (!pred.asPredicate(timerService::getCurrentTimeMillis).test(StateChangeAssetDatapoint.getValue())){
                    throw new Exception("Found state change datapoint failed predicate");
                }
            }else{
                throw new Exception("Couldn't find asset state change value");
            }

        }catch (Exception e){
            getLogger().warning(e.getMessage());
            return Optional.empty();
        }

        if(previousValue.getTimestamp().isEmpty()){
            getLogger().warning("previousValue's timestamp is empty!");
            return Optional.empty();
        }

        //Because of the way that Teltonika sends the Attribute data, it sometimes sends a 1, then a 0, then a trip for the duration of the real trip.
        //Just check if the duration is greater than 10 seconds, any trip less than that should not be recorded.

        if(previousValue.getTimestamp().get() - StateChangeAssetDatapoint.getTimestamp() < 10000) return Optional.empty();

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
     *
     * @param asset The asset to be updated.
     * @param attributes The attributes to be upserted to the Attribute.
     */
    private void updateAsset(Asset<? extends  Asset<VehicleAsset>> asset, AttributeMap attributes) {
        String imei = asset.getAttribute(VehicleAsset.IMEI)
                .orElse(new Attribute<>("IMEI", ValueType.TEXT, "Not Found"))
                .getValue()
                .orElse("Couldn't Find IMEI");

        getLogger().info("Updating "+ attributes.stream().count() +" attributes of "+TELTONIKA_DEVICE_ASSET_CLASS.getSimpleName()+" with IMEI " + imei + " at Timestamp " + attributes.get(VehicleAsset.LAST_CONTACT));

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
            AttributeEvent attributeEvent = new AttributeEvent(
                    asset.getId(),
                    attribute.getName(),
                    attribute.getValue().orElseThrow(),
                    timestamp
            );
//            LOG.info("Publishing to client inbound queue: " + attribute.getName());
            assetProcessingService.sendAttributeEvent(attributeEvent);
        }));
//        endTimestamp = System.currentTimeMillis();

//        getLogger().info("Updated "+TELTONIKA_DEVICE_ASSET_CLASS.getSimpleName()+" in " + (endTimestamp - startTimestamp) + "ms");
    }

}
