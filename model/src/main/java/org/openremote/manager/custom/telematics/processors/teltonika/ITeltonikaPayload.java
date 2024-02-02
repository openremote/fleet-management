package org.openremote.manager.custom.telematics.processors.teltonika;

import org.openremote.container.timer.TimerService;
import org.openremote.model.asset.Asset;
import org.openremote.model.attribute.Attribute;
import org.openremote.model.attribute.AttributeMap;
import org.openremote.model.teltonika.TeltonikaConfiguration;
import org.openremote.model.teltonika.TeltonikaParameter;

import java.util.Map;
import java.util.logging.Logger;

public interface ITeltonikaPayload {

	/**
	 * Returns list of attributes depending on the Teltonika JSON Payload.
	 * Uses the logic and results from parsing the Teltonika Parameter IDs.
	 *
	 * @param payloadContent Payload coming from Teltonika device
	 * @return Map of {@link Attribute}s to be assigned to the {@link Asset}.
	 */
	AttributeMap getAttributesFromPayload(TeltonikaConfiguration config, TimerService timerService);

	AttributeMap getAttributes(Map<Map.Entry<String, TeltonikaParameter>, Object> payloadMap, Logger logger);

}
