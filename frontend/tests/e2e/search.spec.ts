import { expect, test } from '@playwright/test';
import { completeBrowsableLibrarySetup, mockPixiergeApi } from '../helpers/mock-pixierge-api';
import { SearchPage } from './search.page';

test('structured search suggestions, negation, validation, and reload persistence', async ({ page }) => {
  await mockPixiergeApi(page);
  await completeBrowsableLibrarySetup(page);
  const search = new SearchPage(page);

  await search.typeToken('tag:Fa');
  await expect(search.suggestion('Family')).toBeVisible();
  await search.suggestion('Family').click();
  await expect(search.removePill('tag', 'Family')).toBeVisible();

  await search.typeToken('-is:starred');
  await search.commitDraft();
  await expect(search.removePill('is', 'starred', { negated: true })).toBeVisible();
  await expect(page).toHaveURL(/\?q=tag%3AFamily\+-is%3Astarred$/);

  await page.reload();
  await expect(search.removePill('tag', 'Family')).toBeVisible();
  await expect(search.removePill('is', 'starred', { negated: true })).toBeVisible();

  await search.removePill('tag', 'Family').click();
  await search.removePill('is', 'starred', { negated: true }).click();
  await search.input.click();
  await search.typeToken('unknown:value');
  await expect(search.input).toHaveAttribute('aria-invalid', 'true');
});
