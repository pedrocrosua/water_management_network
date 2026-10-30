package es.ua.sd.wm.monitor.application.port.in;

/** Lo que el Monitor sabe de su Engine. Lo llama el servidor de sockets; decide la aplicación. */
public interface EngineEvents {

    /** El Engine respondió a un PING: healthy es false si avisa de una avería (PONG con KO). */
    void engineAnswered(boolean healthy);

    /** El Engine dejó de responder o se perdió su conexión. */
    void engineLost();
}
