import { useMemo, useState } from 'react'
import { useQueryClient } from '@tanstack/react-query'
import { notify } from '../components/feedback/toast'
import { EmptyState } from '../components/states/EmptyState'
import { ErrorState } from '../components/states/ErrorState'
import { LoadingState } from '../components/states/LoadingState'
import { Badge } from '../components/ui/Badge'
import { Button } from '../components/ui/Button'
import { Field, SelectInput, TextInput } from '../components/ui/FormControls'
import { DataTable } from '../components/ui/Table'
import {
  getNotificationPolicyApiErrorMessage,
  type NotificationEscalationStep,
  type NotificationPolicy,
  type NotificationPolicyScopeType,
  type NotificationSeverity,
  type NotificationChannel,
  type UpsertNotificationPolicyPayload,
  useCreateNotificationPolicyMutation,
  useNotificationPoliciesQuery,
  useUpdateNotificationPolicyMutation,
  useTestNotificationPolicyMutation,
} from '../lib/api/notificationPolicies'
import { useMonitorsQuery } from '../lib/api/monitors'
import { useStatusPagesQuery } from '../lib/api/statusPages'

const severityOrder: NotificationSeverity[] = ['CRITICAL', 'HIGH', 'MEDIUM', 'LOW', 'INFO']

const channelOrder: NotificationChannel[] = ['WEBHOOK', 'EMAIL', 'TELEGRAM', 'SLACK', 'DISCORD', 'TEAMS']

interface FormState {
  scopeType: NotificationPolicyScopeType
  scopeRefId: string
  enabled: boolean
  cooldownSeconds: string
  dedupSeconds: string
  routes: Record<NotificationSeverity, NotificationChannel[]>
  escalationSteps: Array<{
    stepOrder: string
    afterSeconds: string
    minSeverity: NotificationSeverity
    channels: NotificationChannel[]
  }>
}

interface FormErrors {
  scopeRefId?: string
  cooldownSeconds?: string
  dedupSeconds?: string
  routes?: string
  escalationSteps?: string
  escalationStepRows: Record<number, { stepOrder?: string; afterSeconds?: string; minSeverity?: string }>
}

function buildDefaultRoutes(): Record<NotificationSeverity, NotificationChannel[]> {
  return {
    CRITICAL: ['WEBHOOK'],
    HIGH: ['WEBHOOK'],
    MEDIUM: ['WEBHOOK'],
    LOW: ['EMAIL'],
    INFO: ['TELEGRAM'],
  }
}

function initFormState(): FormState {
  return {
    scopeType: 'GLOBAL',
    scopeRefId: '',
    enabled: true,
    cooldownSeconds: '120',
    dedupSeconds: '60',
    routes: buildDefaultRoutes(),
    escalationSteps: [{ stepOrder: '1', afterSeconds: '0', minSeverity: 'CRITICAL', channels: ['WEBHOOK'] }],
  }
}

function fromPolicy(policy: NotificationPolicy): FormState {
  const base = buildDefaultRoutes()
  for (const route of policy.routes) {
    base[route.severity] = route.channels
  }
  return {
    scopeType: policy.scopeType,
    scopeRefId: policy.scopeRefId ?? '',
    enabled: policy.enabled,
    cooldownSeconds: String(policy.cooldownSeconds),
    dedupSeconds: String(policy.dedupSeconds),
    routes: base,
    escalationSteps:
      policy.escalationSteps.length > 0
        ? [...policy.escalationSteps]
            .sort((a, b) => a.stepOrder - b.stepOrder)
            .map((step) => ({
              stepOrder: String(step.stepOrder),
              afterSeconds: String(step.afterSeconds),
              minSeverity: step.minSeverity,
              channels: step.channels,
            }))
        : [{ stepOrder: '1', afterSeconds: '0', minSeverity: 'CRITICAL', channels: ['WEBHOOK'] }],
  }
}

function initErrors(): FormErrors {
  return { escalationStepRows: {} }
}

function parseNonNegativeInteger(input: string) {
  if (!/^\d+$/.test(input.trim())) return null
  return Number(input)
}

function scopeLabel(policy: NotificationPolicy, monitorNameById: Record<string, string>, statusPageNameById: Record<string, string>) {
  if (policy.scopeType === 'GLOBAL') return 'GLOBAL'
  if (policy.scopeType === 'MONITOR') {
    const ref = policy.scopeRefId ?? ''
    return monitorNameById[ref] ? `MONITOR · ${monitorNameById[ref]}` : `MONITOR · ${ref}`
  }
  const ref = policy.scopeRefId ?? ''
  return statusPageNameById[ref] ? `STATUS_PAGE · ${statusPageNameById[ref]}` : `STATUS_PAGE · ${ref}`
}

