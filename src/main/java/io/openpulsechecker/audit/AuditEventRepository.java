package io.openpulsechecker.audit;

import java.time.Instant;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AuditEventRepository extends JpaRepository<AuditEventEntity, UUID> {
    AuditEventEntity findTopByOrderByOccurredAtDesc();

    @Query("""
            select e from AuditEventEntity e
            where (:q is null or lower(e.username) like lower(concat('%', :q, '%'))
                   or lower(e.action) like lower(concat('%', :q, '%'))
                   or lower(e.target) like lower(concat('%', :q, '%'))
                   or lower(e.result) like lower(concat('%', :q, '%'))
                   or lower(coalesce(e.details, '')) like lower(concat('%', :q, '%')))
              and (:actor is null or lower(e.username) like lower(concat('%', :actor, '%')))
              and (:action is null or lower(e.action) like lower(concat('%', :action, '%')))
              and (:resource is null or lower(e.target) like lower(concat('%', :resource, '%')))
              and (:outcome is null or lower(e.result) = lower(:outcome))
              and (:fromAt is null or e.occurredAt >= :fromAt)
              and (:toAt is null or e.occurredAt <= :toAt)
            """)
    Page<AuditEventEntity> search(
            @Param("q") String q,
            @Param("actor") String actor,
            @Param("action") String action,
            @Param("resource") String resource,
            @Param("outcome") String outcome,
            @Param("fromAt") Instant fromAt,
            @Param("toAt") Instant toAt,
            Pageable pageable);
}
