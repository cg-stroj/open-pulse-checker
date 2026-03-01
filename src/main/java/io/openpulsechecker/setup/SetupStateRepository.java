package io.openpulsechecker.setup;

import org.springframework.data.jpa.repository.JpaRepository;

public interface SetupStateRepository extends JpaRepository<SetupStateEntity, Integer> {
}
