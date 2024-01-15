package org.openremote.model.custom;

import jakarta.persistence.Entity;
import org.openremote.model.asset.Asset;
import org.openremote.model.asset.AssetDescriptor;
import org.openremote.model.attribute.Attribute;
import org.openremote.model.attribute.MetaItem;
import org.openremote.model.attribute.MetaMap;
import org.openremote.model.geo.GeoJSONPoint;
import org.openremote.model.teltonika.TeltonikaParameter;
import org.openremote.model.value.AttributeDescriptor;
import org.openremote.model.value.MetaItemType;
import org.openremote.model.value.ValueType;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Entity
public class TeltonikaModelConfigurationAsset extends Asset<TeltonikaModelConfigurationAsset> {
    public static final AttributeDescriptor<String> MODEL_NUMBER = new AttributeDescriptor<>("modelNumber", ValueType.TEXT);
    public static final AttributeDescriptor<TeltonikaParameter[]> PARAMETER_DATA = new AttributeDescriptor<>("TeltonikaParameterData", CustomValueTypes.TELTONIKA_PARAMETER.asArray());

    public static final AttributeDescriptor<CustomValueTypes.TeltonikaParameterMap> PARAMETER_MAP = new AttributeDescriptor<>("TeltonikaParameterMap", CustomValueTypes.TELTONIKA_PARAMETER_MAP)
            .withMeta(new MetaMap(Map.of(MetaItemType.READ_ONLY.getName(), new MetaItem<>(MetaItemType.READ_ONLY, true))));
    public static final AssetDescriptor<TeltonikaModelConfigurationAsset> DESCRIPTOR = new AssetDescriptor<>("switch", null, TeltonikaModelConfigurationAsset.class);

    protected TeltonikaModelConfigurationAsset(){}

    public TeltonikaModelConfigurationAsset(String name){
        super(name);
        super.setLocation(new GeoJSONPoint(0,0,0));
        super.setNotes("");
    }

    public TeltonikaModelConfigurationAsset setModelNumber(String name) {
        getAttributes().getOrCreate(MODEL_NUMBER).setValue(name);
        return this;
    }

    public TeltonikaModelConfigurationAsset setParameterData(TeltonikaParameter[] data) {
        //Get TeltonikaParameter array, cast to Map with parameter ID as key, save to PARAMETER_MAP, remove from PARAMETER_DATA
        getAttributes().getOrCreate(PARAMETER_DATA).setValue(data);
        CustomValueTypes.TeltonikaParameterMap map = Arrays.stream(data).collect(Collectors.toMap(
                TeltonikaParameter::getPropertyId, // Key Mapper
                param -> param, // Value Mapper
                (existing, replacement) -> replacement, // Merge Function
                CustomValueTypes.TeltonikaParameterMap::new
        ));

        getAttributes().getOrCreate(PARAMETER_MAP).setValue(map);

        //



        return this;
    }

    public Optional<String> getModelNumber(){
        return getAttributes().getValue(MODEL);
    }

    public CustomValueTypes.TeltonikaParameterMap getParameterMap() {
        Optional<Attribute<CustomValueTypes.TeltonikaParameterMap>> map = getAttributes().get(PARAMETER_MAP);

        return map.flatMap(Attribute::getValue)
                .orElse(new CustomValueTypes.TeltonikaParameterMap()); // or provide a default value other than null, if appropriate
    }

}
