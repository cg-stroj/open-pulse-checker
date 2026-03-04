package io.openpulsechecker.api;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record UpdateStatusPageRequest(
        @Size(max = 120) String name,
        @Size(max = 80) @Pattern(regexp = "^[a-z0-9]+(?:-[a-z0-9]+)*$", message = "must be lowercase letters, numbers, and hyphens") String slug,
        Boolean isPublic,
        @Size(max = 120) String brandName,
        @Size(max = 32) String brandTheme,
        @Size(max = 1024) String brandLogoUrl,
        @Size(max = 240) String brandCustomHeader,
        @Size(max = 500) String brandCustomFooter
) {
}
