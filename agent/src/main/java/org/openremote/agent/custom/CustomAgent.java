/*
 * Copyright 2017, OpenRemote Inc.
 *
 * See the CONTRIBUTORS.txt file in the distribution for a
 * full listing of individual contributors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package org.openremote.agent.custom;

import org.openremote.model.asset.Asset;
import org.openremote.model.asset.agent.Agent;
import org.openremote.model.asset.agent.AgentDescriptor;
import org.openremote.model.asset.agent.DefaultAgentLink;
import org.openremote.model.value.AttributeDescriptor;
import org.openremote.model.value.ValueDescriptor;

import jakarta.persistence.Entity;
import java.util.Optional;

/**
 * This is an example of a custom {@link Agent} type; this must be registered via an
 * {@link org.openremote.model.AssetModelProvider} and must conform to the same requirements as custom {@link Asset}s and
 * in addition the following requirements:
 *
 * <ul>
 * <li>Optionally add a custom {@link org.openremote.model.asset.agent.AgentLink} (the {@link Class#getSimpleName} must
 * be unique compared to all other registered {@link org.openremote.model.asset.agent.AgentLink}s)
 * <li>Must define a {@link org.openremote.model.asset.agent.Protocol} implementation that corresponds to this {@link Agent}
 * <li>Must have a public static final {@link org.openremote.model.asset.agent.AgentDescriptor} rather than an
 * {@link org.openremote.model.asset.AssetDescriptor}
 * </ul>
 */
@Entity
public class CustomAgent extends Agent<CustomAgent, CustomProtocol, DefaultAgentLink> {

    public enum Option {
        ONE,
        TWO,
        THREE
    };

    public static final ValueDescriptor<Option> OPTION_VALUE_DESCRIPTOR = new ValueDescriptor<>("customAgentOption", Option.class);

    public static final AttributeDescriptor<Option> OPTION_ATTRIBUTE_DESCRIPTOR = new AttributeDescriptor<>("option", OPTION_VALUE_DESCRIPTOR);

    public static final AgentDescriptor<CustomAgent, CustomProtocol, DefaultAgentLink> DESCRIPTOR = new AgentDescriptor<>(
        CustomAgent.class, CustomProtocol.class, DefaultAgentLink.class
    );

    protected CustomAgent() {
    }

    public CustomAgent(String name) {
        super(name);
    }

    @Override
    public CustomProtocol getProtocolInstance() {
        return new CustomProtocol(this);
    }

    public Optional<Option> getOption() {
        return getAttributes().getValue(OPTION_ATTRIBUTE_DESCRIPTOR);
    }

    public CustomAgent setOption(Option value) {
        getAttributes().getOrCreate(OPTION_ATTRIBUTE_DESCRIPTOR).setValue(value);
        return this;
    }
}

