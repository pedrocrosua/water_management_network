package es.ua.sd.wm.common.event;

/** Orders that WM_Central can give to a station. */
public enum CommandType {

    /** Open the valve and irrigate until the time limit. */
    START,

    /** Close the valve and end the current irrigation. */
    STOP,

    /** End any irrigation and stay out of service. */
    BLOCK,

    /** Leave the out-of-service state. */
    ACTIVATE
}
