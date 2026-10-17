package es.ua.sd.wm.engine.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import es.ua.sd.wm.engine.application.port.out.MonitorConnection;
import es.ua.sd.wm.engine.domain.StationIdentity;
import java.io.IOException;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

/** The connection loop with an in-memory WM_WS_M and very short intervals. */
@Timeout(10)
class MonitorLinkTest {

    private static final Duration SHORT = Duration.ofMillis(20);

    private final FakeMonitor monitor = new FakeMonitor();
    private final MonitorLink link = new MonitorLink(monitor, SHORT, SHORT);
    private Thread running;

    @AfterEach
    void stopLink() throws InterruptedException {
        link.stop();
        if (running != null) {
            running.join(2000);
        }
    }

    @Test
    void adoptsTheStationGivenByTheMonitor() {
        startLink();

        await().atMost(Duration.ofSeconds(2)).until(() -> link.currentStation().isPresent());
        assertThat(link.currentStation()).contains(new StationIdentity("WS-01", "Park"));
    }

    @Test
    void keepsTryingWhileTheMonitorIsUnreachable() {
        monitor.failedAttemptsBeforeAnswering = 3;

        startLink();

        await().atMost(Duration.ofSeconds(2)).until(() -> link.currentStation().isPresent());
        assertThat(monitor.connections.get()).isEqualTo(4);
    }

    @Test
    void connectsAgainWhenTheConnectionIsLost() {
        startLink();
        await().atMost(Duration.ofSeconds(2)).until(() -> monitor.connections.get() == 1);

        monitor.close();

        await().atMost(Duration.ofSeconds(2)).until(() -> monitor.connections.get() == 2);
    }

    private void startLink() {
        running = Thread.ofPlatform().start(() -> {
            try {
                link.run();
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }
        });
    }

    private static final class FakeMonitor implements MonitorConnection {

        final AtomicInteger connections = new AtomicInteger();
        volatile int failedAttemptsBeforeAnswering;
        private volatile boolean open;

        @Override
        public StationIdentity connect() throws IOException {
            if (connections.incrementAndGet() <= failedAttemptsBeforeAnswering) {
                throw new IOException("Connection refused");
            }
            open = true;
            return new StationIdentity("WS-01", "Park");
        }

        @Override
        public boolean isOpen() {
            return open;
        }

        @Override
        public void close() {
            open = false;
        }
    }
}
