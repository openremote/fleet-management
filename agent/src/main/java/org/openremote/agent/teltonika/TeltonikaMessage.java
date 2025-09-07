package org.openremote.agent.teltonika;

/**
 * Simple Teltonika message representation containing basic parsed information
 */
public class TeltonikaMessage {

    private final int numberOfRecords;
    private final int dataLength;
    private final byte codecId;
    private final long timestamp;

    public TeltonikaMessage(int numberOfRecords, int dataLength, byte codecId) {
        this.numberOfRecords = numberOfRecords;
        this.dataLength = dataLength;
        this.codecId = codecId;
        this.timestamp = System.currentTimeMillis();
    }

    public int getNumberOfRecords() {
        return numberOfRecords;
    }

    public int getDataLength() {
        return dataLength;
    }

    public byte getCodecId() {
        return codecId;
    }

    public long getTimestamp() {
        return timestamp;
    }

    @Override
    public String toString() {
        return "TeltonikaMessage{" +
                "numberOfRecords=" + numberOfRecords +
                ", dataLength=" + dataLength +
                ", codecId=0x" + String.format("%02X", codecId) +
                ", timestamp=" + timestamp +
                '}';
    }
}
