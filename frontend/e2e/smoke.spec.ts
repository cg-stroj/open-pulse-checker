import { expect, test, type Page } from '@playwright/test'

const AUTH_STORAGE_KEY = 'opc.admin.auth'

interface SetupMockState {
  setupRequired: boolean
  setupLocked: boolean
  setupToken: string | null
}

interface SetupMockOptions {
  forceExpiredToken?: boolean
  forceConflictOnCreate?: boolean
  forceDeleteConflict?: boolean
}

async function mockApi(
  page: Page,
  setupState: SetupMockState = { setupRequired: false, setupLocked: true, setupToken: null },
  options: SetupMockOptions = {},
) {
  let brandName: string | null = null
  let brandTheme: string | null = null
  let brandLogoUrl: string | null = null
  let brandCustomHeader: string | null = null
  let brandCustomFooter: string | null = null
  let monitors = [
    {
      id: 'm1',
      name: 'API Gateway',
      type: 'HTTP',
      targetUrl: 'https://example.com/health',
      enabled: true,
      intervalSec: 60,
      timeoutMs: 1200,
      httpMethod: 'GET',
      expectedResponseKeyword: 'healthy',
      emailAlertOnDown: true,
      emailAlertOnRecovery: true,
      lastCheckAt: new Date().toISOString(),
      lastCheckStatus: 'DOWN',
      lastStatusCode: 503,
      lastLatencyMs: 248,
      createdAt: new Date().toISOString(),
      updatedAt: new Date().toISOString(),
    },
    {
      id: 'm2',
      name: 'Core TCP',
      type: 'TCP',
      targetUrl: 'db.internal:5432',
      enabled: true,
      intervalSec: 60,
      timeoutMs: 1200,
      httpMethod: null,
      expectedResponseKeyword: null,
      emailAlertOnDown: true,
      emailAlertOnRecovery: true,
      lastCheckAt: new Date().toISOString(),
      lastCheckStatus: 'UP',
      lastStatusCode: null,
      lastLatencyMs: 19,
      createdAt: new Date().toISOString(),
      updatedAt: new Date().toISOString(),
    },
  ]

  const requiredMetricNames = [
    'openpulse.scheduler.lock.acquire.success',
    'openpulse.scheduler.lock.acquire.fail',
    'openpulse.scheduler.lock.acquire.steal',
    'openpulse.scheduler.lock.renew.fail',
    'openpulse.scheduler.execution.skip.lock',
    'openpulse.scheduler.execution.skip.local_inflight',
    'openpulse.alerts.dlq.backlog',
    'openpulse.alerts.dlq.oldest.age.seconds',
    'openpulse.alerts.dispatch.attempts',
    'openpulse.alerts.dispatch.latency',
    'openpulse.alerts.delivery.delay',
  ]

  await page.route('**/actuator/metrics', async (route) => {
    return route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({ names: requiredMetricNames }),
    })
  })

  await page.route('**/actuator/metrics/**', async (route) => {
    const url = new URL(route.request().url())
    const metricName = decodeURIComponent(url.pathname.split('/actuator/metrics/')[1] ?? '')
    const tags = url.searchParams.getAll('tag')
    const json = (body: unknown, status = 200) => route.fulfill({ status, contentType: 'application/json', body: JSON.stringify(body) })

    const responseByMetric: Record<string, { measurements: Array<{ statistic: string; value: number }> }> = {
      'openpulse.scheduler.lock.acquire.success': { measurements: [{ statistic: 'COUNT', value: 1200 }] },
      'openpulse.scheduler.lock.acquire.fail': { measurements: [{ statistic: 'COUNT', value: 120 }] },
      'openpulse.scheduler.lock.acquire.steal': { measurements: [{ statistic: 'COUNT', value: 8 }] },
      'openpulse.scheduler.lock.renew.fail': { measurements: [{ statistic: 'COUNT', value: 4 }] },
      'openpulse.scheduler.execution.skip.lock': { measurements: [{ statistic: 'COUNT', value: 50 }] },
      'openpulse.scheduler.execution.skip.local_inflight': { measurements: [{ statistic: 'COUNT', value: 25 }] },
      'openpulse.alerts.dlq.backlog': { measurements: [{ statistic: 'VALUE', value: 2 }] },
      'openpulse.alerts.dlq.oldest.age.seconds': { measurements: [{ statistic: 'VALUE', value: 35 }] },
      'openpulse.alerts.dispatch.latency': {
        measurements: [
          { statistic: 'COUNT', value: 350 },
          { statistic: 'TOTAL_TIME', value: 490 },
          { statistic: 'MAX', value: 4.2 },
        ],
      },
      'openpulse.alerts.delivery.delay': {
        measurements: [
          { statistic: 'COUNT', value: 350 },
          { statistic: 'TOTAL_TIME', value: 700 },
          { statistic: 'MAX', value: 12.5 },
        ],
      },
    }

    if (metricName === 'openpulse.alerts.dispatch.attempts') {
      if (tags.includes('outcome:success')) {
        return json({ name: metricName, measurements: [{ statistic: 'COUNT', value: 330 }] })
      }
      if (tags.includes('outcome:failed')) {
        return json({ name: metricName, measurements: [{ statistic: 'COUNT', value: 20 }] })
      }
    }

    const metricResponse = responseByMetric[metricName]
    if (metricResponse) {
      return json({ name: metricName, ...metricResponse })
    }

    return json({ name: metricName, measurements: [] })
  })

  await page.route('**/api/v1/**', async (route) => {
    const url = new URL(route.request().url())
    const { pathname } = url
    const method = route.request().method()
    const authHeader = route.request().headers()['authorization']

    const json = (body: unknown, status = 200) => route.fulfill({ status, contentType: 'application/json', body: JSON.stringify(body) })

    if (pathname === '/api/v1/setup/status' && method === 'GET') {
      return json({
        setupRequired: setupState.setupRequired,
        setupLocked: setupState.setupLocked,
        setupToken: setupState.setupToken,
        setupTokenExpiresAt: setupState.setupToken ? new Date(Date.now() + 600_000).toISOString() : null,
      })
    }

    if (pathname === '/api/v1/setup/first-admin' && method === 'POST') {
      const payload = JSON.parse(route.request().postData() || '{}')

      if (options.forceConflictOnCreate || setupState.setupLocked || !setupState.setupRequired) {
        return json({ error: 'Setup is already completed' }, 409)
      }

      if (!payload.password || payload.password.length < 12) {
        return json({ error: 'password size must be between 12 and 200' }, 400)
      }

      if (options.forceExpiredToken || !setupState.setupToken || payload.setupToken !== setupState.setupToken) {
        return json({ error: 'Invalid or expired setup token' }, 400)
      }

      setupState.setupRequired = false
      setupState.setupLocked = true
      setupState.setupToken = null
      return json({ username: payload.username }, 201)
    }

    const requiresAuth = pathname.startsWith('/api/v1/admin') || pathname === '/api/v1/monitors' || pathname === '/api/v1/status-pages'
    if (requiresAuth && !authHeader) {
      return json({ error: 'Unauthorized' }, 401)
    }

    if (pathname.endsWith('/monitors') && method === 'GET') {
      return json(monitors)
    }

    if (/\/api\/v1\/monitors\/[^/]+$/.test(pathname) && method === 'GET') {
      const id = pathname.split('/').pop() || ''
      const found = monitors.find((monitor) => monitor.id === id)
      if (!found) return json({ error: 'Monitor not found' }, 404)
      return json(found)
    }

    if (pathname.endsWith('/monitors') && method === 'POST') {
      const payload = JSON.parse(route.request().postData() || '{}')
      const created = {
        id: `m-${Date.now()}`,
        ...payload,
        lastCheckAt: null,
        lastCheckStatus: null,
        lastStatusCode: null,
        lastLatencyMs: null,
        createdAt: new Date().toISOString(),
        updatedAt: new Date().toISOString(),
      }
      monitors = [created, ...monitors]
      return json(created, 201)
    }

    if (/\/api\/v1\/monitors\/[^/]+$/.test(pathname) && method === 'PUT') {
      const payload = JSON.parse(route.request().postData() || '{}')
      const id = pathname.split('/').pop() || ''
      const current = monitors.find((monitor) => monitor.id === id)
      const updated = {
        id,
        ...payload,
        lastCheckAt: current?.lastCheckAt ?? new Date().toISOString(),
        lastCheckStatus: current?.lastCheckStatus ?? 'UP',
        lastStatusCode: current?.lastStatusCode ?? 200,
        lastLatencyMs: current?.lastLatencyMs ?? 45,
        createdAt: current?.createdAt ?? new Date().toISOString(),
        updatedAt: new Date().toISOString(),
      }
      monitors = monitors.map((monitor) => (monitor.id === id ? updated : monitor))
      return json(updated)
    }

    if (/\/api\/v1\/monitors\/[^/]+\/enabled$/.test(pathname) && method === 'PATCH') {
      const payload = JSON.parse(route.request().postData() || '{}')
      const id = pathname.split('/')[4]
      const current = monitors.find((monitor) => monitor.id === id)
      if (!current) return json({ error: 'Monitor not found' }, 404)
      const updated = { ...current, enabled: payload.enabled, updatedAt: new Date().toISOString() }
      monitors = monitors.map((monitor) => (monitor.id === id ? updated : monitor))
      return json(updated)
    }

    if (/\/api\/v1\/monitors\/[^/]+\/run-check$/.test(pathname) && method === 'POST') {
      return json({
        id: 'c-1',
        monitorId: pathname.split('/')[4],
        status: 'UP',
        statusCode: 200,
        latencyMs: 40,
        checkedAt: new Date().toISOString(),
        error: null,
      })
    }

    if (/\/api\/v1\/monitors\/[^/]+$/.test(pathname) && method === 'DELETE') {
      const id = pathname.split('/').pop() || ''
      if (options.forceDeleteConflict) {
        return json({ error: 'Monitor deletion blocked: historical references exist (checkResults=1, incidents=1).' }, 409)
      }
      monitors = monitors.filter((monitor) => monitor.id !== id)
      return route.fulfill({ status: 204 })
    }

    if (pathname.endsWith('/admin/incidents') && method === 'GET') {
      return json([
        {
          id: 'inc-1',
          monitorId: 'm1',
          monitorName: 'API Gateway',
          reason: 'HTTP 503 from gateway health endpoint',
          state: 'OPEN',
          openedAt: new Date().toISOString(),
          resolvedAt: null,
        },
        {
          id: 'inc-2',
          monitorId: 'm2',
          monitorName: 'Core TCP',
          reason: 'Recovered after short packet loss',
          state: 'RESOLVED',
          openedAt: new Date(Date.now() - 3_600_000).toISOString(),
          resolvedAt: new Date(Date.now() - 3_540_000).toISOString(),
        },
      ])
    }

    if (pathname.includes('/admin/incidents/') && pathname.endsWith('/events') && method === 'GET') {
      return json([])
    }

    if (pathname.endsWith('/admin/maintenance-windows') && method === 'GET') {
      return json([])
    }

    if (pathname.endsWith('/admin/notification-policies') && method === 'GET') {
      return json([])
    }

    if (pathname.endsWith('/admin/audit-events') && method === 'GET') {
      return json({ items: [], page: 0, totalPages: 1, totalItems: 0, hasNext: false, hasPrevious: false })
    }

    if (/\/api\/v1\/status-pages\/[^/]+\/config$/.test(pathname) && method === 'GET') {
      return json({ componentGroups: [], monitorBindings: [], maintenanceAnnouncements: [] })
    }

    if (/\/api\/v1\/status-pages\/[^/]+\/config$/.test(pathname) && method === 'PUT') {
      return json(JSON.parse(route.request().postData() || '{}'))
    }

    if (/\/api\/v1\/status-pages\/[^/]+$/.test(pathname) && method === 'PUT') {
      const payload = JSON.parse(route.request().postData() || '{}')
      brandName = payload.brandName ?? brandName
      brandTheme = payload.brandTheme ?? brandTheme
      brandLogoUrl = payload.brandLogoUrl ?? brandLogoUrl
      brandCustomHeader = payload.brandCustomHeader ?? brandCustomHeader
      brandCustomFooter = payload.brandCustomFooter ?? brandCustomFooter
      return json({ id: pathname.split('/').pop(), name: 'Main Status', slug: 'main-status', isPublic: true, branding: { brandName, brandTheme, brandLogoUrl, brandCustomHeader, brandCustomFooter }, createdAt: new Date().toISOString(), updatedAt: new Date().toISOString() })
    }

    if (pathname.endsWith('/status-pages') && method === 'GET') {
      return json([{ id: 'sp-1', name: 'Main Status', slug: 'main-status', isPublic: true, branding: { brandName, brandTheme, brandLogoUrl, brandCustomHeader, brandCustomFooter }, createdAt: new Date().toISOString(), updatedAt: new Date().toISOString() }])
    }

    if (pathname.endsWith('/status-pages') && method === 'POST') {
      const payload = JSON.parse(route.request().postData() || '{}')
      return json({ id: 'sp-new', name: payload.name, slug: payload.slug, isPublic: payload.isPublic ?? true, branding: { brandName: null, brandTheme: null, brandLogoUrl: null, brandCustomHeader: null, brandCustomFooter: null }, createdAt: new Date().toISOString(), updatedAt: new Date().toISOString() }, 201)
    }

    if (pathname.includes('/public/status-pages/') && method === 'GET') {
      const slug = pathname.split('/').pop() || 'main-status'
      return json({
        page: { id: 'sp-1', name: 'Main Status', slug, isPublic: true, branding: { brandName: brandName ?? 'Main Status', brandTheme: brandTheme ?? 'light', brandLogoUrl, brandCustomHeader, brandCustomFooter }, createdAt: new Date().toISOString(), updatedAt: new Date().toISOString() },
        overallStatus: 'OPERATIONAL',
        componentGroups: [],
        maintenanceAnnouncements: [],
        monitors: [],
        incidents: [],
      })
    }

    return json({})
  })
}

