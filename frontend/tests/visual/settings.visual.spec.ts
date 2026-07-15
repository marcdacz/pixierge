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
});
