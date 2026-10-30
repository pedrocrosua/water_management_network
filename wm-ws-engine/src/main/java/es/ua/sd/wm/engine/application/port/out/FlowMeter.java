package es.ua.sd.wm.engine.application.port.out;

/** Mide cuánta agua pasa por la válvula abierta. */
public interface FlowMeter {

    double readLitresPerMinute();
}
