import { Navigate, Route, Routes } from 'react-router-dom'
import { AppShell } from '../components/layout/AppShell'
import { AuditExplorerPage } from '../pages/AuditExplorerPage'
import { DashboardPage } from '../pages/DashboardPage'
import { IncidentsPage } from '../pages/IncidentsPage'
import { LoginPage } from '../pages/LoginPage'
import { MaintenanceWindowsPage } from '../pages/MaintenanceWindowsPage'
import { MonitorsPage } from '../pages/MonitorsPage'
import { NotFoundPage } from '../pages/NotFoundPage'
import { NotificationPoliciesPage } from '../pages/NotificationPoliciesPage'
import { SettingsPage } from '../pages/SettingsPage'
import { SetupPage } from '../pages/SetupPage'
import { StatusPagesPage } from '../pages/StatusPagesPage'
import { UnauthorizedPage } from '../pages/UnauthorizedPage'
import { AppErrorBoundary } from './error-boundary'
import { LoginRoute, ProtectedRoute } from './guards'

export function AppRouter() {
  return (
    <AppErrorBoundary>
      <Routes>
        <Route element={<LoginRoute />}>
          <Route path="/login" element={<LoginPage />} />
          <Route path="/setup" element={<SetupPage />} />
        </Route>

        <Route element={<ProtectedRoute />}>
          <Route element={<AppShell />}>
            <Route index element={<Navigate to="/dashboard" replace />} />
            <Route path="/dashboard" element={<DashboardPage />} />
            <Route path="/monitors" element={<MonitorsPage />} />
            <Route path="/incidents" element={<IncidentsPage />} />
            <Route path="/audit-explorer" element={<AuditExplorerPage />} />
            <Route path="/maintenance-windows" element={<MaintenanceWindowsPage />} />
            <Route path="/notification-policies" element={<NotificationPoliciesPage />} />
            <Route path="/status-pages" element={<StatusPagesPage />} />
            <Route path="/settings" element={<SettingsPage />} />
            <Route path="/unauthorized" element={<UnauthorizedPage />} />
          </Route>
        </Route>

        <Route path="*" element={<NotFoundPage />} />
      </Routes>
    </AppErrorBoundary>
  )
}
