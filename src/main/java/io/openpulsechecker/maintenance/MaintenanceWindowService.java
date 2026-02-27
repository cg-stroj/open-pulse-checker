package io.openpulsechecker.maintenance;

import io.openpulsechecker.persistence.MonitorRepository;
import io.openpulsechecker.service.ResourceNotFoundException;
import java.time.DateTimeException;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MaintenanceWindowService {

    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm");

    private final MaintenanceWindowRepository repository;
    private final MonitorRepository monitorRepository;

    public MaintenanceWindowService(MaintenanceWindowRepository repository, MonitorRepository monitorRepository) {
        this.repository = repository;
        this.monitorRepository = monitorRepository;
    }

    @Transactional(readOnly = true)
    public List<MaintenanceWindowModel> list() {
        return repository.findAll().stream().map(this::toModel).toList();
    }

    @Transactional(readOnly = true)
    public MaintenanceWindowModel get(UUID id) {
        return toModel(require(id));
    }

    @Transactional
    public MaintenanceWindowModel create(MaintenanceWindowModel input) {
        validate(input);
        MaintenanceWindowEntity entity = new MaintenanceWindowEntity();
        apply(entity, input);
        return toModel(repository.save(entity));
    }

    @Transactional
    public MaintenanceWindowModel update(UUID id, MaintenanceWindowModel input) {
        validate(input);
        MaintenanceWindowEntity entity = require(id);
        apply(entity, input);
        return toModel(repository.save(entity));
    }

    @Transactional
    public void delete(UUID id) {
        repository.delete(require(id));
    }

    @Transactional(readOnly = true)
    public MaintenanceEvaluation evaluate(UUID monitorId, Instant at) {
        List<MaintenanceWindowEntity> candidates = new ArrayList<>();
        candidates.addAll(repository.findByEnabledTrueAndScopeType(MaintenanceWindowScopeType.GLOBAL));
        candidates.addAll(repository.findByEnabledTrueAndScopeTypeAndScopeRefId(MaintenanceWindowScopeType.MONITOR, monitorId));

        List<MaintenanceWindowEntity> active = candidates.stream()
                .filter(window -> isActive(window, at))
                .sorted(Comparator
                        .comparing((MaintenanceWindowEntity w) -> w.getPolicy() != MaintenancePolicy.SUPPRESS)
                        .thenComparing(MaintenanceWindowEntity::getCreatedAt, Comparator.nullsLast(Comparator.naturalOrder()))
                        .thenComparing(w -> w.getId().toString()))
                .toList();

        if (active.isEmpty()) {
            return MaintenanceEvaluation.inactive();
        }
        MaintenanceWindowEntity chosen = active.getFirst();
        return new MaintenanceEvaluation(
                true,
                chosen.getPolicy(),
                chosen.getId(),
                chosen.getName(),
                "Maintenance window active: " + chosen.getName() + " (" + chosen.getPolicy().name() + ")"
        );
    }

    private boolean isActive(MaintenanceWindowEntity window, Instant at) {
        if (window.getType() == MaintenanceWindowType.ONE_TIME) {
            return !at.isBefore(window.getStartAt()) && at.isBefore(window.getEndAt());
        }

        ZoneId zone = ZoneId.of(window.getTimezone());
        ZonedDateTime local = at.atZone(zone);
        LocalTime time = local.toLocalTime();
        LocalTime start = parseTime(window.getRecurringStartTime());
        LocalTime end = parseTime(window.getRecurringEndTime());
        Set<DayOfWeek> days = parseDays(window.getRecurringDays());

        if (end.isAfter(start)) {
            return days.contains(local.getDayOfWeek()) && !time.isBefore(start) && time.isBefore(end);
        }

        DayOfWeek previous = local.minusDays(1).getDayOfWeek();
        boolean sameDayActive = days.contains(local.getDayOfWeek()) && !time.isBefore(start);
        boolean previousDayCarry = days.contains(previous) && time.isBefore(end);
        return sameDayActive || previousDayCarry;
    }

    private Set<DayOfWeek> parseDays(String raw) {
        EnumSet<DayOfWeek> days = EnumSet.noneOf(DayOfWeek.class);
        for (String token : raw.split(",")) {
            days.add(DayOfWeek.valueOf(token.trim().toUpperCase(Locale.ROOT)));
        }
        return days;
    }

    private LocalTime parseTime(String raw) {
        return LocalTime.parse(raw, TIME_FMT);
    }

    private void apply(MaintenanceWindowEntity entity, MaintenanceWindowModel input) {
        entity.setName(input.name().trim());
        entity.setScopeType(input.scopeType());
        entity.setScopeRefId(input.scopeRefId());
        entity.setType(input.type());
        entity.setPolicy(input.policy());
        entity.setEnabled(input.enabled());

        if (input.type() == MaintenanceWindowType.ONE_TIME) {
            entity.setStartAt(input.startAt());
            entity.setEndAt(input.endAt());
            entity.setTimezone(null);
            entity.setRecurringDays(null);
            entity.setRecurringStartTime(null);
            entity.setRecurringEndTime(null);
        } else {
            entity.setStartAt(null);
            entity.setEndAt(null);
            entity.setTimezone(input.timezone());
            entity.setRecurringDays(String.join(",", input.recurringDays()));
            entity.setRecurringStartTime(input.recurringStartTime());
            entity.setRecurringEndTime(input.recurringEndTime());
        }
    }

    private MaintenanceWindowModel toModel(MaintenanceWindowEntity entity) {
        List<String> days = entity.getRecurringDays() == null || entity.getRecurringDays().isBlank()
                ? List.of()
                : List.of(entity.getRecurringDays().split(","));
        return new MaintenanceWindowModel(
                entity.getId(),
                entity.getName(),
                entity.getScopeType(),
                entity.getScopeRefId(),
                entity.getType(),
                entity.getPolicy(),
                entity.isEnabled(),
                entity.getStartAt(),
                entity.getEndAt(),
                entity.getTimezone(),
                days,
                entity.getRecurringStartTime(),
                entity.getRecurringEndTime(),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }

    private void validate(MaintenanceWindowModel input) {
        if (input.name() == null || input.name().isBlank()) {
            throw new IllegalArgumentException("name is required");
        }
        if (input.scopeType() == MaintenanceWindowScopeType.GLOBAL && input.scopeRefId() != null) {
            throw new IllegalArgumentException("GLOBAL scope does not accept scopeRefId");
        }
        if (input.scopeType() == MaintenanceWindowScopeType.MONITOR) {
            if (input.scopeRefId() == null) {
                throw new IllegalArgumentException("scopeRefId is required for MONITOR scope");
            }
            if (!monitorRepository.existsById(input.scopeRefId())) {
                throw new ResourceNotFoundException("Monitor not found: " + input.scopeRefId());
            }
        }
        if (input.type() == MaintenanceWindowType.ONE_TIME) {
            if (input.startAt() == null || input.endAt() == null) {
                throw new IllegalArgumentException("startAt/endAt are required for ONE_TIME windows");
            }
            if (!input.endAt().isAfter(input.startAt())) {
                throw new IllegalArgumentException("endAt must be after startAt");
            }
            return;
        }

        if (input.timezone() == null || input.timezone().isBlank()) {
            throw new IllegalArgumentException("timezone is required for RECURRING windows");
        }
        try {
            ZoneId.of(input.timezone());
        } catch (DateTimeException ex) {
            throw new IllegalArgumentException("Invalid timezone");
        }
        if (input.recurringDays() == null || input.recurringDays().isEmpty()) {
            throw new IllegalArgumentException("recurringDays is required for RECURRING windows");
        }
        try {
            parseDays(String.join(",", input.recurringDays()));
            parseTime(input.recurringStartTime());
            parseTime(input.recurringEndTime());
        } catch (Exception ex) {
            throw new IllegalArgumentException("Invalid recurring day/time definition");
        }
    }

    private MaintenanceWindowEntity require(UUID id) {
        return repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Maintenance window not found: " + id));
    }
}
