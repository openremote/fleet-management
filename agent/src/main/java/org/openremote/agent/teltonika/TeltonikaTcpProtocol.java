package org.openremote.agent.teltonika;

import java.net.InetSocketAddress;
import java.util.logging.Logger;

public class TeltonikaTcpProtocol extends AbstractTeltonikaProtocol<byte[], TeltonikaTcpServer, TeltonikaTcpProtocol, TeltonikaTcpAgent, TeltonikaAgentLink> {

    private static final Logger LOG = Logger.getLogger(TeltonikaTcpProtocol.class.getName());

    public static final String PROTOCOL_NAME = "Teltonika TCP";

    public TeltonikaTcpProtocol(TeltonikaTcpAgent agent) {
        super(agent);
    }

    @Override
    public String getProtocolName() {
        return PROTOCOL_NAME;
    }

    @Override
    protected TeltonikaTcpServer createTcpServer(int port, String bindAddress, TeltonikaTcpAgent agent) {
        InetSocketAddress localAddress;

        if (bindAddress != null && !bindAddress.isEmpty()) {
            localAddress = new InetSocketAddress(bindAddress, port);
        } else {
            localAddress = new InetSocketAddress(port);
        }

        LOG.info("Creating Teltonika TCP server on: " + localAddress);
        return new TeltonikaTcpServer(localAddress, agent);
    }
}
