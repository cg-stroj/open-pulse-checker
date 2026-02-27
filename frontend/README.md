# Open Pulse Checker Frontend Foundation

React + TypeScript + Vite foundation for upcoming Open Pulse Checker web UI.

## Stack
- React 19 + TypeScript
- Vite 7
- Tailwind CSS v4 (tokenized theme)
- React Router (route skeleton)
- TanStack Query (query/cache baseline)
- Axios (API client)
- Sonner (toast notifications)

## Theme direction (BOS)
- Dark blue backgrounds (`bg-base`, `bg-elevated`, `bg-panel`)
- Gray typography (`text-primary`, `text-secondary`, `text-muted`)
- Orange accents (`accent`, `accent-strong`)

Tokens live in `src/index.css` via Tailwind `@theme` variables.

## Local run
```bash
cd frontend
cp .env.example .env
npm install
npm run dev
```

Default frontend URL: `http://localhost:5173`

## Environment
- `VITE_API_BASE_URL` (default fallback in code: `http://localhost:8080/api/v1`)

## Auth/session behavior
- Backend auth model is reused as-is (`HTTP Basic` + `ADMIN` role checks on `/api/v1/admin/**`).
- Frontend sign-in validates credentials against an admin endpoint and then keeps an in-tab session.
- Use a bootstrap/admin user from backend config (see root `README.md` bootstrap admin section).
- Signing out clears tab session immediately.

## Delivered modules
- App shell (`sidebar`, `topbar`, `content area`)
- Global providers (query client + router + toaster)
- Auth/session UX for admin API:
  - dedicated sign-in route (`/login`) backed by backend HTTP Basic credentials
  - protected admin route guard with redirect-to-login for unauthenticated access
  - global 401 handling: clears session, shows actionable toast, redirects to `/login`
  - global 403 handling: keeps session, shows access toast, redirects to `/unauthorized`
  - session state stored in `sessionStorage` only (tab-scoped, no password persisted directly)
- Error boundary + global query-fetching top indicator
- Dashboard primitives playground
- Incidents Console (`/incidents`):
  - incident list + detail/timeline view
  - search/filter/sort baseline
  - admin lifecycle actions (`acknowledge`, `annotate`, `resolve`, `reopen`)
  - state-transition guardrails with disabled action hints
  - annotation form with UX validation
  - best-effort manual audit/event history from `/api/v1/admin/incidents/{id}/events`
- Maintenance Windows (`/maintenance-windows`):
  - admin CRUD against `/api/v1/admin/maintenance-windows`
  - scope selector (`GLOBAL` / `MONITOR`) with monitor binding
  - type selector (`ONE_TIME` / `RECURRING`) with type-specific fields
  - policy selector (`SUPPRESS` / `ANNOTATE`) and enabled toggle
  - inline validation for required fields and time/day constraints
  - active-window summary preview (best-effort)
- Notification Policies (`/notification-policies`):
  - admin list/create/edit against `/api/v1/admin/notification-policies`
  - scope selector (`GLOBAL` / `STATUS_PAGE` / `MONITOR`) with scope ref binding
  - severity route-rule editor (webhook channel toggles)
  - cooldown + dedup numeric controls with inline validation
  - ordered escalation-step editor (delay/min severity/channel toggle)
  - best-effort effective policy summary for selected policy
- Audit Explorer (`/audit-explorer`):
  - paginated admin audit retrieval against `/api/v1/admin/audit-events`
  - baseline filters: global search, actor, action, resource, outcome, date range
  - CSV/JSON export flow via `/api/v1/admin/audit-events/export`
  - export success/failure toast feedback for operational troubleshooting
- Status Pages (`/status-pages`):
  - admin list/create against `/api/v1/status-pages`
  - monitor binding workspace (`attach`, `reorder`, `remove`) for selected page
  - integrated read-only public preview by slug from `/api/v1/public/status-pages/{slug}`
  - clear behavior for missing/non-public slug preview failures
- UI primitives:
  - `Button`, `Badge`
  - `DataTable`
  - `Modal`
  - `Field`, `TextInput`, `SelectInput`, `TextAreaInput`
  - `EmptyState`, `LoadingState`, `ErrorState`
  - toast helper (`notify`)

## FE quality gate (ticket #58)
- Accessibility baseline:
  - visible focus styles on interactive controls
  - skip-link to main content in app shell
  - modal semantics (`role="dialog"`, `aria-modal`, ESC + backdrop close)
  - labeled filter controls for incident search/sort
  - table semantics (`scope="col"`, table `aria-label` support)
- Responsiveness polish:
  - app shell adapts to tablet/mobile (nav wraps, compact header/content spacing)
  - key page headers and filter controls wrap safely on smaller widths
- E2E smoke:
  - Playwright smoke specs under `e2e/smoke.spec.ts`
  - covers major route navigation + one key admin action flow (status page create)

## Build / quality
```bash
npm run lint
npm run build
```

## Run E2E smoke tests
```bash
npm run test:e2e:smoke
```

If Playwright browsers are missing on a fresh machine:
```bash
npx playwright install chromium
```

## Known limitations
- Smoke tests use frontend-level API mocking to stay deterministic and low-risk.
- They validate critical UI flow wiring, not full backend contract behavior.
- Deeper cross-browser/a11y auditing (axe/pa11y, screen-reader QA) is not yet part of CI.
