package es.ua.sd.wm.common.event;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import es.ua.sd.wm.common.model.EndReason;
import es.ua.sd.wm.common.model.IrrigationSource;
import es.ua.sd.wm.common.model.StationState;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

class KafkaMessagesTest {

    private final JsonMapper json = JsonMapper.builder().build();

    @Test
    void everyMessageSurvivesTheTripToJsonAndBack() {
        IrrigationEnded summary = new IrrigationEnded("req-1", "WS-01", "FO-007", 42.5, 60, EndReason.TIME_LIMIT);
        Object[] messages = {
                new IrrigationRequest("req-1", IrrigationSource.OPERATOR, "FO-007", "WS-01", 60),
                new StationCommand("req-1", CommandType.START, "WS-01", "FO-007", 60, null),
                new OperatorNotification("req-1", "FO-007", "WS-01", NotificationType.IRRIGATION_ENDED, "Finished", summary),
                new StationStatus("WS-01", "Parque de Canalejas", StationState.AVAILABLE)
        };

        for (Object message : messages) {
            String text = json.writeValueAsString(message);
            assertThat(json.readValue(text, message.getClass())).isEqualTo(message);
        }
    }

    @Test
    void stationEventsCarryTheirKindInTheTypeField() {
        StationEvent telemetry = new Telemetry("req-1", "WS-01", "FO-007", 12.0, 24.0, 2);

        String text = json.writeValueAsString(telemetry);

        assertThat(text).contains("\"type\":\"TELEMETRY\"");
        assertThat(json.readValue(text, StationEvent.class)).isEqualTo(telemetry);
    }

    @Test
    void theReaderRecognisesEachKindOfStationEvent() {
        StationEvent acknowledgement = json.readValue(
                "{\"type\":\"COMMAND_ACK\",\"requestId\":\"req-1\",\"stationId\":\"WS-01\","
                        + "\"command\":\"START\",\"accepted\":true}",
                StationEvent.class);
        StationEvent ended = json.readValue(
                "{\"type\":\"IRRIGATION_ENDED\",\"requestId\":\"req-1\",\"stationId\":\"WS-01\","
                        + "\"totalLitres\":10.0,\"durationSeconds\":5,\"reason\":\"LEAK\"}",
                StationEvent.class);

        assertThat(acknowledgement).isInstanceOf(CommandAck.class);
        assertThat(ended).isInstanceOf(IrrigationEnded.class);
        assertThat(((IrrigationEnded) ended).reason()).isEqualTo(EndReason.LEAK);
    }

    @Test
    void operatorRequestNeedsAnOperatorId() {
        assertThatThrownBy(() -> new IrrigationRequest("req-1", IrrigationSource.OPERATOR, null, "WS-01", 60))
                .hasMessageContaining("Operator id");
    }

    @Test
    void stationRequestDoesNotNeedAnOperatorId() {
        IrrigationRequest request = new IrrigationRequest("req-1", IrrigationSource.STATION, null, "WS-01", 60);

        assertThat(request.operatorId()).isNull();
    }

    @Test
    void irrigationTimeMustBePositive() {
        assertThatThrownBy(() -> new IrrigationRequest("req-1", IrrigationSource.CENTRAL, null, "WS-01", 0))
                .hasMessageContaining("Limit seconds");
    }

    @Test
    void startCommandNeedsATimeLimitButStopDoesNot() {
        assertThatThrownBy(() -> new StationCommand("req-1", CommandType.START, "WS-01", null, null, null))
                .hasMessageContaining("Limit seconds");

        StationCommand stop = new StationCommand("req-1", CommandType.STOP, "WS-01", null, null, EndReason.LEAK);
        assertThat(stop.reason()).isEqualTo(EndReason.LEAK);
    }

    @Test
    void messagesWithoutStationAreRejected() {
        assertThatThrownBy(() -> new StationStatus("", "Parque", StationState.AVAILABLE))
                .hasMessageContaining("Station id");
    }
}
