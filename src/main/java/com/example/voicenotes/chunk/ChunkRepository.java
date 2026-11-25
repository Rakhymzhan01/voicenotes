package com.example.voicenotes.chunk;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ChunkRepository extends JpaRepository<Chunk, UUID> {

    Optional<Chunk> findBySessionIdAndIdempotencyKey(UUID sessionId, String idempotencyKey);

    boolean existsBySessionIdAndSequenceNumber(UUID sessionId, int sequenceNumber);

    Page<Chunk> findBySessionId(UUID sessionId, Pageable pageable);

    /**
     * Stats for many sessions in ONE query.
     * The naive way (count chunks per session inside a loop) is the N+1 problem:
     * 1 query for the page of sessions + N queries for their counts.
     */
    @Query("""
            select c.session.id as sessionId,
                   count(c) as chunkCount,
                   coalesce(sum(c.durationMs), 0) as totalDurationMs
            from Chunk c
            where c.session.id in :sessionIds
            group by c.session.id
            """)
    List<ChunkStats> statsForSessions(Collection<UUID> sessionIds);
}
