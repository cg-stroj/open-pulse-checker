import { useMemo, useState } from 'react'
import { useQueryClient } from '@tanstack/react-query'
import { notify } from '../components/feedback/toast'
import { EmptyState } from '../components/states/EmptyState'
import { ErrorState } from '../components/states/ErrorState'
import { LoadingState } from '../components/states/LoadingState'
import { Badge } from '../components/ui/Badge'
import { Button } from '../components/ui/Button'
import { Field, SelectInput, TextInput } from '../components/ui/FormControls'
import {
  getMonitorApiErrorMessage,
  useCreateMonitorMutation,
  useMonitorDetailQuery,
  useMonitorsQuery,
  useRunMonitorCheckMutation,
  useToggleMonitorMutation,
  useUpdateMonitorMutation,
} from '../lib/api/monitors'
import type { CheckStatus, CreateMonitorPayload, Monitor, UpdateMonitorPayload } from '../types/monitor'

type SortMode = 'name' | 'newest' | 'oldest'

interface FormState {
  name: string
  type: 'HTTP'
  targetUrl: string
  intervalSec: string
  timeoutMs: string
  enabled: boolean
}

interface FormErrors {
  name?: string
  targetUrl?: string
  intervalSec?: string
  timeoutMs?: string
}

function initFormState(): FormState {
  return {
    name: '',
    type: 'HTTP',
    targetUrl: '',
    intervalSec: '60',
    timeoutMs: '1200',
    enabled: true,
  }
}

function fromMonitor(monitor: Monitor): FormState {
  return {
    name: monitor.name,
    type: monitor.type,
    targetUrl: monitor.targetUrl,
    intervalSec: String(monitor.intervalSec),
    timeoutMs: String(monitor.timeoutMs),
    enabled: monitor.enabled,
  }
}

function parseInteger(value: string) {
  if (!/^\d+$/.test(value.trim())) return null
  return Number(value)
}

function statusTone(status: CheckStatus | null): 'success' | 'critical' | 'warning' | 'neutral' {
  if (status === 'UP') return 'success'
  if (status === 'DOWN') return 'critical'
  if (status === 'UNKNOWN') return 'warning'
  return 'neutral'
}

function statusLabel(status: CheckStatus | null) {
  return status ?? 'NO DATA'
}

function formatDateTime(value: string | null) {
  if (!value) return '—'
  return new Date(value).toLocaleString()
}

function validate(form: FormState): FormErrors {
  const errors: FormErrors = {}

  if (!form.name.trim()) {
    errors.name = 'Name is required.'
  }

  if (!form.targetUrl.trim()) {
    errors.targetUrl = 'Target URL is required.'
  } else {
    try {
      const parsed = new URL(form.targetUrl.trim())
      if (parsed.protocol !== 'http:' && parsed.protocol !== 'https:') {
        errors.targetUrl = 'URL must use http or https.'
      }
    } catch {
      errors.targetUrl = 'Provide a valid URL.'
    }
  }

  const interval = parseInteger(form.intervalSec)
  if (interval === null || interval < 10 || interval > 86400) {
    errors.intervalSec = 'Interval must be an integer between 10 and 86400 seconds.'
  }

  const timeout = parseInteger(form.timeoutMs)
  if (timeout === null || timeout < 100 || timeout > 120000) {
    errors.timeoutMs = 'Timeout must be an integer between 100 and 120000 ms.'
  }

  return errors
}

function hasErrors(errors: FormErrors) {
  return Object.values(errors).some(Boolean)
}

