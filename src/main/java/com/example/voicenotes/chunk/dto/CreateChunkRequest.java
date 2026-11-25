package com.example.voicenotes.chunk.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;

/** Wrapper types + @NotNull so a missing field is a 400, not a silent 0. */
public record CreateChunkRequest(
        @Schema(description = "Position of the chunk in the recording, from 0", example = "0")
        @NotNull @PositiveOrZero
        Integer sequenceNumber,

        @Schema(example = "30000")
        @NotNull @Positive @Max(600_000)
        Integer durationMs,

        @Schema(example = "61440")
        @NotNull @Positive @Max(100L * 1024 * 1024)
        Long sizeBytes,

        @Schema(example = "audio/webm")
        @NotBlank
        @Pattern(regexp = "(audio|video)/[\\w.+-]+", message = "must be an audio/* or video/* MIME type")
        String contentType
) {
}
