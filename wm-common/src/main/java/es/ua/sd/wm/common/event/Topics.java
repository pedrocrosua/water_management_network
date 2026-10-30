package es.ua.sd.wm.common.event;

/** Nombres de los topics de Kafka que usan todas las aplicaciones. */
public final class Topics {

    /** Peticiones de riego: las envía WM_FO y las lee WM_Central. */
    public static final String IRRIGATION_REQUESTS = "wm.irrigation.requests";

    /** Órdenes para una estación: las envía WM_Central y las lee WM_WS_E. */
    public static final String STATION_COMMANDS = "wm.station.commands";

    /** Lo que informa una estación (caudal, respuestas a órdenes y fin de riego): lo envía WM_WS_E y lo leen WM_Central y WM_FO. */
    public static final String STATION_TELEMETRY = "wm.station.telemetry";

    /** Progreso de una petición: lo envía WM_Central y lo lee WM_FO. */
    public static final String OPERATOR_NOTIFICATIONS = "wm.operator.notifications";

    /** Estado de cada estación: lo envía WM_Central y lo lee WM_FO. */
    public static final String STATION_STATUS = "wm.station.status";

    private Topics() {
    }
}
