package org.openremote.agent.teltonika;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.socket.SocketChannel;

import java.net.InetSocketAddress;
import java.util.Map;
import java.util.logging.Logger;

public class TeltonikaTcpServer extends AbstractTeltonikaTCPServer<byte[], TeltonikaTcpProtocol, TeltonikaTcpAgent, TeltonikaAgentLink> {

    private static final Logger LOG = Logger.getLogger(TeltonikaTcpServer.class.getName());

    public TeltonikaTcpServer(InetSocketAddress localAddress, TeltonikaTcpAgent agent) {
        super(localAddress, agent);
    }

    @Override
    protected void addDecoders(SocketChannel channel) {
        channel.pipeline().addLast("teltonika-decoder", new TeltonikaCodec8EDecoder());
    }

    @Override
    protected void addEncoders(SocketChannel channel) {
        channel.pipeline().addLast("teltonika-encoder", new TeltonikaCodec8EEncoder());
        channel.pipeline().addLast("teltonika-handler", new TeltonikaMessageHandler());
    }

    /**
     * Handler for processing decoded Teltonika messages
     */
    private class TeltonikaMessageHandler extends ChannelInboundHandlerAdapter {

        @Override
        public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
            if (msg instanceof TeltonikaCodec8EDecoder.TeltonikaMessage) {
                TeltonikaCodec8EDecoder.TeltonikaMessage teltonikaMsg = (TeltonikaCodec8EDecoder.TeltonikaMessage) msg;

                LOG.info("Received Teltonika message: " + teltonikaMsg);

                // Process each AVL record
                for (TeltonikaCodec8EDecoder.AvlRecord record : teltonikaMsg.getRecords()) {
                    processAvlRecord(record);
                }

                // Send acknowledgment back to the device
                TeltonikaCodec8EEncoder.TeltonikaAcknowledgment ack =
                    TeltonikaCodec8EEncoder.createAcknowledgment(teltonikaMsg.getRecords().size());
                ctx.writeAndFlush(ack);

            } else {
                super.channelRead(ctx, msg);
            }
        }

        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
            LOG.severe("Error in Teltonika message handler: " + cause.getMessage());
            ctx.close();
        }

        private void processAvlRecord(TeltonikaCodec8EDecoder.AvlRecord record) {
            LOG.info("Processing AVL record:");
            LOG.info("  Timestamp: " + new java.util.Date(record.getTimestamp()));
            LOG.info("  Priority: " + record.getPriority());
            LOG.info("  GPS: " + record.getGps());
            LOG.info("  Event I/O ID: " + record.getEventIoId());

            // Convert I/O values to human-readable format
            Map<String, TeltonikaIoConverter.ConvertedIoValue> convertedIo =
                TeltonikaIoConverter.convertAllIoValues(record.getIo());

            // Log important parameters
            Map<String, Object> importantParams =
                TeltonikaIoConverter.getImportantParameters(record.getIo());

            if (!importantParams.isEmpty()) {
                LOG.info("  Important Parameters:");
                for (Map.Entry<String, Object> entry : importantParams.entrySet()) {
                    LOG.info("    " + entry.getKey() + ": " + entry.getValue());
                }
            }

            // Log detailed I/O summary (for debugging)
            if (LOG.isLoggable(java.util.logging.Level.FINE)) {
                String ioSummary = TeltonikaIoConverter.createIoSummary(record.getIo());
                LOG.fine("  " + ioSummary);
            }

            // TODO: Here you can add custom logic to:
            // - Update asset attributes based on converted values
            // - Store GPS coordinates in database
            // - Trigger alerts based on parameter thresholds
            // - Send data to external systems

            // Example of accessing specific converted values:
            if (convertedIo.containsKey("ignition")) {
                boolean ignitionOn = (Boolean) convertedIo.get("ignition").getConvertedValue();
                LOG.info("  Vehicle ignition is: " + (ignitionOn ? "ON" : "OFF"));
            }

            if (convertedIo.containsKey("gsm_signal")) {
                int signalStrength = (Integer) convertedIo.get("gsm_signal").getConvertedValue();
                LOG.info("  GSM Signal Strength: " + signalStrength + "%");
            }
        }
    }
}
