package org.openremote.model.custom;

import jakarta.persistence.Entity;
import org.openremote.model.asset.Asset;
import org.openremote.model.asset.AssetDescriptor;
import org.openremote.model.geo.GeoJSONPoint;
import org.openremote.model.value.AttributeDescriptor;
import org.openremote.model.value.ValueType;

@Entity
public class TeltonikaConfigurationAsset extends Asset<TeltonikaConfigurationAsset> {
    public static final AttributeDescriptor<String[]> WHITELIST = new AttributeDescriptor<>("deviceIMEIWhitelist", ValueType.TEXT.asArray()).withOptional(true);
    public static final AttributeDescriptor<Boolean> ENABLED = new AttributeDescriptor<>("Enabled", ValueType.BOOLEAN);
    public static final AttributeDescriptor<Boolean> CHECK_FOR_IMEI = new AttributeDescriptor<>("CheckForValidIMEI", ValueType.BOOLEAN);
    public static final AttributeDescriptor<String> DEFAULT_MODEL_NUMBER = new AttributeDescriptor<>("defaultModelNumber", ValueType.TEXT);

    public static final AssetDescriptor<TeltonikaConfigurationAsset> DESCRIPTOR = new AssetDescriptor<>("gear", null, TeltonikaConfigurationAsset.class);

    protected TeltonikaConfigurationAsset(){

    }

    public TeltonikaConfigurationAsset(String name){
        super(name);
        super.setLocation(new GeoJSONPoint(0,0,0));
        super.setNotes("");
    }

    public TeltonikaConfigurationAsset setWhitelist(String[] whitelist) {
        getAttributes().getOrCreate(WHITELIST).setValue(whitelist);
        return this;
    }
    public TeltonikaConfigurationAsset setEnabled(boolean enabled) {
        getAttributes().getOrCreate(ENABLED).setValue(enabled);
        return this;
    }

    public TeltonikaConfigurationAsset setCheckForImei(boolean enabled) {
        getAttributes().getOrCreate(CHECK_FOR_IMEI).setValue(enabled);
        return this;
    }

    public TeltonikaConfigurationAsset setDefaultModelNumber(String value) {
        getAttributes().getOrCreate(DEFAULT_MODEL_NUMBER).setValue(value);
        return this;
    }
}
