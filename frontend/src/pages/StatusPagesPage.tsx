import { useMemo, useState } from 'react'
import { useQueryClient } from '@tanstack/react-query'
import { notify } from '../components/feedback/toast'
import { EmptyState } from '../components/states/EmptyState'
import { ErrorState } from '../components/states/ErrorState'
import { LoadingState } from '../components/states/LoadingState'
import { Badge } from '../components/ui/Badge'
import { Button } from '../components/ui/Button'
import { Field, TextInput } from '../components/ui/FormControls'
import { useMonitorsQuery } from '../lib/api/monitors'
import {
  getStatusPageApiErrorMessage,
  type PublicMonitorSummary,
  useAttachStatusPageMonitorsMutation,
  useCreateStatusPageMutation,
  usePublicStatusPageQuery,
  useRemoveStatusPageMonitorMutation,
  useStatusPagesQuery,
} from '../lib/api/statusPages'

function formatDateTime(input: string | null) {
  if (!input) return '—'
  return new Date(input).toLocaleString()
}

function statusTone(status: 'OPERATIONAL' | 'DEGRADED' | 'OUTAGE' | 'UP' | 'DOWN' | 'UNKNOWN'): 'success' | 'warning' | 'critical' {
  if (status === 'OPERATIONAL' || status === 'UP') return 'success'
  if (status === 'DEGRADED' || status === 'UNKNOWN') return 'warning'
  return 'critical'
}

const slugRegex = /^[a-z0-9]+(?:-[a-z0-9]+)*$/

