package es.ua.sd.wm.common.event;

/** Steps of an irrigation request, as the operator sees them. */
public enum NotificationType {
    RECEIVED,
    VALIDATING,
    AUTHORIZATION_REQUESTED,
    AUTHORIZED,
    DENIED,
    IRRIGATION_ENDED
}
