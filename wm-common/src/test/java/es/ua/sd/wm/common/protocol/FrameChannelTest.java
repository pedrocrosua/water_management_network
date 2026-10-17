package es.ua.sd.wm.common.protocol;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Timeout(10)
class FrameChannelTest {

    @Test
    void validFrameIsAcknowledgedAndDelivered() throws Exception {
        try (SocketPair sockets = new SocketPair()) {
            BlockingQueue<String> receivedMessages = new LinkedBlockingQueue<>();
            try (FrameChannel channel = new FrameChannel(sockets.left, receivedMessages::add)) {
                sockets.right.getOutputStream().write(FrameCodec.encode("HELLO"));

                assertEquals(0x06, sockets.right.getInputStream().read());
                assertEquals("HELLO", receivedMessages.poll(2, TimeUnit.SECONDS));
                assertFalse(channel.isClosed());
            }
        }
    }

    @Test
    void corruptedFrameReceivesNackAndTheNextValidFrameStillWorks() throws Exception {
        try (SocketPair sockets = new SocketPair()) {
            BlockingQueue<String> receivedMessages = new LinkedBlockingQueue<>();
            try (FrameChannel channel = new FrameChannel(sockets.left, receivedMessages::add)) {
                sockets.right.getOutputStream().write(new byte[]{0x02, 'H', 0x03, 0x00});
                assertEquals(0x15, sockets.right.getInputStream().read());
                assertTrue(receivedMessages.isEmpty());

                sockets.right.getOutputStream().write(FrameCodec.encode("HELLO"));
                assertEquals(0x06, sockets.right.getInputStream().read());
                assertEquals("HELLO", receivedMessages.poll(2, TimeUnit.SECONDS));
            }
        }
    }

    @Test
    void sendWaitsForAnAck() throws Exception {
        try (SocketPair sockets = new SocketPair();
             FrameChannel channel = new FrameChannel(sockets.left, message -> {}, 1000);
             ExecutorService workers = Executors.newSingleThreadExecutor()) {
            Future<?> sending = workers.submit(() -> {
                channel.send("HELLO");
                return null;
            });

            assertEquals("HELLO", FrameCodec.decode(new FrameReader(sockets.right.getInputStream()).readFrame()));
            assertFalse(sending.isDone());
            sockets.right.getOutputStream().write(0x06);
            sending.get(2, TimeUnit.SECONDS);
        }
    }

    @Test
    void nackResendsTheSameFrame() throws Exception {
        try (SocketPair sockets = new SocketPair();
             FrameChannel channel = new FrameChannel(sockets.left, message -> {}, 1000);
             ExecutorService workers = Executors.newSingleThreadExecutor()) {
            Future<?> sending = workers.submit(() -> {
                channel.send("HELLO");
                return null;
            });
            FrameReader reader = new FrameReader(sockets.right.getInputStream());
            byte[] firstAttempt = reader.readFrame();
            sockets.right.getOutputStream().write(0x15);

            assertArrayEquals(firstAttempt, reader.readFrame());
            sockets.right.getOutputStream().write(0x06);
            sending.get(2, TimeUnit.SECONDS);
        }
    }

    @Test
    void threeRejectedRetriesCloseTheChannel() throws Exception {
        try (SocketPair sockets = new SocketPair();
             FrameChannel channel = new FrameChannel(sockets.left, message -> {}, 1000);
             ExecutorService workers = Executors.newSingleThreadExecutor()) {
            Future<?> sending = workers.submit(() -> {
                assertThrows(IOException.class, () -> channel.send("HELLO"));
                return null;
            });
            FrameReader reader = new FrameReader(sockets.right.getInputStream());
            for (int attempt = 0; attempt < 4; attempt++) {
                assertEquals("HELLO", FrameCodec.decode(reader.readFrame()));
                sockets.right.getOutputStream().write(0x15);
            }

            sending.get(2, TimeUnit.SECONDS);
            assertTrue(channel.isClosed());
            assertNull(reader.readFrame());
        }
    }

    @Test
    void fourUnansweredAttemptsFailInsteadOfWaitingForever() throws Exception {
        try (SocketPair sockets = new SocketPair();
             FrameChannel channel = new FrameChannel(sockets.left, message -> {}, 100);
             ExecutorService workers = Executors.newSingleThreadExecutor()) {
            Future<?> sending = workers.submit(() -> {
                assertThrows(IOException.class, () -> channel.send("HELLO"));
                return null;
            });
            FrameReader reader = new FrameReader(sockets.right.getInputStream());
            for (int attempt = 0; attempt < 4; attempt++) {
                assertEquals("HELLO", FrameCodec.decode(reader.readFrame()));
            }

            sending.get(2, TimeUnit.SECONDS);
            assertTrue(channel.isClosed());
            assertNull(reader.readFrame());
        }
    }

