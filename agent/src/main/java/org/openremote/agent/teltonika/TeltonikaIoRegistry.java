package org.openremote.agent.teltonika;

import java.util.HashMap;
import java.util.Map;

/**
 * Registry for Teltonika AVL I/O parameter definitions.
 * Maps AVL IDs to human-readable parameter information.
 */
public class TeltonikaIoRegistry {

    public static class IoParameter {
        private final int id;
        private final String name;
        private final String description;
        private final String unit;
        private final IoType type;
        private final double multiplier;
        private final int offset;

        public IoParameter(int id, String name, String description, String unit, IoType type) {
            this(id, name, description, unit, type, 1.0, 0);
        }

        public IoParameter(int id, String name, String description, String unit, IoType type, double multiplier, int offset) {
            this.id = id;
            this.name = name;
            this.description = description;
            this.unit = unit;
            this.type = type;
            this.multiplier = multiplier;
            this.offset = offset;
        }

        public int getId() { return id; }
        public String getName() { return name; }
        public String getDescription() { return description; }
        public String getUnit() { return unit; }
        public IoType getType() { return type; }
        public double getMultiplier() { return multiplier; }
        public int getOffset() { return offset; }

        /**
         * Convert raw value to human-readable format
         */
        public String formatValue(long rawValue) {
            switch (type) {
                case BOOLEAN:
                    return rawValue == 1 ? "true" : "false";
                case VOLTAGE:
                    return String.format("%.3f %s", (rawValue + offset) * multiplier, unit);
                case TEMPERATURE:
                    return String.format("%.1f %s", (rawValue + offset) * multiplier, unit);
                case PERCENTAGE:
                    return String.format("%d %s", rawValue + offset, unit);
                case DISTANCE:
                case SPEED:
                case TIME:
                    return String.format("%.2f %s", (rawValue + offset) * multiplier, unit);
                case ENUM:
                case COUNT:
                default:
                    return String.format("%d %s", rawValue + offset, unit);
            }
        }

        public Object getTypedValue(long rawValue) {
            switch (type) {
                case BOOLEAN:
                    return rawValue == 1;
                case VOLTAGE:
                case TEMPERATURE:
                    return (rawValue + offset) * multiplier;
                case PERCENTAGE:
                case ENUM:
                case COUNT:
                default:
                    return rawValue + offset;
            }
        }
    }

    public enum IoType {
        BOOLEAN, VOLTAGE, TEMPERATURE, PERCENTAGE, DISTANCE, SPEED, TIME, ENUM, COUNT
    }

    private static final Map<Integer, IoParameter> PARAMETERS = new HashMap<>();

    static {
        // Digital inputs
        PARAMETERS.put(1, new IoParameter(1, "din1", "Digital Input 1", "", IoType.BOOLEAN));
        PARAMETERS.put(2, new IoParameter(2, "din2", "Digital Input 2", "", IoType.BOOLEAN));
        PARAMETERS.put(3, new IoParameter(3, "din3", "Digital Input 3", "", IoType.BOOLEAN));
        PARAMETERS.put(4, new IoParameter(4, "din4", "Digital Input 4", "", IoType.BOOLEAN));

        // Analog inputs
        PARAMETERS.put(9, new IoParameter(9, "adc1", "Analog Input 1", "V", IoType.VOLTAGE, 0.001, 0));
        PARAMETERS.put(10, new IoParameter(10, "adc2", "Analog Input 2", "V", IoType.VOLTAGE, 0.001, 0));

        // Digital outputs
        PARAMETERS.put(179, new IoParameter(179, "dout1", "Digital Output 1", "", IoType.BOOLEAN));
        PARAMETERS.put(180, new IoParameter(180, "dout2", "Digital Output 2", "", IoType.BOOLEAN));

        // System parameters
        PARAMETERS.put(21, new IoParameter(21, "gsm_signal", "GSM Signal Strength", "%", IoType.PERCENTAGE));
        PARAMETERS.put(24, new IoParameter(24, "speed", "Speed", "km/h", IoType.SPEED));
        PARAMETERS.put(66, new IoParameter(66, "ext_voltage", "External Voltage", "V", IoType.VOLTAGE, 0.001, 0));
        PARAMETERS.put(67, new IoParameter(67, "battery_voltage", "Battery Voltage", "V", IoType.VOLTAGE, 0.001, 0));
        PARAMETERS.put(68, new IoParameter(68, "battery_current", "Battery Current", "mA", IoType.COUNT));

        // Temperature sensors
        PARAMETERS.put(72, new IoParameter(72, "temp1", "Temperature Sensor 1", "°C", IoType.TEMPERATURE, 0.1, -400));
        PARAMETERS.put(73, new IoParameter(73, "temp2", "Temperature Sensor 2", "°C", IoType.TEMPERATURE, 0.1, -400));
        PARAMETERS.put(74, new IoParameter(74, "temp3", "Temperature Sensor 3", "°C", IoType.TEMPERATURE, 0.1, -400));
        PARAMETERS.put(75, new IoParameter(75, "temp4", "Temperature Sensor 4", "°C", IoType.TEMPERATURE, 0.1, -400));

        // Movement and acceleration
        PARAMETERS.put(239, new IoParameter(239, "ignition", "Ignition Status", "", IoType.BOOLEAN));
        PARAMETERS.put(240, new IoParameter(240, "movement", "Movement Sensor", "", IoType.BOOLEAN));
        PARAMETERS.put(241, new IoParameter(241, "active_gsm_operator", "Active GSM Operator", "", IoType.COUNT));

        // GPS status
        PARAMETERS.put(69, new IoParameter(69, "gps_power", "GPS Power", "", IoType.BOOLEAN));
        PARAMETERS.put(181, new IoParameter(181, "gps_level", "GPS Signal Level", "", IoType.COUNT));

        // OBD parameters (if available)
        PARAMETERS.put(389, new IoParameter(389, "engine_load", "Engine Load", "%", IoType.PERCENTAGE));
        PARAMETERS.put(390, new IoParameter(390, "coolant_temp", "Engine Coolant Temperature", "°C", IoType.TEMPERATURE, 1.0, -40));
        PARAMETERS.put(391, new IoParameter(391, "engine_rpm", "Engine RPM", "rpm", IoType.COUNT, 0.25, 0));
        PARAMETERS.put(392, new IoParameter(392, "vehicle_speed_obd", "Vehicle Speed (OBD)", "km/h", IoType.SPEED));

        // Fuel level
        PARAMETERS.put(16, new IoParameter(16, "fuel_level", "Fuel Level", "%", IoType.PERCENTAGE));

        // Driver behavior
        PARAMETERS.put(17, new IoParameter(17, "total_odometer", "Total Odometer", "km", IoType.DISTANCE, 0.1, 0));
        PARAMETERS.put(199, new IoParameter(199, "trip_odometer", "Trip Odometer", "m", IoType.DISTANCE));

        // Add more parameters as needed...
    }

    /**
     * Get parameter definition by AVL ID
     */
    public static IoParameter getParameter(int avlId) {
        return PARAMETERS.get(avlId);
    }

    /**
     * Check if parameter is known
     */
    public static boolean isKnown(int avlId) {
        return PARAMETERS.containsKey(avlId);
    }

    /**
     * Get all registered parameters
     */
    public static Map<Integer, IoParameter> getAllParameters() {
        return new HashMap<>(PARAMETERS);
    }

    /**
     * Add or update a parameter definition
     */
    public static void registerParameter(IoParameter parameter) {
        PARAMETERS.put(parameter.getId(), parameter);
    }
}
