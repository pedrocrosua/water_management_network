package es.ua.sd.wm.common.event;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

/**
 * Lo que publica el Engine de una estación en {@link Topics#STATION_TELEMETRY}.
 *
 * <p>Ese topic lleva tres tipos de mensaje; el campo "type" del JSON
 * (por ejemplo "type":"TELEMETRY") indica cuál es.
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
