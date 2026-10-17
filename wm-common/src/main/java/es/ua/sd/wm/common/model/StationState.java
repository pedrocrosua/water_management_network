package es.ua.sd.wm.common.model;

/**
 * State of a Watering Station, as shown on the Central panel.
 * Only WM_Central decides the state; the Monitor and the Engine just report what happens.
 */
public enum StationState {

    /** No connection with the station's Monitor. Panel colour: grey. */
    DISCONNECTED,

    /** Working and waiting for an irrigation request. Panel colour: green. */
    AVAILABLE,

    /** Watering right now. Panel colour: blinking green. */
    IRRIGATING,

    /** A leak was detected; no irrigation until it is fixed. Panel colour: red. */
    LEAK,

    /** Blocked on purpose by WM_Central ("Fuera de Servicio"). Panel colour: orange. */
    OUT_OF_SERVICE
}
