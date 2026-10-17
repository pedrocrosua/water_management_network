package es.ua.sd.wm.operator.domain;

import static org.assertj.core.api.Assertions.assertThat;

import es.ua.sd.wm.common.event.StationStatus;
import es.ua.sd.wm.common.model.StationState;
import org.junit.jupiter.api.Test;

class StationBoardTest {

    private final StationBoard board = new StationBoard();

    @Test
    void keepsOnlyTheLatestStateOfEachStationSortedById() {
        board.update(new StationStatus("WS-02", "Jardines", StationState.AVAILABLE));
        board.update(new StationStatus("WS-01", "Canalejas", StationState.DISCONNECTED));
        board.update(new StationStatus("WS-01", "Canalejas", StationState.LEAK));

        assertThat(board.all()).containsExactly(
                new StationStatus("WS-01", "Canalejas", StationState.LEAK),
                new StationStatus("WS-02", "Jardines", StationState.AVAILABLE));
    }
}
