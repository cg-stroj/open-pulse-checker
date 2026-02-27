import { useMemo, useState } from 'react'
import { notify } from '../components/feedback/toast'
import { ErrorState } from '../components/states/ErrorState'
import { LoadingState } from '../components/states/LoadingState'
import { Badge } from '../components/ui/Badge'
import { Button } from '../components/ui/Button'
import { Field, SelectInput, TextInput } from '../components/ui/FormControls'
import { DataTable } from '../components/ui/Table'
import { exportAuditEvents, useAuditEventsQuery, type AuditEventFilters } from '../lib/api/audit'

interface UiFilters {
  q: string
  actor: string
  action: string
  resource: string
  outcome: string
  fromAt: string
  toAt: string
}

const pageSizes = [10, 25, 50, 100]

function toApiDate(value: string) {
  if (!value) return undefined
  return new Date(value).toISOString()
}

function formatDate(value: string) {
  return new Date(value).toLocaleString()
}

function defaultFilters(): UiFilters {
  return {
    q: '',
    actor: '',
    action: '',
    resource: '',
    outcome: '',
    fromAt: '',
    toAt: '',
  }
}

export function AuditExplorerPage() {
  const [filters, setFilters] = useState<UiFilters>(defaultFilters)
  const [page, setPage] = useState(0)
  const [size, setSize] = useState(25)
  const [isExporting, setIsExporting] = useState<'csv' | 'json' | null>(null)

  const apiFilters = useMemo<AuditEventFilters>(
    () => ({
      q: filters.q,
      actor: filters.actor,
      action: filters.action,
      resource: filters.resource,
      outcome: filters.outcome,
      fromAt: toApiDate(filters.fromAt),
      toAt: toApiDate(filters.toAt),
    }),
    [filters],
  )

  const auditQuery = useAuditEventsQuery({
    ...apiFilters,
    page,
    size,
  })

  async function onExport(format: 'csv' | 'json') {
    setIsExporting(format)
    try {
      await exportAuditEvents(format, apiFilters)
      notify.success(`Audit export ready (${format.toUpperCase()}).`)
    } catch {
      notify.error(`Audit export failed (${format.toUpperCase()}). Please retry.`)
    } finally {
      setIsExporting(null)
    }
  }

  function onFilterChange<K extends keyof UiFilters>(key: K, value: UiFilters[K]) {
    setFilters((prev) => ({ ...prev, [key]: value }))
    setPage(0)
  }

  if (auditQuery.isLoading) {
    return <LoadingState title="Loading audit events" description="Fetching administrative audit trail." />
  }

  if (auditQuery.isError || !auditQuery.data) {
    return <ErrorState title="Could not load audit events" description="Verify API auth and availability, then retry." />
  }

  return (
    <section className="space-y-4">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <div>
          <h2 className="text-2xl font-semibold">Audit Explorer</h2>
          <p className="text-sm text-text-secondary">Troubleshoot admin actions with searchable audit history and export support.</p>
        </div>
        <Badge tone="warning">Admin</Badge>
      </div>

      <div className="rounded-lg border border-surface-border bg-bg-elevated p-4">
        <div className="grid gap-3 md:grid-cols-3 xl:grid-cols-4">
          <Field label="Search">
            <TextInput placeholder="Actor, action, resource, details" value={filters.q} onChange={(event) => onFilterChange('q', event.target.value)} />
          </Field>
          <Field label="Actor">
            <TextInput placeholder="admin" value={filters.actor} onChange={(event) => onFilterChange('actor', event.target.value)} />
          </Field>
          <Field label="Action">
            <TextInput placeholder="INCIDENT_RESOLVE" value={filters.action} onChange={(event) => onFilterChange('action', event.target.value)} />
          </Field>
          <Field label="Resource">
            <TextInput placeholder="incident/uuid" value={filters.resource} onChange={(event) => onFilterChange('resource', event.target.value)} />
          </Field>
          <Field label="Outcome">
            <SelectInput value={filters.outcome} onChange={(event) => onFilterChange('outcome', event.target.value)}>
              <option value="">All outcomes</option>
              <option value="SUCCESS">SUCCESS</option>
              <option value="FAILURE">FAILURE</option>
            </SelectInput>
          </Field>
          <Field label="From (UTC/local input)">
            <TextInput type="datetime-local" value={filters.fromAt} onChange={(event) => onFilterChange('fromAt', event.target.value)} />
          </Field>
          <Field label="To (UTC/local input)">
            <TextInput type="datetime-local" value={filters.toAt} onChange={(event) => onFilterChange('toAt', event.target.value)} />
          </Field>
          <Field label="Page size">
            <SelectInput
              value={String(size)}
              onChange={(event) => {
                setSize(Number(event.target.value))
                setPage(0)
              }}
            >
              {pageSizes.map((value) => (
                <option key={value} value={value}>
                  {value}
                </option>
              ))}
            </SelectInput>
          </Field>
        </div>

        <div className="mt-4 flex flex-wrap gap-2">
          <Button variant="secondary" onClick={() => onExport('csv')} disabled={isExporting !== null}>
            {isExporting === 'csv' ? 'Exporting CSV...' : 'Export CSV'}
          </Button>
          <Button variant="secondary" onClick={() => onExport('json')} disabled={isExporting !== null}>
            {isExporting === 'json' ? 'Exporting JSON...' : 'Export JSON'}
          </Button>
          <Button
            variant="ghost"
            onClick={() => {
              setFilters(defaultFilters())
              setPage(0)
            }}
          >
            Reset filters
          </Button>
        </div>
      </div>

      <DataTable
        data={auditQuery.data.items}
        columns={[
          { key: 'occurredAt', header: 'Date/Time', render: (value) => formatDate(String(value)) },
          { key: 'actor', header: 'Actor' },
          { key: 'action', header: 'Action' },
          { key: 'resource', header: 'Resource' },
          { key: 'outcome', header: 'Outcome' },
          {
            key: 'details',
            header: 'Details',
            render: (value) => <span className="line-clamp-2 text-xs text-text-muted">{value ? String(value) : '—'}</span>,
          },
        ]}
      />

      <div className="flex flex-wrap items-center justify-between gap-2 rounded-lg border border-surface-border bg-bg-elevated p-3 text-sm text-text-secondary">
        <p>
          Page {auditQuery.data.page + 1} of {Math.max(auditQuery.data.totalPages, 1)} · {auditQuery.data.totalItems} events
        </p>
        <div className="flex gap-2">
          <Button variant="secondary" disabled={!auditQuery.data.hasPrevious} onClick={() => setPage((prev) => Math.max(prev - 1, 0))}>
            Previous
          </Button>
          <Button variant="secondary" disabled={!auditQuery.data.hasNext} onClick={() => setPage((prev) => prev + 1)}>
            Next
          </Button>
        </div>
      </div>
    </section>
  )
}
