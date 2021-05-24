## Custom Keycloak themes
Add a directory for each custom theme (the directory name is the theme name) and add the required theme templates within; you can use the default [openremote theme](https://github.com/openremote/keycloak/tree/main/themes/openremote) as a template alternatively refer to the keycloak themes documentation.

This themes directory can then be volume mapped into the keycloak container at `/deployment/keycloak/themes`.

### Development
To be able to see the custom theme in development (i.e. when using `dev-testing.yml` compose profile) you need to create a custom `dev-testing.yml` in the custom project `profile` dir with the following content:

```yml
# OpenRemote v3
#
# Profile for doing IDE development work and running build tests.
#
# Please see profile/deploy.yml for configuration details for each service.
#
version: '2.4'

services:

  keycloak:
    extends:
      file: ../openremote/profile/deploy.yml
      service: keycloak
    volumes:
      # Map custom themes
      - ../deployment/keycloak/themes:/deployment/keycloak/themes
      - ../openremote/profile/disable-theme-cache.cli:/opt/jboss/startup-scripts/disable-theme-cache.cli
    # Access directly if needed on localhost
    ports:
      - "8081:8080"
    depends_on:
      postgresql:
        condition: service_healthy
    environment:
      # Use manager dev mode reverse proxy to access keycloak so manager and keycloak hosts match
      KEYCLOAK_FRONTEND_URL: ${KEYCLOAK_FRONTEND_URL:-http://localhost:8080/auth}

  postgresql:
    extends:
      file: ../openremote/profile/deploy.yml
      service: postgresql
    # Access directly if needed on localhost
    ports:
      - "5432:5432"

```