    @Test
    void delayedAcknowledgementsAreConsumedBeforeTheNextSend() throws Exception {
        try (SocketPair sockets = new SocketPair();
             FrameChannel channel = new FrameChannel(sockets.left, message -> {}, 200);
             ExecutorService workers = Executors.newSingleThreadExecutor()) {
            Future<?> sending = workers.submit(() -> {
                channel.send("FIRST");
                channel.send("SECOND");
                return null;
            });
            FrameReader reader = new FrameReader(sockets.right.getInputStream());
            assertEquals("FIRST", FrameCodec.decode(reader.readFrame()));
            assertEquals("FIRST", FrameCodec.decode(reader.readFrame()));
            sockets.right.getOutputStream().write(new byte[]{0x06, 0x06});

            assertEquals("SECOND", FrameCodec.decode(reader.readFrame()));
            assertFalse(sending.isDone());
            sockets.right.getOutputStream().write(0x06);
            sending.get(2, TimeUnit.SECONDS);
        }
    }

    @Test
    void missingReplyToAnOutstandingRetryClosesTheAmbiguousConnection() throws Exception {
        try (SocketPair sockets = new SocketPair();
             FrameChannel channel = new FrameChannel(sockets.left, message -> {}, 200);
             ExecutorService workers = Executors.newSingleThreadExecutor()) {
            Future<?> sending = workers.submit(() -> {
                assertThrows(IOException.class, () -> channel.send("HELLO"));
                return null;
            });
            FrameReader reader = new FrameReader(sockets.right.getInputStream());
            reader.readFrame();
            reader.readFrame();
            sockets.right.getOutputStream().write(0x06);

            sending.get(2, TimeUnit.SECONDS);
            assertTrue(channel.isClosed());
        }
    }

    @Test
    void bothEndpointsCanSendAtTheSameTime() throws Exception {
        try (SocketPair sockets = new SocketPair();
             ExecutorService workers = Executors.newFixedThreadPool(2)) {
            BlockingQueue<String> leftMessages = new LinkedBlockingQueue<>();
            BlockingQueue<String> rightMessages = new LinkedBlockingQueue<>();
            try (FrameChannel left = new FrameChannel(sockets.left, leftMessages::add);
                 FrameChannel right = new FrameChannel(sockets.right, rightMessages::add)) {
                CountDownLatch start = new CountDownLatch(1);
                Future<?> leftSend = workers.submit(() -> {
                    start.await();
                    left.send("LEFT");
                    return null;
                });
                Future<?> rightSend = workers.submit(() -> {
                    start.await();
                    right.send("RIGHT");
                    return null;
                });
                start.countDown();

                leftSend.get(2, TimeUnit.SECONDS);
                rightSend.get(2, TimeUnit.SECONDS);
                assertEquals("RIGHT", leftMessages.poll(2, TimeUnit.SECONDS));
                assertEquals("LEFT", rightMessages.poll(2, TimeUnit.SECONDS));
            }
        }
    }

    @Test
    void messageHandlerCanSendAResponseWithoutBlockingTheReader() throws Exception {
        try (SocketPair sockets = new SocketPair()) {
            AtomicReference<FrameChannel> responder = new AtomicReference<>();
            BlockingQueue<String> responses = new LinkedBlockingQueue<>();
            try (FrameChannel requester = new FrameChannel(sockets.left, responses::add);
                 FrameChannel receiver = new FrameChannel(sockets.right, message -> {
                     try {
                         responder.get().send("PONG");
                     } catch (IOException exception) {
                         throw new IllegalStateException(exception);
                     }
                 })) {
                responder.set(receiver);
                requester.send("PING");

                assertEquals("PONG", responses.poll(2, TimeUnit.SECONDS));
            }
        }
    }

    @Test
    void closeUnblocksASenderWaitingForConfirmation() throws Exception {
        try (SocketPair sockets = new SocketPair();
             FrameChannel channel = new FrameChannel(sockets.left, message -> {}, 5000);
             ExecutorService workers = Executors.newSingleThreadExecutor()) {
            Future<?> sending = workers.submit(() -> {
                assertThrows(IOException.class, () -> channel.send("HELLO"));
                return null;
            });
            new FrameReader(sockets.right.getInputStream()).readFrame();

            channel.close();

            sending.get(1, TimeUnit.SECONDS);
            assertThrows(IOException.class, () -> channel.send("AFTER_CLOSE"));
        }
    }

