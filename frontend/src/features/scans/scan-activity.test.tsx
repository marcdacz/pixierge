import '@testing-library/jest-dom/vitest';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import type { ActiveScan } from '@/api';
import { ScanActivityProvider } from '@/features/scans/scan-activity-context';
import { ScanActivityButton } from '@/features/scans/scan-activity-button';

const baseScan: ActiveScan = {
  id: 'scan-1',
  libraryId: 'library-1',
  libraryName: 'Family Photos',
  rootId: null,
  rootPath: null,
  status: 'running',
  startedAt: '2026-07-04T00:00:00Z',
  scannedFileCount: 12,
  addedCount: 4,
  unchangedCount: 6,
  movedCount: 2,
  modifiedCount: 1,
  duplicateCount: 3,
  missingCount: 0,
  reappearedCount: 4,
  errorCount: 1
};

vi.mock('@/api', async () => {
  const actual = await vi.importActual<typeof import('@/api')>('@/api');
  return {
    ...actual,
    fetchActiveScans: vi.fn(async () => []),
    fetchScan: vi.fn()
  };
});

import { fetchActiveScans } from '@/api';

describe('ScanActivityButton', () => {
  beforeEach(() => {
    vi.mocked(fetchActiveScans).mockResolvedValue([]);
  });

  it('renders nothing when idle', async () => {
    const { container } = render(
      <ScanActivityProvider>
        <ScanActivityButton onOpenSettings={() => {}} />
      </ScanActivityProvider>
    );

    await waitFor(() => expect(fetchActiveScans).toHaveBeenCalled());
    expect(container).toBeEmptyDOMElement();
  });

  it('shows a spinning indicator while scans are running', async () => {
    vi.mocked(fetchActiveScans).mockResolvedValue([baseScan]);

    render(
      <ScanActivityProvider>
        <ScanActivityButton onOpenSettings={() => {}} />
      </ScanActivityProvider>
    );

    expect(await screen.findByRole('button', { name: 'Scan activity' })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Scan activity' }).querySelector('.animate-spin')).toBeTruthy();
  });

  it('opens counts and settings navigation from the popover', async () => {
    vi.mocked(fetchActiveScans).mockResolvedValue([baseScan]);

    render(
      <ScanActivityProvider>
        <ScanActivityButton onOpenSettings={() => {}} />
      </ScanActivityProvider>
    );

    await userEvent.click(await screen.findByRole('button', { name: 'Scan activity' }));
    expect(screen.getByText('Family Photos')).toBeInTheDocument();
    expect(screen.getByText('Scanned 12')).toBeInTheDocument();
    expect(screen.getByText('Added 4')).toBeInTheDocument();
    expect(screen.getByText('Unchanged 6')).toBeInTheDocument();
    expect(screen.getByText('Moved 2')).toBeInTheDocument();
    expect(screen.getByText('Modified 1')).toBeInTheDocument();
    expect(screen.getByText('Duplicates 3')).toBeInTheDocument();
    expect(screen.getByText('Reappeared 4')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Open scan settings' })).toBeInTheDocument();
  });
});
