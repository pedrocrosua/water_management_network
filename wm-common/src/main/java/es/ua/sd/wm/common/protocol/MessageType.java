package es.ua.sd.wm.common.protocol;

/** First word of every socket message: says what the message means. */
public enum MessageType {

    // WM_WS_M -> WM_Central
    /** AUTH#stationId#location: register the station and authenticate it. */
    AUTH,
    /** HEALTH#stationId#OK: the station is alive, sent every second. */
    HEALTH,
    /** LEAK#stationId: a leak was detected. */
    LEAK,
    /** LEAK_CLEARED#stationId: the leak is fixed. */
    LEAK_CLEARED,

    // WM_Central -> WM_WS_M
    /** AUTH_OK#stationId#state: authentication accepted. */
    AUTH_OK,
    /** AUTH_KO#reason: authentication rejected. */
    AUTH_KO,
    /** STATE#stationId#state: the station's state changed. */
    STATE,

    // WM_WS_E <-> WM_WS_M
    /** HELLO: the Engine has just connected to its Monitor. */
    HELLO,
    /** ID#stationId#location: the Monitor tells the Engine which station it is. */
    ID,
    /** PING#sequence: the Monitor checks the Engine, every second. */
    PING,
    /** PONG#sequence#OK or PONG#sequence#KO: the Engine's answer. */
    PONG
}
