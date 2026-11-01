package es.ua.sd.wm.operator.infrastructure.console;

import es.ua.sd.wm.common.event.OperatorNotification;
import es.ua.sd.wm.common.event.StationStatus;
import es.ua.sd.wm.common.event.Telemetry;
import es.ua.sd.wm.operator.application.IrrigationRequests;
import es.ua.sd.wm.operator.application.RequestFile;
import es.ua.sd.wm.operator.domain.StationBoard;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;

/** Menú de texto del operario: listar las estaciones y pedir riegos. */
public class OperatorConsole {

    private final String operatorId;
    private final IrrigationRequests requests;
    private final RequestFile requestFile;
    private final StationBoard board;
    private final BufferedReader input;
    private final PrintStream output;

    public OperatorConsole(String operatorId, IrrigationRequests requests, RequestFile requestFile,
                           StationBoard board, BufferedReader input, PrintStream output) {
        this.operatorId = operatorId;
        this.requests = requests;
        this.requestFile = requestFile;
        this.board = board;
        this.input = input;
        this.output = output;
    }

    /** Lee opciones hasta que se acaba la entrada. */
    public void run() {
        printMenu();
        String option;
        while ((option = readLine()) != null) {
            switch (option.trim()) {
                case "1" -> listStations();
                case "2" -> requestIrrigation();
                case "3" -> requestFromFile();
                case "" -> printMenu();
                default -> print("Unknown option: " + option);
            }
        }
    }

    /** Lo llama el hilo de Kafka cada vez que WM_Central informa del progreso de una petición. */
    public void show(OperatorNotification notification) {
        print(">>> [" + notification.type() + "] " + notification.message());
    }

    /** Lo llama el hilo de Kafka cada segundo mientras dura uno de los riegos de este operario. */
    public void showTelemetry(Telemetry telemetry) {
        print(String.format(Locale.ROOT, "    %s: %.1f L/min, %.1f L, %d s", telemetry.stationId(),
                telemetry.flowLitresPerMinute(), telemetry.accumulatedLitres(), telemetry.elapsedSeconds()));
    }

    private void listStations() {
        List<StationStatus> stations = board.all();
        if (stations.isEmpty()) {
            print("No station is known yet");
        }
        for (StationStatus station : stations) {
            print(station.stationId() + "  " + station.state() + "  " + station.location());
        }
    }

    private void requestIrrigation() {
        print("Station id:");
        String stationId = readLine();
        print("Seconds:");
        String seconds = readLine();
        if (stationId == null || seconds == null) {
            return;
        }
        try {
            requests.request(stationId.trim(), Integer.parseInt(seconds.trim()));
            print("Request sent; WM_Central will answer here");
        } catch (IllegalArgumentException invalid) {
            // NumberFormatException también es una IllegalArgumentException: este catch recoge los dos errores.
            print("Seconds must be a positive whole number and the station id cannot be empty");
        }
    }

    /** CU-12: el menú queda ocupado hasta terminar el fichero; las notificaciones se siguen viendo. */
    private void requestFromFile() {
        print("File:");
        String path = readLine();
        if (path == null) {
            return;
        }
        List<String> lines;
        try {
            lines = Files.readAllLines(Path.of(path.trim()));
        } catch (IOException | InvalidPathException cannotRead) {
            print("Cannot read the file " + path.trim());
            return;
        }
        try {
            requestFile.run(lines, this::print);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt(); // la aplicación se está cerrando
        }
    }

    private void printMenu() {
        print("=== WM_FO " + operatorId + " ===");
        print("1 + Enter: list stations");
        print("2 + Enter: request an irrigation");
        print("3 + Enter: request the irrigations of a file (for example requests/example.csv)");
    }

    /** synchronized: las líneas del menú y las notificaciones de Kafka nunca se mezclan en una línea. */
    private synchronized void print(String line) {
        output.println(line);
    }

    private String readLine() {
        try {
            return input.readLine();
        } catch (IOException failed) {
            return null;
        }
    }
}
