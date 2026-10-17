package es.ua.sd.wm.monitor.application.port.in;

/** What the Monitor learns about its Engine. The socket server calls it; the application decides. */
public interface EngineEvents {

    /** The Engine answered a PING: healthy is false when it reports a fault (PONG with KO). */
    void engineAnswered(boolean healthy);

    /** The Engine stopped answering or its connection was lost. */
    void engineLost();
}
