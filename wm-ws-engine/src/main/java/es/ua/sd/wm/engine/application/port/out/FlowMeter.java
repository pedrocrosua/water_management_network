package es.ua.sd.wm.engine.application.port.out;

/** Measures how much water goes through the open valve. */
public interface FlowMeter {

    double readLitresPerMinute();
}
