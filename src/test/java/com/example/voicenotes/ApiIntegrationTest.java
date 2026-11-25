package com.example.voicenotes;

import com.example.voicenotes.chunk.ChunkRepository;
import com.example.voicenotes.session.SessionService;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Pageable;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The whole application over real HTTP against real PostgreSQL.
 * Tests share one DB, so each test works with its own sessions.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "spring.jpa.properties.hibernate.generate_statistics=true")
@Import(TestcontainersConfiguration.class)
class ApiIntegrationTest {

    @LocalServerPort int port;
    @Autowired JsonMapper json;
    @Autowired ChunkRepository chunks;
    @Autowired SessionService sessionService;
    @Autowired EntityManagerFactory emf;

    private final HttpClient http = HttpClient.newHttpClient();

    record Resp(int status, JsonNode body) {
    }

    private Resp call(String method, String path, String body, String idempotencyKey) throws Exception {
        HttpRequest.Builder b = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                .header("Content-Type", "application/json")
                .method(method, body == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(body));
        if (idempotencyKey != null) b.header("Idempotency-Key", idempotencyKey);
        HttpResponse<String> r = http.send(b.build(), HttpResponse.BodyHandlers.ofString());
        return new Resp(r.statusCode(), r.body().isEmpty() ? null : json.readTree(r.body()));
    }

    private String createSession(String title) throws Exception {
        Resp r = call("POST", "/api/sessions", "{\"title\":\"" + title + "\",\"language\":\"MIXED\"}", null);
        assertThat(r.status()).isEqualTo(201);
        return r.body().get("id").asString();
    }

    private static String chunkBody(int seq, int durationMs) {
        return "{\"sequenceNumber\":" + seq + ",\"durationMs\":" + durationMs
                + ",\"sizeBytes\":1000,\"contentType\":\"audio/webm\"}";
    }

    @Test
    void fullLifecycle() throws Exception {
        String id = createSession("Lifecycle");
        String chunksUrl = "/api/sessions/" + id + "/chunks";

        assertThat(call("POST", chunksUrl, chunkBody(0, 30_000), "k0").status()).isEqualTo(201);
        assertThat(call("POST", chunksUrl, chunkBody(0, 30_000), "k0").status()).isEqualTo(200); // retry
        assertThat(call("POST", chunksUrl, chunkBody(1, 15_000), "k1").status()).isEqualTo(201);

        Resp session = call("GET", "/api/sessions/" + id, null, null);
        assertThat(session.body().get("status").asString()).isEqualTo("UPLOADING");
        assertThat(session.body().get("chunkCount").asInt()).isEqualTo(2);
        assertThat(session.body().get("totalDurationMs").asLong()).isEqualTo(45_000);

        Resp finished = call("POST", "/api/sessions/" + id + "/finish", null, null);
        assertThat(finished.body().get("status").asString()).isEqualTo("PROCESSING");

        Resp late = call("POST", chunksUrl, chunkBody(2, 1_000), "k2");
        assertThat(late.status()).isEqualTo(409);
        assertThat(late.body().get("detail").asString()).contains("PROCESSING");

        assertThat(call("DELETE", "/api/sessions/" + id, null, null).status()).isEqualTo(204);
        assertThat(call("GET", "/api/sessions/" + id, null, null).status()).isEqualTo(404);
    }

    /**
     * Client retries the same chunk 8 times in parallel (e.g. flaky mobile network).
     * Whatever the interleaving: exactly one row in the DB, exactly one 201, and no 500s.
     */
    @Test
    void concurrentRetriesOfSameChunkCreateExactlyOneRow() throws Exception {
        String id = createSession("Race");
        int threads = 8;
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Integer>> results = new ArrayList<>();
        try (ExecutorService pool = Executors.newFixedThreadPool(threads)) {
            for (int i = 0; i < threads; i++) {
                results.add(pool.submit(() -> {
                    start.await(); // release all threads at once
                    return call("POST", "/api/sessions/" + id + "/chunks", chunkBody(0, 30_000), "same-key").status();
                }));
            }
            start.countDown();
        }
        List<Integer> statuses = new ArrayList<>();
        for (Future<Integer> f : results) statuses.add(f.get());

        // isSubsetOf, not containsOnly: we allow these codes, we don't require all of them to appear.
        // Today the losers get 409 (DB constraint / optimistic lock); week 3 goal: they get 200.
        assertThat(statuses).isSubsetOf(201, 200, 409);
        assertThat(statuses).filteredOn(s -> s == 201).hasSize(1);
        assertThat(chunks.findBySessionId(UUID.fromString(id), Pageable.unpaged()).getTotalElements()).isEqualTo(1);
    }

    /**
     * Guard against N+1: listing 20 sessions with chunk stats must take a fixed number of SQL
     * statements (page + count + one GROUP BY), not 1 + 20. Fails if someone "simplifies"
     * withStats() into a per-session query.
     */
    @Test
    void listingSessionsDoesNotDoNPlusOneQueries() throws Exception {
        for (int i = 0; i < 25; i++) {
            String id = createSession("Nplus1-" + i);
            call("POST", "/api/sessions/" + id + "/chunks", chunkBody(0, 1_000), "k");
        }
        Statistics stats = emf.unwrap(SessionFactory.class).getStatistics();
        stats.clear();

        var page = sessionService.list(null, 0, 20);

        assertThat(page.items()).hasSize(20);
        assertThat(stats.getPrepareStatementCount()).isLessThanOrEqualTo(3);
    }
}
