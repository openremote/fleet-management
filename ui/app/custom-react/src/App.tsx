import React from "react";
import openremoteLogo from "../assets/openremote.svg";
import {InputType} from "@openremote/or-mwc-components/or-mwc-input";
import type {} from "@openremote/or-mwc-components/jsx";
import "./App.css";

function App() {
    return (
        <div className="App">
            <div id="logo">
                <img src={openremoteLogo} className="logo openremote" alt="OpenRemote logo"/>
            </div>
            <h2>"A React template for your custom app."</h2>
            <div id="content">
                <a href="https://docs.openremote.io" target="_blank" rel="noreferrer">
                    <or-mwc-input type={InputType.BUTTON} outlined label="View the documentation"></or-mwc-input>
                </a>
            </div>
        </div>
    );
}

export default App;