function formatDateTime(value: string) {
  return new Date(value).toLocaleString()
}

function yesNoBadge(enabled: boolean) {
  return <Badge tone={enabled ? 'success' : 'neutral'}>{enabled ? 'Enabled' : 'Disabled'}</Badge>
}

export function NotificationPoliciesPage() {
  const queryClient = useQueryClient()
  const policiesQuery = useNotificationPoliciesQuery()
  const monitorsQuery = useMonitorsQuery()
  const statusPagesQuery = useStatusPagesQuery()

  const createMutation = useCreateNotificationPolicyMutation()
  const updateMutation = useUpdateNotificationPolicyMutation()
  const testMutation = useTestNotificationPolicyMutation()

  const [selectedId, setSelectedId] = useState<string | null>(null)
  const [mode, setMode] = useState<'create' | 'edit'>('create')
  const [form, setForm] = useState<FormState>(initFormState)
  const [errors, setErrors] = useState<FormErrors>(initErrors)

  const policies = useMemo(() => policiesQuery.data ?? [], [policiesQuery.data])

  const selectedPolicy = useMemo(
    () => policies.find((policy) => policy.id === selectedId) ?? null,
    [policies, selectedId],
  )

  const monitorNameById = useMemo(() => {
    const map: Record<string, string> = {}
    for (const monitor of monitorsQuery.data ?? []) map[monitor.id] = monitor.name
    return map
  }, [monitorsQuery.data])

  const statusPageNameById = useMemo(() => {
    const map: Record<string, string> = {}
    for (const page of statusPagesQuery.data ?? []) map[page.id] = page.name
    return map
  }, [statusPagesQuery.data])

  function onChange<K extends keyof FormState>(key: K, value: FormState[K]) {
    setForm((prev) => ({ ...prev, [key]: value }))
  }

  function selectForCreate() {
    setMode('create')
    setSelectedId(null)
    setForm(initFormState())
    setErrors(initErrors())
  }

  function selectForEdit(policy: NotificationPolicy) {
    setMode('edit')
    setSelectedId(policy.id)
    setForm(fromPolicy(policy))
    setErrors(initErrors())
  }

  function validate(current: FormState): FormErrors {
    const nextErrors: FormErrors = initErrors()

    if (current.scopeType !== 'GLOBAL' && !current.scopeRefId) {
      nextErrors.scopeRefId = `Select a reference for ${current.scopeType} scope.`
    }

    const cooldown = parseNonNegativeInteger(current.cooldownSeconds)
    if (cooldown === null) {
      nextErrors.cooldownSeconds = 'Cooldown must be an integer greater than or equal to 0.'
    }

    const dedup = parseNonNegativeInteger(current.dedupSeconds)
    if (dedup === null) {
      nextErrors.dedupSeconds = 'Dedup must be an integer greater than or equal to 0.'
    }

    if (severityOrder.every((severity) => current.routes[severity].length === 0)) {
      nextErrors.routes = 'Enable at least one severity route.'
    }

    if (current.escalationSteps.length === 0) {
      nextErrors.escalationSteps = 'Add at least one escalation step.'
    }

    const usedStepOrders = new Set<number>()
    current.escalationSteps.forEach((step, index) => {
      const rowError: { stepOrder?: string; afterSeconds?: string; minSeverity?: string } = {}
      const order = parseNonNegativeInteger(step.stepOrder)
      const after = parseNonNegativeInteger(step.afterSeconds)

      if (order === null || order < 1) {
        rowError.stepOrder = 'Step order must be an integer >= 1.'
      } else if (usedStepOrders.has(order)) {
        rowError.stepOrder = 'Step order must be unique.'
      } else {
        usedStepOrders.add(order)
      }

      if (after === null) {
        rowError.afterSeconds = 'Delay must be an integer >= 0.'
      }

      if (!step.minSeverity) {
        rowError.minSeverity = 'Select minimum severity.'
      }
      if (step.channels.length === 0) {
        rowError.minSeverity = 'Select at least one channel.'
      }

      if (Object.values(rowError).some(Boolean)) {
        nextErrors.escalationStepRows[index] = rowError
      }
    })

    return nextErrors
  }

  function hasErrors(value: FormErrors) {
    if (value.scopeRefId || value.cooldownSeconds || value.dedupSeconds || value.routes || value.escalationSteps) return true
    return Object.values(value.escalationStepRows).some((row) => Object.values(row).some(Boolean))
  }

  function toPayload(current: FormState): UpsertNotificationPolicyPayload {
    const escalationSteps: NotificationEscalationStep[] = current.escalationSteps
      .map((step) => ({
        stepOrder: Number(step.stepOrder),
        afterSeconds: Number(step.afterSeconds),
        minSeverity: step.minSeverity,
        channels: step.channels,
      }))
      .sort((a, b) => a.stepOrder - b.stepOrder)

    return {
      scopeType: current.scopeType,
      scopeRefId: current.scopeType === 'GLOBAL' ? null : current.scopeRefId,
      enabled: current.enabled,
      cooldownSeconds: Number(current.cooldownSeconds),
      dedupSeconds: Number(current.dedupSeconds),
      routes: severityOrder.map((severity) => ({ severity, channels: current.routes[severity] })),
      escalationSteps,
    }
  }

  async function refresh() {
    await queryClient.invalidateQueries({ queryKey: ['admin-notification-policies'] })
  }

  async function submit() {
    const foundErrors = validate(form)
    setErrors(foundErrors)
    if (hasErrors(foundErrors)) {
      notify.error('Fix form validation errors before saving.')
      return
    }

    const payload = toPayload(form)

    try {
      if (mode === 'create') {
        const created = await createMutation.mutateAsync(payload)
        notify.success('Notification policy created.')
        await refresh()
        setSelectedId(created.id)
        setMode('edit')
        setForm(fromPolicy(created))
      } else if (selectedPolicy) {
        const updated = await updateMutation.mutateAsync({ id: selectedPolicy.id, data: payload })
        notify.success('Notification policy updated.')
        await refresh()
        setForm(fromPolicy(updated))
      }
    } catch (error) {
      notify.error(getNotificationPolicyApiErrorMessage(error, 'Failed to save notification policy.'))
    }
  }

  const effectiveSummary = useMemo(() => {
    if (!selectedPolicy) {
      return 'Select or create a policy to preview best-effort effective behavior.'
    }

    const enabledSeverities = selectedPolicy.routes.filter((route) => route.channels.length > 0).map((route) => route.severity)

    const scopeMessage =
      selectedPolicy.scopeType === 'MONITOR'
        ? 'Monitor-scoped policy has highest precedence and overrides status-page/global policy for that monitor.'
        : selectedPolicy.scopeType === 'STATUS_PAGE'
          ? 'Status-page scope applies to monitors attached to that status page, unless monitor scope overrides it.'
          : 'Global policy is fallback when no status-page/monitor override exists.'

    const escalations = selectedPolicy.escalationSteps.length
      ? selectedPolicy.escalationSteps
          .slice()
          .sort((a, b) => a.stepOrder - b.stepOrder)
          .map((step) => `#${step.stepOrder}: +${step.afterSeconds}s from ${step.minSeverity} [${step.channels.join(', ') || 'none'}]`)
          .join(' · ')
      : 'No escalation steps configured.'

    return `${scopeMessage} Route severities with webhook enabled: ${enabledSeverities.join(', ') || 'none'}. Cooldown ${selectedPolicy.cooldownSeconds}s, dedup ${selectedPolicy.dedupSeconds}s. Escalation: ${escalations}`
  }, [selectedPolicy])

  if (policiesQuery.isLoading) {
    return <LoadingState title="Loading notification policies" description="Fetching admin notification policy config." />
  }

  if (policiesQuery.isError) {
    return <ErrorState title="Could not load notification policies" description="Verify API auth and availability, then retry." />
  }

  return (
    <section className="space-y-4">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <div>
          <h2 className="text-2xl font-semibold">Notification Policies</h2>
          <p className="text-sm text-text-secondary">Manage scope-level notification routing, suppression windows and escalation behavior.</p>
        </div>
        <div className="flex items-center gap-2">
          <Badge tone="warning">Admin</Badge>
          <Button variant="secondary" onClick={selectForCreate}>New policy</Button>
        </div>
      </div>

      {policies.length === 0 ? (
        <EmptyState title="No notification policies" description="Create your first policy to control routing and escalation behavior." />
      ) : (
        <DataTable
          data={policies}
          columns={[
            {
              key: 'scopeType',
              header: 'Scope',
              render: (_, row) => <span>{scopeLabel(row, monitorNameById, statusPageNameById)}</span>,
            },
            {
              key: 'enabled',
              header: 'Status',
              render: (value) => yesNoBadge(Boolean(value)),
            },
            { key: 'cooldownSeconds', header: 'Cooldown (s)' },
            { key: 'dedupSeconds', header: 'Dedup (s)' },
            {
              key: 'routes',
              header: 'Routes',
              render: (value) => <span>{(value as NotificationPolicy['routes']).filter((route) => route.channels.length > 0).map((route) => route.severity).join(', ') || 'none'}</span>,
            },
            {
              key: 'updatedAt',
              header: 'Updated',
              render: (value) => formatDateTime(String(value)),
            },
            {
              key: 'id',
              header: 'Actions',
              render: (_, row) => <Button variant="secondary" onClick={() => selectForEdit(row)}>Edit</Button>,
            },
          ]}
        />
      )}

      <div className="rounded-lg border border-surface-border bg-bg-elevated p-4">
        <div className="mb-4 flex items-center justify-between gap-2 border-b border-surface-border pb-3">
          <h3 className="text-lg font-semibold">{mode === 'create' ? 'Create notification policy' : `Edit policy ${selectedPolicy?.id ?? ''}`}</h3>
          {mode === 'edit' ? <Button variant="ghost" onClick={selectForCreate}>Switch to create</Button> : null}
        </div>

        <div className="grid gap-4 md:grid-cols-2">
          <Field label="Scope type">
            <SelectInput value={form.scopeType} onChange={(event) => onChange('scopeType', event.target.value as NotificationPolicyScopeType)}>
              <option value="GLOBAL">GLOBAL</option>
              <option value="STATUS_PAGE">STATUS_PAGE</option>
              <option value="MONITOR">MONITOR</option>
            </SelectInput>
          </Field>

          <Field label="Status">
            <SelectInput value={form.enabled ? 'ENABLED' : 'DISABLED'} onChange={(event) => onChange('enabled', event.target.value === 'ENABLED')}>
              <option value="ENABLED">Enabled</option>
              <option value="DISABLED">Disabled</option>
            </SelectInput>
          </Field>

          {form.scopeType !== 'GLOBAL' ? (
            <Field label={form.scopeType === 'MONITOR' ? 'Monitor' : 'Status page'}>
              <SelectInput value={form.scopeRefId} onChange={(event) => onChange('scopeRefId', event.target.value)}>
                <option value="">Select</option>
                {(form.scopeType === 'MONITOR' ? monitorsQuery.data ?? [] : statusPagesQuery.data ?? []).map((item) => (
                  <option key={item.id} value={item.id}>{item.name}</option>
                ))}
              </SelectInput>
              {errors.scopeRefId ? <p className="text-xs text-red-300">{errors.scopeRefId}</p> : null}
            </Field>
          ) : null}

          <Field label="Cooldown seconds">
            <TextInput inputMode="numeric" value={form.cooldownSeconds} onChange={(event) => onChange('cooldownSeconds', event.target.value)} />
            {errors.cooldownSeconds ? <p className="text-xs text-red-300">{errors.cooldownSeconds}</p> : null}
          </Field>

          <Field label="Dedup seconds">
            <TextInput inputMode="numeric" value={form.dedupSeconds} onChange={(event) => onChange('dedupSeconds', event.target.value)} />
            {errors.dedupSeconds ? <p className="text-xs text-red-300">{errors.dedupSeconds}</p> : null}
          </Field>
        </div>

        <div className="mt-4 rounded-md border border-surface-border p-3">
          <div className="mb-2 flex items-center justify-between">
            <h4 className="font-medium">Severity route rules (channels)</h4>
            {errors.routes ? <p className="text-xs text-red-300">{errors.routes}</p> : null}
          </div>
          <div className="space-y-2">
            {severityOrder.map((severity) => (
              <div key={severity} className="rounded-md border border-surface-border p-2">
                <p className="mb-2 text-xs text-text-muted">{severity}</p>
                <div className="flex flex-wrap gap-2">
                  {channelOrder.map((channel) => {
                    const enabled = form.routes[severity].includes(channel)
                    return (
                      <button
                        key={channel}
                        type="button"
                        className={`rounded-md border px-2 py-1 text-xs ${enabled ? 'border-accent bg-bg-panel text-text-primary' : 'border-surface-border text-text-secondary'}`}
                        onClick={() => onChange('routes', {
                          ...form.routes,
                          [severity]: enabled
                            ? form.routes[severity].filter((it) => it !== channel)
                            : [...form.routes[severity], channel],
                        })}
                      >
                        {channel} {enabled ? 'ON' : 'OFF'}
                      </button>
                    )
                  })}
                </div>
              </div>
            ))}
          </div>
        </div>

        <div className="mt-4 rounded-md border border-surface-border p-3">
          <div className="mb-3 flex items-center justify-between">
            <h4 className="font-medium">Escalation steps</h4>
            <Button
              variant="secondary"
              onClick={() =>
                onChange('escalationSteps', [
                  ...form.escalationSteps,
                  { stepOrder: String(form.escalationSteps.length + 1), afterSeconds: '300', minSeverity: 'HIGH', channels: ['WEBHOOK'] },
                ])
              }
            >
              Add step
            </Button>
          </div>

          {errors.escalationSteps ? <p className="mb-2 text-xs text-red-300">{errors.escalationSteps}</p> : null}

          <div className="space-y-3">
            {form.escalationSteps.map((step, index) => {
              const rowErrors = errors.escalationStepRows[index] ?? {}
              return (
                <div key={`${index}-${step.stepOrder}`} className="grid gap-2 rounded-md bg-bg-panel p-3 md:grid-cols-[120px_160px_1fr_auto_auto]">
                  <div>
                    <p className="mb-1 text-xs text-text-muted">Order</p>
                    <TextInput inputMode="numeric" value={step.stepOrder} onChange={(event) => onChange('escalationSteps', form.escalationSteps.map((item, i) => (i === index ? { ...item, stepOrder: event.target.value } : item)))} />
                    {rowErrors.stepOrder ? <p className="text-xs text-red-300">{rowErrors.stepOrder}</p> : null}
                  </div>
                  <div>
                    <p className="mb-1 text-xs text-text-muted">After (s)</p>
                    <TextInput inputMode="numeric" value={step.afterSeconds} onChange={(event) => onChange('escalationSteps', form.escalationSteps.map((item, i) => (i === index ? { ...item, afterSeconds: event.target.value } : item)))} />
                    {rowErrors.afterSeconds ? <p className="text-xs text-red-300">{rowErrors.afterSeconds}</p> : null}
                  </div>
                  <div>
                    <p className="mb-1 text-xs text-text-muted">Min severity</p>
                    <SelectInput value={step.minSeverity} onChange={(event) => onChange('escalationSteps', form.escalationSteps.map((item, i) => (i === index ? { ...item, minSeverity: event.target.value as NotificationSeverity } : item)))}>
                      {severityOrder.map((severity) => (
                        <option key={severity} value={severity}>{severity}</option>
                      ))}
                    </SelectInput>
                    {rowErrors.minSeverity ? <p className="text-xs text-red-300">{rowErrors.minSeverity}</p> : null}
                  </div>
                  <div className="flex items-end">
                    <div className="flex flex-wrap gap-1">
                      {channelOrder.map((channel) => {
                        const enabled = step.channels.includes(channel)
                        return (
                          <button
                            key={channel}
                            type="button"
                            className={`rounded-md border px-2 py-1 text-xs ${enabled ? 'border-accent bg-bg-panel text-text-primary' : 'border-surface-border text-text-secondary'}`}
                            onClick={() => onChange('escalationSteps', form.escalationSteps.map((item, i) => (i === index
                              ? { ...item, channels: enabled ? item.channels.filter((it) => it !== channel) : [...item.channels, channel] }
                              : item)))}
                          >
                            {channel}
                          </button>
                        )
                      })}
                    </div>
                  </div>
                  <div className="flex items-end">
                    <Button
                      variant="ghost"
                      disabled={form.escalationSteps.length <= 1}
                      onClick={() => onChange('escalationSteps', form.escalationSteps.filter((_, i) => i !== index))}
                    >
                      Remove
                    </Button>
                  </div>
                </div>
              )
            })}
          </div>
        </div>

        <div className="mt-4 rounded-md border border-surface-border bg-bg-panel p-3 text-sm">
          <p className="text-xs text-text-muted">Effective policy summary (best-effort)</p>
          <p className="mt-1 text-text-secondary">{effectiveSummary}</p>
        </div>

        <div className="mt-4 flex gap-2">
          <Button onClick={submit} disabled={createMutation.isPending || updateMutation.isPending}>{mode === 'create' ? 'Create policy' : 'Save changes'}</Button>
          <Button variant="secondary" onClick={selectForCreate}>Reset form</Button>
          {mode === 'edit' && selectedPolicy ? (
            <Button
              variant="secondary"
              onClick={async () => {
                try {
                  const channels = Array.from(new Set(form.escalationSteps.flatMap((step) => step.channels).concat(severityOrder.flatMap((severity) => form.routes[severity]))))
                  await testMutation.mutateAsync({ id: selectedPolicy.id, channels, reason: 'ui-test-trigger' })
                  notify.success('Test notification triggered.')
                } catch (error) {
                  notify.error(getNotificationPolicyApiErrorMessage(error, 'Failed to trigger test notification.'))
                }
              }}
              disabled={testMutation.isPending}
            >
              Trigger test
            </Button>
          ) : null}
        </div>
      </div>
    </section>
  )
}
