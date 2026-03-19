import { useMemo, useState } from 'react'
import { EmptyState } from '../components/states/EmptyState'
import { ErrorState } from '../components/states/ErrorState'
import { LoadingState } from '../components/states/LoadingState'
import { Badge } from '../components/ui/Badge'
import { Button } from '../components/ui/Button'
import { type CheckStatus, type Monitor } from '../types/monitor'
import { type AdminIncident, type IncidentState, useAdminIncidentsQuery } from '../lib/api/incidents'
import { useMonitorsQuery } from '../lib/api/monitors'

function monitorTone(status: CheckStatus | null, enabled: boolean): 'success' | 'critical' | 'warning' | 'neutral' {
  if (!enabled) return 'neutral'
  if (status === 'UP') return 'success'
  if (status === 'DOWN') return 'critical'
  if (status === 'UNKNOWN') return 'warning'
  return 'neutral'
}

function monitorLabel(status: CheckStatus | null, enabled: boolean) {
  if (!enabled) return 'DISABLED'
  return status ?? 'NO DATA'
}

function incidentTone(state: IncidentState): 'critical' | 'warning' | 'success' {
  if (state === 'OPEN') return 'critical'
  if (state === 'ACKNOWLEDGED') return 'warning'
  return 'success'
}

function formatDateTime(value: string | null) {
  if (!value) return '—'
  return new Date(value).toLocaleString()
}

function formatTypeMetadata(monitor: Monitor) {
  if (monitor.type !== 'HTTP') {
    return monitor.type
  }

  const method = monitor.httpMethod ?? 'GET'
  const keyword = monitor.expectedResponseKeyword ? ` · keyword: ${monitor.expectedResponseKeyword}` : ''
  return `${monitor.type} · ${method}${keyword}`
}