export function MonitorsPage() {
  const queryClient = useQueryClient()
  const monitorsQuery = useMonitorsQuery()

  const createMonitorMutation = useCreateMonitorMutation()
  const updateMonitorMutation = useUpdateMonitorMutation()
  const toggleMonitorMutation = useToggleMonitorMutation()
  const runCheckMutation = useRunMonitorCheckMutation()

  const [selectedMonitorId, setSelectedMonitorId] = useState<string | null>(null)
  const [mode, setMode] = useState<'create' | 'edit'>('create')
  const [form, setForm] = useState<FormState>(initFormState)
  const [errors, setErrors] = useState<FormErrors>({})

  const [search, setSearch] = useState('')
  const [statusFilter, setStatusFilter] = useState<'ALL' | CheckStatus>('ALL')
  const [enabledFilter, setEnabledFilter] = useState<'ALL' | 'ENABLED' | 'DISABLED'>('ALL')
  const [sortMode, setSortMode] = useState<SortMode>('name')

  const monitorDetailQuery = useMonitorDetailQuery(mode === 'edit' ? selectedMonitorId : null)

  const monitors = useMemo(() => monitorsQuery.data ?? [], [monitorsQuery.data])

  const filteredMonitors = useMemo(() => {
    const needle = search.trim().toLowerCase()

    return [...monitors]
      .filter((monitor) => {
        if (!needle) return true
        return (
          monitor.name.toLowerCase().includes(needle) ||
          monitor.targetUrl.toLowerCase().includes(needle) ||
          monitor.id.toLowerCase().includes(needle)
        )
      })
      .filter((monitor) => (statusFilter === 'ALL' ? true : monitor.lastCheckStatus === statusFilter))
      .filter((monitor) => {
        if (enabledFilter === 'ALL') return true
        return enabledFilter === 'ENABLED' ? monitor.enabled : !monitor.enabled
      })
      .sort((left, right) => {
        if (sortMode === 'name') return left.name.localeCompare(right.name)
        const leftTime = new Date(left.createdAt).getTime()
        const rightTime = new Date(right.createdAt).getTime()
        return sortMode === 'newest' ? rightTime - leftTime : leftTime - rightTime
      })
  }, [enabledFilter, monitors, search, sortMode, statusFilter])

  const selectedMonitor = useMemo(() => {
    if (filteredMonitors.length === 0) return null
    if (!selectedMonitorId) return filteredMonitors[0]
    return filteredMonitors.find((monitor) => monitor.id === selectedMonitorId) ?? filteredMonitors[0]
  }, [filteredMonitors, selectedMonitorId])

  async function refresh() {
    await queryClient.invalidateQueries({ queryKey: ['monitors'] })
    if (selectedMonitorId) {
      await queryClient.invalidateQueries({ queryKey: ['monitors', selectedMonitorId] })
    }
  }

  function onChange<K extends keyof FormState>(key: K, value: FormState[K]) {
    setForm((prev) => ({ ...prev, [key]: value }))
  }

  function selectForCreate() {
    setMode('create')
    setSelectedMonitorId(null)
    setForm(initFormState())
    setErrors({})
  }

  function selectForEdit(monitor: Monitor) {
    setMode('edit')
    setSelectedMonitorId(monitor.id)
    setForm(fromMonitor(monitor))
    setErrors({})
  }

  function toCreatePayload(current: FormState): CreateMonitorPayload {
    return {
      name: current.name.trim(),
      type: current.type,
      targetUrl: current.targetUrl.trim(),
      intervalSec: Number(current.intervalSec),
      enabled: current.enabled,
      timeoutMs: Number(current.timeoutMs),
    }
  }

  function toUpdatePayload(current: FormState): UpdateMonitorPayload {
    return {
      name: current.name.trim(),
      type: current.type,
      targetUrl: current.targetUrl.trim(),
      intervalSec: Number(current.intervalSec),
      enabled: current.enabled,
      timeoutMs: Number(current.timeoutMs),
    }
  }

  async function submit() {
    const foundErrors = validate(form)
    setErrors(foundErrors)

    if (hasErrors(foundErrors)) {
      notify.error('Fix monitor form validation errors before saving.')
      return
    }

    try {
      if (mode === 'create') {
        if (!window.confirm('Create this monitor?')) return
        const created = await createMonitorMutation.mutateAsync(toCreatePayload(form))
        notify.success('Monitor created.')
        await refresh()
        setSelectedMonitorId(created.id)
        setMode('edit')
      } else if (selectedMonitor) {
        if (!window.confirm(`Save changes to monitor "${selectedMonitor.name}"?`)) return
        const updated = await updateMonitorMutation.mutateAsync({ id: selectedMonitor.id, data: toUpdatePayload(form) })
        notify.success('Monitor updated.')
        await refresh()
        setForm(fromMonitor(updated))
      }
    } catch (error) {
      notify.error(getMonitorApiErrorMessage(error, 'Failed to save monitor.'))
    }
  }

  async function toggleMonitor(monitor: Monitor) {
    const nextEnabled = !monitor.enabled
    const confirmed = window.confirm(
      `${nextEnabled ? 'Enable' : 'Disable'} monitor "${monitor.name}"?`,
    )
    if (!confirmed) return

    try {
      await toggleMonitorMutation.mutateAsync({ id: monitor.id, enabled: nextEnabled })
      notify.success(`Monitor ${nextEnabled ? 'enabled' : 'disabled'}.`)
      await refresh()
    } catch (error) {
      notify.error(getMonitorApiErrorMessage(error, 'Failed to toggle monitor status.'))
    }
  }

  async function runCheck(monitor: Monitor) {
    const confirmed = window.confirm(`Run an immediate check for "${monitor.name}"?`)
    if (!confirmed) return

    try {
      const result = await runCheckMutation.mutateAsync(monitor.id)
      notify.success(`Check completed: ${result.status}${result.statusCode ? ` (${result.statusCode})` : ''}`)
      await refresh()
    } catch (error) {
      notify.error(getMonitorApiErrorMessage(error, 'Failed to run monitor check.'))
    }
  }

  if (monitorsQuery.isLoading) {
    return <LoadingState title="Loading monitors" description="Fetching monitor inventory and check state." />
  }

  if (monitorsQuery.isError) {
    return <ErrorState title="Could not load monitors" description="Verify API auth and availability, then retry." />
  }

  return (
    <section className="space-y-4">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <div>
          <h2 className="text-2xl font-semibold">Monitors</h2>
          <p className="text-sm text-text-secondary">Create, manage and execute checks for monitor targets.</p>
        </div>
        <div className="flex items-center gap-2">
          <Badge tone="warning">Admin write actions</Badge>
          <Button variant="secondary" onClick={selectForCreate}>New monitor</Button>
        </div>
      </div>

      <div className="grid gap-4 xl:grid-cols-[360px_1fr]">
        <aside className="space-y-3 rounded-lg border border-surface-border bg-bg-elevated p-4">
          <div className="grid gap-2">
            <Field label="Search monitors">
              <TextInput placeholder="Search by name, URL, ID" value={search} onChange={(event) => setSearch(event.target.value)} />
            </Field>
            <div className="grid gap-2 sm:grid-cols-2">
              <Field label="Last check status">
                <SelectInput value={statusFilter} onChange={(event) => setStatusFilter(event.target.value as 'ALL' | CheckStatus)}>
                  <option value="ALL">All</option>
                  <option value="UP">UP</option>
                  <option value="DOWN">DOWN</option>
                  <option value="UNKNOWN">UNKNOWN</option>
                </SelectInput>
              </Field>
              <Field label="Enabled">
                <SelectInput value={enabledFilter} onChange={(event) => setEnabledFilter(event.target.value as 'ALL' | 'ENABLED' | 'DISABLED')}>
                  <option value="ALL">All</option>
                  <option value="ENABLED">Enabled</option>
                  <option value="DISABLED">Disabled</option>
                </SelectInput>
              </Field>
            </div>
            <Field label="Sort">
              <SelectInput value={sortMode} onChange={(event) => setSortMode(event.target.value as SortMode)}>
                <option value="name">Name</option>
                <option value="newest">Newest first</option>
                <option value="oldest">Oldest first</option>
              </SelectInput>
            </Field>
          </div>

          {filteredMonitors.length > 0 ? (
            <div className="max-h-[70vh] space-y-2 overflow-auto pr-1">
              {filteredMonitors.map((monitor) => {
                const selected = selectedMonitor?.id === monitor.id
                return (
                  <button
                    key={monitor.id}
                    type="button"
                    onClick={() => selectForEdit(monitor)}
                    className={`w-full rounded-md border p-3 text-left transition ${
                      selected ? 'border-accent bg-bg-panel' : 'border-surface-border bg-bg-base hover:border-accent/40'
                    }`}
                  >
                    <div className="mb-2 flex items-center justify-between gap-2">
                      <p className="truncate text-sm font-medium">{monitor.name}</p>
                      <Badge tone={monitor.enabled ? 'success' : 'neutral'}>{monitor.enabled ? 'Enabled' : 'Disabled'}</Badge>
                    </div>
                    <div className="mb-1 flex flex-wrap gap-2 text-xs">
                      <Badge tone={statusTone(monitor.lastCheckStatus)}>{statusLabel(monitor.lastCheckStatus)}</Badge>
                    </div>
                    <p className="line-clamp-1 text-xs text-text-secondary">{monitor.targetUrl}</p>
                    <p className="mt-1 text-xs text-text-muted">Last check: {formatDateTime(monitor.lastCheckAt)}</p>
                  </button>
                )
              })}
            </div>
          ) : (
            <EmptyState title="No monitors found" description="Adjust filters or create your first monitor." />
          )}
        </aside>

        <div className="space-y-4 rounded-lg border border-surface-border bg-bg-elevated p-4">
          {selectedMonitor ? (
            <>
              <div className="flex flex-wrap items-center justify-between gap-2 border-b border-surface-border pb-4">
                <div>
                  <h3 className="text-lg font-semibold">{selectedMonitor.name}</h3>
                  <p className="text-xs text-text-muted">{selectedMonitor.id}</p>
                </div>
                <div className="flex flex-wrap items-center gap-2">
                  <Badge tone={statusTone(selectedMonitor.lastCheckStatus)}>{statusLabel(selectedMonitor.lastCheckStatus)}</Badge>
                  <Badge tone={selectedMonitor.enabled ? 'success' : 'neutral'}>{selectedMonitor.enabled ? 'Enabled' : 'Disabled'}</Badge>
                </div>
              </div>

              <div className="grid gap-3 md:grid-cols-4">
                <div className="rounded-md bg-bg-panel p-3">
                  <p className="text-xs text-text-muted">Interval</p>
                  <p className="text-sm text-text-secondary">{selectedMonitor.intervalSec}s</p>
                </div>
                <div className="rounded-md bg-bg-panel p-3">
                  <p className="text-xs text-text-muted">Timeout</p>
                  <p className="text-sm text-text-secondary">{selectedMonitor.timeoutMs}ms</p>
                </div>
                <div className="rounded-md bg-bg-panel p-3">
                  <p className="text-xs text-text-muted">Last status code</p>
                  <p className="text-sm text-text-secondary">{selectedMonitor.lastStatusCode ?? '—'}</p>
                </div>
                <div className="rounded-md bg-bg-panel p-3">
                  <p className="text-xs text-text-muted">Last latency</p>
                  <p className="text-sm text-text-secondary">{selectedMonitor.lastLatencyMs ?? '—'}ms</p>
                </div>
              </div>

              <div className="flex flex-wrap gap-2">
                <Button
                  variant="secondary"
                  disabled={toggleMonitorMutation.isPending}
                  onClick={() => toggleMonitor(selectedMonitor)}
                >
                  {selectedMonitor.enabled ? 'Disable' : 'Enable'} monitor
                </Button>
                <Button
                  variant="secondary"
                  disabled={runCheckMutation.isPending}
                  onClick={() => runCheck(selectedMonitor)}
                >
                  Run check now
                </Button>
              </div>
            </>
          ) : (
            <EmptyState title="No monitor selected" description="Select a monitor to view details and run actions." />
          )}

          <div className="rounded-md border border-surface-border p-4">
            <div className="mb-4 flex items-center justify-between border-b border-surface-border pb-3">
              <h4 className="font-semibold">{mode === 'create' ? 'Create monitor' : `Edit monitor ${selectedMonitor?.name ?? ''}`}</h4>
              {mode === 'edit' ? <Button variant="ghost" onClick={selectForCreate}>Switch to create</Button> : null}
            </div>

            {mode === 'edit' && monitorDetailQuery.isFetching ? <p className="mb-3 text-xs text-text-muted">Refreshing monitor details…</p> : null}

            <div className="grid gap-4 md:grid-cols-2">
              <Field label="Name">
                <TextInput value={form.name} maxLength={120} onChange={(event) => onChange('name', event.target.value)} />
                {errors.name ? <p className="text-xs text-red-300">{errors.name}</p> : null}
              </Field>

              <Field label="Type">
                <SelectInput value={form.type} onChange={(event) => onChange('type', event.target.value as 'HTTP')}>
                  <option value="HTTP">HTTP</option>
                </SelectInput>
              </Field>

              <Field label="Target URL">
                <TextInput value={form.targetUrl} maxLength={1024} onChange={(event) => onChange('targetUrl', event.target.value)} placeholder="https://example.com/health" />
                {errors.targetUrl ? <p className="text-xs text-red-300">{errors.targetUrl}</p> : null}
              </Field>

              <Field label="Enabled by default">
                <SelectInput value={form.enabled ? 'ENABLED' : 'DISABLED'} onChange={(event) => onChange('enabled', event.target.value === 'ENABLED')}>
                  <option value="ENABLED">Enabled</option>
                  <option value="DISABLED">Disabled</option>
                </SelectInput>
              </Field>

              <Field label="Interval (seconds)">
                <TextInput inputMode="numeric" value={form.intervalSec} onChange={(event) => onChange('intervalSec', event.target.value)} />
                {errors.intervalSec ? <p className="text-xs text-red-300">{errors.intervalSec}</p> : null}
              </Field>

              <Field label="Timeout (ms)">
                <TextInput inputMode="numeric" value={form.timeoutMs} onChange={(event) => onChange('timeoutMs', event.target.value)} />
                {errors.timeoutMs ? <p className="text-xs text-red-300">{errors.timeoutMs}</p> : null}
              </Field>
            </div>

            <div className="mt-4 flex gap-2">
              <Button onClick={submit} disabled={createMonitorMutation.isPending || updateMonitorMutation.isPending}>
                {mode === 'create' ? 'Create monitor' : 'Save changes'}
              </Button>
              <Button variant="secondary" onClick={selectForCreate}>Reset form</Button>
            </div>
          </div>
        </div>
      </div>
    </section>
  )
}
