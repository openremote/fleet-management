package org.openremote.model.custom;

import org.openremote.model.teltonika.TeltonikaParameter;
import org.openremote.model.value.ValueDescriptor;

public class CustomValueTypes {
    public static final ValueDescriptor<AssetStateDuration> ASSET_STATE_DURATION = new ValueDescriptor<>("AssetStateDuration", AssetStateDuration.class);
    public static final ValueDescriptor<TeltonikaParameter> TELTONIKA_PARAMETER = new ValueDescriptor<>("TeltonikaParameter", TeltonikaParameter.class);
}
