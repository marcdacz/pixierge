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
  await expect(page.getByRole('heading', { name: 'Backups' })).toBeVisible();

  await admin.logout();
  await expect(page.getByRole('heading', { name: 'Sign in' })).toBeVisible();
});
