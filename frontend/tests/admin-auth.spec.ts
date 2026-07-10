import { expect, test, type Page } from '@playwright/test';

const authBody = {
  csrfToken: 'csrf-token',
  user: {
    id: 'user-1',
    username: 'admin',
    roles: ['ADMIN'],
    permissions: ['identity:admin', 'identity:read', 'library:admin', 'library:read']
  }
};

const assetFileSvg = `
<svg xmlns="http://www.w3.org/2000/svg" width="640" height="480" viewBox="0 0 640 480">
  <rect width="640" height="480" fill="#1f2937"/>
  <circle cx="230" cy="210" r="82" fill="#facc15"/>
  <path d="M0 360L180 250L310 335L430 265L640 390V480H0Z" fill="#16a34a"/>
</svg>
`;

const assetDetailResponse = {
  id: 'asset-1',
  contentHash: 'confirmed-content-hash',
  identityStatus: 'confirmed',
  mediaType: 'image/jpeg',
  availability: 'available',
  duplicateCount: 1,
  metadata: {
    capturedAt: null,
    width: null,
    height: null,
    fileExtension: 'jpg',
    mimeType: 'image/jpeg',
    extractionStatus: 'pending',
    extractedAt: null,
    errorMessage: null
  },
  files: [
    {
      id: 'file-1',
      libraryId: 'library-1',
      libraryName: 'Family Photos',
      path: '/photos/family/beach.jpg',
      folderPath: '/photos/family',
      fileName: 'beach.jpg',
      sizeBytes: 1200,
      modifiedAt: '2026-07-04T00:00:00Z',
      status: 'active'
    }
  ]
};

const assetBrowseResponse = {
  sections: [
    {
      folderPath: '/photos/family',
      folderName: 'family',
      assets: [
        {
          id: 'asset-1',
          fileName: 'beach.jpg',
          displayPath: '/photos/family/beach.jpg',
          folderPath: '/photos/family',
          libraryId: 'library-1',
          libraryName: 'Family Photos',
          availability: 'available',
          identityStatus: 'confirmed',
          duplicateCount: 1,
          capturedAt: null,
          observedAt: '2026-07-04T00:00:00Z',
          mediaType: 'image/jpeg',
          mimeType: 'image/jpeg',
          width: null,
          height: null,
          previewable: true,
          thumbnailStatus: 'ready',
          thumbnailCacheKey: 'e2e-thumbnail-v1',
          thumbnailPlaceholder: 'linear-gradient(135deg, rgb(120, 130, 140), rgb(90, 100, 110))'
        }
      ]
    }
  ],
  totalCount: 1,
  page: 0,
  pageSize: 48,
  hasNext: false
};

const libraryTreeResponse = {
  roots: [
    {
      id: 'library-1:/photos',
      libraryId: 'library-1',
      libraryName: 'Family Photos',
      path: '/photos',
      name: 'photos',
      assetCount: 1,
      childCount: 1,
      children: [
        {
          id: 'library-1:/photos/family',
          libraryId: 'library-1',
          libraryName: 'Family Photos',
          path: '/photos/family',
          name: 'family',
          assetCount: 1,
          childCount: 0,
          children: []
        }
      ]
    }
  ],
  libraryRootAssetCounts: {},
  libraryAssetCounts: {}
};

