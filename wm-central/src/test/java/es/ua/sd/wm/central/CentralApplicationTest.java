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
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

/** Starts the real WM_Central on a random port, with a fresh SQLite file. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class CentralApplicationTest {

    @TempDir
    static Path databaseFolder;

    @DynamicPropertySource
    static void useTemporaryDatabase(DynamicPropertyRegistry properties) {
        properties.add("db.path", () -> databaseFolder.resolve("test.db").toString());
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

        // The stream never ends: read only the first three station events.
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

    private HttpRequest request(String path) {
        return HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                .timeout(Duration.ofSeconds(5))
                .build();
    }
}
