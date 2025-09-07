package org.openremote.test.custom

import com.fasterxml.jackson.databind.ObjectMapper
import groovy.json.JsonOutput
import groovy.json.JsonSlurper
import org.openremote.agent.protocol.mqtt.MQTTMessage
import org.openremote.agent.protocol.mqtt.MQTT_IOClient
import org.openremote.container.Container
import org.openremote.model.util.UniqueIdentifierGenerator
import org.openremote.manager.agent.AgentService
import org.openremote.manager.asset.AssetProcessingService
import org.openremote.manager.asset.AssetStorageService
import org.openremote.manager.event.ClientEventService
import org.openremote.manager.mqtt.MQTTBrokerService
import org.openremote.model.custom.AssetStateDuration
import org.openremote.model.custom.CustomValueTypes
import org.openremote.model.query.AssetQuery
import telematics.teltonika.TeltonikaMQTTHandler
import org.openremote.manager.setup.SetupService
import org.openremote.manager.setup.custom.CustomKeycloakSetup
import org.openremote.model.Constants
import org.openremote.model.asset.Asset
import org.openremote.model.asset.agent.ConnectionStatus
import org.openremote.model.attribute.Attribute
import org.openremote.model.attribute.AttributeEvent
import org.openremote.model.attribute.AttributeRef
import org.openremote.model.custom.VehicleAsset
import org.openremote.model.teltonika.TeltonikaParameter
import org.openremote.model.util.ValueUtil
import org.openremote.model.value.MetaItemType
import org.openremote.model.value.ValueType
import org.openremote.test.ManagerContainerTrait
import spock.lang.Shared
import spock.lang.Specification
import spock.util.concurrent.PollingConditions

class TeltonikaMQTTProtocolTest extends Specification implements ManagerContainerTrait {
    @Shared public def conditions = new PollingConditions(timeout: 10, delay: 0.1)
    public static Map<String, TeltonikaParameter> params;

    public static Container container
    public static AssetStorageService assetStorageService;
    public static AssetProcessingService assetProcessingService;
    public static AgentService agentService;
    public static MQTTBrokerService mqttBrokerService
    public static ClientEventService clientEventService
    public static String mqttHost
    public static int mqttPort
    public static String mqttClientId
    public static String username
    public static String password
    public static MQTT_IOClient client
    public static TeltonikaMQTTHandler handler

    //TODO: I do not know how/if to link this to environment variable
    String TELTONIKA_DEVICE_RECEIVE_TOPIC = "data"
    String TELTONIKA_DEVICE_SEND_TOPIC = "commands";
    String TELTONIKA_DEVICE_TOKEN = "teltonika";
    String TELTONIKA_DEVICE_SEND_COMMAND_ATTRIBUTE_NAME = "sendToDevice";
    String TELTONIKA_DEVICE_RECEIVE_COMMAND_ATTRIBUTE_NAME = "response";
    //Real IMEI: https://www.imei.info/?imei=358491098808487
    def TELTONIKA_DEVICE_IMEI = "358491098808487";

    Map<String, TeltonikaParameter> retrieveTeltonikaParams() {
        ObjectMapper mapper = new ObjectMapper();
        String jsonText = new File("../deployment/manager/fleet/FMC003.json").text
        TeltonikaParameter[] paramArray = mapper.readValue(jsonText, TeltonikaParameter[].class)
        Map<String, TeltonikaParameter> params = new HashMap<String, TeltonikaParameter>()
        for (final def param in paramArray) {
            params.put(param.getPropertyId().toString(), param)
        }
        return params
    }

