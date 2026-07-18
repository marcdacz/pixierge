import '@testing-library/jest-dom/vitest';
import { act, cleanup, fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { App } from './App';

type MockResponse = {
  status: number;
  body?: unknown;
};

const authBody = {
  csrfToken: 'csrf-token',
  user: {
    id: 'user-1',
    username: 'admin',
    roles: ['ADMIN'],
    permissions: ['identity:admin', 'identity:read', 'library:admin', 'library:read']
  }
};

const configuredLibraries = [
  {
    id: 'library-1',
    name: 'Family Photos',
    status: 'active',
    sourceCount: 3,
    availableSourceCount: 2,
    unavailableSourceCount: 1,
    createdAt: '2026-07-04T00:00:00Z',
    updatedAt: '2026-07-04T00:00:00Z',
    archivedAt: null,
    sources: [
      {
        id: 'source-1',
        path: '/photos/family',
        available: true,
        unavailableReason: null,
        createdAt: '2026-07-04T00:00:00Z'
      },
      {
        id: 'source-2',
        path: '/photos/scans',
        available: false,
        unavailableReason: 'missing',
        createdAt: '2026-07-04T00:00:00Z'
      }
    ],
    exclusionPatterns: []
  }
];

const globalExclusionPatterns = [
  {
    id: 'global-pattern-1',
    pattern: '**/@eaDir/**',
    createdAt: '2026-07-04T00:00:00Z'
  },
  {
    id: 'global-pattern-2',
    pattern: '**/#recycle/**',
    createdAt: '2026-07-04T00:00:00Z'
  }
];

const completedScan = {
  id: 'scan-1',
  libraryId: 'library-1',
  rootId: null,
  status: 'completed',
  startedAt: '2026-07-04T00:00:00Z',
  completedAt: '2026-07-04T00:01:00Z',
  scannedFileCount: 3,
  addedCount: 2,
  unchangedCount: 1,
  movedCount: 0,
  modifiedCount: 0,
  duplicateCount: 0,
  missingCount: 0,
  reappearedCount: 0,
  errorCount: 0,
  errors: []
};

const runningScan = {
  ...completedScan,
  status: 'running',
  completedAt: null,
  scannedFileCount: 0,
  addedCount: 0,
  unchangedCount: 0
};

const backgroundWorkHealth = {
  queues: [
    {
      jobType: 'library-catalog-root',
      status: 'pending',
      count: 2,
      oldestCreatedAt: '2026-07-04T00:00:00Z',
      oldestNextRunAt: '2026-07-04T00:00:00Z',
      latestUpdatedAt: '2026-07-04T00:02:00Z'
    },
    {
      jobType: 'filesystem-change-event',
      status: 'dead_letter',
      count: 1,
      oldestCreatedAt: '2026-07-04T00:03:00Z',
      oldestNextRunAt: '2026-07-04T00:03:00Z',
      latestUpdatedAt: '2026-07-04T00:04:00Z'
    }
  ],
  recentProblems: [
    {
      id: 'job-1',
      jobType: 'filesystem-change-event',
      status: 'dead_letter',
      attempts: 3,
      maxAttempts: 3,
      lastErrorCode: 'watcher_overflow',
      lastErrorMessage: 'Watcher overflow under /photos',
      updatedAt: '2026-07-04T00:04:00Z',
      completedAt: '2026-07-04T00:04:00Z'
    }
  ],
  watcher: {
    status: 'degraded',
    lastErrorCode: 'watcher_overflow',
    lastErrorMessage: 'Filesystem watcher overflow under /photos',
    lastErrorAt: '2026-07-04T00:04:00Z',
    lastOverflowAt: '2026-07-04T00:04:00Z',
    lastRegistrationRefreshAt: '2026-07-04T00:05:00Z',
    registeredRootCount: 1,
    registeredDirectoryCount: 42
  }
};

const libraryTree = {
  roots: [
    {
      id: 'library-1:/photos',
      libraryId: 'library-1',
      libraryName: 'Family Photos',
      path: '/photos',
      name: 'photos',
      assetCount: 2,
      childCount: 1,
      children: [
        {
          id: 'library-1:/photos/family',
          libraryId: 'library-1',
          libraryName: 'Family Photos',
          path: '/photos/family',
          name: 'family',
          assetCount: 2,
          childCount: 0,
          children: []
        }
      ]
    }
  ],
  libraryRootAssetCounts: {},
  libraryAssetCounts: {}
};

const browseAssets = {
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
          duplicateCount: 2,
          capturedAt: '2026-07-04T00:00:00Z',
          observedAt: '2026-07-04T00:00:00Z',
          mediaType: 'image/jpeg',
          mimeType: 'image/jpeg',
          width: 1200,
          height: 800,
          previewable: true,
          thumbnailStatus: 'ready',
          thumbnailCacheKey: 'grid-cache-asset-1',
          thumbnailPlaceholder: 'linear-gradient(135deg, rgb(120, 130, 140), rgb(90, 100, 110) 52%, rgb(50, 60, 70))',
          starred: false
        }
      ]
    }
  ],
  totalCount: 1,
  page: 0,
  pageSize: 48,
  hasNext: false
};

const assetDetail = {
  id: 'asset-1',
  contentHash: 'sha256:aaa',
  identityStatus: 'confirmed',
  mediaType: 'image/jpeg',
  availability: 'available',
  duplicateCount: 2,
  metadata: {
    capturedAt: '2026-07-04T00:00:00Z',
    width: 1200,
    height: 800,
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
  ],
  tags: []
};

