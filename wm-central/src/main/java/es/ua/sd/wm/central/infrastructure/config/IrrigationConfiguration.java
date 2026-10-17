package es.ua.sd.wm.central.infrastructure.config;

import es.ua.sd.wm.central.application.IrrigationService;
import es.ua.sd.wm.central.application.StationRegistry;
import es.ua.sd.wm.central.application.port.out.OperatorNotifier;
import es.ua.sd.wm.central.application.port.out.OperatorRepository;
import es.ua.sd.wm.central.application.port.out.StationCommandSender;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
class IrrigationConfiguration {

    /** stop() ends the timer that waits for the stations' answers. */
    @Bean(destroyMethod = "stop")
    IrrigationService irrigationService(StationRegistry registry, OperatorRepository operators,
                                        StationCommandSender commands, OperatorNotifier notifier,
                                        @Value("${irrigation.answer-timeout-seconds}") long answerTimeoutSeconds) {
        return new IrrigationService(registry, operators, commands, notifier,
                Duration.ofSeconds(answerTimeoutSeconds));
    }
}
