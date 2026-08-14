import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, describe, expect, it, vi } from 'vitest';
import type { AssetDetail, AssetSummary } from '@/api';
import {
  AssetFocus,
  AssetGrid,
  AssetTile,
  ASSET_FOCUS_MAX_ZOOM,
  ASSET_FOCUS_MIN_ZOOM,
  ASSET_FOCUS_ZOOM_STEP,
  ASSET_TILE_SIZE_OPTIONS,
  ASSET_TILE_SIZE_STORAGE_KEY,
  DEFAULT_ASSET_TILE_SIZE_INDEX,
  MAX_ASSET_TILE_SIZE_INDEX,
  readStoredAssetTileSizeIndex,
  writeStoredAssetTileSizeIndex
} from './photo-grid';

afterEach(() => {
  cleanup();
});

const asset: AssetSummary = {
  id: 'asset-1',
  fileName: 'beach.jpg',
  displayPath: '/photos/beach.jpg',
  folderPath: '/photos',
  libraryId: 'library-1',
  libraryName: 'Main',
  availability: 'available',
  identityStatus: 'confirmed',
  duplicateCount: 1,
  capturedAt: null,
  observedAt: '2026-07-04T00:00:00Z',
  mediaType: 'image',
  mimeType: 'image/jpeg',
  width: 100,
  height: 80,
  previewable: true,
  thumbnailStatus: 'ready',
  thumbnailCacheKey: 'grid-cache-asset-1',
  thumbnailPlaceholder: 'linear-gradient(135deg, rgb(120, 130, 140), rgb(90, 100, 110))',
  starred: false
};

const assetDetail: AssetDetail = {
  id: 'asset-1',
  contentHash: 'sha256:aaa',
  identityStatus: 'confirmed',
  mediaType: 'image/jpeg',
  availability: 'available',
  duplicateCount: 1,
  metadata: {
    capturedAt: '2026-07-04T00:00:00Z',
    width: 800,
    height: 1200,
    orientation: null,
    fileExtension: 'jpg',
    mimeType: 'image/jpeg',
    extractionStatus: 'extracted',
    extractedAt: '2026-07-04T00:00:00Z',
    errorCode: null,
    errorMessage: null,
    cameraMake: null,
    cameraModel: null,
    lensModel: null,
    focalLength: null,
    aperture: null,
    exposureTime: null,
    iso: null,
    latitude: null,
    longitude: null,
    title: null,
    description: null,
    keywords: [],
    durationMs: null,
    displayRotation: null,
    container: null,
    videoCodec: null,
    audioCodec: null,
    frameRate: null,
    bitrate: null,
    hasAudio: null
  },
  files: [
    {
      id: 'file-1',
      libraryId: 'library-1',
      libraryName: 'Main',
      path: '/photos/portrait.jpg',
      folderPath: '/photos',
      fileName: 'portrait.jpg',
      sizeBytes: 1200,
      modifiedAt: '2026-07-04T00:00:00Z',
      status: 'active'
    }
  ],
  tags: []
};

describe('asset tile size persistence', () => {
  afterEach(() => {
    window.localStorage.removeItem(ASSET_TILE_SIZE_STORAGE_KEY);
  });

  it('reads the default when nothing is stored', () => {
    expect(readStoredAssetTileSizeIndex()).toBe(DEFAULT_ASSET_TILE_SIZE_INDEX);
  });

  it('persists and restores a valid index', () => {
    writeStoredAssetTileSizeIndex(4);
    expect(window.localStorage.getItem(ASSET_TILE_SIZE_STORAGE_KEY)).toBe('4');
    expect(readStoredAssetTileSizeIndex()).toBe(4);
  });

  it('falls back to the default for invalid stored values', () => {
    window.localStorage.setItem(ASSET_TILE_SIZE_STORAGE_KEY, '99');
    expect(readStoredAssetTileSizeIndex()).toBe(DEFAULT_ASSET_TILE_SIZE_INDEX);
    window.localStorage.setItem(ASSET_TILE_SIZE_STORAGE_KEY, 'nope');
    expect(readStoredAssetTileSizeIndex()).toBe(DEFAULT_ASSET_TILE_SIZE_INDEX);
  });

  it('ignores out-of-range writes', () => {
    writeStoredAssetTileSizeIndex(99);
    expect(window.localStorage.getItem(ASSET_TILE_SIZE_STORAGE_KEY)).toBeNull();
  });
});

