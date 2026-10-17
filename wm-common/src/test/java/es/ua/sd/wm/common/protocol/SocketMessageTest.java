package es.ua.sd.wm.common.protocol;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class SocketMessageTest {

    @Test
    void formatsTypeAndFieldsSeparatedByHash() {
        SocketMessage message = SocketMessage.of(MessageType.AUTH, "WS-01", "Parque de Canalejas");

        assertThat(message.format()).isEqualTo("AUTH#WS-01#Parque de Canalejas");
    }

    @Test
    void messageWithoutFieldsIsJustItsType() {
        assertThat(SocketMessage.of(MessageType.HELLO).format()).isEqualTo("HELLO");
        assertThat(SocketMessage.parse("HELLO").fields()).isEmpty();
    }

    @Test
    void parsingGivesBackTheSameMessage() {
        SocketMessage message = SocketMessage.parse("STATE#WS-01#AVAILABLE");

        assertThat(message.type()).isEqualTo(MessageType.STATE);
        assertThat(message.field(0)).isEqualTo("WS-01");
        assertThat(message.field(1)).isEqualTo("AVAILABLE");
        assertThat(message).isEqualTo(SocketMessage.of(MessageType.STATE, "WS-01", "AVAILABLE"));
    }

    @Test
    void emptyFieldAtTheEndIsKept() {
        assertThat(SocketMessage.parse("STATE#WS-01#").fields()).containsExactly("WS-01", "");
    }

    @Test
    void unknownTypeIsRejected() {
        assertThatThrownBy(() -> SocketMessage.parse("SELF_DESTRUCT#now"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unknown message type");
    }

    @Test
    void askingForAMissingFieldIsAnError() {
        assertThatThrownBy(() -> SocketMessage.parse("LEAK").field(0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("no field number 0");
    }

    @Test
    void fieldsCannotContainTheSeparatorOrNonAsciiCharacters() {
        assertThatThrownBy(() -> SocketMessage.of(MessageType.AUTH, "WS#01", "Park"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> SocketMessage.of(MessageType.AUTH, "WS-01", "Jardín"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