test.beforeEach(async ({ page }) => {
  await mockApi(page)
})

async function seedAuthSession(page: Page) {
  await page.addInitScript(([key, value]) => {
    window.sessionStorage.setItem(key, JSON.stringify(value))
  }, [AUTH_STORAGE_KEY, { username: 'admin', authorizationHeader: 'Basic dGVzdDp0ZXN0' }])
}

test('login gate smoke and navigation across major routes', async ({ page }) => {
  await page.goto('/dashboard')
  await expect(page.getByRole('heading', { name: 'Admin sign in' })).toBeVisible()

  await page.getByLabel('Username').fill('admin')
  await page.getByLabel('Password').fill('admin-change-me')
  await page.getByRole('button', { name: 'Sign in' }).click()

  await expect(page.getByRole('heading', { name: 'Operations Dashboard' })).toBeVisible()
  await expect(page.getByRole('heading', { name: 'Live monitor grid' })).toBeVisible()
  await expect(page.getByRole('heading', { name: 'Incident timeline' })).toBeVisible()
  await page.getByRole('link', { name: 'Monitors' }).click()
  await expect(page.getByRole('heading', { name: 'Monitors', exact: true })).toBeVisible()

  await page.getByRole('link', { name: 'Incidents' }).click()
  await expect(page.getByRole('heading', { name: 'Incidents Console' })).toBeVisible()

  await page.getByRole('link', { name: 'Maintenance Windows' }).click()
  await expect(page.getByRole('heading', { name: 'Maintenance Windows', exact: true })).toBeVisible()

  await page.getByRole('link', { name: 'Notification Policies' }).click()
  await expect(page.getByRole('heading', { name: 'Notification Policies', exact: true })).toBeVisible()

  await page.getByRole('link', { name: 'Status Pages' }).click()
  await expect(page.getByRole('heading', { name: 'Status Pages', exact: true })).toBeVisible()

  await page.getByRole('link', { name: 'Audit Explorer' }).click()
  await expect(page.getByRole('heading', { name: 'Audit Explorer', exact: true })).toBeVisible()
})

