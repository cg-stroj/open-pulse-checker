package io.openpulsechecker.audit;

import java.time.Clock;
import java.time.Instant;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

@Service
public class AuditService {

    private final AuditEventRepository repository;
    private final Clock clock;

    public AuditService(AuditEventRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    public void log(String action, String target, String result, String details) {
        String username = currentUsername();
        log(username, action, target, result, details);
    }

    public void log(String username, String action, String target, String result, String details) {
        AuditEventEntity event = new AuditEventEntity();
        event.setUsername(username == null || username.isBlank() ? "anonymous" : username);
        event.setAction(action);
        event.setTarget(target);
        event.setResult(result);
        event.setDetails(details);
        event.setOccurredAt(Instant.now(clock));
        repository.save(event);
    }

    private String currentUsername() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null) return "system";
        return auth.getName();
    }
}
