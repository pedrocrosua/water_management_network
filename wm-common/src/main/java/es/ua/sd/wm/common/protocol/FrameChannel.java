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

/**
 * Envía y recibe mensajes por un socket con tramas STX-DATA-ETX-LRC.
 * Cada trama recibida se confirma con ACK (correcta) o NACK (dañada).
 *
 * <p>Hilos que intervienen:
 * <ul>
 *   <li>quien llama a {@link #send}: escribe la trama y espera su ACK;</li>
 *   <li>{@code frame-channel-reader}: lee el socket, recoge los ACK/NACK y confirma las tramas;</li>
 *   <li>{@code frame-channel-handler}: entrega los mensajes en orden y, al final, avisa del cierre.</li>
 * </ul>
 * Si el handler de mensajes lanza una excepción, la conexión se cierra.
 */
public final class FrameChannel implements AutoCloseable {

    private static final int MAXIMUM_ATTEMPTS = 4;
    private static final int DEFAULT_ACKNOWLEDGEMENT_TIMEOUT_MILLIS = 2000;
    // No es ACK ni NACK: despierta a quien espera un ACK cuando se cierra el canal.
    private static final int CLOSE_SIGNAL = -1;

    private final Socket socket;
    private final PushbackInputStream input;
    private final OutputStream output;
    private final FrameReader frameReader;
    private final Consumer<String> messageHandler;
    private final Runnable closeHandler;
    private final int acknowledgementTimeoutMillis;
    // Un solo hilo: entrega los mensajes y después el aviso de cierre, en orden de llegada.
    private final ExecutorService handlerThread;
    // Solo un envío espera ACK a la vez: un ACK no dice a qué trama responde.
    private final Object sendLock = new Object();
    // El lector (ACK/NACK) y los envíos (tramas) escriben en el mismo socket.
    private final Object writeLock = new Object();

    // Donde el lector deja los ACK/NACK del envío en curso; null si no hay ninguno.
    private volatile BlockingQueue<Integer> pendingAcknowledgements;
    private volatile boolean closed;
    // Motivo del cierre. Se guarda antes que closed: quien vea closed=true ve también la causa.
    private volatile IOException failure;

    public FrameChannel(Socket socket, Consumer<String> messageHandler) throws IOException {
        this(socket, messageHandler, () -> { }, DEFAULT_ACKNOWLEDGEMENT_TIMEOUT_MILLIS);
    }

    /** closeHandler se ejecuta una vez al cerrarse la conexión, después de los mensajes ya recibidos. */
    public FrameChannel(Socket socket, Consumer<String> messageHandler, Runnable closeHandler) throws IOException {
        this(socket, messageHandler, closeHandler, DEFAULT_ACKNOWLEDGEMENT_TIMEOUT_MILLIS);
    }

    /** Para los tests: permite esperar menos por cada ACK. */
    FrameChannel(Socket socket, Consumer<String> messageHandler, int acknowledgementTimeoutMillis)
            throws IOException {
        this(socket, messageHandler, () -> { }, acknowledgementTimeoutMillis);
    }

    FrameChannel(Socket socket, Consumer<String> messageHandler, Runnable closeHandler,
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
        this.frameReader = new FrameReader(input, FrameCodec.MAXIMUM_MESSAGE_LENGTH);
        this.handlerThread = Executors.newSingleThreadExecutor(task -> new Thread(task, "frame-channel-handler"));

        new Thread(this::readLoop, "frame-channel-reader").start();
    }

