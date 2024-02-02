
package org.openremote.manager.custom.telematics.processors.teltonika;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import org.openremote.container.timer.TimerService;
import org.openremote.manager.custom.telematics.processors.teltonika.helpers.TeltonikaParameterData;
import org.openremote.model.attribute.Attribute;
import org.openremote.model.attribute.AttributeMap;
import org.openremote.model.teltonika.TeltonikaConfiguration;
import org.openremote.model.teltonika.TeltonikaParameter;
import org.openremote.model.value.ValueType;

import java.util.Map;
import java.util.logging.Logger;

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({
        "RSP"
})
public class TeltonikaResponsePayload implements ITeltonikaPayload {

    @JsonProperty("RSP")
    public String rsp;
    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append(TeltonikaResponsePayload.class.getName()).append('@').append(Integer.toHexString(System.identityHashCode(this))).append('[');
        sb.append("rsp");
        sb.append('=');
        sb.append(((this.rsp == null)?"<null>":this.rsp));
        sb.append(',');
        if (sb.charAt((sb.length()- 1)) == ',') {
            sb.setCharAt((sb.length()- 1), ']');
        } else {
            sb.append(']');
        }
        return sb.toString();
    }

    @Override
    public Map<TeltonikaParameterData, Object> getAttributesFromPayload(TeltonikaConfiguration config, TimerService timerService) {
        TeltonikaParameterData parameter = new TeltonikaParameterData(
                "RSP",
                //Create fake teltonikaparameter data
                new TeltonikaParameter(-1, "RSP", "-", "ASCII", "-", "-", "-", "-", "Response to an SMS message", "0", "0")
        );
        return Map.of(parameter, rsp);
    }

    public AttributeMap getAttributes(Map<TeltonikaParameterData, Object> payloadMap, TeltonikaConfiguration config, Logger logger) {
        AttributeMap attributeMap = new AttributeMap();

        Attribute<String> attribute = config.getResponseAttribute();
        attribute.setValue((String) payloadMap.get(new TeltonikaParameterData("RSP", null)));
        attributeMap.put(attribute);
        return attributeMap;
    }
}
