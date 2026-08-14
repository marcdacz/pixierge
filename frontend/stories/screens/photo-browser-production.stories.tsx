import type { Meta, StoryObj } from '@storybook/react-vite';
import { userEvent, within } from 'storybook/test';
import type { AssetBrowseResponse, AssetSummary } from '../../src/api';
import { PhotoBrowser } from '../../src/features/library/photo-browser';

const meta = {
  title: 'Screens/Photo Browser Production',
  component: PhotoBrowser,
  parameters: {
    layout: 'fullscreen'
  },
  decorators: [
    (Story) => (
      <div className="h-screen bg-canvas p-4 font-sans text-content md:p-6">
        <Story />
      </div>
    )
  ]
} satisfies Meta<typeof PhotoBrowser>;

export default meta;

type Story = StoryObj<typeof meta>;

const asset = (
  id: string,
  fileName: string,
  background: string,
  dimensions: { width: number; height: number },
  starred = false
): AssetSummary => ({
  id,
  fileName,
  displayPath: `/photos/family/${fileName}`,
  folderPath: '/photos/family',
  libraryId: 'library-1',
  libraryName: 'Family Photos',
  availability: 'available',
  identityStatus: 'confirmed',
  duplicateCount: 1,
  capturedAt: '2025-05-24T10:30:00Z',
  observedAt: '2025-05-24T10:30:00Z',
  mediaType: 'image',
  mimeType: 'image/jpeg',
  width: dimensions.width,
  height: dimensions.height,
  previewable: false,
  thumbnailStatus: 'ready',
  thumbnailCacheKey: null,
  thumbnailPlaceholder: background,
  starred
});

const browsableAssets: AssetBrowseResponse = {
  sections: [
    {
      folderPath: '/photos/family/may',
      folderName: 'May weekend',
      assets: [
        asset(
          'story-asset-1',
          'beach.jpg',
          'linear-gradient(135deg, #67e8f9 0%, #4338ca 52%, #111827 100%)',
          { width: 2400, height: 1600 },
          true
        ),
        asset('story-asset-2', 'ridge.jpg', 'linear-gradient(135deg, #fde68a 0%, #0e7490 48%, #312e81 100%)', {
          width: 1200,
          height: 1800
        }),
        asset('story-asset-3', 'dinner.jpg', 'linear-gradient(135deg, #fecdd3 0%, #fb7185 45%, #14532d 100%)', {
          width: 1800,
          height: 1800
        }),
        asset('story-asset-4', 'window.jpg', 'linear-gradient(135deg, #bbf7d0 0%, #0f766e 46%, #111827 100%)', {
          width: 1400,
          height: 2100
        }),
        asset('story-asset-5', 'city.jpg', 'linear-gradient(135deg, #0ea5e9 0%, #172554 50%, #020617 100%)', {
          width: 2200,
          height: 1200
        }),
        asset('story-asset-6', 'flowers.jpg', 'linear-gradient(135deg, #f9a8d4 0%, #fb7185 44%, #14532d 100%)', {
          width: 1600,
          height: 1200
        }),
        asset('story-asset-7', 'portrait.jpg', 'linear-gradient(135deg, #c4b5fd 0%, #7c3aed 48%, #111827 100%)', {
          width: 1200,
          height: 1800
        }),
        asset('story-asset-8', 'panorama.jpg', 'linear-gradient(135deg, #bae6fd 0%, #1e3a8a 54%, #0f172a 100%)', {
          width: 2600,
          height: 1200
        })
      ]
    },
    {
      folderPath: '/photos/family/april',
      folderName: 'April archive',
      assets: [
        asset('story-asset-9', 'cabin.jpg', 'linear-gradient(135deg, #84cc16 0%, #164e63 52%, #0f172a 100%)', {
          width: 1600,
          height: 2000
        }),
        asset('story-asset-10', 'studio.jpg', 'linear-gradient(135deg, #fed7aa 0%, #92400e 48%, #111827 100%)', {
          width: 2000,
          height: 1400
        }),
        asset('story-asset-11', 'garden.jpg', 'linear-gradient(135deg, #38bdf8 0%, #14b8a6 48%, #b91c1c 100%)', {
          width: 1200,
          height: 1600
        }),
        asset('story-asset-12', 'table.jpg', 'linear-gradient(135deg, #fef3c7 0%, #84cc16 48%, #166534 100%)', {
          width: 1800,
          height: 1200
        })
      ]
    }
  ],
  totalCount: 12,
  page: 0,
  pageSize: 50,
  hasNext: false
};

const emptyAssets: AssetBrowseResponse = {
  sections: [],
  totalCount: 0,
  page: 0,
  pageSize: 50,
  hasNext: false
};

export const Populated: Story = {
  args: {
    assets: browsableAssets,
    browseContextKey: 'storybook-populated',
    loadingAssets: false,
    subtitle: <p className="text-sm text-content-muted">Family Photos</p>,
    title: 'All folders'
  }
};

export const Loading: Story = {
  args: {
    assets: null,
    browseContextKey: 'storybook-loading',
    loadingAssets: true,
    title: 'All folders'
  }
};

export const Empty: Story = {
  args: {
    assets: emptyAssets,
    browseContextKey: 'storybook-empty',
    emptyDescription: 'Run a scan from Settings, or adjust the current folder.',
    emptyTitle: 'No assets found',
    loadingAssets: false,
    title: 'All folders'
  }
};

export const Error: Story = {
  args: {
    assets: null,
    browseContextKey: 'storybook-error',
    error: 'Assets could not be loaded.',
    loadingAssets: false,
    title: 'All folders'
  }
};

export const SelectedAsset: Story = {
  args: {
    assets: browsableAssets,
    browseContextKey: 'storybook-selected',
    loadingAssets: false,
    title: 'All folders'
  },
  play: async ({ canvasElement }) => {
    const canvas = within(canvasElement);
    await userEvent.click(canvas.getByTestId('asset-tile-story-asset-1'));
  }
};

export const BulkSelection: Story = {
  args: {
    assets: browsableAssets,
    browseContextKey: 'storybook-bulk-selection',
    loadingAssets: false,
    title: 'All folders'
  },
  play: async ({ canvasElement }) => {
    const canvas = within(canvasElement);
    await userEvent.click(canvas.getByTestId('asset-tile-story-asset-1'));
    await userEvent.click(canvas.getByTestId('asset-tile-story-asset-3'), { ctrlKey: true });
  }
};
