package es.ua.sd.wm.monitor.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import es.ua.sd.wm.monitor.application.port.out.CentralConnection;
import es.ua.sd.wm.monitor.domain.StationIdentity;
import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

/** The monitoring loop with an in-memory WM_Central and very short intervals. */
@Timeout(10)
class StationMonitorTest {

    private static final Duration SHORT = Duration.ofMillis(20);

    private final FakeCentral central = new FakeCentral();
    private final StationMonitor monitor =
            new StationMonitor(new StationIdentity("WS-01", "Park"), central, SHORT, SHORT);
    private Thread running;

    @AfterEach
    void stopMonitor() throws InterruptedException {
        monitor.stop();
        if (running != null) {
            running.join(2000);
        }
    }

    @Test
    void authenticatesAndThenReportsHealthRegularly() {
        startMonitor();

        await().atMost(Duration.ofSeconds(2)).until(() -> central.healthMessages.get() >= 3);
        assertThat(central.connections.get()).isEqualTo(1);
    }

    @Test
    void connectsAgainWhenTheConnectionIsLost() {
        central.closeAfterHealthMessages = 2;

        startMonitor();

        await().atMost(Duration.ofSeconds(2)).until(() -> central.connections.get() >= 2);
    }

    @Test
    void keepsTryingWhileCentralIsUnreachable() {
        central.failedAttemptsBeforeAnswering = 3;

        startMonitor();

        await().atMost(Duration.ofSeconds(2)).until(() -> central.healthMessages.get() >= 1);
        assertThat(central.connections.get()).isEqualTo(4);
    }

    @Test
    void givesUpWhenCentralRejectsTheStation() throws InterruptedException {
        central.rejectStation = true;

        startMonitor();
        running.join(2000);

        assertThat(running.isAlive()).isFalse();
        assertThat(central.connections.get()).isEqualTo(1);
    }

    @Test
    void engineFaultIsReportedToCentralOnlyOnce() {
        startMonitorAndWaitUntilConnected();

        monitor.engineAnswered(false);
        monitor.engineAnswered(false);

        assertThat(central.reports).containsExactly("LEAK");
    }

    @Test
    void engineWorkingAgainClearsTheLeak() {
        startMonitorAndWaitUntilConnected();

        monitor.engineAnswered(false);
        monitor.engineAnswered(true);

        assertThat(central.reports).containsExactly("LEAK", "LEAK_CLEARED");
    }

    @Test
    void healthyEngineSendsNothingExtra() {
        startMonitorAndWaitUntilConnected();

        monitor.engineAnswered(true);

        assertThat(central.reports).isEmpty();
    }

    @Test
    void lostEngineIsReportedAsALeak() {
        startMonitorAndWaitUntilConnected();

        monitor.engineLost();

        assertThat(central.reports).containsExactly("LEAK");
    }

    @Test
    void leakDetectedWhileCentralIsUnreachableIsSentOnceConnected() {
        monitor.engineAnswered(false);

        startMonitorAndWaitUntilConnected();

        await().atMost(Duration.ofSeconds(2)).until(() -> central.reports.equals(List.of("LEAK")));
    }

    private void startMonitorAndWaitUntilConnected() {
        startMonitor();
        await().atMost(Duration.ofSeconds(2)).until(() -> central.healthMessages.get() >= 1);
    }

    private void startMonitor() {
        running = Thread.ofPlatform().start(() -> {
            try {
                monitor.run();
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }
        });
    }

    private static final class FakeCentral implements CentralConnection {

        final AtomicInteger connections = new AtomicInteger();
        final AtomicInteger healthMessages = new AtomicInteger();
        final List<String> reports = new CopyOnWriteArrayList<>();
        volatile int failedAttemptsBeforeAnswering;
        volatile int closeAfterHealthMessages = Integer.MAX_VALUE;
        volatile boolean rejectStation;
        private volatile boolean open;
        private int healthOnThisConnection;

        @Override
        public String connect(StationIdentity station, Consumer<String> stateListener) throws IOException {
            int attempt = connections.incrementAndGet();
            if (rejectStation) {
                throw new AuthenticationRejectedException("Invalid AUTH message");
            }
            if (attempt <= failedAttemptsBeforeAnswering) {
                throw new IOException("Connection refused");
            }
            open = true;
            healthOnThisConnection = 0;
            return "AVAILABLE";
        }

        @Override
        public void sendHealth() {
            healthMessages.incrementAndGet();
            if (++healthOnThisConnection >= closeAfterHealthMessages) {
                open = false;
            }
        }

        @Override
        public void reportLeak() {
            reports.add("LEAK");
        }

        @Override
        public void reportLeakCleared() {
            reports.add("LEAK_CLEARED");
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
