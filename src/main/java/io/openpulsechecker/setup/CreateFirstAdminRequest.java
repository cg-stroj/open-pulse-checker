package io.openpulsechecker.setup;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateFirstAdminRequest(
        @NotBlank @Size(max = 120) String username,
        @NotBlank @Size(min = 12, max = 200) String password,
        @NotBlank String setupToken
) {
}
