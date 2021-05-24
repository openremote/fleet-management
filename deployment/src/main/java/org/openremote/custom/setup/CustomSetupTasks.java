package org.openremote.custom.setup;

import org.openremote.manager.setup.EmptySetupTasks;
import org.openremote.manager.setup.Setup;
import org.openremote.model.Container;

import java.util.List;
import java.util.logging.Logger;

import static org.openremote.container.util.MapAccess.getBoolean;

public class CustomSetupTasks extends EmptySetupTasks {

    private static final Logger LOG = Logger.getLogger(CustomSetupTasks.class.getName());

    @Override
    public List<Setup> createTasks(Container container) {

        // Essential to call super to perform keycloak setup
        super.createTasks(container);

        // Add custom Setup task implementations here
        //addTask(new CustomManagerSetup(container));

        return getTasks();
    }
}
