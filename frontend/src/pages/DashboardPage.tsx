import { useMemo, useState } from 'react'
import { ErrorState } from '../components/states/ErrorState'
import { LoadingState } from '../components/states/LoadingState'
import { Badge } from '../components/ui/Badge'
import { Button } from '../components/ui/Button'
import { Field, SelectInput } from '../components/ui/FormControls'
import { useOpsDashboardQuery } from '../lib/api/observability'

const defaultRefreshMs = 10_000
const refreshOptionsMs = [5_000, 10_000, 30_000, 60_000]

function formatPercent(value: number) {
  return `${(value * 100).toFixed(1)}%`
}

function formatSeconds(value: number) {
  return `${value.toFixed(2)}s`
}

function formatCount(value: number) {
  return Intl.NumberFormat('en-US').format(Math.round(value))
}

interface PanelProps {
  title: string
  badge: { text: string; tone: 'neutral' | 'success' | 'warning' | 'critical' }
  lines: Array<{ label: string; value: string }>
}

function DashboardPanel({ title, badge, lines }: PanelProps) {
  return (
    <article className="rounded-lg border border-surface-border bg-bg-elevated p-4">
      <div className="mb-3 flex items-center justify-between gap-2">
        <h3 className="font-medium">{title}</h3>
        <Badge tone={badge.tone}>{badge.text}</Badge>
      </div>
      <dl className="space-y-2">
        {lines.map((line) => (
          <div key={line.label} className="flex items-center justify-between gap-3 text-sm">
            <dt className="text-text-secondary">{line.label}</dt>
            <dd className="font-medium text-text-primary">{line.value}</dd>
          </div>
        ))}
      </dl>
    </article>
  )
}

export function DashboardPage() {
  const [autoRefreshEnabled, setAutoRefreshEnabled] = useState(true)
  const [refreshMs, setRefreshMs] = useState(defaultRefreshMs)

  const dashboardQuery = useOpsDashboardQuery(refreshMs, autoRefreshEnabled)

  const lastUpdated = useMemo(() => {
    if (!dashboardQuery.dataUpdatedAt) {
      return '—'
    }
    return new Date(dashboardQuery.dataUpdatedAt).toLocaleTimeString()
  }, [dashboardQuery.dataUpdatedAt])

  if (dashboardQuery.isLoading) {
    return <LoadingState title="Loading Ops Observability" description="Collecting scheduler, notifier, and DLQ telemetry." />
  }

  if (dashboardQuery.isError || !dashboardQuery.data) {
    return (
      <ErrorState
        title="Ops observability unavailable"
        description="Unable to load metrics from actuator endpoints. Verify ADMIN access and backend availability."
        action={
          <Button variant="secondary" onClick={() => void dashboardQuery.refetch()}>
            Retry
          </Button>
        }
      />
    )
  }

  const { lock, scheduler, dlq, notifier, latency } = dashboardQuery.data

  return (
    <section className="space-y-6">
      <div className="flex flex-wrap items-start justify-between gap-4">
        <div>
          <h2 className="text-2xl font-semibold">Ops Observability Dashboard</h2>
          <p className="text-sm text-text-secondary">Live operational telemetry for scheduler locks, delivery reliability, and alert pipeline health.</p>
        </div>
        <div className="flex flex-wrap items-end gap-3 rounded-lg border border-surface-border bg-bg-elevated p-3">
          <Field label="Auto-refresh">
            <Button variant={autoRefreshEnabled ? 'primary' : 'secondary'} onClick={() => setAutoRefreshEnabled((value) => !value)}>
              {autoRefreshEnabled ? 'Enabled' : 'Paused'}
            </Button>
          </Field>
          <Field label="Interval">
            <SelectInput
              value={refreshMs}
              onChange={(event) => {
                setRefreshMs(Number(event.target.value))
              }}
              disabled={!autoRefreshEnabled}
            >
              {refreshOptionsMs.map((option) => (
                <option key={option} value={option}>
                  {option / 1000}s
                </option>
              ))}
            </SelectInput>
          </Field>
          <Field label="Last updated">
            <p className="rounded-md border border-surface-border bg-bg-panel px-3 py-2 text-sm text-text-primary">{lastUpdated}</p>
          </Field>
        </div>
      </div>

      <div className="grid gap-4 lg:grid-cols-2">
        <DashboardPanel
          title="Lock contention"
          badge={{ text: formatPercent(lock.contentionRatio), tone: lock.contentionRatio > 0.2 ? 'critical' : lock.contentionRatio > 0.1 ? 'warning' : 'success' }}
          lines={[
            { label: 'Acquire success', value: formatCount(lock.lockAcquireSuccess) },
            { label: 'Acquire fail', value: formatCount(lock.lockAcquireFail) },
            { label: 'Acquire steals', value: formatCount(lock.lockAcquireSteal) },
            { label: 'Renew failures', value: formatCount(lock.lockRenewFail) },
          ]}
        />

        <DashboardPanel
          title="Scheduler skip rates"
          badge={{ text: formatPercent(scheduler.skipLockRatio), tone: scheduler.skipLockRatio > 0.2 ? 'critical' : scheduler.skipLockRatio > 0.1 ? 'warning' : 'neutral' }}
          lines={[
            { label: 'Skip: distributed lock', value: formatCount(scheduler.skipLock) },
            { label: 'Skip: local in-flight', value: formatCount(scheduler.skipLocalInflight) },
            { label: 'Lock skip share', value: formatPercent(scheduler.skipLockRatio) },
          ]}
        />

        <DashboardPanel
          title="DLQ backlog"
          badge={{ text: formatCount(dlq.backlog), tone: dlq.backlog > 25 ? 'critical' : dlq.backlog > 0 ? 'warning' : 'success' }}
          lines={[
            { label: 'Current backlog', value: formatCount(dlq.backlog) },
            { label: 'Oldest age', value: formatSeconds(dlq.oldestAgeSeconds) },
          ]}
        />

        <DashboardPanel
          title="Notifier failure rate"
          badge={{ text: formatPercent(notifier.failureRatio), tone: notifier.failureRatio > 0.05 ? 'critical' : notifier.failureRatio > 0.01 ? 'warning' : 'success' }}
          lines={[
            { label: 'Dispatch success', value: formatCount(notifier.dispatchSuccess) },
            { label: 'Dispatch failed', value: formatCount(notifier.dispatchFailed) },
            { label: 'Failure ratio', value: formatPercent(notifier.failureRatio) },
          ]}
        />

        <DashboardPanel
          title="Dispatch latency"
          badge={{ text: formatSeconds(latency.dispatchMeanSeconds), tone: latency.dispatchMeanSeconds > 5 ? 'warning' : 'neutral' }}
          lines={[
            { label: 'Mean latency', value: formatSeconds(latency.dispatchMeanSeconds) },
            { label: 'Max latency', value: formatSeconds(latency.dispatchMaxSeconds) },
          ]}
        />

        <DashboardPanel
          title="Delivery latency"
          badge={{ text: formatSeconds(latency.deliveryMeanSeconds), tone: latency.deliveryMeanSeconds > 120 ? 'critical' : latency.deliveryMeanSeconds > 60 ? 'warning' : 'success' }}
          lines={[
            { label: 'Mean delay', value: formatSeconds(latency.deliveryMeanSeconds) },
            { label: 'Max delay', value: formatSeconds(latency.deliveryMaxSeconds) },
          ]}
        />
      </div>
    </section>
  )
}
