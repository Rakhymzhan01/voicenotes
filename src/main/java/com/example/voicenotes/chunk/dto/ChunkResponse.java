package com.example.voicenotes.chunk.dto;

import com.example.voicenotes.chunk.Chunk;
import com.example.voicenotes.chunk.ChunkStatus;

import java.time.Instant;
import java.util.UUID;

public record ChunkResponse(
        UUID id,
        int sequenceNumber,
        int durationMs,
        long sizeBytes,
        String contentType,
        ChunkStatus status,
        Instant createdAt
) {
    public static ChunkResponse of(Chunk c) {
        return new ChunkResponse(c.getId(), c.getSequenceNumber(), c.getDurationMs(),
                c.getSizeBytes(), c.getContentType(), c.getStatus(), c.getCreatedAt());
    }
}