    /**
     * Envía el mensaje y espera su ACK. Si no lo consigue, cierra el canal y lanza la excepción.
     * Un reintento puede entregar el mismo mensaje más de una vez.
     */
    public void send(String message) throws IOException {
        Validator.requireMaximumLength(message, FrameCodec.MAXIMUM_MESSAGE_LENGTH, "Message");
        byte[] frame = FrameCodec.encode(message);

        synchronized (sendLock) {
            ensureOpen();
            // Una respuesta por intento, más la señal de cierre.
            BlockingQueue<Integer> replies = new ArrayBlockingQueue<>(MAXIMUM_ATTEMPTS + 1);
            pendingAcknowledgements = replies;
            try {
                sendUntilAcknowledged(frame, replies);
            } catch (InterruptedException exception) {
                throw closeAfterInterruption(exception);
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

    // ---------- Envío ----------

    private void sendUntilAcknowledged(byte[] frame, BlockingQueue<Integer> replies)
            throws IOException, InterruptedException {
        int receivedReplies = 0;

        for (int attempt = 1; attempt <= MAXIMUM_ATTEMPTS; attempt++) {
            writeBytes(frame);
            Integer reply = waitForReply(replies);
            if (reply == null) {
                continue; // Sin respuesta a tiempo: se reenvía.
            }

            receivedReplies++;
            if (reply == FrameCodec.ACK) {
                // Los intentos que agotaron el tiempo pueden recibir aún su respuesta.
                int lateReplies = attempt - receivedReplies;
                awaitLateReplies(replies, lateReplies);
                return;
            }
            // NACK: la trama llegó dañada; se reenvía en la siguiente vuelta.
        }

        throw new IOException("Message was not acknowledged after " + MAXIMUM_ATTEMPTS + " attempts");
    }

    /** Devuelve el ACK o NACK recibido, o null si no llega a tiempo. */
    private Integer waitForReply(BlockingQueue<Integer> replies) throws InterruptedException, IOException {
        Integer reply = replies.poll(acknowledgementTimeoutMillis, TimeUnit.MILLISECONDS);
        ensureOpen();
        return reply;
    }

    /** Recoge las respuestas tardías para que no se confundan con las del siguiente mensaje. */
    private void awaitLateReplies(BlockingQueue<Integer> replies, int lateReplies)
            throws InterruptedException, IOException {
        for (int index = 0; index < lateReplies; index++) {
            if (waitForReply(replies) == null) {
                throw new IOException("An outstanding acknowledgement is missing; connection state is ambiguous");
            }
        }
    }

    /** Convierte la interrupción en una IOException y cierra el canal. */
    private InterruptedIOException closeAfterInterruption(InterruptedException exception) {
        Thread.currentThread().interrupt();
        InterruptedIOException interrupted = new InterruptedIOException("Interrupted while waiting for acknowledgement");
        interrupted.initCause(exception);
        terminate(interrupted);
        return interrupted;
    }

    // ---------- Recepción (hilo frame-channel-reader) ----------

    private void readLoop() {
        try {
            while (!closed) {
                int firstByte = input.read();
                if (firstByte == FrameReader.END_OF_STREAM) {
                    throw new EOFException("Peer closed the connection");
                }

                if (firstByte == FrameCodec.ACK || firstByte == FrameCodec.NACK) {
                    receiveAcknowledgement(firstByte);
                } else if (firstByte == FrameCodec.START_OF_TEXT) {
                    // Se devuelve el STX para que FrameReader lea la trama completa.
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

    /** Pasa el ACK o NACK al envío que lo espera. */
    private void receiveAcknowledgement(int acknowledgement) throws IOException {
        BlockingQueue<Integer> replies = pendingAcknowledgements;
        if (replies == null || !replies.offer(acknowledgement)) {
            throw new IOException("Received an unexpected acknowledgement");
        }
    }

    /** Trama dañada: responde NACK. Trama correcta: responde ACK y entrega el mensaje. */
    private void receiveFrame() throws IOException {
        byte[] frame = frameReader.readFrame();
        String message;
        try {
            message = FrameCodec.decode(frame);
        } catch (IllegalArgumentException damagedFrame) {
            writeBytes(new byte[]{FrameCodec.NACK});
            return;
        }
        deliverAfterAcknowledging(message);
    }

    /**
     * La entrega se encola antes del ACK, para que un cierre simultáneo no pierda un mensaje
     * ya confirmado; pero espera a que salga el ACK, para que un handler que falle no cierre
     * el socket antes de confirmar.
     */
    private void deliverAfterAcknowledging(String message) throws IOException {
        CompletableFuture<Void> ackAttempted = new CompletableFuture<>();
        handlerThread.execute(() -> {
            ackAttempted.join();
            try {
                messageHandler.accept(message);
            } catch (RuntimeException exception) {
                terminate(new IOException("Message handler failed", exception));
            }
        });
        try {
            writeBytes(new byte[]{FrameCodec.ACK});
        } finally {
            // Aunque el ACK no se pueda escribir, el mensaje recibido se entrega igual.
            ackAttempted.complete(null);
        }
    }

    // ---------- Escritura y cierre ----------

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

    /** Cierra el canal una sola vez, aunque lo llamen varios hilos a la vez. */
    private synchronized void terminate(IOException cause) {
        if (closed) {
            return;
        }
        failure = cause;
        closed = true;

        BlockingQueue<Integer> replies = pendingAcknowledgements;
        if (replies != null) {
            // Despierta al emisor; ensureOpen le lanzará la causa.
            replies.offer(CLOSE_SIGNAL);
        }
        // Va detrás de los mensajes ya aceptados: el aviso de cierre siempre es el último.
        handlerThread.execute(this::runCloseHandler);
        // shutdown deja terminar lo encolado: los mensajes aceptados se entregan aunque se cierre el socket.
        handlerThread.shutdown();
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
            // La conexión ya está cerrada; un fallo aquí no cambia nada.
        }
    }
}
