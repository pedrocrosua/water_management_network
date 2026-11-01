package es.ua.sd.wm.operator.application;

import java.time.Duration;
import java.util.List;
import java.util.function.Consumer;

/**
 * CU-12: pide los riegos de un fichero, uno detrás de otro. Envía uno, espera a que termine (con
 * éxito o no), espera 4 segundos y pasa al siguiente.
 *
 * <p>Formato: una petición por línea, "stationId,limitSeconds". Se saltan las líneas vacías y las
 * que empiezan por #.
 */
public class RequestFile {

    /** La espera entre una petición y la siguiente que pide el enunciado. */
    public static final Duration PAUSE_BETWEEN_REQUESTS = Duration.ofSeconds(4);

    private final IrrigationRequests requests;
    private final Duration pause;
    // Margen sobre la duración del riego: validar, abrir la válvula y avisar del final.
    private final Duration extraWait;

    public RequestFile(IrrigationRequests requests, Duration pause, Duration extraWait) {
        this.requests = requests;
        this.pause = pause;
        this.extraWait = extraWait;
    }

    /** Pide las líneas en orden. show recibe cada mensaje para el operario. */
    public void run(List<String> lines, Consumer<String> show) throws InterruptedException {
        int sent = 0;
        for (int number = 1; number <= lines.size(); number++) {
            String line = lines.get(number - 1).trim();
            if (line.isEmpty() || line.startsWith("#")) {
                continue;
            }
            String[] fields = line.split(",");
            int seconds = fields.length == 2 ? readSeconds(fields[1]) : 0;
            String stationId = fields[0].trim();
            if (seconds <= 0 || stationId.isEmpty()) {
                show.accept("Line " + number + " ignored: expected stationId,limitSeconds with seconds greater than zero");
                continue;
            }
            if (sent > 0) {
                Thread.sleep(pause); // la anterior ya terminó: se esperan los 4 segundos
            }
            show.accept("Line " + number + ": asking " + stationId + " for " + seconds + " s");
            boolean ended = requests.requestAndWait(stationId, seconds, Duration.ofSeconds(seconds).plus(extraWait));
            sent++;
            if (!ended) {
                show.accept("Imposible conectar con WM_Central: no answer to line " + number);
            }
        }
        show.accept("File finished. Requests sent: " + sent);
    }

    /** Los segundos de la línea, o 0 si no son un número entero. */
    private static int readSeconds(String text) {
        try {
            return Integer.parseInt(text.trim());
        } catch (NumberFormatException notANumber) {
            return 0;
        }
    }
}
