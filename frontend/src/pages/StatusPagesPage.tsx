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
  useCreateStatusPageMutation,
  usePublicStatusPageQuery,
  useStatusPageV2ConfigQuery,
  useStatusPagesQuery,
  useUpdateStatusPageMutation,
  useUpsertStatusPageV2ConfigMutation,
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

type StatusPage = NonNullable<ReturnType<typeof useStatusPagesQuery>['data']>[number]
type V2Config = NonNullable<ReturnType<typeof useStatusPageV2ConfigQuery>['data']>
type Monitor = NonNullable<ReturnType<typeof useMonitorsQuery>['data']>[number]

function toMaintenanceDraft(config: V2Config) {
  return config.maintenanceAnnouncements.map((m) => ({
    id: m.id,
    title: m.title,
    message: m.message,
    publishAt: m.publishAt.slice(0, 16),
    startsAt: m.startsAt ? m.startsAt.slice(0, 16) : '',
    endsAt: m.endsAt ? m.endsAt.slice(0, 16) : '',
    isPublic: m.isPublic,
  }))
}

function BrandingEditor({
  selectedPage,
  isSaving,
  onSave,
}: {
  selectedPage: StatusPage
  isSaving: boolean
  onSave: (payload: { brandName: string; brandTheme: string; brandLogoUrl: string; brandCustomHeader: string; brandCustomFooter: string }) => Promise<void>
}) {
  const [brandingName, setBrandingName] = useState(selectedPage.branding?.brandName ?? '')
  const [brandingTheme, setBrandingTheme] = useState(selectedPage.branding?.brandTheme ?? '')
  const [brandingLogoUrl, setBrandingLogoUrl] = useState(selectedPage.branding?.brandLogoUrl ?? '')
  const [brandingHeader, setBrandingHeader] = useState(selectedPage.branding?.brandCustomHeader ?? '')
  const [brandingFooter, setBrandingFooter] = useState(selectedPage.branding?.brandCustomFooter ?? '')

  return (
    <div className="rounded-md border border-surface-border p-3 space-y-2">
      <p className="text-sm font-medium">Branding</p>
      <Field label="Brand name"><TextInput value={brandingName} onChange={(e) => setBrandingName(e.target.value)} /></Field>
      <Field label="Theme token"><TextInput value={brandingTheme} onChange={(e) => setBrandingTheme(e.target.value)} placeholder="light|dark|custom" /></Field>
      <Field label="Logo URL"><TextInput value={brandingLogoUrl} onChange={(e) => setBrandingLogoUrl(e.target.value)} /></Field>
      <Field label="Header text"><TextInput value={brandingHeader} onChange={(e) => setBrandingHeader(e.target.value)} /></Field>
      <Field label="Footer text"><TextInput value={brandingFooter} onChange={(e) => setBrandingFooter(e.target.value)} /></Field>
      <Button
        variant="secondary"
        disabled={isSaving}
        onClick={() => onSave({
          brandName: brandingName,
          brandTheme: brandingTheme,
          brandLogoUrl: brandingLogoUrl,
          brandCustomHeader: brandingHeader,
          brandCustomFooter: brandingFooter,
        })}
      >
        Save branding
      </Button>
    </div>
  )
}

