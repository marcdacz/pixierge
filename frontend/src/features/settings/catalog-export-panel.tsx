import { Download, RefreshCw } from 'lucide-react';
import { useEffect, useState } from 'react';
import {
  ApiError,
  exportCatalog,
  fetchCatalogHistory,
  fetchCatalogStatus,
  type AuthResponse,
  type CatalogHistory,
  type CatalogStatus
} from '@/api';
import { Alert } from '@/components/ui/alert';
import { Badge } from '@/components/ui/badge';
import { Button } from '@/components/ui/button';
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table';

const HISTORY_PAGE_SIZE = 10;

export function CatalogExportPanel({
  auth,
  onError
}: {
  auth: AuthResponse;
  onError: (title: string, description?: string) => void;
}) {
  const [status, setStatus] = useState<CatalogStatus | null>(null);
  const [history, setHistory] = useState<CatalogHistory | null>(null);
  const [page, setPage] = useState(0);
  const [loading, setLoading] = useState(true);
  const [exporting, setExporting] = useState(false);

  async function load(nextPage = page) {
    setLoading(true);
    try {
      const [nextStatus, nextHistory] = await Promise.all([
        fetchCatalogStatus(),
        fetchCatalogHistory(nextPage, HISTORY_PAGE_SIZE)
      ]);
      setStatus(nextStatus);
      setHistory(nextHistory);
    } catch (error) {
      onError('Catalog export could not be loaded', error instanceof ApiError ? error.message : undefined);
    } finally {
      setLoading(false);
    }
  }

  useEffect(() => {
    void load(page);
  }, [page]);

  async function handleExport() {
    setExporting(true);
    try {
      await exportCatalog(auth.csrfToken);
      if (page !== 0) {
        setPage(0);
      } else {
        await load(0);
      }
    } catch (error) {
      onError('Catalog export failed', error instanceof ApiError ? error.message : undefined);
    } finally {
      setExporting(false);
    }
  }

  if (loading && !history) {
    return <p className="text-sm text-muted-foreground">Loading catalog export…</p>;
  }

  const state = status?.status ?? 'degraded';
  return (
    <div className="grid gap-6">
      <section
        aria-label="Catalog export status"
        className="grid gap-4 rounded-md border border-border p-4 md:grid-cols-[minmax(0,1fr)_auto] md:items-center"
      >
        <div className="grid gap-1">
          <div className="flex items-center gap-2">
            <h3 className="font-medium">Catalog export</h3>
            <Badge variant={state === 'current' ? 'success' : 'warning'}>{state}</Badge>
          </div>
          <p className="text-sm text-muted-foreground">
            {state === 'current'
              ? 'No new catalog changes since the last export. You can still create a fresh export.'
              : state === 'lagging'
                ? `${status?.pendingEventCount ?? 0} change${status?.pendingEventCount === 1 ? '' : 's'} waiting to export.`
                : 'The latest catalog export needs attention.'}
          </p>
        </div>
        <Button disabled={exporting} onClick={() => void handleExport()} type="button">
          <Download className="h-4 w-4" aria-hidden />
          {exporting ? 'Exporting…' : 'Export now'}
        </Button>
      </section>

      {status?.failureDetail && <Alert>{status.failureDetail}</Alert>}

      <section className="grid gap-3">
        <div className="flex items-center justify-between gap-3">
          <h3 className="font-medium">Export history</h3>
          <Button
            aria-label="Refresh catalog export history"
            disabled={loading}
            onClick={() => void load(page)}
            size="icon"
            type="button"
            variant="ghost"
          >
            <RefreshCw className="h-4 w-4" aria-hidden />
          </Button>
        </div>

        {!history?.items.length ? (
          <p className="rounded-md border border-dashed border-border p-6 text-sm text-muted-foreground">
            No catalog exports have been created yet.
          </p>
        ) : (
          <>
            <div className="overflow-x-auto rounded-md border border-border">
              <Table>
                <TableHeader>
                  <TableRow>
                    <TableHead>Created</TableHead>
                    <TableHead>Status</TableHead>
                    <TableHead>Through</TableHead>
                    <TableHead>Size</TableHead>
                    <TableHead>Checksum</TableHead>
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
                      <TableCell>{snapshot.throughSequence}</TableCell>
                      <TableCell>{formatBytes(snapshot.byteSize)}</TableCell>
                      <TableCell className="font-mono text-xs">
                        {snapshot.checksum ? `${snapshot.checksum.slice(0, 12)}…` : '—'}
                      </TableCell>
                    </TableRow>
                  ))}
                </TableBody>
              </Table>
            </div>
            <div className="flex items-center justify-end gap-2">
              <span className="mr-auto text-sm text-muted-foreground">Page {page + 1}</span>
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
          </>
        )}
      </section>
    </div>
  );
}

function formatBytes(bytes: number) {
  return bytes < 1024 ? `${bytes} B` : `${(bytes / 1024).toFixed(1)} KB`;
}