    def setupSpec() {
        given: "the container is started"
        container = startContainer(defaultConfig(), defaultServices())
        assetStorageService = container.getService(AssetStorageService.class)
        assetProcessingService = container.getService(AssetProcessingService.class)
        agentService = container.getService(AgentService.class)
        mqttBrokerService = container.getService(MQTTBrokerService.class)
        clientEventService = container.getService(ClientEventService.class)
        def keycloakTestSetup = container.getService(SetupService.class).getTaskOfType(CustomKeycloakSetup.class)
        handler = mqttBrokerService.customHandlers.find {it instanceof TeltonikaMQTTHandler} as TeltonikaMQTTHandler

        mqttHost = mqttBrokerService.host
        mqttPort = mqttBrokerService.port

        mqttClientId = UniqueIdentifierGenerator.generateId()
        username = Constants.MASTER_REALM + ":" + keycloakTestSetup.serviceUser.username // realm and OAuth client id
        password = keycloakTestSetup.serviceUser.secret

        params = retrieveTeltonikaParams()


        client = new MQTT_IOClient(mqttClientId, mqttHost, mqttPort, false, false,  null, null, null)
    }

    def setup(){
//        I think that it is required to provide usernamePassword to connect the client to the broker. Look at AbstractMQTT_IOClient:L100.
        client.removeAllMessageConsumers()
        client.disconnect()

        client = new MQTT_IOClient(mqttClientId, mqttHost, mqttPort, false, false, null, null, null)
    }

    def cleanupSpec(){
        getLOG().debug("cleanupSpec")
        container.stop()
    }

    def "the device connects to the MQTT broker"() {

        when: "the device connects to the MQTT broker"
        client.connect()


        then: "mqtt connection should exist"
        conditions.eventually {
            assert client.getConnectionStatus() == ConnectionStatus.CONNECTED
            def connection = mqttBrokerService.getConnectionFromClientID(mqttClientId)
            assert connection != null
        }


        cleanup: "disconnect client from broker"
        client.disconnect()
    }

    def "the device connects to the correct data topic" () {
        when: "client connects to the MQTT broker and to the correct data topic"

        String dataTopic = "${Constants.MASTER_REALM}/${mqttClientId}/${TELTONIKA_DEVICE_TOKEN}/${TELTONIKA_DEVICE_IMEI}/${TELTONIKA_DEVICE_RECEIVE_TOPIC}".toString();
        client.connect();

        then: "mqtt connection should exist"
        conditions.eventually {
            assert client.getConnectionStatus() == ConnectionStatus.CONNECTED
            def connection = mqttBrokerService.getConnectionFromClientID(mqttClientId)
            assert connection != null
        }

        client.addMessageConsumer(dataTopic, { _ -> return});

        and: "There should be a subscription handled by TeltonikaMQTTHandler"
        conditions.eventually {
//            FOR SOME REASON, MQTTBrokerService.java:252 considers this connection internal,
//            so it returns void without going through with the subscription *??????????*
//            I think that client.cleanSession is what allows this to not be internal
            assert handler.connectionSubscriberInfoMap.containsKey(getTELTONIKA_DEVICE_IMEI());
        }

        cleanup: "disconnect client from broker"
        client.removeAllMessageConsumers()
        client.disconnect()
    }

    def "the device connects to the MQTT broker to a data topic without TELTONIKA_DEVICE_TOKEN"() {


        when: "the device connects to the MQTT broker to a data topic without TELTONIKA_DEVICE_TOKEN"

        def incorrectDataTopic1 = "${Constants.MASTER_REALM}/${mqttClientId}/${TELTONIKA_DEVICE_IMEI}/${TELTONIKA_DEVICE_RECEIVE_TOPIC}".toString();

        client.connect()
        client.addMessageConsumer(incorrectDataTopic1, { _ -> return });
        then: "mqtt connection should exist"
        conditions.eventually {
            assert client.getConnectionStatus() == ConnectionStatus.CONNECTED
            def connection = mqttBrokerService.getConnectionFromClientID(mqttClientId)
            assert connection != null
        }

        then: "A subscription should not exist"
        conditions.eventually {
            assert client.topicConsumerMap.get(incorrectDataTopic1) == null // Consumer added and removed on failure
            assert !handler.connectionSubscriberInfoMap.containsKey(getTELTONIKA_DEVICE_IMEI());
        }

        cleanup: "disconnect client from broker"
        client.disconnect()
        client.removeAllMessageConsumers()

    }

