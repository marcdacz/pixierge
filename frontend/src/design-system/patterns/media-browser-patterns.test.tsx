import { cleanup, fireEvent, render, screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { Download, FolderPlus, Star, Trash2 } from 'lucide-react';
import { afterEach, describe, expect, it, vi } from 'vitest';
import {
  BulkActionBar,
  MediaGrid,
  PageHeader,
  ThumbnailSizeControl,
  TimelineRail,
  ToolbarViewToggle,
  type MediaGroup,
  type TimelineRailItem
} from './index';

const mediaGroups: MediaGroup[] = [
  {
    date: 'May 24, 2025',
    count: '3 items',
    assets: [
      { label: 'Lighthouse', background: 'linear-gradient(135deg, #f9a8d4, #67e8f9)', mode: 'wide' },
      { label: 'Alpine lake', background: 'linear-gradient(135deg, #fde68a, #0e7490)' },
      { label: 'Selected clip', background: 'linear-gradient(135deg, #111827, #2563eb)', mode: 'selected' }
    ]
  }
];

const timelineItems: TimelineRailItem[] = [
  { label: '2025', kind: 'year' },
  { label: 'May', kind: 'active' },
  { kind: 'active-line' },
  { kind: 'dots' }
];

afterEach(() => cleanup());

describe('media browser patterns', () => {
  it('renders a page header with equal-height toolbar controls', () => {
    render(
      <PageHeader
        actions={
          <>
            <ToolbarViewToggle value="grid" />
            <ThumbnailSizeControl id="test-zoom" />
          </>
        }
        meta="12,842 items"
        title="All folders"
      />
    );

    expect(screen.getByRole('heading', { name: 'All folders' })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Grid view' })).toHaveAttribute('aria-pressed', 'true');
    expect(screen.getByRole('button', { name: 'List view' })).toHaveAttribute('aria-pressed', 'false');
    expect(screen.getByRole('slider', { name: 'Zoom media grid' })).toHaveValue('48');
  });

  it('renders selected media state and timeline active state accessibly', () => {
    render(
      <>
        <MediaGrid groups={mediaGroups} />
        <TimelineRail items={timelineItems} className="md:grid" />
      </>
    );

    const selectedTile = screen.getByRole('img', { name: 'Selected clip' });
    expect(selectedTile).toHaveClass('ring-2', 'ring-focus');
    expect(within(selectedTile).getByRole('button', { name: 'Asset actions' })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'May' })).toHaveAttribute('aria-current', 'date');
  });

  it('supports thumbnail, view, and bulk action callbacks', async () => {
    const user = userEvent.setup();
    const onViewChange = vi.fn();
    const onZoomChange = vi.fn();
    const onClear = vi.fn();
    const onDelete = vi.fn();

    render(
      <>
        <ToolbarViewToggle value="grid" onChange={onViewChange} />
        <ThumbnailSizeControl id="callback-zoom" onChange={onZoomChange} />
        <BulkActionBar
          actions={[
            { icon: Star, label: 'Star' },
            { icon: Download, label: 'Download' },
            { icon: FolderPlus, label: 'Add to' },
            { icon: Trash2, label: 'Delete', destructive: true, onSelect: onDelete }
          ]}
          className="static"
          onClear={onClear}
          selectedCount={1}
          summary="7.4 MB"
        />
      </>
    );

    await user.click(screen.getAllByRole('button', { name: 'List view' }).at(-1)!);
    await user.click(screen.getByRole('button', { name: 'Delete' }));
    await user.click(screen.getByRole('button', { name: 'Close selection bar' }));

    expect(onViewChange).toHaveBeenCalledWith('list');
    expect(onDelete).toHaveBeenCalledTimes(1);
    expect(onClear).toHaveBeenCalledTimes(1);

    fireEvent.change(screen.getByRole('slider', { name: 'Zoom media grid' }), { target: { value: '64' } });
    expect(onZoomChange).toHaveBeenCalledWith(64);
  });
});
