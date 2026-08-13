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
  title: 'Components/Patterns',
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
      { label: 'vacations', count: '2,156' }
    ]
  }
];

const mediaGroups: MediaGroup[] = [
  {
    date: 'May 24, 2025',
    count: '6 items',
    assets: [
      {
        label: 'Lighthouse',
        background: 'linear-gradient(135deg, #f9a8d4 0%, #67e8f9 42%, #1e1b4b 100%)',
        mode: 'wide'
      },
      { label: 'Alpine lake', background: 'linear-gradient(135deg, #fde68a 0%, #0e7490 48%, #312e81 100%)' },
      { label: 'Old street', background: 'linear-gradient(135deg, #fbbf24 0%, #38bdf8 42%, #581c87 100%)' },
      {
        label: 'Selected clip',
        background: 'linear-gradient(135deg, #111827 0%, #2563eb 52%, #7c2d12 100%)',
        mode: 'selected'
      }
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
  { label: '2024', kind: 'year' },
  { kind: 'dots' }
];

const bulkActions = [
  { icon: Star, label: 'Star' },
  { icon: Share2, label: 'Share' },
  { icon: Download, label: 'Download' },
  { icon: FolderPlus, label: 'Add to' },
  { icon: Trash2, label: 'Delete', destructive: true }
];

function StorySurface({ children }: { children: React.ReactNode }) {
  return <div className="min-h-screen bg-canvas p-8 font-sans text-content">{children}</div>;
}

export const ShellNavigation: Story = {
  render: () => (
    <div className="grid h-screen grid-rows-[var(--topbar-height)_1fr] overflow-hidden bg-canvas font-sans text-content">
      <TopBar
        logo={<PixiergeLogoMark className="w-40 min-w-0 md:w-52" showWordmark />}
        profile={{ initials: 'JL', name: 'Jessica Lau' }}
        searchLabel="Search library"
        searchPlaceholder='Search "mountains"'
      />
      <main className="grid min-h-0 grid-cols-[var(--rail-width)_var(--context-sidebar-width)_1fr]">
        <AppRail items={railItems} />
        <ContextSidebar title="Library" items={libraryItems} folderGroups={folderGroups} className="lg:grid" />
        <div className="min-w-0 p-6">
          <PageHeader
            actions={
              <>
                <ToolbarViewToggle value="grid" />
                <ThumbnailSizeControl />
              </>
            }
            meta="12,842 items"
            title="All folders"
          />
        </div>
      </main>
    </div>
  )
};

export const MediaBrowserPatterns: Story = {
  render: () => (
    <StorySurface>
      <div className="grid gap-6">
        <PageHeader
          actions={
            <>
              <ToolbarViewToggle value="grid" />
              <ThumbnailSizeControl />
            </>
          }
          meta="6 items"
          title="May 24, 2025"
        />
        <div className="grid grid-cols-[1fr_3rem] overflow-hidden">
          <MediaGrid groups={mediaGroups} />
          <TimelineRail items={timelineItems} className="md:grid" />
        </div>
        <BulkActionBar actions={bulkActions} className="static" selectedCount={1} summary="7.4 MB" />
      </div>
    </StorySurface>
  )
};
