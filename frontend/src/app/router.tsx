import { Navigate, Route, Routes } from 'react-router-dom'
import { AppShell } from '../components/layout/AppShell'
import { DashboardPage } from '../pages/DashboardPage'
import { IncidentsPage } from '../pages/IncidentsPage'
import { MaintenanceWindowsPage } from '../pages/MaintenanceWindowsPage'
import { MonitorsPage } from '../pages/MonitorsPage'
import { NotFoundPage } from '../pages/NotFoundPage'
import { SettingsPage } from '../pages/SettingsPage'
import { StatusPagesPage } from '../pages/StatusPagesPage'
import { AppErrorBoundary } from './error-boundary'

export function AppRouter() {
  return (
    <AppErrorBoundary>
      <Routes>
        <Route element={<AppShell />}>
          <Route index element={<Navigate to="/dashboard" replace />} />
          <Route path="/dashboard" element={<DashboardPage />} />
          <Route path="/monitors" element={<MonitorsPage />} />
          <Route path="/incidents" element={<IncidentsPage />} />
          <Route path="/maintenance-windows" element={<MaintenanceWindowsPage />} />
          <Route path="/status-pages" element={<StatusPagesPage />} />
          <Route path="/settings" element={<SettingsPage />} />
        </Route>
        <Route path="*" element={<NotFoundPage />} />
      </Routes>
    </AppErrorBoundary>
  )
}
