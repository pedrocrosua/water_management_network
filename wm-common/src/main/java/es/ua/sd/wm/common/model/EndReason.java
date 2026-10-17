package es.ua.sd.wm.common.model;

/** Why an irrigation finished. */
public enum EndReason {

    /** The time limit set when the irrigation started was reached. */
    TIME_LIMIT,

    /** Someone stopped it from the station menu. */
    MANUAL_STOP,

    /** A leak was detected while irrigating. */
    LEAK,

    /** WM_Central put the station out of service. */
    BLOCKED,

    /** The station lost its connection with WM_Central. */
    DISCONNECTED
}