function V2ConfigEditor({
  initialConfig,
  monitorCatalog,
  isSaving,
  onSave,
}: {
  initialConfig: V2Config | null
  monitorCatalog: Monitor[]
  isSaving: boolean
  onSave: (payload: {
    componentGroups: Array<{ id: string; name: string; displayOrder: number }>
    monitorBindings: Array<{ monitorId: string; displayOrder: number; componentGroupId: string | null }>
    maintenanceAnnouncements: Array<{ id: string; title: string; message: string; publishAt: string; startsAt: string | null; endsAt: string | null; isPublic: boolean }>
  }) => Promise<void>
}) {
  const [groupsDraft, setGroupsDraft] = useState<Array<{ id: string; name: string }>>(() => initialConfig?.componentGroups.map((g) => ({ id: g.id, name: g.name })) ?? [])
  const [bindingsDraft, setBindingsDraft] = useState<Array<{ monitorId: string; componentGroupId: string | null }>>(() =>
    initialConfig?.monitorBindings.map((m) => ({ monitorId: m.monitorId, componentGroupId: m.componentGroupId })) ?? [],
  )
  const [maintenanceDraft, setMaintenanceDraft] = useState<Array<{ id: string; title: string; message: string; publishAt: string; startsAt: string; endsAt: string; isPublic: boolean }>>(
    () => (initialConfig ? toMaintenanceDraft(initialConfig) : []),
  )

  return (
    <>
      <div className="rounded-md border border-surface-border p-3 space-y-3">
        <div className="flex items-center justify-between"><p className="text-sm font-medium">Component groups + monitor binding</p><Button variant="secondary" disabled={isSaving} onClick={() => onSave({
          componentGroups: groupsDraft.map((group, index) => ({ id: group.id, name: group.name, displayOrder: index })),
          monitorBindings: bindingsDraft.map((binding, index) => ({ monitorId: binding.monitorId, displayOrder: index, componentGroupId: binding.componentGroupId })),
          maintenanceAnnouncements: maintenanceDraft.map((m) => ({
            id: m.id,
            title: m.title,
            message: m.message,
            publishAt: new Date(m.publishAt).toISOString(),
            startsAt: m.startsAt ? new Date(m.startsAt).toISOString() : null,
            endsAt: m.endsAt ? new Date(m.endsAt).toISOString() : null,
            isPublic: m.isPublic,
          })),
        })}>Save v2 config</Button></div>
        <Button variant="ghost" onClick={() => setGroupsDraft((prev) => [...prev, { id: crypto.randomUUID(), name: `Group ${prev.length + 1}` }])}>+ Add group</Button>
        {groupsDraft.map((group, idx) => (
          <div key={group.id} className="flex gap-2"><TextInput value={group.name} onChange={(e) => setGroupsDraft((prev) => prev.map((g) => g.id === group.id ? { ...g, name: e.target.value } : g))} /><Button variant="ghost" onClick={() => setGroupsDraft((prev) => prev.filter((g) => g.id !== group.id))}>Remove</Button><span className="text-xs">#{idx + 1}</span></div>
        ))}
        <p className="text-xs text-text-muted">Bind monitors to groups:</p>
        {monitorCatalog.map((monitor) => {
          const binding = bindingsDraft.find((it) => it.monitorId === monitor.id)
          return (
            <div key={monitor.id} className="grid gap-2 md:grid-cols-[1fr_1fr] md:items-center">
              <span className="text-sm">{monitor.name}</span>
              <select className="rounded-md border border-surface-border bg-bg-panel px-3 py-2 text-sm" value={binding?.componentGroupId ?? ''} onChange={(e) => setBindingsDraft((prev) => {
                const next = prev.filter((it) => it.monitorId !== monitor.id)
                next.push({ monitorId: monitor.id, componentGroupId: e.target.value || null })
                return next
              })}>
                <option value="">Ungrouped</option>
                {groupsDraft.map((group) => <option key={group.id} value={group.id}>{group.name}</option>)}
              </select>
            </div>
          )
        })}
      </div>

      <div className="rounded-md border border-surface-border p-3 space-y-2">
        <div className="flex items-center justify-between"><p className="text-sm font-medium">Maintenance announcements</p><Button variant="ghost" onClick={() => setMaintenanceDraft((prev) => [...prev, { id: crypto.randomUUID(), title: '', message: '', publishAt: new Date().toISOString().slice(0, 16), startsAt: '', endsAt: '', isPublic: true }])}>+ Add announcement</Button></div>
        {maintenanceDraft.length === 0 ? <p className="text-sm text-text-secondary">No announcements configured.</p> : maintenanceDraft.map((item) => (
          <div key={item.id} className="rounded bg-bg-panel p-2 space-y-2">
            <TextInput value={item.title} onChange={(e) => setMaintenanceDraft((prev) => prev.map((m) => m.id === item.id ? { ...m, title: e.target.value } : m))} placeholder="Title" />
            <TextInput value={item.message} onChange={(e) => setMaintenanceDraft((prev) => prev.map((m) => m.id === item.id ? { ...m, message: e.target.value } : m))} placeholder="Message" />
            <div className="grid gap-2 md:grid-cols-3">
              <input type="datetime-local" value={item.publishAt} onChange={(e) => setMaintenanceDraft((prev) => prev.map((m) => m.id === item.id ? { ...m, publishAt: e.target.value } : m))} />
              <input type="datetime-local" value={item.startsAt} onChange={(e) => setMaintenanceDraft((prev) => prev.map((m) => m.id === item.id ? { ...m, startsAt: e.target.value } : m))} />
              <input type="datetime-local" value={item.endsAt} onChange={(e) => setMaintenanceDraft((prev) => prev.map((m) => m.id === item.id ? { ...m, endsAt: e.target.value } : m))} />
            </div>
            <label className="text-sm"><input type="checkbox" checked={item.isPublic} onChange={(e) => setMaintenanceDraft((prev) => prev.map((m) => m.id === item.id ? { ...m, isPublic: e.target.checked } : m))} /> Public</label>
          </div>
        ))}
      </div>
    </>
  )
}

