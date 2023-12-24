package org.openremote.model.custom;

import jakarta.persistence.Entity;
import net.fortuna.ical4j.model.property.Geo;
import org.openremote.model.asset.Asset;
import org.openremote.model.asset.AssetDescriptor;
import org.openremote.model.geo.GeoJSONPoint;
import org.openremote.model.teltonika.TeltonikaParameter;
import org.openremote.model.value.AttributeDescriptor;
import org.openremote.model.value.ValueType;

@Entity
public class TeltonikaModelConfigurationAsset extends Asset<TeltonikaModelConfigurationAsset> {
    public static final AttributeDescriptor<String> MODEL_NUMBER = new AttributeDescriptor<>("modelNumber", ValueType.TEXT);
    public static final AttributeDescriptor<TeltonikaParameter[]> PARAMETER_DATA = new AttributeDescriptor<>("TeltonikaParameterData", CustomValueTypes.TELTONIKA_PARAMETER.asArray());
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
        getAttributes().getOrCreate(PARAMETER_DATA).setValue(data);
        return this;
    }
}
