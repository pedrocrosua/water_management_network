package es.ua.sd.wm.common.model;

/** Por qué terminó un riego. */
public enum EndReason {

    /** Se alcanzó el tiempo límite fijado al empezar. */
    TIME_LIMIT,

    /** Alguien lo paró desde el menú de la estación. */
    MANUAL_STOP,

    /** Se detectó una fuga durante el riego. */
    LEAK,

    /** WM_Central dejó la estación fuera de servicio. */
    BLOCKED,

    /** La estación perdió la conexión con WM_Central. */
    DISCONNECTED
}
