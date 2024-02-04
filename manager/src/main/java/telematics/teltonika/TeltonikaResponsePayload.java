
package telematics.teltonika;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import org.openremote.container.timer.TimerService;
import org.openremote.model.attribute.Attribute;
import org.openremote.model.attribute.AttributeMap;
import org.openremote.model.teltonika.TeltonikaConfiguration;
import org.openremote.model.teltonika.TeltonikaParameter;

import java.util.Map;
import java.util.logging.Logger;
/**
 * This class is used to represent the payload from a Teltonika device when responding to an SMS message.
 * It is used to parse the payload and extract the response from the device.
 * It arrives in the format of {@code {"RSP":"OK"}}.
 * <p>
 * It implements the {@code ITeltonikaPayload} interface, which is used to extract the payload's
 * attributes and create an attribute map.
 */
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
