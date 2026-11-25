package com.example.voicenotes.session.dto;

import com.example.voicenotes.session.Language;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** PATCH semantics: null field = leave unchanged. */
public record UpdateSessionRequest(
        @Size(max = 200)
        @Pattern(regexp = ".*\\S.*", message = "must not be blank")
        String title,

        Language language
) {
}