describe('AssetTile', () => {
  it('reveals cached sharp thumbnails without waiting for a load event', async () => {
    const descriptor = Object.getOwnPropertyDescriptor(HTMLImageElement.prototype, 'complete');
    const naturalWidthDescriptor = Object.getOwnPropertyDescriptor(HTMLImageElement.prototype, 'naturalWidth');

    Object.defineProperty(HTMLImageElement.prototype, 'complete', {
      configurable: true,
      get() {
        return true;
      }
    });
    Object.defineProperty(HTMLImageElement.prototype, 'naturalWidth', {
      configurable: true,
      get() {
        return 120;
      }
    });

    try {
      render(<AssetTile asset={asset} imageSource="grid" onOpen={vi.fn()} />);

      const sharp = document.querySelector(
        'img[src="http://localhost:8080/api/assets/asset-1/thumbnail?c=grid-cache-asset-1"]'
      );
      expect(sharp).not.toBeNull();
      await waitFor(() => expect(sharp).toHaveClass('opacity-100'));
    } finally {
      if (descriptor) {
        Object.defineProperty(HTMLImageElement.prototype, 'complete', descriptor);
      } else {
        delete (HTMLImageElement.prototype as { complete?: unknown }).complete;
      }
      if (naturalWidthDescriptor) {
        Object.defineProperty(HTMLImageElement.prototype, 'naturalWidth', naturalWidthDescriptor);
      } else {
        delete (HTMLImageElement.prototype as { naturalWidth?: unknown }).naturalWidth;
      }
    }
  });
});

