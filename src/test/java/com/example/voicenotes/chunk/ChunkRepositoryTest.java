package com.example.voicenotes.chunk;

import com.example.voicenotes.TestcontainersConfiguration;
import com.example.voicenotes.session.Language;
import com.example.voicenotes.session.RecordingSession;
import com.example.voicenotes.session.SessionRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * JPA slice against real PostgreSQL (Testcontainers), schema created by the real Flyway migration.
 * An in-memory H2 would not prove that our SQL, constraints and ON DELETE CASCADE work on Postgres.
 * Each test runs in a transaction that is rolled back afterwards.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(TestcontainersConfiguration.class)
class ChunkRepositoryTest {

    @Autowired ChunkRepository chunks;
    @Autowired SessionRepository sessions;
    @Autowired EntityManager em;

    private RecordingSession newSession(String title) {
        return sessions.saveAndFlush(new RecordingSession(title, Language.KK));
    }

    private Chunk chunk(RecordingSession s, int seq, String key, int durationMs) {
        return new Chunk(s, seq, key, durationMs, 1_000L, "audio/webm");
    }

    @Test
    void statsAggregatePerSessionAndSkipSessionsWithoutChunks() {
        RecordingSession a = newSession("A");
        RecordingSession b = newSession("B");
        RecordingSession empty = newSession("empty");
        chunks.saveAll(List.of(chunk(a, 0, "a0", 30_000), chunk(a, 1, "a1", 15_000), chunk(b, 0, "b0", 5_000)));
        chunks.flush();

        Map<UUID, ChunkStats> stats = chunks.statsForSessions(List.of(a.getId(), b.getId(), empty.getId()))
                .stream().collect(Collectors.toMap(ChunkStats::getSessionId, s -> s));

        assertThat(stats).hasSize(2).doesNotContainKey(empty.getId());
        assertThat(stats.get(a.getId()).getChunkCount()).isEqualTo(2);
        assertThat(stats.get(a.getId()).getTotalDurationMs()).isEqualTo(45_000);
        assertThat(stats.get(b.getId()).getChunkCount()).isEqualTo(1);
    }

    @Test
    void sameIdempotencyKeyTwiceInOneSessionViolatesConstraint() {
        RecordingSession s = newSession("S");
        chunks.saveAndFlush(chunk(s, 0, "dup", 1_000));

        assertThatThrownBy(() -> chunks.saveAndFlush(chunk(s, 1, "dup", 1_000)))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("uq_chunk_idempotency");
    }

    @Test
    void sameKeyInDifferentSessionsIsAllowed() {
        chunks.saveAndFlush(chunk(newSession("S1"), 0, "same", 1_000));
        chunks.saveAndFlush(chunk(newSession("S2"), 0, "same", 1_000));
    }

    @Test
    void sameSequenceNumberTwiceViolatesConstraint() {
        RecordingSession s = newSession("S");
        chunks.saveAndFlush(chunk(s, 0, "k1", 1_000));

        assertThatThrownBy(() -> chunks.saveAndFlush(chunk(s, 0, "k2", 1_000)))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("uq_chunk_sequence");
    }

    @Test
    void checkConstraintRejectsNonPositiveDuration() {
        RecordingSession s = newSession("S");
        assertThatThrownBy(() -> chunks.saveAndFlush(chunk(s, 0, "k", 0)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void deletingSessionDeletesItsChunksInTheDatabase() {
        RecordingSession s = newSession("S");
        chunks.saveAllAndFlush(List.of(chunk(s, 0, "k0", 1_000), chunk(s, 1, "k1", 1_000)));
        em.clear(); // forget cached entities, so what we read next comes from the DB

        sessions.deleteById(s.getId());
        sessions.flush();
        em.clear();

        assertThat(chunks.findBySessionId(s.getId(), Pageable.unpaged()).getTotalElements()).isZero();
    }
}
