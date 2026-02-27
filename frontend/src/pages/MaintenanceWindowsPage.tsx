import { useMemo, useState } from 'react'
import { useQueryClient } from '@tanstack/react-query'
import { notify } from '../components/feedback/toast'
import { EmptyState } from '../components/states/EmptyState'
import { ErrorState } from '../components/states/ErrorState'
import { LoadingState } from '../components/states/LoadingState'
import { Badge } from '../components/ui/Badge'
import { Button } from '../components/ui/Button'
import { DataTable } from '../components/ui/Table'
import { Field, SelectInput, TextInput } from '../components/ui/FormControls'
import { useMonitorsQuery } from '../lib/api/monitors'
import {
  getMaintenanceApiErrorMessage,
  type MaintenancePolicy,
  type MaintenanceWindow,
  type MaintenanceWindowScopeType,
  type MaintenanceWindowType,
  type UpsertMaintenanceWindowPayload,
  useCreateMaintenanceWindowMutation,
  useDeleteMaintenanceWindowMutation,
  useMaintenanceWindowsQuery,
  useUpdateMaintenanceWindowMutation,
} from '../lib/api/maintenanceWindows'

interface FormState {
  name: string
  scopeType: MaintenanceWindowScopeType
  scopeRefId: string
  type: MaintenanceWindowType
  policy: MaintenancePolicy
  enabled: boolean
  startAt: string
  endAt: string
  timezone: string
  recurringDays: string[]
  recurringStartTime: string
  recurringEndTime: string
}

const dayOptions = ['MONDAY', 'TUESDAY', 'WEDNESDAY', 'THURSDAY', 'FRIDAY', 'SATURDAY', 'SUNDAY']

function initFormState(): FormState {
  return {
    name: '',
    scopeType: 'GLOBAL',
    scopeRefId: '',
    type: 'ONE_TIME',
    policy: 'SUPPRESS',
    enabled: true,
    startAt: '',
    endAt: '',
    timezone: Intl.DateTimeFormat().resolvedOptions().timeZone || 'UTC',
    recurringDays: ['MONDAY'],
    recurringStartTime: '02:00',
    recurringEndTime: '03:00',
  }
}

function toLocalDateTimeInput(value: string | null) {
  if (!value) return ''
  const date = new Date(value)
  const local = new Date(date.getTime() - date.getTimezoneOffset() * 60_000)
  return local.toISOString().slice(0, 16)
}

function toIsoFromLocalDateTime(value: string) {
  return value ? new Date(value).toISOString() : null
}

function fromWindow(window: MaintenanceWindow): FormState {
  return {
    name: window.name,
    scopeType: window.scopeType,
    scopeRefId: window.scopeRefId ?? '',
    type: window.type,
    policy: window.policy,
    enabled: window.enabled,
    startAt: toLocalDateTimeInput(window.startAt),
    endAt: toLocalDateTimeInput(window.endAt),
    timezone: window.timezone || Intl.DateTimeFormat().resolvedOptions().timeZone || 'UTC',
    recurringDays: window.recurringDays?.length ? window.recurringDays : ['MONDAY'],
    recurringStartTime: window.recurringStartTime ?? '02:00',
    recurringEndTime: window.recurringEndTime ?? '03:00',
  }
}

function formatDateTime(value: string | null) {
  if (!value) return '—'
  return new Date(value).toLocaleString()
}

function policyTone(policy: MaintenancePolicy) {
  return policy === 'SUPPRESS' ? 'critical' : 'warning'
}

function scopeLabel(window: MaintenanceWindow, monitorNameById: Record<string, string>) {
  if (window.scopeType === 'GLOBAL') return 'GLOBAL'
  return monitorNameById[window.scopeRefId ?? ''] ? `MONITOR · ${monitorNameById[window.scopeRefId ?? '']}` : `MONITOR · ${window.scopeRefId}`
}

