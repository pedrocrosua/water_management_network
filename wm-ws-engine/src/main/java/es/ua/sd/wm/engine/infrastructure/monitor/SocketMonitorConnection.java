package es.ua.sd.wm.engine.infrastructure.monitor;

import es.ua.sd.wm.common.protocol.FrameChannel;
import es.ua.sd.wm.common.protocol.MessageType;
import es.ua.sd.wm.common.protocol.SocketMessage;
import es.ua.sd.wm.engine.application.port.out.MonitorConnection;
import es.ua.sd.wm.engine.domain.LeakSensor;
import es.ua.sd.wm.engine.domain.StationIdentity;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

/** Talks to the station's WM_WS_M over a socket, using the frame protocol. */
public class SocketMonitorConnection implements MonitorConnection {

    private static final int CONNECT_TIMEOUT_MILLIS = 3000;
    private static final int ID_ANSWER_TIMEOUT_SECONDS = 5;
    // The Monitor sends a PING every second: three silent seconds mean it is gone.
    private static final int SILENCE_TIMEOUT_MILLIS = 3000;

    private final String host;
    private final int port;
    private final LeakSensor sensor;
    private final BlockingQueue<SocketMessage> idAnswers = new LinkedBlockingQueue<>();

    private volatile FrameChannel channel;

    public SocketMonitorConnection(String host, int port, LeakSensor sensor) {
        this.host = host;
        this.port = port;
        this.sensor = sensor;
    }

    @Override
    public StationIdentity connect() throws IOException {
        idAnswers.clear();
        Socket socket = new Socket();
        socket.connect(new InetSocketAddress(host, port), CONNECT_TIMEOUT_MILLIS);
        socket.setSoTimeout(SILENCE_TIMEOUT_MILLIS);
        channel = new FrameChannel(socket, this::handle);

        channel.send(SocketMessage.of(MessageType.HELLO).format());
        SocketMessage answer = waitForIdAnswer();
        return new StationIdentity(answer.field(0), answer.field(1));
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
            case ID -> idAnswers.add(message);
            case PING -> answerPing(message.field(0));
            default -> throw new IllegalArgumentException("Unexpected message from WM_WS_M: " + text);
        }
    }

    /** PONG carries the same sequence number and KO while the sensor detects a leak. */
    private void answerPing(String sequence) {
        String status = sensor.isLeaking() ? "KO" : "OK";
        try {
            channel.send(SocketMessage.of(MessageType.PONG, sequence, status).format());
        } catch (IOException failed) {
            // FrameChannel closes itself on failure; MonitorLink notices and reconnects.
        }
    }

    private SocketMessage waitForIdAnswer() throws IOException {
        try {
            SocketMessage answer = idAnswers.poll(ID_ANSWER_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            if (answer == null) {
                throw new IOException("WM_WS_M did not answer HELLO");
            }
            return answer;
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted while waiting for WM_WS_M", interrupted);
        }
    }
}
