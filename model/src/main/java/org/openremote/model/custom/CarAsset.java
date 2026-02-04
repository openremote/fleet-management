package org.openremote.model.custom;

import jakarta.persistence.Entity;
import org.openremote.model.Constants;
import org.openremote.model.asset.AssetDescriptor;
import org.openremote.model.teltonika.TeltonikaModelConfigurationAsset;
import org.openremote.model.value.AttributeDescriptor;
import org.openremote.model.value.ValueType;
import org.openremote.model.value.impl.ColourRGB;

import java.util.Optional;
/**
 * CarAsset is an extension of the VehicleAsset class, specifically intended for the fleet management use case of OpenRemote.
 * It is used as the class for the car asset type that should work using this integration.
 *
 * This Asset Type is used as both an example and as a viable use-case for the OpenRemote Fleet Telematics integration
 * of OpenRemote with Teltonika Telematics.
 *
 * It contains the correct, user-fillable metadata, while also containing some specific attributes that are widely used
 * in the fleet management use case.
 *
 * In this situation, the user has two options; either extend the Vehicle asset as this class, or extend this asset type,
 * to use the attributes that are included in it.
 * */
@Entity
public class CarAsset extends VehicleAsset{
	public static final AssetDescriptor<CarAsset> DESCRIPTOR = new AssetDescriptor<>("car", null, CarAsset.class);

	// Vehicle meta-data
	public static final AttributeDescriptor<ColourRGB> COLOR = new AttributeDescriptor<>("color", ValueType.COLOUR_RGB).withOptional(true);
	public static final AttributeDescriptor<Integer> MODEL_YEAR = new AttributeDescriptor<>("modelYear", ValueType.INTEGER).withOptional(true)
			.withUnits(Constants.UNITS_YEAR);
	public static final AttributeDescriptor<String> LICENSE_PLATE = new AttributeDescriptor<>("licensePlate", ValueType.TEXT).withOptional(true);

	//Ignition
	public static final AttributeDescriptor<Boolean> IGNITION_ON = new AttributeDescriptor<>("239", ValueType.BOOLEAN)
			.withMeta(TeltonikaModelConfigurationAsset.getPayloadAttributeMeta("Ignition status"));

	//Movement
	public static final AttributeDescriptor<Boolean> MOVEMENT = new AttributeDescriptor<>("240", ValueType.BOOLEAN)
			.withMeta(TeltonikaModelConfigurationAsset.getPayloadAttributeMeta("Movement status"));

	//odometer
	public static final AttributeDescriptor<Double> ODOMETER = new AttributeDescriptor<>("16", ValueType.NUMBER)
			.withMeta(TeltonikaModelConfigurationAsset.getPayloadAttributeMeta("Odometer"))
			.withUnits(Constants.UNITS_METRE);


	// All the permanent ones (pr, alt, ang, sat, sp, evt)

	public static final AttributeDescriptor<Double> EVENT_ATTR_NAME = new AttributeDescriptor<>("evt", ValueType.NUMBER).withOptional(true)
			.withMeta(TeltonikaModelConfigurationAsset.getPayloadAttributeMeta("Event triggered by"));
	public static final AttributeDescriptor<Double> ALTITUDE = new AttributeDescriptor<>("alt", ValueType.NUMBER).withOptional(true)
			.withMeta(TeltonikaModelConfigurationAsset.getPayloadAttributeMeta("Altitude"))
			.withUnits(Constants.UNITS_METRE);
	public static final AttributeDescriptor<Double> SATELLITES = new AttributeDescriptor<>("sat", ValueType.NUMBER).withOptional(true)
			.withMeta(TeltonikaModelConfigurationAsset.getPayloadAttributeMeta("Number of satellites in use"));
	public static final AttributeDescriptor<Double> SPEED = new AttributeDescriptor<>("sp", ValueType.NUMBER)
			.withMeta(TeltonikaModelConfigurationAsset.getPayloadAttributeMeta("Speed"))
			.withUnits(Constants.UNITS_KILO, Constants.UNITS_METRE, Constants.UNITS_PER, Constants.UNITS_HOUR);
	public static final AttributeDescriptor<Double> PRIORITY = new AttributeDescriptor<>("pr", ValueType.NUMBER).withOptional(true)
			.withMeta(TeltonikaModelConfigurationAsset.getPayloadAttributeMeta("Payload priority (0-2)"));



	//Hydration
	protected CarAsset() {
	}

	public CarAsset(String name) {
		super(name);
	}
	public Optional<Integer> getModelYear() {
		return getAttributes().getValue(MODEL_YEAR);
	}
	public Optional<ColourRGB> getColor() {
		return getAttributes().getValue(COLOR);
	}
}