describe('App', () => {
  beforeEach(() => {
    vi.restoreAllMocks();
    window.history.replaceState(null, '', '/');
    window.localStorage.clear();
  });

  afterEach(() => {
    cleanup();
    vi.unstubAllGlobals();
    window.localStorage.clear();
  });

  it('shows first-admin setup when no users exist', async () => {
    mockFetch([
      { status: 200, body: { required: true } }
    ]);

    render(<App />);

    expect(await screen.findByRole('heading', { name: 'Create admin account' })).toBeInTheDocument();
    expect(screen.queryByRole('navigation', { name: 'Primary' })).not.toBeInTheDocument();
  });

  it('creates the first admin and opens the empty library', async () => {
    const fetchMock = mockFetch([
      { status: 200, body: { required: true } },
      { status: 200, body: authBody },
      { status: 200, body: [] }
    ]);

    render(<App />);

    await userEvent.type(await screen.findByLabelText('Username'), 'admin');
    await userEvent.type(screen.getByLabelText('Password'), 'correct horse battery staple');
    await userEvent.click(screen.getByRole('button', { name: 'Create admin' }));

    expect(await screen.findByRole('heading', { name: 'Libraries' })).toBeInTheDocument();
    expect(await screen.findByText('No library sources have been added yet.')).toBeInTheDocument();
    expect(fetchMock).toHaveBeenCalledWith(
      'http://localhost:8080/api/setup/admin',
      expect.objectContaining({ credentials: 'include', method: 'POST' })
    );
  });

  it('signs in and sends the csrf token on logout', async () => {
    const fetchMock = mockFetch([
      { status: 200, body: { required: false } },
      { status: 401, body: {} },
      { status: 200, body: authBody },
      { status: 200, body: [] },
      { status: 200 }
    ]);

    render(<App />);

    await userEvent.type(await screen.findByLabelText('Username'), 'admin');
    await userEvent.type(screen.getByLabelText('Password'), 'correct horse battery staple');
    await userEvent.click(screen.getByRole('button', { name: 'Sign in' }));

    expect(await screen.findByRole('heading', { name: 'Libraries' })).toBeInTheDocument();
    expect(await screen.findByText('No library sources have been added yet.')).toBeInTheDocument();

    await userEvent.click(screen.getByRole('button', { name: 'Profile' }));
    await userEvent.click(await screen.findByRole('menuitem', { name: 'Log out' }));

    expect(await screen.findByRole('heading', { name: 'Sign in' })).toBeInTheDocument();
    expect(fetchMock).toHaveBeenLastCalledWith(
      'http://localhost:8080/api/auth/logout',
      expect.objectContaining({
        credentials: 'include',
        headers: expect.objectContaining({ 'X-Pixierge-Csrf': 'csrf-token' }),
        method: 'POST'
      })
    );
  });

  it('routes the empty library CTA to source configuration', async () => {
    mockFetch([
      { status: 200, body: { required: false } },
      { status: 200, body: authBody },
      { status: 200, body: [] },
      { status: 200, body: globalExclusionPatterns }
    ]);

    render(<App />);

    await userEvent.click(await screen.findByRole('button', { name: 'Configure sources' }));

    expect(await screen.findByRole('heading', { name: 'Settings' })).toBeInTheDocument();
    expect(screen.getByRole('heading', { name: 'Configuration' })).toBeInTheDocument();
    expect(screen.getByLabelText('Library name')).toBeInTheDocument();
  });

  it('shows background work health in settings', async () => {
    const fetchMock = mockFetch([
      { status: 200, body: { required: false } },
      { status: 200, body: authBody },
      { status: 200, body: configuredLibraries },
      { status: 200, body: libraryTree },
      { status: 200, body: browseAssets },
      { status: 200, body: globalExclusionPatterns },
      { status: 200, body: backgroundWorkHealth }
    ]);

    render(<App />);

    await screen.findByRole('heading', { name: 'All folders', level: 2 });
    await userEvent.click(
      within(screen.getByRole('navigation', { name: 'Utilities' })).getByRole('button', { name: 'Settings' })
    );
    await userEvent.click(screen.getByRole('button', { name: 'Background work' }));

    expect(await screen.findByRole('heading', { name: 'Background work' })).toBeInTheDocument();
    expect(screen.getByRole('heading', { name: 'Queue health' })).toBeInTheDocument();
    expect(screen.getByText('library-catalog-root')).toBeInTheDocument();
    expect(screen.getAllByText('dead letter')).not.toHaveLength(0);
    expect(screen.getByText(/Filesystem watcher overflow under \/photos/)).toBeInTheDocument();
    expect(screen.getByText('42')).toBeInTheDocument();
    expect(fetchMock).toHaveBeenCalledWith(
      'http://localhost:8080/api/admin/background/health',
      expect.objectContaining({ credentials: 'include', method: 'GET' })
    );
  });

  it('opens the search page when searching from another view', async () => {
    const fetchMock = mockFetch([
      { status: 200, body: { required: false } },
      { status: 200, body: authBody },
      { status: 200, body: configuredLibraries },
      { status: 200, body: libraryTree },
      { status: 200, body: browseAssets },
      { status: 200, body: globalExclusionPatterns },
      { status: 200, body: browseAssets }
    ]);

    render(<App />);

    expect(await screen.findByRole('heading', { name: 'All folders' })).toBeInTheDocument();
    const search = screen.getByRole('textbox', { name: 'Search' });
    expect(search).toHaveAttribute('placeholder', 'Search');

    await userEvent.click(
      within(screen.getByRole('navigation', { name: 'Utilities' })).getByRole('button', { name: 'Settings' })
    );
    expect(await screen.findByRole('heading', { name: 'Settings' })).toBeInTheDocument();

    await userEvent.type(screen.getByRole('textbox', { name: 'Search' }), 'beach');

    expect(await screen.findByRole('heading', { name: 'Search results' })).toBeInTheDocument();
    expect(await screen.findByRole('navigation', { name: 'Filters' })).toBeInTheDocument();
    await waitFor(() => {
      expect(fetchMock).toHaveBeenCalledWith(
        'http://localhost:8080/api/assets?q=beach&page=0&pageSize=48',
        expect.objectContaining({ credentials: 'include', method: 'GET' })
      );
    });
    expect(window.location.search).toBe('?q=beach');
  });

  it('clears the search bar when leaving search and restores it on return', async () => {
    mockFetch([
      { status: 200, body: { required: false } },
      { status: 200, body: authBody },
      { status: 200, body: configuredLibraries },
      { status: 200, body: libraryTree },
      { status: 200, body: browseAssets },
      { status: 200, body: browseAssets },
      { status: 200, body: libraryTree },
      { status: 200, body: browseAssets },
      { status: 200, body: browseAssets }
    ]);

    render(<App />);

    await userEvent.type(await screen.findByRole('textbox', { name: 'Search' }), 'beach');
    expect(await screen.findByRole('heading', { name: 'Search results' })).toBeInTheDocument();
    expect(screen.getByRole('textbox', { name: 'Search' })).toHaveValue('beach');

    await userEvent.click(screen.getByRole('button', { name: 'Libraries' }));
    expect(await screen.findByRole('heading', { name: 'All folders' })).toBeInTheDocument();
    expect(screen.getByRole('textbox', { name: 'Search' })).toHaveValue('');
    expect(window.location.search).toBe('');

    await userEvent.click(screen.getByRole('button', { name: 'Search' }));
    expect(await screen.findByRole('heading', { name: 'Search results' })).toBeInTheDocument();
    expect(screen.getByRole('textbox', { name: 'Search' })).toHaveValue('beach');
    expect(window.location.search).toBe('?q=beach');
  });
  it('shows library folders in the browse tree and configuration health totals', async () => {
    mockFetch([
      { status: 200, body: { required: false } },
      { status: 200, body: authBody },
      { status: 200, body: configuredLibraries },
      { status: 200, body: libraryTree },
      { status: 200, body: browseAssets },
      { status: 200, body: globalExclusionPatterns }
    ]);

    render(<App />);

    const foldersNav = await screen.findByRole('navigation', { name: 'Folders' });
    expect(await within(foldersNav).findByRole('button', { name: /^Family Photos/ })).toBeInTheDocument();
    expect(await within(foldersNav).findByRole('button', { name: /^family/ })).toBeInTheDocument();

    await userEvent.click(await screen.findByRole('button', { name: 'Expand navigation' }));
    const primaryNav = screen.getByRole('navigation', { name: 'Primary' });
    expect(within(primaryNav).queryByText('Family Photos')).not.toBeInTheDocument();
    expect(within(primaryNav).queryByText('3 sources')).not.toBeInTheDocument();

    await userEvent.click(
      within(screen.getByRole('navigation', { name: 'Utilities' })).getByRole('button', { name: 'Settings' })
    );

    expect(await screen.findAllByText('2 available')).not.toHaveLength(0);
    expect(screen.getAllByText('1 unavailable')).not.toHaveLength(0);
    expect(screen.getByText('Unavailable: missing')).toBeInTheDocument();
  });

  it('defaults to all folders and applies recursive folder filtering when selected', async () => {
    const fetchMock = mockFetch([
      { status: 200, body: { required: false } },
      { status: 200, body: authBody },
      { status: 200, body: configuredLibraries },
      { status: 200, body: libraryTree },
      { status: 200, body: browseAssets },
      { status: 200, body: browseAssets },
      { status: 200, body: browseAssets },
      { status: 200, body: assetDetail }
    ]);

    render(<App />);

    expect(await screen.findByRole('navigation', { name: 'Folders' })).toBeInTheDocument();
    expect(await screen.findByRole('heading', { name: 'All folders' })).toBeInTheDocument();
    expect(await screen.findAllByText('1 item')).not.toHaveLength(0);
    expect(await screen.findByRole('button', { name: 'Select beach.jpg' })).toBeInTheDocument();
    expect(document.querySelector('[data-asset-placeholder="asset-1"]')).not.toBeNull();
    const assetGrid = document.querySelector('[aria-label="Asset grid"]') as HTMLElement;
    expect(assetGrid.style.getPropertyValue('--asset-grid-tile-size')).toBe('11rem');
    expect(
      document.querySelector('img[src="http://localhost:8080/api/assets/asset-1/thumbnail?c=grid-cache-asset-1"]')
    ).not.toBeNull();
    const thumbnailSize = screen.getByRole('slider', { name: 'Thumbnail size' });
    fireEvent.change(thumbnailSize, { target: { value: '3' } });
    expect(assetGrid.style.getPropertyValue('--asset-grid-tile-size')).toBe(
      'calc((100% - 2 * 0.25rem) / 3)'
    );
    expect(
      document.querySelector('img[src="http://localhost:8080/api/assets/asset-1/preview?c=grid-cache-asset-1"]')
    ).not.toBeNull();
    fireEvent.change(thumbnailSize, { target: { value: '4' } });
    expect(assetGrid.style.getPropertyValue('--asset-grid-tile-size')).toBe(
      'calc((100% - 0.25rem) / 2)'
    );
    fireEvent.change(thumbnailSize, { target: { value: '5' } });
    expect(assetGrid.style.getPropertyValue('--asset-grid-tile-size')).toBe('100%');
    expect(thumbnailSize).toHaveAttribute('aria-valuenow', '5');
    expect(window.localStorage.getItem('pixierge.assetTileSizeIndex')).toBe('5');
    fireEvent.change(thumbnailSize, { target: { value: '0' } });
    expect(assetGrid.style.getPropertyValue('--asset-grid-tile-size')).toBe('5.5rem');
    expect(thumbnailSize).toHaveAttribute('aria-valuenow', '0');
    expect(window.localStorage.getItem('pixierge.assetTileSizeIndex')).toBe('0');
    expect(
      document.querySelector('img[src="http://localhost:8080/api/assets/asset-1/thumbnail?c=grid-cache-asset-1"]')
    ).not.toBeNull();
    expect(fetchMock).toHaveBeenCalledWith(
      'http://localhost:8080/api/assets?libraryId=library-1&page=0&pageSize=48',
      expect.objectContaining({ credentials: 'include', method: 'GET' })
    );

    await userEvent.click(screen.getByRole('button', { name: /^family/ }));
    expect(await screen.findByRole('heading', { name: 'family' })).toBeInTheDocument();

    await userEvent.click(screen.getByRole('button', { name: 'Hide folders' }));
    expect(screen.queryByRole('navigation', { name: 'Folders' })).not.toBeInTheDocument();
    await userEvent.click(screen.getByRole('button', { name: 'Show folders' }));
    expect(await screen.findByRole('navigation', { name: 'Folders' })).toBeInTheDocument();

    await userEvent.type(screen.getByRole('textbox', { name: 'Search' }), 'beach');
    expect(await screen.findByRole('heading', { name: 'Search results' })).toBeInTheDocument();
    await waitFor(() => {
      expect(fetchMock).toHaveBeenCalledWith(
        'http://localhost:8080/api/assets?q=beach&page=0&pageSize=48',
        expect.objectContaining({ credentials: 'include', method: 'GET' })
      );
    });

    await userEvent.dblClick(await screen.findByRole('button', { name: 'Select beach.jpg' }));
    expect(await screen.findByRole('button', { name: 'Close photo viewer' })).toBeInTheDocument();
    expect(
      document.querySelector('img[src="http://localhost:8080/api/assets/asset-1/preview?c=grid-cache-asset-1"]')
    ).not.toBeNull();
    expect(screen.queryByRole('navigation', { name: 'Folders' })).not.toBeInTheDocument();
    expect(screen.queryByText('/photos/family/beach.jpg')).not.toBeInTheDocument();
    await userEvent.click(screen.getByRole('button', { name: 'Show photo metadata' }));
    expect(await screen.findAllByText('/photos/family/beach.jpg')).not.toHaveLength(0);
    await userEvent.click(screen.getByRole('button', { name: 'Dismiss photo metadata' }));
    expect(screen.queryByText('Identity')).not.toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Close photo viewer' })).toBeInTheDocument();
    await userEvent.keyboard('{Escape}');
    expect(await screen.findByRole('heading', { name: 'Search results' })).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Close photo viewer' })).not.toBeInTheDocument();
    expect(await screen.findByRole('navigation', { name: 'Filters' })).toBeInTheDocument();
    expect(fetchMock).toHaveBeenCalledWith(
      'http://localhost:8080/api/assets/asset-1',
      expect.objectContaining({ credentials: 'include', method: 'GET' })
    );
  });

  it('keeps the generated placeholder when a thumbnail fails', async () => {
    mockFetch([
      { status: 200, body: { required: false } },
      { status: 200, body: authBody },
      { status: 200, body: configuredLibraries },
      { status: 200, body: libraryTree },
      { status: 200, body: browseAssets }
    ]);

    render(<App />);

    await screen.findByRole('button', { name: 'Select beach.jpg' });
    const thumbnailSelector = 'img[src="http://localhost:8080/api/assets/asset-1/thumbnail?c=grid-cache-asset-1"]';
    const thumbnail = document.querySelector(thumbnailSelector) as HTMLImageElement;
    expect(thumbnail).toHaveClass('opacity-0');
    fireEvent.load(thumbnail);
    await waitFor(() => expect(thumbnail).toHaveClass('opacity-100'));
    fireEvent.error(thumbnail);

    await waitFor(() => expect(document.querySelector(thumbnailSelector)).toBeNull());
    expect(document.querySelector('[data-asset-placeholder="asset-1"]')).not.toBeNull();
  });

  it('does not request stable thumbnails while asset identity is pending', async () => {
    const pendingBrowse = {
      ...browseAssets,
      sections: [
        {
          ...browseAssets.sections[0],
          assets: [
            {
              ...browseAssets.sections[0].assets[0],
              identityStatus: 'pending',
              thumbnailStatus: 'pending',
              thumbnailCacheKey: null,
              thumbnailPlaceholder: null
            }
          ]
        }
      ]
    };
    mockFetch([
      { status: 200, body: { required: false } },
      { status: 200, body: authBody },
      { status: 200, body: configuredLibraries },
      { status: 200, body: libraryTree },
      { status: 200, body: pendingBrowse }
    ]);

    render(<App />);

    await screen.findByRole('button', { name: 'Select beach.jpg' });
    expect(document.querySelector('img[src*="/api/assets/asset-1/thumbnail"]')).toBeNull();
    expect(screen.getByText('Identity pending')).toBeInTheDocument();
    expect(document.querySelector('[data-asset-placeholder="asset-1"]')).not.toBeNull();
  });

  it('navigates photos in the viewer and returns to the last viewed asset', async () => {
    const twoAssetBrowse = {
      ...browseAssets,
      totalCount: 2,
      sections: [
        {
          ...browseAssets.sections[0],
          assets: [
            browseAssets.sections[0].assets[0],
            {
              ...browseAssets.sections[0].assets[0],
              id: 'asset-2',
              fileName: 'sunset.jpg',
              displayPath: '/photos/family/sunset.jpg'
            }
          ]
        }
      ]
    };
    const sunsetDetail = {
      ...assetDetail,
      id: 'asset-2',
      files: [
        {
          ...assetDetail.files[0],
          id: 'file-2',
          path: '/photos/family/sunset.jpg',
          fileName: 'sunset.jpg'
        }
      ]
    };
    const scrollIntoView = vi.fn();
    Object.defineProperty(HTMLElement.prototype, 'scrollIntoView', {
      configurable: true,
      value: scrollIntoView
    });

    mockFetch([
      { status: 200, body: { required: false } },
      { status: 200, body: authBody },
      { status: 200, body: configuredLibraries },
      { status: 200, body: libraryTree },
      { status: 200, body: twoAssetBrowse },
      { status: 200, body: assetDetail },
      { status: 200, body: sunsetDetail },
      { status: 200, body: assetDetail },
      { status: 200, body: sunsetDetail }
    ]);

    render(<App />);

    await userEvent.dblClick(await screen.findByRole('button', { name: 'Select beach.jpg' }));
    expect(await screen.findByRole('button', { name: 'Next photo' })).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Previous photo' })).not.toBeInTheDocument();

    await userEvent.click(screen.getByRole('button', { name: 'Next photo' }));
    await waitFor(() => {
      expect(
        document.querySelector('img[src="http://localhost:8080/api/assets/asset-2/preview?c=grid-cache-asset-1"]')
      ).not.toBeNull();
    });
    expect(screen.getByRole('button', { name: 'Previous photo' })).toBeInTheDocument();

    await userEvent.keyboard('{ArrowLeft}');
    await waitFor(() => {
      expect(
        document.querySelector('img[src="http://localhost:8080/api/assets/asset-1/preview?c=grid-cache-asset-1"]')
      ).not.toBeNull();
    });

    await userEvent.keyboard('{ArrowRight}');
    await waitFor(() => {
      expect(
        document.querySelector('img[src="http://localhost:8080/api/assets/asset-2/preview?c=grid-cache-asset-1"]')
      ).not.toBeNull();
    });

    await userEvent.keyboard('{Escape}');
    expect(await screen.findByRole('button', { name: 'Select sunset.jpg' })).toBeInTheDocument();
    await waitFor(() => {
      expect(scrollIntoView).toHaveBeenCalled();
    });
    const sunsetTile = document.querySelector('[data-asset-id="asset-2"]');
    expect(scrollIntoView.mock.instances[0]).toBe(sunsetTile);
  });

  it('loads the next browse page when the asset grid reaches the scroll end', async () => {
    const pageZeroAssets = {
      ...browseAssets,
      totalCount: 2,
      hasNext: true
    };
    const pageOneAssets = {
      sections: [
        {
          folderPath: '/photos/family',
          folderName: 'family',
          assets: [
            {
              ...browseAssets.sections[0].assets[0],
              id: 'asset-2',
              fileName: 'sunset.jpg',
              displayPath: '/photos/family/sunset.jpg'
            }
          ]
        }
      ],
      totalCount: 2,
      page: 1,
      pageSize: 48,
      hasNext: false
    };

    const fetchMock = mockFetch([
      { status: 200, body: { required: false } },
      { status: 200, body: authBody },
      { status: 200, body: configuredLibraries },
      { status: 200, body: libraryTree },
      { status: 200, body: pageZeroAssets },
      { status: 200, body: pageOneAssets }
    ]);

    const intersectionObserver = installIntersectionObserverMock();

    render(<App />);

    expect(await screen.findByText('2 items')).toBeInTheDocument();
    expect(await screen.findByRole('button', { name: 'Select beach.jpg' })).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Select sunset.jpg' })).not.toBeInTheDocument();

    act(() => {
      intersectionObserver.trigger();
    });

    await waitFor(() => {
      expect(fetchMock).toHaveBeenCalledWith(
        'http://localhost:8080/api/assets?libraryId=library-1&page=1&pageSize=48',
        expect.objectContaining({ credentials: 'include', method: 'GET' })
      );
    });

    expect(await screen.findByRole('button', { name: 'Select sunset.jpg' })).toBeInTheDocument();
    intersectionObserver.restore();
  });

  it('does not request another browse page after a page merge unless the scroll end is reached again', async () => {
    const pageZeroAssets = {
      ...browseAssets,
      totalCount: 2,
      hasNext: true
    };
    const pageOneAssets = {
      sections: [
        {
          folderPath: '/photos/family',
          folderName: 'family',
          assets: [
            {
              ...browseAssets.sections[0].assets[0],
              id: 'asset-2',
              fileName: 'sunset.jpg',
              displayPath: '/photos/family/sunset.jpg'
            }
          ]
        }
      ],
      totalCount: 2,
      page: 1,
      pageSize: 48,
      hasNext: false
    };

    const fetchMock = mockFetch([
      { status: 200, body: { required: false } },
      { status: 200, body: authBody },
      { status: 200, body: configuredLibraries },
      { status: 200, body: libraryTree },
      { status: 200, body: pageZeroAssets },
      { status: 200, body: pageOneAssets }
    ]);

    const intersectionObserver = installIntersectionObserverMock();

    render(<App />);

    expect(await screen.findByText('2 items')).toBeInTheDocument();

    act(() => {
      intersectionObserver.trigger();
    });

    await waitFor(() => {
      expect(fetchMock).toHaveBeenCalledWith(
        'http://localhost:8080/api/assets?libraryId=library-1&page=1&pageSize=48',
        expect.objectContaining({ credentials: 'include', method: 'GET' })
      );
    });

    expect(await screen.findByRole('button', { name: 'Select sunset.jpg' })).toBeInTheDocument();

    const browseCallsAfterMerge = fetchMock.mock.calls.filter(([url]) =>
      String(url).includes('/api/assets?libraryId=library-1')
    ).length;
    expect(browseCallsAfterMerge).toBe(2);

    act(() => {
      intersectionObserver.trigger();
    });

    await new Promise((resolve) => setTimeout(resolve, 50));
    expect(
      fetchMock.mock.calls.filter(([url]) => String(url).includes('/api/assets?libraryId=library-1')).length
    ).toBe(2);

    intersectionObserver.restore();
  });

  it('creates libraries and adds sources from configuration', async () => {
    const fetchMock = mockFetch([
      { status: 200, body: { required: false } },
      { status: 200, body: authBody },
      { status: 200, body: [] },
      { status: 200, body: globalExclusionPatterns },
      { status: 201, body: { ...configuredLibraries[0], sourceCount: 0, availableSourceCount: 0, unavailableSourceCount: 0, sources: [] } },
      { status: 200, body: [{ ...configuredLibraries[0], sourceCount: 0, availableSourceCount: 0, unavailableSourceCount: 0, sources: [] }] },
      { status: 201, body: configuredLibraries[0] },
      { status: 200, body: configuredLibraries }
    ]);

    render(<App />);

    await userEvent.click(await screen.findByRole('button', { name: 'Configure sources' }));
    await userEvent.type(screen.getByLabelText('Library name'), 'Family Photos');
    await userEvent.click(screen.getByRole('button', { name: 'Create library' }));

    expect(await screen.findAllByText('0 sources')).not.toHaveLength(0);

    await userEvent.type(screen.getByLabelText('Source path'), '/photos/family');
    await userEvent.hover(screen.getByRole('button', { name: 'Source path Docker guidance' }));
    expect(await screen.findAllByText(/Docker sources must use container paths/)).not.toHaveLength(0);

    await userEvent.click(screen.getByRole('button', { name: 'Add source' }));
    expect(await screen.findByText('/photos/family')).toBeInTheDocument();
    expect(await screen.findByText('Library settings changed.')).toBeInTheDocument();
    expect(fetchMock).toHaveBeenCalledWith(
      'http://localhost:8080/api/libraries',
      expect.objectContaining({
        headers: expect.objectContaining({ 'X-Pixierge-Csrf': 'csrf-token' }),
        method: 'POST'
      })
    );
    expect(fetchMock).toHaveBeenCalledWith(
      'http://localhost:8080/api/libraries/library-1/roots',
      expect.objectContaining({
        headers: expect.objectContaining({ 'X-Pixierge-Csrf': 'csrf-token' }),
        method: 'POST'
      })
    );
  });

  it('shows a toast when a source path is rejected', async () => {
    const toastDismissCallbacks: Array<() => void> = [];
    const originalSetTimeout = globalThis.setTimeout;
    vi.spyOn(globalThis, 'setTimeout').mockImplementation((callback, delay, ...args) => {
      if (delay === 15_000 && typeof callback === 'function') {
        toastDismissCallbacks.push(callback as () => void);
        return 1 as unknown as ReturnType<typeof setTimeout>;
      }
      return originalSetTimeout(callback, delay, ...args);
    });
    mockFetch([
      { status: 200, body: { required: false } },
      { status: 200, body: authBody },
      { status: 200, body: configuredLibraries },
      { status: 200, body: libraryTree },
      { status: 200, body: browseAssets },
      { status: 200, body: globalExclusionPatterns },
      { status: 400, body: { title: 'Bad Request' } }
    ]);

    render(<App />);

    await screen.findByRole('heading', { name: 'All folders', level: 2 });
    await userEvent.click(
      within(screen.getByRole('navigation', { name: 'Utilities' })).getByRole('button', { name: 'Settings' })
    );

    await userEvent.type(screen.getByLabelText('Source path'), '/etc');
    await userEvent.click(screen.getByRole('button', { name: 'Add source' }));

    const toast = await screen.findByRole('alert');
    expect(toast).toHaveTextContent('Source path could not be added');
    expect(toast).toHaveTextContent('Enter an absolute path to an existing readable directory mounted into Pixierge.');
    expect(toast).not.toHaveTextContent('Bad Request');
    expect(toastDismissCallbacks).toHaveLength(1);

    act(() => {
      toastDismissCallbacks[0]();
    });
    await waitFor(() => expect(screen.queryByRole('alert')).not.toBeInTheDocument());
  });

  it('adds and removes global exclusions from configuration', async () => {
    const updatedGlobalExclusions = [
      ...globalExclusionPatterns,
      {
        id: 'global-pattern-3',
        pattern: '**/.cache/**',
        createdAt: '2026-07-04T00:00:00Z'
      }
    ];
    const fetchMock = mockFetch([
      { status: 200, body: { required: false } },
      { status: 200, body: authBody },
      { status: 200, body: configuredLibraries },
      { status: 200, body: libraryTree },
      { status: 200, body: browseAssets },
      { status: 200, body: globalExclusionPatterns },
      { status: 201, body: updatedGlobalExclusions[2] },
      { status: 200, body: updatedGlobalExclusions },
      { status: 204 },
      { status: 200, body: globalExclusionPatterns }
    ]);

    render(<App />);

    await screen.findByRole('heading', { name: 'All folders', level: 2 });
    await userEvent.click(
      within(screen.getByRole('navigation', { name: 'Utilities' })).getByRole('button', { name: 'Settings' })
    );
    await userEvent.click(await screen.findByRole('button', { name: /Global exclusions/ }));

    expect(await screen.findByText('**/@eaDir/**')).toBeInTheDocument();
    await userEvent.type(screen.getByLabelText('Exclusion pattern'), '**/.cache/**');
    await userEvent.click(screen.getByRole('button', { name: 'Add exclusion' }));

    expect(await screen.findByText('**/.cache/**')).toBeInTheDocument();
    await userEvent.click(screen.getByRole('button', { name: 'Remove global exclusion **/.cache/**' }));
    await waitFor(() => expect(screen.queryByText('**/.cache/**')).not.toBeInTheDocument());
    expect(fetchMock).toHaveBeenCalledWith(
      'http://localhost:8080/api/settings/global-exclusion-patterns',
      expect.objectContaining({
        headers: expect.objectContaining({ 'X-Pixierge-Csrf': 'csrf-token' }),
        method: 'POST'
      })
    );
    expect(fetchMock).toHaveBeenCalledWith(
      'http://localhost:8080/api/settings/global-exclusion-patterns/global-pattern-3',
      expect.objectContaining({
        headers: expect.objectContaining({ 'X-Pixierge-Csrf': 'csrf-token' }),
        method: 'DELETE'
      })
    );
  });

  it('adds exclusions and can run a manual scan from configuration', async () => {
    const libraryWithCustomExclusion = {
      ...configuredLibraries[0],
      exclusionPatterns: [
        ...configuredLibraries[0].exclusionPatterns,
        {
          id: 'pattern-2',
          pattern: '**/.cache/**',
          createdAt: '2026-07-04T00:00:00Z'
        }
      ]
    };
    const fetchMock = mockFetch([
      { status: 200, body: { required: false } },
      { status: 200, body: authBody },
      { status: 200, body: configuredLibraries },
      { status: 200, body: libraryTree },
      { status: 200, body: browseAssets },
      { status: 200, body: globalExclusionPatterns },
      { status: 201, body: libraryWithCustomExclusion },
      { status: 200, body: [libraryWithCustomExclusion] },
      { status: 202, body: runningScan },
      { status: 200, body: runningScan },
      { status: 200, body: completedScan }
    ]);

    render(<App />);

    await screen.findByRole('heading', { name: 'All folders', level: 2 });
    await userEvent.click(
      within(screen.getByRole('navigation', { name: 'Utilities' })).getByRole('button', { name: 'Settings' })
    );
    expect(await screen.findByRole('button', { name: /Global exclusions/ })).toBeInTheDocument();
    await userEvent.click(screen.getByRole('button', { name: /Library exclusions/ }));
    await userEvent.type(screen.getByLabelText('Exclusion pattern'), '**/.cache/**');
    await userEvent.click(screen.getByRole('button', { name: 'Add exclusion' }));

    expect(await screen.findByText('**/.cache/**')).toBeInTheDocument();
    expect(await screen.findByText('Library settings changed.')).toBeInTheDocument();

    await userEvent.click(screen.getByRole('button', { name: 'Run scan now' }));

    expect(await screen.findByText('Scan running')).toBeInTheDocument();
    expect(await screen.findByText('Scan completed', {}, { timeout: 4_000 })).toBeInTheDocument();
    await userEvent.click(await screen.findByRole('button', { name: 'Scan completed' }));
    expect(screen.getByText('Added 2')).toBeInTheDocument();
    expect(screen.getByText('Unchanged 1')).toBeInTheDocument();
    expect(screen.getByText('Reappeared 0')).toBeInTheDocument();
    expect(fetchMock).toHaveBeenCalledWith(
      'http://localhost:8080/api/libraries/library-1/exclusion-patterns',
      expect.objectContaining({
        headers: expect.objectContaining({ 'X-Pixierge-Csrf': 'csrf-token' }),
        method: 'POST'
      })
    );
    expect(fetchMock).toHaveBeenCalledWith(
      'http://localhost:8080/api/libraries/library-1/scans',
      expect.objectContaining({
        headers: expect.objectContaining({ 'X-Pixierge-Csrf': 'csrf-token' }),
        method: 'POST'
      })
    );
    expect(fetchMock).toHaveBeenCalledWith(
      'http://localhost:8080/api/scans/scan-1',
      expect.objectContaining({
        credentials: 'include',
        method: 'GET'
      })
    );
  });

  it('archives and unarchives libraries from configuration', async () => {
    const archivedLibrary = {
      ...configuredLibraries[0],
      status: 'archived',
      archivedAt: '2026-07-05T00:00:00Z'
    };
    const fetchMock = mockFetch([
      { status: 200, body: { required: false } },
      { status: 200, body: authBody },
      { status: 200, body: configuredLibraries },
      { status: 200, body: libraryTree },
      { status: 200, body: browseAssets },
      { status: 200, body: globalExclusionPatterns },
      { status: 200, body: archivedLibrary },
      { status: 200, body: [archivedLibrary] },
      { status: 200, body: configuredLibraries[0] },
      { status: 200, body: configuredLibraries }
    ]);

    render(<App />);

    await screen.findByRole('heading', { name: 'All folders', level: 2 });
    await userEvent.click(
      within(screen.getByRole('navigation', { name: 'Utilities' })).getByRole('button', { name: 'Settings' })
    );
    expect(screen.getByRole('button', { name: 'Scan library' })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Archive' })).toBeInTheDocument();
    await userEvent.click(screen.getByRole('button', { name: 'Archive' }));

    const archiveDialog = await screen.findByRole('dialog', { name: 'Archive Family Photos?' });
    expect(archiveDialog).toHaveTextContent(
      'Archived libraries are hidden from normal browsing and cannot be scanned until they are unarchived.'
    );
    await userEvent.click(within(archiveDialog).getByRole('button', { name: 'Archive' }));

    expect(await screen.findByText('No libraries match the current view.')).toBeInTheDocument();

    await userEvent.click(screen.getByLabelText('Show archived'));
    expect(await screen.findByText('Archived libraries are hidden from normal browsing and cannot be scanned.')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Unarchive' })).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Scan library' })).not.toBeInTheDocument();

    await userEvent.click(screen.getByRole('button', { name: 'Unarchive' }));
    expect(await screen.findByRole('button', { name: 'Archive' })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Scan library' })).toBeInTheDocument();
    expect(fetchMock).toHaveBeenCalledWith(
      'http://localhost:8080/api/libraries/library-1/archive',
      expect.objectContaining({
        headers: expect.objectContaining({ 'X-Pixierge-Csrf': 'csrf-token' }),
        method: 'POST'
      })
    );
    expect(fetchMock).toHaveBeenCalledWith(
      'http://localhost:8080/api/libraries/library-1/restore',
      expect.objectContaining({
        headers: expect.objectContaining({ 'X-Pixierge-Csrf': 'csrf-token' }),
        method: 'POST'
      })
    );
  });
});

