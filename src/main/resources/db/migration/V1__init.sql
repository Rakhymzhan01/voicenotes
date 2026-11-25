-- A recording session: one screen + voice recording that will become one note.
CREATE TABLE recording_session (
    id          UUID PRIMARY KEY,
    title       VARCHAR(200) NOT NULL,
    language    VARCHAR(16)  NOT NULL,  -- KK, RU, EN, MIXED, AUTO
    status      VARCHAR(16)  NOT NULL,  -- CREATED, UPLOADING, PROCESSING, COMPLETED, FAILED
    version     BIGINT       NOT NULL DEFAULT 0, -- optimistic locking
    created_at  TIMESTAMPTZ  NOT NULL,
    updated_at  TIMESTAMPTZ  NOT NULL
);

-- List endpoint filters by status and sorts by created_at.
CREATE INDEX idx_session_status_created ON recording_session (status, created_at DESC);

-- A chunk: one uploaded piece of the recording (e.g. 30 s of audio).
CREATE TABLE chunk (
    id               UUID PRIMARY KEY,
    session_id       UUID         NOT NULL REFERENCES recording_session (id) ON DELETE CASCADE,
    sequence_number  INT          NOT NULL CHECK (sequence_number >= 0),
    idempotency_key  VARCHAR(100) NOT NULL,
    duration_ms      INT          NOT NULL CHECK (duration_ms > 0),
    size_bytes       BIGINT       NOT NULL CHECK (size_bytes > 0),
    content_type     VARCHAR(100) NOT NULL,
    status           VARCHAR(16)  NOT NULL,  -- UPLOADED, TRANSCRIBING, TRANSCRIBED, FAILED
    created_at       TIMESTAMPTZ  NOT NULL,

    -- Retried upload with the same key must not create a second row.
    CONSTRAINT uq_chunk_idempotency UNIQUE (session_id, idempotency_key),
    -- One chunk per position in the recording.
    CONSTRAINT uq_chunk_sequence UNIQUE (session_id, sequence_number)
);
