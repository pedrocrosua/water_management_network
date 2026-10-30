package es.ua.sd.wm.central.application.port.out;

import es.ua.sd.wm.common.event.OperatorNotification;

/** Le cuenta a un operario cómo va su petición. */
public interface OperatorNotifier {

    void notify(OperatorNotification notification);
}
