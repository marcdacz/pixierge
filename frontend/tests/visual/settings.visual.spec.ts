import { expect, test } from '@playwright/test';
import { AdminShellPage } from '../e2e/admin-shell.page';
import { completeBrowsableLibrarySetup, mockPixiergeApi } from '../helpers/mock-pixierge-api';

test.describe('visual', { tag: '@visual' }, () => {
  test('scheduler details visual regression', async ({ page }) => {
    await mockPixiergeApi(page);
    await completeBrowsableLibrarySetup(page);
    const admin = new AdminShellPage(page);

    await admin.openSettings();
    await admin.openSettingsSection('scheduler');
    await expect(page.getByRole('heading', { name: 'Scheduler details' })).toBeVisible();
    await expect(admin.scheduler.job('job-metadata', 'Metadata scan').row).toBeVisible();
    await expect(page).toHaveScreenshot('settings-scheduler-details.png', {
      fullPage: true
    });
  });

  test('users settings visual regression', async ({ page }) => {
    await mockPixiergeApi(page);
    await completeBrowsableLibrarySetup(page);
    const admin = new AdminShellPage(page);

    await admin.openSettings();
    await admin.openSettingsSection('users');
    await page.getByLabel('Username').fill('sam');
    await page.getByLabel('Password').fill('a secure password');
    await page.getByRole('button', { name: 'Create user' }).click();
    await expect(page.getByText('sam created.')).toBeVisible();
    await expect(page.getByRole('row', { name: /sam active USER/ })).toBeVisible();
    await expect(page).toHaveScreenshot('settings-users.png', {
      fullPage: true
    });
  });

  test('backup and restore visual regression', async ({ page }) => {
    await mockPixiergeApi(page);
    await completeBrowsableLibrarySetup(page);
    const admin = new AdminShellPage(page);

    await admin.openSettings();
    await admin.openSettingsSection('backups');
    await expect(page.getByRole('heading', { name: 'Backup and Restore' })).toBeVisible();
    await expect(page.getByRole('heading', { name: 'Database backups' })).toBeVisible();
    await expect(page).toHaveScreenshot('settings-backup-and-restore.png', {
      fullPage: true
    });
  });

  test('audit log visual regression', async ({ page }) => {
    await mockPixiergeApi(page);
    await completeBrowsableLibrarySetup(page);
    const admin = new AdminShellPage(page);

    await admin.openSettings();
    await admin.openSettingsSection('audit');
    await expect(page.getByRole('heading', { name: 'Audit log', level: 3 })).toBeVisible();
    await expect(page.getByRole('cell', { name: 'admin' })).toBeVisible();
    await expect(page).toHaveScreenshot('settings-audit-log.png', {
      fullPage: true
    });
  });
});
