/*
 * Copyright 2019, OpenRemote Inc.
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
package org.openremote.test.custom

import org.openremote.agent.custom.CustomAgent
import org.openremote.agent.custom.CustomProtocol
import org.openremote.manager.agent.AgentService
import org.openremote.manager.asset.AssetStorageService
import org.openremote.model.Constants
import org.openremote.model.asset.agent.ConnectionStatus
import org.openremote.test.ManagerContainerTrait
import spock.lang.Specification
import spock.util.concurrent.PollingConditions

class CustomTest extends Specification implements ManagerContainerTrait {

    def "Check custom agent and protocol"() {

        given: "expected conditions"
        def conditions = new PollingConditions(timeout: 10, delay: 1)

        when: "the container starts"
        def serverPort = findEphemeralPort()
        def container = startContainer(defaultConfig(serverPort), defaultServices())
        def assetStorageService = container.getService(AssetStorageService.class)
        def agentService = container.getService(AgentService.class)

        then: "the container should be running"
        conditions.eventually {
            assert container.isRunning()
        }

        when: "a custom agent asset is added"
        def agent = new CustomAgent("Test Agent")
            .setRealm(Constants.MASTER_REALM)
            .setOption(CustomAgent.Option.TWO)

        and: "the agent is added to the asset service"
        agent = assetStorageService.merge(agent)

        then: "the agent should be started"
        conditions.eventually {
            assert agentService.protocolInstanceMap.get(agent.id) != null
            assert (agentService.protocolInstanceMap.get(agent.id) as CustomProtocol).running
        }
    }
}
