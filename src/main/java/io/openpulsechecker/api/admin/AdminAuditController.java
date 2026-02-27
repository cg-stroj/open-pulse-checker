package io.openpulsechecker.api.admin;

import io.openpulsechecker.audit.AuditEventEntity;
import io.openpulsechecker.audit.AuditEventRepository;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
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

@RestController
@RequestMapping("/api/v1/admin/audit-events")
public class AdminAuditController {

    private static final int DEFAULT_PAGE_SIZE = 25;
    private static final int MAX_PAGE_SIZE = 200;
    private static final int MAX_EXPORT_SIZE = 5000;

    private final AuditEventRepository repository;

    public AdminAuditController(AuditEventRepository repository) {
        this.repository = repository;
    }

    @GetMapping
    public AdminAuditEventPageResponse list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "25") int size,
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String actor,
            @RequestParam(required = false) String action,
            @RequestParam(required = false) String resource,
            @RequestParam(required = false) String outcome,
            @RequestParam(required = false) Instant fromAt,
            @RequestParam(required = false) Instant toAt
    ) {
        PageRequest pageable = PageRequest.of(
                Math.max(page, 0),
                normalizeSize(size, DEFAULT_PAGE_SIZE, MAX_PAGE_SIZE),
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

    @GetMapping(value = "/export")
    public ResponseEntity<byte[]> export(
            @RequestParam(defaultValue = "csv") String format,
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String actor,
            @RequestParam(required = false) String action,
            @RequestParam(required = false) String resource,
            @RequestParam(required = false) String outcome,
            @RequestParam(required = false) Instant fromAt,
            @RequestParam(required = false) Instant toAt
    ) {
        PageRequest exportPageable = PageRequest.of(0, MAX_EXPORT_SIZE, Sort.by(Sort.Direction.DESC, "occurredAt"));
        List<AdminAuditEventResponse> items = repository.search(
                        normalizeQuery(q),
                        normalizeQuery(actor),
                        normalizeQuery(action),
                        normalizeQuery(resource),
                        normalizeQuery(outcome),
                        fromAt,
                        toAt,
                        exportPageable)
                .getContent()
                .stream()
                .map(this::toResponse)
                .toList();

        if ("json".equalsIgnoreCase(format)) {
            return ResponseEntity.ok()
                    .contentType(MediaType.APPLICATION_JSON)
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=audit-events.json")
                    .body(toJson(items).getBytes(StandardCharsets.UTF_8));
        }

        return ResponseEntity.ok()
                .contentType(new MediaType("text", "csv"))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=audit-events.csv")
                .body(toCsv(items).getBytes(StandardCharsets.UTF_8));
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
        StringBuilder json = new StringBuilder("[");
        DateTimeFormatter formatter = DateTimeFormatter.ISO_INSTANT;
        for (int i = 0; i < items.size(); i++) {
            AdminAuditEventResponse item = items.get(i);
            if (i > 0) json.append(',');
            json.append("{\"id\":\"").append(item.id()).append("\",")
                    .append("\"occurredAt\":\"").append(item.occurredAt() == null ? "" : formatter.format(item.occurredAt())).append("\",")
                    .append("\"actor\":").append(jsonString(item.actor())).append(',')
                    .append("\"action\":").append(jsonString(item.action())).append(',')
                    .append("\"resource\":").append(jsonString(item.resource())).append(',')
                    .append("\"outcome\":").append(jsonString(item.outcome())).append(',')
                    .append("\"details\":").append(jsonString(item.details()))
                    .append('}');
        }
        json.append(']');
        return json.toString();
    }

    private String jsonString(String value) {
        if (value == null) return "null";
        return '"' + value
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r") + '"';
    }
}
