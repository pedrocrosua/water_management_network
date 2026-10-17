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
 * Keeps the board of stations up to date. On every start it reads the topic from the very
 * beginning (offset 0 of its three partitions, created by WM_Central), so the board shows the
 * latest state of every station even if WM_FO was off when it changed.
 *
 * <p>It has its own group: it reads fixed partitions instead of joining a group, and Kafka
 * rejects its offsets in a group that already has members (the notification listener).
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
            partitions = "0-2",
            partitionOffsets = @PartitionOffset(partition = "*", initialOffset = "0")))
    void receive(String message) {
        try {
            board.update(json.readValue(message, StationStatus.class));
        } catch (JacksonException | IllegalArgumentException invalid) {
            log.warn("Ignored an invalid station status: {}", message);
        }
    }
}
