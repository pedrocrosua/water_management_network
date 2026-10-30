package es.ua.sd.wm.engine.domain;

/**
 * Detecta fugas en la estación. No hay hardware real: la fuga se simula desde el menú
 * ({@code EngineConsole}).
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
