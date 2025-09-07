package org.openremote.agent.teltonika;

import org.openremote.agent.protocol.tcp.AbstractTCPServerProtocol;
import org.openremote.model.Container;
import org.openremote.model.attribute.Attribute;
import org.openremote.model.attribute.AttributeEvent;

import java.util.logging.Logger;

public abstract class AbstractTeltonikaProtocol<R,
        S extends AbstractTeltonikaTCPServer<R, ?, ?, ?>,
        T extends AbstractTeltonikaProtocol<R, S, T, U, V>,
        U extends AbstractTeltonikaAgent<U, T, V>,
        V extends TeltonikaAgentLink>
        extends AbstractTCPServerProtocol<R, S, T, U, V> {

    private static final Logger LOG = Logger.getLogger(AbstractTeltonikaProtocol.class.getName());

    public static final String PROTOCOL_NAME_PREFIX = "Teltonika";

    public AbstractTeltonikaProtocol(U agent) {
        super(agent);
    }

    @Override
    public String getProtocolInstanceUri() {
        return "teltonika://" + getAgent().getId();
    }

    @Override
    protected void doStart(Container container) throws Exception {
        // Validate that codec is set
        if (getAgent().getCodec().isEmpty()) {
            LOG.warning("No codec specified for Teltonika agent, defaulting to CODEC_8E");
            getAgent().setCodec(AbstractTeltonikaAgent.Codec.CODEC_8E);
        }

        super.doStart(container);
        LOG.info("Teltonika protocol started successfully: " + getProtocolName());
    }

    @Override
    protected void doStop(Container container) throws Exception {
        super.doStop(container);
        LOG.info("Teltonika protocol stopped: " + getProtocolName());
    }

    @Override
    protected void doLinkAttribute(String assetId, Attribute<?> attribute, V agentLink) {
        // Handle attribute linking for Teltonika specific attributes
        LOG.info("Linking Teltonika attribute: " + attribute.getName() + " for asset: " + assetId);
    }

    @Override
    protected void doUnlinkAttribute(String assetId, Attribute<?> attribute, V agentLink) {
        // Handle attribute unlinking
        LOG.info("Unlinking Teltonika attribute: " + attribute.getName() + " for asset: " + assetId);
    }

    @Override
    protected void doLinkedAttributeWrite(V agentLink, AttributeEvent event, Object processedValue) {
        // Handle attribute write events if needed
        LOG.info("Processing Teltonika attribute write for: " + event.getRef().getName());
    }
}
