package es.ua.sd.wm.engine.infrastructure.monitor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import es.ua.sd.wm.common.protocol.FrameChannel;
import es.ua.sd.wm.engine.domain.LeakSensor;
import es.ua.sd.wm.engine.domain.StationIdentity;
import java.io.IOException;
import java.net.ServerSocket;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

/** El adaptador de socket contra un WM_WS_M falso que responde con el protocolo de tramas real. */
@Timeout(15)
class SocketMonitorConnectionTest {

    private final ServerSocket fakeMonitorSocket = new ServerSocket(0);
    private final BlockingQueue<String> receivedByMonitor = new LinkedBlockingQueue<>();
    private final CompletableFuture<FrameChannel> monitorSide = new CompletableFuture<>();
    private final LeakSensor sensor = new LeakSensor();
    private final SocketMonitorConnection connection =
            new SocketMonitorConnection("localhost", fakeMonitorSocket.getLocalPort(), sensor);

    SocketMonitorConnectionTest() throws IOException {
    }

    @AfterEach
    void closeEverything() throws IOException {
        connection.close();
        fakeMonitorSocket.close();
    }

    @Test
    void saysHelloAndAdoptsTheIdentityGivenByTheMonitor() throws Exception {
        answerHelloWith("ID#WS-01#Parque de Canalejas");

        StationIdentity station = connection.connect();

        assertThat(station).isEqualTo(new StationIdentity("WS-01", "Parque de Canalejas"));
        assertThat(receivedByMonitor.poll(2, TimeUnit.SECONDS)).isEqualTo("HELLO");
    }

    @Test
    void answersPingWithOkWhileThereIsNoLeak() throws Exception {
        answerHelloWith("ID#WS-01#Park");
        connection.connect();
        receivedByMonitor.poll(2, TimeUnit.SECONDS);

        monitorSide.join().send("PING#7");

        assertThat(receivedByMonitor.poll(2, TimeUnit.SECONDS)).isEqualTo("PONG#7#OK");
    }

    @Test
    void answersPingWithKoWhileLeaking() throws Exception {
        answerHelloWith("ID#WS-01#Park");
        connection.connect();
        receivedByMonitor.poll(2, TimeUnit.SECONDS);

        sensor.simulateLeak();
        monitorSide.join().send("PING#8");

        assertThat(receivedByMonitor.poll(2, TimeUnit.SECONDS)).isEqualTo("PONG#8#KO");
    }

    @Test
    void monitorThatNeverAnswersHelloIsAnError() {
        answerHelloWith(null);

        assertThatThrownBy(connection::connect)
                .isInstanceOf(IOException.class)
                .hasMessageContaining("did not answer HELLO");
    }

    @Test
    void unreachableMonitorIsAnError() throws IOException {
        fakeMonitorSocket.close();

        assertThatThrownBy(connection::connect).isInstanceOf(IOException.class);
    }

    /** Acepta al Engine y, cuando dice HELLO, responde con el mensaje indicado (null: nunca). */
    private void answerHelloWith(String reply) {
        Thread.ofPlatform().start(() -> {
            try {
                // El handler espera a monitorSide, así que puede responder incluso al primer mensaje.
                monitorSide.complete(new FrameChannel(fakeMonitorSocket.accept(), message -> {
                    receivedByMonitor.add(message);
                    if (message.equals("HELLO") && reply != null) {
                        sendQuietly(monitorSide.join(), reply);
                    }
                }));
            } catch (IOException ignored) {
                // Lo espera el test que cierra el socket del servidor.
            }
        });
    }

    private static void sendQuietly(FrameChannel channel, String message) {
        try {
            channel.send(message);
        } catch (IOException ignored) {
            // Las comprobaciones del test mostrarán qué ha fallado.
        }
    }
}
