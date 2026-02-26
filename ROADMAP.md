# Roadmap

## Phase 1 (In Progress)
Delivered in this kickoff slice:
- ✅ Core entities + persistence: Monitor, CheckResult, Incident
- ✅ Flyway baseline migration and schema indexes
- ✅ API v1 monitor endpoints (create/list/get/toggle enabled/manual run-check)
- ✅ HTTP check execution MVP with timeout handling
- ✅ Incident lifecycle transitions (OPEN on DOWN, RESOLVED on recovery)
- ✅ Unit + integration test baseline

Delivered Phase 1.1 increments:
- ✅ scheduler orchestration for periodic checks (bounded worker pool, due-time logic, anti-duplicate in-flight guard)
- ✅ basic authn/authz baseline (Spring Security, ADMIN/VIEWER roles, write endpoint protection)
- ✅ alerting pipeline baseline (notifier abstraction + webhook notifier)

Next Phase 1 increments:
- paging/filtering and audit improvements
- notification policy controls and retry strategy

## Phase 2
- Agent-based distributed checks
- Alert channels and notification policies
- Initial dashboard UI

## Phase 3
- Multi-tenant hardening
- Rule engine and anomaly detection
- Advanced deployment profiles (Kubernetes, air-gapped guidance)