function StatusPagePreview({ selectedPage }: { selectedPage: StatusPage | null }) {
  const [previewSlugInput, setPreviewSlugInput] = useState(selectedPage?.slug ?? '')
  const effectivePreviewSlug = previewSlugInput.trim() || selectedPage?.slug || null
  const publicPreviewQuery = usePublicStatusPageQuery(effectivePreviewSlug)

  return (
    <aside className="space-y-3 rounded-lg border border-surface-border bg-bg-elevated p-4">
      <Field label="Preview slug"><TextInput value={previewSlugInput} onChange={(e) => setPreviewSlugInput(e.target.value)} /></Field>
      {!effectivePreviewSlug ? (
        <EmptyState title="Enter a slug" description="Provide a slug to fetch public status page preview." />
      ) : publicPreviewQuery.isLoading ? (
        <LoadingState title="Loading preview" description="Fetching public status page payload." />
      ) : publicPreviewQuery.isError ? (
        <ErrorState title="Public preview unavailable" description="Slug missing, non-public, or not found." />
      ) : publicPreviewQuery.data ? (
        <div className="space-y-3 rounded-md border border-surface-border p-3">
          <div className="flex items-center justify-between"><h3 className="font-semibold">{publicPreviewQuery.data.page.branding?.brandName || publicPreviewQuery.data.page.name}</h3><Badge tone={statusTone(publicPreviewQuery.data.overallStatus)}>{publicPreviewQuery.data.overallStatus}</Badge></div>
          {publicPreviewQuery.data.page.branding?.brandCustomHeader ? <p className="text-xs text-text-muted">{publicPreviewQuery.data.page.branding.brandCustomHeader}</p> : null}
          {publicPreviewQuery.data.maintenanceAnnouncements.length > 0 && (
            <div className="space-y-2"><p className="text-sm font-medium">Active maintenance</p>{publicPreviewQuery.data.maintenanceAnnouncements.map((m) => <div key={m.id} className="rounded bg-bg-panel p-2 text-xs"><p className="font-semibold">{m.title}</p><p>{m.message}</p></div>)}</div>
          )}
          <div className="space-y-2"><p className="text-sm font-medium">Services</p>{publicPreviewQuery.data.componentGroups.map((group) => (
            <div key={group.id}><p className="text-xs font-semibold text-text-muted">{group.name}</p><ul className="space-y-2">{publicPreviewQuery.data.monitors.filter((m) => m.componentGroupId === group.id).map((monitor) => <li key={monitor.monitorId} className="rounded-md bg-bg-panel p-2"><div className="flex justify-between"><span>{monitor.monitorName}</span><Badge tone={statusTone(monitor.currentStatus)}>{monitor.currentStatus}</Badge></div><p className="text-xs">Last check: {formatDateTime(monitor.checkedAt)}</p></li>)}</ul></div>
          ))}
          <ul className="space-y-2">{publicPreviewQuery.data.monitors.filter((m) => !m.componentGroupId).map((monitor) => <li key={monitor.monitorId} className="rounded-md bg-bg-panel p-2"><div className="flex justify-between"><span>{monitor.monitorName}</span><Badge tone={statusTone(monitor.currentStatus)}>{monitor.currentStatus}</Badge></div></li>)}</ul>
          </div>
          {publicPreviewQuery.data.page.branding?.brandCustomFooter ? <p className="text-xs text-text-muted">{publicPreviewQuery.data.page.branding.brandCustomFooter}</p> : null}
        </div>
      ) : null}
    </aside>
  )
}

