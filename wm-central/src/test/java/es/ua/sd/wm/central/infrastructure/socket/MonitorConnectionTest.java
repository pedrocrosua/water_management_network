package es.ua.sd.wm.central.infrastructure.socket;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import es.ua.sd.wm.central.application.StationRegistry;
import es.ua.sd.wm.common.model.StationState;
import es.ua.sd.wm.common.protocol.FrameChannel;
import java.io.IOException;
import java.net.Socket;
import java.nio.file.Path;
import java.time.Duration;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * Starts the real WM_Central and connects fake Monitors to its socket server.
 * Each test uses its own station id so they do not affect each other.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class MonitorConnectionTest {

    @TempDir
    static Path databaseFolder;

    @DynamicPropertySource
    static void useTemporaryDatabaseAndAnyFreePort(DynamicPropertyRegistry properties) {
        properties.add("db.path", () -> databaseFolder.resolve("test.db").toString());
        properties.add("socket.port", () -> 0);
    }

    @Autowired
    private StationSocketServer server;

    @Autowired
    private StationRegistry registry;

    @Test
    void knownStationAuthenticatesAndBecomesAvailable() throws Exception {
        try (FakeMonitor monitor = new FakeMonitor()) {
            monitor.send("AUTH#WS-01#Parque de Canalejas");

            assertThat(monitor.nextMessage()).isEqualTo("AUTH_OK#WS-01#AVAILABLE");
            assertThat(stateOf("WS-01")).isEqualTo(StationState.AVAILABLE);
        }
    }

    @Test
    void unknownStationIsRegisteredWithItsLocation() throws Exception {
        try (FakeMonitor monitor = new FakeMonitor()) {
            monitor.send("AUTH#WS-50#Plaza Nueva");

            assertThat(monitor.nextMessage()).isEqualTo("AUTH_OK#WS-50#AVAILABLE");
            assertThat(registry.find("WS-50").orElseThrow().location()).isEqualTo("Plaza Nueva");
        }
    }

    @Test
    void closingTheConnectionMarksTheStationDisconnected() throws Exception {
        FakeMonitor monitor = new FakeMonitor();
        monitor.send("AUTH#WS-02#Jardines de Abril");
        monitor.nextMessage();

        monitor.close();

        await().atMost(Duration.ofSeconds(2)).until(() -> stateOf("WS-02") == StationState.DISCONNECTED);
    }

    @Test
    void silentMonitorIsDisconnectedAfterThreeSeconds() throws Exception {
        try (FakeMonitor monitor = new FakeMonitor()) {
            monitor.send("AUTH#WS-51#Silent park");
            monitor.nextMessage();

            await().atLeast(Duration.ofMillis(2500)).atMost(Duration.ofSeconds(5))
                    .until(() -> stateOf("WS-51") == StationState.DISCONNECTED);
        }
    }

    @Test
    void healthMessagesKeepTheStationConnected() throws Exception {
        try (FakeMonitor monitor = new FakeMonitor()) {
            monitor.send("AUTH#WS-52#Healthy park");
            monitor.nextMessage();

            for (int second = 0; second < 5; second++) {
                monitor.send("HEALTH#WS-52#OK");
                Thread.sleep(1000);
            }

            assertThat(stateOf("WS-52")).isEqualTo(StationState.AVAILABLE);
        }
    }

    @Test
    void authWithoutLocationIsRejected() throws Exception {
        try (FakeMonitor monitor = new FakeMonitor()) {
            monitor.send("AUTH#WS-53");

            assertThat(monitor.nextMessage()).isEqualTo("AUTH_KO#Invalid AUTH message");
            assertThat(registry.find("WS-53")).isEmpty();
        }
    }

    @Test
    void anyMessageBeforeAuthClosesTheConnection() throws Exception {
        try (FakeMonitor monitor = new FakeMonitor()) {
            monitor.send("HEALTH#WS-54#OK");

            await().atMost(Duration.ofSeconds(2)).until(monitor::isClosed);
        }
    }

    @Test
    void reconnectingMonitorReplacesTheOldConnectionAndStaysAvailable() throws Exception {
        try (FakeMonitor oldMonitor = new FakeMonitor(); FakeMonitor newMonitor = new FakeMonitor()) {
            oldMonitor.send("AUTH#WS-55#Park");
            oldMonitor.nextMessage();

            newMonitor.send("AUTH#WS-55#Park");
            assertThat(newMonitor.nextMessage()).isEqualTo("AUTH_OK#WS-55#AVAILABLE");

            await().atMost(Duration.ofSeconds(2)).until(oldMonitor::isClosed);
            Thread.sleep(300);
            assertThat(stateOf("WS-55")).isEqualTo(StationState.AVAILABLE);
        }
    }

    private StationState stateOf(String stationId) {
        return registry.find(stationId).orElseThrow().state();
    }

    /** Plays the role of a WM_WS_M: a real socket with the real frame protocol. */
    private final class FakeMonitor implements AutoCloseable {

        private final BlockingQueue<String> received = new LinkedBlockingQueue<>();
        private final FrameChannel channel;

        FakeMonitor() throws IOException {
            channel = new FrameChannel(new Socket("localhost", server.getPort()), received::add);
        }

        void send(String message) throws IOException {
            channel.send(message);
        }

        String nextMessage() throws InterruptedException {
            return received.poll(3, TimeUnit.SECONDS);
        }

        boolean isClosed() {
            return channel.isClosed();
        }

        @Override
        public void close() {
            channel.close();
        }
    }
}
