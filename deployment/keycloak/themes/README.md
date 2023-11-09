## Custom Keycloak themes
Add a directory for each custom theme (the directory name is the theme name) and add the required theme templates within; you can use the default [openremote theme](https://github.com/openremote/keycloak/tree/main/themes/openremote) as a template alternatively refer to the keycloak themes documentation.

This themes directory can then be volume mapped into the keycloak container at `/deployment/keycloak/themes`.

### Development
To be able to see the custom theme in development (i.e. when using `dev-testing.yml` compose profile) you need to create a copy of `openremote/profile/dev-testing.yml` in the custom project `profile` directory.
