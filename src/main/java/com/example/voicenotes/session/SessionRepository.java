package com.example.voicenotes.session;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface SessionRepository extends JpaRepository<RecordingSession, UUID> {

    /** Uses index idx_session_status_created. */
    Page<RecordingSession> findByStatus(SessionStatus status, Pageable pageable);
}
