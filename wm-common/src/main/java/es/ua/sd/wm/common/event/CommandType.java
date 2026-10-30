package es.ua.sd.wm.common.event;

/** Órdenes que WM_Central puede dar a una estación. */
public enum CommandType {

    /** Abrir la válvula y regar hasta el tiempo límite. */
    START,

    /** Cerrar la válvula y terminar el riego en curso. */
    STOP,

    /** Terminar cualquier riego y quedar fuera de servicio. */
    BLOCK,

    /** Salir de fuera de servicio. */
    ACTIVATE
}
