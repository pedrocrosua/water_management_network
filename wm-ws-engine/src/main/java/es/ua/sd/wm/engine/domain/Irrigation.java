package es.ua.sd.wm.engine.domain;

/** The irrigation in progress: who asked for it, its time limit and the water supplied so far. */
public class Irrigation {

    private static final double SECONDS_PER_MINUTE = 60.0;

    private final String requestId;
    private final String stationId;
    private final String operatorId;
    private final int limitSeconds;
    private long elapsedSeconds;
    private double litres;

    public Irrigation(String requestId, String stationId, String operatorId, int limitSeconds) {
        this.requestId = requestId;
        this.stationId = stationId;
        this.operatorId = operatorId;
        this.limitSeconds = limitSeconds;
    }

    /** One more second of water at the given flow. */
    public void addSecond(double flowLitresPerMinute) {
        elapsedSeconds++;
        litres += flowLitresPerMinute / SECONDS_PER_MINUTE;
    }

    public boolean limitReached() {
        return elapsedSeconds >= limitSeconds;
    }

    public String requestId() {
        return requestId;
    }

    public String stationId() {
        return stationId;
    }

    public String operatorId() {
        return operatorId;
    }

    public long elapsedSeconds() {
        return elapsedSeconds;
    }

    public double litres() {
        return litres;
    }
}
