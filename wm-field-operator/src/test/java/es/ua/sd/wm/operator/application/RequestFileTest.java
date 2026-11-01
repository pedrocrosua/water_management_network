package es.ua.sd.wm.operator.application;

import static org.assertj.core.api.Assertions.assertThat;

import es.ua.sd.wm.common.event.IrrigationRequest;
import es.ua.sd.wm.common.event.NotificationType;
import es.ua.sd.wm.common.event.OperatorNotification;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/** CU-12: peticiones leídas de un fichero, con un WM_Central de mentira que contesta al momento. */
class RequestFileTest {

    private final List<IrrigationRequest> sent = new ArrayList<>();
    private final List<Long> sentAtNanos = new ArrayList<>();
    private final List<String> screen = new ArrayList<>();
    private IrrigationRequests requests;

    @Test
    void everyRequestOfTheFileIsSentInOrderAfterThePreviousOneEnds() throws InterruptedException {
        centralAnswers(NotificationType.IRRIGATION_ENDED);

        new RequestFile(requests, Duration.ZERO, Duration.ofSeconds(5))
                .run(List.of("# stationId,limitSeconds", "", "WS-01,30", " WS-02 , 10 "), screen::add);

        assertThat(sent).extracting(IrrigationRequest::stationId).containsExactly("WS-01", "WS-02");
        assertThat(sent).extracting(IrrigationRequest::limitSeconds).containsExactly(30, 10);
        assertThat(screen).contains("File finished. Requests sent: 2");
    }

    @Test
    void deniedRequestAlsoCountsAsFinished() throws InterruptedException {
        centralAnswers(NotificationType.DENIED);

        new RequestFile(requests, Duration.ZERO, Duration.ofSeconds(5)).run(List.of("WS-01,30", "WS-02,10"), screen::add);

        assertThat(sent).hasSize(2);
        assertThat(screen).noneMatch(line -> line.contains("Imposible conectar"));
    }

    @Test
    void waitsThePauseBetweenOneRequestAndTheNext() throws InterruptedException {
        centralAnswers(NotificationType.IRRIGATION_ENDED);

        new RequestFile(requests, Duration.ofMillis(300), Duration.ofSeconds(5)).run(List.of("WS-01,30", "WS-02,10"), screen::add);

        assertThat(Duration.ofNanos(sentAtNanos.get(1) - sentAtNanos.get(0))).isGreaterThanOrEqualTo(Duration.ofMillis(300));
    }

    @Test
    void wrongLinesAreShownAndSkipped() throws InterruptedException {
        centralAnswers(NotificationType.IRRIGATION_ENDED);

        new RequestFile(requests, Duration.ZERO, Duration.ofSeconds(5))
                .run(List.of("WS-01", "WS-02,abc", "WS-03,-5", "WS-04,20"), screen::add);

        assertThat(sent).extracting(IrrigationRequest::stationId).containsExactly("WS-04");
        assertThat(screen).contains(
                "Line 1 ignored: expected stationId,limitSeconds with seconds greater than zero",
                "Line 2 ignored: expected stationId,limitSeconds with seconds greater than zero",
                "Line 3 ignored: expected stationId,limitSeconds with seconds greater than zero");
    }

    @Test
    void requestWithoutAnswerSaysCentralCannotBeReachedAndGoesOn() throws InterruptedException {
        requests = new IrrigationRequests("FO-001", sent::add); // nadie contesta

        new RequestFile(requests, Duration.ZERO, Duration.ZERO).run(List.of("WS-01,1", "WS-02,1"), screen::add);

        assertThat(sent).hasSize(2);
        assertThat(screen).contains("Imposible conectar con WM_Central: no answer to line 1");
    }

    @Test
    void theEndOfAnotherRequestDoesNotCount() throws InterruptedException {
        // Mientras se espera la línea, termina otra petición del mismo operario (por ejemplo, una de la opción 2).
        requests = new IrrigationRequests("FO-001", request -> {
            sent.add(request);
            requests.notificationReceived(new OperatorNotification("another-request", "FO-001",
                    "WS-02", NotificationType.IRRIGATION_ENDED, "answer", null));
        });

        new RequestFile(requests, Duration.ZERO, Duration.ZERO).run(List.of("WS-01,1"), screen::add);

        assertThat(screen).contains("Imposible conectar con WM_Central: no answer to line 1");
    }

    /** WM_Central de mentira: contesta a cada petición, en cuanto se envía, con el tipo indicado. */
    private void centralAnswers(NotificationType answer) {
        requests = new IrrigationRequests("FO-001", request -> {
            sent.add(request);
            sentAtNanos.add(System.nanoTime());
            requests.notificationReceived(new OperatorNotification(request.requestId(), "FO-001",
                    request.stationId(), answer, "answer", null));
        });
    }
}
