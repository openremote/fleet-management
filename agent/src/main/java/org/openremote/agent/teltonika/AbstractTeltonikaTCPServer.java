package org.openremote.agent.teltonika;

import org.openremote.agent.protocol.tcp.AbstractTCPServer;

import java.net.InetSocketAddress;
import java.util.logging.Logger;

public abstract class AbstractTeltonikaTCPServer<R,
        T extends AbstractTeltonikaProtocol<R, ?, T, U, V>,
        U extends AbstractTeltonikaAgent<U, T, V>,
        V extends TeltonikaAgentLink>
        extends AbstractTCPServer<R> {

    private static final Logger LOG = Logger.getLogger(AbstractTeltonikaTCPServer.class.getName());

    protected U agent;

    public AbstractTeltonikaTCPServer(InetSocketAddress localAddress, U agent) {
        super(localAddress);
        this.agent = agent;
        LOG.info("AbstractTeltonikaTCPServer created for address: " + localAddress + ", agent: " + agent.getId());
    }

}

