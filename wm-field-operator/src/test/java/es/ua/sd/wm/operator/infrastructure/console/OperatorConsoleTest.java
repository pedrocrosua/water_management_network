package es.ua.sd.wm.operator.infrastructure.console;

import static org.assertj.core.api.Assertions.assertThat;

import es.ua.sd.wm.common.event.IrrigationRequest;
import es.ua.sd.wm.common.event.NotificationType;
import es.ua.sd.wm.common.event.OperatorNotification;
import es.ua.sd.wm.common.event.StationStatus;
import es.ua.sd.wm.common.model.StationState;
import es.ua.sd.wm.operator.application.IrrigationRequests;
import es.ua.sd.wm.operator.application.RequestFile;
import es.ua.sd.wm.operator.domain.StationBoard;
import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.io.StringReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** El menú de consola, con líneas escritas en lugar de un teclado real. */
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
    }

    @Test
    void optionTwoSaysSoWhenCentralDoesNotAnswer() {
        type("2\nWS-01\n60\n"); // en estos tests nadie contesta

        assertThat(screen.toString()).contains("Imposible conectar con WM_Central: no answer in 0 s");
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

    @Test
    void optionThreeRequestsTheIrrigationsOfAFile(@TempDir Path folder) throws IOException {
        Path file = folder.resolve("requests.csv");
        Files.writeString(file, "# stationId,limitSeconds\nWS-01,1\n");

        type("3\n" + file + "\n");

        assertThat(sent).extracting(IrrigationRequest::stationId).containsExactly("WS-01");
        assertThat(screen.toString()).contains("File:", "File finished. Requests sent: 1");
    }

    @Test
    void optionThreeWithAMissingFileSaysSo() {
        type("3\n/no/such/file.csv\n");

        assertThat(sent).isEmpty();
        assertThat(screen.toString()).contains("Cannot read the file /no/such/file.csv");
    }

    private void type(String lines) {
        console(lines).run();
    }

    private OperatorConsole console(String lines) {
        IrrigationRequests requests = new IrrigationRequests("FO-001", sent::add);
        // Nadie contesta en estos tests: sin margen, cada línea del fichero espera solo sus segundos.
        RequestFile requestFile = new RequestFile(requests, Duration.ZERO, Duration.ZERO);
        return new OperatorConsole("FO-001", requests, requestFile, board,
                new BufferedReader(new StringReader(lines)), new PrintStream(screen), Duration.ZERO);
    }
}
