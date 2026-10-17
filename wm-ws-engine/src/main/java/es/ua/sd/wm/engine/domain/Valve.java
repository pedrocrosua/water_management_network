package es.ua.sd.wm.engine.domain;

/** The station's solenoid valve: open means water is flowing. Simulated, there is no hardware. */
public class Valve {

    private volatile boolean open;

    public void open() {
        open = true;
    }

    public void close() {
        open = false;
    }

    public boolean isOpen() {
        return open;
    }
}