    def "the device connects to the MQTT broker to a data topic without an IMEI"() {


        when: "the device connects to the MQTT broker to a data topic without an IMEI"

        def incorrectDataTopic2 = "${Constants.MASTER_REALM}/${mqttClientId}/${TELTONIKA_DEVICE_TOKEN}/${TELTONIKA_DEVICE_RECEIVE_TOPIC}".toString();
        client.connect();
        client.addMessageConsumer(incorrectDataTopic2, { _ -> return });

        then: "mqtt connection should exist"
        conditions.eventually {
            assert client.getConnectionStatus() == ConnectionStatus.CONNECTED
            def connection = mqttBrokerService.getConnectionFromClientID(mqttClientId)
            assert connection != null
        }

        then: "A subscription should not exist"
        // This works because I am expecting either "data" or "command" on the 5th token, but the 5th token does not exist in the Topic
        conditions.eventually {
            assert !handler.connectionSubscriberInfoMap.containsKey(getTELTONIKA_DEVICE_IMEI());
            assert client.topicConsumerMap.get(incorrectDataTopic2) == null // Consumer added and removed on failure
        }
        cleanup: "disconnect client from broker"
        client.disconnect()
        client.removeAllMessageConsumers()
    }

    def "the device connects to the MQTT broker to a data topic without a RX or TX endpoint"() {

        when: "the device connects to the MQTT broker to a data topic without a RX or TX endpoint"

        def incorrectDataTopic3 = "${Constants.MASTER_REALM}/${mqttClientId}/${TELTONIKA_DEVICE_TOKEN}/${TELTONIKA_DEVICE_IMEI}".toString();

        client.connect();
        client.addMessageConsumer(incorrectDataTopic3, { _ -> return });

        then: "mqtt connection should exist"
        conditions.eventually {
            assert client.getConnectionStatus() == ConnectionStatus.CONNECTED
            def connection = mqttBrokerService.getConnectionFromClientID(mqttClientId)
            assert connection != null
        }

        then: "A subscription should not exist"
        // This works because I am expecting either "data" or "command" on the 5th token, but the 5th token does not exist in the Topic
        conditions.eventually {
            assert client.topicConsumerMap.get(incorrectDataTopic3) == null // Consumer added and removed on failure
            assert !handler.connectionSubscriberInfoMap.containsKey(getTELTONIKA_DEVICE_IMEI());
        }

        cleanup: "disconnect client from broker"
        client.disconnect()
        client.removeAllMessageConsumers();
    }

    def "the device connects to the broker with a valid data topic"() {
        when: "the device connects to the MQTT broker to a data topic with a RX endpoint"

        def correctTopic1 = "${Constants.MASTER_REALM}/${mqttClientId}/${TELTONIKA_DEVICE_TOKEN}/${TELTONIKA_DEVICE_IMEI}/${TELTONIKA_DEVICE_RECEIVE_TOPIC}".toString();

        client.connect();

        then: "mqtt connection should exist"
        conditions.eventually {
            assert client.getConnectionStatus() == ConnectionStatus.CONNECTED
            def connection = mqttBrokerService.getConnectionFromClientID(mqttClientId)
            assert connection != null
        }
        client.addMessageConsumer(correctTopic1, { _ -> return });

        then: "A subscription should exist"
        conditions.eventually {
            assert client.topicConsumerMap.get(correctTopic1) != null
            assert handler.connectionSubscriberInfoMap.containsKey(getTELTONIKA_DEVICE_IMEI());
        }

        cleanup: "disconnect client from broker"
        client.disconnect()
        client.removeAllMessageConsumers();
    }

