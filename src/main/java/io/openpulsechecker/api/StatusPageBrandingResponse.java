package io.openpulsechecker.api;

public record StatusPageBrandingResponse(
        String brandName,
        String brandTheme,
        String brandLogoUrl,
        String brandCustomHeader,
        String brandCustomFooter
) {
}