describe('AssetFocus', () => {
  function renderFocus(overrides: Partial<Parameters<typeof AssetFocus>[0]> = {}) {
    return render(
      <div className="h-[600px]">
        <AssetFocus
          asset={assetDetail}
          cacheKey="preview-cache"
          hasNext={false}
          hasPrevious={false}
          loading={false}
          onClose={vi.fn()}
          onNext={vi.fn()}
          onPrevious={vi.fn()}
          {...overrides}
        />
      </div>
    );
  }

  it('fits the preview inside the available viewport height', () => {
    const { container } = renderFocus();
    const root = container.firstElementChild?.firstElementChild;
    const surface = container.querySelector('.absolute.inset-0.overflow-hidden.bg-black');
    const image = container.querySelector('img');

    expect(root).toHaveClass('h-full', 'max-h-full', 'min-h-0');
    expect(surface).not.toBeNull();
    expect(image).toHaveClass('h-full', 'w-full', 'object-contain');
    expect(image).toHaveStyle({ transform: `translate(0px, 0px) scale(${ASSET_FOCUS_MIN_ZOOM})` });
  });

  it('zooms in and out from the toolbar controls', () => {
    renderFocus();

    const image = document.querySelector('img');
    const zoomSlider = screen.getByRole('slider', { name: 'Zoom' });
    expect(image).not.toBeNull();
    expect(zoomSlider).toHaveValue(String(ASSET_FOCUS_MIN_ZOOM));

    fireEvent.change(zoomSlider, {
      target: { value: String(ASSET_FOCUS_MIN_ZOOM + ASSET_FOCUS_ZOOM_STEP) }
    });
    expect(image).toHaveStyle({
      transform: `translate(0px, 0px) scale(${ASSET_FOCUS_MIN_ZOOM + ASSET_FOCUS_ZOOM_STEP})`
    });
    expect(zoomSlider).toHaveValue(String(ASSET_FOCUS_MIN_ZOOM + ASSET_FOCUS_ZOOM_STEP));

    fireEvent.change(zoomSlider, { target: { value: String(ASSET_FOCUS_MIN_ZOOM) } });
    expect(image).toHaveStyle({ transform: `translate(0px, 0px) scale(${ASSET_FOCUS_MIN_ZOOM})` });
  });

  it('zooms with the mouse wheel and resets on double click', () => {
    const { container } = renderFocus();
    const surface = container.querySelector('.absolute.inset-0.overflow-hidden.bg-black');
    const image = container.querySelector('img');
    expect(surface).not.toBeNull();
    expect(image).not.toBeNull();

    fireEvent.wheel(surface!, { deltaY: -100 });
    expect(image).toHaveStyle({
      transform: `translate(0px, 0px) scale(${ASSET_FOCUS_MIN_ZOOM + ASSET_FOCUS_ZOOM_STEP})`
    });

    fireEvent.doubleClick(surface!);
    expect(image).toHaveStyle({ transform: `translate(0px, 0px) scale(${ASSET_FOCUS_MIN_ZOOM})` });
  });

  it('clamps zoom at the maximum', () => {
    renderFocus();
    const image = document.querySelector('img');
    const zoomSlider = screen.getByRole('slider', { name: 'Zoom' });

    fireEvent.change(zoomSlider, { target: { value: String(ASSET_FOCUS_MAX_ZOOM + 1) } });

    expect(image).toHaveStyle({ transform: `translate(0px, 0px) scale(${ASSET_FOCUS_MAX_ZOOM})` });
    expect(zoomSlider).toHaveValue(String(ASSET_FOCUS_MAX_ZOOM));
  });

  it('opens actions from the more button and right-click', async () => {
    const user = userEvent.setup();
    const onOpenActions = vi.fn();
    const onContextMenu = vi.fn();
    const { container } = renderFocus({ onOpenActions, onContextMenu });

    await user.click(screen.getByRole('button', { name: 'Photo actions' }));
    expect(onOpenActions).toHaveBeenCalledWith(
      expect.objectContaining({ x: expect.any(Number), y: expect.any(Number) })
    );

    const surface = container.querySelector('.absolute.inset-0.overflow-hidden.bg-black');
    expect(surface).not.toBeNull();
    fireEvent.contextMenu(surface!);
    expect(onContextMenu).toHaveBeenCalled();
  });

  it('closes metadata from the panel close button', async () => {
    const user = userEvent.setup();
    renderFocus();

    await user.click(screen.getByRole('button', { name: 'Show photo metadata' }));
    expect(screen.getByRole('complementary')).toBeInTheDocument();

    await user.click(screen.getByRole('button', { name: 'Close photo metadata' }));
    expect(screen.queryByRole('complementary')).not.toBeInTheDocument();
  });

  it('hides completed status and empty optional metadata', async () => {
    const user = userEvent.setup();
    renderFocus();

    await user.click(screen.getByRole('button', { name: 'Show photo metadata' }));

    expect(screen.queryByText('Metadata')).not.toBeInTheDocument();
    expect(screen.queryByText('Camera')).not.toBeInTheDocument();
    expect(screen.queryByText('Lens')).not.toBeInTheDocument();
    expect(screen.queryByText('Exposure')).not.toBeInTheDocument();
  });

  it('shows in-progress status and only the metadata already available', async () => {
    const user = userEvent.setup();
    renderFocus({
      asset: {
        ...assetDetail,
        metadata: {
          ...assetDetail.metadata,
          extractionStatus: 'processing',
          cameraMake: 'Nikon',
          cameraModel: 'D7100'
        }
      }
    });

    await user.click(screen.getByRole('button', { name: 'Show photo metadata' }));

    expect(screen.getByText('Metadata')).toBeInTheDocument();
    expect(screen.getByText('processing')).toBeInTheDocument();
    expect(screen.getByText('Camera')).toBeInTheDocument();
    expect(screen.getByText('Nikon D7100')).toBeInTheDocument();
    expect(screen.queryByText('Lens')).not.toBeInTheDocument();
    expect(screen.queryByText('Exposure')).not.toBeInTheDocument();
  });

  it('shows a star badge when starred', () => {
    renderFocus({ starred: true });
    expect(screen.getByLabelText('Starred')).toBeInTheDocument();
  });

  it('hides the star badge when not starred', () => {
    renderFocus({ starred: false });
    expect(screen.queryByLabelText('Starred')).not.toBeInTheDocument();
  });
});

