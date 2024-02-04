package org.openremote.model.custom;

import org.openremote.model.teltonika.TeltonikaDataPayloadModel;
import org.openremote.model.teltonika.TeltonikaParameter;
import org.openremote.model.value.ValueDescriptor;

import java.util.HashMap;

public class CustomValueTypes {
    public static final ValueDescriptor<AssetStateDuration> ASSET_STATE_DURATION = new ValueDescriptor<>("AssetStateDuration", AssetStateDuration.class);
    public static final ValueDescriptor<TeltonikaParameter> TELTONIKA_PARAMETER = new ValueDescriptor<>("TeltonikaParameter", TeltonikaParameter.class);
    public static class TeltonikaParameterMap extends HashMap<Integer, TeltonikaParameter> {}

    public static final ValueDescriptor<TeltonikaParameterMap> TELTONIKA_PARAMETER_MAP = new ValueDescriptor<>("TeltonikaParameterMap", TeltonikaParameterMap.class);

    public static final ValueDescriptor<TeltonikaDataPayloadModel> TELTONIKA_PAYLOAD = new ValueDescriptor<>("TeltonikaPayload", TeltonikaDataPayloadModel.class);
}
