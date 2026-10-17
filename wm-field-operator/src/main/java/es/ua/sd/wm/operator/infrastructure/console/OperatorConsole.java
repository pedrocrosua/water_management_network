package es.ua.sd.wm.operator.infrastructure.console;

import es.ua.sd.wm.common.event.OperatorNotification;
import es.ua.sd.wm.common.event.StationStatus;
import es.ua.sd.wm.common.event.Telemetry;
import es.ua.sd.wm.operator.application.IrrigationRequests;
import es.ua.sd.wm.operator.domain.StationBoard;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.PrintStream;
import java.util.Locale;

/** Text menu of the operator: list the stations and request irrigations. */
public class OperatorConsole {

    private final String operatorId;
    private final IrrigationRequests requests;
    private final StationBoard board;
    private final BufferedReader input;
    private final PrintStream output;

    public OperatorConsole(String operatorId, IrrigationRequests requests, StationBoard board,
                           BufferedReader input, PrintStream output) {
        this.operatorId = operatorId;
        this.requests = requests;
        this.board = board;
        this.input = input;
        this.output = output;
    }

    /** Reads options until the input ends. */
    public void run() {
        printMenu();
        String option;
        while ((option = readLine()) != null) {
            switch (option.trim()) {
                case "1" -> listStations();
                case "2" -> requestIrrigation();
                case "" -> printMenu();
                default -> print("Unknown option: " + option);
            }
        }
    }

    /** Called from the Kafka thread whenever WM_Central reports progress of a request. */
    public void show(OperatorNotification notification) {
        print(">>> [" + notification.type() + "] " + notification.message());
    }

    /** Called from the Kafka thread every second while one of this operator's irrigations lasts. */
    public void showTelemetry(Telemetry telemetry) {
        print(String.format(Locale.ROOT, "    %s: %.1f L/min, %.1f L, %d s", telemetry.stationId(),
                telemetry.flowLitresPerMinute(), telemetry.accumulatedLitres(), telemetry.elapsedSeconds()));
    }

    private void listStations() {
        if (board.all().isEmpty()) {
            print("No station is known yet");
        }
        for (StationStatus station : board.all()) {
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
            // NumberFormatException is also an IllegalArgumentException.
            print("Seconds must be a positive whole number and the station id cannot be empty");
        }
    }

    private void printMenu() {
        print("=== WM_FO " + operatorId + " ===");
        print("1 + Enter: list stations");
        print("2 + Enter: request an irrigation");
    }

    /** Synchronized: menu lines and notifications from Kafka never get mixed on one line. */
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
