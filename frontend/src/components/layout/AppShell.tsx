import { useIsFetching } from '@tanstack/react-query'
import { NavLink, Outlet } from 'react-router-dom'
import { appConfig } from '../../lib/config/app'
import { cn } from '../../lib/utils/cn'

const navItems = [
  { to: '/dashboard', label: 'Dashboard' },
  { to: '/monitors', label: 'Monitors' },
  { to: '/incidents', label: 'Incidents' },
  { to: '/audit-explorer', label: 'Audit Explorer' },
  { to: '/maintenance-windows', label: 'Maintenance Windows' },
  { to: '/notification-policies', label: 'Notification Policies' },
  { to: '/status-pages', label: 'Status Pages' },
  { to: '/settings', label: 'Settings' },
]

export function AppShell() {
  const isFetching = useIsFetching()

  return (
    <div className="min-h-screen bg-bg-base text-text-primary">
      <div className={cn('fixed left-0 right-0 top-0 z-50 h-1 bg-accent transition-opacity', isFetching > 0 ? 'opacity-100' : 'opacity-0')} />
      <div className="grid min-h-screen grid-cols-[240px_1fr]">
        <aside className="border-r border-surface-border bg-bg-elevated p-4">
          <h1 className="mb-8 text-lg font-semibold">Open Pulse Checker</h1>
          <nav className="space-y-2">
            {navItems.map((item) => (
              <NavLink
                key={item.to}
                to={item.to}
                className={({ isActive }) =>
                  cn(
                    'block rounded-md px-3 py-2 text-sm transition',
                    isActive ? 'bg-accent text-bg-base' : 'text-text-secondary hover:bg-bg-panel hover:text-text-primary',
                  )
                }
              >
                {item.label}
              </NavLink>
            ))}
          </nav>
        </aside>

        <div className="flex min-h-screen flex-col">
          <header className="flex h-16 items-center justify-between border-b border-surface-border bg-bg-elevated px-6">
            <p className="text-sm text-text-muted">Foundation shell · React + TS + Vite</p>
            <p className="rounded-full bg-bg-panel px-3 py-1 text-xs text-text-secondary">API: {appConfig.apiBaseUrl}</p>
          </header>
          <main className="flex-1 bg-bg-base p-6">
            <Outlet />
          </main>
        </div>
      </div>
    </div>
  )
}
