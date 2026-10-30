package es.ua.sd.wm.central.application.port.out;

import es.ua.sd.wm.central.domain.Station;

/**
 * Avisa de que una estación ha cambiado. Lo implementan el panel web ({@code PanelBroadcaster}),
 * los Monitores conectados ({@code MonitorSessions}) y Kafka ({@code KafkaStationStatusPublisher}).
 */
public interface StationChangePublisher {

    void publish(Station station);
}
