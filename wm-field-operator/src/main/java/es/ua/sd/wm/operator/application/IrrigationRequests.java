package es.ua.sd.wm.operator.application;

import es.ua.sd.wm.common.event.IrrigationRequest;
import es.ua.sd.wm.common.event.NotificationType;
import es.ua.sd.wm.common.event.OperatorNotification;
import es.ua.sd.wm.common.model.IrrigationSource;
import es.ua.sd.wm.common.validation.Validator;
import es.ua.sd.wm.operator.application.port.out.IrrigationRequestSender;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/** Pide riegos a WM_Central en nombre de un operario. */
public class IrrigationRequests {

    private final String operatorId;
    private final IrrigationRequestSender sender;
    // Petición cuyo final se está esperando (modo fichero) y el cerrojo que se abre cuando termina.
    private String awaitedRequestId;
    private CountDownLatch awaitedEnd;

    public IrrigationRequests(String operatorId, IrrigationRequestSender sender) {
        Validator.requireNonEmpty(operatorId, "Operator id (--operator.id)");
        this.operatorId = operatorId;
        this.sender = sender;
    }

    /** Envía la petición y la devuelve; las respuestas llegan después como notificaciones. */
    public IrrigationRequest request(String stationId, int limitSeconds) {
        IrrigationRequest request = newRequest(stationId, limitSeconds);
        sender.send(request);
        return request;
    }

    /**
     * Envía la petición y espera a que termine: denegada, o con el riego acabado. Devuelve false si
     * en maxWait no llega el final (por ejemplo, porque WM_Central no responde).
     */
    public boolean requestAndWait(String stationId, int limitSeconds, Duration maxWait) throws InterruptedException {
        IrrigationRequest request = newRequest(stationId, limitSeconds);
        CountDownLatch end = new CountDownLatch(1);
        synchronized (this) {
            awaitedRequestId = request.requestId();
            awaitedEnd = end;
        }
        sender.send(request);
        // Se espera fuera del synchronized: así el hilo de Kafka puede entrar en notificationReceived.
        return end.await(maxWait.toMillis(), TimeUnit.MILLISECONDS);
    }

    /** Lo llama el hilo de Kafka con cada notificación de este operario. */
    public synchronized void notificationReceived(OperatorNotification notification) {
        boolean isTheEnd = notification.type() == NotificationType.DENIED
                || notification.type() == NotificationType.IRRIGATION_ENDED;
        if (isTheEnd && notification.requestId().equals(awaitedRequestId)) {
            awaitedEnd.countDown();
        }
    }

    private IrrigationRequest newRequest(String stationId, int limitSeconds) {
        // Cada petición lleva un id único para poder relacionarla con sus notificaciones.
        return new IrrigationRequest(UUID.randomUUID().toString(), IrrigationSource.OPERATOR,
                operatorId, stationId, limitSeconds);
    }
}
