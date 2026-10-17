package es.ua.sd.wm.central.infrastructure.kafka;

import es.ua.sd.wm.central.application.port.out.StationCommandSender;
import es.ua.sd.wm.common.event.StationCommand;
import es.ua.sd.wm.common.event.Topics;
import org.springframework.stereotype.Component;

/** Orders go to every Engine; each one keeps those whose key is its own station id. */
@Component
class KafkaStationCommandSender implements StationCommandSender {

    private final KafkaPublisher publisher;

    KafkaStationCommandSender(KafkaPublisher publisher) {
        this.publisher = publisher;
    }

    @Override
    public void send(StationCommand command) {
        publisher.publish(Topics.STATION_COMMANDS, command.stationId(), command);
    }
}
