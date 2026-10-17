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

/** The socket adapter against a fake WM_WS_M that answers with the real frame protocol. */
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

    /** Accepts the Engine and, when it says HELLO, replies with the given message (null: never). */
    private void answerHelloWith(String reply) {
        Thread.ofPlatform().start(() -> {
            try {
                // The handler waits for monitorSide, so it can reply even to the very first message.
                monitorSide.complete(new FrameChannel(fakeMonitorSocket.accept(), message -> {
                    receivedByMonitor.add(message);
                    if (message.equals("HELLO") && reply != null) {
                        sendQuietly(monitorSide.join(), reply);
                    }
                }));
            } catch (IOException ignored) {
                // The test that closes the server socket expects this.
            }
        });
    }

    private static void sendQuietly(FrameChannel channel, String message) {
        try {
            channel.send(message);
        } catch (IOException ignored) {
            // The assertions in the test will show what went wrong.
        }
    }
}
