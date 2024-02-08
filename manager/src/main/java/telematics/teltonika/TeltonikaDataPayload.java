package telematics.teltonika;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.checkerframework.checker.units.qual.A;
import org.openremote.container.timer.TimerService;
import org.openremote.model.Constants;
import org.openremote.model.asset.Asset;
import org.openremote.model.attribute.Attribute;
import org.openremote.model.attribute.AttributeMap;
import org.openremote.model.attribute.MetaItem;
import org.openremote.model.attribute.MetaMap;
import org.openremote.model.custom.VehicleAsset;
import org.openremote.model.geo.GeoJSONPoint;
import org.openremote.model.teltonika.State;
import org.openremote.model.teltonika.TeltonikaParameter;
import org.openremote.model.util.ValueUtil;
import org.openremote.model.value.*;

import java.lang.reflect.Field;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.*;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static org.openremote.model.value.MetaItemType.*;
import static org.openremote.model.value.MetaItemType.READ_ONLY;
/**
 * This class is used to represent the payload from a Teltonika device when sending a data payload.
 * A sample payload can be found and is used in the {@code org.openremote.test.custom.TeltonikaMQTTProtocolTest} class.
 * <p>
 * It implements the {@code ITeltonikaPayload} interface, which is used to extract the payload's
 * attributes and create an attribute map.
 */
public class TeltonikaDataPayload implements ITeltonikaPayload {

	@Override
	public String getModelNumber() {
		return modelNumber;
	}

	private String modelNumber = null;

	protected TeltonikaDataPayload(State payload, String modelNumber) {
		this.state = payload;
		this.modelNumber = modelNumber;

	}

	private State state;

	public State getState() {
		return state;
	}
	// getter and setter for logger

	private static final Logger logger = Logger.getLogger(TeltonikaDataPayload.class.getName());

	private Logger getLogger() {
		return logger;
	}

	/**
	 * Returns list of attributes depending on the Teltonika JSON Payload.
	 * Uses the logic and results from parsing the Teltonika Parameter IDs.
	 */
	public Map<TeltonikaParameterData, Object> getAttributesFromPayload(TeltonikaConfiguration config, TimerService timerService) {


		HashMap<String, TeltonikaParameter> params = new HashMap<>();
		ObjectMapper mapper = new ObjectMapper();
		try {
			// Parse file with Parameter details

			// Add each element to the HashMap, with the key being the unique parameter ID and the parameter
			// being the value

			//Cast keys to String
			params = config.getModelParameterMap(modelNumber).get(config.getDefaultModelNumber()).entrySet().stream().collect(Collectors.toMap(
					kvp -> kvp.getKey().toString(),
					Map.Entry::getValue,
					(existing, replacement) -> existing,
					HashMap::new
			));
			if (params.size() < 10) {
				logger.warning("Parsed " + params.size() + " Teltonika Parameters");
			}


		} catch (Exception e) {
			logger.warning("Could not parse the Teltonika Parameter file");
			logger.info(e.toString());
		}

		// Add the custom parameters (pr, alt, ang, sat, sp, evt)
		List<TeltonikaParameterData> customParams = new ArrayList<TeltonikaParameterData>(List.of(
				new TeltonikaParameterData("pr", new TeltonikaParameter(-1, "Priority", String.valueOf(1), "Unsigned", String.valueOf(0), String.valueOf(4), String.valueOf(1), "-", "0: Low - 1: High - 2: Panic", "all", "Permanent I/O Elements")),
				new TeltonikaParameterData("alt", new TeltonikaParameter(-1, "Altitude", "2", "Signed", "-1000", "+3000", "1", "m", "meters above sea level", "all", "Permanent I/O Elements")),
				new TeltonikaParameterData("ang", new TeltonikaParameter(-1, "Direction", "2", "Signed", "-360", "+460", "1", "deg", "degrees from north pole", "all", "Permanent I/O Elements")),
				new TeltonikaParameterData("sat", new TeltonikaParameter(-1, "Satellites", "1", "Unsigned", "0", "1000", "1", "-", "number of visible satellites", "all", "Permanent I/O Elements")),
				new TeltonikaParameterData("sp", new TeltonikaParameter(-1, "Speed", "2", "Signed", "0", "1000", "1", "km/h", "speed calculated from satellites", "all", "Permanent I/O Elements")),
				new TeltonikaParameterData("evt", new TeltonikaParameter(-1, "Event Triggered", "2", "Signed", "0", "10000", "1", "-", "Parameter ID which generated this payload", "all", "Permanent I/O Elements")),
				new TeltonikaParameterData("latlng", new TeltonikaParameter(-1, "Coordinates", "8", "ASCII", "-", "-", "-", "-", "The device's coordinates at the given time", "all", "Permanent I/O Elements")),
				new TeltonikaParameterData("ts", new TeltonikaParameter(-1, "Timestamp", "8", "Signed", "-", "-", "1", "-", "The device time when the payload was sent", "all", "Permanent I/O Elements"))
		));

		params.putAll(customParams.stream().collect(Collectors.toMap(
				TeltonikaParameterData::getParameterId,
				TeltonikaParameterData::getParameter,
				(existing, replacement) -> existing,
				HashMap::new
		)));
		//Parameters parsed, time to understand the payload
		AttributeMap attributeMap;

		try {
			HashMap<String, TeltonikaParameter> finalParams = params;
			Map<TeltonikaParameterData, Object> payloadMap = this.state.reported.entrySet().stream().collect(Collectors.toMap(
					entry -> {
						TeltonikaParameterData desiredEntry = null;

						for (Map.Entry<String, TeltonikaParameter> parameterEntry : finalParams.entrySet()) {
							if (parameterEntry.getKey().equals(entry.getKey())) {
								desiredEntry = new TeltonikaParameterData(entry.getKey(), parameterEntry.getValue());
								break;
							}
						}

						// Check if the entry was found
						if (desiredEntry != null) {
							return desiredEntry;
						} else {
							throw new IllegalArgumentException("Key " + entry.getValue().toString() + " not found in finalParams");
						}
					}, // Key Mapper
					Map.Entry::getValue, // Value Mapper
					(existing, replacement) -> {
						logger.severe("Parameter " + replacement.toString() + " already exists in the map");
						return null;
					}, // Merge Function
					HashMap::new
			));
			return payloadMap;
		} catch (Exception e) {
			logger.severe("Failed to payload.state.GetAttributes");
			logger.severe(e.toString());
			throw e;
		}

	}

