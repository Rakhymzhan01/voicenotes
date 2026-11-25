package com.example.voicenotes.session;

import com.example.voicenotes.common.InvalidStateException;
import com.example.voicenotes.session.dto.SessionResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.UUID;

import static org.hamcrest.Matchers.endsWith;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(SessionController.class)
class SessionControllerTest {

    @Autowired MockMvc mvc;
    @MockitoBean SessionService service;

    @Test
    void createReturns201WithLocation() throws Exception {
        UUID id = UUID.randomUUID();
        when(service.create(any())).thenReturn(new SessionResponse(id, "Lecture", Language.MIXED,
                SessionStatus.CREATED, 0, 0, Instant.now(), Instant.now()));

        mvc.perform(post("/api/sessions").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"Lecture","language":"MIXED"}"""))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", endsWith("/api/sessions/" + id)))
                .andExpect(jsonPath("$.status").value("CREATED"));
    }

    @Test
    void blankTitleIs400() throws Exception {
        mvc.perform(post("/api/sessions").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"   "}"""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("title"));
        verifyNoInteractions(service);
    }

    @Test
    void unknownLanguageIs400() throws Exception {
        mvc.perform(post("/api/sessions").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"Lecture","language":"FR"}"""))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }

    @Test
    void pageSizeAbove100Is400() throws Exception {
        mvc.perform(get("/api/sessions").param("size", "500"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("size"));
        verifyNoInteractions(service);
    }

    @Test
    void malformedIdIs400() throws Exception {
        mvc.perform(get("/api/sessions/not-a-uuid"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void invalidTransitionIs409() throws Exception {
        UUID id = UUID.randomUUID();
        when(service.finishUpload(id)).thenThrow(new InvalidStateException("Only an UPLOADING session can be finished"));

        mvc.perform(post("/api/sessions/{id}/finish", id))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("Only an UPLOADING session can be finished"));
    }
}
