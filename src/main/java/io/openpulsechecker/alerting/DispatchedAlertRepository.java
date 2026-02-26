package io.openpulsechecker.alerting;

import org.springframework.data.jpa.repository.JpaRepository;

public interface DispatchedAlertRepository extends JpaRepository<DispatchedAlertEntity, String> {
}
