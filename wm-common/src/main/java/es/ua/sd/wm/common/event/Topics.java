package es.ua.sd.wm.common.event;

/** Names of the Kafka topics used by every application. */
public final class Topics {

    /** Irrigation requests: WM_FO and WM_WS_E send them, WM_Central reads them. */
    public static final String IRRIGATION_REQUESTS = "wm.irrigation.requests";

    /** Orders for a station: WM_Central sends them, WM_WS_E reads them. */
    public static final String STATION_COMMANDS = "wm.station.commands";

    /** What a station reports: WM_WS_E sends it, WM_Central and WM_FO read it. */
    public static final String STATION_TELEMETRY = "wm.station.telemetry";

    /** Progress of a request: WM_Central sends it, WM_FO reads it. */
    public static final String OPERATOR_NOTIFICATIONS = "wm.operator.notifications";

    /** State of every station: WM_Central sends it, WM_FO reads it. */
    public static final String STATION_STATUS = "wm.station.status";

    private Topics() {
    }
}
