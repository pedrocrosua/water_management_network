package es.ua.sd.wm.monitor.infrastructure.central;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import es.ua.sd.wm.common.protocol.FrameChannel;
import es.ua.sd.wm.monitor.application.port.out.CentralConnection.AuthenticationRejectedException;
import es.ua.sd.wm.monitor.domain.StationIdentity;
import java.io.IOException;
import java.net.ServerSocket;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

/** The socket adapter against a fake WM_Central that answers with the real frame protocol. */
@Timeout(10)
class SocketCentralConnectionTest {

    private static final StationIdentity STATION = new StationIdentity("WS-01", "Parque de Canalejas");

    private final ServerSocket fakeCentralSocket = new ServerSocket(0);
    private final BlockingQueue<String> receivedByCentral = new LinkedBlockingQueue<>();
    private final CompletableFuture<FrameChannel> centralSide = new CompletableFuture<>();
    private final SocketCentralConnection connection =
            new SocketCentralConnection("localhost", fakeCentralSocket.getLocalPort());

    SocketCentralConnectionTest() throws IOException {
    }

    @AfterEach
    void closeEverything() throws IOException {
        connection.close();
        fakeCentralSocket.close();
    }

    @Test
    void sendsAuthAndReturnsTheStateGivenByCentral() throws Exception {
        answerAuthWith("AUTH_OK#WS-01#AVAILABLE");

        String state = connection.connect(STATION, newState -> { });

        assertThat(state).isEqualTo("AVAILABLE");
        assertThat(receivedByCentral.poll(2, TimeUnit.SECONDS)).isEqualTo("AUTH#WS-01#Parque de Canalejas");
    }

    @Test
    void rejectedAuthenticationIsReported() {
        answerAuthWith("AUTH_KO#Invalid AUTH message");

        assertThatThrownBy(() -> connection.connect(STATION, newState -> { }))
                .isInstanceOf(AuthenticationRejectedException.class)
                .hasMessageContaining("Invalid AUTH message");
    }

    @Test
    void healthMessageCarriesTheStationId() throws Exception {
        answerAuthWith("AUTH_OK#WS-01#AVAILABLE");
        connection.connect(STATION, newState -> { });
        receivedByCentral.poll(2, TimeUnit.SECONDS);

        connection.sendHealth();

        assertThat(receivedByCentral.poll(2, TimeUnit.SECONDS)).isEqualTo("HEALTH#WS-01#OK");
    }

    @Test
    void stateChangesFromCentralReachTheListener() throws Exception {
        answerAuthWith("AUTH_OK#WS-01#AVAILABLE");
        BlockingQueue<String> states = new LinkedBlockingQueue<>();
        connection.connect(STATION, states::add);

        centralSide.get(2, TimeUnit.SECONDS).send("STATE#WS-01#OUT_OF_SERVICE");

        assertThat(states.poll(2, TimeUnit.SECONDS)).isEqualTo("OUT_OF_SERVICE");
    }

    @Test
    void unreachableCentralIsAnIoError() throws IOException {
        fakeCentralSocket.close();

        assertThatThrownBy(() -> connection.connect(STATION, newState -> { })).isInstanceOf(IOException.class);
    }

    /** Accepts the Monitor's connection and answers its first message (the AUTH). */
    private void answerAuthWith(String answer) {
        Thread.ofPlatform().start(() -> {
            try {
                FrameChannel[] channel = new FrameChannel[1];
                channel[0] = new FrameChannel(fakeCentralSocket.accept(), message -> {
                    receivedByCentral.add(message);
                    if (message.startsWith("AUTH#")) {
                        sendQuietly(centralSide.join(), answer);
                    }
                });
                centralSide.complete(channel[0]);
            } catch (IOException ignored) {
                // The test closed the server socket.
            }
        });
    }

    private static void sendQuietly(FrameChannel channel, String message) {
        try {
            channel.send(message);
        } catch (IOException ignored) {
            // The test is over.
        }
    }
}
