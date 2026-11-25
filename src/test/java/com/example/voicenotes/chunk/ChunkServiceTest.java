package com.example.voicenotes.chunk;

import com.example.voicenotes.chunk.dto.CreateChunkRequest;
import com.example.voicenotes.common.ConflictException;
import com.example.voicenotes.common.InvalidStateException;
import com.example.voicenotes.common.NotFoundException;
import com.example.voicenotes.session.Language;
import com.example.voicenotes.session.RecordingSession;
import com.example.voicenotes.session.SessionRepository;
import com.example.voicenotes.session.SessionStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit test of the idempotency rules with mocked repositories (Mockito).
 * Checks the decisions the service makes, not SQL; SQL is covered by the Testcontainers tests.
 */
@ExtendWith(MockitoExtension.class)
class ChunkServiceTest {

    private static final UUID SESSION_ID = UUID.randomUUID();
    private static final String KEY = "key-1";
    private static final CreateChunkRequest REQ = new CreateChunkRequest(0, 30_000, 61_440L, "audio/webm");

    @Mock ChunkRepository chunks;
    @Mock SessionRepository sessions;
    @InjectMocks ChunkService service;

    private final RecordingSession session = new RecordingSession("Lecture", Language.RU);

    @Test
    void firstUploadSavesChunkAndMovesSessionToUploading() {
        when(sessions.findById(SESSION_ID)).thenReturn(Optional.of(session));
        when(chunks.findBySessionIdAndIdempotencyKey(SESSION_ID, KEY)).thenReturn(Optional.empty());
        when(chunks.save(any(Chunk.class))).thenAnswer(inv -> inv.getArgument(0));

        ChunkService.UploadResult result = service.upload(SESSION_ID, KEY, REQ);

        assertThat(result.created()).isTrue();
        assertThat(session.getStatus()).isEqualTo(SessionStatus.UPLOADING);

        ArgumentCaptor<Chunk> saved = ArgumentCaptor.forClass(Chunk.class);
        verify(chunks).save(saved.capture());
        assertThat(saved.getValue().getIdempotencyKey()).isEqualTo(KEY);
        assertThat(saved.getValue().getSequenceNumber()).isZero();
    }

    @Test
    void retryWithSameKeyAndBodyReturnsOriginalWithoutSaving() {
        Chunk original = new Chunk(session, 0, KEY, 30_000, 61_440L, "audio/webm");
        when(sessions.findById(SESSION_ID)).thenReturn(Optional.of(session));
        when(chunks.findBySessionIdAndIdempotencyKey(SESSION_ID, KEY)).thenReturn(Optional.of(original));

        ChunkService.UploadResult result = service.upload(SESSION_ID, KEY, REQ);

        assertThat(result.created()).isFalse();
        assertThat(result.chunk().durationMs()).isEqualTo(30_000);
        verify(chunks, never()).save(any());
    }

    @Test
    void sameKeyWithDifferentBodyIsRejected() {
        Chunk original = new Chunk(session, 0, KEY, 999, 61_440L, "audio/webm");
        when(sessions.findById(SESSION_ID)).thenReturn(Optional.of(session));
        when(chunks.findBySessionIdAndIdempotencyKey(SESSION_ID, KEY)).thenReturn(Optional.of(original));

        assertThatThrownBy(() -> service.upload(SESSION_ID, KEY, REQ))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("different request body");
        verify(chunks, never()).save(any());
    }

    @Test
    void takenSequenceNumberIsRejected() {
        when(sessions.findById(SESSION_ID)).thenReturn(Optional.of(session));
        when(chunks.findBySessionIdAndIdempotencyKey(SESSION_ID, KEY)).thenReturn(Optional.empty());
        when(chunks.existsBySessionIdAndSequenceNumber(SESSION_ID, 0)).thenReturn(true);

        assertThatThrownBy(() -> service.upload(SESSION_ID, KEY, REQ))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("#0");
        verify(chunks, never()).save(any());
    }

    @Test
    void unknownSessionIs404() {
        when(sessions.findById(SESSION_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.upload(SESSION_ID, KEY, REQ)).isInstanceOf(NotFoundException.class);
    }

    @Test
    void finishedSessionAcceptsNoChunks() {
        session.onChunkUploaded();
        session.finishUpload();
        when(sessions.findById(SESSION_ID)).thenReturn(Optional.of(session));
        when(chunks.findBySessionIdAndIdempotencyKey(SESSION_ID, KEY)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.upload(SESSION_ID, KEY, REQ)).isInstanceOf(InvalidStateException.class);
        verify(chunks, never()).save(any());
    }
}
