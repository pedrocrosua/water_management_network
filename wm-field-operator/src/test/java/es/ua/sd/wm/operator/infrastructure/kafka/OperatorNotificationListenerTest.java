package es.ua.sd.wm.operator.infrastructure.kafka;

import static org.assertj.core.api.Assertions.assertThat;

import es.ua.sd.wm.common.event.Topics;
import es.ua.sd.wm.operator.application.IrrigationRequests;
import es.ua.sd.wm.operator.application.RequestFile;
import es.ua.sd.wm.operator.domain.StationBoard;
import es.ua.sd.wm.operator.infrastructure.console.OperatorConsole;
import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.io.StringReader;
import java.time.Duration;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

/** Cada notificación llega a la pantalla y también al modo fichero, que espera el final de su petición. */
class OperatorNotificationListenerTest {

    private final ByteArrayOutputStream screen = new ByteArrayOutputStream();
    private OperatorNotificationListener listener;

    @Test
    void theEndOfARequestIsShownAndReachesTheFileMode() throws InterruptedException {
        // WM_Central de mentira: deniega cada petición en cuanto se envía.
        IrrigationRequests requests = new IrrigationRequests("FO-001",
                request -> listener.receive(record("FO-001", request.requestId())));
        listener = listenerOf(requests);

        boolean ended = requests.requestAndWait("WS-01", 10, Duration.ofSeconds(5));

        assertThat(ended).isTrue();
        assertThat(screen.toString()).contains(">>> [DENIED] WS-01 is not available");
    }

    @Test
    void notificationsOfOtherOperatorsAreIgnored() {
        IrrigationRequests requests = new IrrigationRequests("FO-001", request -> { });
        listener = listenerOf(requests);

        listener.receive(record("FO-002", "R1"));

        assertThat(screen.toString()).isEmpty();
    }

    private OperatorNotificationListener listenerOf(IrrigationRequests requests) {
        OperatorConsole console = new OperatorConsole("FO-001", requests,
                new RequestFile(requests, Duration.ZERO, Duration.ZERO), new StationBoard(),
                new BufferedReader(new StringReader("")), new PrintStream(screen), Duration.ZERO);
        return new OperatorNotificationListener("FO-001", console, requests, JsonMapper.builder().build());
    }

    /** Lo que llegaría de Kafka: la clave es el operario y el valor, la notificación en JSON. */
    private static ConsumerRecord<String, String> record(String operatorId, String requestId) {
        String json = """
                {"requestId":"%s","operatorId":"%s","stationId":"WS-01","type":"DENIED",
                 "message":"WS-01 is not available (OUT_OF_SERVICE)"}
                """.formatted(requestId, operatorId);
        return new ConsumerRecord<>(Topics.OPERATOR_NOTIFICATIONS, 0, 0L, operatorId, json);
    }
}
