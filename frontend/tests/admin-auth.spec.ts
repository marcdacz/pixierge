import { expect, test } from '@playwright/test';

const apiBaseUrl = 'http://localhost:8080';

const authBody = {
  csrfToken: 'csrf-token',
  user: {
    id: 'user-1',
    email: 'admin@example.com',
    displayName: 'Admin User',
    roles: ['ADMIN'],
    permissions: ['identity:admin', 'identity:read']
  }
};

test('admin setup, sign out, sign in, and user list visibility', async ({ page }) => {
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
            email: 'admin@example.com',
            displayName: 'Admin User',
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

  await page.goto('/');
  await expect(page.getByRole('heading', { name: 'Create admin account' })).toBeVisible();

  await page.getByLabel('Email').fill('admin@example.com');
  await page.getByLabel('Display name').fill('Admin User');
  await page.getByLabel('Password').fill('correct horse battery staple');
  await page.getByRole('button', { name: 'Create admin' }).click();

  await expect(page.getByRole('heading', { name: 'Admin' })).toBeVisible();
  await expect(page.getByText('admin@example.com')).toBeVisible();

  await page.getByRole('button', { name: 'Sign out' }).click();
  await expect(page.getByRole('heading', { name: 'Admin sign in' })).toBeVisible();

  await page.getByLabel('Email').fill('admin@example.com');
  await page.getByLabel('Password').fill('correct horse battery staple');
  await page.getByRole('button', { name: 'Sign in' }).click();

  await expect(page.getByRole('heading', { name: 'Admin' })).toBeVisible();
  await expect(page.getByRole('cell', { name: 'Admin User' })).toBeVisible();
  await expect(page.getByRole('cell', { name: 'ADMIN', exact: true })).toBeVisible();
});
