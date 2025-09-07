package org.openremote.agent.teltonika;

import jakarta.persistence.Entity;
import org.openremote.model.asset.agent.AgentDescriptor;

@Entity
public class TeltonikaTcpAgent extends AbstractTeltonikaAgent<TeltonikaTcpAgent, TeltonikaTcpProtocol, TeltonikaAgentLink> {

    public static final AgentDescriptor<TeltonikaTcpAgent, TeltonikaTcpProtocol, TeltonikaAgentLink> DESCRIPTOR = new AgentDescriptor<>(
            TeltonikaTcpAgent.class, TeltonikaTcpProtocol.class, TeltonikaAgentLink.class
    );

    @SuppressWarnings("unused")
    protected TeltonikaTcpAgent() {
    }

    @SuppressWarnings("unused")
    public TeltonikaTcpAgent(String name) {
        super(name);
    }

    @Override
    @SuppressWarnings("unchecked")
    public TeltonikaTcpProtocol getProtocolInstance() {
        return new TeltonikaTcpProtocol(this);
    }
}
