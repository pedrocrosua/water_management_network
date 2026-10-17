package es.ua.sd.wm.central.application.port.out;

import es.ua.sd.wm.common.event.OperatorNotification;

/** Tells an operator how their request is going. */
public interface OperatorNotifier {

    void notify(OperatorNotification notification);
}
