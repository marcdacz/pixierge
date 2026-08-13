import {
  Briefcase,
  Download,
  Folder,
  FolderPlus,
  Heart,
  Images,
  MapPin,
  Search,
  Share2,
  Star,
  Tags,
  Trash2,
  User
} from 'lucide-react';
import type { Meta, StoryObj } from '@storybook/react-vite';
import {
  AppRail,
  BulkActionBar,
  ContextSidebar,
  MediaGrid,
  PageHeader,
  ThumbnailSizeControl,
  TimelineRail,
  ToolbarViewToggle,
  TopBar,
  type MediaGroup,
  type TimelineRailItem
} from '../../src/design-system/patterns';
import { PixiergeLogoMark } from '../shared/pixierge-logo-mark';

const meta = {
  title: 'Screens/Library North Star',
  parameters: {
    layout: 'fullscreen'
  }
} satisfies Meta;

export default meta;

type Story = StoryObj<typeof meta>;

const railItems = [
  { icon: Folder, label: 'Library', selected: true },
  { icon: Search, label: 'Search' },
  { icon: Star, label: 'Starred' },
  { icon: Images, label: 'Albums' },
  { icon: Tags, label: 'Tags' },
  { icon: Briefcase, label: 'Jobs' }
];

const libraryItems = [
  { icon: Folder, label: 'All folders', count: '12,842', selected: true },
  { icon: Heart, label: 'Favorites', count: '1,128' },
  { icon: User, label: 'People', count: '358' },
  { icon: MapPin, label: 'Places', count: '217' },
  { icon: Trash2, label: 'Trash', count: '23' }
];

const folderGroups = [
  {
    title: 'Family Photos',
    children: [
      { label: 'photos', count: '2,156' },
      { label: 'vacations', count: '2,156' },
      { label: 'events', count: '1,256' }
    ]
  },
  {
    title: 'Projects',
    children: [
      { label: 'Brand Photos', count: '842' },
      { label: 'Website Redesign', count: '1,128' },
      { label: 'Campaigns', count: '645' }
    ]
  },
  {
    title: 'Archive',
    children: [
      { label: 'Completed', count: '3,210' },
      { label: '2019 and earlier', count: '645' }
    ]
  }
];

const assetGroups: MediaGroup[] = [
  {
    date: 'May 24, 2025',
    count: '12 items',
    assets: [
      {
        label: 'Lighthouse',
        background: 'linear-gradient(135deg, #f9a8d4 0%, #67e8f9 42%, #1e1b4b 100%)',
        mode: 'wide'
      },
      { label: 'Alpine lake', background: 'linear-gradient(135deg, #fde68a 0%, #0e7490 48%, #312e81 100%)' },
      { label: 'Old street', background: 'linear-gradient(135deg, #fbbf24 0%, #38bdf8 42%, #581c87 100%)' },
      { label: 'Forest', background: 'linear-gradient(135deg, #bbf7d0 0%, #0f766e 46%, #111827 100%)' },
      { label: 'Flowers', background: 'linear-gradient(135deg, #fecdd3 0%, #fb7185 45%, #14532d 100%)' },
      {
        label: 'Selected clip',
        background: 'linear-gradient(135deg, #111827 0%, #2563eb 52%, #7c2d12 100%)',
        mode: 'selected'
      }
    ]
  },
  {
    date: 'May 23, 2025',
    count: '18 items',
    assets: [
      { label: 'Road bend', background: 'linear-gradient(135deg, #84cc16 0%, #164e63 52%, #0f172a 100%)' },
      { label: 'Concert', background: 'linear-gradient(135deg, #22d3ee 0%, #4f46e5 42%, #0f172a 100%)' },
      { label: 'Ridge', background: 'linear-gradient(135deg, #fed7aa 0%, #92400e 48%, #111827 100%)' },
      { label: 'Kayak', background: 'linear-gradient(135deg, #38bdf8 0%, #14b8a6 48%, #b91c1c 100%)' },
      { label: 'Cabin', background: 'linear-gradient(135deg, #166534 0%, #713f12 52%, #0f172a 100%)' },
      { label: 'Fog pier', background: 'linear-gradient(135deg, #bae6fd 0%, #1e3a8a 54%, #0f172a 100%)', mode: 'wide' },
      { label: 'Dinner', background: 'linear-gradient(135deg, #fed7aa 0%, #854d0e 46%, #111827 100%)' },
      {
        label: 'Hill country',
        background: 'linear-gradient(135deg, #fef3c7 0%, #84cc16 48%, #166534 100%)',
        mode: 'wide'
      },
      { label: 'City', background: 'linear-gradient(135deg, #0ea5e9 0%, #172554 50%, #020617 100%)' }
    ]
  }
];

const timelineItems: TimelineRailItem[] = [
  { label: '2025', kind: 'year' },
  { label: 'May', kind: 'active' },
  { kind: 'active-line' },
  { kind: 'dots' },
  { label: 'Apr', kind: 'month' },
  { kind: 'line' },
  { label: 'Mar', kind: 'month' },
  { kind: 'line' },
  { label: '2024', kind: 'year' },
  { kind: 'dots' },
  { label: '2023', kind: 'year' },
  { kind: 'dots' },
  { label: '2022', kind: 'year' },
  { kind: 'dots' },
  { label: '2021', kind: 'year' },
  { kind: 'dots' },
  { label: '2020', kind: 'year' },
  { kind: 'dots' },
  { label: '2019', kind: 'year' },
  { kind: 'dots' }
];

const bulkActions = [
  { icon: Star, label: 'Star' },
  { icon: Share2, label: 'Share' },
  { icon: Download, label: 'Download' },
  { icon: FolderPlus, label: 'Add to' },
  { icon: Folder, label: 'Move' },
  { icon: Trash2, label: 'Delete', destructive: true }
];

export function LibraryNorthStar({ theme }: { theme?: 'light' }) {
  return (
    <div
      className="grid h-screen grid-rows-[var(--topbar-height)_1fr] overflow-hidden bg-canvas font-sans text-content"
      data-theme={theme}
    >
      <TopBar
        logo={<PixiergeLogoMark className="w-40 min-w-0 md:w-52" showWordmark />}
        profile={{ initials: 'JL', name: 'Jessica Lau' }}
        searchLabel="Search library"
        searchPlaceholder='Search "mountains"'
      />
      <main className="grid min-h-0 grid-cols-[var(--rail-width)_1fr] overflow-hidden">
        <AppRail items={railItems} />
        <div className="grid min-w-0 lg:grid-cols-[var(--context-sidebar-width)_1fr]">
          <ContextSidebar title="Library" items={libraryItems} folderGroups={folderGroups} />
          <section className="grid min-w-0 grid-cols-[1fr_3rem] overflow-hidden bg-canvas">
            <div className="min-w-0 overflow-auto px-4 py-5 md:px-6">
              <div className="grid gap-5 pb-4">
                <PageHeader
                  actions={
                    <>
                      <ToolbarViewToggle value="grid" />
                      <ThumbnailSizeControl id="library-zoom" />
                    </>
                  }
                  meta="12,842 items"
                  title="All folders"
                />
                <MediaGrid groups={assetGroups} />
                <BulkActionBar actions={bulkActions} selectedCount={1} summary="7.4 MB" />
              </div>
            </div>
            <TimelineRail items={timelineItems} />
          </section>
        </div>
      </main>
    </div>
  );
}

export const Dark: Story = {
  render: () => <LibraryNorthStar />
};

export const Light: Story = {
  render: () => <LibraryNorthStar theme="light" />
};
