import {
  Bell,
  Briefcase,
  ChevronDown,
  Download,
  Folder,
  FolderPlus,
  Grid3X3,
  Heart,
  Images,
  List,
  MapPin,
  Maximize2,
  MoreHorizontal,
  Search,
  Settings,
  Share2,
  SlidersHorizontal,
  Star,
  Tags,
  Trash2,
  User,
  X
} from 'lucide-react';
import type { Meta, StoryObj } from '@storybook/react-vite';
import { Button } from '../../src/components/ui/button';
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
  { icon: Folder, label: 'Library' },
  { icon: Search, label: 'Search' },
  { icon: Star, label: 'Starred' },
  { icon: Images, label: 'Albums' },
  { icon: Tags, label: 'Tags' },
  { icon: Briefcase, label: 'Jobs' }
];

const libraryItems = [
  { icon: Folder, label: 'All folders', count: '12,842' },
  { icon: Heart, label: 'Favorites', count: '1,128' },
  { icon: User, label: 'People', count: '358' },
  { icon: MapPin, label: 'Places', count: '217' },
  { icon: Trash2, label: 'Trash', count: '23' }
];

const folderGroups = [
  {
    title: 'Family Photos',
    children: [
      ['photos', '2,156'],
      ['vacations', '2,156'],
      ['events', '1,256']
    ]
  },
  {
    title: 'Projects',
    children: [
      ['Brand Photos', '842'],
      ['Website Redesign', '1,128'],
      ['Campaigns', '645']
    ]
  },
  {
    title: 'Archive',
    children: [
      ['Completed', '3,210'],
      ['2019 and earlier', '645']
    ]
  }
];

const assetGroups = [
  {
    date: 'May 24, 2025',
    count: '12 items',
    assets: [
      ['Lighthouse', 'linear-gradient(135deg, #f9a8d4 0%, #67e8f9 42%, #1e1b4b 100%)', 'wide'],
      ['Alpine lake', 'linear-gradient(135deg, #fde68a 0%, #0e7490 48%, #312e81 100%)', 'normal'],
      ['Old street', 'linear-gradient(135deg, #fbbf24 0%, #38bdf8 42%, #581c87 100%)', 'normal'],
      ['Forest', 'linear-gradient(135deg, #bbf7d0 0%, #0f766e 46%, #111827 100%)', 'normal'],
      ['Flowers', 'linear-gradient(135deg, #fecdd3 0%, #fb7185 45%, #14532d 100%)', 'normal'],
      ['Selected clip', 'linear-gradient(135deg, #111827 0%, #2563eb 52%, #7c2d12 100%)', 'selected']
    ]
  },
  {
    date: 'May 23, 2025',
    count: '18 items',
    assets: [
      ['Road bend', 'linear-gradient(135deg, #84cc16 0%, #164e63 52%, #0f172a 100%)', 'normal'],
      ['Concert', 'linear-gradient(135deg, #22d3ee 0%, #4f46e5 42%, #0f172a 100%)', 'normal'],
      ['Ridge', 'linear-gradient(135deg, #fed7aa 0%, #92400e 48%, #111827 100%)', 'normal'],
      ['Kayak', 'linear-gradient(135deg, #38bdf8 0%, #14b8a6 48%, #b91c1c 100%)', 'normal'],
      ['Cabin', 'linear-gradient(135deg, #166534 0%, #713f12 52%, #0f172a 100%)', 'normal'],
      ['Fog pier', 'linear-gradient(135deg, #bae6fd 0%, #1e3a8a 54%, #0f172a 100%)', 'wide'],
      ['Dinner', 'linear-gradient(135deg, #fed7aa 0%, #854d0e 46%, #111827 100%)', 'normal'],
      ['Hill country', 'linear-gradient(135deg, #fef3c7 0%, #84cc16 48%, #166534 100%)', 'wide'],
      ['City', 'linear-gradient(135deg, #0ea5e9 0%, #172554 50%, #020617 100%)', 'normal']
    ]
  }
];

