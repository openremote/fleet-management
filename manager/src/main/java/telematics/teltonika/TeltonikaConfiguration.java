package telematics.teltonika;

import org.openremote.manager.asset.AssetStorageService;
import org.openremote.model.attribute.Attribute;
import org.openremote.model.custom.CustomValueTypes;
import org.openremote.model.query.AssetQuery;
import org.openremote.model.query.filter.ParentPredicate;
import org.openremote.model.query.filter.RealmPredicate;
import org.openremote.model.teltonika.TeltonikaConfigurationAsset;
import org.openremote.model.teltonika.TeltonikaModelConfigurationAsset;

import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.stream.Collectors;

/**
 * <p>
 * {@code TeltonikaConfiguration} is a class that holds the configuration for Teltonika devices.
 * </p>
 * <br>
 * <p>
 *      It employs the Singleton pattern to ensure that only one instance of the class is created.
 *      We employ the Singleton pattern because retrieving the configuration data is a costly process, which requires a lot
 *      of requests to the database to retrieve the attributes.
 *      We want to ensure that we only retrieve the configuration data once and then use it throughout the application.
 * </p>
 * <br>
 * <p>
 *     The class also employs the Factory pattern in {@code TeltonikaConfigurationFactory} to create the configuration
 *     object. The factory method is used to create the configuration object and to ensure that only one instance of the
 *     configuration object is created.
 *     The factory class does help with managing the instance and creating it, but also helps with keeping it up to date.
 *     To ensure that the configuration object is up to date, the factory class uses a timestamp to check if the
 *     configuration has been used for up to a minute. When a minute has elapsed
 *     (editable using {@code CONFIGURATION_UPDATE_INTERVAL}), it refreshes the instance of the configuration object.
 * </p>
 * <br>
 * <p>
 *     We want to be sure that the configuration is always up to date, even when the user is configuring the asset.
 *     To do so, we employ a sufficient-enough version of the observer pattern on the TeltonikaConfigurationFactory.
 *     When the AttributeEvent handler for configuration asset data detects a change, it triggers a refresh of the
 *     instance.
 * </p>
 * <br>
 * <p>
 *      The TeltonikaConfiguration object is also using a builder pattern to make it easier to instantiate and use the
 *      configuration data.
 * </p>
 * <br>
 * <p>
 *     A Teltonika configuration consists of multiple assets; one {@code TeltonikaMasterConfigurationAsset} and multiple
 *     {@code TeltonikaModelConfigurationAssets}. All assets are only retrieved from the {@code master} realm.
 *     The {@code TeltonikaMasterConfigurationAsset} holds the configuration for the totality of the implementation, with
 *     settings like payload storage etc., while the {@code TeltonikaModelConfigurationAssets} hold the configuration for
 *     specific Teltonika device models. They specifically contain the model number and the parameters to be used
 *     for parsing of the payloads received.
 * </p>
 */
public class TeltonikaConfiguration {

    private static TeltonikaConfiguration instance = null;

    public static TeltonikaConfiguration getInstance() throws NullPointerException {
        if (instance == null) throw new NullPointerException("TeltonikaConfiguration instance is null");
        //if the update time has elapsed, request update to configuration
        if (latestUpdateTimestamp == null ||
                (new Date().getTime() - latestUpdateTimestamp.getTime()) > CONFIGURATION_UPDATE_INTERVAL
        ) {
            throw new NullPointerException("TeltonikaConfiguration instance needs to be refreshed.");
        }
        return instance;
    }

    public static void setInstance(TeltonikaConfiguration instance) {
        TeltonikaConfiguration.instance = instance;
    }

    private static Date latestUpdateTimestamp = null;

    private static final int CONFIGURATION_UPDATE_INTERVAL = 1000 * 60 * 1; // 1 minute

    TeltonikaConfigurationAsset masterAsset;

    public TeltonikaConfigurationAsset getMasterAsset() {
        return masterAsset;
    }
    /**
     * Maps Model Number to Map of parameters: {@code [{"FMCOO3": {<map of parameters>}...]}}
     */
    HashMap<String, CustomValueTypes.TeltonikaParameterMap> defaultParameterMap = new HashMap<>();

    private List<TeltonikaModelConfigurationAsset> modelAssets;



    public TeltonikaConfiguration(TeltonikaConfigurationAsset master, List<TeltonikaModelConfigurationAsset> models, Date date){


        if (master == null) return;
        if (models.isEmpty()) return;

        masterAsset = master;

        defaultParameterMap = models.stream().collect(Collectors.toMap(
                val ->val.getAttributes().get(TeltonikaModelConfigurationAsset.MODEL_NUMBER).get().getValue().get(), // Key Mapper
                TeltonikaModelConfigurationAsset::getParameterMap, // Value Mapper
                (existing, replacement) -> replacement, // Merge Function
                HashMap::new
        ));

        modelAssets = models;
        latestUpdateTimestamp = date;

    }

    public List<TeltonikaModelConfigurationAsset> getModelAssets() {
        return modelAssets;
    }

    public TeltonikaModelConfigurationAsset getModelAsset(String modelNumber) throws NoSuchElementException {
        return modelAssets.stream()
                .filter(
                        val -> val.getAttributes().get(TeltonikaModelConfigurationAsset.MODEL_NUMBER).get().getValue().get().equals(modelNumber)
                )
                .findFirst().orElseThrow(NoSuchElementException::new);
    }

    @Override
    public String toString() {
        return "TeltonikaConfiguration{" +
                "masterAsset=" + masterAsset +
                ", parameterMap=" + defaultParameterMap +
                ", modelAssets=" + modelAssets +
                '}';
    }

    public List<String> getChildModelIDs(){
        return modelAssets.stream().map(TeltonikaModelConfigurationAsset::getId).collect(Collectors.toList());
    }

    public Boolean getEnabled(){
        return masterAsset.getAttribute(TeltonikaConfigurationAsset.ENABLED).get().getValue().get();
    }

    public boolean getCheckForImei() {
        return masterAsset.getAttribute(TeltonikaConfigurationAsset.CHECK_FOR_IMEI).get().getValue().get();
    }

    public String getDefaultModelNumber() {
        return masterAsset.getAttribute(TeltonikaConfigurationAsset.DEFAULT_MODEL_NUMBER).get().getValue().get();
    }

    public HashMap<String, CustomValueTypes.TeltonikaParameterMap> getModelParameterMap(String modelNumber) {
        return defaultParameterMap;
    }

    public Attribute<String> getCommandAttribute(){
        return getMasterAsset().getAttribute(TeltonikaConfigurationAsset.COMMAND).get();
    }
    public Attribute<String> getResponseAttribute(){
        return getMasterAsset().getAttribute(TeltonikaConfigurationAsset.RESPONSE).get();
    }

    public Attribute<Boolean> getStorePayloads(){
        return getMasterAsset().getAttribute(TeltonikaConfigurationAsset.STORE_PAYLOADS).get();
    }
}