function installIntersectionObserverMock() {
  let callback: IntersectionObserverCallback | null = null;
  const original = globalThis.IntersectionObserver;

  class MockIntersectionObserver {
    constructor(next: IntersectionObserverCallback) {
      callback = next;
    }

    observe = vi.fn();
    disconnect = vi.fn();
    unobserve = vi.fn();
  }

  vi.stubGlobal('IntersectionObserver', MockIntersectionObserver);

  return {
    trigger() {
      callback?.([{ isIntersecting: true } as IntersectionObserverEntry], {} as IntersectionObserver);
    },
    restore() {
      if (original) {
        vi.stubGlobal('IntersectionObserver', original);
      } else {
        vi.unstubAllGlobals();
      }
    }
  };
}

function mockFetch(responses: MockResponse[]) {
  const fetchMock = vi.fn(async (input: RequestInfo | URL) => {
    const url = typeof input === 'string' ? input : input.toString();
    if (url.endsWith('/api/scans/active')) {
      return jsonResponse(200, []);
    }
    if (url.includes('/api/search/parse')) {
      const query = new URL(url).searchParams.get('q') ?? '';
      return jsonResponse(200, mockParseResponse(query));
    }
    if (url.includes('/api/search/suggestions')) {
      return jsonResponse(200, []);
    }
    if (url.endsWith('/api/albums') || url.includes('/api/albums?')) {
      return jsonResponse(200, []);
    }
    if (url.endsWith('/api/tags') || url.includes('/api/tags?')) {
      return jsonResponse(200, []);
    }

    const next = responses.shift();
    if (!next) {
      throw new Error(`Unexpected fetch call: ${url}`);
    }

    return jsonResponse(next.status, next.body);
  });

  vi.stubGlobal('fetch', fetchMock);
  return fetchMock;
}

function jsonResponse(status: number, body: unknown) {
  return {
    ok: status >= 200 && status < 300,
    headers: new Headers({ 'Content-Type': 'application/json' }),
    status,
    json: async () => body
  };
}

function mockParseResponse(query: string) {
  const tokens = query.trim() ? query.trim().split(/\s+/) : [];
  const clauses: Array<{
    field: string;
    value: string;
    negated: boolean;
    start: number;
    end: number;
    label: string;
  }> = [];
  const freeText: string[] = [];
  let cursor = 0;
  for (const token of tokens) {
    const start = query.indexOf(token, cursor);
    const end = start + token.length;
    cursor = end;
    const negated = token.startsWith('-') && token.includes(':');
    const candidate = negated ? token.slice(1) : token;
    const colon = candidate.indexOf(':');
    if (colon < 0) {
      freeText.push(token);
      continue;
    }
    const field = candidate.slice(0, colon);
    const value = candidate.slice(colon + 1).replace(/^"|"$/g, '');
    clauses.push({
      field,
      value,
      negated,
      start,
      end,
      label: `${negated ? 'Not ' : ''}${field}: ${value}`
    });
  }
  return {
    query,
    freeText: freeText.join(' '),
    clauses,
    errors: [],
    valid: true
  };
}
