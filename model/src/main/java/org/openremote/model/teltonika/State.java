package org.openremote.model.teltonika;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import org.openremote.model.Constants;
import org.openremote.model.asset.Asset;
import org.openremote.model.attribute.Attribute;
import org.openremote.model.attribute.AttributeMap;
import org.openremote.model.attribute.MetaItem;
import org.openremote.model.attribute.MetaMap;
import org.openremote.model.custom.CarAsset;
import org.openremote.model.geo.GeoJSONPoint;
import org.openremote.model.util.ValueUtil;
import org.openremote.model.value.*;

import java.io.Serial;
import java.io.Serializable;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.*;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.openremote.model.value.MetaItemType.*;
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({
        "reported"
})
public class State implements Serializable {

//    public ReportedState reportedState;
    @JsonProperty("reported")
    public Map<String, Object> reported;

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append(State.class.getName()).append('@').append(Integer.toHexString(System.identityHashCode(this))).append('[');
        sb.append("reported");
        sb.append('=');
        sb.append(((this.reported == null) ? "<null>" : this.reported));
        sb.append(',');
        if (sb.charAt((sb.length() - 1)) == ',') {
            sb.setCharAt((sb.length() - 1), ']');
        } else {
            sb.append(']');
        }
        return sb.toString();
    }
}
