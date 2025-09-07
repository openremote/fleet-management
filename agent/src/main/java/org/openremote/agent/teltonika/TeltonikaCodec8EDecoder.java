package org.openremote.agent.teltonika;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.ByteToMessageDecoder;

import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.logging.Logger;

/**
 * Simple Teltonika Codec 8E decoder that parses the message structure
 * and extracts the number of data records.
 * Also handles IMEI identification packets that devices send first.
 */
public class TeltonikaCodec8EDecoder extends ByteToMessageDecoder {

    private static final Logger LOG = Logger.getLogger(TeltonikaCodec8EDecoder.class.getName());

    // Codec 8E identifier
    private static final byte CODEC_8E = (byte) 0x8E;

    // Minimum message size: 4 bytes preamble + 4 bytes data length + 1 byte codec + 1 byte record count + 1 byte record count + 4 bytes CRC
    private static final int MIN_MESSAGE_SIZE = 15;

    // State tracking for each channel
    private boolean imeiReceived = false;
    private String deviceImei = null;

    @Override
    protected void decode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) throws Exception {
        // If we haven't received IMEI yet, check for it first
        if (!imeiReceived) {
            if (handleImeiPacket(ctx, in)) {
                imeiReceived = true;
                return; // Continue to next decode call for GPS data
            }
        }

        if (in.readableBytes() < MIN_MESSAGE_SIZE) {
            return; // Wait for more data
        }

        // Check if this looks like ASCII hex data
        if (isAsciiHex(in)) {
            LOG.info("Detected ASCII hex input, converting to binary");
            ByteBuf binaryData = convertHexStringToBinary(in);
            if (binaryData != null) {
                try {
                    decodeMessage(binaryData, out);
                } finally {
                    binaryData.release();
                }
            }
            return;
        }

        // Process as binary data
        decodeMessage(in, out);
    }

    private boolean isAsciiHex(ByteBuf in) {
        // Consider it hex only if the *entire* readable area looks hex-ish and length is even
        int len = in.readableBytes();
        if (len < 8 || (len % 2 != 0)) return false;
        int startIndex = in.readerIndex();
        try {
            for (int i = 0; i < Math.min(len, 64); i++) {
                byte b = in.getByte(startIndex + i);
                if (!((b >= '0' && b <= '9') || (b >= 'A' && b <= 'F') || (b >= 'a' && b <= 'f') || b == ' ' || b == '\n' || b == '\r' || b == '\t')) {
                    return false;
                }
            }
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private ByteBuf convertHexStringToBinary(ByteBuf hexBuf) {
        try {
            int hexLength = hexBuf.readableBytes();
            byte[] hexBytes = new byte[hexLength];
            hexBuf.getBytes(hexBuf.readerIndex(), hexBytes);
            String hexString = new String(hexBytes, StandardCharsets.US_ASCII);

            // Remove any whitespace and ensure even length
            hexString = hexString.replaceAll("\\s", "");
            if (hexString.length() % 2 != 0) {
                LOG.warning("Hex string has odd length: " + hexString.length());
                return null;
            }

            byte[] binaryData = new byte[hexString.length() / 2];
            for (int i = 0; i < binaryData.length; i++) {
                int hi = Character.digit(hexString.charAt(i * 2), 16);
                int lo = Character.digit(hexString.charAt(i * 2 + 1), 16);
                if (hi == -1 || lo == -1) {
                    LOG.warning("Invalid hex character encountered");
                    return null;
                }
                binaryData[i] = (byte) ((hi << 4) | lo);
            }

            // Mark all hex data as read
            hexBuf.readerIndex(hexBuf.readerIndex() + hexLength);

            return Unpooled.wrappedBuffer(binaryData);
        } catch (Exception e) {
            LOG.severe("Error converting hex string to binary: " + e.getMessage());
            return null;
        }
    }

    private void decodeMessage(ByteBuf in, List<Object> out) {
        // We might have multiple frames stuck together; loop while a full frame is present.
        while (true) {
            if (in.readableBytes() < MIN_MESSAGE_SIZE) {
                return; // not enough to even try
            }

            int frameStart = in.readerIndex();
            in.markReaderIndex();

            try {
                // Preamble (4 bytes) must be 0x00000000
                int preamble = in.readInt();
                if (preamble != 0) {
                    LOG.fine(String.format("Invalid preamble 0x%08X; skipping 1 byte.", preamble));
                    in.resetReaderIndex();
                    in.readByte(); // skip one and rescan
                    continue;
                }

                // Data Field Length (4 bytes) – from Codec ID through Number of Data 2 (inclusive)
                int dataLength = in.readInt();
                if (dataLength <= 0) {
                    LOG.fine("Non-positive dataLength; skipping 1 byte.");
                    in.readerIndex(frameStart + 1);
                    continue;
                }

                // Check we have the full Data Field (dataLength) + CRC (4)
                if (in.readableBytes() < dataLength + 4) {
                    // Not all bytes are available yet — rewind and wait for more
                    in.readerIndex(frameStart);
                    return;
                }

                // Slice for CRC computation (Codec ID..Number of Data 2)
                int dataStartIdx = in.readerIndex();
                ByteBuf dataSlice = in.slice(dataStartIdx, dataLength);

                // Read Codec ID
                byte codecId = in.readByte();
                if (codecId != CODEC_8E) {
                    LOG.fine(String.format("Unsupported codec: 0x%02X (expected 0x8E). Skipping 1 byte from frame start.", codecId));
                    in.readerIndex(frameStart + 1);
                    continue;
                }

                // Number of data 1
                int recordCount = in.readUnsignedByte();
                if (recordCount <= 0) {
                    LOG.fine("recordCount <= 0; skipping 1 byte.");
                    in.readerIndex(frameStart + 1);
                    continue;
                }

                List<AvlRecord> records = new ArrayList<>(recordCount);

                // Parse each AVL record
                for (int i = 0; i < recordCount; i++) {
                    AvlRecord rec = parseAvlRecord(in);
                    if (rec == null) {
                        // Failed to parse this record; resync one byte after frameStart
                        LOG.fine("Failed to parse AVL record; resyncing.");
                        in.readerIndex(frameStart + 1);
                        continue;
                    }
                    records.add(rec);
                }

                // Number of data 2 (must match)
                int finalRecordCount = in.readUnsignedByte();
                if (finalRecordCount != recordCount) {
                    LOG.fine(String.format("Record count mismatch: %d != %d; resync.", recordCount, finalRecordCount));
                    in.readerIndex(frameStart + 1);
                    continue;
                }

                // CRC-16/IBM over dataSlice
                int expectedCrc = in.readInt();
                int computedCrc = crc16IBM(dataSlice);
                if (expectedCrc != computedCrc) {
                    LOG.fine(String.format("CRC mismatch: expected 0x%04X, got 0x%04X; resync.", expectedCrc & 0xFFFF, computedCrc & 0xFFFF));
                    // Move forward a byte to avoid deadlock
                    in.readerIndex(frameStart + 1);
                    continue;
                }

                // Success: build message and push downstream
                TeltonikaMessage msg = new TeltonikaMessage(codecId & 0xFF, dataLength, records, deviceImei);
                out.add(msg);

                LOG.fine("Decoded Teltonika Codec8E message with " + records.size() + " record(s).");

                // Advance reader to end of this frame (8 header bytes + dataLength + 4 CRC)
                int frameTotal = 8 + dataLength + 4;
                in.readerIndex(frameStart + frameTotal);

                // loop for the next frame, if available
            } catch (IndexOutOfBoundsException e) {
                // Not enough bytes — reset and wait for more
                in.readerIndex(frameStart);
                return;
            } catch (Exception e) {
                LOG.severe("Error decoding Teltonika message: " + e.getMessage());
                // Skip a byte to avoid infinite loop on corrupted data
                in.readerIndex(frameStart + 1);
            }
        }
    }

    /**
     * Handle IMEI identification packet that Teltonika devices send first.
     * Format: 2-byte length + IMEI string (15 digits)
     */
    private boolean handleImeiPacket(ChannelHandlerContext ctx, ByteBuf in) {
        if (in.readableBytes() < 2) {
            return false; // Need at least 2 bytes for length
        }

        int startIndex = in.readerIndex();

        try {
            // Read length (2 bytes) - should be 15 for IMEI
            int length = in.readUnsignedShort();

            // IMEI should be 15 characters
            if (length != 15) {
                // This might not be an IMEI packet, could be ASCII hex data
                // Check if it looks like hex
                in.readerIndex(startIndex);
                if (isAsciiHex(in)) {
                    return false; // Let the main decoder handle it as hex
                }

                LOG.fine("Expected IMEI length 15, got: " + length);
                in.readerIndex(startIndex);
                return false;
            }

            if (in.readableBytes() < length) {
                in.readerIndex(startIndex);
                return false; // Not enough data yet
            }

            // Read IMEI string
            byte[] imeiBytes = new byte[length];
            in.readBytes(imeiBytes);
            String imei = new String(imeiBytes, StandardCharsets.US_ASCII);

            // Validate IMEI (should be 15 digits)
            if (!imei.matches("\\d{15}")) {
                LOG.warning("Invalid IMEI format: " + imei);
                in.readerIndex(startIndex);
                return false;
            }

            LOG.info("Received IMEI: " + imei);

            // Store the IMEI for this channel
            this.deviceImei = imei;

            // Send acknowledgment (0x01 for accept, 0x00 for reject)
            ByteBuf response = ctx.alloc().buffer(1);
            response.writeByte(0x01); // Accept
            ctx.writeAndFlush(response);

            LOG.info("Sent IMEI acknowledgment for device: " + imei);
            return true;

        } catch (Exception e) {
            LOG.warning("Error handling IMEI packet: " + e.getMessage());
            in.readerIndex(startIndex);
            return false;
        }
    }

    /** Parse one AVL record for Codec 8E */
    private AvlRecord parseAvlRecord(ByteBuf in) {
        int start = in.readerIndex();
        try {
            long timestamp = in.readLong();           // 8 bytes
            int priority = in.readUnsignedByte();     // 1 byte

            // GPS element (15 bytes): lon(4), lat(4), alt(2), angle(2), sats(1), speed(2)
            int lonRaw = in.readInt();
            int latRaw = in.readInt();
            short altitude = in.readShort();
            int angle = in.readUnsignedShort();
            short satellites = in.readUnsignedByte();
            int speed = in.readUnsignedShort();

            // Convert to degrees using 1e-7 scale per docs
            double lon = lonRaw / 1e7d;
            double lat = latRaw / 1e7d;

            GpsElement gps = new GpsElement(lon, lat, altitude, angle, satellites, speed);

            // IO element (Codec 8E)
            int eventIoId = in.readUnsignedShort();   // 2 bytes

            int totalIo = in.readUnsignedShort();     // N of total IO (2 bytes)

            Map<Integer, IoValue> io = new LinkedHashMap<>(Math.max(4, totalIo));

            // N1 of 1-byte IO
            int n1 = in.readUnsignedShort();
            for (int i = 0; i < n1; i++) {
                int id = in.readUnsignedShort();
                short v = in.readUnsignedByte();
                io.put(id, IoValue.ofUInt(v, 1));
            }

            // N2 of 2-byte IO
            int n2 = in.readUnsignedShort();
            for (int i = 0; i < n2; i++) {
                int id = in.readUnsignedShort();
                int v = in.readUnsignedShort();
                io.put(id, IoValue.ofUInt(v, 2));
            }

            // N4 of 4-byte IO
            int n4 = in.readUnsignedShort();
            for (int i = 0; i < n4; i++) {
                int id = in.readUnsignedShort();
                long v = in.readUnsignedInt(); // 4 bytes unsigned
                io.put(id, IoValue.ofULong(v, 4));
            }

            // N8 of 8-byte IO
            int n8 = in.readUnsignedShort();
            for (int i = 0; i < n8; i++) {
                int id = in.readUnsignedShort();
                long v = in.readLong(); // signed 8 bytes; most values fit here
                io.put(id, IoValue.ofInt64(v, 8));
            }

            // NX of X-byte IO (variable-length)
            int nx = in.readUnsignedShort();
            for (int i = 0; i < nx; i++) {
                int id = in.readUnsignedShort();
                int len = in.readUnsignedShort();
                if (len < 0 || len > 1024) { // sanity limit
                    throw new IllegalArgumentException("Variable IO length out of bounds: " + len);
                }
                byte[] data = new byte[len];
                in.readBytes(data);
                io.put(id, IoValue.ofBytes(data));
            }

            // Some devices put inconsistent "totalIo"; we trust the counts we actually read.
            return new AvlRecord(timestamp, priority, gps, eventIoId, totalIo, io);
        } catch (IndexOutOfBoundsException e) {
            // Not enough bytes for a full record; rewind and signal failure
            in.readerIndex(start);
            return null;
        }
    }

    /** CRC16/IBM (aka CRC-16-ANSI, polynomial 0xA001, init 0x0000, refin/refout true, xorout 0x0000) */
    private static int crc16IBM(ByteBuf buf) {
        int crc = 0x0000;
        for (int i = 0; i < buf.readableBytes(); i++) {
            int b = buf.getUnsignedByte(buf.readerIndex() + i);
            crc ^= b;
            for (int j = 0; j < 8; j++) {
                if ((crc & 0x0001) != 0) {
                    crc = (crc >>> 1) ^ 0xA001;
                } else {
                    crc = (crc >>> 1);
                }
            }
        }
        // Return as signed int carrying the 16-bit value; keep usage consistent with readInt()
        return crc & 0xFFFF;
    }

    // ======== Message / Model classes (compact POJOs) ========

    /** The decoded Teltonika message containing all AVL records in a frame. */
    public static class TeltonikaMessage {
        private final int codecId;
        private final int dataLength;
        private final List<AvlRecord> records;
        private final String imei;

        public TeltonikaMessage(int codecId, int dataLength, List<AvlRecord> records) {
            this(codecId, dataLength, records, null);
        }

        public TeltonikaMessage(int codecId, int dataLength, List<AvlRecord> records, String imei) {
            this.codecId = codecId;
            this.dataLength = dataLength;
            this.records = Collections.unmodifiableList(records);
            this.imei = imei;
        }

        public int getCodecId() { return codecId; }
        public int getDataLength() { return dataLength; }
        public List<AvlRecord> getRecords() { return records; }
        public String getImei() { return imei; }

        @Override public String toString() {
            return "TeltonikaMessage{codec=0x" + Integer.toHexString(codecId) +
                    ", dataLength=" + dataLength + ", records=" + records.size() +
                    ", imei=" + (imei != null ? imei : "unknown") + "}";
        }
    }

    /** One AVL record. */
    public static class AvlRecord {
        private final long timestamp;     // ms since epoch (UTC)
        private final int priority;       // 0/1/2
        private final GpsElement gps;
        private final int eventIoId;      // 0x0000 if not an event
        private final int totalIoDeclared;
        private final Map<Integer, IoValue> io; // AVL ID -> value

        public AvlRecord(long timestamp, int priority, GpsElement gps, int eventIoId,
                         int totalIoDeclared, Map<Integer, IoValue> io) {
            this.timestamp = timestamp;
            this.priority = priority;
            this.gps = gps;
            this.eventIoId = eventIoId;
            this.totalIoDeclared = totalIoDeclared;
            this.io = Collections.unmodifiableMap(new LinkedHashMap<>(io));
        }

        public long getTimestamp() { return timestamp; }
        public int getPriority() { return priority; }
        public GpsElement getGps() { return gps; }
        public int getEventIoId() { return eventIoId; }
        public int getTotalIoDeclared() { return totalIoDeclared; }
        public Map<Integer, IoValue> getIo() { return io; }

        @Override public String toString() {
            return "AvlRecord{ts=" + timestamp + ", prio=" + priority + ", gps=" + gps +
                    ", ioSize=" + io.size() + "}";
        }
    }

    /** GPS element parsed from each record. */
    public static class GpsElement {
        private final double longitude; // degrees
        private final double latitude;  // degrees
        private final short altitude;   // meters
        private final int angle;        // degrees
        private final short satellites; // count
        private final int speed;        // km/h (per Teltonika docs)

        public GpsElement(double longitude, double latitude, short altitude,
                          int angle, short satellites, int speed) {
            this.longitude = longitude;
            this.latitude = latitude;
            this.altitude = altitude;
            this.angle = angle;
            this.satellites = satellites;
            this.speed = speed;
        }

        public double getLongitude() { return longitude; }
        public double getLatitude() { return latitude; }
        public short getAltitude() { return altitude; }
        public int getAngle() { return angle; }
        public short getSatellites() { return satellites; }
        public int getSpeed() { return speed; }

        @Override public String toString() {
            return String.format(Locale.ROOT, "GPS{lon=%.7f,lat=%.7f,alt=%dm,ang=%d,sats=%d,spd=%d}",
                    longitude, latitude, altitude, angle, satellites, speed);
        }
    }

    /** IO value wrapper keeping both size and a typed representation. */
    public static class IoValue {
        public enum Kind { UINT, INT64, BYTES }

        private final Kind kind;
        private final int size;    // bytes: 1,2,4,8 or variable
        private final long uValue; // for 1/2/4-byte unsigned (fits in long) or 8-byte signed in sValue
        private final long sValue; // for 8-byte signed
        private final byte[] bytes;

        private IoValue(Kind kind, int size, long uValue, long sValue, byte[] bytes) {
            this.kind = kind; this.size = size; this.uValue = uValue; this.sValue = sValue; this.bytes = bytes;
        }

        public static IoValue ofUInt(long value, int size) { return new IoValue(Kind.UINT, size, value, 0L, null); }
        public static IoValue ofULong(long value, int size) { return new IoValue(Kind.UINT, size, value, 0L, null); }
        public static IoValue ofInt64(long value, int size) { return new IoValue(Kind.INT64, size, 0L, value, null); }
        public static IoValue ofBytes(byte[] data) { return new IoValue(Kind.BYTES, data.length, 0L, 0L, data); }

        public Kind getKind() { return kind; }
        public int getSize() { return size; }
        public long asUnsigned() { return uValue; }
        public long asInt64() { return sValue; }
        public byte[] asBytes() { return bytes; }

        @Override public String toString() {
            return switch (kind) {
                case UINT -> "u" + (size * 8) + "=" + uValue;
                case INT64 -> "i64=" + sValue;
                case BYTES -> "bytes[" + size + "]";
            };
        }
    }
}
