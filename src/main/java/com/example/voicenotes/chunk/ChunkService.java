package com.example.voicenotes.chunk;

import com.example.voicenotes.chunk.dto.ChunkResponse;
import com.example.voicenotes.chunk.dto.CreateChunkRequest;
import com.example.voicenotes.common.ConflictException;
import com.example.voicenotes.common.NotFoundException;
import com.example.voicenotes.common.PageResponse;
import com.example.voicenotes.session.RecordingSession;
import com.example.voicenotes.session.SessionRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class ChunkService {

    /** created = false means this was a retry and we returned the chunk saved the first time. */
    public record UploadResult(ChunkResponse chunk, boolean created) {
    }

    private final ChunkRepository chunks;
    private final SessionRepository sessions;

    public ChunkService(ChunkRepository chunks, SessionRepository sessions) {
        this.chunks = chunks;
        this.sessions = sessions;
    }

    /**
     * Registers a chunk. Safe to retry: the client sends the same Idempotency-Key
     * again after a timeout and gets the original result instead of a duplicate.
     *
     * <p>Two retries arriving at exactly the same moment can both pass the lookup;
     * then the UNIQUE (session_id, idempotency_key) constraint rejects the second
     * insert and the client gets 409 and retries. Making that path return 200 is a
     * week 3-4 topic (insert ... on conflict, or a row lock on the session).
     */
    @Transactional
    public UploadResult upload(UUID sessionId, String idempotencyKey, CreateChunkRequest req) {
        RecordingSession session = sessions.findById(sessionId)
                .orElseThrow(() -> new NotFoundException("Session " + sessionId + " not found"));

        var existing = chunks.findBySessionIdAndIdempotencyKey(sessionId, idempotencyKey);
        if (existing.isPresent()) {
            Chunk c = existing.get();
            if (!samePayload(c, req)) {
                throw new ConflictException("Idempotency-Key '" + idempotencyKey
                        + "' was already used with a different request body");
            }
            return new UploadResult(ChunkResponse.of(c), false);
        }

        if (chunks.existsBySessionIdAndSequenceNumber(sessionId, req.sequenceNumber())) {
            throw new ConflictException("Chunk #" + req.sequenceNumber() + " already exists in this session");
        }

        session.onChunkUploaded(); // throws 409 if the session no longer accepts chunks
        Chunk saved = chunks.save(new Chunk(session, req.sequenceNumber(), idempotencyKey,
                req.durationMs(), req.sizeBytes(), req.contentType()));
        chunks.flush(); // surface constraint violations here, and fill createdAt
        return new UploadResult(ChunkResponse.of(saved), true);
    }

    public PageResponse<ChunkResponse> list(UUID sessionId, int page, int size) {
        if (!sessions.existsById(sessionId)) {
            throw new NotFoundException("Session " + sessionId + " not found");
        }
        var pageable = PageRequest.of(page, size, Sort.by("sequenceNumber"));
        return PageResponse.of(chunks.findBySessionId(sessionId, pageable), ChunkResponse::of);
    }

    private static boolean samePayload(Chunk c, CreateChunkRequest r) {
        return c.getSequenceNumber() == r.sequenceNumber()
                && c.getDurationMs() == r.durationMs()
                && c.getSizeBytes() == r.sizeBytes()
                && Objects.equals(c.getContentType(), r.contentType());
    }
}
