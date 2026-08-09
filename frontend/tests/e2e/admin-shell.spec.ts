import { expect, test } from '@playwright/test';
import { mockPixiergeApi } from '../helpers/mock-pixierge-api';
import { AdminShellPage } from './admin-shell.page';

test('admin setup, empty library, settings, and profile logout', async ({ page }) => {
  await mockPixiergeApi(page);
  const admin = new AdminShellPage(page);

  await admin.goto();
  await expect(page.getByRole('heading', { name: 'Create admin account' })).toBeVisible();

  await admin.createAdmin('admin', 'correct horse battery staple');

  await expect(page.getByRole('heading', { name: 'Libraries' })).toBeVisible();
  await expect(page.getByText('No library sources have been added yet.')).toBeVisible();

  await admin.configureSourcesFromEmptyLibrary();
  await expect(page.getByRole('heading', { name: 'Settings' })).toBeVisible();
  await expect(page.getByRole('heading', { name: 'Configuration' })).toBeVisible();

  await admin.createLibrary('Family Photos');
  await expect(admin.libraryNavItem(/Family Photos\s+0 sources/)).toBeVisible();

  await admin.hoverSourcePathGuidance();
  await expect(page.getByText(/Docker sources must use container paths/)).toBeVisible();
  await admin.addSource('/photos/family');
  await expect(page.getByText('/photos/family')).toBeVisible();
  await expect(admin.libraryNavItem(/Family Photos\s+1 source/)).toBeVisible();

  await admin.togglePrimaryNavigation();
  await expect(page.getByRole('navigation', { name: 'Primary' }).getByText('Family Photos')).toBeHidden();

  await admin.openPrimary('libraries');
  await expect(page.getByRole('navigation', { name: 'Folders' })).toBeVisible();
  await expect(page.getByRole('heading', { name: 'All folders' })).toBeVisible();
  await expect(admin.assetTile('asset-1')).toBeVisible();

  await admin.folder(/^family/).click();
  await expect(page.getByRole('heading', { name: 'family' })).toBeVisible();
  await admin.search.typeToken('beach');
  await expect(page.getByRole('heading', { name: 'Search results' })).toBeVisible();
  await expect(page.getByRole('navigation', { name: 'Filters' })).toBeVisible();
  await expect(admin.assetTile('asset-1')).toBeVisible();
  await admin.search.typeToken('tag:Family');
  await admin.search.commitDraft();
  await expect(admin.search.removePill('tag', 'Family')).toBeVisible();
  await expect(page).toHaveURL(/\?q=tag%3AFamily$/);
  await page.reload();
  await expect(page.getByRole('heading', { name: 'Search results' })).toBeVisible();
  await expect(admin.search.removePill('tag', 'Family')).toBeVisible();
  await admin.assetTile('asset-1').click();
  await expect(admin.assetTile('asset-1')).toHaveAttribute('aria-selected', 'true');
  await admin.openAsset('asset-1');
  await expect(page.getByTestId('photo-viewer-close')).toBeVisible();
  await admin.showPhotoMetadata();
  await expect(page.getByText('/photos/family/beach.jpg').first()).toBeVisible();
  await admin.dismissPhotoMetadata();
  await admin.closePhotoViewer();

  await admin.openSettings();
  await admin.openSettingsSection('scheduler');
  await expect(page.getByRole('heading', { name: 'Scheduler details' })).toBeVisible();
  const metadataSchedulerRow = admin.scheduler.job('job-metadata', 'Metadata scan');
  await expect(metadataSchedulerRow.row).toBeVisible();
  await metadataSchedulerRow.runNow();
  await metadataSchedulerRow.expectRanSuccessfully();
  await metadataSchedulerRow.toggleEnabled();
  await expect(metadataSchedulerRow.enabledToggle).toHaveAttribute('aria-label', 'Enable · Metadata scan');
  await expect(metadataSchedulerRow.row.getByText('Next: —')).toBeVisible();
  await metadataSchedulerRow.editSchedule('every-5-mins', 'Australia/Melbourne');
  await expect(metadataSchedulerRow.row.getByText('0 */5 * * * *')).toBeVisible();
  await expect(metadataSchedulerRow.row.getByText('Australia/Melbourne')).toBeVisible();
  await admin.openSettingsSection('plugins');
  await expect(page.getByRole('heading', { name: 'Plugins' })).toBeVisible();
  await admin.openSettingsSection('backups');
  await expect(page.getByRole('heading', { name: 'Backup and Restore' })).toBeVisible();
  await expect(page.getByRole('heading', { name: 'Database backups' })).toBeVisible();
  await admin.openSettingsSection('audit');
  await expect(page.getByRole('heading', { name: 'Audit log', level: 3 })).toBeVisible();
  await expect(page.getByRole('cell', { name: 'admin' })).toBeVisible();

  await admin.logout();
  await expect(page.getByRole('heading', { name: 'Sign in' })).toBeVisible();
});

