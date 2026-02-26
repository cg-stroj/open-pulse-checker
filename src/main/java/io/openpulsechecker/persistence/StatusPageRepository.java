package io.openpulsechecker.persistence;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StatusPageRepository extends JpaRepository<StatusPageEntity, UUID> {
    Optional<StatusPageEntity> findBySlug(String slug);
    boolean existsBySlug(String slug);
}
