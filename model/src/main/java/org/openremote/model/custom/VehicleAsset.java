package org.openremote.model.custom;

import jakarta.persistence.Entity;
import org.openremote.model.Constants;
import org.openremote.model.asset.Asset;
import org.openremote.model.asset.AssetDescriptor;
import org.openremote.model.attribute.MetaMap;
import org.openremote.model.geo.GeoJSONPoint;
import org.openremote.model.teltonika.TeltonikaModelConfigurationAsset;
import org.openremote.model.value.AttributeDescriptor;
import org.openremote.model.value.ValueType;
import org.openremote.model.value.impl.ColourRGB;

import java.util.Date;
import java.util.Optional;
/**
 * {@code VehicleAsset} is a custom asset type specifically intended for the fleet management use case of OpenRemote.
 * It is used as the base class of any subsequent vehicle asset types that should work using this integration.
 * The VehicleAsset class contains all required attributes and methods to be used by the Teltonika Telematics integration.
 *
 * In case the user wants to add more attributes to the vehicle asset, they can do so by extending the VehicleAsset class.
 * To view such an example, see the CarAsset class.
 */
@Entity
public class VehicleAsset extends Asset<VehicleAsset> {

    public static final AttributeDescriptor<GeoJSONPoint> LOCATION = new AttributeDescriptor<>("location", ValueType.GEO_JSON_POINT)
            .withMeta(TeltonikaModelConfigurationAsset.getPayloadAttributeMeta("Location"));

    public static final AttributeDescriptor<String> IMEI = new AttributeDescriptor<>("IMEI", ValueType.TEXT);
    public static final AttributeDescriptor<Date> LAST_CONTACT = new AttributeDescriptor<>("lastContact", ValueType.DATE_AND_TIME)
            .withMeta(TeltonikaModelConfigurationAsset.getPayloadAttributeMeta("Last message time"));
    public static final AttributeDescriptor<String> MODEL_NUMBER = new AttributeDescriptor<>("modelNumber", ValueType.TEXT);

    public static final AttributeDescriptor<Integer> DIRECTION = new AttributeDescriptor<>("direction", ValueType.DIRECTION)
            .withMeta(TeltonikaModelConfigurationAsset.getPayloadAttributeMeta("Direction"))
            .withUnits(Constants.UNITS_DEGREE);

    // Figure out a way to use the colour parameter for the color of the car on the map



    public static final AssetDescriptor<VehicleAsset> DESCRIPTOR = new AssetDescriptor<>("car", null, VehicleAsset.class);

    protected VehicleAsset(){
    }
    public VehicleAsset(String name){super(name);}

    public Optional<String> getIMEI() {
        return getAttributes().getValue(IMEI);
    }
    public Optional<Date> getLastContact() {
        return getAttributes().getValue(LAST_CONTACT);
    }

    public Optional<String> getModelNumber(){return getAttributes().getValue(MODEL_NUMBER);}

    public VehicleAsset setModelNumber(String value){
        getAttributes().getOrCreate(MODEL_NUMBER).setValue(value);
        return this;
    }
}