test('dashboard triage smoke: identify DOWN monitor and inspect incident details', async ({ page }) => {
  await seedAuthSession(page)
  await page.goto('/dashboard')

  const apiGatewayCard = page.locator('article', { hasText: 'API Gateway' })
  await expect(apiGatewayCard.getByText('DOWN')).toBeVisible()

  await page.getByRole('button', { name: /API Gateway OPEN/ }).click()
  await expect(page.getByText('Incident ID: inc-1')).toBeVisible()
  await expect(page.getByRole('definition').filter({ hasText: 'HTTP 503 from gateway health endpoint' })).toBeVisible()
})

test('key action flow smoke: create status page', async ({ page }) => {
  await seedAuthSession(page)
  await page.goto('/status-pages')

  await page.getByLabel('Page name').fill('Prod Ops')
  await page.getByLabel('Slug', { exact: true }).fill('prod-ops')
  await page.getByRole('button', { name: 'Create page' }).click()

  await expect(page.getByText('Status page created.')).toBeVisible()
})

test('status page v2 smoke: branding + config saved and public preview rendered', async ({ page }) => {
  await seedAuthSession(page)
  await page.goto('/status-pages')

  await page.getByLabel('Brand name').fill('OpenPulse Public')
  await page.getByRole('button', { name: 'Save branding' }).click()
  await expect(page.getByText('Branding saved.')).toBeVisible()

  await page.getByRole('button', { name: '+ Add group' }).click()
  await page.getByRole('button', { name: 'Save v2 config' }).click()
  await expect(page.getByText('Status Page v2 config saved.')).toBeVisible()

  await expect(page.getByText('OpenPulse Public')).toBeVisible()
})

