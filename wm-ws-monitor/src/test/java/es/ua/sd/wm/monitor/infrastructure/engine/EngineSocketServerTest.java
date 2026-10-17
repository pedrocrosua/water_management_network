package es.ua.sd.wm.monitor.infrastructure.engine;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import es.ua.sd.wm.common.protocol.FrameChannel;
import es.ua.sd.wm.monitor.application.port.in.EngineEvents;
import es.ua.sd.wm.monitor.domain.StationIdentity;
import java.io.IOException;
import java.net.Socket;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

/** A real server socket and a fake WM_WS_E that uses the real frame protocol. */
@Timeout(15)
class EngineSocketServerTest {

    private static final Duration PING_INTERVAL = Duration.ofMillis(200);

    private final RecordingEvents events = new RecordingEvents();
    private EngineSocketServer server;

    @BeforeEach
    void startServer() throws IOException {
        server = new EngineSocketServer(0, new StationIdentity("WS-01", "Park"), events, PING_INTERVAL);
        server.start();
    }

    @AfterEach
    void stopServer() {
        server.stop();
    }

    @Test
    void engineReceivesItsIdentityAfterHello() throws Exception {
        try (FakeEngine engine = new FakeEngine(null)) {
            engine.send("HELLO");

            assertThat(engine.nextMessage()).isEqualTo("ID#WS-01#Park");
        }
    }

    @Test
    void engineIsPingedWithIncreasingSequenceNumbers() throws Exception {
        try (FakeEngine engine = new FakeEngine("OK")) {
            engine.send("HELLO");
            engine.nextMessage();

            assertThat(engine.nextMessage()).isEqualTo("PING#1");
            assertThat(engine.nextMessage()).isEqualTo("PING#2");
        }
    }

    @Test
    void healthyAnswersAreReported() throws Exception {
        try (FakeEngine engine = new FakeEngine("OK")) {
            engine.send("HELLO");

            await().atMost(Duration.ofSeconds(2)).until(() -> events.received.contains("answered OK"));
        }
    }

    @Test
    void faultyAnswersAreReported() throws Exception {
        try (FakeEngine engine = new FakeEngine("KO")) {
            engine.send("HELLO");

            await().atMost(Duration.ofSeconds(2)).until(() -> events.received.contains("answered KO"));
        }
    }

    @Test
    void engineThatStopsAnsweringIsReportedLost() throws Exception {
        try (FakeEngine engine = new FakeEngine(null)) {
            engine.send("HELLO");

            // Three unanswered PINGs: about 600 ms with the short test interval.
            await().atLeast(Duration.ofMillis(400)).atMost(Duration.ofSeconds(3))
                    .until(() -> events.received.contains("lost"));
        }
    }

    @Test
    void closedConnectionIsReportedLost() throws Exception {
        FakeEngine engine = new FakeEngine("OK");
        engine.send("HELLO");
        engine.nextMessage();

        engine.close();

        await().atMost(Duration.ofSeconds(2)).until(() -> events.received.contains("lost"));
    }

    @Test
    void messageBeforeHelloClosesTheConnectionWithoutReportingAnything() throws Exception {
        try (FakeEngine engine = new FakeEngine("OK")) {
            engine.send("PONG#1#OK");

            await().atMost(Duration.ofSeconds(2)).until(engine::isClosed);
            Thread.sleep(300);
            assertThat(events.received).isEmpty();
        }
    }

    @Test
    void newEngineReplacesTheOldOneWithoutReportingItLost() throws Exception {
        try (FakeEngine oldEngine = new FakeEngine("OK"); FakeEngine newEngine = new FakeEngine("OK")) {
            oldEngine.send("HELLO");
            oldEngine.nextMessage();

            newEngine.send("HELLO");
            assertThat(newEngine.nextMessage()).isEqualTo("ID#WS-01#Park");

            await().atMost(Duration.ofSeconds(2)).until(oldEngine::isClosed);
            Thread.sleep(300);
            assertThat(events.received).doesNotContain("lost");
        }
    }

    private static final class RecordingEvents implements EngineEvents {

        final List<String> received = new CopyOnWriteArrayList<>();

        @Override
        public void engineAnswered(boolean healthy) {
            received.add(healthy ? "answered OK" : "answered KO");
        }

        @Override
        public void engineLost() {
            received.add("lost");
        }
    }

    /** Plays the role of a WM_WS_E. With a pongStatus it answers every PING, otherwise it stays silent. */
    private final class FakeEngine implements AutoCloseable {

        private final BlockingQueue<String> received = new LinkedBlockingQueue<>();
        private final String pongStatus;
        private final FrameChannel channel;

        FakeEngine(String pongStatus) throws IOException {
            this.pongStatus = pongStatus;
            channel = new FrameChannel(new Socket("localhost", server.getPort()), this::handle);
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

        private void handle(String message) {
            received.add(message);
            if (pongStatus != null && message.startsWith("PING#")) {
                String sequence = message.substring("PING#".length());
                try {
                    channel.send("PONG#" + sequence + "#" + pongStatus);
                } catch (IOException ignored) {
                    // The test checks what the server saw; a lost PONG is not relevant here.
                }
            }
        }
    }
}
