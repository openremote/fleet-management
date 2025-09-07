package org.openremote.agent.teltonika;

import org.openremote.agent.protocol.tcp.AbstractTCPServerAgent;
import org.openremote.model.value.AttributeDescriptor;
import org.openremote.model.value.ValueDescriptor;

import java.util.Optional;

public abstract class AbstractTeltonikaAgent<T extends AbstractTeltonikaAgent<T, U, V>, U extends AbstractTeltonikaProtocol<?, ?, U, T, V>, V extends TeltonikaAgentLink> extends AbstractTCPServerAgent<T, U, V> {

    public enum Codec {
        CODEC_8E,
        CODEC_8,
        CODEC_16
    }

    public static final ValueDescriptor<Codec> CODEC_DESCRIPTOR = new ValueDescriptor<>("Codec", Codec.class);
    public static final AttributeDescriptor<Codec> CODEC = new AttributeDescriptor<>("codec", CODEC_DESCRIPTOR);

    protected AbstractTeltonikaAgent() {
    }

    public AbstractTeltonikaAgent(String name) {
        super(name);
    }

    public Optional<Codec> getCodec() {
        return getAttributes().getValue(CODEC);
    }

    @SuppressWarnings("unchecked")
    public T setCodec(Codec value) {
        getAttributes().getOrCreate(CODEC).setValue(value);
        return (T) this;
    }
}
