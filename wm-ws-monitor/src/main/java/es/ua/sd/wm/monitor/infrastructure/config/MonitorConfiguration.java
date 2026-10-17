package es.ua.sd.wm.monitor.infrastructure.config;

import es.ua.sd.wm.monitor.application.StationMonitor;
import es.ua.sd.wm.monitor.domain.StationIdentity;
import es.ua.sd.wm.monitor.infrastructure.central.SocketCentralConnection;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Builds the Monitor from the command-line arguments and starts it. */
@Configuration
class MonitorConfiguration {

    /** stop() closes the connection with WM_Central when the application shuts down. */
    @Bean(destroyMethod = "stop")
    StationMonitor stationMonitor(@Value("${station.id}") String stationId,
                                  @Value("${station.location}") String location,
                                  @Value("${central.host}") String centralHost,
                                  @Value("${central.port}") int centralPort) {
        return new StationMonitor(
                new StationIdentity(stationId, location),
                new SocketCentralConnection(centralHost, centralPort),
                Duration.ofSeconds(1),
                Duration.ofSeconds(3));
    }

    /** Runs the Monitor on the main thread; the application lives as long as it runs. */
    @Bean
    ApplicationRunner startMonitor(StationMonitor monitor) {
        return arguments -> monitor.run();
    }
}
