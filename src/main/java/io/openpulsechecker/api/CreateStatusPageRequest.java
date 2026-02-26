package io.openpulsechecker.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CreateStatusPageRequest(
        @NotBlank @Size(max = 120) String name,
        @NotBlank @Size(max = 80) @Pattern(regexp = "^[a-z0-9]+(?:-[a-z0-9]+)*$", message = "must be lowercase letters, numbers, and hyphens") String slug,
        Boolean isPublic
) {
}
