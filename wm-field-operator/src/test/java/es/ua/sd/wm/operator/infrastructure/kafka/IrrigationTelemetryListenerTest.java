package es.ua.sd.wm.operator.infrastructure.kafka;

import static org.assertj.core.api.Assertions.assertThat;

import es.ua.sd.wm.operator.application.IrrigationRequests;
import es.ua.sd.wm.operator.domain.StationBoard;
import es.ua.sd.wm.operator.infrastructure.console.OperatorConsole;
import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.io.StringReader;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

/** The listener's filter, fed directly with the JSON text that would come from Kafka. */
class IrrigationTelemetryListenerTest {

    private final ByteArrayOutputStream screen = new ByteArrayOutputStream();
    private final OperatorConsole console = new OperatorConsole("FO-001",
            new IrrigationRequests("FO-001", request -> { }), new StationBoard(),
            new BufferedReader(new StringReader("")), new PrintStream(screen));
    private final IrrigationTelemetryListener listener =
            new IrrigationTelemetryListener("FO-001", console, JsonMapper.builder().build());

    @Test
    void showsTheTelemetryOfItsOwnIrrigations() {
        listener.receive(telemetryOf("FO-001"));

        assertThat(screen.toString()).contains("WS-01: 20.0 L/min, 5.5 L, 16 s");
    }

    @Test
    void ignoresTheIrrigationsOfOtherOperators() {
        listener.receive(telemetryOf("FO-002"));

        assertThat(screen.toString()).isEmpty();
    }

    @Test
    void ignoresOtherStationEvents() {
        listener.receive("""
                {"type":"COMMAND_ACK","requestId":"R1","stationId":"WS-01","command":"START","accepted":true}
                """);

        assertThat(screen.toString()).isEmpty();
    }

    private static String telemetryOf(String operatorId) {
        return """
                {"type":"TELEMETRY","requestId":"R1","stationId":"WS-01","operatorId":"%s",
                 "flowLitresPerMinute":20.0,"accumulatedLitres":5.5,"elapsedSeconds":16}
                """.formatted(operatorId);
    }
}
