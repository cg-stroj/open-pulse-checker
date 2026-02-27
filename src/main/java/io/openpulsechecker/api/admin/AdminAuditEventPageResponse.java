package io.openpulsechecker.api.admin;

import java.util.List;

public record AdminAuditEventPageResponse(
        List<AdminAuditEventResponse> items,
        int page,
        int size,
        long totalItems,
        int totalPages,
        boolean hasNext,
        boolean hasPrevious
) {
}
