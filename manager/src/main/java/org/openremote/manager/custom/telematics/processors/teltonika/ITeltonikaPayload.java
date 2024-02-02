package org.openremote.manager.custom.telematics.processors.teltonika;

import com.fasterxml.jackson.core.JsonProcessingException;
import org.openremote.container.timer.TimerService;
import org.openremote.manager.custom.telematics.processors.teltonika.helpers.TeltonikaParameterData;
import org.openremote.model.asset.Asset;
import org.openremote.model.attribute.Attribute;
import org.openremote.model.attribute.AttributeMap;
import org.openremote.model.teltonika.TeltonikaConfiguration;

import java.util.Map;
import java.util.logging.Logger;

public interface ITeltonikaPayload {

	/**
	 * Returns list of attributes depending on the Teltonika JSON Payload.
	 * Uses the logic and results from parsing the Teltonika Parameter IDs.
	 *
	 * @return Map of {@link Attribute}s to be assigned to the {@link Asset}.
	 */
	Map<TeltonikaParameterData, Object> getAttributesFromPayload(TeltonikaConfiguration config, TimerService timerService) throws JsonProcessingException;

	AttributeMap getAttributes(Map<TeltonikaParameterData, Object> payloadMap, TeltonikaConfiguration config, Logger logger);
}
