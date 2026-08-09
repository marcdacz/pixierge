import { useEffect, useState } from 'react';
import { ApiError, fetchAuditEvents, type AuditHistory } from '@/api';
import { Badge } from '@/components/ui/badge';
import { Button } from '@/components/ui/button';
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table';

export function AuditLogPanel({ onError }: { onError: (title: string, description?: string) => void }) {
  const [history, setHistory] = useState<AuditHistory | null>(null);
  const [query, setQuery] = useState('');
  const [page, setPage] = useState(0);
  const [loading, setLoading] = useState(true);

  async function load(nextPage = page, nextQuery = query) {
    setLoading(true);
    try {
      setHistory(await fetchAuditEvents({ page: nextPage, pageSize: 25, q: nextQuery }));
    } catch (error) {
      onError('Audit log could not be loaded', error instanceof ApiError ? error.message : undefined);
    } finally {
      setLoading(false);
    }
  }

  useEffect(() => {
    void load(page);
  }, [page]);

  return (
    <section className="grid gap-4" aria-labelledby="audit-log-heading">
      <div>
        <h3 className="font-medium" id="audit-log-heading">
          Audit log
        </h3>
        <p className="mt-1 text-sm text-muted-foreground">
          Track important changes made to Pixierge. Events are retained for 90 days.
        </p>
      </div>
      <form
        className="flex flex-wrap gap-2"
        onSubmit={(event) => {
          event.preventDefault();
          setPage(0);
          void load(0);
        }}
      >
        <input
          aria-label="Search audit log"
          className="h-10 min-w-56 flex-1 rounded-md border border-input bg-background px-3 text-sm"
          onChange={(event) => setQuery(event.target.value)}
          placeholder="Search actions"
          value={query}
        />
        <Button disabled={loading} type="submit" variant="secondary">
          Search
        </Button>
      </form>
      {!history?.items.length && !loading ? (
        <p className="rounded-md border border-dashed border-border p-6 text-sm text-muted-foreground">
          No audit events match these filters.
        </p>
      ) : (
        <>
          <div className="overflow-x-auto rounded-md border border-border">
            <Table>
              <TableHeader>
                <TableRow>
                  <TableHead>When</TableHead>
                  <TableHead>Who</TableHead>
                  <TableHead>Action</TableHead>
                  <TableHead>Area</TableHead>
                  <TableHead>Resource</TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {history?.items.map((event) => (
                  <TableRow key={event.id}>
                    <TableCell>{new Date(event.createdAt).toLocaleString()}</TableCell>
                    <TableCell title={event.actorUserId ?? undefined}>
                      {event.actorUsername ?? (event.actorUserId ? 'Deleted user' : 'System')}
                    </TableCell>
                    <TableCell>{event.action.replaceAll('.', ' ')}</TableCell>
                    <TableCell>
                      <Badge variant="secondary">{event.area}</Badge>
                    </TableCell>
                    <TableCell>{event.resourceType}</TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          </div>
          <div className="flex items-center justify-between gap-3">
            <p className="text-sm text-muted-foreground">
              Page {page + 1} · {history?.totalCount ?? 0} events
            </p>
            <div className="flex gap-2">
              <Button
                disabled={page === 0 || loading}
                onClick={() => setPage((value) => value - 1)}
                type="button"
                variant="secondary"
              >
                Previous
              </Button>
              <Button
                disabled={!history?.hasNext || loading}
                onClick={() => setPage((value) => value + 1)}
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
  );
}
