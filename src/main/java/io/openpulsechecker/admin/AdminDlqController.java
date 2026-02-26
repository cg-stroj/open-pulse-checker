package io.openpulsechecker.admin;

import io.openpulsechecker.alerting.AlertDeadLetterService;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/dlq")
public class AdminDlqController {

    private final AlertDeadLetterService service;

    public AdminDlqController(AlertDeadLetterService service) {
        this.service = service;
    }

    @PostMapping("/{id}/replay")
    public ResponseEntity<Map<String, String>> replay(@PathVariable UUID id) {
        service.replay(id);
        return ResponseEntity.ok(Map.of("status", "replayed", "id", id.toString()));
    }
}
