import { expect, test, type Page } from '@playwright/test'

const AUTH_STORAGE_KEY = 'opc.admin.auth'

async function mockApi(page: Page) {
  await page.route('**/api/v1/**', async (route) => {
    const url = new URL(route.request().url())
    const { pathname } = url
    const method = route.request().method()
    const authHeader = route.request().headers()['authorization']

    const json = (body: unknown, status = 200) => route.fulfill({ status, contentType: 'application/json', body: JSON.stringify(body) })

    const requiresAuth = pathname.startsWith('/api/v1/admin') || pathname === '/api/v1/monitors' || pathname === '/api/v1/status-pages'
    if (requiresAuth && !authHeader) {
      return json({ error: 'Unauthorized' }, 401)
    }

    if (pathname.endsWith('/monitors') && method === 'GET') {
      return json([
        {
          id: 'm1',
          name: 'API Gateway',
          target: 'https://example.com/health',
          enabled: true,
          intervalSeconds: 60,
          createdAt: new Date().toISOString(),
        },
      ])
    }

    if (pathname.endsWith('/admin/incidents') && method === 'GET') {
      return json([
        {
          id: 'inc-1',
          monitorName: 'API Gateway',
          reason: 'Synthetic smoke incident',
          state: 'OPEN',
          openedAt: new Date().toISOString(),
          resolvedAt: null,
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

    if (pathname.endsWith('/status-pages') && method === 'GET') {
      return json([{ id: 'sp-1', name: 'Main Status', slug: 'main-status', isPublic: true, createdAt: new Date().toISOString() }])
    }

    if (pathname.endsWith('/status-pages') && method === 'POST') {
      const payload = JSON.parse(route.request().postData() || '{}')
      return json({ id: 'sp-new', name: payload.name, slug: payload.slug, isPublic: payload.isPublic ?? true, createdAt: new Date().toISOString() }, 201)
    }

    if (pathname.includes('/public/status-pages/') && method === 'GET') {
      const slug = pathname.split('/').pop() || 'main-status'
      return json({
        statusPage: { id: 'sp-1', name: 'Main Status', slug, isPublic: true, createdAt: new Date().toISOString() },
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

  await expect(page.getByRole('heading', { name: 'Frontend Foundation' })).toBeVisible()
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

test('key action flow smoke: create status page', async ({ page }) => {
  await seedAuthSession(page)
  await page.goto('/status-pages')

  await page.getByLabel('Page name').fill('Prod Ops')
  await page.getByLabel('Slug', { exact: true }).fill('prod-ops')
  await page.getByRole('button', { name: 'Create page' }).click()

  await expect(page.getByText('Status page created.')).toBeVisible()
})
