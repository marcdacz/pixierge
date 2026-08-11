import {
  AlertCircle,
  CheckCircle2,
  Download,
  Folder,
  FolderPlus,
  Grid3X3,
  List,
  Loader2,
  Maximize2,
  MoreHorizontal,
  Search,
  Share2,
  SlidersHorizontal,
  Star,
  Trash2,
  X
} from 'lucide-react';
import type { Meta, StoryObj } from '@storybook/react-vite';
import { Alert } from '../../src/components/ui/alert';
import { Badge } from '../../src/components/ui/badge';
import { Button } from '../../src/components/ui/button';
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '../../src/components/ui/card';
import { Input } from '../../src/components/ui/input';
import { Label } from '../../src/components/ui/label';
import { Skeleton } from '../../src/components/ui/skeleton';
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '../../src/components/ui/table';
import { Tooltip, TooltipContent, TooltipProvider, TooltipTrigger } from '../../src/components/ui/tooltip';

const meta = {
  title: 'Components/Primitives',
  parameters: {
    layout: 'fullscreen'
  }
} satisfies Meta;

export default meta;

type Story = StoryObj<typeof meta>;

const rows = [
  { name: 'Camera roll', count: '2,184', status: 'Synced', variant: 'success' as const },
  { name: 'Family archive', count: '842', status: 'Processing', variant: 'processing' as const },
  { name: 'Travel imports', count: '319', status: 'Review', variant: 'warning' as const }
];

function StorySurface({ children }: { children: React.ReactNode }) {
  return <div className="min-h-screen bg-canvas p-8 font-sans text-content">{children}</div>;
}

export const ButtonsAndBadges: Story = {
  render: () => (
    <StorySurface>
      <div className="grid max-w-4xl gap-8">
        <section className="grid gap-3">
          <h1 className="text-2xl font-semibold tracking-display">Buttons</h1>
          <div className="flex flex-wrap gap-3">
            <Button>Primary action</Button>
            <Button variant="secondary">Secondary</Button>
            <Button variant="ghost">Ghost</Button>
            <Button aria-pressed="true" className="bg-action-pressed text-action-content">
              Pressed
            </Button>
            <Button disabled>Disabled</Button>
            <Button disabled>
              <Loader2 aria-hidden="true" className="h-4 w-4 animate-spin" />
              Syncing
            </Button>
            <Button size="icon" aria-label="More actions">
              <MoreHorizontal aria-hidden="true" className="h-4 w-4" />
            </Button>
          </div>
        </section>

        <section className="grid gap-3">
          <h2 className="text-xl font-semibold">Badges</h2>
          <div className="flex flex-wrap gap-2">
            <Badge>Default</Badge>
            <Badge variant="secondary">Secondary</Badge>
            <Badge variant="success">Synced</Badge>
            <Badge variant="processing">Processing</Badge>
            <Badge variant="warning">Needs review</Badge>
            <Badge variant="danger">Missing</Badge>
            <Badge variant="unavailable">Offline</Badge>
          </div>
        </section>

        <section className="grid gap-3">
          <h2 className="text-xl font-semibold">Toolbar controls</h2>
          <div className="flex flex-wrap items-center gap-2">
            <div className="flex h-12 items-center rounded-lg border border-border bg-surface-raised p-1">
              <button
                aria-label="Grid view"
                aria-pressed="true"
                className="grid h-10 w-10 place-items-center rounded-md border border-border-strong bg-surface-sunken text-content shadow-[inset_0_0_0_1px_var(--color-border)]"
                type="button"
              >
                <Grid3X3 aria-hidden="true" className="h-4 w-4" strokeWidth={2.4} />
              </button>
              <button
                aria-label="List view"
                className="grid h-10 w-10 place-items-center rounded-md text-content-muted hover:bg-surface-hover"
                type="button"
              >
                <List aria-hidden="true" className="h-4 w-4" />
              </button>
            </div>
            <div className="flex h-12 items-center gap-3 rounded-lg border border-border bg-surface-raised px-3 text-content-muted">
              <SlidersHorizontal aria-hidden="true" className="h-5 w-5" />
              <label className="sr-only" htmlFor="primitive-zoom">
                Zoom media grid
              </label>
              <input
                className="h-1 w-28 accent-info"
                defaultValue="48"
                id="primitive-zoom"
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
        </section>
      </div>
    </StorySurface>
  )
};

