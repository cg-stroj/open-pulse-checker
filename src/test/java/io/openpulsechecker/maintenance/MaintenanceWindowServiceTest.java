package io.openpulsechecker.maintenance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

import io.openpulsechecker.persistence.MonitorRepository;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class MaintenanceWindowServiceTest {

    @Mock
    private MaintenanceWindowRepository repository;
    @Mock
    private MonitorRepository monitorRepository;

    @InjectMocks
    private MaintenanceWindowService service;

    @Test
    void oneTimeWindowEvaluatesInUtcRange() {
        UUID monitorId = UUID.randomUUID();
        MaintenanceWindowEntity window = new MaintenanceWindowEntity();
        window.setId(UUID.randomUUID());
        window.setName("deploy");
        window.setScopeType(MaintenanceWindowScopeType.GLOBAL);
        window.setType(MaintenanceWindowType.ONE_TIME);
        window.setPolicy(MaintenancePolicy.SUPPRESS);
        window.setEnabled(true);
        window.setStartAt(Instant.parse("2026-02-27T10:00:00Z"));
        window.setEndAt(Instant.parse("2026-02-27T11:00:00Z"));

        when(repository.findByEnabledTrueAndScopeType(MaintenanceWindowScopeType.GLOBAL)).thenReturn(List.of(window));
        when(repository.findByEnabledTrueAndScopeTypeAndScopeRefId(MaintenanceWindowScopeType.MONITOR, monitorId)).thenReturn(List.of());

        assertTrue(service.evaluate(monitorId, Instant.parse("2026-02-27T10:30:00Z")).active());
        assertFalse(service.evaluate(monitorId, Instant.parse("2026-02-27T11:00:00Z")).active());
    }

    @Test
    void recurringWindowUsesTimezoneAndSupportsOvernight() {
        UUID monitorId = UUID.randomUUID();
        MaintenanceWindowEntity window = new MaintenanceWindowEntity();
        window.setId(UUID.randomUUID());
        window.setName("nightly");
        window.setScopeType(MaintenanceWindowScopeType.GLOBAL);
        window.setType(MaintenanceWindowType.RECURRING);
        window.setPolicy(MaintenancePolicy.ANNOTATE);
        window.setEnabled(true);
        window.setTimezone("Europe/Berlin");
        window.setRecurringDays("MONDAY");
        window.setRecurringStartTime("23:00");
        window.setRecurringEndTime("02:00");

        when(repository.findByEnabledTrueAndScopeType(MaintenanceWindowScopeType.GLOBAL)).thenReturn(List.of(window));
        when(repository.findByEnabledTrueAndScopeTypeAndScopeRefId(MaintenanceWindowScopeType.MONITOR, monitorId)).thenReturn(List.of());

        assertTrue(service.evaluate(monitorId, Instant.parse("2026-03-02T22:30:00Z")).active());
        assertTrue(service.evaluate(monitorId, Instant.parse("2026-03-03T00:30:00Z")).active());
        assertFalse(service.evaluate(monitorId, Instant.parse("2026-03-03T02:00:00Z")).active());
    }

    @Test
    void suppressPolicyWinsOverAnnotateWhenBothActive() {
        UUID monitorId = UUID.randomUUID();
        MaintenanceWindowEntity annotate = new MaintenanceWindowEntity();
        annotate.setId(UUID.fromString("00000000-0000-0000-0000-000000000002"));
        annotate.setName("annotate");
        annotate.setScopeType(MaintenanceWindowScopeType.GLOBAL);
        annotate.setType(MaintenanceWindowType.ONE_TIME);
        annotate.setPolicy(MaintenancePolicy.ANNOTATE);
        annotate.setEnabled(true);
        annotate.setStartAt(Instant.parse("2026-02-27T10:00:00Z"));
        annotate.setEndAt(Instant.parse("2026-02-27T11:00:00Z"));

        MaintenanceWindowEntity suppress = new MaintenanceWindowEntity();
        suppress.setId(UUID.fromString("00000000-0000-0000-0000-000000000001"));
        suppress.setName("suppress");
        suppress.setScopeType(MaintenanceWindowScopeType.GLOBAL);
        suppress.setType(MaintenanceWindowType.ONE_TIME);
        suppress.setPolicy(MaintenancePolicy.SUPPRESS);
        suppress.setEnabled(true);
        suppress.setStartAt(Instant.parse("2026-02-27T10:00:00Z"));
        suppress.setEndAt(Instant.parse("2026-02-27T11:00:00Z"));

        when(repository.findByEnabledTrueAndScopeType(MaintenanceWindowScopeType.GLOBAL)).thenReturn(List.of(annotate, suppress));
        when(repository.findByEnabledTrueAndScopeTypeAndScopeRefId(MaintenanceWindowScopeType.MONITOR, monitorId)).thenReturn(List.of());

        MaintenanceEvaluation evaluation = service.evaluate(monitorId, Instant.parse("2026-02-27T10:15:00Z"));
        assertEquals(MaintenancePolicy.SUPPRESS, evaluation.policy());
        assertEquals("suppress", evaluation.windowName());
    }
}
