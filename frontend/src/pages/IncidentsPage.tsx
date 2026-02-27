import { useMemo, useState } from 'react'
import { useQueryClient } from '@tanstack/react-query'
import { notify } from '../components/feedback/toast'
import { EmptyState } from '../components/states/EmptyState'
import { ErrorState } from '../components/states/ErrorState'
import { LoadingState } from '../components/states/LoadingState'
import { Badge } from '../components/ui/Badge'
import { Button } from '../components/ui/Button'
import { Field, SelectInput, TextAreaInput, TextInput } from '../components/ui/FormControls'
import {
  getApiErrorMessage,
  type AdminIncident,
  type IncidentState,
  useAcknowledgeIncidentMutation,
  useAdminIncidentsQuery,
  useAnnotateIncidentMutation,
  useIncidentEventsQuery,
  useReopenIncidentMutation,
  useResolveIncidentMutation,
} from '../lib/api/incidents'

type SortMode = 'newest' | 'oldest' | 'monitor'

const annotationMinLength = 4

const toneByState: Record<IncidentState, 'critical' | 'warning' | 'success'> = {
  OPEN: 'critical',
  ACKNOWLEDGED: 'warning',
  RESOLVED: 'success',
}

const labelByState: Record<IncidentState, string> = {
  OPEN: 'Open',
  ACKNOWLEDGED: 'Acknowledged',
  RESOLVED: 'Resolved',
}

function formatDateTime(input: string | null) {
  if (!input) return '—'
  return new Date(input).toLocaleString()
}

function canAcknowledge(state: IncidentState) {
  return state === 'OPEN'
}

function canResolve(state: IncidentState) {
  return state === 'OPEN' || state === 'ACKNOWLEDGED'
}

function canReopen(state: IncidentState) {
  return state === 'RESOLVED'
}

function actionHint(action: 'ack' | 'resolve' | 'reopen', state: IncidentState) {
  if (action === 'ack') return state === 'OPEN' ? '' : 'Only OPEN incidents can be acknowledged.'
  if (action === 'resolve') return canResolve(state) ? '' : 'Only OPEN or ACKNOWLEDGED incidents can be resolved.'
  return state === 'RESOLVED' ? '' : 'Only RESOLVED incidents can be reopened.'
}

