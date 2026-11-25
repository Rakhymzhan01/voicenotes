package com.example.voicenotes.session.dto;

import com.example.voicenotes.session.Language;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateSessionRequest(
        @Schema(example = "Lecture: Databases, week 12")
        @NotBlank @Size(max = 200)
        String title,

        @Schema(description = "Spoken language; AUTO if omitted", example = "MIXED")
        Language language
) {
}
