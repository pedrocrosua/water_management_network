package es.ua.sd.wm.common.model;

/** Who asked for an irrigation. */
public enum IrrigationSource {

    /** A field operator, from WM_FO. */
    OPERATOR,

    /** The menu of the station itself, from WM_WS_E. */
    STATION,

    /** An order given from the WM_Central panel. */
    CENTRAL
}
