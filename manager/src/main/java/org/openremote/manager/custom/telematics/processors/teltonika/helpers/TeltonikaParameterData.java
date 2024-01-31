package org.openremote.manager.custom.telematics.processors.teltonika.helpers;

import org.openremote.model.teltonika.TeltonikaParameter;

import java.util.Map;

public class TeltonikaParameterData {
    String key;
    TeltonikaParameter value;

    public TeltonikaParameterData(String key, TeltonikaParameter value) {
        this.key = key;
        this.value = value;
    }

    public String getParameterId() {
        return key;
    }

    public TeltonikaParameter getParameter() {
        return value;
    }
}