	public AttributeMap getAttributes(Map<TeltonikaParameterData, Object> payloadMap, TeltonikaConfiguration config, Logger logger, Map<String, AttributeDescriptor<?>> descs) {
		AttributeMap attributes = new AttributeMap();
		String[] specialProperties = {"latlng", "ts"};
		for (Map.Entry<TeltonikaParameterData, Object> entry : payloadMap.entrySet()) {

			TeltonikaParameter parameter = entry.getKey().getParameter();
			String parameterId = entry.getKey().getParameterId();
			//latlng are the latitude-longitude coordinates, also check if it's 0,0, if it is, don't update.
			if (parameterId.equals("latlng") && !Objects.equals(entry.getValue(), "0.000000,0.000000")) {
				try {
					String latlngString = entry.getValue().toString();
					GeoJSONPoint point = ParseLatLngToGeoJSONObject(latlngString);
					Attribute<?> attr = new Attribute<>(Asset.LOCATION, point);

					attributes.add(attr);
				} catch (Exception e) {
					logger.severe("Failed coordinates");
					logger.severe(e.toString());
					throw e;
				}
				continue;
			}
			//Timestamp grabbed from the device.
			if (Objects.equals(parameterId, "ts")) {
				try {
					long unixTimestampMillis = Long.parseLong(entry.getValue().toString());
					Timestamp deviceTimestamp = Timestamp.from(Instant.ofEpochMilli(unixTimestampMillis));
					//Maybe this attribute should have the value set as server time and the device time as a timestamp?
					attributes.add(new Attribute<>(VehicleAsset.LAST_CONTACT, deviceTimestamp, deviceTimestamp.getTime()));

					//Update all affected attribute timestamps
					attributes.forEach(attribute -> attribute.setTimestamp(deviceTimestamp.getTime()));
				} catch (Exception e) {
					logger.severe("Failed timestamps");
					logger.severe(e.toString());
					throw e;
				}
				continue;
			}
			if(Objects.equals(parameterId, "ang")){
				//This is the parameter ID which triggered the payload
				Object angle = ValueUtil.getValueCoerced(entry.getValue(), ValueType.DIRECTION.getType()).orElseThrow();
				Attribute<String> attr = new Attribute(VehicleAsset.DIRECTION, angle);
				attributes.add(attr);
				continue;
			}

			/*
			* Quick explanation here, with the new update that allows for custom Asset Types, we need to somehow
			* be able to understand when a parameter has been defined as an Attribute in the custom Asset Type that we
			* are using. By using the list of AttributeDescriptors, we can check if the parameter ID is present in the
			* list of AttributeDescriptors. If it is, then we can directly use the AttributeDescriptor we found to
			* dynamically create that attribute in the way the AttributeDescriptor says, whether the Attribute has been
			* created yet or not.
			* */
			if(descs.containsKey(parameterId)){
				AttributeDescriptor<?> descriptor = descs.get(parameterId);
				Object obj = ValueUtil.getValueCoerced(entry.getValue(), descriptor.getType().getType()).orElseThrow();
				Attribute<?> attr = new Attribute(descs.get(parameterId), obj);
				attributes.add(attr);
				continue;
			}

			//Create the MetaItem Map
			MetaMap metaMap = new MetaMap();

			// Figure out its attributeType
			ValueDescriptor<?> attributeType = GetAttributeType(parameter);

			//Retrieve its coerced value
			Optional<?> value;
			try {
				//Inner method returns Optional.empty, but still throws and prints exception. A lot of clutter, but the exception is handled.
				value = ValueUtil.getValueCoerced(entry.getValue(), attributeType.getType());
				if (value.isEmpty()) {
					attributeType = ValueType.TEXT;
					value = Optional.of(entry.getValue().toString());
				}
			} catch (Exception e) {
				value = Optional.of(entry.getValue().toString());
				attributeType = ValueType.TEXT;
				logger.severe("Failed value parse");
				logger.severe(e.toString());
			}
			Optional<?> originalValue = value;

			double multiplier = 1L;
			//If value was parsed correctly,
			// Multiply the value with its multiplier if given
			if (!Objects.equals(parameter.multiplier, "-")) {
				Optional<?> optionalMultiplier = ValueUtil.parse(parameter.multiplier, attributeType.getType());

				if (optionalMultiplier.isPresent()) {

					if (!ValueUtil.objectsEquals(optionalMultiplier.get(), multiplier)) {
						try {
							multiplier = (Double) optionalMultiplier.get();
						} catch (Exception e) {
							logger.info(e.toString());
						}
					}

					try {
						double valueNumber = (double) value.get();

						value = Optional.of(valueNumber * multiplier);
						//If the original value is unequal to the new (multiplied) value, then we have to also multiply the constraints

					} catch (Exception e) {
						logger.severe(parameterId + "Failed multiplier");
						logger.severe(e.toString());
						throw e;
					}
				}

				//possibly prepend the unit with the string "custom."? So that it matches the predefined format
				//Add on its units
				if (!Objects.equals(parameter.units, "-")) {
					try {
						MetaItem<String[]> units = new MetaItem<>(MetaItemType.UNITS);
						units.setValue(Constants.units(parameter.units));
						//                            Error when deploying: https://i.imgur.com/4IihWC3.png
						//                            metaMap.add(units);
					} catch (Exception e) {
						logger.severe(parameterId + "Failed units");
						logger.severe(e.toString());
						throw e;
					}
				}
			}
			//Add on its constraints (min, max)
			if (ValueUtil.isNumber(attributeType.getType())) {
				Optional<?> min;
				Optional<?> max;

				try {
					//param id 17, 18 and 19, parsed as double, with min = -8000 and max = +8000 is being parsed as 0 and 0?
					//You cant do this to me Teltonika, why does parameter ID 237 with constraints (0, 1) have value 2 (and description says it can go up to 99)?
					min = ValueUtil.getValueCoerced(parameter.min, attributeType.getBaseType());
					max = ValueUtil.getValueCoerced(parameter.max, attributeType.getBaseType());
					if (min.isPresent() || max.isPresent()) {
						MetaItem<ValueConstraint[]> constraintsMeta = new MetaItem<>(CONSTRAINTS);
						List<ValueConstraint> constraintValues = new ArrayList<>();
						//Do I even have to multiply the constraints?
						double finalMultiplier = 1;
						//Try this with parameter 66 - why is it not properly storing the max constraint?
						//      It is calculated correctly, check with a debugger, but why is it not storing the data as required?
						if (multiplier != 1L) {
							finalMultiplier = multiplier;
						}

						// Check if the value is correctly within the constraints. If it's not, don't apply the constraint.
						// The only reason I am doing this is that the constraints are currently not programmatically, thus "seriously", set.
						// If it was properly defined, then parameter ID 237 wouldn't be inaccurate, let alone to this state.
						// Not to mention parsing errors (from example from the UDP/Codec 8 to MQTT/Codec JSON converter, look at accelerator axes)
						// THE AXES VARS OVERFLOW  - if I see that TCT has value -46, if I do 65535 minus the value I am given, then it gives me the real value
						if (min.isPresent()) {
							if (!((Double) value.get() < (Double) min.get())) {
								constraintValues.add(new ValueConstraint.Min((Double) min.get() * finalMultiplier));
							}
						}
						if (max.isPresent()) {
							if (!((Double) value.get() > (Double) max.get())) {
								constraintValues.add(new ValueConstraint.Max((Double) max.get() * finalMultiplier));
							}
						}

						ValueConstraint[] constraints = constraintValues.toArray(new ValueConstraint[0]);

						constraintsMeta.setValue(constraints);

						//TODO: Fix this, constraints for some reason are not being applied
						metaMap.add(constraintsMeta);
					}
				} catch (Exception e) {
					logger.severe(parameterId + "Failed constraints");
					logger.severe(e.toString());
					throw e;
				}
			}
			// Add on its label
			try {
				MetaItem<String> label = new MetaItem<>(MetaItemType.LABEL);
				label.setValue(parameter.propertyName);
				metaMap.add(label);
			} catch (Exception e) {
				logger.severe(parameter.propertyName + "Failed label");
				logger.severe(e.toString());
				throw e;
			}
			//Use the MetaMap to create an AttributeDescriptor
			AttributeDescriptor<?> attributeDescriptor = new AttributeDescriptor<>(parameterId, attributeType, metaMap);

			//Use the AttributeDescriptor and the Value to create a new Attribute
			Attribute<?> generatedAttribute = new Attribute(attributeDescriptor, value.get());
			// Add it to the AttributeMap
			attributes.addOrReplace(generatedAttribute);


		}
		//Timestamp grabbed from the device.
		attributes.get(VehicleAsset.LAST_CONTACT).ifPresent(lastContact -> {
			lastContact.getValue().ifPresent(value -> {
				attributes.forEach(attribute -> attribute.setTimestamp(value.getTime()));
			});
		});

		// Store data points, allow use for rules, and don't allow user parameter modification, for every attribute parsed
		try {
			attributes.forEach(attribute -> attribute.addOrReplaceMeta(
							new MetaItem<>(STORE_DATA_POINTS, true),
							new MetaItem<>(RULE_STATE, true),
							new MetaItem<>(READ_ONLY, true)
					)
			);
		} catch (Exception e) {
			logger.severe("Failed metaItems");
			logger.severe(e.toString());
			throw e;
		}

		return attributes;
	}

	private static GeoJSONPoint ParseLatLngToGeoJSONObject(String latlngString) {
		String regexPattern = "^([-+]?[0-8]?\\d(\\.\\d+)?|90(\\.0+)?),([-+]?(1?[0-7]?[0-9](\\.\\d+)?|180(\\.0+)?))$";

		Pattern r = Pattern.compile(regexPattern);
		Matcher m = r.matcher(latlngString);

		if (m.find()) {
			String latitude = m.group(1);
			String longitude = m.group(4);
			// Since the regex pattern was validated, there is no way for parsing these to throw a NumberFormatException.

			// GeoJSON requires the points in long-lat form, not lat-long
			return new GeoJSONPoint(Double.parseDouble(longitude), Double.parseDouble(latitude));

		} else {
			return null;
		}
	}

	private static ValueDescriptor<?> GetAttributeType(TeltonikaParameter parameter) {
		try {
			Double.valueOf(parameter.min);
			Double.valueOf(parameter.max);
			return ValueType.NUMBER;
		} catch (NumberFormatException e) {
			return switch (parameter.type) {
				case "Unsigned", "Signed", "unsigned", "UNSIGNED LONG INT" -> ValueType.NUMBER;
				default -> ValueType.TEXT;
			};
		}
	}


}
