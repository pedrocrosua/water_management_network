package es.ua.sd.wm.common.event;

/** Pasos de una petición de riego, tal como los ve el operario. */
public enum NotificationType {
    RECEIVED,
    VALIDATING,
    AUTHORIZATION_REQUESTED,
    AUTHORIZED,
    DENIED,
    IRRIGATION_ENDED
}