test('first-run setup wizard creates admin and redirects to sign in', async ({ page }) => {
  await mockApi(page, { setupRequired: true, setupLocked: false, setupToken: 'setup-token-1' })

  await page.goto('/login')
  await expect(page.getByRole('heading', { name: 'Initial admin setup' })).toBeVisible()

  await page.getByLabel('Owner email or username').fill('owner@example.com')
  await page.getByLabel('Password', { exact: true }).fill('very-secure-password')
  await page.getByLabel('Confirm password').fill('very-secure-password')
  await page.getByLabel('I understand this account receives full administrative access and must follow organization policy.').check()
  await page.getByLabel('I confirm the credentials are stored securely and not shared in plain text.').check()

  await page.getByRole('button', { name: 'Complete setup' }).click()

  await expect(page.getByRole('heading', { name: 'Admin sign in' })).toBeVisible()
})

test('setup route lockout redirects when onboarding is already complete', async ({ page }) => {
  await mockApi(page, { setupRequired: false, setupLocked: true, setupToken: null })

  await page.goto('/setup')

  await expect(page).toHaveURL(/\/login$/)
  await expect(page.getByRole('heading', { name: 'Admin sign in' })).toBeVisible()
})

test('setup wizard shows weak password validation before submit', async ({ page }) => {
  await mockApi(page, { setupRequired: true, setupLocked: false, setupToken: 'setup-token-weak' })

  await page.goto('/login')
  await expect(page.getByRole('heading', { name: 'Initial admin setup' })).toBeVisible()

  await page.getByLabel('Owner email or username').fill('owner@example.com')
  await page.getByLabel('Password', { exact: true }).fill('shortpass')
  await page.getByLabel('Confirm password').fill('shortpass')
  await page.getByLabel('I understand this account receives full administrative access and must follow organization policy.').check()
  await page.getByLabel('I confirm the credentials are stored securely and not shared in plain text.').check()

  await page.getByRole('button', { name: 'Complete setup' }).click()

  await expect(page.getByText('Password must be at least 12 characters long.')).toBeVisible()
})

