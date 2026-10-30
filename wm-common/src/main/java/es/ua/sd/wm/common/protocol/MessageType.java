package es.ua.sd.wm.common.protocol;

/** Primera palabra de cada mensaje de socket: indica qué significa. */
public enum MessageType {

    // WM_WS_M -> WM_Central
    /** AUTH#stationId#location: registra y autentica la estación. */
    AUTH,
    /** HEALTH#stationId#OK: la estación sigue viva; cada segundo. */
    HEALTH,
    /** LEAK#stationId: se ha detectado una fuga. */
    LEAK,
    /** LEAK_CLEARED#stationId: la fuga está reparada. */
    LEAK_CLEARED,

    // WM_Central -> WM_WS_M
    /** AUTH_OK#stationId#state: autenticación aceptada. */
    AUTH_OK,
    /** AUTH_KO#reason: autenticación rechazada. */
    AUTH_KO,
    /** STATE#stationId#state: la estación ha cambiado de estado. */
    STATE,

    // WM_WS_E <-> WM_WS_M
    /** HELLO: el Engine acaba de conectarse a su Monitor. */
    HELLO,
    /** ID#stationId#location: el Monitor le dice al Engine qué estación es. */
    ID,
    /** PING#sequence: el Monitor comprueba el Engine; cada segundo. */
    PING,
    /** PONG#sequence#OK o PONG#sequence#KO: la respuesta del Engine. */
    PONG
}
