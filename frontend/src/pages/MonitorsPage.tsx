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
  useDeleteMonitorMutation,
  useMonitorDetailQuery,
  useMonitorsQuery,
  useRunMonitorCheckMutation,
  useToggleMonitorMutation,
  useUpdateMonitorMutation,
} from '../lib/api/monitors'
import type { CheckStatus, CreateMonitorPayload, HttpMethod, Monitor, MonitorType, UpdateMonitorPayload } from '../types/monitor'
import { fromMonitor, hasErrors, initFormState, type FormErrors, type FormState, validateMonitorForm } from './monitorForm'

type SortMode = 'name' | 'newest' | 'oldest'


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


export function MonitorsPage() {
  const queryClient = useQueryClient()
  const monitorsQuery = useMonitorsQuery()

  const createMonitorMutation = useCreateMonitorMutation()
  const updateMonitorMutation = useUpdateMonitorMutation()
  const toggleMonitorMutation = useToggleMonitorMutation()
  const runCheckMutation = useRunMonitorCheckMutation()
  const deleteMonitorMutation = useDeleteMonitorMutation()

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

  const isHttpMonitor = form.type === 'HTTP'
  const targetLabel = isHttpMonitor ? 'Target URL' : 'Target'
  const targetPlaceholder = isHttpMonitor ? 'https://example.com/health' : form.type === 'TCP' ? 'example.com:443' : 'example.com'
  const targetHint = form.type === 'TCP'
    ? 'Format hint: host:port'
    : form.type === 'PING'
      ? 'Format hint: hostname or IP (without port)'
      : null

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
    const isHttp = current.type === 'HTTP'
    return {
      name: current.name.trim(),
      type: current.type ?? 'HTTP',
      targetUrl: current.targetUrl.trim(),
      intervalSec: Number(current.intervalSec),
      enabled: current.enabled,
      timeoutMs: Number(current.timeoutMs),
      httpMethod: isHttp ? current.httpMethod : undefined,
      expectedResponseKeyword: isHttp && current.expectedResponseKeyword.trim()
        ? current.expectedResponseKeyword.trim()
        : undefined,
      emailAlertOnDown: current.emailAlertOnDown,
      emailAlertOnRecovery: current.emailAlertOnRecovery,
    }
  }

  function toUpdatePayload(current: FormState): UpdateMonitorPayload {
    const isHttp = current.type === 'HTTP'
    return {
      name: current.name.trim(),
      type: current.type ?? 'HTTP',
      targetUrl: current.targetUrl.trim(),
      intervalSec: Number(current.intervalSec),
      enabled: current.enabled,
      timeoutMs: Number(current.timeoutMs),
      httpMethod: isHttp ? current.httpMethod : undefined,
      expectedResponseKeyword: isHttp && current.expectedResponseKeyword.trim()
        ? current.expectedResponseKeyword.trim()
        : undefined,
      emailAlertOnDown: current.emailAlertOnDown,
      emailAlertOnRecovery: current.emailAlertOnRecovery,
    }
  }

  async function submit() {
    const foundErrors = validateMonitorForm(form)
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

  async function deleteMonitor(monitor: Monitor) {
    const warning = [
      `Delete monitor "${monitor.name}"?`,
      '',
      'This permanently removes monitor configuration.',
      'Status page bindings will be detached automatically.',
      'Deletion is blocked when incident/check history exists.',
    ].join('\n')
    if (!window.confirm(warning)) return

    try {
      await deleteMonitorMutation.mutateAsync(monitor.id)
      notify.success('Monitor deleted. Status page bindings were detached automatically.')
      await refresh()
      setSelectedMonitorId(null)
      setMode('create')
      setForm(initFormState())
      setErrors({})
    } catch (error) {
      notify.error(getMonitorApiErrorMessage(error, 'Failed to delete monitor.'))
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
                  <p className="text-sm text-text-secondary">{Math.round(selectedMonitor.intervalSec / 60)} min ({selectedMonitor.intervalSec}s)</p>
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
                <Button
                  variant="secondary"
                  className="text-red-300 hover:bg-red-950/40"
                  disabled={deleteMonitorMutation.isPending}
                  onClick={() => deleteMonitor(selectedMonitor)}
                >
                  Delete monitor
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
                <SelectInput value={form.type} onChange={(event) => onChange('type', event.target.value as MonitorType)}>
                  <option value="HTTP">HTTP</option>
                  <option value="TCP">TCP</option>
                  <option value="PING">PING</option>
                </SelectInput>
              </Field>

              <Field label={targetLabel}>
                <TextInput value={form.targetUrl} maxLength={1024} onChange={(event) => onChange('targetUrl', event.target.value)} placeholder={targetPlaceholder} />
                {targetHint ? <p className="text-xs text-text-muted">{targetHint}</p> : null}
                {errors.targetUrl ? <p className="text-xs text-red-300">{errors.targetUrl}</p> : null}
              </Field>

              {isHttpMonitor ? (
                <Field label="HTTP method">
                  <SelectInput value={form.httpMethod} onChange={(event) => onChange('httpMethod', event.target.value as HttpMethod)}>
                    <option value="GET">GET</option>
                    <option value="POST">POST</option>
                    <option value="PUT">PUT</option>
                    <option value="DELETE">DELETE</option>
                    <option value="PATCH">PATCH</option>
                    <option value="OPTIONS">OPTIONS</option>
                    <option value="HEAD">HEAD</option>
                  </SelectInput>
                </Field>
              ) : null}

              {isHttpMonitor ? (
                <Field label="Expected response keyword (optional)">
                  <TextInput
                    value={form.expectedResponseKeyword}
                    maxLength={255}
                    onChange={(event) => onChange('expectedResponseKeyword', event.target.value)}
                    placeholder="healthy"
                  />
                </Field>
              ) : null}

              <Field label="Enabled by default">
                <SelectInput value={form.enabled ? 'ENABLED' : 'DISABLED'} onChange={(event) => onChange('enabled', event.target.value === 'ENABLED')}>
                  <option value="ENABLED">Enabled</option>
                  <option value="DISABLED">Disabled</option>
                </SelectInput>
              </Field>

              <Field label="Interval">
                <SelectInput value={form.intervalSec} onChange={(event) => onChange('intervalSec', event.target.value)}>
                  <option value="60">1 minute (60s)</option>
                  <option value="120">2 minutes (120s)</option>
                  <option value="180">3 minutes (180s)</option>
                  <option value="240">4 minutes (240s)</option>
                  <option value="300">5 minutes (300s)</option>
                </SelectInput>
                {errors.intervalSec ? <p className="text-xs text-red-300">{errors.intervalSec}</p> : null}
              </Field>

              <Field label="Email alert on incident down">
                <SelectInput value={form.emailAlertOnDown ? 'ON' : 'OFF'} onChange={(event) => onChange('emailAlertOnDown', event.target.value === 'ON')}>
                  <option value="ON">Enabled</option>
                  <option value="OFF">Disabled</option>
                </SelectInput>
              </Field>

              <Field label="Email alert on incident recovery">
                <SelectInput value={form.emailAlertOnRecovery ? 'ON' : 'OFF'} onChange={(event) => onChange('emailAlertOnRecovery', event.target.value === 'ON')}>
                  <option value="ON">Enabled</option>
                  <option value="OFF">Disabled</option>
                </SelectInput>
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
