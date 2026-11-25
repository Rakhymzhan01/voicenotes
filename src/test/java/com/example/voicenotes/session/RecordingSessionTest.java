package com.example.voicenotes.session;

import com.example.voicenotes.common.InvalidStateException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Plain unit test: no Spring, no DB. Runs in milliseconds. */
class RecordingSessionTest {

    private final RecordingSession session = new RecordingSession("Lecture", Language.MIXED);

    @Test
    void newSessionIsCreated() {
        assertThat(session.getStatus()).isEqualTo(SessionStatus.CREATED);
    }

    @Test
    void firstChunkMovesToUploadingAndNextChunksKeepIt() {
        session.onChunkUploaded();
        assertThat(session.getStatus()).isEqualTo(SessionStatus.UPLOADING);

        session.onChunkUploaded();
        assertThat(session.getStatus()).isEqualTo(SessionStatus.UPLOADING);
    }

    @Test
    void finishMovesUploadingToProcessing() {
        session.onChunkUploaded();
        session.finishUpload();
        assertThat(session.getStatus()).isEqualTo(SessionStatus.PROCESSING);
    }

    @Test
    void cannotFinishWithoutChunks() {
        assertThatThrownBy(session::finishUpload)
                .isInstanceOf(InvalidStateException.class)
                .hasMessageContaining("CREATED");
    }

    @Test
    void noChunksAfterFinish() {
        session.onChunkUploaded();
        session.finishUpload();
        assertThatThrownBy(session::onChunkUploaded).isInstanceOf(InvalidStateException.class);
    }

    @Test
    void languageIsLockedOnceProcessingStarted() {
        session.changeLanguage(Language.KK);
        assertThat(session.getLanguage()).isEqualTo(Language.KK);

        session.onChunkUploaded();
        session.finishUpload();
        assertThatThrownBy(() -> session.changeLanguage(Language.RU)).isInstanceOf(InvalidStateException.class);
        assertThat(session.getLanguage()).isEqualTo(Language.KK);
    }
}