describe('AssetTile starred', () => {
  it('shows a star badge when the asset is starred', () => {
    render(<AssetTile asset={{ ...asset, starred: true }} imageSource="grid" onOpen={vi.fn()} />);
    expect(screen.getByLabelText('Starred')).toBeInTheDocument();
  });

  it('hides the star badge when the asset is not starred', () => {
    render(<AssetTile asset={asset} imageSource="grid" onOpen={vi.fn()} />);
    expect(screen.queryByLabelText('Starred')).not.toBeInTheDocument();
  });
});

describe('AssetGrid', () => {
  it('keeps a fixed row height while sizing tile width from the asset aspect ratio', () => {
    render(
      <AssetGrid
        assetTileSize={ASSET_TILE_SIZE_OPTIONS[DEFAULT_ASSET_TILE_SIZE_INDEX]}
        onOpen={vi.fn()}
        sections={[
          {
            folderPath: '/photos/2017-09-01',
            folderName: '2017-09-01 edited',
            assets: [asset]
          }
        ]}
      />
    );

    const grid = screen.getByLabelText('Asset grid');
    const tile = screen.getByTestId('asset-tile-asset-1');
    expect(grid).toHaveClass('flex', 'flex-wrap');
    expect(grid.style.getPropertyValue('--asset-grid-tile-height')).toBe(
      ASSET_TILE_SIZE_OPTIONS[DEFAULT_ASSET_TILE_SIZE_INDEX].rowHeight
    );
    expect(tile).toHaveStyle({ '--asset-tile-ratio': '1.25' });
    expect(tile).toHaveStyle({
      height: 'var(--asset-grid-tile-height)',
      width: 'min(100%, calc(var(--asset-grid-tile-height) * var(--asset-tile-ratio)))'
    });
  });

  it('uses larger fixed row heights for the last three slider steps', () => {
    const { rerender } = render(
      <AssetGrid
        assetTileSize={ASSET_TILE_SIZE_OPTIONS[3]}
        onOpen={vi.fn()}
        sections={[
          {
            folderPath: '/photos/2017-09-01',
            folderName: '2017-09-01 edited',
            assets: [asset]
          }
        ]}
      />
    );

    expect(screen.getByLabelText('Asset grid').style.getPropertyValue('--asset-grid-tile-height')).toBe(
      ASSET_TILE_SIZE_OPTIONS[3].rowHeight
    );
    expect(ASSET_TILE_SIZE_OPTIONS[3].rowHeight).toBe('14rem');
    expect(ASSET_TILE_SIZE_OPTIONS[4].rowHeight).toBe('20rem');
    expect(ASSET_TILE_SIZE_OPTIONS[5].rowHeight).toBe('min(32rem, 70vh)');

    rerender(
      <AssetGrid
        assetTileSize={ASSET_TILE_SIZE_OPTIONS[4]}
        onOpen={vi.fn()}
        sections={[
          {
            folderPath: '/photos/2017-09-01',
            folderName: '2017-09-01 edited',
            assets: [asset]
          }
        ]}
      />
    );
    expect(screen.getByLabelText('Asset grid').style.getPropertyValue('--asset-grid-tile-height')).toBe(
      ASSET_TILE_SIZE_OPTIONS[4].rowHeight
    );
  });

  it('uses the maximum fixed row height at the maximum size setting', () => {
    render(
      <AssetGrid
        assetTileSize={ASSET_TILE_SIZE_OPTIONS[MAX_ASSET_TILE_SIZE_INDEX]}
        onOpen={vi.fn()}
        sections={[
          {
            folderPath: '/photos/2017-09-01',
            folderName: '2017-09-01 edited',
            assets: [asset]
          }
        ]}
      />
    );

    expect(screen.getByLabelText('Asset grid').style.getPropertyValue('--asset-grid-tile-height')).toBe(
      'min(32rem, 70vh)'
    );
  });

  it('falls back to a four-by-three tile ratio when dimensions are unavailable', () => {
    render(
      <AssetGrid
        assetTileSize={ASSET_TILE_SIZE_OPTIONS[DEFAULT_ASSET_TILE_SIZE_INDEX]}
        onOpen={vi.fn()}
        sections={[
          {
            folderPath: '/photos/2017-09-01',
            folderName: '2017-09-01 edited',
            assets: [{ ...asset, width: null, height: null }]
          }
        ]}
      />
    );

    expect(screen.getByTestId('asset-tile-asset-1')).toHaveStyle({
      '--asset-tile-ratio': `${4 / 3}`
    });
  });

  it('shows an item count beside each section title', () => {
    render(
      <AssetGrid
        assetTileSize={ASSET_TILE_SIZE_OPTIONS[DEFAULT_ASSET_TILE_SIZE_INDEX]}
        onOpen={vi.fn()}
        sections={[
          {
            folderPath: '/photos/2017-09-01',
            folderName: '2017-09-01 edited',
            assets: [asset, { ...asset, id: 'asset-2', fileName: 'lake.jpg' }]
          }
        ]}
      />
    );

    expect(screen.getByRole('heading', { name: '2017-09-01 edited' })).toBeInTheDocument();
    expect(screen.getByText('2 items')).toBeInTheDocument();
  });

  it('keeps the section rename control collapsed so the count stays close', () => {
    render(
      <AssetGrid
        assetTileSize={ASSET_TILE_SIZE_OPTIONS[DEFAULT_ASSET_TILE_SIZE_INDEX]}
        onOpen={vi.fn()}
        onRenameSection={vi.fn()}
        sections={[
          {
            folderPath: '/photos/2017-09-01',
            folderName: '2017-09-01 edited',
            assets: [asset]
          }
        ]}
      />
    );

    const rename = screen.getByRole('button', { name: 'Rename folder name' });
    expect(rename).toHaveClass('w-0');
    expect(rename).toHaveClass('group-hover:w-6');
    expect(screen.getByText('1 item')).toBeInTheDocument();
  });

  it('renders a consolidated grid without folder headers when headers are hidden', () => {
    render(
      <AssetGrid
        assetTileSize={ASSET_TILE_SIZE_OPTIONS[DEFAULT_ASSET_TILE_SIZE_INDEX]}
        onOpen={vi.fn()}
        sections={[
          {
            folderPath: '/photos/2017-09-01',
            folderName: '2017-09-01',
            assets: [asset]
          },
          {
            folderPath: '/photos/2017-09-20',
            folderName: '2017-09-20',
            assets: [{ ...asset, id: 'asset-2', fileName: 'lake.jpg' }]
          }
        ]}
        showSectionHeaders={false}
      />
    );

    expect(screen.queryByRole('heading', { name: '2017-09-01' })).not.toBeInTheDocument();
    expect(screen.queryByRole('heading', { name: '2017-09-20' })).not.toBeInTheDocument();
    expect(screen.getAllByLabelText('Asset grid')).toHaveLength(1);
    expect(screen.getByRole('button', { name: 'Select beach.jpg' })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Select lake.jpg' })).toBeInTheDocument();
  });
});
