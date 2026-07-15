import { expect, test } from '@playwright/test';
import { SearchPage } from '../e2e/search.page';
import { completeBrowsableLibrarySetup, mockPixiergeApi } from '../helpers/mock-pixierge-api';

test.describe('visual', { tag: '@visual' }, () => {
  test('structured search chips and filters visual regression', async ({ page }) => {
    await mockPixiergeApi(page);
    await completeBrowsableLibrarySetup(page);
    const search = new SearchPage(page);

    await search.typeToken('tag:Fa');
    await expect(search.suggestion('Family')).toBeVisible();
    await search.suggestion('Family').click();
    await search.typeToken('-is:starred');
    await search.commitDraft();

    await expect(search.removePill('tag', 'Family')).toBeVisible();
    await expect(search.removePill('is', 'starred', { negated: true })).toBeVisible();
    await expect(page.getByRole('heading', { name: 'Search results' })).toBeVisible();
    await expect(page.getByRole('navigation', { name: 'Filters' })).toBeVisible();
    await expect(page).toHaveScreenshot('structured-search.png', {
      fullPage: true
    });
  });
});
