package es.ua.sd.wm.monitor.infrastructure.config;

import es.ua.sd.wm.monitor.application.StationMonitor;
import es.ua.sd.wm.monitor.domain.StationIdentity;
import es.ua.sd.wm.monitor.infrastructure.central.SocketCentralConnection;
import es.ua.sd.wm.monitor.infrastructure.engine.EngineSocketServer;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Construye el Monitor a partir de los argumentos y lo arranca. */
@Configuration
class MonitorConfiguration {

    // Cada cuánto se envía HEALTH a Central y PING al Engine.
    private static final Duration HEALTH_INTERVAL = Duration.ofSeconds(1);
    private static final Duration PING_INTERVAL = Duration.ofSeconds(1);
    // Espera antes de volver a conectar con Central.
    private static final Duration RETRY_DELAY = Duration.ofSeconds(3);

    /** stop() cierra la conexión con WM_Central al apagar la aplicación. */
    @Bean(destroyMethod = "stop")
    StationMonitor stationMonitor(@Value("${station.id}") String stationId,
                                  @Value("${station.location}") String location,
                                  @Value("${central.host}") String centralHost,
                                  @Value("${central.port}") int centralPort) {
        return new StationMonitor(
                new StationIdentity(stationId, location),
                new SocketCentralConnection(centralHost, centralPort),
                HEALTH_INTERVAL,
                RETRY_DELAY);
    }

    /** Escucha al Engine de la estación desde el arranque y le envía un PING cada segundo. */
    @Bean(initMethod = "start", destroyMethod = "stop")
    EngineSocketServer engineSocketServer(@Value("${engine.port}") int enginePort,
                                          @Value("${station.id}") String stationId,
                                          @Value("${station.location}") String location,
                                          StationMonitor monitor) {
        return new EngineSocketServer(enginePort, new StationIdentity(stationId, location), monitor,
                PING_INTERVAL);
    }

    /** Ejecuta el Monitor en el hilo principal; la aplicación vive mientras se ejecuta. */
    @Bean
    ApplicationRunner startMonitor(StationMonitor monitor) {
        return arguments -> monitor.run();
    }
}
