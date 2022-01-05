/*
 * Copyright 2021, OpenRemote Inc.
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
package org.openremote.manager.setup.custom;

import org.openremote.manager.setup.EmptySetupTasks;
import org.openremote.manager.setup.Setup;
import org.openremote.model.Container;

import java.util.List;

import static org.openremote.container.util.MapAccess.getString;

public class CustomSetupTasks extends EmptySetupTasks {

    public static final String DEPLOYMENT_TYPE = "DEPLOYMENT_TYPE";
    public static final String DEPLOYMENT_TYPE_DEFAULT = "staging";

    @Override
    public List<Setup> createTasks(Container container) {

        String deploymentType = getString(container.getConfig(), DEPLOYMENT_TYPE, DEPLOYMENT_TYPE_DEFAULT);

        // Essential to call super to perform keycloak setup
        super.createTasks(container);

        // Add custom Setup task implementations here
        if ("staging".equals(deploymentType)) {
            addTask(new CustomKeycloakSetup(container));
            addTask(new CustomManagerSetup(container));
        } else if ("production".equals(deploymentType)) {
          // Specify production setup tasks
        }

        return getTasks();
    }
}