    @Test
    void invalidOutgoingMessageDoesNotWriteBytesOrCloseTheChannel() throws Exception {
        try (SocketPair sockets = new SocketPair();
             FrameChannel channel = new FrameChannel(sockets.left, message -> {})) {
            assertThrows(IllegalArgumentException.class, () -> channel.send(""));
            assertThrows(IllegalArgumentException.class, () -> channel.send("A".repeat(4097)));
            assertFalse(channel.isClosed());
            channel.close();
            assertEquals(-1, sockets.right.getInputStream().read());
        }
    }

    @Test
    void peerDisconnectionUnblocksASenderWaitingForConfirmation() throws Exception {
        try (SocketPair sockets = new SocketPair();
             FrameChannel channel = new FrameChannel(sockets.left, message -> {}, 5000);
             ExecutorService workers = Executors.newSingleThreadExecutor()) {
            Future<?> sending = workers.submit(() -> {
                assertThrows(IOException.class, () -> channel.send("HELLO"));
                return null;
            });
            new FrameReader(sockets.right.getInputStream()).readFrame();

            sockets.right.close();

            sending.get(1, TimeUnit.SECONDS);
            assertTrue(channel.isClosed());
        }
    }

    @Test
    void unexpectedBytesCloseTheConnection() throws Exception {
        try (SocketPair sockets = new SocketPair();
             FrameChannel channel = new FrameChannel(sockets.left, message -> {})) {
            sockets.right.getOutputStream().write('X');

            assertEquals(-1, sockets.right.getInputStream().read());
            assertTrue(channel.isClosed());
        }
    }

    @Test
    void handlerFailureClosesTheConnectionAfterAcknowledgingReceipt() throws Exception {
        try (SocketPair sockets = new SocketPair();
             FrameChannel channel = new FrameChannel(sockets.left, message -> {
                 throw new IllegalStateException("Handler failed");
             })) {
            sockets.right.getOutputStream().write(FrameCodec.encode("HELLO"));

            assertEquals(0x06, sockets.right.getInputStream().read());
            assertEquals(-1, sockets.right.getInputStream().read());
            assertTrue(channel.isClosed());
        }
    }

    @Test
    void acknowledgedQueuedMessagesSurvivePeerDisconnection() throws Exception {
        try (SocketPair sockets = new SocketPair()) {
            CountDownLatch firstStarted = new CountDownLatch(1);
            CountDownLatch releaseFirst = new CountDownLatch(1);
            BlockingQueue<String> delivered = new LinkedBlockingQueue<>();
            try (FrameChannel channel = new FrameChannel(sockets.left, message -> {
                if (message.equals("FIRST")) {
                    firstStarted.countDown();
                    try {
                        releaseFirst.await();
                    } catch (InterruptedException exception) {
                        Thread.currentThread().interrupt();
                    }
                }
                delivered.add(message);
            })) {
                try {
                    sockets.right.getOutputStream().write(FrameCodec.encode("FIRST"));
                    assertEquals(0x06, sockets.right.getInputStream().read());
                    assertTrue(firstStarted.await(2, TimeUnit.SECONDS));
                    sockets.right.getOutputStream().write(FrameCodec.encode("SECOND"));
                    assertEquals(0x06, sockets.right.getInputStream().read());

                    sockets.right.shutdownOutput();
                    assertEquals(-1, sockets.right.getInputStream().read());
                    releaseFirst.countDown();

                    assertEquals("FIRST", delivered.poll(2, TimeUnit.SECONDS));
                    assertEquals("SECOND", delivered.poll(2, TimeUnit.SECONDS));
                } finally {
                    releaseFirst.countDown();
                }
            }
        }
    }

    @ParameterizedTest
    @ValueSource(ints = {0, -1})
    void rejectsInvalidAcknowledgementTimeouts(int timeout) throws IOException {
        try (SocketPair sockets = new SocketPair()) {
            assertThrows(IllegalArgumentException.class,
                    () -> new FrameChannel(sockets.left, message -> {}, timeout));
        }
    }

    private static final class SocketPair implements AutoCloseable {
        private final Socket left;
        private final Socket right;

        private SocketPair() throws IOException {
            InetAddress loopback = InetAddress.getLoopbackAddress();
            try (ServerSocket listener = new ServerSocket(0, 1, loopback)) {
                right = new Socket(loopback, listener.getLocalPort());
                left = listener.accept();
                right.setSoTimeout(3000);
            }
        }

        @Override
        public void close() throws IOException {
            try {
                left.close();
            } finally {
                right.close();
            }
        }
    }
}
