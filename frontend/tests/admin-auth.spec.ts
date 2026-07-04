import { expect, test, type Page } from '@playwright/test';

const apiBaseUrl = 'http://localhost:8080';

const authBody = {
  csrfToken: 'csrf-token',
  user: {
    id: 'user-1',
    username: 'admin',
    roles: ['ADMIN'],
    permissions: ['identity:admin', 'identity:read']
  }
};

test('admin setup, empty library, settings, and profile logout', async ({ page }) => {
  await mockPixiergeApi(page);

  await page.goto('/');
  await expect(page.getByRole('heading', { name: 'Create admin account' })).toBeVisible();

  await page.getByLabel('Username').fill('admin');
  await page.getByLabel('Password').fill('correct horse battery staple');
  await page.getByRole('button', { name: 'Create admin' }).click();

  await expect(page.getByRole('heading', { name: 'Libraries' })).toBeVisible();
  await expect(page.getByText('No libraries have been added yet.')).toBeVisible();

  await page.getByRole('navigation', { name: 'Utilities' }).getByRole('button', { name: 'Settings' }).click();
  await expect(page.getByRole('heading', { name: 'Settings' })).toBeVisible();
  await expect(page.getByRole('heading', { name: 'Configuration' })).toBeVisible();
  await page.getByRole('navigation', { name: 'Settings' }).getByRole('button', { name: 'Plugins' }).click();
  await expect(page.getByRole('heading', { name: 'Plugins' })).toBeVisible();
  await page.getByRole('navigation', { name: 'Settings' }).getByRole('button', { name: 'Backups' }).click();
  await expect(page.getByRole('heading', { name: 'Backups' })).toBeVisible();

  await page.getByRole('button', { name: 'Profile' }).click();
  await page.getByRole('menuitem', { name: 'Log out' }).click();
  await expect(page.getByRole('heading', { name: 'Sign in' })).toBeVisible();
});

test('authenticated shell visual regression @visual', async ({ page }) => {
  await mockPixiergeApi(page);
  await completeOnboarding(page);

  await expect(page).toHaveScreenshot('authenticated-libraries.png', {
    fullPage: true
  });

  await page.getByRole('navigation', { name: 'Utilities' }).getByRole('button', { name: 'Settings' }).click();
  await expect(page.getByRole('heading', { name: 'Settings' })).toBeVisible();
  await expect(page).toHaveScreenshot('settings.png', {
    fullPage: true
  });
});

async function mockPixiergeApi(page: Page) {
  let setupRequired = true;
  let signedIn = false;

  await page.route(`${apiBaseUrl}/api/**`, async (route) => {
    const request = route.request();
    const path = new URL(request.url()).pathname;

    if (path === '/api/health') {
      await route.fulfill({ json: { status: 'ok', database: 'ready', app: 'pixierge-api' } });
      return;
    }

    if (path === '/api/setup/status') {
      await route.fulfill({ json: { required: setupRequired } });
      return;
    }

    if (path === '/api/setup/admin' && request.method() === 'POST') {
      setupRequired = false;
      signedIn = true;
      await route.fulfill({ json: authBody });
      return;
    }

    if (path === '/api/auth/session') {
      await route.fulfill(signedIn ? { json: authBody } : { status: 401, json: {} });
      return;
    }

    if (path === '/api/auth/login' && request.method() === 'POST') {
      signedIn = true;
      await route.fulfill({ json: authBody });
      return;
    }

    if (path === '/api/auth/logout' && request.method() === 'POST') {
      signedIn = false;
      await route.fulfill({ status: 200, body: '' });
      return;
    }

    if (path === '/api/admin/users') {
      await route.fulfill({
        json: [
          {
            id: 'user-1',
            username: 'admin',
            status: 'active',
            roles: ['ADMIN'],
            createdAt: '2026-07-03T00:00:00Z'
          }
        ]
      });
      return;
    }

    if (path === '/api/admin/roles') {
      await route.fulfill({
        json: [
          {
            key: 'ADMIN',
            name: 'Admin',
            description: 'Full local administration role',
            permissions: ['identity:admin', 'identity:read']
          }
        ]
      });
      return;
    }

    await route.fulfill({ status: 404, json: {} });
  });
}

async function completeOnboarding(page: Page) {
  await page.goto('/');
  await expect(page.getByRole('heading', { name: 'Create admin account' })).toBeVisible();

  await page.getByLabel('Username').fill('admin');
  await page.getByLabel('Password').fill('correct horse battery staple');
  await page.getByRole('button', { name: 'Create admin' }).click();

  await expect(page.getByRole('heading', { name: 'Libraries' })).toBeVisible();
}
