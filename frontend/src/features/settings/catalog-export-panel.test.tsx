import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { CatalogExportPanel } from '@/features/settings/catalog-export-panel';

const { fetchDatabaseBackups, createDatabaseBackup } = vi.hoisted(() => ({
  fetchDatabaseBackups: vi.fn(),
  createDatabaseBackup: vi.fn()
}));

vi.mock('@/api', async () => {
  const actual = await vi.importActual<typeof import('@/api')>('@/api');
  return { ...actual, fetchDatabaseBackups, createDatabaseBackup };
});

describe('CatalogExportPanel', () => {
  beforeEach(() => {
    cleanup();
    vi.clearAllMocks();
    fetchDatabaseBackups.mockResolvedValue({
      items: [
        {
          id: 'backup-1',
          createdAt: '2026-08-07T00:42:00Z',
          byteSize: 2048,
          checksum: 'a'.repeat(64),
          postgresVersion: '16.4',
          schemaVersion: '22',
          status: 'completed',
          failureDetail: null
        }
      ],
      page: 0,
      pageSize: 25,
      totalCount: 63,
      hasNext: true
    });
  });

  it('shows streamlined paginated database backups', async () => {
    render(<CatalogExportPanel auth={{ csrfToken: 'csrf', user: {} as never }} onError={vi.fn()} />);

    expect(await screen.findByRole('heading', { name: 'Database backups' })).toBeInTheDocument();
    expect(screen.getByText('Page 1 of 3 · 63 database backups')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Previous' })).toBeDisabled();
    expect(screen.getByRole('cell', { name: 'Verified' })).toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'Download' })).toHaveAttribute(
      'href',
      'http://localhost:8080/api/admin/backups/backup-1/download'
    );
    expect(screen.queryByText('Through')).not.toBeInTheDocument();

    fireEvent.click(screen.getByRole('button', { name: 'Next' }));
    await waitFor(() => expect(fetchDatabaseBackups).toHaveBeenLastCalledWith(1, 25));
  });

  it('asks for confirmation before restoring an import path', async () => {
    render(<CatalogExportPanel auth={{ csrfToken: 'csrf', user: {} as never }} onError={vi.fn()} />);

    await screen.findByRole('heading', { name: 'Database backups' });
    fireEvent.click(screen.getByRole('tab', { name: 'Restore system' }));
    fireEvent.change(screen.getByLabelText('Database backup path'), { target: { value: 'weekly/backup.dump' } });
    fireEvent.click(screen.getByRole('button', { name: 'Restore database backup' }));

    expect(screen.getByRole('dialog', { name: 'Restore this database backup?' })).toBeInTheDocument();
    expect(screen.getByText('recovery-import/weekly/backup.dump')).toBeInTheDocument();
  });
});
