package io.openpulsechecker.audit;

import java.time.Instant;
import java.util.List;
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
            where (:q is null or lower(e.username) like lower(concat('%', cast(:q as string), '%'))
                   or lower(e.action) like lower(concat('%', cast(:q as string), '%'))
                   or lower(e.target) like lower(concat('%', cast(:q as string), '%'))
                   or lower(e.result) like lower(concat('%', cast(:q as string), '%'))
                   or lower(cast(coalesce(e.details, '') as string)) like lower(concat('%', cast(:q as string), '%')))
              and (:actor is null or lower(e.username) like lower(concat('%', cast(:actor as string), '%')))
              and (:action is null or lower(e.action) like lower(concat('%', cast(:action as string), '%')))
              and (:resource is null or lower(e.target) like lower(concat('%', cast(:resource as string), '%')))
              and (:outcome is null or lower(e.result) = lower(cast(:outcome as string)))
              and e.occurredAt >= coalesce(:fromAt, e.occurredAt)
              and e.occurredAt <= coalesce(:toAt, e.occurredAt)
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

    @Query("""
            select e from AuditEventEntity e
            where (:q is null or lower(e.username) like lower(concat('%', cast(:q as string), '%'))
                   or lower(e.action) like lower(concat('%', cast(:q as string), '%'))
                   or lower(e.target) like lower(concat('%', cast(:q as string), '%'))
                   or lower(e.result) like lower(concat('%', cast(:q as string), '%'))
                   or lower(cast(coalesce(e.details, '') as string)) like lower(concat('%', cast(:q as string), '%')))
              and (:actor is null or lower(e.username) like lower(concat('%', cast(:actor as string), '%')))
              and (:action is null or lower(e.action) like lower(concat('%', cast(:action as string), '%')))
              and (:resource is null or lower(e.target) like lower(concat('%', cast(:resource as string), '%')))
              and (:outcome is null or lower(e.result) = lower(cast(:outcome as string)))
              and e.occurredAt >= coalesce(:fromAt, e.occurredAt)
              and e.occurredAt <= coalesce(:toAt, e.occurredAt)
              and e.occurredAt < coalesce(:cursorOccurredAt, CURRENT_TIMESTAMP)
            order by e.occurredAt desc, e.id desc
            """)
    List<AuditEventEntity> searchAfterCursor(
            @Param("q") String q,
            @Param("actor") String actor,
            @Param("action") String action,
            @Param("resource") String resource,
            @Param("outcome") String outcome,
            @Param("fromAt") Instant fromAt,
            @Param("toAt") Instant toAt,
            @Param("cursorOccurredAt") Instant cursorOccurredAt,
            Pageable pageable);
}
