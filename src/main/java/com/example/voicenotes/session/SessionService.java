package com.example.voicenotes.session;

import com.example.voicenotes.chunk.ChunkRepository;
import com.example.voicenotes.chunk.ChunkStats;
import com.example.voicenotes.common.NotFoundException;
import com.example.voicenotes.common.PageResponse;
import com.example.voicenotes.session.dto.CreateSessionRequest;
import com.example.voicenotes.session.dto.SessionResponse;
import com.example.voicenotes.session.dto.UpdateSessionRequest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@Transactional(readOnly = true) // default for the class; writes override it below
public class SessionService {

    private final SessionRepository sessions;
    private final ChunkRepository chunks;

    public SessionService(SessionRepository sessions, ChunkRepository chunks) {
        this.sessions = sessions;
        this.chunks = chunks;
    }

    @Transactional
    public SessionResponse create(CreateSessionRequest req) {
        Language language = req.language() != null ? req.language() : Language.AUTO;
        RecordingSession saved = sessions.save(new RecordingSession(req.title().strip(), language));
        // flush so @CreationTimestamp values are set before we build the response
        sessions.flush();
        return SessionResponse.of(saved, 0, 0);
    }

    public SessionResponse get(UUID id) {
        RecordingSession s = getEntity(id);
        return withStats(List.of(s)).getFirst();
    }

    public PageResponse<SessionResponse> list(SessionStatus status, int page, int size) {
        PageRequest pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<RecordingSession> result = status == null
                ? sessions.findAll(pageable)
                : sessions.findByStatus(status, pageable);
        // 2 queries total for any page size (+1 count query for paging), not 1 + N
        List<SessionResponse> items = withStats(result.getContent());
        return new PageResponse<>(items, result.getNumber(), result.getSize(),
                result.getTotalElements(), result.getTotalPages());
    }

    @Transactional
    public SessionResponse update(UUID id, UpdateSessionRequest req) {
        RecordingSession s = getEntity(id);
        if (req.title() != null) s.rename(req.title().strip());
        if (req.language() != null) s.changeLanguage(req.language());
        // No save() needed: s is managed, Hibernate's dirty checking writes the UPDATE on commit.
        sessions.flush();
        return withStats(List.of(s)).getFirst();
    }

    @Transactional
    public SessionResponse finishUpload(UUID id) {
        RecordingSession s = getEntity(id);
        s.finishUpload();
        sessions.flush();
        // Week 3: publish a "session.upload-finished" event to Kafka here (via outbox).
        return withStats(List.of(s)).getFirst();
    }

    @Transactional
    public void delete(UUID id) {
        RecordingSession s = getEntity(id);
        sessions.delete(s); // chunks go too: ON DELETE CASCADE in V1__init.sql
    }

    RecordingSession getEntity(UUID id) {
        return sessions.findById(id)
                .orElseThrow(() -> new NotFoundException("Session " + id + " not found"));
    }

    private List<SessionResponse> withStats(List<RecordingSession> page) {
        if (page.isEmpty()) return List.of();
        List<UUID> ids = page.stream().map(RecordingSession::getId).toList();
        Map<UUID, ChunkStats> stats = chunks.statsForSessions(ids).stream()
                .collect(Collectors.toMap(ChunkStats::getSessionId, Function.identity()));
        return page.stream().map(s -> {
            ChunkStats st = stats.get(s.getId());
            return st == null
                    ? SessionResponse.of(s, 0, 0)
                    : SessionResponse.of(s, st.getChunkCount(), st.getTotalDurationMs());
        }).toList();
    }
}
