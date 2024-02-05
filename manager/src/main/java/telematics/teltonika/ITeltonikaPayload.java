package telematics.teltonika;

import com.fasterxml.jackson.core.JsonProcessingException;
import org.openremote.container.timer.TimerService;
import org.openremote.model.asset.Asset;
import org.openremote.model.attribute.Attribute;
import org.openremote.model.attribute.AttributeMap;

import java.util.Map;
import java.util.logging.Logger;

public interface ITeltonikaPayload {

	String getModelNumber();
	/**
	 * Returns list of attributes depending on the Teltonika JSON Payload.
	 * Uses the logic and results from parsing the Teltonika Parameter IDs.
	 *
	 * @return Map of {@link Attribute}s to be assigned to the {@link Asset}.
	 */
	Map<TeltonikaParameterData, Object> getAttributesFromPayload(TeltonikaConfiguration config, TimerService timerService) throws JsonProcessingException;

	AttributeMap getAttributes(Map<TeltonikaParameterData, Object> payloadMap, TeltonikaConfiguration config, Logger logger);
}
