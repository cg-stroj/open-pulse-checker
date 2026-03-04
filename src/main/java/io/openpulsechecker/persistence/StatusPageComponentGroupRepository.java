package io.openpulsechecker.persistence;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StatusPageComponentGroupRepository extends JpaRepository<StatusPageComponentGroupEntity, UUID> {
    List<StatusPageComponentGroupEntity> findByStatusPageIdOrderByDisplayOrderAsc(UUID statusPageId);
    void deleteByStatusPageId(UUID statusPageId);
}
