package com.example.voicenotes.chunk;

import com.example.voicenotes.chunk.dto.ChunkResponse;
import com.example.voicenotes.common.ConflictException;
import com.example.voicenotes.common.NotFoundException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Web layer only (controller + validation + exception handler + JSON); the service is a mock.
 * Checks the HTTP contract: status codes, headers, error format.
 */
@WebMvcTest(ChunkController.class)
class ChunkControllerTest {

    private static final UUID SESSION_ID = UUID.randomUUID();
    private static final String URL = "/api/sessions/" + SESSION_ID + "/chunks";
    private static final String BODY = """
            {"sequenceNumber":0,"durationMs":30000,"sizeBytes":61440,"contentType":"audio/webm"}""";

    @Autowired MockMvc mvc;
    @MockitoBean ChunkService service;

    private static ChunkResponse chunk() {
        return new ChunkResponse(UUID.randomUUID(), 0, 30_000, 61_440, "audio/webm",
                ChunkStatus.UPLOADED, Instant.now());
    }

    @Test
    void firstUploadIs201() throws Exception {
        when(service.upload(eq(SESSION_ID), eq("k-0"), any()))
                .thenReturn(new ChunkService.UploadResult(chunk(), true));

        mvc.perform(post(URL).header("Idempotency-Key", "k-0")
                        .contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.sequenceNumber").value(0))
                .andExpect(jsonPath("$.status").value("UPLOADED"));
    }

    @Test
    void replayIs200() throws Exception {
        when(service.upload(eq(SESSION_ID), eq("k-0"), any()))
                .thenReturn(new ChunkService.UploadResult(chunk(), false));

        mvc.perform(post(URL).header("Idempotency-Key", "k-0")
                        .contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isOk());
    }

    @Test
    void missingIdempotencyKeyIs400() throws Exception {
        mvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Required header 'Idempotency-Key' is not present."));
        verifyNoInteractions(service);
    }

    @Test
    void invalidBodyListsEveryBadField() throws Exception {
        String bad = """
                {"sequenceNumber":-1,"durationMs":0,"contentType":"text/plain"}""";

        mvc.perform(post(URL).header("Idempotency-Key", "k-0")
                        .contentType(MediaType.APPLICATION_JSON).content(bad))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Validation failed"))
                .andExpect(jsonPath("$.errors.length()").value(4))
                .andExpect(jsonPath("$.errors[?(@.field == 'sizeBytes')]").exists())
                .andExpect(jsonPath("$.errors[?(@.field == 'contentType')]").exists());
        verifyNoInteractions(service);
    }

    @Test
    void blankIdempotencyKeyIsReportedByHeaderName() throws Exception {
        mvc.perform(post(URL).header("Idempotency-Key", " ")
                        .contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("Idempotency-Key"));
        verifyNoInteractions(service);
    }

    @Test
    void conflictIs409ProblemJson() throws Exception {
        when(service.upload(any(), any(), any())).thenThrow(new ConflictException("Chunk #0 already exists"));

        mvc.perform(post(URL).header("Idempotency-Key", "k-x")
                        .contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.detail").value("Chunk #0 already exists"));
    }

    @Test
    void unknownSessionIs404() throws Exception {
        when(service.upload(any(), any(), any())).thenThrow(new NotFoundException("Session not found"));

        mvc.perform(post(URL).header("Idempotency-Key", "k-0")
                        .contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isNotFound());
    }
}
