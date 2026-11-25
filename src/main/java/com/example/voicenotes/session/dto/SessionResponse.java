package com.example.voicenotes.session.dto;

import com.example.voicenotes.session.Language;
import com.example.voicenotes.session.RecordingSession;
import com.example.voicenotes.session.SessionStatus;

import java.time.Instant;
import java.util.UUID;

public record SessionResponse(
        UUID id,
        String title,
        Language language,
        SessionStatus status,
        long chunkCount,
        long totalDurationMs,
        Instant createdAt,
        Instant updatedAt
) {
    public static SessionResponse of(RecordingSession s, long chunkCount, long totalDurationMs) {
        return new SessionResponse(s.getId(), s.getTitle(), s.getLanguage(), s.getStatus(),
                chunkCount, totalDurationMs, s.getCreatedAt(), s.getUpdatedAt());
    }
}
