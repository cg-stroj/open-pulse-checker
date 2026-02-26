# Architecture (Phase 1 kickoff)

## Current modules
- **API Layer (`io.openpulsechecker.api`)**
  - REST endpoints under `/api/v1/monitors`
  - Health endpoint `/api/v1/health`
  - Validation + exception mapping
- **Service Layer (`io.openpulsechecker.service`)**
  - `MonitorService` for monitor CRUD operations
  - `CheckExecutionService` for manual HTTP check execution
  - `IncidentService` for incident state transitions
  - `DefaultHttpCheckClient` for outbound HTTP probing
- **Persistence Layer (`io.openpulsechecker.persistence`)**
  - JPA entities: Monitor, CheckResult, Incident
  - Spring Data repositories
  - Flyway migration `V1__init_phase1.sql`

## Data flow: manual run-check
1. Client calls `POST /api/v1/monitors/{id}/run-check`
2. API delegates to `CheckExecutionService`
3. Service loads monitor config, performs HTTP check with timeout
4. Check result is persisted in `check_results`
5. Incident transition is applied:
   - DOWN + no open incident → create OPEN incident
   - UP + open incident exists → mark RESOLVED
6. Result payload is returned to caller

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
