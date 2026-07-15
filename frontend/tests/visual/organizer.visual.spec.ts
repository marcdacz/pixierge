import { expect, test } from '@playwright/test';
import { OrganizerPage } from '../e2e/organizer.page';
import { completeBrowsableLibrarySetup, mockPixiergeApi } from '../helpers/mock-pixierge-api';

test.describe('visual', { tag: '@visual' }, () => {
  test('assignment picker visual regression', async ({ page }) => {
    await mockPixiergeApi(page);
    await completeBrowsableLibrarySetup(page);
    const organizer = new OrganizerPage(page);

    await organizer.assets.openActions('asset-1');
    await organizer.actionMenuItem('Add to albums…').click();
    const albumsPicker = organizer.assignmentPicker('albums');
    await albumsPicker.searchFor('Summer');
    await expect(albumsPicker.createOption('Summer')).toBeVisible();
    await expect(page).toHaveScreenshot('assignment-picker.png', {
      fullPage: true
    });
  });

  test('starred organizer visual regression', async ({ page }) => {
    await mockPixiergeApi(page);
    await completeBrowsableLibrarySetup(page);
    const organizer = new OrganizerPage(page);

    await organizer.assets.openActions('asset-1');
    await organizer.actionMenuItem('Add to starred').click();
    await organizer.openPrimary('Starred');
    await expect(page.getByRole('heading', { name: 'Starred' })).toBeVisible();
    await expect(organizer.assets.tile('asset-1')).toBeVisible();
    await expect(page).toHaveScreenshot('browse-starred.png', {
      fullPage: true
    });
  });

  test('albums organizer visual regression', async ({ page }) => {
    await mockPixiergeApi(page);
    await completeBrowsableLibrarySetup(page);
    const organizer = new OrganizerPage(page);

    await organizer.assets.openActions('asset-1');
    await organizer.actionMenuItem('Add to albums…').click();
    const albumsPicker = organizer.assignmentPicker('albums');
    await albumsPicker.create('Summer');
    await albumsPicker.submit();
    await organizer.openPrimary('Albums');
    await expect(organizer.albums.region).toBeVisible();
    await expect(page).toHaveScreenshot('browse-albums.png', {
      fullPage: true
    });
  });
});