export function StatusPagesPage() {
  const queryClient = useQueryClient()
  const statusPagesQuery = useStatusPagesQuery()
  const monitorsQuery = useMonitorsQuery()

  const createPageMutation = useCreateStatusPageMutation()
  const attachMonitorsMutation = useAttachStatusPageMonitorsMutation()
  const removeMonitorMutation = useRemoveStatusPageMonitorMutation()

  const [selectedPageId, setSelectedPageId] = useState<string | null>(null)
  const [name, setName] = useState('')
  const [slug, setSlug] = useState('')
  const [isPublic, setIsPublic] = useState(true)

  const [selectedMonitorId, setSelectedMonitorId] = useState('')
  const [workingMonitorsByPage, setWorkingMonitorsByPage] = useState<Record<string, PublicMonitorSummary[]>>({})
  const [previewSlugInput, setPreviewSlugInput] = useState('')

  const selectedPage = useMemo(() => {
    const pages = statusPagesQuery.data ?? []
    if (pages.length === 0) return null
    if (!selectedPageId) return pages[0]
    return pages.find((page) => page.id === selectedPageId) ?? pages[0]
  }, [selectedPageId, statusPagesQuery.data])

  const effectivePreviewSlug = previewSlugInput.trim() || selectedPage?.slug || null
  const publicPreviewQuery = usePublicStatusPageQuery(effectivePreviewSlug)

  const persistedWorkingMonitors = selectedPage ? workingMonitorsByPage[selectedPage.id] : undefined
  const workingMonitors = useMemo(
    () => persistedWorkingMonitors ?? publicPreviewQuery.data?.monitors ?? [],
    [persistedWorkingMonitors, publicPreviewQuery.data?.monitors],
  )

  function setWorkingMonitors(next: PublicMonitorSummary[]) {
    if (!selectedPage) return
    setWorkingMonitorsByPage((prev) => ({ ...prev, [selectedPage.id]: next }))
  }

  const attachedMonitorIds = useMemo(() => new Set(workingMonitors.map((monitor) => monitor.monitorId)), [workingMonitors])

  const availableMonitors = useMemo(() => {
    const monitors = monitorsQuery.data ?? []
    return monitors.filter((monitor) => !attachedMonitorIds.has(monitor.id))
  }, [attachedMonitorIds, monitorsQuery.data])

  async function refreshStatusPages() {
    await queryClient.invalidateQueries({ queryKey: ['status-pages'] })
  }

  async function refreshPreview(slugToRefresh: string | null) {
    await queryClient.invalidateQueries({ queryKey: ['status-pages', 'public', slugToRefresh] })
  }

  async function createPage() {
    const payload = {
      name: name.trim(),
      slug: slug.trim(),
      isPublic,
    }

    if (!payload.name) {
      notify.error('Page name is required.')
      return
    }

    if (!payload.slug || !slugRegex.test(payload.slug)) {
      notify.error('Slug must use lowercase letters, numbers and hyphens only.')
      return
    }

    try {
      const created = await createPageMutation.mutateAsync(payload)
      setName('')
      setSlug('')
      setIsPublic(true)
      await refreshStatusPages()
      setSelectedPageId(created.id)
      setPreviewSlugInput(created.slug)
      notify.success('Status page created.')
    } catch (error) {
      notify.error(getStatusPageApiErrorMessage(error, 'Failed to create status page.'))
    }
  }

  async function saveMonitorOrder() {
    if (!selectedPage) return

    try {
      await attachMonitorsMutation.mutateAsync({
        pageId: selectedPage.id,
        monitorIds: workingMonitors.map((monitor) => monitor.monitorId),
      })
      await refreshPreview(selectedPage.slug)
      notify.success('Monitor bindings saved.')
    } catch (error) {
      notify.error(getStatusPageApiErrorMessage(error, 'Failed to save monitor bindings.'))
    }
  }

  async function attachMonitor() {
    if (!selectedPage || !selectedMonitorId) return
    const monitor = (monitorsQuery.data ?? []).find((item) => item.id === selectedMonitorId)
    if (!monitor) return

    setWorkingMonitors([
      ...workingMonitors,
      {
        monitorId: monitor.id,
        monitorName: monitor.name,
        displayOrder: workingMonitors.length,
        currentStatus: 'UNKNOWN',
        statusCode: null,
        latencyMs: null,
        checkedAt: null,
      },
    ])
    setSelectedMonitorId('')
  }

  function moveMonitor(index: number, direction: -1 | 1) {
    const target = index + direction
    if (target < 0 || target >= workingMonitors.length) return
    const next = [...workingMonitors]
    const [item] = next.splice(index, 1)
    next.splice(target, 0, item)
    setWorkingMonitors(next)
  }

  async function removeMonitor(monitorId: string) {
    if (!selectedPage) return

    try {
      await removeMonitorMutation.mutateAsync({ pageId: selectedPage.id, monitorId })
      setWorkingMonitors(workingMonitors.filter((monitor) => monitor.monitorId !== monitorId))
      await refreshPreview(selectedPage.slug)
      notify.success('Monitor removed from status page.')
    } catch (error) {
      notify.error(getStatusPageApiErrorMessage(error, 'Failed to remove monitor.'))
    }
  }

  if (statusPagesQuery.isLoading || monitorsQuery.isLoading) {
    return <LoadingState title="Loading status page workspace" description="Fetching pages and monitor catalog from API." />
  }

  if (statusPagesQuery.isError || monitorsQuery.isError) {
    return <ErrorState title="Could not load status pages" description="Check API availability and credentials, then retry." />
  }

  return (
    <section className="space-y-4">
      <header className="flex items-center justify-between">
        <div>
          <h2 className="text-2xl font-semibold">Status Pages</h2>
          <p className="text-sm text-text-secondary">Admin controls + live public preview by slug.</p>
        </div>
        <Badge tone="warning">Admin + Public view</Badge>
      </header>

      <div className="grid gap-4 xl:grid-cols-[1.2fr_1fr]">
        <div className="space-y-4 rounded-lg border border-surface-border bg-bg-elevated p-4">
          <div className="rounded-md border border-surface-border p-3">
            <p className="mb-3 text-sm font-medium text-text-primary">Admin controls</p>
            <div className="grid gap-3 md:grid-cols-[1fr_220px_auto_auto] md:items-end">
              <Field label="Page name">
                <TextInput value={name} maxLength={120} onChange={(event) => setName(event.target.value)} placeholder="Production Status" />
              </Field>
              <Field label="Slug">
                <TextInput value={slug} maxLength={80} onChange={(event) => setSlug(event.target.value)} placeholder="production-status" />
              </Field>
              <label className="flex items-center gap-2 text-sm text-text-secondary">
                <input type="checkbox" checked={isPublic} onChange={(event) => setIsPublic(event.target.checked)} />
                Public
              </label>
              <Button disabled={createPageMutation.isPending} onClick={createPage}>
                Create page
              </Button>
            </div>
          </div>

          {(statusPagesQuery.data?.length ?? 0) > 0 ? (
            <>
              <div className="grid gap-2">
                <p className="text-sm font-medium">Existing pages</p>
                <div className="grid gap-2 max-h-44 overflow-auto pr-1">
                  {(statusPagesQuery.data ?? []).map((page) => {
                    const selected = selectedPage?.id === page.id
                    return (
                      <button
                        key={page.id}
                        type="button"
                        onClick={() => {
                          setSelectedPageId(page.id)
                          setPreviewSlugInput(page.slug)
                          setSelectedMonitorId('')
                        }}
                        className={`rounded-md border p-3 text-left transition ${
                          selected ? 'border-accent bg-bg-panel' : 'border-surface-border bg-bg-base hover:border-accent/40'
                        }`}
                      >
                        <div className="flex items-center justify-between gap-3">
                          <p className="font-medium">{page.name}</p>
                          <Badge tone={page.isPublic ? 'success' : 'neutral'}>{page.isPublic ? 'Public' : 'Private'}</Badge>
                        </div>
                        <p className="text-xs text-text-muted">/{page.slug}</p>
                      </button>
                    )
                  })}
                </div>
              </div>

              {selectedPage ? (
                <div className="space-y-3 rounded-md border border-surface-border p-3">
                  <div className="flex flex-wrap items-center justify-between gap-2">
                    <div>
                      <p className="font-medium">Monitor binding for {selectedPage.name}</p>
                      <p className="text-xs text-text-muted">Attach, order, and remove monitors shown publicly.</p>
                    </div>
                    <Button variant="secondary" disabled={attachMonitorsMutation.isPending} onClick={saveMonitorOrder}>
                      Save order
                    </Button>
                  </div>

                  <div className="grid gap-2 md:grid-cols-[1fr_auto] md:items-end">
                    <Field label="Add monitor">
                      <select
                        className="w-full rounded-md border border-surface-border bg-bg-panel px-3 py-2 text-sm"
                        value={selectedMonitorId}
                        onChange={(event) => setSelectedMonitorId(event.target.value)}
                      >
                        <option value="">Select monitor to attach…</option>
                        {availableMonitors.map((monitor) => (
                          <option key={monitor.id} value={monitor.id}>
                            {monitor.name}
                          </option>
                        ))}
                      </select>
                    </Field>
                    <Button variant="secondary" disabled={!selectedMonitorId} onClick={attachMonitor}>
                      Add
                    </Button>
                  </div>

                  {workingMonitors.length > 0 ? (
                    <ol className="space-y-2">
                      {workingMonitors.map((monitor, index) => (
                        <li key={monitor.monitorId} className="flex items-center justify-between rounded-md bg-bg-panel p-3">
                          <div>
                            <p className="text-sm font-medium">{index + 1}. {monitor.monitorName}</p>
                            <p className="text-xs text-text-muted">Current status: {monitor.currentStatus}</p>
                          </div>
                          <div className="flex gap-2">
                            <Button variant="ghost" disabled={index === 0} onClick={() => moveMonitor(index, -1)}>
                              ↑
                            </Button>
                            <Button variant="ghost" disabled={index === workingMonitors.length - 1} onClick={() => moveMonitor(index, 1)}>
                              ↓
                            </Button>
                            <Button variant="ghost" onClick={() => removeMonitor(monitor.monitorId)}>
                              Remove
                            </Button>
                          </div>
                        </li>
                      ))}
                    </ol>
                  ) : (
                    <EmptyState title="No monitors attached" description="Attach monitors, then save order to publish this section." />
                  )}
                </div>
              ) : null}
            </>
          ) : (
            <EmptyState title="No status pages yet" description="Create the first status page to start building public health views." />
          )}
        </div>

        <aside className="space-y-3 rounded-lg border border-surface-border bg-bg-elevated p-4">
          <div className="rounded-md border border-surface-border p-3">
            <p className="mb-2 text-sm font-medium">Public view preview</p>
            <p className="mb-3 text-xs text-text-muted">Preview uses public endpoint by slug and is read-only.</p>
            <Field label="Preview slug">
              <TextInput value={previewSlugInput} onChange={(event) => setPreviewSlugInput(event.target.value)} placeholder="production-status" />
            </Field>
          </div>

          {!previewSlugInput.trim() ? (
            <EmptyState title="Enter a slug" description="Provide a slug to fetch public status page preview." />
          ) : publicPreviewQuery.isLoading ? (
            <LoadingState title="Loading preview" description="Fetching public status page payload." />
          ) : publicPreviewQuery.isError ? (
            <ErrorState
              title="Public preview unavailable"
              description="Slug missing, non-public, or not found. Verify slug and page visibility."
            />
          ) : publicPreviewQuery.data ? (
            <div className="space-y-3 rounded-md border border-surface-border p-3">
              <div className="flex items-center justify-between gap-2">
                <h3 className="font-semibold">{publicPreviewQuery.data.page.name}</h3>
                <Badge tone={statusTone(publicPreviewQuery.data.overallStatus)}>{publicPreviewQuery.data.overallStatus}</Badge>
              </div>
              <p className="text-xs text-text-muted">/{publicPreviewQuery.data.page.slug}</p>

              <div className="space-y-2">
                <p className="text-sm font-medium">Services</p>
                {publicPreviewQuery.data.monitors.length > 0 ? (
                  <ul className="space-y-2">
                    {publicPreviewQuery.data.monitors.map((monitor) => (
                      <li key={monitor.monitorId} className="rounded-md bg-bg-panel p-3">
                        <div className="mb-1 flex items-center justify-between gap-2">
                          <p className="text-sm font-medium">{monitor.monitorName}</p>
                          <Badge tone={statusTone(monitor.currentStatus)}>{monitor.currentStatus}</Badge>
                        </div>
                        <p className="text-xs text-text-muted">
                          Status code: {monitor.statusCode ?? '—'} · Latency: {monitor.latencyMs ?? '—'}ms · Last check: {formatDateTime(monitor.checkedAt)}
                        </p>
                      </li>
                    ))}
                  </ul>
                ) : (
                  <p className="text-sm text-text-secondary">No monitors are currently published on this page.</p>
                )}
              </div>

              <div className="space-y-2">
                <p className="text-sm font-medium">Incident timeline</p>
                {publicPreviewQuery.data.incidents.length > 0 ? (
                  <ul className="space-y-2">
                    {publicPreviewQuery.data.incidents.map((incident) => (
                      <li key={incident.incidentId} className="rounded-md bg-bg-panel p-3 text-sm">
                        <p className="font-medium">{incident.monitorName}</p>
                        <p className="text-xs text-text-muted">{incident.state} · opened {formatDateTime(incident.openedAt)}</p>
                        <p className="mt-1 text-xs text-text-secondary">{incident.reason}</p>
                      </li>
                    ))}
                  </ul>
                ) : (
                  <p className="text-sm text-text-secondary">No incidents currently visible for this page.</p>
                )}
              </div>
            </div>
          ) : null}
        </aside>
      </div>
    </section>
  )
}
