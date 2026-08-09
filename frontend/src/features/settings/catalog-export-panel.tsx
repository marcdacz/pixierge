import { Download, RefreshCw } from 'lucide-react';
import { useEffect, useState } from 'react';
import {
  ApiError,
  createDatabaseBackup,
  databaseBackupDownloadUrl,
  fetchDatabaseBackups,
  restoreDatabaseBackup,
  type AuthResponse,
  type DatabaseBackupHistory
} from '@/api';
import { Alert } from '@/components/ui/alert';
import { Badge } from '@/components/ui/badge';
import { Button } from '@/components/ui/button';
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table';

const HISTORY_PAGE_SIZES = [25, 50, 100] as const;
type HistoryPageSize = (typeof HISTORY_PAGE_SIZES)[number];
const DEFAULT_HISTORY_PAGE_SIZE: HistoryPageSize = 25;

type BackupRestoreTab = 'backup' | 'restore';

export function CatalogExportPanel({
  auth,
  onError
}: {
  auth: AuthResponse;
  onError: (title: string, description?: string) => void;
}) {
  const [history, setHistory] = useState<DatabaseBackupHistory | null>(null);
  const [activeTab, setActiveTab] = useState<BackupRestoreTab>('backup');
  const [page, setPage] = useState(0);
  const [pageSize, setPageSize] = useState<HistoryPageSize>(DEFAULT_HISTORY_PAGE_SIZE);
  const [loading, setLoading] = useState(true);
  const [exporting, setExporting] = useState(false);
  const [restorePath, setRestorePath] = useState('pixierge-db-backup.dump');
  const [restoreConfirmationOpen, setRestoreConfirmationOpen] = useState(false);

  async function load(nextPage = page, nextPageSize = pageSize) {
    setLoading(true);
    try {
      const nextHistory = await fetchDatabaseBackups(nextPage, nextPageSize);
      setHistory(nextHistory);
    } catch (error) {
      onError('Database backups could not be loaded', error instanceof ApiError ? error.message : undefined);
    } finally {
      setLoading(false);
    }
  }

  useEffect(() => {
    void load(page);
  }, [page, pageSize]);

  async function handleExport() {
    setExporting(true);
    try {
      await createDatabaseBackup(auth.csrfToken);
      if (page !== 0) {
        setPage(0);
      } else {
        await load(0);
      }
    } catch (error) {
      onError('Database backup failed', error instanceof ApiError ? error.message : undefined);
    } finally {
      setExporting(false);
    }
  }

  async function handleRestore() {
    setExporting(true);
    try {
      await restoreDatabaseBackup(restorePath.trim(), auth.csrfToken);
      setRestoreConfirmationOpen(false);
      await load(0);
    } catch (error) {
      onError('Database restore failed', error instanceof ApiError ? error.message : undefined);
    } finally {
      setExporting(false);
    }
  }

  if (loading && !history && activeTab === 'backup') {
    return <p className="text-sm text-muted-foreground">Loading database backups…</p>;
  }

  const totalPages = history ? Math.max(1, Math.ceil(history.totalCount / pageSize)) : 1;
  return (
    <div className="grid gap-6">
      <div aria-label="Backup and Restore sections" className="flex flex-wrap gap-1" role="tablist">
        <button
          aria-controls="catalog-backup-panel"
          aria-selected={activeTab === 'backup'}
          className={`rounded-md px-3 py-2 text-sm font-medium transition-colors ${
            activeTab === 'backup'
              ? 'bg-muted text-foreground'
              : 'text-muted-foreground hover:bg-muted/60 hover:text-foreground'
          }`}
          onClick={() => setActiveTab('backup')}
          role="tab"
          type="button"
        >
          Backup
        </button>
        <button
          aria-controls="catalog-restore-panel"
          aria-selected={activeTab === 'restore'}
          className={`rounded-md px-3 py-2 text-sm font-medium transition-colors ${
            activeTab === 'restore'
              ? 'bg-muted text-foreground'
              : 'text-muted-foreground hover:bg-muted/60 hover:text-foreground'
          }`}
          onClick={() => setActiveTab('restore')}
          role="tab"
          type="button"
        >
          Restore system
        </button>
      </div>

      {activeTab === 'backup' ? (
        <section
          aria-labelledby="catalog-backup-heading"
          className="grid gap-3"
          id="catalog-backup-panel"
          role="tabpanel"
        >
          <div className="flex items-center justify-between gap-3">
            <div>
              <h3 className="font-medium" id="catalog-backup-heading">
                Database backups
              </h3>
              <p className="mt-1 text-sm text-muted-foreground">
                Download completed database backups to your external backup location.
              </p>
            </div>
            <div className="flex items-center gap-1">
              <Button
                aria-label="Refresh database backups"
                disabled={loading}
                onClick={() => void load(page)}
                size="icon"
                type="button"
                variant="ghost"
              >
                <RefreshCw className="h-4 w-4" aria-hidden />
              </Button>
              <Button disabled={exporting} onClick={() => void handleExport()} type="button">
                <Download className="h-4 w-4" aria-hidden />
                {exporting ? 'Creating…' : 'Create database backup'}
              </Button>
            </div>
          </div>

          {!history?.items.length ? (
            <p className="rounded-md border border-dashed border-border p-6 text-sm text-muted-foreground">
              No database backups have been created yet.
            </p>
          ) : (
            <>
              <div className="overflow-x-auto rounded-md border border-border">
                <Table>
                  <TableHeader>
                    <TableRow>
                      <TableHead>Created</TableHead>
                      <TableHead>Status</TableHead>
                      <TableHead>Size</TableHead>
                      <TableHead>Integrity</TableHead>
                      <TableHead className="text-right">Actions</TableHead>
                    </TableRow>
                  </TableHeader>
                  <TableBody>
                    {history.items.map((snapshot) => (
                      <TableRow key={snapshot.id}>
                        <TableCell>{new Date(snapshot.createdAt).toLocaleString()}</TableCell>
                        <TableCell>
                          <Badge variant={snapshot.status === 'completed' ? 'success' : 'warning'}>
                            {snapshot.status}
                          </Badge>
                        </TableCell>
                        <TableCell>{formatBytes(snapshot.byteSize)}</TableCell>
                        <TableCell>
                          {snapshot.status === 'completed' && snapshot.checksum ? (
                            <Badge variant="success">Verified</Badge>
                          ) : (
                            '—'
                          )}
                        </TableCell>
                        <TableCell className="text-right">
                          {snapshot.status === 'completed' ? (
                            <Button asChild size="sm" type="button" variant="secondary">
                              <a href={databaseBackupDownloadUrl(snapshot.id)}>Download</a>
                            </Button>
                          ) : null}
                        </TableCell>
                      </TableRow>
                    ))}
                  </TableBody>
                </Table>
              </div>
              <div className="flex flex-wrap items-center justify-between gap-3">
                <p className="text-sm text-muted-foreground">
                  Page {page + 1} of {totalPages} ·{' '}
                  {history.totalCount === 1 ? '1 database backup' : `${history.totalCount} database backups`}
                </p>
                <div className="flex flex-wrap items-center gap-2">
                  <label className="flex items-center gap-2 text-sm text-muted-foreground">
                    <span className="whitespace-nowrap">Per page</span>
                    <select
                      aria-label="Database backups per page"
                      className="h-10 w-auto rounded-md border border-input bg-background px-3 text-sm text-foreground shadow-sm outline-none transition-colors hover:bg-muted focus-visible:border-ring focus-visible:ring-2 focus-visible:ring-ring/25"
                      onChange={(event) => {
                        setPageSize(Number(event.target.value) as HistoryPageSize);
                        setPage(0);
                      }}
                      value={pageSize}
                    >
                      {HISTORY_PAGE_SIZES.map((size) => (
                        <option key={size} value={size}>
                          {size}
                        </option>
                      ))}
                    </select>
                  </label>
                  <Button
                    disabled={page === 0 || loading}
                    onClick={() => setPage((current) => Math.max(0, current - 1))}
                    type="button"
                    variant="secondary"
                  >
                    Previous
                  </Button>
                  <Button
                    disabled={!history.hasNext || loading}
                    onClick={() => setPage((current) => current + 1)}
                    type="button"
                    variant="secondary"
                  >
                    Next
                  </Button>
                </div>
              </div>
            </>
          )}
        </section>
      ) : (
        <section
          aria-labelledby="catalog-restore-heading"
          className="grid gap-4"
          id="catalog-restore-panel"
          role="tabpanel"
        >
          <div className="grid gap-1">
            <h3 className="font-medium" id="catalog-restore-heading">
              Restore database backup
            </h3>
            <p className="text-sm text-muted-foreground">
              Copy the database backup into Pixierge’s configured recovery import location, then enter its relative
              path.
            </p>
          </div>
          <label className="grid gap-2 text-sm text-muted-foreground">
            Database backup path
            <div className="flex min-w-0">
              <span className="rounded-l-md border border-r-0 border-input bg-muted px-3 py-2 font-mono text-xs text-muted-foreground">
                recovery-import/
              </span>
              <input
                aria-label="Database backup path"
                className="min-w-0 flex-1 rounded-r-md border border-input bg-background px-3 py-2 text-sm text-foreground outline-none focus-visible:border-ring focus-visible:ring-2 focus-visible:ring-ring/25"
                onChange={(event) => setRestorePath(event.target.value)}
                value={restorePath}
              />
            </div>
          </label>
          <Alert>Pixierge validates the selected backup before restoring. Source media remains in place.</Alert>
          <div className="flex justify-end">
            <Button disabled={!restorePath.trim()} onClick={() => setRestoreConfirmationOpen(true)} type="button">
              Restore database backup
            </Button>
          </div>
        </section>
      )}

      {restoreConfirmationOpen && (
        <div className="fixed inset-0 z-50 grid place-items-center bg-black/60 p-4">
          <div
            aria-labelledby="catalog-restore-confirmation-title"
            aria-modal="true"
            className="grid w-full max-w-md gap-4 rounded-md border border-border bg-surface p-5 text-foreground shadow-lg"
            role="dialog"
          >
            <div className="grid gap-2">
              <h2 className="text-lg font-semibold" id="catalog-restore-confirmation-title">
                Restore this database backup?
              </h2>
              <p className="text-sm text-muted-foreground">recovery-import/{restorePath}</p>
              <p className="text-sm text-muted-foreground">
                This replaces the current Pixierge database. Your photos and videos remain in place.
              </p>
            </div>
            <div className="flex justify-end gap-2">
              <Button onClick={() => setRestoreConfirmationOpen(false)} type="button" variant="ghost">
                Cancel
              </Button>
              <Button onClick={() => void handleRestore()} type="button" variant="secondary">
                Restore database backup
              </Button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}

function formatBytes(bytes: number) {
  return bytes < 1024 ? `${bytes} B` : `${(bytes / 1024).toFixed(1)} KB`;
}
