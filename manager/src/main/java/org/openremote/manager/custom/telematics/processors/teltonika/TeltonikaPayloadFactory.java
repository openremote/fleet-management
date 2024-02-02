package org.openremote.manager.custom.telematics.processors.teltonika;


import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.util.JSONPObject;
import jakarta.validation.UnexpectedTypeException;

public class TeltonikaPayloadFactory {
	public static ITeltonikaPayload getPayload(String payload) throws JsonProcessingException {
		ObjectMapper mapper = new ObjectMapper();
		JsonNode rootNode = new ObjectMapper().readTree(payload);
		if (rootNode.has("state")) {
			// This looks like a DataPayload.
			return  mapper.readValue(payload, TeltonikaDataPayload.class);
		} else if (rootNode.has("RSP")) {
			// This looks like an SMSPayload.
			return  mapper.readValue(payload, TeltonikaResponsePayload.class);
		} else {
			throw new UnexpectedTypeException("Unknown type for data payload");
		}
	}
}
