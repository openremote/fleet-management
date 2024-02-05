package telematics.teltonika;


import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.UnexpectedTypeException;
import org.openremote.model.teltonika.TeltonikaDataPayloadModel;

public class TeltonikaPayloadFactory {
	public static ITeltonikaPayload getPayload(String payload, String modelNumber) throws JsonProcessingException {
		ObjectMapper mapper = new ObjectMapper();
		JsonNode rootNode = new ObjectMapper().readTree(payload);
		if (rootNode.has("state")) {
			// This looks like a DataPayload.
			TeltonikaDataPayloadModel model = mapper.readValue(payload, TeltonikaDataPayloadModel.class);
			return new TeltonikaDataPayload(model.state, modelNumber);
		} else if (rootNode.has("RSP")) {
			// This looks like an SMSPayload.
			return  mapper.readValue(payload, TeltonikaResponsePayload.class);
		} else {
			throw new UnexpectedTypeException("Unknown type for data payload");
		}
	}
}
