package com.example.voicenotes.session;

import com.example.voicenotes.common.PageResponse;
import com.example.voicenotes.session.dto.CreateSessionRequest;
import com.example.voicenotes.session.dto.SessionResponse;
import com.example.voicenotes.session.dto.UpdateSessionRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.util.UUID;

@Tag(name = "Sessions", description = "Recording sessions (one recording = one note)")
@RestController
@RequestMapping("/api/sessions")
public class SessionController {

    private final SessionService service;

    public SessionController(SessionService service) {
        this.service = service;
    }

    @Operation(summary = "Create a recording session")
    @PostMapping
    public ResponseEntity<SessionResponse> create(@Valid @RequestBody CreateSessionRequest req) {
        SessionResponse created = service.create(req);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}").buildAndExpand(created.id()).toUri();
        return ResponseEntity.created(location).body(created);
    }

    @Operation(summary = "List sessions, newest first")
    @GetMapping
    public PageResponse<SessionResponse> list(
            @RequestParam(required = false) SessionStatus status,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return service.list(status, page, size);
    }

    @Operation(summary = "Get one session with chunk statistics")
    @GetMapping("/{id}")
    public SessionResponse get(@PathVariable UUID id) {
        return service.get(id);
    }

    @Operation(summary = "Change title and/or language")
    @PatchMapping("/{id}")
    public SessionResponse update(@PathVariable UUID id, @Valid @RequestBody UpdateSessionRequest req) {
        return service.update(id, req);
    }

    @Operation(summary = "Mark upload as finished; session moves to PROCESSING")
    @PostMapping("/{id}/finish")
    public SessionResponse finish(@PathVariable UUID id) {
        return service.finishUpload(id);
    }

    @Operation(summary = "Delete a session and its chunks")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }
}
