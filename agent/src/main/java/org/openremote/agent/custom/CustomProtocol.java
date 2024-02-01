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

import org.openremote.agent.protocol.AbstractProtocol;
import org.openremote.model.Container;
import org.openremote.model.asset.agent.DefaultAgentLink;
import org.openremote.model.attribute.Attribute;
import org.openremote.model.attribute.AttributeEvent;
import org.openremote.model.syslog.SyslogCategory;

import java.util.logging.Logger;

import static org.openremote.model.syslog.SyslogCategory.PROTOCOL;

/**
 * A custom protocol that is used by the {@link CustomAgent}; there is a one-to-one mapping between an {@link
 * CustomAgent} {@link org.openremote.model.asset.Asset} and its' {@link org.openremote.model.asset.agent.Protocol}.
 * This example does nothing useful but is intended to show where protocol classes should be created.
 */
public class CustomProtocol extends AbstractProtocol<CustomAgent, DefaultAgentLink> {

    public static final String PROTOCOL_DISPLAY_NAME = "Custom";
    private static final Logger LOG = SyslogCategory.getLogger(PROTOCOL, CustomProtocol.class);
    protected boolean running;

    public CustomProtocol(CustomAgent agent) {
        super(agent);
    }

    @Override
    protected void doStart(Container container) throws Exception {
        running = true;
    }

    @Override
    protected void doStop(Container container) throws Exception {

    }

    @Override
    protected void doLinkAttribute(String assetId, Attribute<?> attribute, DefaultAgentLink agentLink) throws RuntimeException {

    }

    @Override
    protected void doUnlinkAttribute(String assetId, Attribute<?> attribute, DefaultAgentLink agentLink) {

    }

    @Override
    protected void doLinkedAttributeWrite(DefaultAgentLink agentLink, AttributeEvent event, Object processedValue) {

    }

    @Override
    public String getProtocolName() {
        return PROTOCOL_DISPLAY_NAME;
    }

    @Override
    public String getProtocolInstanceUri() {
        return "custom://" + agent.getOption();
    }
}
