package org.openremote.model.teltonika;

import org.openremote.model.attribute.Attribute;
import org.openremote.model.custom.CustomValueTypes;
import org.openremote.model.custom.TeltonikaConfigurationAsset;
import org.openremote.model.custom.TeltonikaModelConfigurationAsset;
import org.openremote.model.value.AttributeDescriptor;

import java.util.HashMap;
import java.util.List;
import java.util.stream.Collectors;

public class TeltonikaConfiguration {
    public TeltonikaConfigurationAsset getMasterAsset() {
        return masterAsset;
    }

    TeltonikaConfigurationAsset masterAsset;
    /**
     * Maps Model Number to Map of parameters: {@code [{"FMCOO3": {<map of parameters>}...]}}
     */
    HashMap<String, CustomValueTypes.TeltonikaParameterMap> parameterMap;

    public List<TeltonikaModelConfigurationAsset> getModelAssets() {
        return modelAssets;
    }

    private List<TeltonikaModelConfigurationAsset> modelAssets;

    public TeltonikaConfiguration(TeltonikaConfigurationAsset master, List<TeltonikaModelConfigurationAsset> models){
        if (master == null) return;
        if (models.isEmpty()) return;

        masterAsset = master;

        parameterMap = models.stream().collect(Collectors.toMap(
                val ->val.getAttributes().get(TeltonikaModelConfigurationAsset.MODEL_NUMBER).get().getValue().get(), // Key Mapper
                TeltonikaModelConfigurationAsset::getParameterMap, // Value Mapper
                (existing, replacement) -> replacement, // Merge Function
                HashMap::new
        ));

        modelAssets = models;

    }

    @Override
    public String toString() {
        return "TeltonikaConfiguration{" +
                "masterAsset=" + masterAsset +
                ", parameterMap=" + parameterMap +
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

    public HashMap<String, CustomValueTypes.TeltonikaParameterMap> getParameterMap() {
        return parameterMap;
    }
    public Attribute<String> getCommandAttribute(){
        return getMasterAsset().getAttribute(TeltonikaConfigurationAsset.COMMAND).get();
    }
    public Attribute<String> getResponseAttribute(){
        return getMasterAsset().getAttribute(TeltonikaConfigurationAsset.RESPONSE).get();
    }
}
