package es.ua.sd.wm.common.protocol;

import es.ua.sd.wm.common.validation.Validator;

import java.io.EOFException;
import java.io.IOException;
import java.io.InterruptedIOException;
import java.io.OutputStream;
import java.io.PushbackInputStream;
import java.net.Socket;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/** Sends framed messages over a socket and confirms reception with ACK/NACK bytes. */
public final class FrameChannel implements AutoCloseable {

    private static final int ACK = 0x06;
    private static final int NACK = 0x15;
    private static final int CLOSED = -1;
    private static final int MAXIMUM_ATTEMPTS = 4;
    private static final int MAXIMUM_MESSAGE_LENGTH = 4096;
    private static final int DEFAULT_ACKNOWLEDGEMENT_TIMEOUT_MILLIS = 2000;

    private final Socket socket;
    private final PushbackInputStream input;
    private final OutputStream output;
    private final FrameReader frameReader;
    private final Consumer<String> messageHandler;
    private final Runnable closeHandler;
    private final int acknowledgementTimeoutMillis;
    private final ExecutorService handlers;
    private final Object sendLock = new Object();
    private final Object writeLock = new Object();

    private volatile BlockingQueue<Integer> pendingAcknowledgements;
    private volatile boolean closed;
    private volatile IOException failure;

    public FrameChannel(Socket socket, Consumer<String> messageHandler) throws IOException {
        this(socket, messageHandler, () -> { }, DEFAULT_ACKNOWLEDGEMENT_TIMEOUT_MILLIS);
    }

    public FrameChannel(Socket socket, Consumer<String> messageHandler, int acknowledgementTimeoutMillis)
            throws IOException {
        this(socket, messageHandler, () -> { }, acknowledgementTimeoutMillis);
    }

    /** closeHandler runs once when the connection ends, after every message already received. */
    public FrameChannel(Socket socket, Consumer<String> messageHandler, Runnable closeHandler) throws IOException {
        this(socket, messageHandler, closeHandler, DEFAULT_ACKNOWLEDGEMENT_TIMEOUT_MILLIS);
    }

    public FrameChannel(Socket socket, Consumer<String> messageHandler, Runnable closeHandler,
                        int acknowledgementTimeoutMillis) throws IOException {
        Validator.requireNonNull(socket, "Socket");
        Validator.requireNonNull(messageHandler, "Message handler");
        Validator.requireNonNull(closeHandler, "Close handler");
        Validator.requirePositive(acknowledgementTimeoutMillis, "Acknowledgement timeout");

        this.socket = socket;
        this.messageHandler = messageHandler;
        this.closeHandler = closeHandler;
        this.acknowledgementTimeoutMillis = acknowledgementTimeoutMillis;
        this.input = new PushbackInputStream(socket.getInputStream(), 1);
        this.output = socket.getOutputStream();
        this.frameReader = new FrameReader(input, MAXIMUM_MESSAGE_LENGTH);
        this.handlers = Executors.newSingleThreadExecutor(task -> new Thread(task, "frame-channel-handler"));

        new Thread(this::readLoop, "frame-channel-reader").start();
    }

    /** Waits for reception confirmation. A retry can deliver the same message more than once. */
    public void send(String message) throws IOException {
        Validator.requireMaximumLength(message, MAXIMUM_MESSAGE_LENGTH, "Message");
        byte[] frame = FrameCodec.encode(message);

        synchronized (sendLock) {
            ensureOpen();
            BlockingQueue<Integer> replies = new ArrayBlockingQueue<>(MAXIMUM_ATTEMPTS + 1);
            pendingAcknowledgements = replies;
            int receivedReplies = 0;

            try {
                for (int attempt = 1; attempt <= MAXIMUM_ATTEMPTS; attempt++) {
                    writeBytes(frame);
                    Integer reply = waitForReply(replies);
                    if (reply == null) {
                        continue;
                    }

                    receivedReplies++;
                    if (reply == ACK) {
                        awaitOutstandingReplies(replies, attempt - receivedReplies);
                        return;
                    }
                }

                throw new IOException("Message was not acknowledged after " + MAXIMUM_ATTEMPTS + " attempts");
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                InterruptedIOException interrupted = new InterruptedIOException("Interrupted while waiting for acknowledgement");
                interrupted.initCause(exception);
                terminate(interrupted);
                throw interrupted;
            } catch (IOException exception) {
                terminate(exception);
                throw exception;
            } finally {
                pendingAcknowledgements = null;
            }
        }
    }