export function IncidentsPage() {
  const queryClient = useQueryClient()
  const incidentsQuery = useAdminIncidentsQuery()
  const [selectedIncidentId, setSelectedIncidentId] = useState<string | null>(null)
  const [search, setSearch] = useState('')
  const [stateFilter, setStateFilter] = useState<'ALL' | IncidentState>('ALL')
  const [sortMode, setSortMode] = useState<SortMode>('newest')
  const [actionReason, setActionReason] = useState('')
  const [annotationText, setAnnotationText] = useState('')

  const acknowledgeMutation = useAcknowledgeIncidentMutation()
  const resolveMutation = useResolveIncidentMutation()
  const reopenMutation = useReopenIncidentMutation()
  const annotateMutation = useAnnotateIncidentMutation()

  const filteredIncidents = useMemo(() => {
    const incidents = incidentsQuery.data ?? []
    const searchNeedle = search.trim().toLowerCase()
    return [...incidents]
      .filter((incident) => (stateFilter === 'ALL' ? true : incident.state === stateFilter))
      .filter((incident) => {
        if (!searchNeedle) return true
        return (
          incident.monitorName.toLowerCase().includes(searchNeedle) ||
          incident.reason.toLowerCase().includes(searchNeedle) ||
          incident.id.toLowerCase().includes(searchNeedle)
        )
      })
      .sort((left, right) => {
        if (sortMode === 'monitor') return left.monitorName.localeCompare(right.monitorName)
        const leftTime = new Date(left.openedAt).getTime()
        const rightTime = new Date(right.openedAt).getTime()
        return sortMode === 'oldest' ? leftTime - rightTime : rightTime - leftTime
      })
  }, [incidentsQuery.data, search, sortMode, stateFilter])

  const selectedIncident = useMemo(() => {
    if (filteredIncidents.length === 0) return null
    if (!selectedIncidentId) return filteredIncidents[0]
    return filteredIncidents.find((incident) => incident.id === selectedIncidentId) ?? filteredIncidents[0]
  }, [filteredIncidents, selectedIncidentId])

  const eventsQuery = useIncidentEventsQuery(selectedIncident?.id ?? null)

  async function refreshIncidentsSelection(updatedId: string) {
    await queryClient.invalidateQueries({ queryKey: ['admin-incidents'] })
    await queryClient.invalidateQueries({ queryKey: ['admin-incidents', updatedId, 'events'] })
    setSelectedIncidentId(updatedId)
  }

  async function applyAction(
    incident: AdminIncident,
    action: 'acknowledge' | 'resolve' | 'reopen',
    run: (payload: { incidentId: string; reason: string }) => Promise<unknown>,
  ) {
    const reason = actionReason.trim()
    if (!reason) {
      notify.error('Action reason is required.')
      return
    }

    try {
      await run({ incidentId: incident.id, reason })
      setActionReason('')
      notify.success(`Incident ${action} succeeded.`)
      await refreshIncidentsSelection(incident.id)
    } catch (error) {
      notify.error(getApiErrorMessage(error, `Failed to ${action} incident.`))
    }
  }

  async function submitAnnotation(incident: AdminIncident) {
    const reason = annotationText.trim()
    if (reason.length < annotationMinLength) {
      notify.error(`Annotation must be at least ${annotationMinLength} characters.`)
      return
    }

    try {
      await annotateMutation.mutateAsync({ incidentId: incident.id, reason })
      setAnnotationText('')
      notify.success('Annotation saved.')
      await refreshIncidentsSelection(incident.id)
    } catch (error) {
      notify.error(getApiErrorMessage(error, 'Failed to annotate incident.'))
    }
  }

  if (incidentsQuery.isLoading) {
    return <LoadingState title="Loading incidents" description="Pulling incident stream from admin API." />
  }

  if (incidentsQuery.isError) {
    return <ErrorState title="Could not load incidents" description="Verify API auth and availability, then retry." />
  }

  if ((incidentsQuery.data?.length ?? 0) === 0) {
    return <EmptyState title="No incidents yet" description="Incident records will appear here after monitor failures." />
  }

  return (
    <section className="space-y-4">
      <div className="flex items-center justify-between">
        <div>
          <h2 className="text-2xl font-semibold">Incidents Console</h2>
          <p className="text-sm text-text-secondary">Filter, inspect and manually control incident lifecycle.</p>
        </div>
        <Badge tone="warning">Admin actions</Badge>
      </div>

      <div className="grid gap-4 xl:grid-cols-[360px_1fr]">
        <aside className="space-y-3 rounded-lg border border-surface-border bg-bg-elevated p-4">
          <div className="grid gap-2">
            <TextInput placeholder="Search by monitor, reason, ID" value={search} onChange={(event) => setSearch(event.target.value)} />
            <div className="grid grid-cols-2 gap-2">
              <SelectInput value={stateFilter} onChange={(event) => setStateFilter(event.target.value as 'ALL' | IncidentState)}>
                <option value="ALL">All states</option>
                <option value="OPEN">Open</option>
                <option value="ACKNOWLEDGED">Acknowledged</option>
                <option value="RESOLVED">Resolved</option>
              </SelectInput>
              <SelectInput value={sortMode} onChange={(event) => setSortMode(event.target.value as SortMode)}>
                <option value="newest">Newest first</option>
                <option value="oldest">Oldest first</option>
                <option value="monitor">Monitor name</option>
              </SelectInput>
            </div>
          </div>

          <div className="max-h-[70vh] space-y-2 overflow-auto pr-1">
            {filteredIncidents.map((incident) => {
              const isSelected = selectedIncident?.id === incident.id
              return (
                <button
                  key={incident.id}
                  type="button"
                  onClick={() => setSelectedIncidentId(incident.id)}
                  className={`w-full rounded-md border p-3 text-left transition ${
                    isSelected ? 'border-accent bg-bg-panel' : 'border-surface-border bg-bg-base hover:border-accent/40'
                  }`}
                >
                  <div className="mb-2 flex items-center justify-between gap-2">
                    <p className="truncate text-sm font-medium">{incident.monitorName}</p>
                    <Badge tone={toneByState[incident.state]}>{labelByState[incident.state]}</Badge>
                  </div>
                  <p className="line-clamp-2 text-xs text-text-secondary">{incident.reason}</p>
                  <p className="mt-2 text-xs text-text-muted">Opened {formatDateTime(incident.openedAt)}</p>
                </button>
              )
            })}
          </div>
        </aside>

        <div className="space-y-4 rounded-lg border border-surface-border bg-bg-elevated p-4">
          {selectedIncident ? (
            <>
              <div className="flex flex-wrap items-center justify-between gap-3 border-b border-surface-border pb-4">
                <div>
                  <h3 className="text-lg font-semibold">{selectedIncident.monitorName}</h3>
                  <p className="text-xs text-text-muted">Incident ID: {selectedIncident.id}</p>
                </div>
                <Badge tone={toneByState[selectedIncident.state]}>{labelByState[selectedIncident.state]}</Badge>
              </div>

              <div className="grid gap-3 md:grid-cols-3">
                <div className="rounded-md bg-bg-panel p-3">
                  <p className="text-xs text-text-muted">Opened</p>
                  <p className="text-sm text-text-secondary">{formatDateTime(selectedIncident.openedAt)}</p>
                </div>
                <div className="rounded-md bg-bg-panel p-3">
                  <p className="text-xs text-text-muted">Resolved</p>
                  <p className="text-sm text-text-secondary">{formatDateTime(selectedIncident.resolvedAt)}</p>
                </div>
                <div className="rounded-md bg-bg-panel p-3">
                  <p className="text-xs text-text-muted">Trigger reason</p>
                  <p className="line-clamp-2 text-sm text-text-secondary">{selectedIncident.reason}</p>
                </div>
              </div>

              <div className="space-y-2 rounded-md border border-surface-border p-3">
                <Field label="Lifecycle action reason">
                  <TextAreaInput
                    rows={2}
                    value={actionReason}
                    onChange={(event) => setActionReason(event.target.value)}
                    placeholder="Required for acknowledge / resolve / reopen"
                    maxLength={2048}
                  />
                </Field>
                <div className="flex flex-wrap gap-2">
                  <Button
                    variant="secondary"
                    disabled={!canAcknowledge(selectedIncident.state) || acknowledgeMutation.isPending}
                    title={actionHint('ack', selectedIncident.state)}
                    onClick={() => applyAction(selectedIncident, 'acknowledge', acknowledgeMutation.mutateAsync)}
                  >
                    Acknowledge
                  </Button>
                  <Button
                    variant="secondary"
                    disabled={!canResolve(selectedIncident.state) || resolveMutation.isPending}
                    title={actionHint('resolve', selectedIncident.state)}
                    onClick={() => applyAction(selectedIncident, 'resolve', resolveMutation.mutateAsync)}
                  >
                    Resolve
                  </Button>
                  <Button
                    variant="secondary"
                    disabled={!canReopen(selectedIncident.state) || reopenMutation.isPending}
                    title={actionHint('reopen', selectedIncident.state)}
                    onClick={() => applyAction(selectedIncident, 'reopen', reopenMutation.mutateAsync)}
                  >
                    Reopen
                  </Button>
                </div>
                <p className="text-xs text-text-muted">
                  {!canAcknowledge(selectedIncident.state) && !canResolve(selectedIncident.state) && !canReopen(selectedIncident.state)
                    ? 'No manual transitions are available for this state.'
                    : 'Disabled actions include state hints on hover.'}
                </p>
              </div>

              <div className="space-y-2 rounded-md border border-surface-border p-3">
                <Field label="Annotation">
                  <TextAreaInput
                    rows={3}
                    value={annotationText}
                    onChange={(event) => setAnnotationText(event.target.value)}
                    placeholder="Add timeline context for responders"
                    maxLength={2048}
                  />
                </Field>
                <div className="flex items-center justify-between text-xs text-text-muted">
                  <span>Minimum {annotationMinLength} characters</span>
                  <span>{annotationText.trim().length}/2048</span>
                </div>
                <Button
                  disabled={annotationText.trim().length < annotationMinLength || annotateMutation.isPending}
                  onClick={() => submitAnnotation(selectedIncident)}
                >
                  Add annotation
                </Button>
              </div>

              <div className="space-y-2 rounded-md border border-surface-border p-3">
                <h4 className="font-medium">Manual action history</h4>
                {eventsQuery.isLoading ? (
                  <p className="text-sm text-text-secondary">Loading timeline…</p>
                ) : eventsQuery.data && eventsQuery.data.length > 0 ? (
                  <ol className="space-y-3">
                    {eventsQuery.data.map((event) => (
                      <li key={event.id} className="rounded-md bg-bg-panel p-3">
                        <div className="flex flex-wrap items-center gap-2 text-xs">
                          <Badge tone="neutral">{event.action}</Badge>
                          <span className="text-text-muted">{formatDateTime(event.occurredAt)}</span>
                          <span className="text-text-muted">by {event.actor}</span>
                        </div>
                        <p className="mt-1 text-sm text-text-secondary">{event.reason}</p>
                        <p className="mt-1 text-xs text-text-muted">
                          {event.fromState} → {event.toState}
                        </p>
                      </li>
                    ))}
                  </ol>
                ) : (
                  <p className="text-sm text-text-secondary">
                    No manual event records returned for this incident yet. This block is best-effort based on current API availability.
                  </p>
                )}
              </div>
            </>
          ) : (
            <EmptyState title="No incident selected" description="Adjust filters or pick an incident from the list." />
          )}
        </div>
      </div>
    </section>
  )
}
