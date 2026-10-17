package es.ua.sd.wm.operator.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import es.ua.sd.wm.common.event.IrrigationRequest;
import es.ua.sd.wm.common.model.IrrigationSource;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class IrrigationRequestsTest {

    private final List<IrrigationRequest> sent = new ArrayList<>();
    private final IrrigationRequests requests = new IrrigationRequests("FO-001", sent::add);

    @Test
    void sendsARequestInTheNameOfTheOperator() {
        IrrigationRequest request = requests.request("WS-01", 60);

        assertThat(sent).containsExactly(request);
        assertThat(request.source()).isEqualTo(IrrigationSource.OPERATOR);
        assertThat(request.operatorId()).isEqualTo("FO-001");
        assertThat(request.stationId()).isEqualTo("WS-01");
        assertThat(request.limitSeconds()).isEqualTo(60);
    }

    @Test
    void everyRequestHasItsOwnId() {
        assertThat(requests.request("WS-01", 60).requestId())
                .isNotEqualTo(requests.request("WS-01", 60).requestId());
    }

    @Test
    void invalidRequestIsNotSent() {
        assertThatThrownBy(() -> requests.request("WS-01", 0)).isInstanceOf(IllegalArgumentException.class);
        assertThat(sent).isEmpty();
    }
}
