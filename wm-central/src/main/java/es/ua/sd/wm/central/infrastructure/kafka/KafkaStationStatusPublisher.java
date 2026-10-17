package es.ua.sd.wm.central.infrastructure.kafka;

import es.ua.sd.wm.central.application.port.out.StationChangePublisher;
import es.ua.sd.wm.central.domain.Station;
import es.ua.sd.wm.common.event.StationStatus;
import es.ua.sd.wm.common.event.Topics;
import org.springframework.stereotype.Component;

/** Every state change is also published, so operators can list the available stations. */
@Component
class KafkaStationStatusPublisher implements StationChangePublisher {

    private final KafkaPublisher publisher;

    KafkaStationStatusPublisher(KafkaPublisher publisher) {
        this.publisher = publisher;
    }

    @Override
    public void publish(Station station) {
        publisher.publish(Topics.STATION_STATUS, station.id(),
                new StationStatus(station.id(), station.location(), station.state()));
    }
}
