package es.ua.sd.wm.central.infrastructure.config;

import es.ua.sd.wm.central.application.CentralOrders;
import es.ua.sd.wm.central.application.IrrigationService;
import es.ua.sd.wm.central.application.StationRegistry;
import es.ua.sd.wm.central.application.port.out.IrrigationProgressPublisher;
import es.ua.sd.wm.central.application.port.out.IrrigationRepository;
import es.ua.sd.wm.central.application.port.out.OperatorNotifier;
import es.ua.sd.wm.central.application.port.out.OperatorRepository;
import es.ua.sd.wm.central.application.port.out.StationChangePublisher;
import es.ua.sd.wm.central.application.port.out.StationCommandSender;
import es.ua.sd.wm.central.application.port.out.StationRepository;
import java.time.Duration;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Crea los servicios de la capa de aplicación. Esas clases no llevan anotaciones de Spring:
 * así la lógica no depende del framework.
 */
@Configuration
class ApplicationConfiguration {

    /** Spring pasa en la lista todos los beans que implementan StationChangePublisher. */
    @Bean
    StationRegistry stationRegistry(StationRepository repository, List<StationChangePublisher> changePublishers) {
        return new StationRegistry(repository, changePublishers);
    }

    /** stop() para el temporizador que espera las respuestas de las estaciones. */
    @Bean(destroyMethod = "stop")
    IrrigationService irrigationService(StationRegistry registry, OperatorRepository operators,
                                        StationCommandSender commands, OperatorNotifier notifier,
                                        IrrigationProgressPublisher progress, IrrigationRepository history,
                                        @Value("${irrigation.answer-timeout-seconds}") long answerTimeoutSeconds) {
        return new IrrigationService(registry, operators, commands, notifier, progress, history,
                Duration.ofSeconds(answerTimeoutSeconds));
    }

    @Bean
    CentralOrders centralOrders(StationRegistry registry, IrrigationService irrigations) {
        return new CentralOrders(registry, irrigations);
    }
}
