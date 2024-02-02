package org.openremote.model.teltonika;

import org.openremote.manager.custom.telematics.processors.teltonika.ITeltonikaPayload;

public class TeltonikaPayloadFactory {
	public static ITeltonikaPayload getPayload(String payload) {
		switch (payload) {
			case "data":
				return new TeltonikaDataPayload();
			case "response":
				return new TeltonikaResponsePayload();
			default:
				return null;
		}
	}
}
