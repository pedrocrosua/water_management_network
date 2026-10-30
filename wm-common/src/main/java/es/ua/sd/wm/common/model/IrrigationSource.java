package es.ua.sd.wm.common.model;

/** Quién pidió un riego. */
public enum IrrigationSource {

    /** Un operario de campo, desde WM_FO. */
    OPERATOR,

    /** El menú de la propia estación, desde WM_WS_E. */
    STATION,

    /** Una orden dada desde el panel de WM_Central. */
    CENTRAL
}
