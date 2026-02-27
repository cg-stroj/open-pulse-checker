import { useState } from 'react'
import { EmptyState } from '../components/states/EmptyState'
import { ErrorState } from '../components/states/ErrorState'
import { LoadingState } from '../components/states/LoadingState'
import { notify } from '../components/feedback/toast'
import { Badge } from '../components/ui/Badge'
import { Button } from '../components/ui/Button'
import { Field, SelectInput, TextAreaInput, TextInput } from '../components/ui/FormControls'
import { Modal } from '../components/ui/Modal'
import { DataTable } from '../components/ui/Table'
import { useMonitorsQuery } from '../lib/api/monitors'
import type { Monitor } from '../types/monitor'

const fallbackData: Monitor[] = [
  {
    id: 'placeholder-1',
    name: 'API Gateway',
    target: 'https://api.example.com/health',
    enabled: true,
    intervalSeconds: 60,
    createdAt: new Date().toISOString(),
  },
]

export function DashboardPage() {
  const [modalOpen, setModalOpen] = useState(false)
  const monitorsQuery = useMonitorsQuery()

  return (
    <section className="space-y-6">
      <div className="flex items-center justify-between">
        <div>
          <h2 className="text-2xl font-semibold">Frontend Foundation</h2>
          <p className="text-sm text-text-secondary">Reusable primitives and architecture baseline for Open Pulse Checker.</p>
        </div>
        <div className="flex items-center gap-2">
          <Badge tone="warning">Foundation Mode</Badge>
          <Button onClick={() => notify.success('Toast system ready')}>Toast test</Button>
        </div>
      </div>

      <div className="grid gap-4 lg:grid-cols-2">
        <div className="space-y-4 rounded-lg border border-surface-border bg-bg-elevated p-4">
          <h3 className="font-medium">Form controls</h3>
          <Field label="Monitor name">
            <TextInput placeholder="Payments API" />
          </Field>
          <Field label="Environment">
            <SelectInput defaultValue="prod">
              <option value="prod">Production</option>
              <option value="staging">Staging</option>
            </SelectInput>
          </Field>
          <Field label="Notes">
            <TextAreaInput rows={3} placeholder="Placeholder foundation form" />
          </Field>
          <Button variant="secondary" onClick={() => setModalOpen(true)}>
            Open modal
          </Button>
        </div>

        <div className="rounded-lg border border-surface-border bg-bg-elevated p-4">
          <h3 className="mb-4 font-medium">State primitives</h3>
          <div className="space-y-3">
            <LoadingState title="Loading UI primitive" description="Skeleton phase for feature modules." />
            <EmptyState title="No monitor module wired yet" description="Feature slices will be attached incrementally." />
            <ErrorState title="Example error" description="Global error surfaces are now standardized." />
          </div>
        </div>
      </div>

      <div className="space-y-3">
        <h3 className="font-medium">Reusable data table</h3>
        <DataTable
          data={monitorsQuery.data?.length ? monitorsQuery.data : fallbackData}
          columns={[
            { key: 'name', header: 'Name' },
            { key: 'target', header: 'Target' },
            {
              key: 'enabled',
              header: 'Status',
              render: (value) => (value ? <Badge tone="success">Enabled</Badge> : <Badge tone="critical">Disabled</Badge>),
            },
            { key: 'intervalSeconds', header: 'Interval (s)' },
          ]}
        />
      </div>

      <Modal open={modalOpen} title="Modal primitive" onClose={() => setModalOpen(false)}>
        <p className="text-sm text-text-secondary">Use this modal wrapper for future create/edit flows.</p>
      </Modal>
    </section>
  )
}
