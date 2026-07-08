import { expect, test, type Page } from '@playwright/test';

const apiBaseUrl = 'http://localhost:8080';

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
  contentHash: 'sha256:beach',
  mediaType: 'image/jpeg',
  availability: 'available',
  duplicateCount: 1,
  metadata: {
    capturedAt: '2026-07-04T00:00:00Z',
    width: 640,
    height: 480,
    fileExtension: 'jpg',
    mimeType: 'image/jpeg',
    extractionStatus: 'extracted',
    extractedAt: '2026-07-04T00:00:00Z',
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
          duplicateCount: 1,
          capturedAt: '2026-07-04T00:00:00Z',
          observedAt: '2026-07-04T00:00:00Z',
          mediaType: 'image/jpeg',
          mimeType: 'image/jpeg',
          width: 640,
          height: 480,
          previewable: true
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
  libraryRootAssetCounts: {}
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

  await expect(page.getByRole('button', { name: 'Browse' })).toBeVisible();
  await expect(page.getByText(/Docker sources must use container paths/)).toBeVisible();
  await page.getByLabel('Source path').fill('/photos/family');
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
  await expect(page.getByRole('button', { name: 'Close' })).toBeVisible();
  await expect(page.getByText('/photos/family/beach.jpg').first()).toBeVisible();
  await page.getByRole('button', { name: 'Close' }).click();

  await page.getByRole('navigation', { name: 'Utilities' }).getByRole('button', { name: 'Settings' }).click();
  await page.getByRole('navigation', { name: 'Settings' }).getByRole('button', { name: 'Plugins' }).click();
  await expect(page.getByRole('heading', { name: 'Plugins' })).toBeVisible();
  await page.getByRole('navigation', { name: 'Settings' }).getByRole('button', { name: 'Backups' }).click();
  await expect(page.getByRole('heading', { name: 'Backups' })).toBeVisible();

  await page.getByRole('button', { name: 'Profile' }).click();
  await page.getByRole('menuitem', { name: 'Log out' }).click();
  await expect(page.getByRole('heading', { name: 'Sign in' })).toBeVisible();
});

test('authenticated shell visual regression @visual', async ({ page }) => {
  await mockPixiergeApi(page);
  await completeBrowsableLibrarySetup(page);

  await expect(page.getByRole('navigation', { name: 'Folders' })).toBeVisible();
  await expect(page.getByRole('button', { name: 'Open beach.jpg' })).toBeVisible();
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

  await page.route(`${apiBaseUrl}/api/**`, async (route) => {
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

    if (path === '/api/assets/asset-1') {
      await route.fulfill({ json: assetDetailResponse });
      return;
    }

    if (path === '/api/assets') {
      await route.fulfill({ json: assetBrowseResponse });
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
      await route.fulfill({
        status: 201,
        json: scanResponse(scanLibraryMatch[1], null)
      });
      return;
    }

    const scanRootMatch = path.match(/^\/api\/libraries\/([^/]+)\/roots\/([^/]+)\/scans$/);
    if (scanRootMatch && request.method() === 'POST') {
      await route.fulfill({
        status: 201,
        json: scanResponse(scanRootMatch[1], scanRootMatch[2])
      });
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

function scanResponse(libraryId: string, rootId: string | null) {
  return {
    id: 'scan-1',
    libraryId,
    rootId,
    status: 'completed',
    startedAt: '2026-07-04T00:00:00Z',
    completedAt: '2026-07-04T00:01:00Z',
    scannedFileCount: 1,
    addedCount: 1,
    unchangedCount: 0,
    movedCount: 0,
    modifiedCount: 0,
    duplicateCount: 0,
    missingCount: 0,
    reappearedCount: 0,
    errorCount: 0,
    errors: []
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
  await page.getByLabel('Source path').fill('/photos/family');
  await page.getByRole('button', { name: 'Add source' }).click();
  await page.getByRole('button', { name: 'Libraries' }).click();
  await expect(page.getByRole('heading', { name: 'All folders' })).toBeVisible();
}
