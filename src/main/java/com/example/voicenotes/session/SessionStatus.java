package com.example.voicenotes.session;

public enum SessionStatus {
    /** Created, no chunks yet. */
    CREATED,
    /** At least one chunk uploaded, client is still recording. */
    UPLOADING,
    /** Client finished uploading; transcription/summarisation in progress. */
    PROCESSING,
    COMPLETED,
    FAILED
}
