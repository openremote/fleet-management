// Declare require method which we'll use for importing webpack resources (using ES6 imports will confuse typescript parser)
import {combineReducers, configureStore} from "@reduxjs/toolkit";
import "@openremote/or-app";
import {AnyAction, appReducer, AppStateKeyed, HeaderConfig, HeaderItem, OrApp, PageProvider, RealmAppConfig} from "@openremote/or-app";
import {headerItemAccount, headerItemLanguage, headerItemLogout, headerItemMap, headerItemAssets} from "@openremote/manager/headers";
import {pageAssetsReducer, pageAssetsProvider} from "@openremote/manager/pages/page-assets";
import {pageMapReducer, pageMapProvider} from "@openremote/manager/pages/page-map";
import "./pages/page-custom";
import {pageCustomProvider} from "./pages/page-custom";

const rootReducer = combineReducers({
    app: appReducer,
    map: pageMapReducer,
    assets: pageAssetsReducer
});

type RootState = ReturnType<typeof rootReducer>;

export const store = configureStore({
    reducer: rootReducer
});

const orApp = new OrApp(store);

export const DefaultPagesConfig: PageProvider<any>[] = [
    pageMapProvider(store), // Standard manager map page
    pageAssetsProvider(store), // Standard manager asset page
    pageCustomProvider(store) // Custom page
];

// A new header for our custom page
export function headerItemCustom<S extends AppStateKeyed, A extends AnyAction>(orApp: OrApp<S>): HeaderItem {
    return {
        icon: "rhombus-split",
        href: "custom1",
        text: "app:customPage",
    };
}

export const DefaultHeaderMainMenu: {[name: string]: HeaderItem} = {
    map: headerItemMap(orApp),
    assets: headerItemAssets(orApp),
    custom: headerItemCustom(orApp)
};

export const DefaultHeaderSecondaryMenu: {[name: string]: HeaderItem} = {
    language: headerItemLanguage(orApp),
    account: headerItemAccount(orApp),
    logout: headerItemLogout(orApp)
};

export const DefaultHeaderConfig: HeaderConfig = {
    mainMenu: Object.values(DefaultHeaderMainMenu),
    secondaryMenu: Object.values(DefaultHeaderSecondaryMenu)
};

export const DefaultRealmConfig: RealmAppConfig = {
    appTitle: "Custom App",
    header: DefaultHeaderConfig,
    styles: ":host > * {--or-app-color2: #F0F0F0; --or-app-color3: #22211f; --or-app-color4: #e3000a; --or-app-color5: #CCCCCC;}",
};

// Configure manager connection and i18next settings
orApp.managerConfig = {
    realm: "custom",
    loadTranslations: ["app", "or"]
};

// Configure app pages and per realm styling/settings
orApp.appConfig = {
    pages: [...DefaultPagesConfig],
    languages: {
        en: "english",
        nl: "dutch"
    },
    superUserHeader: DefaultHeaderConfig,
    realms: {
        default: {...DefaultRealmConfig, header: DefaultHeaderConfig}
    }
};

document.body.appendChild(orApp);
