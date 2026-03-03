# Ticket #100 QA Report — Advanced Monitors (TCP/API/Keywords)

Date (UTC): 2026-03-03

## Scope verified
- HTTP monitor with custom method
- TCP monitor
- PING monitor
- `expectedResponseKeyword` behavior for HTTP
- Frontend lint/build for monitor UI integration

## Commands executed

### Backend tests
1. Focused service test (pass):
```bash
mvn -Dtest=CheckExecutionServiceTest test
```
Result: **PASS** (`Tests run: 6, Failures: 0, Errors: 0, Skipped: 0`)

2. Service + API integration slice (environment-limited):
```bash
mvn -Dtest=CheckExecutionServiceTest,MonitorApiIntegrationTest test
```
Result: **PARTIAL**
- `CheckExecutionServiceTest`: PASS
- `MonitorApiIntegrationTest`: FAIL to boot test context due DB auth
- Error: `FATAL: password authentication failed for user "openpulse"`

### Frontend checks
1. Lint:
```bash
npm run lint
```
Result: **PASS**

2. Build:
```bash
npm run build
```
Result: **PASS** (Vite production build completed)

## Behavior verification (code + tests)

### HTTP custom method
- Verified in service test: `usesConfiguredHttpMethodAndKeyword`
- Expected behavior: configured method (e.g., `PATCH`) passed to HTTP check client; omitted method defaults to `GET`.

### TCP monitor
- Verified in service test: `persistsUpResultForTcpOutcome`
- Expected behavior: check execution uses TCP client path; status code is null for non-HTTP checks.

### PING monitor
- Verified in service test: `persistsDownResultForPingOutcome`
- Expected behavior: check execution uses PING client path and persists DOWN/error payload when unreachable.

### HTTP expectedResponseKeyword
- Verified in service test: `persistsDownWhenExpectedKeywordDoesNotMatch`
- Expected behavior: when keyword is configured but not present in response body, result is DOWN with:
  - `Expected response keyword not found: <keyword>`

## Environment limitations
- Full Spring Boot API integration (`MonitorApiIntegrationTest`) could not be executed because local PostgreSQL credentials are not valid in current environment.
- Because of this, complete backend E2E API verification is **blocked by environment**, not by compile/lint/unit-test failures.

## BOS manual UI verification checklist

1. Open `/monitors` as ADMIN.
2. Create **HTTP** monitor:
   - Set URL to reachable endpoint.
   - Change HTTP method from default GET to another method (e.g., HEAD/POST/PATCH depending on endpoint support).
   - Save and run check; confirm method is persisted and used.
3. For same HTTP monitor, set `Expected response keyword`:
   - Positive case: keyword present → check UP (assuming status code success).
   - Negative case: keyword absent → check DOWN and error contains `Expected response keyword not found`.
4. Create **TCP** monitor:
   - Use target format `host:port` (e.g., `localhost:5432`).
   - Save and run check; verify no HTTP method/keyword fields are active and check executes via TCP path.
5. Create **PING** monitor:
   - Use URL target.
   - Save and run check; verify HTTP-only fields are hidden/ignored.
6. Edit existing monitors across types:
   - Switch HTTP → TCP/PING and confirm HTTP-only fields are cleared/ignored.
   - Switch back to HTTP and confirm method defaults to GET if unset.
7. API quick checks (optional via network tab or API client):
   - `POST /api/v1/monitors` and `PUT /api/v1/monitors/{id}` payloads reflect type-specific fields.
   - `run-check` result data reflects expected UP/DOWN transitions for scenarios above.

## Recommendation
- **Ready for commit with environment note**:
  - Frontend quality gates pass.
  - Targeted backend service tests pass for advanced monitor logic.
  - Integration tests are blocked by DB credential mismatch and should be re-run in correctly configured QA environment before merge/release.
