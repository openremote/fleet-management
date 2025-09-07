package org.openremote.agent.teltonika;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToByteEncoder;

import java.util.logging.Logger;

/**
 * Simple Teltonika Codec 8E encoder that sends acknowledgment responses
 * back to Teltonika devices after receiving data.
 */
public class TeltonikaCodec8EEncoder extends MessageToByteEncoder<TeltonikaCodec8EEncoder.TeltonikaAcknowledgment> {

    private static final Logger LOG = Logger.getLogger(TeltonikaCodec8EEncoder.class.getName());

    @Override
    protected void encode(ChannelHandlerContext ctx, TeltonikaAcknowledgment msg, ByteBuf out) throws Exception {
        // Teltonika devices expect an acknowledgment with the number of records processed
        // The acknowledgment is simply a 4-byte integer representing the number of records

        int numberOfRecords = msg.getNumberOfRecords();
        out.writeInt(numberOfRecords);

        LOG.info("Sent acknowledgment for " + numberOfRecords + " records to Teltonika device");
    }

    /**
     * Create a simple acknowledgment message
     */
    public static TeltonikaAcknowledgment createAcknowledgment(int numberOfRecords) {
        return new TeltonikaAcknowledgment(numberOfRecords);
    }

    /**
     * Simple acknowledgment message class
     */
    public static class TeltonikaAcknowledgment {
        private final int numberOfRecords;

        public TeltonikaAcknowledgment(int numberOfRecords) {
            this.numberOfRecords = numberOfRecords;
        }

        public int getNumberOfRecords() {
            return numberOfRecords;
        }
    }
}