    public boolean isClosed() {
        return closed;
    }

    @Override
    public void close() {
        terminate(new IOException("Channel was closed"));
    }

    private void readLoop() {
        try {
            while (!closed) {
                int firstByte = input.read();
                if (firstByte == -1) {
                    throw new EOFException("Peer closed the connection");
                }

                if (firstByte == ACK || firstByte == NACK) {
                    receiveAcknowledgement(firstByte);
                } else if (firstByte == FrameCodec.START_OF_TEXT) {
                    // Give STX back so the existing frame reader can read the whole frame.
                    input.unread(firstByte);
                    receiveFrame();
                } else {
                    throw new IOException("Expected STX, ACK or NACK");
                }
            }
        } catch (IOException exception) {
            terminate(exception);
        } catch (RuntimeException exception) {
            terminate(new IOException("Could not process incoming data", exception));
        }
    }

    private void receiveAcknowledgement(int acknowledgement) throws IOException {
        BlockingQueue<Integer> replies = pendingAcknowledgements;
        if (replies == null || !replies.offer(acknowledgement)) {
            throw new IOException("Received an unexpected acknowledgement");
        }
    }

    private void receiveFrame() throws IOException {
        byte[] frame = frameReader.readFrame();
        String message;
        try {
            message = FrameCodec.decode(frame);
        } catch (IllegalArgumentException exception) {
            writeBytes(new byte[]{NACK});
            return;
        }

        CompletableFuture<Void> acknowledgementAttempted = new CompletableFuture<>();
        // Admit delivery before ACK so a concurrent close cannot discard an acknowledged message.
        handlers.execute(() -> {
            acknowledgementAttempted.join();
            try {
                messageHandler.accept(message);
            } catch (RuntimeException exception) {
                terminate(new IOException("Message handler failed", exception));
            }
        });
        try {
            writeBytes(new byte[]{ACK});
        } finally {
            // Even if ACK cannot be written, the valid message already received is delivered.
            acknowledgementAttempted.complete(null);
        }
    }

    private Integer waitForReply(BlockingQueue<Integer> replies) throws InterruptedException, IOException {
        Integer reply = replies.poll(acknowledgementTimeoutMillis, TimeUnit.MILLISECONDS);
        ensureOpen();
        return reply;
    }

    private void awaitOutstandingReplies(BlockingQueue<Integer> replies, int remainingReplies)
            throws InterruptedException, IOException {
        // Bare ACK bytes have no ID. Consume replies to retries before starting another message.
        for (int index = 0; index < remainingReplies; index++) {
            if (waitForReply(replies) == null) {
                throw new IOException("An outstanding acknowledgement is missing; connection state is ambiguous");
            }
        }
    }

    private void writeBytes(byte[] bytes) throws IOException {
        synchronized (writeLock) {
            ensureOpen();
            output.write(bytes);
            output.flush();
        }
    }

    private void ensureOpen() throws IOException {
        if (closed) {
            throw new IOException("Channel is closed", failure);
        }
    }

    private synchronized void terminate(IOException cause) {
        if (closed) {
            return;
        }
        failure = cause;
        closed = true;

        BlockingQueue<Integer> replies = pendingAcknowledgements;
        if (replies != null) {
            // Wake a sender immediately; ensureOpen will report the recorded failure.
            replies.offer(CLOSED);
        }
        // Queued after the messages already accepted, so it always runs last.
        handlers.execute(this::runCloseHandler);
        // Already accepted messages must still reach their handlers after the socket closes.
        handlers.shutdown();
        try {
            socket.close();
        } catch (IOException exception) {
            cause.addSuppressed(exception);
        }
    }

    private void runCloseHandler() {
        try {
            closeHandler.run();
        } catch (RuntimeException ignored) {
            // The connection is already closed; a failing close handler cannot change that.
        }
    }
}
