package es.ua.sd.wm.common.event;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

/**
 * Anything a station Engine publishes on {@link Topics#STATION_TELEMETRY}.
 *
 * <p>Three kinds of message share that topic, so the JSON includes a "type" field
 * (for example "type":"TELEMETRY") that tells the reader which one it is.
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
@JsonSubTypes({
        @JsonSubTypes.Type(value = CommandAck.class, name = "COMMAND_ACK"),
        @JsonSubTypes.Type(value = Telemetry.class, name = "TELEMETRY"),
        @JsonSubTypes.Type(value = IrrigationEnded.class, name = "IRRIGATION_ENDED")
})
public sealed interface StationEvent permits CommandAck, Telemetry, IrrigationEnded {

    String stationId();
}
