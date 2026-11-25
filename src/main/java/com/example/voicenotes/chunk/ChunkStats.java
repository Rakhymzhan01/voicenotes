package com.example.voicenotes.chunk;

import java.util.UUID;

/** Spring Data interface projection: one row of the GROUP BY query in ChunkRepository. */
public interface ChunkStats {
    UUID getSessionId();
    long getChunkCount();
    long getTotalDurationMs();
}
