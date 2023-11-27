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

import org.openremote.manager.setup.AbstractKeycloakSetup;
import org.openremote.model.Constants;
import org.openremote.model.Container;
import org.openremote.model.security.User;

public class CustomKeycloakSetup extends AbstractKeycloakSetup {

    protected User serviceUser;

    public CustomKeycloakSetup(Container container, boolean isProduction) {
        super(container);
    }

    @Override
    public void onStart() throws Exception {

        serviceUser = new User()
            .setServiceAccount(true)
            .setEnabled(true)
            .setUsername("serviceUser");

        serviceUser = keycloakProvider.createUpdateUser(Constants.MASTER_REALM, serviceUser, null, true);
    }
}
