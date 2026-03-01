# Onboarding QA Smoke + Regression Matrix (Ticket #84)

Date: 2026-03-01 (UTC)

## Scope executed
- End-to-end onboarding flow coverage: clean install context -> setup wizard -> first login -> setup lock -> re-login gate.
- Negative scenarios: expired token, weak password, duplicate setup attempt.
- Backend/frontend consistency check after tickets #81/#82/#83.

## QA Matrix

| Scenario | Expected | Actual | Status |
|---|---|---|---|
| Setup status before first admin | `setupRequired=true`, token issued, setup unlocked | Integration test confirms token issuance and unlocked setup state | ✅ PASS |
| First admin creation through setup wizard | `201`, admin created, redirect to login | Playwright smoke verifies wizard completion and redirect to `Admin sign in` | ✅ PASS |
| Setup lock after first admin | Setup becomes locked and token no longer returned | Integration test verifies `setupRequired=false`, `setupLocked=true`, token fields null | ✅ PASS |
| Re-login gate after onboarding complete | `/setup` should not be accessible, route redirects to login/dashboard | Playwright smoke confirms `/setup` redirects to `/login` for unauthenticated user | ✅ PASS |
| Expired setup token (negative) | API rejects with `400 Invalid or expired setup token`; UI surfaces token error | Backend integration validates `400` + error string; Playwright validates error message in setup form | ✅ PASS |
| Weak password (negative) | Password < 12 chars rejected (`400` backend, inline validation frontend) | Backend integration validates `400` and password validation error; Playwright validates inline `Password must be at least 12 characters long.` | ✅ PASS |
| Duplicate setup attempt (negative) | Second/duplicate attempt rejected with `409 Setup is already completed` and UI conflict message | Backend integration verifies post-lock duplicate returns `409`; Playwright validates conflict message mapping | ✅ PASS |
| Backend/frontend behavior consistency | Error semantics and setup lock behavior align across API and UI | Error mappings and flow behavior observed consistent in both suites | ✅ PASS |

## Evidence (commands + key outputs)

### 1) Backend integration (initial run, env constraint observed)
Command:
```bash
mvn -Dtest=SetupApiIntegrationTest test
```
Key output:
- Failed due to DB auth: `FATAL: password authentication failed for user "openpulse"`

### 2) Backend integration (constraint resolved + rerun)
Command:
```bash
OPENPULSE_DB_PASSWORD=change-me-local-dev mvn -Dtest=SetupApiIntegrationTest test
```
Key output:
- `Tests run: 4, Failures: 0, Errors: 0, Skipped: 0`
- `BUILD SUCCESS`

### 3) Frontend onboarding smoke/e2e
Command:
```bash
cd frontend && npm run test:e2e:smoke
```
Key output:
- `Running 7 tests`
- `7 passed`

## Files changed for Ticket #84
- `src/test/java/io/openpulsechecker/api/SetupApiIntegrationTest.java`
  - Added weak-password negative integration coverage.
- `frontend/e2e/smoke.spec.ts`
  - Added onboarding negative smoke coverage for weak password, expired token, duplicate setup conflict.
- `docs/onboarding-qa-matrix-report-2026-03-01.md`
  - Added QA matrix and run evidence.

## Risks / open gaps
- Backend integration tests require reachable PostgreSQL with correct credentials in this environment; without env override (`OPENPULSE_DB_PASSWORD`) suite fails at context boot.
- Frontend smoke suite uses API routing mocks (fast and deterministic); it validates UI/API contract behavior but is not a full live backend-browser integration environment.

## Final verdict
**Ready for BOS review** for Ticket #84 scope.

All required onboarding smoke + regression scenarios (including required negatives) were executed and passed after resolving environment credential mismatch.
