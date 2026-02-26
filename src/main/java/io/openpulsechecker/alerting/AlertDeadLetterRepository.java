package io.openpulsechecker.alerting;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AlertDeadLetterRepository extends JpaRepository<AlertDeadLetterEntity, UUID> {
}
