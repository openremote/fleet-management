const util = require("@openremote/util");
const {rspack} = require("@rspack/core");

module.exports = (env, argv) => {

    const customConfigDir = env.config;
    const managerUrl = env.managerUrl;
    const keycloakUrl = env.keycloakUrl;
    const IS_DEV_SERVER = process.argv.find(arg => arg.includes("serve"));
    const config = util.getAppConfig(argv.mode, IS_DEV_SERVER, __dirname, managerUrl, keycloakUrl);

    if (IS_DEV_SERVER && customConfigDir) {
        console.log("CUSTOM_CONFIG_DIR: " + customConfigDir);
        // Try and include the static files in the specified config dir if we're in dev server mode
        config.plugins.push(new rspack.CopyRspackPlugin({
            patterns: [
                {
                    from: customConfigDir
                },
            ]
        }));
    }

    config.plugins.push(new rspack.CopyRspackPlugin({
        patterns: [
            { from: 'locales', to: 'locales' }
        ]
    }));

    // Add a custom base URL to resolve the config dir to the path of the dev server not root
    config.plugins.push(
        new rspack.DefinePlugin({
            CONFIG_URL_PREFIX: JSON.stringify(IS_DEV_SERVER && customConfigDir ? "/manager" : "")
        })
    );

    return config;
};