test('admin setup, empty library, settings, and profile logout', async ({ page }) => {
  await mockPixiergeApi(page);

  await page.goto('/');
  await expect(page.getByRole('heading', { name: 'Create admin account' })).toBeVisible();

  await page.getByLabel('Username').fill('admin');
  await page.getByLabel('Password').fill('correct horse battery staple');
  await page.getByRole('button', { name: 'Create admin' }).click();

  await expect(page.getByRole('heading', { name: 'Libraries' })).toBeVisible();
  await expect(page.getByText('No library sources have been added yet.')).toBeVisible();

  await page.getByRole('button', { name: 'Configure sources' }).click();
  await expect(page.getByRole('heading', { name: 'Settings' })).toBeVisible();
  await expect(page.getByRole('heading', { name: 'Configuration' })).toBeVisible();

  await page.getByLabel('Library name').fill('Family Photos');
  await page.getByRole('button', { name: 'Create' }).click();
  const librariesNavigation = page.getByRole('navigation', { name: 'Libraries' });
  await expect(
    librariesNavigation.getByRole('button', { name: /Family Photos\s+0 sources/ })
  ).toBeVisible();

  await page.getByRole('button', { name: 'Source path Docker guidance' }).hover();
  await expect(page.getByText(/Docker sources must use container paths/)).toBeVisible();
  await page.getByRole('textbox', { name: 'Source path' }).fill('/photos/family');
  await page.getByRole('button', { name: 'Add source' }).click();
  await expect(page.getByText('/photos/family')).toBeVisible();
  await expect(
    librariesNavigation.getByRole('button', { name: /Family Photos\s+1 source/ })
  ).toBeVisible();

  await page.getByRole('button', { name: 'Expand navigation' }).click();
  await expect(page.getByRole('navigation', { name: 'Primary' }).getByText('Family Photos')).toBeHidden();

  await page.getByRole('button', { name: 'Libraries' }).click();
  await expect(page.getByRole('navigation', { name: 'Folders' })).toBeVisible();
  await expect(page.getByRole('heading', { name: 'All folders' })).toBeVisible();
  await expect(page.getByRole('button', { name: 'Open beach.jpg' })).toBeVisible();

  await page.getByRole('button', { name: /^family/ }).click();
  await expect(page.getByRole('heading', { name: 'family' })).toBeVisible();
  await page.getByLabel('Search').fill('beach');
  await expect(page.getByRole('button', { name: 'Open beach.jpg' })).toBeVisible();
  await page.getByRole('button', { name: 'Open beach.jpg' }).click();
  await expect(page.getByRole('button', { name: 'Close photo viewer' })).toBeVisible();
  await page.getByRole('button', { name: 'Show photo metadata' }).click();
  await expect(page.getByText('/photos/family/beach.jpg').first()).toBeVisible();
  await page.getByRole('button', { name: 'Dismiss photo metadata' }).click();
  await page.getByRole('button', { name: 'Close photo viewer' }).click();

  await page.getByRole('navigation', { name: 'Utilities' }).getByRole('button', { name: 'Settings' }).click();
  await page.getByRole('navigation', { name: 'Settings' }).getByRole('button', { name: 'Plugins' }).click();
  await expect(page.getByRole('heading', { name: 'Plugins' })).toBeVisible();
  await page.getByRole('navigation', { name: 'Settings' }).getByRole('button', { name: 'Backups' }).click();
  await expect(page.getByRole('heading', { name: 'Backups' })).toBeVisible();

  await page.getByRole('button', { name: 'Profile' }).click();
  await page.getByRole('menuitem', { name: 'Log out' }).click();
  await expect(page.getByRole('heading', { name: 'Sign in' })).toBeVisible();
});

test('scan activity indicator and confirmed asset detail @visual', async ({ page }) => {
  await mockPixiergeApi(page);
  await completeBrowsableLibrarySetup(page);

  await page.getByRole('navigation', { name: 'Utilities' }).getByRole('button', { name: 'Settings' }).click();
  await page.getByRole('button', { name: 'Scan library' }).click();
  await expect(page.getByRole('button', { name: 'Scan activity' })).toBeVisible();
  await page.getByRole('button', { name: 'Scan activity' }).click();
  await expect(page.getByText('Scan activity', { exact: true })).toBeVisible();
  await expect(page.getByText('Scanned 3')).toBeVisible();
  await page.keyboard.press('Escape');

  await page.getByRole('button', { name: 'Libraries' }).click();
  await page.getByRole('button', { name: 'Open beach.jpg' }).click();
  await page.getByRole('button', { name: 'Show photo metadata' }).click();
  await expect(page.getByText('Identity').locator('..')).toContainText('confirmed');
  await expect(page.getByText('Metadata').locator('..')).toContainText('pending');
});

test('authenticated shell visual regression @visual', async ({ page }) => {
  await mockPixiergeApi(page);
  await completeBrowsableLibrarySetup(page);

  await expect(page.getByRole('navigation', { name: 'Folders' })).toBeVisible();
  await expect(page.getByRole('button', { name: 'Open beach.jpg' })).toBeVisible();
  await expect(page.locator('img[src="/api/assets/asset-1/thumbnail?c=e2e-thumbnail-v1"]')).toHaveClass(/opacity-100/);
  await expect(page).toHaveScreenshot('browse-library.png', {
    fullPage: true
  });

  await page.getByRole('navigation', { name: 'Utilities' }).getByRole('button', { name: 'Settings' }).click();
  await expect(page.getByRole('heading', { name: 'Settings' })).toBeVisible();
  await expect(page).toHaveScreenshot('settings.png', {
    fullPage: true
  });
});