    def "the device connects to the broker with valid data and command topics"() {
        when: "the device connects to the MQTT broker to a data topic with a valid data topic"

        def correctTopicData = "${Constants.MASTER_REALM}/${mqttClientId}/${TELTONIKA_DEVICE_TOKEN}/${TELTONIKA_DEVICE_IMEI}/${TELTONIKA_DEVICE_RECEIVE_TOPIC}".toString();
        def correctTopicCommands = "${Constants.MASTER_REALM}/${mqttClientId}/${TELTONIKA_DEVICE_TOKEN}/${TELTONIKA_DEVICE_IMEI}/${TELTONIKA_DEVICE_SEND_TOPIC}".toString();

        client.connect();
        client.addMessageConsumer(correctTopicData, { _ -> return });
        client.addMessageConsumer(correctTopicCommands, { _ -> return });

        then: "mqtt connection should exist"
        conditions.eventually {
            assert client.getConnectionStatus() == ConnectionStatus.CONNECTED
            def connection = mqttBrokerService.getConnectionFromClientID(mqttClientId)
            assert connection != null
        }

        then: "Two subscriptions should exist"
        conditions.eventually {
            assert client.topicConsumerMap.get(correctTopicData) != null
            assert client.topicConsumerMap.get(correctTopicCommands) != null
            assert handler.connectionSubscriberInfoMap.size() == 1;
            assert handler.connectionSubscriberInfoMap.containsKey(getTELTONIKA_DEVICE_IMEI());

        }

        cleanup: "disconnect client from broker"
        client.disconnect()
        client.removeAllMessageConsumers();
    }

    def "the handler parses the payload correctly"(){
        when: "the device connects to the MQTT broker to a data topic with a valid data topic"

        def correctTopicData = "${Constants.MASTER_REALM}/${mqttClientId}/${TELTONIKA_DEVICE_TOKEN}/${TELTONIKA_DEVICE_IMEI}/${TELTONIKA_DEVICE_RECEIVE_TOPIC}".toString();

        client.connect();
        client.addMessageConsumer(correctTopicData, { _ -> return });

        then: "mqtt connection should exist"
        conditions.eventually {
            assert client.getConnectionStatus() == ConnectionStatus.CONNECTED
            def connection = mqttBrokerService.getConnectionFromClientID(mqttClientId)
            assert connection != null
        }

        then: "Two subscriptions should exist"
        conditions.eventually {
            assert client.topicConsumerMap.get(correctTopicData) != null
            assert handler.connectionSubscriberInfoMap.size() == 1;
            assert handler.connectionSubscriberInfoMap.containsKey(getTELTONIKA_DEVICE_IMEI());

        }

        when: "A correct payload is sent to the handler"

        client.sendMessage(new MQTTMessage<String>(correctTopicData, getClass().getResource("/teltonika/teltonikaValidPayload1.json").text))

        then: "A new asset should be created"


        Asset asset;


        conditions.eventually {
            asset = assetStorageService.find(UniqueIdentifierGenerator.generateId(getTELTONIKA_DEVICE_IMEI()))
            assert asset != null;
            assert asset.getAttribute(VehicleAsset.IMEI).get().getValue().get() == (getTELTONIKA_DEVICE_IMEI());
            //Make sure that it parsed the attributes, since there is an issue of parsing the FMC003.json file
            assert asset.getAttributes().size() > 5;

        }

        and: "with the correct values within the Asset"

        conditions.eventually {
            // We cannot check every single attribute, so we are going to use specific attributes that have various quirks in them.

            //TODO: Add more parameters, maybe some weird ones like hex,

            //Attribute External Voltage, AVL ID "66"
            Optional<Attribute<?>> externalVoltage = asset.getAttribute("66");
            assert externalVoltage.isPresent();

            Attribute<?> retrievedVoltage = externalVoltage.get();

            assert retrievedVoltage.getValue().isPresent();
            TeltonikaParameter voltageParam = params.get("66")
            assert retrievedVoltage.getType() == ValueType.NUMBER;
            assert retrievedVoltage.getMeta().containsKey(MetaItemType.STORE_DATA_POINTS.getName())
            assert retrievedVoltage.getMeta().containsKey(MetaItemType.LABEL.getName())
            assert retrievedVoltage.getMeta().get(MetaItemType.LABEL.getName()).get().getValue().get() == voltageParam.propertyName

            assert retrievedVoltage.getValue(ValueType.NUMBER.getType()).get() == 11922 * Double.parseDouble(voltageParam.multiplier)
        }


        cleanup: "delete asset and disconnect client from broker"

        assetStorageService.delete([asset.getId()]);

        client.removeAllMessageConsumers()
        client.disconnect()
    }

