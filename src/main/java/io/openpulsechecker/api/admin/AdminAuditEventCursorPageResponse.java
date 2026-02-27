package io.openpulsechecker.api.admin;

import java.util.List;

public record AdminAuditEventCursorPageResponse(
        List<AdminAuditEventResponse> items,
        int size,
        String nextCursor,
        boolean hasNext
) {
}
