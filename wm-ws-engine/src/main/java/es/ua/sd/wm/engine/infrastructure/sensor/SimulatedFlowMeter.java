package es.ua.sd.wm.engine.infrastructure.sensor;

import es.ua.sd.wm.engine.application.port.out.FlowMeter;
import java.util.concurrent.ThreadLocalRandom;

/** There is no real flow meter: the flow varies a little around 20 litres per minute. */
public class SimulatedFlowMeter implements FlowMeter {

    private static final double MINIMUM_LITRES_PER_MINUTE = 18.0;
    private static final double MAXIMUM_LITRES_PER_MINUTE = 22.0;

    @Override
    public double readLitresPerMinute() {
        return ThreadLocalRandom.current().nextDouble(MINIMUM_LITRES_PER_MINUTE, MAXIMUM_LITRES_PER_MINUTE);
    }
}
