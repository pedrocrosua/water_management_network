package es.ua.sd.wm.operator.infrastructure.config;

import es.ua.sd.wm.operator.application.IrrigationRequests;
import es.ua.sd.wm.operator.application.RequestFile;
import es.ua.sd.wm.operator.domain.StationBoard;
import es.ua.sd.wm.operator.infrastructure.console.OperatorConsole;
import es.ua.sd.wm.operator.infrastructure.kafka.KafkaIrrigationRequestSender;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.KafkaTemplate;
import tools.jackson.databind.json.JsonMapper;

/** Construye WM_FO a partir de los argumentos y arranca su menú. */
@Configuration
class OperatorConfiguration {

    @Bean
    StationBoard stationBoard() {
        return new StationBoard();
    }

    @Bean
    IrrigationRequests irrigationRequests(@Value("${operator.id}") String operatorId,
                                          KafkaTemplate<String, String> kafka, JsonMapper json) {
        return new IrrigationRequests(operatorId, new KafkaIrrigationRequestSender(kafka, json));
    }

    /** CU-12: entre petición y petición se esperan 4 s; si en 30 s más no termina, se pasa a la siguiente. */
    @Bean
    RequestFile requestFile(IrrigationRequests requests) {
        return new RequestFile(requests, RequestFile.PAUSE_BETWEEN_REQUESTS, Duration.ofSeconds(30));
    }

    @Bean
    OperatorConsole operatorConsole(@Value("${operator.id}") String operatorId,
                                    IrrigationRequests requests, RequestFile requestFile, StationBoard board) {
        return new OperatorConsole(operatorId, requests, requestFile, board,
                new BufferedReader(new InputStreamReader(System.in)), System.out);
    }

    /** El menú va en el hilo principal; los listeners de Kafka mantienen viva la aplicación. */
    @Bean
    ApplicationRunner startMenu(OperatorConsole console) {
        return arguments -> console.run();
    }
}