    def "the handler sends a message when the send attribute is updated and the response is added to the correct attribute"() {
        when: "the device connects to the MQTT broker to a data topic with valid data and command topics"

        def correctTopicData = "${Constants.MASTER_REALM}/${mqttClientId}/${TELTONIKA_DEVICE_TOKEN}/${TELTONIKA_DEVICE_IMEI}/${TELTONIKA_DEVICE_RECEIVE_TOPIC}".toString();
        def correctTopicCommands = "${Constants.MASTER_REALM}/${mqttClientId}/${TELTONIKA_DEVICE_TOKEN}/${TELTONIKA_DEVICE_IMEI}/${TELTONIKA_DEVICE_SEND_TOPIC}".toString();

        String testCommandValue = "test";
        String testResponseKey = "RSP"
        String testResponseValue = "test successful"

        Map<String, String> cmdMap = new HashMap<>();
        Map<String, String> rspMap = Map.of(testResponseKey, testResponseValue)

        Boolean correctResponse = false;
        client.connect();
        client.addMessageConsumer(correctTopicData, { _ -> return });
        client.addMessageConsumer(correctTopicCommands, { msg -> {
            getLOG().error("RECEIVED NEW MESSAGE: "+msg.getPayload())
            ValueUtil.parse(msg.getPayload(), Map<String,String>.class).ifPresent {map ->
                cmdMap = map
                getLOG().error("PARSED PAYLOAD" + cmdMap);
            }
        }});

        then: "mqtt connection should exist"
        conditions.eventually {
            assert client.getConnectionStatus() == ConnectionStatus.CONNECTED
            def connection = mqttBrokerService.getConnectionFromClientID(mqttClientId)
            assert connection != null
        }

        then: "Two subscriptions should exist"
        conditions.eventually {
            assert client.topicConsumerMap.get(correctTopicData) != null
            assert client.topicConsumerMap.get(correctTopicCommands) != null
            assert handler.connectionSubscriberInfoMap.size() == 1;
            assert handler.connectionSubscriberInfoMap.containsKey(getTELTONIKA_DEVICE_IMEI());

        }

        when: "A correct payload is sent to the handler"
        client.sendMessage(new MQTTMessage<String>(correctTopicData, getClass().getResource("/teltonika/teltonikaValidPayload1.json").text))

        then: "A new asset should be created"
        Asset asset;
        conditions.eventually {
            asset = assetStorageService.find(UniqueIdentifierGenerator.generateId(getTELTONIKA_DEVICE_IMEI()))
            assert asset != null;
            assert asset.getAttribute(VehicleAsset.IMEI).get().getValue().get() == (getTELTONIKA_DEVICE_IMEI());
        }

        when: "the send message attribute is created and then updated"
        AttributeRef ref = new AttributeRef(
                asset.getId(),
                getTELTONIKA_DEVICE_SEND_COMMAND_ATTRIBUTE_NAME()
        )


        asset.addAttributes(new Attribute<String>(ref.getName(), ValueType.TEXT));
        assetStorageService.merge(asset);

        assetProcessingService.sendAttributeEvent(new AttributeEvent(ref, testCommandValue));

        then: "A message is sent to the device with the correct format"

        conditions.eventually {
            getLOG().info("eventually: "+cmdMap.toString())
            assert cmdMap.containsKey("CMD")
            assert cmdMap.get("CMD") == testCommandValue
        }

        when: "the device replies to that message"

        // Check AWS IoT Core tutorial from Teltonika, the bottom of the page always shows that rspMap is the format we nee
        client.sendMessage(new MQTTMessage<String>(correctTopicData, ValueUtil.asJSON(rspMap).get()));

        then: "The Handler understands the message and updates the response attribute"

        conditions.eventually {
            asset = assetStorageService.find(asset.getId())
            Optional<Attribute<?>> attr = asset.getAttribute(getTELTONIKA_DEVICE_RECEIVE_COMMAND_ATTRIBUTE_NAME());
            assert attr.isPresent();
            assert attr.get().getValue(String.class).get() == testResponseValue
        }


        cleanup: "delete asset and disconnect client from broker"

        boolean result = assetStorageService.delete([asset.getId()]);

        conditions.eventually {
            result
        }

        client.removeAllMessageConsumers()
        client.disconnect()
    }