test('settings keeps scroll inside the app shell', async ({ page }) => {
  await mockPixiergeApi(page);
  const admin = new AdminShellPage(page);

  await admin.goto();
  await admin.createAdmin('admin', 'correct horse battery staple');
  await admin.openSettings();
  await admin.openSettingsSection('background');

  await expect(page.getByRole('heading', { name: 'Background work' })).toBeVisible();
  await expect(page.getByRole('heading', { name: 'Queue health' })).toBeVisible();

  const documentMetrics = await page.evaluate(() => ({
    bodyOverflowY: getComputedStyle(document.body).overflowY,
    documentOverflowY: getComputedStyle(document.documentElement).overflowY,
    rootClientHeight: document.documentElement.clientHeight,
    rootScrollHeight: document.documentElement.scrollHeight,
    scrollY: window.scrollY
  }));
  expect(documentMetrics).toEqual({
    bodyOverflowY: 'hidden',
    documentOverflowY: 'hidden',
    rootClientHeight: documentMetrics.rootClientHeight,
    rootScrollHeight: documentMetrics.rootClientHeight,
    scrollY: 0
  });

  const settingsScroll = page.getByTestId('settings-content-scroll');
  await expect(settingsScroll).toBeVisible();
  await expect(settingsScroll).toHaveCSS('overscroll-behavior-y', 'contain');
  await expect(settingsScroll).toHaveCSS('overflow-y', 'auto');

  await page.mouse.wheel(0, 4000);
  await expect.poll(() => page.evaluate(() => window.scrollY)).toBe(0);
});

test('admin creates and deletes local users with replacement', async ({ page }) => {
  await mockPixiergeApi(page);
  const admin = new AdminShellPage(page);

  await admin.goto();
  await admin.createAdmin('admin', 'correct horse battery staple');
  await admin.openSettings();
  await admin.openSettingsSection('users');

  await expect(page.getByRole('heading', { name: 'Users' })).toBeVisible();
  await page.getByLabel('Username').fill('sam');
  await page.getByLabel('Password').fill('a secure password');
  await page.getByRole('button', { name: 'Create user' }).click();
  await expect(page.getByText('sam created.')).toBeVisible();

  await page.getByLabel('Username').fill('lee');
  await page.getByLabel('Password').fill('another secure password');
  await page.getByRole('button', { name: 'Create user' }).click();
  await expect(page.getByText('lee created.')).toBeVisible();

  const samRow = page.getByRole('row', { name: /sam active USER/ });
  await expect(samRow).toBeVisible();
  await samRow.getByRole('button', { name: 'Delete' }).click();
  const dialog = page.getByRole('dialog', { name: 'Delete sam?' });
  await dialog.getByLabel('Replacement user').selectOption({ label: 'lee' });
  await dialog.getByLabel('Type sam to confirm').fill('sam');
  await dialog.getByRole('button', { name: 'Delete user' }).click();

  await expect(page.getByText('sam deleted.')).toBeVisible();
  await expect(samRow).toBeHidden();
  await expect(page.getByRole('row', { name: /lee active USER/ })).toBeVisible();
});
