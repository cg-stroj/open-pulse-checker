package io.openpulsechecker.api;

import java.util.List;

public record PagedResponse<T>(
        List<T> items,
        int page,
        int size,
        long total,
        int totalPages,
        boolean hasNext,
        boolean hasPrevious
) {
}
