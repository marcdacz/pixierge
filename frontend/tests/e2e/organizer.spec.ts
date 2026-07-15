import { expect, test } from '@playwright/test';
import { completeBrowsableLibrarySetup, mockPixiergeApi } from '../helpers/mock-pixierge-api';
import { OrganizerPage } from './organizer.page';

test('selects photos and assigns albums and tags', async ({ page }) => {
  await mockPixiergeApi(page);
  await completeBrowsableLibrarySetup(page);
  const organizer = new OrganizerPage(page);

  const tile = await organizer.assets.openActions('asset-1');
  await expect(tile).toHaveAttribute('aria-selected', 'true');

  await organizer.actionMenuItem('Add to starred').click();
  await expect(organizer.assets.starredBadge('asset-1')).toBeVisible();
  await organizer.openPrimary('Starred');
  await expect(page.getByRole('heading', { name: 'Starred' })).toBeVisible();
  await expect(page.getByRole('navigation', { name: 'Albums' })).toHaveCount(0);
  await expect(organizer.assets.tile('asset-1')).toBeVisible();
  await expect(organizer.assets.starredBadge('asset-1')).toBeVisible();

  await organizer.openPrimary('Libraries');
  await organizer.assets.openActions('asset-1');
  await organizer.actionMenuItem('Add to albums…').click();
  const albumsPicker = organizer.assignmentPicker('albums');
  await albumsPicker.create('Best of 2026');
  await albumsPicker.submit();

  await organizer.assets.openActions('asset-1');
  await organizer.actionMenuItem('Add tags…').click();
  const tagsPicker = organizer.assignmentPicker('tags');
  await tagsPicker.create('Favourite');
  await tagsPicker.submit();

  await organizer.openPrimary('Albums');
  await expect(organizer.albums.region).toBeVisible();
  await expect(organizer.albums.item(/^Best of 2026/)).toBeVisible();
  await expect(organizer.albums.item(/^Starred/)).toHaveCount(0);
  await expect(organizer.assets.tile('asset-1')).toBeVisible();

  await organizer.openPrimary('Tags');
  await expect(organizer.tags.region).toBeVisible();
  await expect(organizer.tags.item(/^Favourite/)).toBeVisible();
  await expect(organizer.assets.tile('asset-1')).toBeVisible();

  await page.reload();
  await organizer.openPrimary('Tags');
  await expect(organizer.tags.item(/^Favourite/)).toBeVisible();
  await organizer.openPrimary('Albums');
  await expect(organizer.albums.item(/^Best of 2026/)).toBeVisible();
});

test('album key photo and drag-drop assignment', async ({ page }) => {
  await mockPixiergeApi(page);
  await completeBrowsableLibrarySetup(page);
  const organizer = new OrganizerPage(page);

  await organizer.assets.openActions('asset-1');
  await organizer.actionMenuItem('Add to albums…').click();
  const albumsPicker = organizer.assignmentPicker('albums');
  await albumsPicker.create('Best of 2026');
  await albumsPicker.create('Archive');
  await albumsPicker.submit();

  await organizer.openPrimary('Albums');
  await expect(organizer.albums.item(/^Best of 2026/)).toBeVisible();
  await expect(organizer.albums.item(/^Archive/)).toBeVisible();

  await organizer.assets.openActions('asset-1');
  await organizer.actionMenuItem('Set as key photo').click();
  await expect(organizer.albums.region.locator('img[src$="/api/assets/asset-1/thumbnail?size=tiny"]')).toBeVisible();

  await organizer.assets.dragTo('asset-1', organizer.albums.item(/^Archive/));
  await expect(organizer.albums.item(/^Archive\s+\(2\)/)).toBeVisible();
  await organizer.albums.item(/^Archive/).click();
  await expect(organizer.assets.tile('asset-1')).toBeVisible();
});
