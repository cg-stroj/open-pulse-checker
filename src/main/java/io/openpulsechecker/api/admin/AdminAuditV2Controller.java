package io.openpulsechecker.api.admin;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.openpulsechecker.audit.AuditEventEntity;
import io.openpulsechecker.audit.AuditEventRepository;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import static org.springframework.http.HttpStatus.BAD_REQUEST;

@RestController
@RequestMapping("/api/v2/admin/audit-events")
public class AdminAuditV2Controller {

    private static final int DEFAULT_PAGE_SIZE = 25;
    private static final int MAX_PAGE_SIZE = 200;
    private static final int MAX_EXPORT_SIZE = 5000;

    private final AuditEventRepository repository;
    private final ObjectMapper objectMapper;

    public AdminAuditV2Controller(AuditEventRepository repository, ObjectMapper objectMapper) {
        this.repository = repository;
        this.objectMapper = objectMapper;
    }

    @GetMapping
    public Object list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "25") int size,
            @RequestParam(required = false) String cursor,
            @RequestParam(defaultValue = "false") boolean cursorMode,
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String actor,
            @RequestParam(required = false) String action,
            @RequestParam(required = false) String resource,
            @RequestParam(required = false) String outcome,
            @RequestParam(required = false) Instant fromAt,
            @RequestParam(required = false) Instant toAt
    ) {
        int normalizedSize = normalizeSize(size, DEFAULT_PAGE_SIZE, MAX_PAGE_SIZE);
        if (cursorMode || (cursor != null && !cursor.isBlank())) {
            Instant cursorOccurredAt = (cursor == null || cursor.isBlank()) ? null : decodeCursor(cursor);
            List<AuditEventEntity> rows = repository.searchAfterCursor(
                    normalizeQuery(q),
                    normalizeQuery(actor),
                    normalizeQuery(action),
                    normalizeQuery(resource),
                    normalizeQuery(outcome),
                    fromAt,
                    toAt,
                    cursorOccurredAt,
                    PageRequest.of(0, normalizedSize + 1, Sort.by(Sort.Direction.DESC, "occurredAt").and(Sort.by(Sort.Direction.DESC, "id"))));
            boolean hasNext = rows.size() > normalizedSize;
            List<AuditEventEntity> sliced = hasNext ? rows.subList(0, normalizedSize) : rows;
            String nextCursor = hasNext ? encodeCursor(sliced.get(sliced.size() - 1).getOccurredAt()) : null;
            return new AdminAuditEventCursorPageResponse(sliced.stream().map(this::toResponse).toList(), normalizedSize, nextCursor, hasNext);
        }

        PageRequest pageable = PageRequest.of(
                Math.max(page, 0),
                normalizedSize,
                Sort.by(Sort.Direction.DESC, "occurredAt"));

        Page<AuditEventEntity> result = repository.search(
                normalizeQuery(q),
                normalizeQuery(actor),
                normalizeQuery(action),
                normalizeQuery(resource),
                normalizeQuery(outcome),
                fromAt,
                toAt,
                pageable);

        return new AdminAuditEventPageResponse(
                result.getContent().stream().map(this::toResponse).toList(),
                result.getNumber(),
                result.getSize(),
                result.getTotalElements(),
                result.getTotalPages(),
                result.hasNext(),
                result.hasPrevious());
    }

    @GetMapping("/export")
    public ResponseEntity<byte[]> export(
            @RequestParam(defaultValue = "csv") String format,
            @RequestParam(defaultValue = "1000") int limit,
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String actor,
            @RequestParam(required = false) String action,
            @RequestParam(required = false) String resource,
            @RequestParam(required = false) String outcome,
            @RequestParam(required = false) Instant fromAt,
            @RequestParam(required = false) Instant toAt
    ) {
        int exportLimit = normalizeSize(limit, 1000, MAX_EXPORT_SIZE);
        List<AdminAuditEventResponse> items = repository.search(
                        normalizeQuery(q),
                        normalizeQuery(actor),
                        normalizeQuery(action),
                        normalizeQuery(resource),
                        normalizeQuery(outcome),
                        fromAt,
                        toAt,
                        PageRequest.of(0, exportLimit, Sort.by(Sort.Direction.DESC, "occurredAt")))
                .getContent()
                .stream()
                .map(this::toResponse)
                .toList();

        if ("json".equalsIgnoreCase(format)) {
            return ResponseEntity.ok()
                    .contentType(MediaType.APPLICATION_JSON)
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=audit-events-v2.json")
                    .body(toJson(items).getBytes(StandardCharsets.UTF_8));
        }

        if (!"csv".equalsIgnoreCase(format)) {
            throw new ResponseStatusException(BAD_REQUEST, "Unsupported format: " + format + ". Use csv or json.");
        }

        return ResponseEntity.ok()
                .contentType(new MediaType("text", "csv"))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=audit-events-v2.csv")
                .body(toCsv(items).getBytes(StandardCharsets.UTF_8));
    }

    private Instant decodeCursor(String cursor) {
        try {
            String decoded = new String(Base64.getUrlDecoder().decode(cursor), StandardCharsets.UTF_8);
            return Instant.parse(decoded);
        } catch (Exception e) {
            throw new ResponseStatusException(BAD_REQUEST, "Invalid cursor");
        }
    }

    private String encodeCursor(Instant occurredAt) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(occurredAt.toString().getBytes(StandardCharsets.UTF_8));
    }

    private AdminAuditEventResponse toResponse(AuditEventEntity entity) {
        return new AdminAuditEventResponse(
                entity.getId(),
                entity.getUsername(),
                entity.getAction(),
                entity.getTarget(),
                entity.getResult(),
                entity.getDetails(),
                entity.getOccurredAt());
    }

    private int normalizeSize(int requested, int fallback, int max) {
        if (requested <= 0) return fallback;
        return Math.min(requested, max);
    }

    private String normalizeQuery(String value) {
        if (value == null) return null;
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private String toCsv(List<AdminAuditEventResponse> items) {
        StringBuilder csv = new StringBuilder("id,occurredAt,actor,action,resource,outcome,details\n");
        for (AdminAuditEventResponse item : items) {
            csv.append(csvValue(item.id() == null ? "" : item.id().toString())).append(',')
                    .append(csvValue(item.occurredAt() == null ? "" : item.occurredAt().toString())).append(',')
                    .append(csvValue(item.actor())).append(',')
                    .append(csvValue(item.action())).append(',')
                    .append(csvValue(item.resource())).append(',')
                    .append(csvValue(item.outcome())).append(',')
                    .append(csvValue(item.details()))
                    .append('\n');
        }
        return csv.toString();
    }

    private String csvValue(String raw) {
        String value = raw == null ? "" : raw;
        return '"' + value.replace("\"", "\"\"") + '"';
    }

    private String toJson(List<AdminAuditEventResponse> items) {
        try {
            return objectMapper.writeValueAsString(items);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize audit export", e);
        }
    }
}