function TopNav() {
  return (
    <header className="grid h-[var(--topbar-height)] grid-cols-[minmax(12rem,1fr)_minmax(20rem,40rem)_minmax(12rem,1fr)] items-center gap-4 border-b border-border bg-surface/92 pl-8 pr-4">
      <PixiergeLogoMark className="w-52 min-w-0" showWordmark />
      <div className="flex min-w-0 items-center gap-2">
        <label className="flex h-11 min-w-0 flex-1 items-center gap-3 rounded-lg border border-focus bg-surface-sunken px-4 text-sm text-content-muted">
          <Search aria-hidden="true" className="h-5 w-5 shrink-0" />
          <input className="min-w-0 flex-1 bg-transparent text-content outline-none" placeholder='Search "mountains"' />
        </label>
        <button
          className="hidden h-10 w-10 shrink-0 place-items-center rounded-lg border border-border bg-surface-raised text-content-muted sm:grid"
          type="button"
          aria-label="Search filters"
        >
          <SlidersHorizontal aria-hidden="true" className="h-5 w-5" />
        </button>
      </div>
      <div className="flex min-w-0 items-center justify-end gap-3">
        <Button variant="ghost" size="icon" aria-label="Notifications">
          <Bell aria-hidden="true" className="h-5 w-5" />
        </Button>
        <button
          className="hidden h-11 items-center gap-3 rounded-lg pl-2 pr-0 text-sm font-semibold text-content sm:flex"
          type="button"
        >
          <span className="flex h-8 w-8 items-center justify-center rounded-full bg-cyan-500 text-xs text-white">
            JL
          </span>
          <span>Jessica Lau</span>
          <ChevronDown aria-hidden="true" className="h-4 w-4 text-content-muted" />
        </button>
      </div>
    </header>
  );
}

function AppRail() {
  return (
    <nav
      aria-label="Primary"
      className="flex h-[calc(100vh-var(--topbar-height))] min-h-0 flex-col overflow-hidden border-r border-border bg-canvas-subtle px-2 py-4"
    >
      <div className="grid min-h-0 flex-1 content-start gap-4 overflow-y-auto pb-3">
        {railItems.map(({ icon: Icon, label }, index) => (
          <button
            className={`relative grid h-[3.75rem] place-items-center gap-0.5 rounded-md text-[11px] font-semibold leading-none transition-colors hover:bg-surface-hover hover:text-content focus-visible:ring-2 focus-visible:ring-focus ${
              index === 0 ? 'bg-surface-active text-content' : 'text-content-muted'
            }`}
            key={label}
            type="button"
          >
            {index === 0 ? <span className="absolute top-1 bottom-1 left-0 w-0.5 rounded-full bg-info" /> : null}
            <Icon aria-hidden="true" className="h-6 w-6" strokeWidth={1.85} />
            <span>{label}</span>
          </button>
        ))}
      </div>
      <button
        className="mt-auto grid h-[3.75rem] shrink-0 place-items-center gap-0.5 rounded-md text-[11px] font-semibold leading-none text-content-muted hover:bg-surface-hover"
        type="button"
      >
        <Settings aria-hidden="true" className="h-6 w-6" strokeWidth={1.85} />
        <span>Settings</span>
      </button>
    </nav>
  );
}

function ContextSidebar() {
  return (
    <aside className="hidden min-h-0 overflow-auto border-r border-border bg-surface px-4 py-5 lg:grid lg:content-start lg:gap-6">
      <section className="grid gap-3">
        <h2 className="text-sm font-semibold text-content">Library</h2>
        <div className="grid gap-1">
          {libraryItems.map(({ icon: Icon, label, count }, index) => (
            <button
              className={`grid h-10 grid-cols-[1.25rem_minmax(0,1fr)_3.25rem] items-center gap-3 rounded-md px-3 text-sm transition-colors ${
                index === 0
                  ? 'bg-surface-active text-content'
                  : 'text-content-muted hover:bg-surface-hover hover:text-content'
              }`}
              key={label}
              type="button"
            >
              <Icon aria-hidden="true" className="h-4 w-4" />
              <span className="min-w-0 flex-1 truncate text-left">{label}</span>
              <span className="justify-self-end rounded bg-surface-hover px-1.5 py-0.5 text-xs tabular-nums text-content-subtle">
                {count}
              </span>
            </button>
          ))}
        </div>
      </section>

      <section className="grid gap-3">
        <div className="grid h-10 grid-cols-[minmax(0,1fr)_3.25rem] items-center gap-3 px-3">
          <h3 className="text-sm font-semibold text-content">My folders</h3>
          <button
            className="grid h-6 min-w-8 place-items-center justify-self-end rounded bg-surface-hover px-1.5 text-sm font-semibold leading-none text-content-subtle"
            type="button"
            aria-label="Add folder"
          >
            +
          </button>
        </div>
        {folderGroups.map((group) => (
          <div className="grid gap-1" key={group.title}>
            <button
              className="flex h-9 items-center gap-2 rounded-md px-3 text-sm text-content-muted hover:bg-surface-hover"
              type="button"
            >
              <ChevronDown aria-hidden="true" className="h-4 w-4" />
              <Folder aria-hidden="true" className="h-4 w-4" />
              <span>{group.title}</span>
            </button>
            {group.children.map(([label, count]) => (
              <button
                className="ml-7 grid h-8 grid-cols-[1.25rem_minmax(0,1fr)_3.25rem] items-center gap-2 rounded-md px-3 text-sm text-content-muted hover:bg-surface-hover"
                key={label}
                type="button"
              >
                <Folder aria-hidden="true" className="h-4 w-4" />
                <span className="min-w-0 flex-1 truncate text-left">{label}</span>
                <span className="justify-self-end rounded bg-surface-hover px-1.5 py-0.5 text-xs tabular-nums text-content-subtle">
                  {count}
                </span>
              </button>
            ))}
          </div>
        ))}
      </section>
    </aside>
  );
}