export function MaintenanceWindowsPage() {
  const queryClient = useQueryClient()
  const windowsQuery = useMaintenanceWindowsQuery()
  const monitorsQuery = useMonitorsQuery()

  const createMutation = useCreateMaintenanceWindowMutation()
  const updateMutation = useUpdateMaintenanceWindowMutation()
  const deleteMutation = useDeleteMaintenanceWindowMutation()

  const [selectedId, setSelectedId] = useState<string | null>(null)
  const [mode, setMode] = useState<'create' | 'edit'>('create')
  const [form, setForm] = useState<FormState>(initFormState)
  const [errors, setErrors] = useState<Partial<Record<keyof FormState, string>>>({})

  const windows = windowsQuery.data ?? []

  const selectedWindow = useMemo(
    () => (windowsQuery.data ?? []).find((item) => item.id === selectedId) ?? null,
    [selectedId, windowsQuery.data],
  )

  const monitorNameById = useMemo(() => {
    const map: Record<string, string> = {}
    for (const monitor of monitorsQuery.data ?? []) {
      map[monitor.id] = monitor.name
    }
    return map
  }, [monitorsQuery.data])

  const timezoneOptions = useMemo(() => {
    if (typeof Intl.supportedValuesOf === 'function') {
      return Intl.supportedValuesOf('timeZone')
    }
    return ['UTC']
  }, [])

  function onChange<K extends keyof FormState>(key: K, value: FormState[K]) {
    setForm((prev) => ({ ...prev, [key]: value }))
    setErrors((prev) => ({ ...prev, [key]: undefined }))
  }

  function selectForCreate() {
    setMode('create')
    setSelectedId(null)
    setForm(initFormState())
    setErrors({})
  }

  function selectForEdit(window: MaintenanceWindow) {
    setMode('edit')
    setSelectedId(window.id)
    setForm(fromWindow(window))
    setErrors({})
  }

  function validate(current: FormState) {
    const nextErrors: Partial<Record<keyof FormState, string>> = {}

    if (!current.name.trim()) {
      nextErrors.name = 'Name is required.'
    }

    if (current.scopeType === 'MONITOR' && !current.scopeRefId) {
      nextErrors.scopeRefId = 'Select a monitor for MONITOR scope.'
    }

    if (current.type === 'ONE_TIME') {
      if (!current.startAt) nextErrors.startAt = 'Start date/time is required.'
      if (!current.endAt) nextErrors.endAt = 'End date/time is required.'
      if (current.startAt && current.endAt && new Date(current.endAt).getTime() <= new Date(current.startAt).getTime()) {
        nextErrors.endAt = 'End date/time must be after start date/time.'
      }
    }

    if (current.type === 'RECURRING') {
      if (!current.timezone) nextErrors.timezone = 'Timezone is required.'
      if (current.recurringDays.length === 0) nextErrors.recurringDays = 'Pick at least one recurring day.'
      if (!current.recurringStartTime) nextErrors.recurringStartTime = 'Recurring start time is required.'
      if (!current.recurringEndTime) nextErrors.recurringEndTime = 'Recurring end time is required.'
      if (current.recurringStartTime && current.recurringEndTime && current.recurringStartTime === current.recurringEndTime) {
        nextErrors.recurringEndTime = 'Start and end time cannot be the same.'
      }
    }

    return nextErrors
  }

  function toPayload(current: FormState): UpsertMaintenanceWindowPayload {
    return {
      name: current.name.trim(),
      scopeType: current.scopeType,
      scopeRefId: current.scopeType === 'MONITOR' ? current.scopeRefId || null : null,
      type: current.type,
      policy: current.policy,
      enabled: current.enabled,
      startAt: current.type === 'ONE_TIME' ? toIsoFromLocalDateTime(current.startAt) : null,
      endAt: current.type === 'ONE_TIME' ? toIsoFromLocalDateTime(current.endAt) : null,
      timezone: current.type === 'RECURRING' ? current.timezone : null,
      recurringDays: current.type === 'RECURRING' ? current.recurringDays : [],
      recurringStartTime: current.type === 'RECURRING' ? current.recurringStartTime : null,
      recurringEndTime: current.type === 'RECURRING' ? current.recurringEndTime : null,
    }
  }

  async function refresh() {
    await queryClient.invalidateQueries({ queryKey: ['admin-maintenance-windows'] })
  }

  async function submit() {
    const foundErrors = validate(form)
    setErrors(foundErrors)
    if (Object.values(foundErrors).some(Boolean)) return

    const payload = toPayload(form)

    try {
      if (mode === 'create') {
        const created = await createMutation.mutateAsync(payload)
        notify.success('Maintenance window created.')
        await refresh()
        if (created?.id) {
          setSelectedId(created.id)
          setMode('edit')
          setForm(fromWindow(created))
        }
      } else if (selectedWindow) {
        const updated = await updateMutation.mutateAsync({ id: selectedWindow.id, data: payload })
        notify.success('Maintenance window updated.')
        await refresh()
        setForm(fromWindow(updated))
      }
    } catch (error) {
      notify.error(getMaintenanceApiErrorMessage(error, 'Failed to save maintenance window.'))
    }
  }

  async function remove(item: MaintenanceWindow) {
    const ok = window.confirm(`Delete maintenance window "${item.name}"?`)
    if (!ok) return

    try {
      await deleteMutation.mutateAsync(item.id)
      notify.success('Maintenance window deleted.')
      await refresh()
      if (selectedId === item.id) {
        selectForCreate()
      }
    } catch (error) {
      notify.error(getMaintenanceApiErrorMessage(error, 'Failed to delete maintenance window.'))
    }
  }

  const summary = useMemo(() => {
    if (form.type === 'ONE_TIME') {
      if (!form.startAt || !form.endAt) return 'Set start and end to preview the one-time schedule.'
      const startIso = toIsoFromLocalDateTime(form.startAt)
      const endIso = toIsoFromLocalDateTime(form.endAt)
      if (!startIso || !endIso) return 'Invalid one-time schedule.'
      return `${new Date(startIso).toLocaleString()} → ${new Date(endIso).toLocaleString()} (stored in UTC)`
    }

    if (form.recurringDays.length === 0 || !form.recurringStartTime || !form.recurringEndTime || !form.timezone) {
      return 'Pick timezone, days and start/end times to preview recurring maintenance.'
    }

    const days = form.recurringDays.map((day) => day.slice(0, 3)).join(', ')
    const overnight = form.recurringEndTime <= form.recurringStartTime ? ' (overnight)' : ''
    return `${days} · ${form.recurringStartTime} → ${form.recurringEndTime}${overnight} · ${form.timezone}`
  }, [form])

  if (windowsQuery.isLoading) {
    return <LoadingState title="Loading maintenance windows" description="Fetching admin scheduling configuration." />
  }

  if (windowsQuery.isError) {
    return <ErrorState title="Could not load maintenance windows" description="Verify API auth and availability, then retry." />
  }

  return (
    <section className="space-y-4">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <div>
          <h2 className="text-2xl font-semibold">Maintenance Windows</h2>
          <p className="text-sm text-text-secondary">Configure one-time and recurring maintenance suppression/annotation policies.</p>
        </div>
        <div className="flex items-center gap-2">
          <Badge tone="warning">Admin</Badge>
          <Button variant="secondary" onClick={selectForCreate}>New window</Button>
        </div>
      </div>

      {windows.length === 0 ? (
        <EmptyState title="No maintenance windows" description="Create your first schedule to suppress or annotate incident handling." />
      ) : (
        <DataTable
          data={windows}
          columns={[
            { key: 'name', header: 'Name' },
            {
              key: 'scopeType',
              header: 'Scope',
              render: (_, row) => <span>{scopeLabel(row, monitorNameById)}</span>,
            },
            {
              key: 'type',
              header: 'Type',
              render: (value) => <Badge tone="neutral">{String(value)}</Badge>,
            },
            {
              key: 'policy',
              header: 'Policy',
              render: (value) => <Badge tone={policyTone(value as MaintenancePolicy)}>{String(value)}</Badge>,
            },
            {
              key: 'enabled',
              header: 'Status',
              render: (value) => <Badge tone={value ? 'success' : 'neutral'}>{value ? 'Enabled' : 'Disabled'}</Badge>,
            },
            {
              key: 'updatedAt',
              header: 'Updated',
              render: (value) => formatDateTime(String(value)),
            },
            {
              key: 'id',
              header: 'Actions',
              render: (_, row) => (
                <div className="flex gap-2">
                  <Button variant="secondary" onClick={() => selectForEdit(row)}>Edit</Button>
                  <Button variant="ghost" onClick={() => remove(row)}>Delete</Button>
                </div>
              ),
            },
          ]}
        />
      )}

      <div className="rounded-lg border border-surface-border bg-bg-elevated p-4">
        <div className="mb-4 flex items-center justify-between gap-2 border-b border-surface-border pb-3">
          <h3 className="text-lg font-semibold">{mode === 'create' ? 'Create maintenance window' : `Edit: ${selectedWindow?.name ?? 'Maintenance window'}`}</h3>
          {mode === 'edit' ? (
            <Button variant="ghost" onClick={selectForCreate}>Switch to create</Button>
          ) : null}
        </div>

        <div className="grid gap-4 md:grid-cols-2">
          <Field label="Name">
            <TextInput value={form.name} onChange={(event) => onChange('name', event.target.value)} maxLength={120} placeholder="e.g. weekly patching" />
            {errors.name ? <p className="text-xs text-red-300">{errors.name}</p> : null}
          </Field>

          <Field label="Policy">
            <SelectInput value={form.policy} onChange={(event) => onChange('policy', event.target.value as MaintenancePolicy)}>
              <option value="SUPPRESS">SUPPRESS (skip incident create/close)</option>
              <option value="ANNOTATE">ANNOTATE (allow incidents + annotate reason)</option>
            </SelectInput>
          </Field>

          <Field label="Scope type">
            <SelectInput value={form.scopeType} onChange={(event) => onChange('scopeType', event.target.value as MaintenanceWindowScopeType)}>
              <option value="GLOBAL">GLOBAL</option>
              <option value="MONITOR">MONITOR</option>
            </SelectInput>
          </Field>

          <Field label="Status">
            <SelectInput value={form.enabled ? 'ENABLED' : 'DISABLED'} onChange={(event) => onChange('enabled', event.target.value === 'ENABLED')}>
              <option value="ENABLED">Enabled</option>
              <option value="DISABLED">Disabled</option>
            </SelectInput>
          </Field>

          {form.scopeType === 'MONITOR' ? (
            <Field label="Monitor">
              <SelectInput value={form.scopeRefId} onChange={(event) => onChange('scopeRefId', event.target.value)}>
                <option value="">Select monitor</option>
                {(monitorsQuery.data ?? []).map((monitor) => (
                  <option key={monitor.id} value={monitor.id}>
                    {monitor.name}
                  </option>
                ))}
              </SelectInput>
              {errors.scopeRefId ? <p className="text-xs text-red-300">{errors.scopeRefId}</p> : null}
            </Field>
          ) : null}

          <Field label="Window type">
            <SelectInput value={form.type} onChange={(event) => onChange('type', event.target.value as MaintenanceWindowType)}>
              <option value="ONE_TIME">ONE_TIME</option>
              <option value="RECURRING">RECURRING</option>
            </SelectInput>
          </Field>

          {form.type === 'ONE_TIME' ? (
            <>
              <Field label="Start (local timezone)">
                <TextInput type="datetime-local" value={form.startAt} onChange={(event) => onChange('startAt', event.target.value)} />
                {errors.startAt ? <p className="text-xs text-red-300">{errors.startAt}</p> : null}
              </Field>
              <Field label="End (local timezone)">
                <TextInput type="datetime-local" value={form.endAt} onChange={(event) => onChange('endAt', event.target.value)} />
                {errors.endAt ? <p className="text-xs text-red-300">{errors.endAt}</p> : null}
              </Field>
            </>
          ) : (
            <>
              <Field label="Timezone">
                <SelectInput value={form.timezone} onChange={(event) => onChange('timezone', event.target.value)}>
                  {timezoneOptions.map((zone) => (
                    <option key={zone} value={zone}>
                      {zone}
                    </option>
                  ))}
                </SelectInput>
                {errors.timezone ? <p className="text-xs text-red-300">{errors.timezone}</p> : null}
              </Field>

              <Field label="Recurring days">
                <div className="grid grid-cols-2 gap-2 md:grid-cols-4">
                  {dayOptions.map((day) => {
                    const active = form.recurringDays.includes(day)
                    return (
                      <button
                        key={day}
                        type="button"
                        className={`rounded-md border px-3 py-2 text-xs ${active ? 'border-accent bg-bg-panel text-text-primary' : 'border-surface-border text-text-secondary'}`}
                        onClick={() => {
                          onChange(
                            'recurringDays',
                            active ? form.recurringDays.filter((item) => item !== day) : [...form.recurringDays, day],
                          )
                        }}
                      >
                        {day.slice(0, 3)}
                      </button>
                    )
                  })}
                </div>
                {errors.recurringDays ? <p className="text-xs text-red-300">{errors.recurringDays}</p> : null}
              </Field>

              <Field label="Recurring start">
                <TextInput type="time" value={form.recurringStartTime} onChange={(event) => onChange('recurringStartTime', event.target.value)} />
                {errors.recurringStartTime ? <p className="text-xs text-red-300">{errors.recurringStartTime}</p> : null}
              </Field>

              <Field label="Recurring end">
                <TextInput type="time" value={form.recurringEndTime} onChange={(event) => onChange('recurringEndTime', event.target.value)} />
                {errors.recurringEndTime ? <p className="text-xs text-red-300">{errors.recurringEndTime}</p> : null}
              </Field>
            </>
          )}
        </div>

        <div className="mt-4 rounded-md border border-surface-border bg-bg-panel p-3 text-sm">
          <p className="text-xs text-text-muted">Active-window preview</p>
          <p className="mt-1 text-text-secondary">{summary}</p>
        </div>

        <div className="mt-4 flex gap-2">
          <Button onClick={submit} disabled={createMutation.isPending || updateMutation.isPending}>
            {mode === 'create' ? 'Create window' : 'Save changes'}
          </Button>
          <Button variant="secondary" onClick={selectForCreate}>Reset form</Button>
        </div>
      </div>
    </section>
  )
}
