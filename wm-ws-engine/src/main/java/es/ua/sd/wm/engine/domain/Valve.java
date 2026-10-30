package es.ua.sd.wm.engine.domain;

/** La electroválvula de la estación: abierta significa que sale agua. Simulada, sin hardware. */
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