export function StatusPagesPage() {
  const queryClient = useQueryClient()
  const statusPagesQuery = useStatusPagesQuery()
  const monitorsQuery = useMonitorsQuery()
  const createPageMutation = useCreateStatusPageMutation()
  const updatePageMutation = useUpdateStatusPageMutation()
  const upsertConfigMutation = useUpsertStatusPageV2ConfigMutation()

  const [selectedPageId, setSelectedPageId] = useState<string | null>(null)
  const [name, setName] = useState('')
  const [slug, setSlug] = useState('')
  const [isPublic, setIsPublic] = useState(true)

  const selectedPage = useMemo(() => {
    const pages = statusPagesQuery.data ?? []
    if (pages.length === 0) return null
    if (!selectedPageId) return pages[0]
    return pages.find((page) => page.id === selectedPageId) ?? pages[0]
  }, [selectedPageId, statusPagesQuery.data])

  const configQuery = useStatusPageV2ConfigQuery(selectedPage?.id ?? null)
  const monitorCatalog = monitorsQuery.data ?? []

  async function refreshStatusPages() {
    await queryClient.invalidateQueries({ queryKey: ['status-pages'] })
  }

  async function createPage() {
    const payload = { name: name.trim(), slug: slug.trim(), isPublic }
    if (!payload.name) return notify.error('Page name is required.')
    if (!payload.slug || !slugRegex.test(payload.slug)) return notify.error('Slug must use lowercase letters, numbers and hyphens only.')
    try {
      const created = await createPageMutation.mutateAsync(payload)
      setName('')
      setSlug('')
      await refreshStatusPages()
      setSelectedPageId(created.id)
      notify.success('Status page created.')
    } catch (error) {
      notify.error(getStatusPageApiErrorMessage(error, 'Failed to create status page.'))
    }
  }

  async function saveBranding(payload: { brandName: string; brandTheme: string; brandLogoUrl: string; brandCustomHeader: string; brandCustomFooter: string }) {
    if (!selectedPage) return
    try {
      await updatePageMutation.mutateAsync({
        pageId: selectedPage.id,
        data: payload,
      })
      await refreshStatusPages()
      notify.success('Branding saved.')
    } catch (error) {
      notify.error(getStatusPageApiErrorMessage(error, 'Failed to save branding.'))
    }
  }

  async function saveV2Config(payload: {
    componentGroups: Array<{ id: string; name: string; displayOrder: number }>
    monitorBindings: Array<{ monitorId: string; displayOrder: number; componentGroupId: string | null }>
    maintenanceAnnouncements: Array<{ id: string; title: string; message: string; publishAt: string; startsAt: string | null; endsAt: string | null; isPublic: boolean }>
  }) {
    if (!selectedPage) return
    try {
      await upsertConfigMutation.mutateAsync({
        pageId: selectedPage.id,
        data: payload,
      })
      await queryClient.invalidateQueries({ queryKey: ['status-pages', selectedPage.id, 'config'] })
      await queryClient.invalidateQueries({ queryKey: ['status-pages', 'public', selectedPage.slug] })
      notify.success('Status Page v2 config saved.')
    } catch (error) {
      notify.error(getStatusPageApiErrorMessage(error, 'Failed to save v2 config.'))
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
          <p className="text-sm text-text-secondary">Status Page v2: groups, maintenance announcements, and branding.</p>
        </div>
        <Badge tone="warning">Admin + Public view</Badge>
      </header>

      <div className="grid gap-4 xl:grid-cols-[1.3fr_1fr]">
        <div className="space-y-4 rounded-lg border border-surface-border bg-bg-elevated p-4">
          <div className="rounded-md border border-surface-border p-3">
            <p className="mb-3 text-sm font-medium text-text-primary">Create page</p>
            <div className="grid gap-3 md:grid-cols-[1fr_220px_auto_auto] md:items-end">
              <Field label="Page name"><TextInput value={name} maxLength={120} onChange={(e) => setName(e.target.value)} /></Field>
              <Field label="Slug"><TextInput value={slug} maxLength={80} onChange={(e) => setSlug(e.target.value)} /></Field>
              <label className="flex items-center gap-2 text-sm text-text-secondary"><input type="checkbox" checked={isPublic} onChange={(e) => setIsPublic(e.target.checked)} />Public</label>
              <Button disabled={createPageMutation.isPending} onClick={createPage}>Create page</Button>
            </div>
          </div>

          {selectedPage ? (
            <>
              <div className="grid gap-2">
                <p className="text-sm font-medium">Existing pages</p>
                <div className="grid gap-2 max-h-36 overflow-auto pr-1">
                  {(statusPagesQuery.data ?? []).map((page) => (
                    <button key={page.id} type="button" onClick={() => setSelectedPageId(page.id)} className={`rounded-md border p-2 text-left ${selectedPage.id === page.id ? 'border-accent' : 'border-surface-border'}`}>
                      <div className="flex items-center justify-between"><span>{page.name}</span><Badge tone={page.isPublic ? 'success' : 'neutral'}>{page.isPublic ? 'Public' : 'Private'}</Badge></div>
                      <p className="text-xs text-text-muted">/{page.slug}</p>
                    </button>
                  ))}
                </div>
              </div>

              <BrandingEditor key={selectedPage.id} selectedPage={selectedPage} isSaving={updatePageMutation.isPending} onSave={saveBranding} />

              <V2ConfigEditor
                key={`${selectedPage.id}:${configQuery.dataUpdatedAt}`}
                initialConfig={configQuery.data ?? null}
                monitorCatalog={monitorCatalog}
                isSaving={upsertConfigMutation.isPending}
                onSave={saveV2Config}
              />
            </>
          ) : (
            <EmptyState title="No status pages yet" description="Create the first status page to start building public health views." />
          )}
        </div>

        <StatusPagePreview key={selectedPage?.id ?? 'none'} selectedPage={selectedPage} />
      </div>
    </section>
  )
}
