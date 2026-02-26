# Architecture (Phase 1 kickoff)

## Current modules
- **API Layer (`io.openpulsechecker.api`)**
  - REST endpoints under `/api/v1/monitors`
  - Health endpoint `/api/v1/health`
  - Validation + exception mapping
- **Service Layer (`io.openpulsechecker.service`)**
  - `MonitorService` for monitor CRUD operations
  - `CheckExecutionService` for manual and scheduler-driven HTTP check execution
  - `MonitorCheckScheduler` for periodic due-check dispatching
  - `IncidentService` for incident state transitions
  - `DefaultHttpCheckClient` for outbound HTTP probing
- **Alerting Layer (`io.openpulsechecker.alerting`)**
  - `AlertNotifier` abstraction
  - `AlertDispatchService` fan-out with notifier failure isolation
  - `WebhookAlertNotifier` config-driven outbound webhook implementation
- **Security Layer (`io.openpulsechecker.config`)**
  - `SecurityConfig` role-based API protection (ADMIN/VIEWER)
- **Persistence Layer (`io.openpulsechecker.persistence`)**
  - JPA entities: Monitor, CheckResult, Incident
  - Spring Data repositories
  - Flyway migration `V1__init_phase1.sql`

## Data flow: check execution (manual + scheduled)
1. Trigger source:
   - Manual: `POST /api/v1/monitors/{id}/run-check` (ADMIN only)
   - Scheduled: `MonitorCheckScheduler` polls enabled monitors and selects due checks
2. `CheckExecutionService` loads monitor config and performs HTTP check with timeout
3. Check result is persisted in `check_results`
4. `IncidentService` applies transition:
   - DOWN + no open incident → create OPEN incident + dispatch `INCIDENT_OPENED`
   - UP + open incident exists → mark RESOLVED + dispatch `INCIDENT_RESOLVED`
5. Alert dispatch fan-out executes notifiers; notifier failures are logged and isolated
6. Manual path returns result payload to caller; scheduled path continues asynchronously

## Security-first properties currently enforced
- Input constraints and server-side validation on monitor payloads
- URL scheme restrictions (HTTP/HTTPS only)
- Sanitized check error strings persisted with length bounds
- Flyway-managed schema + explicit constraints/indexes
- Non-secret local defaults for dev/test runtime

## Planned evolution
- scheduler module for periodic checks
- authn/authz module and policy enforcement
- split toward API/Agent/UI components as scope grows
