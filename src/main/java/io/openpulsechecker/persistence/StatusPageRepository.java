package io.openpulsechecker.persistence;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface StatusPageRepository extends JpaRepository<StatusPageEntity, UUID>, JpaSpecificationExecutor<StatusPageEntity> {
    Optional<StatusPageEntity> findBySlug(String slug);
    boolean existsBySlug(String slug);
}
