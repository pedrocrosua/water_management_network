package es.ua.sd.wm.engine.infrastructure.config;

import es.ua.sd.wm.engine.application.IrrigationController;
import es.ua.sd.wm.engine.application.ManualIrrigation;
import es.ua.sd.wm.engine.application.MonitorLink;
import es.ua.sd.wm.engine.domain.LeakSensor;
import es.ua.sd.wm.engine.domain.Valve;
import es.ua.sd.wm.engine.infrastructure.backup.FileIrrigationBackup;
import es.ua.sd.wm.engine.infrastructure.console.EngineConsole;
import es.ua.sd.wm.engine.infrastructure.kafka.KafkaIrrigationRequestSender;
import es.ua.sd.wm.engine.infrastructure.kafka.KafkaStationEventPublisher;
import es.ua.sd.wm.engine.infrastructure.kafka.StationCommandListener;
import es.ua.sd.wm.engine.infrastructure.monitor.SocketMonitorConnection;
import es.ua.sd.wm.engine.infrastructure.sensor.SimulatedFlowMeter;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.file.Path;
import java.time.Duration;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import tools.jackson.databind.json.JsonMapper;

/** Construye el Engine a partir de los argumentos y lo arranca. */
@Configuration
class EngineConfiguration {

    // Espera antes de volver a conectar con el Monitor.
    private static final Duration RETRY_DELAY = Duration.ofSeconds(3);
    // Cada cuánto se comprueba si la conexión con el Monitor sigue abierta.
    private static final Duration CONNECTION_CHECK_INTERVAL = Duration.ofMillis(500);

    @Bean
    LeakSensor leakSensor() {
        return new LeakSensor();
    }

    @Bean
    Valve valve() {
        return new Valve();
    }

    @Bean
    IrrigationController irrigationController(Valve valve, LeakSensor sensor,
                                              KafkaTemplate<String, String> kafka, JsonMapper json,
                                              @Value("${engine.backup-file}") String backupFile) {
        return new IrrigationController(valve, sensor, new SimulatedFlowMeter(),
                new KafkaStationEventPublisher(kafka, json), new FileIrrigationBackup(Path.of(backupFile), json));
    }

    /** Avanza el riego una vez por segundo (telemetría y tiempo límite). */
    @Bean(destroyMethod = "shutdownNow")
    ScheduledExecutorService irrigationClock(IrrigationController irrigations) {
        ScheduledExecutorService clock = Executors.newSingleThreadScheduledExecutor();
        clock.scheduleAtFixedRate(irrigations::tick, 1, 1, TimeUnit.SECONDS);
        return clock;
    }

    @Bean
    ManualIrrigation manualIrrigation(IrrigationController irrigations, LeakSensor sensor,
                                      KafkaTemplate<String, String> kafka, JsonMapper json) {
        return new ManualIrrigation(irrigations, sensor, new KafkaIrrigationRequestSender(kafka, json));
    }

    /** stop() sale limpiamente del grupo de consumidores al apagar la aplicación. */
    @Bean(destroyMethod = "stop")
    StationCommandListener stationCommandListener(ConsumerFactory<String, String> consumerFactory,
                                                  IrrigationController irrigations, JsonMapper json) {
        return new StationCommandListener(consumerFactory, irrigations::execute, json);
    }

    /**
     * stop() cierra la conexión con el Monitor al apagar la aplicación. Cuando el Monitor dice qué
     * estación es, el Engine empieza a leer las órdenes de esa estación y ya puede pedir riegos.
     * Si el Monitor se cae, la estación queda averiada hasta que vuelva.
     */
    @Bean(destroyMethod = "stop")
    MonitorLink monitorLink(@Value("${monitor.host}") String monitorHost,
                            @Value("${monitor.port}") int monitorPort,
                            LeakSensor sensor, StationCommandListener commandListener,
                            ManualIrrigation manual, IrrigationController irrigations) {
        return new MonitorLink(
                new SocketMonitorConnection(monitorHost, monitorPort, sensor),
                station -> {
                    commandListener.listenFor(station.id());
                    manual.stationIdentified(station.id());
                    irrigations.monitorBack();
                },
                irrigations::monitorLost,
                RETRY_DELAY,
                CONNECTION_CHECK_INTERVAL);
    }

    /** El menú va en su propio hilo; el bucle de conexión mantiene vivo el hilo principal. */
    @Bean
    ApplicationRunner startEngine(LeakSensor sensor, IrrigationController irrigations, ManualIrrigation manual,
                                  MonitorLink link) {
        return arguments -> {
            irrigations.recoverInterruptedIrrigation(); // por si el Engine se cayó durante un riego
            EngineConsole console = new EngineConsole(sensor, irrigations, manual,
                    new BufferedReader(new InputStreamReader(System.in)), System.out);
            Thread.ofPlatform().daemon().name("engine-console").start(console::run);
            link.run();
        };
    }
}
