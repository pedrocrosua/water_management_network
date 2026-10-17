package es.ua.sd.wm.engine.infrastructure.config;

import es.ua.sd.wm.engine.application.MonitorLink;
import es.ua.sd.wm.engine.domain.LeakSensor;
import es.ua.sd.wm.engine.infrastructure.console.EngineConsole;
import es.ua.sd.wm.engine.infrastructure.monitor.SocketMonitorConnection;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Builds the Engine from the command-line arguments and starts it. */
@Configuration
class EngineConfiguration {

    @Bean
    LeakSensor leakSensor() {
        return new LeakSensor();
    }

    /** stop() closes the connection with the Monitor when the application shuts down. */
    @Bean(destroyMethod = "stop")
    MonitorLink monitorLink(@Value("${monitor.host}") String monitorHost,
                            @Value("${monitor.port}") int monitorPort,
                            LeakSensor sensor) {
        return new MonitorLink(
                new SocketMonitorConnection(monitorHost, monitorPort, sensor),
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
