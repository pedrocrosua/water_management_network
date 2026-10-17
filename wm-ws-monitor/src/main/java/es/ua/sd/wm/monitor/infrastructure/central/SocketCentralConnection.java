package es.ua.sd.wm.monitor.infrastructure.central;

import es.ua.sd.wm.common.protocol.FrameChannel;
import es.ua.sd.wm.common.protocol.MessageType;
import es.ua.sd.wm.common.protocol.SocketMessage;
import es.ua.sd.wm.monitor.application.port.out.CentralConnection;
import es.ua.sd.wm.monitor.domain.StationIdentity;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/** Talks to WM_Central over a socket, using the frame protocol. */
public class SocketCentralConnection implements CentralConnection {

    private static final int CONNECT_TIMEOUT_MILLIS = 3000;
    private static final int AUTH_ANSWER_TIMEOUT_SECONDS = 5;

    private final String host;
    private final int port;

    private volatile FrameChannel channel;
    private volatile StationIdentity station;
    private volatile Consumer<String> stateListener;
    private final BlockingQueue<SocketMessage> authAnswers = new LinkedBlockingQueue<>();

    public SocketCentralConnection(String host, int port) {
        this.host = host;
        this.port = port;
    }

    @Override
    public String connect(StationIdentity station, Consumer<String> stateListener) throws IOException {
        this.station = station;
        this.stateListener = stateListener;
        authAnswers.clear();

        Socket socket = new Socket();
        socket.connect(new InetSocketAddress(host, port), CONNECT_TIMEOUT_MILLIS);
        channel = new FrameChannel(socket, this::handle);
        channel.send(SocketMessage.of(MessageType.AUTH, station.id(), station.location()).format());

        SocketMessage answer = waitForAuthAnswer();
        if (answer.type() == MessageType.AUTH_KO) {
            throw new AuthenticationRejectedException(answer.field(0));
        }
        return answer.field(1);
    }

    @Override
    public void sendHealth() throws IOException {
        channel.send(SocketMessage.of(MessageType.HEALTH, station.id(), "OK").format());
    }

    @Override
    public boolean isOpen() {
        FrameChannel current = channel;
        return current != null && !current.isClosed();
    }

    @Override
    public void close() {
        FrameChannel current = channel;
        if (current != null) {
            current.close();
        }
    }

    private void handle(String text) {
        SocketMessage message = SocketMessage.parse(text);
        switch (message.type()) {
            case AUTH_OK, AUTH_KO -> authAnswers.add(message);
            case STATE -> stateListener.accept(message.field(1));
            default -> throw new IllegalArgumentException("Unexpected message from WM_Central: " + text);
        }
    }

    private SocketMessage waitForAuthAnswer() throws IOException {
        try {
            SocketMessage answer = authAnswers.poll(AUTH_ANSWER_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            if (answer == null) {
                throw new IOException("WM_Central did not answer the AUTH message");
            }
            return answer;
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted while waiting for WM_Central", interrupted);
        }
    }
}
