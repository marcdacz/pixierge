import { expect, test } from '@playwright/test';
import { AdminShellPage } from '../e2e/admin-shell.page';
import { completeBrowsableLibrarySetup, mockPixiergeApi } from '../helpers/mock-pixierge-api';

test.describe('visual', { tag: '@visual' }, () => {
  test('scan activity popover and focused metadata visual regression', async ({ page }) => {
    await mockPixiergeApi(page);
    await completeBrowsableLibrarySetup(page);
    const admin = new AdminShellPage(page);

    await admin.openSettings();
    await admin.scanLibrary('library-1');
    await expect(admin.scanActivityTrigger()).toBeVisible();
    await admin.openScanActivity();
    await expect(page.getByText('Scan activity', { exact: true })).toBeVisible();
    await expect(page.getByText('Scanned 3')).toBeVisible();
    await expect(page).toHaveScreenshot('scan-activity.png', {
      fullPage: true,
      mask: [page.getByText(/\d+h \d+m/)]
    });
    await page.keyboard.press('Escape');

    await admin.openPrimary('libraries');
    await admin.openAsset('asset-1');
    await admin.showPhotoMetadata();
    await expect(page.getByText('/photos/family/beach.jpg').first()).toBeVisible();
    await expect(page.getByText('Metadata').locator('..')).toContainText('pending');
    await expect(page).toHaveScreenshot('photo-metadata.png', {
      fullPage: true
    });
  });

  test('authenticated shell visual regression', async ({ page }) => {
    await mockPixiergeApi(page);
    await completeBrowsableLibrarySetup(page);
    const admin = new AdminShellPage(page);

    await expect(page.getByRole('navigation', { name: 'Folders' })).toBeVisible();
    await expect(admin.assetTile('asset-1')).toBeVisible();
    await expect(admin.assetImage('asset-1')).toHaveClass(/opacity-100/, {
      timeout: 10_000
    });
    await expect(page).toHaveScreenshot('browse-library.png', {
      fullPage: true
    });

    await admin.openSettings();
    await expect(page.getByRole('heading', { name: 'Settings' })).toBeVisible();
    await expect(page).toHaveScreenshot('settings.png', {
      fullPage: true
    });
  });
});
