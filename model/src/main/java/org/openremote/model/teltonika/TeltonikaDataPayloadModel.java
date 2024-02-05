package org.openremote.model.teltonika;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.io.Serializable;

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({
		"payload"
})
public class TeltonikaDataPayloadModel implements Serializable {

	@JsonProperty("state")
	public State state;

	public TeltonikaDataPayloadModel() {
	}

	public TeltonikaDataPayloadModel(State state) {
		this.state = state;
	}

}