function AssetGrid() {
  return (
    <section className="grid gap-6">
      {assetGroups.map((group) => (
        <section className="grid gap-3" key={group.date}>
          <div className="flex items-center gap-2">
            <ChevronDown aria-hidden="true" className="h-4 w-4" />
            <h2 className="text-base font-semibold text-content">{group.date}</h2>
            <span className="text-sm text-content-muted">{group.count}</span>
          </div>
          <div className="grid auto-rows-[9rem] grid-cols-2 gap-1 sm:grid-cols-3 lg:grid-cols-4 xl:grid-cols-5 2xl:grid-cols-6">
            {group.assets.map(([label, background, mode]) => (
              <article
                aria-label={label}
                className={`relative overflow-hidden bg-surface ${mode === 'wide' ? 'col-span-2' : ''} ${
                  mode === 'selected' ? 'ring-2 ring-focus' : ''
                }`}
                key={label}
                role="img"
                style={{ backgroundImage: background }}
              >
                <div className="absolute inset-0 bg-gradient-to-t from-black/72 via-black/8 to-transparent" />
                {mode === 'selected' ? (
                  <>
                    <span className="absolute top-2 left-2 grid h-6 w-6 place-items-center rounded-full bg-info text-xs font-bold text-content-inverse">
                      ✓
                    </span>
                    <button
                      className="absolute top-2 right-2 grid h-7 w-7 place-items-center rounded-md text-white/90 hover:bg-black/30"
                      type="button"
                      aria-label="Asset actions"
                    >
                      <MoreHorizontal aria-hidden="true" className="h-4 w-4" />
                    </button>
                  </>
                ) : null}
                <span className="absolute bottom-2 left-2 max-w-[calc(100%-1rem)] truncate text-xs font-semibold text-white/86">
                  {label}
                </span>
              </article>
            ))}
          </div>
        </section>
      ))}
    </section>
  );
}