export function DashboardPage() {
  const monitorsQuery = useMonitorsQuery()
  const incidentsQuery = useAdminIncidentsQuery()
  const [selectedIncidentId, setSelectedIncidentId] = useState<string | null>(null)

  const monitors = useMemo(() => monitorsQuery.data ?? [], [monitorsQuery.data])
  const incidents = useMemo(() => incidentsQuery.data ?? [], [incidentsQuery.data])

  const sortedMonitors = useMemo(
    () =>
      [...monitors].sort((left, right) => {
        const score = (monitor: Monitor) => {
          if (!monitor.enabled) return 3
          if (monitor.lastCheckStatus === 'DOWN') return 0
          if (monitor.lastCheckStatus === 'UNKNOWN') return 1
          return 2
        }

        const scoreDiff = score(left) - score(right)
        if (scoreDiff !== 0) return scoreDiff
        return left.name.localeCompare(right.name)
      }),
    [monitors],
  )

  const sortedIncidents = useMemo(
    () =>
      [...incidents].sort((left, right) => {
        const leftTime = new Date(left.openedAt).getTime()
        const rightTime = new Date(right.openedAt).getTime()
        return rightTime - leftTime
      }),
    [incidents],
  )

  const selectedIncident = useMemo(() => {
    if (sortedIncidents.length === 0) return null
    if (!selectedIncidentId) return sortedIncidents[0]
    return sortedIncidents.find((incident) => incident.id === selectedIncidentId) ?? sortedIncidents[0]
  }, [selectedIncidentId, sortedIncidents])

  const downCount = useMemo(() => monitors.filter((monitor) => monitor.enabled && monitor.lastCheckStatus === 'DOWN').length, [monitors])
  const upCount = useMemo(() => monitors.filter((monitor) => monitor.enabled && monitor.lastCheckStatus === 'UP').length, [monitors])

  if (monitorsQuery.isLoading || incidentsQuery.isLoading) {
    return <LoadingState title="Loading live dashboard" description="Fetching monitor states and incidents." />
  }

  if (monitorsQuery.isError || incidentsQuery.isError) {
    return (
      <ErrorState
        title="Dashboard data unavailable"
        description="Could not load monitors or incidents. Verify API auth and retry."
        action={
          <Button variant="secondary" onClick={() => { void monitorsQuery.refetch(); void incidentsQuery.refetch() }}>
            Retry
          </Button>
        }
      />
    )
  }

  return (
    <section className="space-y-6">
      <div className="flex flex-wrap items-start justify-between gap-3">
        <div>
          <h2 className="text-2xl font-semibold">Operations Dashboard</h2>
          <p className="text-sm text-text-secondary">Top: live monitor grid. Bottom: incident timeline for triage.</p>
        </div>
        <div className="flex flex-wrap items-center gap-2">
          <Badge tone={downCount > 0 ? 'critical' : 'success'}>{downCount} DOWN</Badge>
          <Badge tone="success">{upCount} UP</Badge>
          <Badge tone="neutral">{sortedIncidents.length} incidents</Badge>
        </div>
      </div>

      <section className="space-y-3" aria-label="Live monitor status grid">
        <h3 className="text-lg font-semibold">Live monitor grid</h3>
        {sortedMonitors.length === 0 ? (
          <EmptyState title="No monitors yet" description="Create monitors to start live status tracking." />
        ) : (
          <div className="grid gap-3 sm:grid-cols-2 xl:grid-cols-3">
            {sortedMonitors.map((monitor) => (
              <article key={monitor.id} className="rounded-lg border border-surface-border bg-bg-elevated p-4">
                <div className="mb-2 flex items-center justify-between gap-2">
                  <p className="truncate font-medium">{monitor.name}</p>
                  <Badge tone={monitorTone(monitor.lastCheckStatus, monitor.enabled)}>{monitorLabel(monitor.lastCheckStatus, monitor.enabled)}</Badge>
                </div>
                <p className="line-clamp-1 text-xs text-text-secondary">{monitor.targetUrl}</p>
                <p className="mt-2 text-xs text-text-muted">{formatTypeMetadata(monitor)}</p>
                <div className="mt-2 grid grid-cols-2 gap-2 text-xs text-text-muted">
                  <p>Interval: {monitor.intervalSec}s</p>
                  <p>Timeout: {monitor.timeoutMs}ms</p>
                  <p>Status code: {monitor.lastStatusCode ?? '—'}</p>
                  <p>Latency: {monitor.lastLatencyMs ?? '—'}ms</p>
                </div>
                <p className="mt-2 text-xs text-text-muted">Last check: {formatDateTime(monitor.lastCheckAt)}</p>
              </article>
            ))}
          </div>
        )}
      </section>

      <section className="space-y-3" aria-label="Incident timeline">
        <h3 className="text-lg font-semibold">Incident timeline</h3>
        {sortedIncidents.length === 0 ? (
          <EmptyState title="No incidents" description="When monitors fail, incidents appear here for triage." />
        ) : (
          <div className="grid gap-4 lg:grid-cols-[360px_1fr]">
            <ol className="max-h-[60vh] space-y-2 overflow-auto rounded-lg border border-surface-border bg-bg-elevated p-3">
              {sortedIncidents.map((incident: AdminIncident) => {
                const selected = selectedIncident?.id === incident.id
                return (
                  <li key={incident.id}>
                    <button
                      type="button"
                      onClick={() => setSelectedIncidentId(incident.id)}
                      className={`w-full rounded-md border p-3 text-left transition ${
                        selected ? 'border-accent bg-bg-panel' : 'border-surface-border bg-bg-base hover:border-accent/40'
                      }`}
                    >
                      <div className="mb-1 flex items-center justify-between gap-2">
                        <p className="truncate text-sm font-medium">{incident.monitorName}</p>
                        <Badge tone={incidentTone(incident.state)}>{incident.state}</Badge>
                      </div>
                      <p className="line-clamp-2 text-xs text-text-secondary">{incident.reason}</p>
                      <p className="mt-1 text-xs text-text-muted">Opened: {formatDateTime(incident.openedAt)}</p>
                    </button>
                  </li>
                )
              })}
            </ol>

            {selectedIncident ? (
              <article className="rounded-lg border border-surface-border bg-bg-elevated p-4">
                <div className="mb-3 flex flex-wrap items-center justify-between gap-2 border-b border-surface-border pb-3">
                  <div>
                    <h4 className="font-semibold">{selectedIncident.monitorName}</h4>
                    <p className="text-xs text-text-muted">Incident ID: {selectedIncident.id}</p>
                  </div>
                  <Badge tone={incidentTone(selectedIncident.state)}>{selectedIncident.state}</Badge>
                </div>
                <dl className="space-y-2 text-sm">
                  <div>
                    <dt className="text-text-muted">Opened</dt>
                    <dd className="text-text-secondary">{formatDateTime(selectedIncident.openedAt)}</dd>
                  </div>
                  <div>
                    <dt className="text-text-muted">Resolved</dt>
                    <dd className="text-text-secondary">{formatDateTime(selectedIncident.resolvedAt)}</dd>
                  </div>
                  <div>
                    <dt className="text-text-muted">Reason</dt>
                    <dd className="text-text-secondary">{selectedIncident.reason}</dd>
                  </div>
                </dl>
              </article>
            ) : null}
          </div>
        )}
      </section>
    </section>
  )
}