test('setup wizard surfaces expired token API errors', async ({ page }) => {
  await mockApi(page, { setupRequired: true, setupLocked: false, setupToken: 'setup-token-expired' }, { forceExpiredToken: true })

  await page.goto('/login')
  await expect(page.getByRole('heading', { name: 'Initial admin setup' })).toBeVisible()

  await page.getByLabel('Owner email or username').fill('owner@example.com')
  await page.getByLabel('Password', { exact: true }).fill('very-secure-password')
  await page.getByLabel('Confirm password').fill('very-secure-password')
  await page.getByLabel('I understand this account receives full administrative access and must follow organization policy.').check()
  await page.getByLabel('I confirm the credentials are stored securely and not shared in plain text.').check()

  await page.getByRole('button', { name: 'Complete setup' }).click()

  await expect(page.getByText('Invalid or expired setup token')).toBeVisible()
})

test('setup wizard surfaces duplicate setup attempt conflict', async ({ page }) => {
  await mockApi(page, { setupRequired: true, setupLocked: false, setupToken: 'setup-token-conflict' }, { forceConflictOnCreate: true })

  await page.goto('/login')
  await expect(page.getByRole('heading', { name: 'Initial admin setup' })).toBeVisible()

  await page.getByLabel('Owner email or username').fill('owner@example.com')
  await page.getByLabel('Password', { exact: true }).fill('very-secure-password')
  await page.getByLabel('Confirm password').fill('very-secure-password')
  await page.getByLabel('I understand this account receives full administrative access and must follow organization policy.').check()
  await page.getByLabel('I confirm the credentials are stored securely and not shared in plain text.').check()

  await page.getByRole('button', { name: 'Complete setup' }).click()

  await expect(page.getByText('Setup is already completed and locked. Please sign in with an existing admin account.')).toBeVisible()
})

test('monitors smoke: delete action success + blocked feedback', async ({ page }) => {
  await seedAuthSession(page)
  await page.goto('/monitors')

  page.once('dialog', (dialog) => dialog.accept())
  await page.getByRole('button', { name: 'Delete monitor' }).click()
  await expect(page.getByText('Monitor deleted. Status page bindings were detached automatically.')).toBeVisible()

  await mockApi(page, { setupRequired: false, setupLocked: true, setupToken: null }, { forceDeleteConflict: true })
  await page.goto('/monitors')
  page.once('dialog', (dialog) => dialog.accept())
  await page.getByRole('button', { name: 'Delete monitor' }).click()
  await expect(page.getByText('Monitor deletion blocked: historical references exist')).toBeVisible()
})