async function mockPixiergeApi(page: Page) {
  let setupRequired = true;
  let signedIn = false;
  let scanStatus: 'running' | 'completed' = 'completed';
  const libraries = new Map<string, {
    id: string;
    name: string;
    status: 'active' | 'archived';
    sources: {
      id: string;
      path: string;
      available: boolean;
      unavailableReason: string | null;
      createdAt: string;
    }[];
    exclusionPatterns: {
      id: string;
      pattern: string;
      createdAt: string;
    }[];
  }>();

  await page.route('**/api/**', async (route) => {
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

    if (path === '/api/library-tree') {
      await route.fulfill({ json: libraryTreeResponse });
      return;
    }

    if (path === '/api/assets/asset-1/file') {
      await route.fulfill({
        body: assetFileSvg,
        contentType: 'image/svg+xml'
      });
      return;
    }

    if (path === '/api/assets/asset-1/thumbnail' || path === '/api/assets/asset-1/preview') {
      await route.fulfill({
        body: assetFileSvg,
        contentType: 'image/svg+xml'
      });
      return;
    }

    if (path === '/api/assets/asset-1') {
      await route.fulfill({ json: assetDetailResponse });
      return;
    }

    if (path === '/api/assets') {
      await route.fulfill({ json: assetBrowseResponse });
      return;
    }

    if (path === '/api/settings/global-exclusion-patterns' && request.method() === 'GET') {
      await route.fulfill({ json: [] });
      return;
    }

    if (path === '/api/libraries' && request.method() === 'GET') {
      await route.fulfill({ json: libraryResponses(libraries) });
      return;
    }

    if (path === '/api/libraries' && request.method() === 'POST') {
      const body = await request.postDataJSON();
      libraries.set('library-1', {
        id: 'library-1',
        name: body.name,
        status: 'active',
        sources: [],
        exclusionPatterns: [
          {
            id: 'pattern-1',
            pattern: '**/@eaDir/**',
            createdAt: '2026-07-04T00:00:00Z'
          }
        ]
      });
      await route.fulfill({ status: 201, json: libraryResponses(libraries)[0] });
      return;
    }

    const addRootMatch = path.match(/^\/api\/libraries\/([^/]+)\/roots$/);
    if (addRootMatch && request.method() === 'POST') {
      const body = await request.postDataJSON();
      const library = libraries.get(addRootMatch[1]);
      if (!library) {
        await route.fulfill({ status: 404, json: {} });
        return;
      }
      library.sources.push({
        id: `source-${library.sources.length + 1}`,
        path: body.path,
        available: true,
        unavailableReason: null,
        createdAt: '2026-07-04T00:00:00Z'
      });
      await route.fulfill({ status: 201, json: libraryResponses(libraries).find((item) => item.id === library.id) });
      return;
    }

    const deleteRootMatch = path.match(/^\/api\/libraries\/([^/]+)\/roots\/([^/]+)$/);
    if (deleteRootMatch && request.method() === 'DELETE') {
      const library = libraries.get(deleteRootMatch[1]);
      if (library) {
        library.sources = library.sources.filter((source) => source.id !== deleteRootMatch[2]);
      }
      await route.fulfill({ status: 200, body: '' });
      return;
    }

    const archiveLibraryMatch = path.match(/^\/api\/libraries\/([^/]+)\/archive$/);
    if (archiveLibraryMatch && request.method() === 'POST') {
      const library = libraries.get(archiveLibraryMatch[1]);
      if (library) {
        library.status = 'archived';
      }
      await route.fulfill({ json: libraryResponses(libraries).find((item) => item.id === archiveLibraryMatch[1]) });
      return;
    }

    const restoreLibraryMatch = path.match(/^\/api\/libraries\/([^/]+)\/restore$/);
    if (restoreLibraryMatch && request.method() === 'POST') {
      const library = libraries.get(restoreLibraryMatch[1]);
      if (library) {
        library.status = 'active';
      }
      await route.fulfill({ json: libraryResponses(libraries).find((item) => item.id === restoreLibraryMatch[1]) });
      return;
    }

    const scanLibraryMatch = path.match(/^\/api\/libraries\/([^/]+)\/scans$/);
    if (scanLibraryMatch && request.method() === 'POST') {
      scanStatus = 'running';
      await route.fulfill({
        status: 202,
        json: scanResponse(scanLibraryMatch[1], null, 'running')
      });
      return;
    }

    const scanRootMatch = path.match(/^\/api\/libraries\/([^/]+)\/roots\/([^/]+)\/scans$/);
    if (scanRootMatch && request.method() === 'POST') {
      scanStatus = 'running';
      await route.fulfill({
        status: 202,
        json: scanResponse(scanRootMatch[1], scanRootMatch[2], 'running')
      });
      return;
    }

    if (path === '/api/scans/active') {
      await route.fulfill({
        json: scanStatus === 'running'
          ? [activeScanResponse('library-1', 'Family Photos', null, 'running')]
          : []
      });
      return;
    }

    const scanGetMatch = path.match(/^\/api\/scans\/([^/]+)$/);
    if (scanGetMatch && request.method() === 'GET') {
      await route.fulfill({ json: scanResponse('library-1', null, scanStatus) });
      return;
    }

    if (path === '/api/admin/users') {
      await route.fulfill({
        json: [
          {
            id: 'user-1',
            username: 'admin',
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
}

function libraryResponses(libraries: Map<string, {
  id: string;
  name: string;
  status: 'active' | 'archived';
  sources: {
      id: string;
      path: string;
      available: boolean;
      unavailableReason: string | null;
      createdAt: string;
    }[];
    exclusionPatterns: {
      id: string;
      pattern: string;
      createdAt: string;
    }[];
}>) {
  return [...libraries.values()].map((library) => {
    const availableSourceCount = library.sources.filter((source) => source.available).length;

    return {
      id: library.id,
      name: library.name,
      status: library.status,
      sourceCount: library.sources.length,
      availableSourceCount,
      unavailableSourceCount: library.sources.length - availableSourceCount,
      createdAt: '2026-07-04T00:00:00Z',
      updatedAt: '2026-07-04T00:00:00Z',
      archivedAt: library.status === 'archived' ? '2026-07-05T00:00:00Z' : null,
      sources: library.sources,
      exclusionPatterns: library.exclusionPatterns
    };
  });
}

function scanResponse(libraryId: string, rootId: string | null, status: 'running' | 'completed' = 'completed') {
  return {
    id: 'scan-1',
    libraryId,
    rootId,
    status,
    startedAt: '2026-07-04T00:00:00Z',
    completedAt: status === 'running' ? null : '2026-07-04T00:01:00Z',
    scannedFileCount: status === 'running' ? 3 : 1,
    addedCount: status === 'running' ? 2 : 1,
    unchangedCount: status === 'running' ? 1 : 0,
    movedCount: 0,
    modifiedCount: 0,
    duplicateCount: 0,
    missingCount: 0,
    reappearedCount: 0,
    errorCount: 0,
    errors: []
  };
}

function activeScanResponse(
  libraryId: string,
  libraryName: string,
  rootPath: string | null,
  status: 'running' | 'completed'
) {
  return {
    id: 'scan-1',
    libraryId,
    libraryName,
    rootId: null,
    rootPath,
    status,
    startedAt: '2026-07-04T00:00:00Z',
    scannedFileCount: 3,
    addedCount: 2,
    unchangedCount: 1,
    movedCount: 0,
    modifiedCount: 0,
    duplicateCount: 0,
    missingCount: 0,
    reappearedCount: 0,
    errorCount: 0
  };
}

async function completeOnboarding(page: Page) {
  await page.goto('/');
  await expect(page.getByRole('heading', { name: 'Create admin account' })).toBeVisible();

  await page.getByLabel('Username').fill('admin');
  await page.getByLabel('Password').fill('correct horse battery staple');
  await page.getByRole('button', { name: 'Create admin' }).click();

  await expect(page.getByRole('heading', { name: 'Libraries' })).toBeVisible();
}

async function completeBrowsableLibrarySetup(page: Page) {
  await completeOnboarding(page);
  await page.getByRole('button', { name: 'Configure sources' }).click();
  await page.getByLabel('Library name').fill('Family Photos');
  await page.getByRole('button', { name: 'Create' }).click();
  await page.getByRole('textbox', { name: 'Source path' }).fill('/photos/family');
  await page.getByRole('button', { name: 'Add source' }).click();
  await page.getByRole('button', { name: 'Libraries' }).click();
  await expect(page.getByRole('heading', { name: 'All folders' })).toBeVisible();
}
