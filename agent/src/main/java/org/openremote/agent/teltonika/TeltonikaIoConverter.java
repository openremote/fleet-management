package org.openremote.agent.teltonika;

import java.util.HashMap;
import java.util.Map;
import java.util.logging.Logger;

/**
 * Service for converting Teltonika I/O values to human-readable format
 */
public class TeltonikaIoConverter {

    private static final Logger LOG = Logger.getLogger(TeltonikaIoConverter.class.getName());

    /**
     * Represents a converted I/O value with both raw and human-readable formats
     */
    public static class ConvertedIoValue {
        private final int avlId;
        private final Object rawValue;
        private final Object convertedValue;
        private final String formattedValue;
        private final String parameterName;
        private final String description;
        private final String unit;
        private final boolean isKnown;

        public ConvertedIoValue(int avlId, Object rawValue, Object convertedValue,
                               String formattedValue, String parameterName,
                               String description, String unit, boolean isKnown) {
            this.avlId = avlId;
            this.rawValue = rawValue;
            this.convertedValue = convertedValue;
            this.formattedValue = formattedValue;
            this.parameterName = parameterName;
            this.description = description;
            this.unit = unit;
            this.isKnown = isKnown;
        }

        public int getAvlId() { return avlId; }
        public Object getRawValue() { return rawValue; }
        public Object getConvertedValue() { return convertedValue; }
        public String getFormattedValue() { return formattedValue; }
        public String getParameterName() { return parameterName; }
        public String getDescription() { return description; }
        public String getUnit() { return unit; }
        public boolean isKnown() { return isKnown; }

        @Override
        public String toString() {
            if (isKnown) {
                return String.format("%s (%s): %s", parameterName, description, formattedValue);
            } else {
                return String.format("AVL_%d: %s", avlId, rawValue);
            }
        }
    }

    /**
     * Convert a single I/O value to human-readable format
     */
    public static ConvertedIoValue convertIoValue(int avlId, TeltonikaCodec8EDecoder.IoValue ioValue) {
        TeltonikaIoRegistry.IoParameter parameter = TeltonikaIoRegistry.getParameter(avlId);

        // Extract raw value based on type
        long rawValue = switch (ioValue.getKind()) {
            case UINT -> ioValue.asUnsigned();
            case INT64 -> ioValue.asInt64();
            case BYTES -> {
                byte[] bytes = ioValue.asBytes();
                if (bytes.length <= 8) {
                    // Convert bytes to long for small byte arrays
                    long val = 0;
                    for (int i = 0; i < bytes.length; i++) {
                        val = (val << 8) | (bytes[i] & 0xFF);
                    }
                    yield val;
                } else {
                    yield 0L; // Large byte arrays handled separately
                }
            }
        };

        if (parameter != null) {
            // Known parameter - convert using registry
            Object convertedValue = parameter.getTypedValue(rawValue);
            String formattedValue = parameter.formatValue(rawValue);

            return new ConvertedIoValue(
                avlId, rawValue, convertedValue, formattedValue,
                parameter.getName(), parameter.getDescription(),
                parameter.getUnit(), true
            );
        } else {
            // Unknown parameter - provide raw value with generic formatting
            String formattedValue;
            Object convertedValue = rawValue;

            if (ioValue.getKind() == TeltonikaCodec8EDecoder.IoValue.Kind.BYTES) {
                byte[] bytes = ioValue.asBytes();
                formattedValue = bytesToHex(bytes);
                convertedValue = bytes;
            } else {
                formattedValue = String.valueOf(rawValue);
            }

            return new ConvertedIoValue(
                avlId, rawValue, convertedValue, formattedValue,
                "AVL_" + avlId, "Unknown parameter", "", false
            );
        }
    }

    /**
     * Convert all I/O values in a record to human-readable format
     */
    public static Map<String, ConvertedIoValue> convertAllIoValues(Map<Integer, TeltonikaCodec8EDecoder.IoValue> ioValues) {
        Map<String, ConvertedIoValue> converted = new HashMap<>();

        for (Map.Entry<Integer, TeltonikaCodec8EDecoder.IoValue> entry : ioValues.entrySet()) {
            ConvertedIoValue convertedValue = convertIoValue(entry.getKey(), entry.getValue());
            converted.put(convertedValue.getParameterName(), convertedValue);
        }

        return converted;
    }

    /**
     * Create a summary string of all I/O values
     */
    public static String createIoSummary(Map<Integer, TeltonikaCodec8EDecoder.IoValue> ioValues) {
        if (ioValues.isEmpty()) {
            return "No I/O data";
        }

        StringBuilder summary = new StringBuilder();
        summary.append("I/O Parameters (").append(ioValues.size()).append("):\n");

        for (Map.Entry<Integer, TeltonikaCodec8EDecoder.IoValue> entry : ioValues.entrySet()) {
            ConvertedIoValue converted = convertIoValue(entry.getKey(), entry.getValue());
            summary.append("  ").append(converted.toString()).append("\n");
        }

        return summary.toString();
    }

    /**
     * Get only the known/important parameters for logging
     */
    public static Map<String, Object> getImportantParameters(Map<Integer, TeltonikaCodec8EDecoder.IoValue> ioValues) {
        Map<String, Object> important = new HashMap<>();

        // Define important parameter IDs
        int[] importantIds = {
            1, 2, 3, 4,      // Digital inputs
            21,              // GSM signal
            24,              // Speed
            66, 67,          // Voltages
            72, 73, 74, 75,  // Temperature sensors
            239,             // Ignition
            240,             // Movement
            389, 390, 391, 392  // OBD parameters
        };

        for (int id : importantIds) {
            if (ioValues.containsKey(id)) {
                ConvertedIoValue converted = convertIoValue(id, ioValues.get(id));
                important.put(converted.getParameterName(), converted.getConvertedValue());
            }
        }

        return important;
    }

    /**
     * Convert byte array to hex string
     */
    private static String bytesToHex(byte[] bytes) {
        StringBuilder result = new StringBuilder();
        for (byte b : bytes) {
            result.append(String.format("%02X", b));
        }
        return "0x" + result.toString();
    }
}
