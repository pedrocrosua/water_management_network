package es.ua.sd.wm.operator.infrastructure.kafka;

import es.ua.sd.wm.common.event.StationStatus;
import es.ua.sd.wm.common.event.Topics;
import es.ua.sd.wm.operator.domain.StationBoard;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.annotation.PartitionOffset;
import org.springframework.kafka.annotation.TopicPartition;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

/**
 * Mantiene al día el tablero de estaciones. En cada arranque lee el topic desde el principio
 * (offset 0 de sus tres particiones, creadas por WM_Central), así que el tablero muestra el
 * último estado de cada estación aunque WM_FO estuviera apagado cuando cambió.
 *
 * <p>Tiene su propio grupo: lee particiones fijas en lugar de unirse a un grupo, y Kafka rechaza
 * sus offsets en un grupo que ya tiene miembros (los otros listeners, en "fo-FO-001").
 */
@Component
class StationStatusListener {

    private static final Logger log = LoggerFactory.getLogger(StationStatusListener.class);

    private final StationBoard board;
    private final JsonMapper json;

    StationStatusListener(StationBoard board, JsonMapper json) {
        this.board = board;
        this.json = json;
    }

    @KafkaListener(groupId = "fo-${operator.id}-board", topicPartitions = @TopicPartition(
            topic = Topics.STATION_STATUS,
            partitions = "0-2", // las 3 que crea WM_Central (PARTITIONS en su KafkaTopics)
            partitionOffsets = @PartitionOffset(partition = "*", initialOffset = "0")))
    void receive(String message) {
        try {
            board.update(json.readValue(message, StationStatus.class));
        } catch (JacksonException | IllegalArgumentException invalid) {
            log.warn("Ignored an invalid station status: {}", message);
        }
    }
}
