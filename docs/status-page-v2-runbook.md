# Status Page v2 Runbook

## Scope
Status Page v2 introduces:
- Component groups for monitor organization
- Scheduled maintenance announcements with publish/start/end windows
- Branding metadata (name/theme/logo/custom header/footer)

## Admin setup flow
1. Create status page: `POST /api/v1/status-pages`
2. Save branding: `PUT /api/v1/status-pages/{id}` with branding fields.
3. Save v2 config: `PUT /api/v1/status-pages/{id}/config` with:
   - `componentGroups[]`
   - `monitorBindings[]` (optionally assign `componentGroupId`)
   - `maintenanceAnnouncements[]`
4. Verify config: `GET /api/v1/status-pages/{id}/config`
5. Verify public rendering: `GET /api/v1/public/status-pages/{slug}`

## Timing behavior (maintenance)
Announcement is shown publicly only when all are true:
- `isPublic = true`
- `publishAt <= now`
- `startsAt` is null OR `startsAt <= now`
- `endsAt` is null OR `endsAt >= now`

## Troubleshooting
- 404 on public page: slug missing or page not public.
- Missing grouped monitors: ensure `monitorBindings` include monitor IDs and valid `componentGroupId` references.
- Maintenance not visible: validate timezone and publish/start/end windows.
- Branding not applied: verify `PUT /status-pages/{id}` response and refresh frontend cache.

## Safety notes
- Admin endpoints stay ADMIN-protected; public endpoint remains read-only.
- Private pages still return 404 on public API (no visibility leak).
- Branding fields are length-validated in API DTOs.
