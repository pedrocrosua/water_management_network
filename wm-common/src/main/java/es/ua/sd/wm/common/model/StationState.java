package es.ua.sd.wm.common.model;

/**
 * Estado de una Watering Station, tal como se ve en el panel de Central.
 * Solo WM_Central decide el estado; el Monitor y el Engine solo informan de lo que pasa.
 */
public enum StationState {

    /** Sin conexión con el Monitor de la estación. Color en el panel: gris. */
    DISCONNECTED,

    /** Funciona y espera peticiones de riego. Color: verde. */
    AVAILABLE,

    /** Regando ahora mismo. Color: verde parpadeante. */
    IRRIGATING,

    /** Tiene una fuga; no riega hasta repararla. Color: rojo. */
    LEAK,

    /** Bloqueada a propósito por WM_Central ("Fuera de Servicio"). Color: naranja. */
    OUT_OF_SERVICE
}
