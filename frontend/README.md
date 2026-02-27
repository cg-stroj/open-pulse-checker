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

## Delivered modules
- App shell (`sidebar`, `topbar`, `content area`)
- Global providers (query client + router + toaster)
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

## Build / quality
```bash
npm run lint
npm run build
```
