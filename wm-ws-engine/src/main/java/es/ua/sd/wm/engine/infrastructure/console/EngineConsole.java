package es.ua.sd.wm.engine.infrastructure.console;

import es.ua.sd.wm.engine.application.IrrigationController;
import es.ua.sd.wm.engine.application.ManualIrrigation;
import es.ua.sd.wm.engine.domain.LeakSensor;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.PrintStream;

/** Menú de texto del Engine: simular una fuga, repararla, parar el riego en curso y pedir un riego manual. */
public class EngineConsole {

    private final LeakSensor sensor;
    private final IrrigationController irrigations;
    private final ManualIrrigation manual;
    private final BufferedReader input;
    private final PrintStream output;

    public EngineConsole(LeakSensor sensor, IrrigationController irrigations, ManualIrrigation manual,
                         BufferedReader input, PrintStream output) {
        this.sensor = sensor;
        this.irrigations = irrigations;
        this.manual = manual;
        this.input = input;
        this.output = output;
    }

    /** Lee opciones hasta que se acaba la entrada (por ejemplo, si se arranca sin terminal). */
    public void run() {
        printMenu();
        String option;
        while ((option = readLine()) != null) {
            switch (option.trim()) {
                case "1" -> {
                    sensor.simulateLeak();
                    output.println(">>> LEAK simulated: the Engine now answers KO");
                }
                case "2" -> {
                    sensor.repair();
                    output.println(">>> Leak repaired: the Engine answers OK again");
                }
                case "3" -> stopIrrigation();
                case "4" -> requestManualIrrigation();
                case "" -> printMenu();
                default -> output.println("Unknown option: " + option);
            }
        }
    }

    private void stopIrrigation() {
        if (!irrigations.stopFromStation()) {
            output.println("The station is not irrigating");
        }
    }

    /** Pregunta los segundos y pide el riego a WM_Central. */
    private void requestManualIrrigation() {
        output.println("Seconds:");
        String line = readLine();
        if (line == null) {
            return;
        }
        int seconds;
        try {
            seconds = Integer.parseInt(line.trim());
        } catch (NumberFormatException notANumber) {
            seconds = 0;
        }
        if (seconds <= 0) {
            output.println("Seconds must be a positive whole number");
            return;
        }
        output.println(manual.request(seconds));
    }

    private void printMenu() {
        output.println("=== WM_WS_E ===");
        output.println("1 + Enter: simulate a leak");
        output.println("2 + Enter: repair the leak");
        output.println("3 + Enter: stop the current irrigation");
        output.println("4 + Enter: irrigate manually (WM_Central has to authorize it)");
    }

    private String readLine() {
        try {
            return input.readLine();
        } catch (IOException failed) {
            return null;
        }
    }
}
