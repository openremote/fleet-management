package telematics.teltonika;

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

    //override equals to compare only keys
    @Override
    public boolean equals(Object obj) {
        if (obj == this) {
            return true;
        }
        if (!(obj instanceof TeltonikaParameterData)) {
            return false;
        }
        return this.key.equals(((TeltonikaParameterData) obj).key);
    }
}