function Timeline() {
  const items = [
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

  return (
    <aside className="hidden border-l border-border bg-canvas px-2 py-6 text-xs text-content-muted md:grid">
      <div className="grid content-start justify-items-center">
        {items.map((item, index) => (
          <div className="grid min-h-6 justify-items-center" key={`${item.kind}-${item.label ?? index}`}>
            {item.kind === 'year' || item.kind === 'month' || item.kind === 'active' ? (
              <button
                className={`rounded px-1 py-0.5 ${
                  item.kind === 'active'
                    ? 'font-semibold text-info'
                    : item.kind === 'year'
                      ? 'text-sm font-semibold text-content'
                      : 'text-content-muted'
                }`}
                type="button"
              >
                {item.label}
              </button>
            ) : null}
            {item.kind === 'active-line' ? (
              <div className="relative my-1 h-14 w-3">
                <div className="mx-auto h-full w-0.5 rounded-full bg-info" />
                <span className="absolute top-1/2 left-1/2 h-2.5 w-2.5 -translate-x-1/2 -translate-y-1/2 rounded-full bg-content-subtle" />
              </div>
            ) : null}
            {item.kind === 'line' ? <div className="my-1 h-8 w-0.5 rounded-full bg-content-subtle/60" /> : null}
            {item.kind === 'dots' ? (
              <div className="my-2 grid gap-1">
                {Array.from({ length: 4 }, (_, dotIndex) => (
                  <span className="h-1 w-1 rounded-full bg-content-subtle" key={dotIndex} />
                ))}
              </div>
            ) : null}
          </div>
        ))}
      </div>
    </aside>
  );
}

function SelectionBar() {
  const actions = [
    { icon: Star, label: 'Star' },
    { icon: Share2, label: 'Share' },
    { icon: Download, label: 'Download' },
    { icon: FolderPlus, label: 'Add to' },
    { icon: Folder, label: 'Move' }
  ];

  return (
    <div className="pointer-events-none fixed right-8 bottom-4 left-[calc(var(--rail-width)+var(--context-sidebar-width)+1.25rem)] z-10 flex justify-center">
      <div className="pointer-events-auto flex w-full items-center rounded-lg border border-border-strong bg-surface-raised/95 px-4 py-3 shadow-[var(--shadow-floating)]">
        <div className="flex w-64 shrink-0 items-center gap-3">
          <span className="grid h-12 w-12 place-items-center rounded-md border border-border bg-surface-sunken text-xl font-semibold text-content">
            1
          </span>
          <div className="grid gap-0.5">
            <span className="text-base font-semibold text-content">item selected</span>
            <span className="text-sm text-content-muted">7.4 MB</span>
          </div>
        </div>
        <div className="hidden items-center gap-10 text-sm text-content-muted sm:flex">
          {actions.map(({ icon: Icon, label }) => (
            <button className="grid min-w-16 place-items-center gap-1 hover:text-content" key={label} type="button">
              <Icon aria-hidden="true" className="h-6 w-6" />
              <span>{label}</span>
            </button>
          ))}
          <button
            className="grid min-w-16 place-items-center gap-1 text-danger hover:text-danger-content"
            type="button"
          >
            <Trash2 aria-hidden="true" className="h-6 w-6" />
            <span>Delete</span>
          </button>
          <button
            className="grid min-w-16 place-items-center gap-1 hover:text-content"
            type="button"
            aria-label="More actions"
          >
            <MoreHorizontal aria-hidden="true" className="h-6 w-6" />
            <span className="sr-only">More</span>
          </button>
        </div>
        <button
          className="ml-auto grid h-12 w-12 place-items-center rounded-md border border-border bg-surface-sunken text-content-muted hover:text-content"
          type="button"
          aria-label="Close selection bar"
        >
          <X aria-hidden="true" className="h-7 w-7" />
        </button>
      </div>
    </div>
  );
}

export function LibraryNorthStar({ theme }: { theme?: 'light' }) {
  return (
    <div
      className="grid h-screen grid-rows-[var(--topbar-height)_1fr] overflow-hidden bg-canvas font-sans text-content"
      data-theme={theme}
    >
      <TopNav />
      <main className="grid min-h-0 grid-cols-[var(--rail-width)_1fr] overflow-hidden">
        <AppRail />
        <div className="grid min-w-0 lg:grid-cols-[var(--context-sidebar-width)_1fr]">
          <ContextSidebar />
          <section className="grid min-w-0 grid-cols-[1fr_3rem] overflow-hidden bg-canvas">
            <div className="min-w-0 overflow-auto px-4 py-5 md:px-6">
              <div className="grid gap-5 pb-4">
                <div className="flex flex-wrap items-center justify-between gap-3">
                  <div className="flex min-w-0 items-baseline gap-3">
                    <h1 className="text-2xl font-semibold leading-tight tracking-display text-content">All folders</h1>
                    <span className="text-sm text-content-muted">12,842 items</span>
                  </div>
                  <div className="flex items-center gap-2">
                    <div className="hidden h-12 items-center rounded-lg border border-border bg-surface-raised p-1 sm:flex">
                      <button
                        className="grid h-10 w-10 place-items-center rounded-md border border-border-strong bg-surface-sunken text-content shadow-[inset_0_0_0_1px_var(--color-border)]"
                        type="button"
                        aria-label="Grid view"
                        aria-pressed="true"
                      >
                        <Grid3X3 aria-hidden="true" className="h-4 w-4" strokeWidth={2.4} />
                      </button>
                      <button
                        className="grid h-10 w-10 place-items-center rounded-md text-content-muted"
                        type="button"
                        aria-label="List view"
                      >
                        <List aria-hidden="true" className="h-4 w-4" />
                      </button>
                    </div>
                    <div className="hidden h-12 items-center gap-3 rounded-lg border border-border bg-surface-raised px-3 text-content-muted md:flex">
                      <SlidersHorizontal aria-hidden="true" className="h-5 w-5" />
                      <label className="sr-only" htmlFor="library-zoom">
                        Zoom media grid
                      </label>
                      <input
                        className="h-1 w-28 accent-info"
                        defaultValue="48"
                        id="library-zoom"
                        max="100"
                        min="0"
                        type="range"
                      />
                      <button
                        className="grid h-8 w-8 place-items-center rounded-md hover:bg-surface-hover hover:text-content"
                        type="button"
                        aria-label="Fullscreen"
                      >
                        <Maximize2 aria-hidden="true" className="h-4 w-4" />
                      </button>
                    </div>
                  </div>
                </div>
                <AssetGrid />
                <SelectionBar />
              </div>
            </div>
            <Timeline />
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