export const InputsAlertsAndFeedback: Story = {
  render: () => (
    <StorySurface>
      <div className="grid max-w-3xl gap-6">
        <Card>
          <CardHeader>
            <CardTitle>Search input</CardTitle>
            <CardDescription>Default, focus, disabled, and invalid/error examples.</CardDescription>
          </CardHeader>
          <CardContent className="grid gap-4">
            <div className="grid gap-2">
              <Label htmlFor="search-default">Default</Label>
              <div className="relative">
                <Search aria-hidden="true" className="absolute top-2.5 left-3 h-5 w-5 text-content-muted" />
                <Input id="search-default" className="pl-10" placeholder="Search assets" />
              </div>
            </div>
            <div className="grid gap-2">
              <Label htmlFor="search-focus">Focus visible</Label>
              <Input
                id="search-focus"
                className="border-focus ring-2 ring-focus-shadow"
                defaultValue="wedding photos"
              />
            </div>
            <div className="grid gap-2">
              <Label htmlFor="search-invalid">Invalid</Label>
              <Input
                id="search-invalid"
                aria-describedby="search-invalid-error"
                aria-invalid="true"
                className="border-danger ring-2 ring-danger/20"
                defaultValue="date:maybe"
              />
              <p className="flex items-center gap-2 text-sm text-danger-content" id="search-invalid-error">
                <AlertCircle aria-hidden="true" className="h-4 w-4" />
                Use a supported date filter.
              </p>
            </div>
            <div className="grid gap-2">
              <Label htmlFor="search-disabled">Disabled</Label>
              <Input id="search-disabled" disabled placeholder="Unavailable while indexing" />
            </div>
          </CardContent>
        </Card>

        <Alert className="flex items-center gap-2">
          <AlertCircle aria-hidden="true" className="h-4 w-4" />
          Import paused until the source folder is reachable.
        </Alert>

        <div className="rounded-md border border-success bg-success-surface px-4 py-3 text-sm font-medium text-success-content">
          <CheckCircle2 aria-hidden="true" className="mr-2 inline h-4 w-4" />
          Collection synced successfully.
        </div>
      </div>
    </StorySurface>
  )
};

export const TableStates: Story = {
  render: () => (
    <StorySurface>
      <div className="grid max-w-5xl gap-6">
        <Card>
          <CardHeader>
            <CardTitle>Collection table</CardTitle>
            <CardDescription>Sortable lists use semantic row, status, and action treatments.</CardDescription>
          </CardHeader>
          <CardContent>
            <Table>
              <TableHeader>
                <TableRow>
                  <TableHead>Name</TableHead>
                  <TableHead>Assets</TableHead>
                  <TableHead>Status</TableHead>
                  <TableHead className="w-12">
                    <span className="sr-only">Actions</span>
                  </TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {rows.map((row) => (
                  <TableRow key={row.name}>
                    <TableCell className="font-medium">{row.name}</TableCell>
                    <TableCell>{row.count}</TableCell>
                    <TableCell>
                      <Badge variant={row.variant}>{row.status}</Badge>
                    </TableCell>
                    <TableCell>
                      <Button size="icon" variant="ghost" aria-label={`Open actions for ${row.name}`}>
                        <MoreHorizontal aria-hidden="true" className="h-4 w-4" />
                      </Button>
                    </TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          </CardContent>
        </Card>

        <Card>
          <CardHeader>
            <CardTitle>Empty table</CardTitle>
            <CardDescription>No rows are available for the current filter.</CardDescription>
          </CardHeader>
          <CardContent>
            <div className="grid min-h-32 place-items-center rounded-md border border-dashed border-border p-6 text-center">
              <div className="grid gap-2">
                <p className="font-medium text-content">No matching collections</p>
                <p className="text-sm text-content-muted">Clear filters or import assets to populate this view.</p>
              </div>
            </div>
          </CardContent>
        </Card>

        <Card>
          <CardHeader>
            <CardTitle>Loading table</CardTitle>
          </CardHeader>
          <CardContent className="grid gap-3">
            {Array.from({ length: 3 }, (_, index) => (
              <Skeleton className="h-12" key={index} />
            ))}
          </CardContent>
        </Card>
      </div>
    </StorySurface>
  )
};

export const SelectionBar: Story = {
  render: () => {
    const actions = [
      { icon: Star, label: 'Star' },
      { icon: Share2, label: 'Share' },
      { icon: Download, label: 'Download' },
      { icon: FolderPlus, label: 'Add to' },
      { icon: Folder, label: 'Move' }
    ];

    return (
      <StorySurface>
        <div className="grid min-h-[26rem] content-end">
          <div className="flex w-full max-w-5xl items-center rounded-lg border border-border-strong bg-surface-raised/95 px-4 py-3 shadow-[var(--shadow-floating)]">
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
      </StorySurface>
    );
  }
};

export const TooltipStates: Story = {
  render: () => (
    <StorySurface>
      <TooltipProvider>
        <Tooltip defaultOpen>
          <TooltipTrigger asChild>
            <Button variant="secondary">Hover or focus</Button>
          </TooltipTrigger>
          <TooltipContent>Tooltips describe compact controls without blocking the workflow.</TooltipContent>
        </Tooltip>
      </TooltipProvider>
    </StorySurface>
  )
};
