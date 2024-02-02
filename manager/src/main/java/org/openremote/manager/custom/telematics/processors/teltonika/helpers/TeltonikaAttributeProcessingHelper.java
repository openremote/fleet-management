package org.openremote.manager.custom.telematics.processors.teltonika.helpers;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.openremote.container.timer.TimerService;
import org.openremote.model.Constants;
import org.openremote.model.asset.Asset;
import org.openremote.model.attribute.Attribute;
import org.openremote.model.attribute.AttributeMap;
import org.openremote.model.attribute.MetaItem;
import org.openremote.model.attribute.MetaMap;
import org.openremote.model.custom.CarAsset;
import org.openremote.model.geo.GeoJSONPoint;
import org.openremote.model.teltonika.TeltonikaConfiguration;
import org.openremote.model.teltonika.TeltonikaDataPayload;
import org.openremote.model.teltonika.TeltonikaParameter;
import org.openremote.model.teltonika.TeltonikaResponsePayload;
import org.openremote.model.util.ValueUtil;
import org.openremote.model.value.*;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.*;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static org.openremote.model.value.MetaItemType.*;
import static org.openremote.model.value.MetaItemType.READ_ONLY;

public class TeltonikaAttributeProcessingHelper {


}