    //TODO: Commented-out test for now, need to create a concise list of payloads for full integration test, with
    // Asset state duration, bidirectional messages, etc.


    def "the handler stores all attributes with the correct timestamp"() {
//        when: "remove the asset, if it exists"
//        then: "asset is not there"
//        conditions.eventually {
//            assert assetStorageService.delete([UniqueIdentifierGenerator.generateId(TELTONIKA_DEVICE_IMEI)])
//            assert assetStorageService.find(UniqueIdentifierGenerator.generateId(TELTONIKA_DEVICE_IMEI)) == null
//        }

        when: "the device connects to the MQTT broker to a data topic with a RX endpoint"

        String correctTopic1 = "${Constants.MASTER_REALM}/${mqttClientId}/${TELTONIKA_DEVICE_TOKEN}/${TELTONIKA_DEVICE_IMEI}/${TELTONIKA_DEVICE_RECEIVE_TOPIC}".toString();
        client.connect();
        then: "mqtt connection should exist"
        conditions.eventually {
            assert client.getConnectionStatus() == ConnectionStatus.CONNECTED
            def connection = mqttBrokerService.getConnectionFromClientID(mqttClientId)
            assert connection != null
        }
        when: "a client subscribes to the correct data topic"
        client.addMessageConsumer(correctTopic1, { _ -> return });

        then: "A subscription should exist"
        conditions.eventually {
            assert client.topicConsumerMap.get(correctTopic1) != null
            assert handler.connectionSubscriberInfoMap.containsKey(getTELTONIKA_DEVICE_IMEI());
        }

        when: "the JSON with all the payloads is parsed"
        def slurp = new JsonSlurper()
        ArrayList<Object> payloads = slurp.parseText(getClass().getResource("/teltonika/SortedPayloads.json").text) as ArrayList<Object>;
        then: "Assert that the payloads are correct"

        String assetId = UniqueIdentifierGenerator.generateId(getTELTONIKA_DEVICE_IMEI());
        int i = 0;
        when: "the device starts publishing payloads"
        payloads.stream().limit(100).forEach { Object payload ->
//             Your logic here, for example:
            getLOG().debug(JsonOutput.toJson(payload));

            client.sendMessage(new MQTTMessage<String>(correctTopic1, JsonOutput.toJson(payload)))

            conditions.eventually {
                if(i > 0){
                    long payloadTimestamp = payload['state']['reported']['ts'] as long;
                    int payloadElements = payload['state']['reported'].collect().size();
                    Asset<VehicleAsset> asset = assetStorageService.find(new AssetQuery().ids(assetId).types(VehicleAsset.class))
                    assert asset.getAttribute(VehicleAsset.LAST_CONTACT).get().getValue().get().getTime() == payload['state']['reported']['ts']

                    //Check if the payload creates an equal amount of AttributeEvents
                    assert asset.getAttributes().stream().filter ({a -> a.getTimestamp().get() == payloadTimestamp }).filter(a -> a.getType() != CustomValueTypes.ASSET_STATE_DURATION).count() == payloadElements
                }
            }
            i++;

            sleep(100);
        }
        then: "it's done"
        sleep(100)



        cleanup: "disconnect client from broker"
        client.disconnect()
        client.removeAllMessageConsumers();


    }

    //TODO: Write a test for AssetStateDuration (Multiple trip payloads etc.)
}
