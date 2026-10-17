package es.ua.sd.wm.operator.infrastructure.console;

import static org.assertj.core.api.Assertions.assertThat;

import es.ua.sd.wm.common.event.IrrigationRequest;
import es.ua.sd.wm.common.event.NotificationType;
import es.ua.sd.wm.common.event.OperatorNotification;
import es.ua.sd.wm.common.event.StationStatus;
import es.ua.sd.wm.common.model.StationState;
import es.ua.sd.wm.operator.application.IrrigationRequests;
import es.ua.sd.wm.operator.domain.StationBoard;
import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/** The console menu, fed with typed lines instead of a real keyboard. */
class OperatorConsoleTest {

    private final List<IrrigationRequest> sent = new ArrayList<>();
    private final StationBoard board = new StationBoard();
    private final ByteArrayOutputStream screen = new ByteArrayOutputStream();

    @Test
    void optionOneListsTheStations() {
        board.update(new StationStatus("WS-01", "Parque de Canalejas", StationState.AVAILABLE));

        type("1\n");

        assertThat(screen.toString()).contains("WS-01  AVAILABLE  Parque de Canalejas");
    }

    @Test
    void optionTwoAsksForStationAndSecondsAndSendsTheRequest() {
        type("2\nWS-01\n60\n");

        assertThat(sent).singleElement().satisfies(request -> {
            assertThat(request.stationId()).isEqualTo("WS-01");
            assertThat(request.limitSeconds()).isEqualTo(60);
        });
        assertThat(screen.toString()).contains("Request sent");
    }

    @Test
    void secondsThatAreNotAPositiveNumberAreRejected() {
        type("2\nWS-01\nabc\n2\nWS-01\n-5\n");

        assertThat(sent).isEmpty();
        assertThat(screen.toString()).contains("Seconds must be a positive whole number");
    }

    @Test
    void notificationsAreShownWithTheirStep() {
        console("").show(new OperatorNotification("R1", "FO-001", "WS-01",
                NotificationType.AUTHORIZED, "WS-01 is irrigating", null));

        assertThat(screen.toString()).contains(">>> [AUTHORIZED] WS-01 is irrigating");
    }

    private void type(String lines) {
        console(lines).run();
    }

    private OperatorConsole console(String lines) {
        return new OperatorConsole("FO-001", new IrrigationRequests("FO-001", sent::add), board,
                new BufferedReader(new StringReader(lines)), new PrintStream(screen));
    }
}
