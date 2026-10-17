package es.ua.sd.wm.engine.infrastructure.config;

import es.ua.sd.wm.engine.application.MonitorLink;
import es.ua.sd.wm.engine.application.StationCommandHandler;
import es.ua.sd.wm.engine.domain.LeakSensor;
import es.ua.sd.wm.engine.domain.Valve;
import es.ua.sd.wm.engine.infrastructure.console.EngineConsole;
import es.ua.sd.wm.engine.infrastructure.kafka.KafkaStationEventPublisher;
import es.ua.sd.wm.engine.infrastructure.kafka.StationCommandListener;
import es.ua.sd.wm.engine.infrastructure.monitor.SocketMonitorConnection;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import tools.jackson.databind.json.JsonMapper;

/** Builds the Engine from the command-line arguments and starts it. */
@Configuration
class EngineConfiguration {

    @Bean
    LeakSensor leakSensor() {
        return new LeakSensor();
    }

    @Bean
    Valve valve() {
        return new Valve();
    }

    @Bean
    StationCommandHandler stationCommandHandler(Valve valve, LeakSensor sensor,
                                                KafkaTemplate<String, String> kafka, JsonMapper json) {
        return new StationCommandHandler(valve, sensor, new KafkaStationEventPublisher(kafka, json));
    }

    /** stop() leaves the consumer group cleanly when the application shuts down. */
    @Bean(destroyMethod = "stop")
    StationCommandListener stationCommandListener(ConsumerFactory<String, String> consumerFactory,
                                                  StationCommandHandler handler, JsonMapper json) {
        return new StationCommandListener(consumerFactory, handler::execute, json);
    }

    /**
     * stop() closes the connection with the Monitor when the application shuts down. Once the
     * Monitor says which station this is, the Engine starts reading that station's orders.
     */
    @Bean(destroyMethod = "stop")
    MonitorLink monitorLink(@Value("${monitor.host}") String monitorHost,
                            @Value("${monitor.port}") int monitorPort,
                            LeakSensor sensor, StationCommandListener commandListener) {
        return new MonitorLink(
                new SocketMonitorConnection(monitorHost, monitorPort, sensor),
                station -> commandListener.listenFor(station.id()),
                Duration.ofSeconds(3),
                Duration.ofMillis(500));
    }

    /** The menu runs on its own thread; the connection loop keeps the main thread alive. */
    @Bean
    ApplicationRunner startEngine(LeakSensor sensor, MonitorLink link) {
        return arguments -> {
            EngineConsole console = new EngineConsole(sensor,
                    new BufferedReader(new InputStreamReader(System.in)), System.out);
            Thread.ofPlatform().daemon().name("engine-console").start(console::run);
            link.run();
        };
    }
}
