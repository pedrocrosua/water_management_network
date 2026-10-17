package es.ua.sd.wm.engine.infrastructure.console;

import es.ua.sd.wm.engine.application.IrrigationController;
import es.ua.sd.wm.engine.domain.LeakSensor;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.PrintStream;

/** Text menu of the Engine: simulate a leak, repair it and stop the current irrigation. */
public class EngineConsole {

    private final LeakSensor sensor;
    private final IrrigationController irrigations;
    private final BufferedReader input;
    private final PrintStream output;

    public EngineConsole(LeakSensor sensor, IrrigationController irrigations,
                         BufferedReader input, PrintStream output) {
        this.sensor = sensor;
        this.irrigations = irrigations;
        this.input = input;
        this.output = output;
    }

    /** Reads options until the input ends (for example, when started without a terminal). */
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

    private void printMenu() {
        output.println("=== WM_WS_E ===");
        output.println("1 + Enter: simulate a leak");
        output.println("2 + Enter: repair the leak");
        output.println("3 + Enter: stop the current irrigation");
    }

    private String readLine() {
        try {
            return input.readLine();
        } catch (IOException failed) {
            return null;
        }
    }
}
