package es.ua.sd.wm.engine.infrastructure.console;

import es.ua.sd.wm.engine.domain.LeakSensor;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.PrintStream;

/** Text menu of the Engine: lets a person simulate a leak and repair it. */
public class EngineConsole {

    private final LeakSensor sensor;
    private final BufferedReader input;
    private final PrintStream output;

    public EngineConsole(LeakSensor sensor, BufferedReader input, PrintStream output) {
        this.sensor = sensor;
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
                case "" -> printMenu();
                default -> output.println("Unknown option: " + option);
            }
        }
    }

    private void printMenu() {
        output.println("=== WM_WS_E ===");
        output.println("1 + Enter: simulate a leak");
        output.println("2 + Enter: repair the leak");
    }

    private String readLine() {
        try {
            return input.readLine();
        } catch (IOException failed) {
            return null;
        }
    }
}
