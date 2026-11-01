package es.ua.sd.wm.central;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

/** Arranca el WM_Central real en un puerto libre, con un fichero SQLite nuevo. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@EmbeddedKafka(bootstrapServersProperty = "spring.kafka.bootstrap-servers")
class CentralApplicationTest {

    @TempDir
    static Path databaseFolder;

    @DynamicPropertySource
    static void useTemporaryDatabase(DynamicPropertyRegistry properties) {
        properties.add("db.path", () -> databaseFolder.resolve("test.db").toString());
        properties.add("socket.port", () -> 0);
    }

    @Value("${local.server.port}")
    private int port;

    private final HttpClient http = HttpClient.newHttpClient();

    @Test
    void panelApiListsTheStoredStationsAsDisconnected() throws Exception {
        HttpResponse<String> response = http.send(request("/api/stations"), HttpResponse.BodyHandlers.ofString());

        List<Map<String, Object>> stations = JsonMapper.builder().build()
                .readValue(response.body(), new TypeReference<>() { });

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(stations).extracting(station -> station.get("id")).containsExactly("WS-01", "WS-02", "WS-03");
        assertThat(stations).extracting(station -> station.get("state")).containsOnly("DISCONNECTED");
    }

    @Test
    void liveStreamStartsBySendingEveryStation() throws Exception {
        HttpResponse<Stream<String>> response = http.send(request("/api/stations/stream"), HttpResponse.BodyHandlers.ofLines());

        // El flujo no termina nunca: solo se leen los tres primeros eventos de estación.
        try (Stream<String> lines = response.body()) {
            List<String> stationData = lines.filter(line -> line.startsWith("data:")).limit(3).toList();
            assertThat(stationData).hasSize(3);
            assertThat(stationData.getFirst()).contains("\"id\":\"WS-01\"", "\"state\":\"DISCONNECTED\"");
        }
    }

    @Test
    void panelPageIsServed() throws IOException, InterruptedException {
        HttpResponse<String> response = http.send(request("/"), HttpResponse.BodyHandlers.ofString());

        assertThat(response.body()).contains("WaterManagement · Central");
    }

    @Test
    void panelCanBlockAndActivateAStation() throws Exception {
        HttpResponse<String> blocked = http.send(post("/api/orders/block?stationId=WS-02"), HttpResponse.BodyHandlers.ofString());

        assertThat(blocked.statusCode()).isEqualTo(200);
        assertThat(blocked.body()).isEqualTo("WS-02 bloqueada");
        assertThat(findStation("WS-02").get("outOfService")).isEqualTo(true);

        HttpResponse<String> activated = http.send(post("/api/orders/activate?stationId=WS-02"), HttpResponse.BodyHandlers.ofString());

        assertThat(activated.body()).isEqualTo("WS-02 activada");
        assertThat(findStation("WS-02").get("outOfService")).isEqualTo(false);
    }

    @Test
    void orderForAnUnknownStationIsRejected() throws Exception {
        HttpResponse<String> response = http.send(post("/api/orders/block?stationId=WS-99"), HttpResponse.BodyHandlers.ofString());

        assertThat(response.statusCode()).isEqualTo(400);
        assertThat(response.body()).isEqualTo("Unknown station WS-99");
    }

    private Map<String, Object> findStation(String stationId) throws Exception {
        HttpResponse<String> response = http.send(request("/api/stations"), HttpResponse.BodyHandlers.ofString());
        List<Map<String, Object>> stations = JsonMapper.builder().build()
                .readValue(response.body(), new TypeReference<>() { });
        for (Map<String, Object> station : stations) {
            if (station.get("id").equals(stationId)) {
                return station;
            }
        }
        throw new AssertionError("Station not found: " + stationId);
    }

    private HttpRequest request(String path) {
        return HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                .timeout(Duration.ofSeconds(5))
                .build();
    }

    private HttpRequest post(String path) {
        return HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                .timeout(Duration.ofSeconds(5))
                .POST(HttpRequest.BodyPublishers.noBody())
                .build();
    }
}
