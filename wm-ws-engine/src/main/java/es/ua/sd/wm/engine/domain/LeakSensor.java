package es.ua.sd.wm.engine.domain;

/**
 * The station's flow meter, reduced to what matters for faults: is there a leak or not.
 * There is no real hardware, so the leak is simulated from the console.
 */
public class LeakSensor {

    private volatile boolean leaking;

    public void simulateLeak() {
        leaking = true;
    }

    public void repair() {
        leaking = false;
    }

    public boolean isLeaking() {
        return leaking;
    }
}
