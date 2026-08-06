import { Calendar, Check, ChevronDown, ChevronRight, Maximize2, Trash2, X } from 'lucide-react';
import { Fragment, useEffect, useRef, useState } from 'react';
import {
  ApiError,
  fetchAsset,
  fetchBackgroundWorkActivity,
  fetchBackgroundWorkConfig,
  fetchBackgroundWorkFiles,
  clearBackgroundWorkFiles,
  fetchBackgroundWorkHealth,
  recoverDeadLetterMetadata,
  type AuthResponse,
  type BackgroundFileActivityPage,
  type BackgroundJobProblemSummary,
  type BackgroundWorkActivity,
  type BackgroundWorkConfig,
  type BackgroundWorkHealth,
  type AssetDetail
} from '@/api';
import { Alert } from '@/components/ui/alert';
import { Badge } from '@/components/ui/badge';
import { Button } from '@/components/ui/button';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuTrigger
} from '@/components/ui/dropdown-menu';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table';
import { formatScanTimestamp } from '@/features/scans/scan-utils';
import { AssetFocus } from '@/features/library/photo-grid';
import { cn } from '@/lib/utils';

type BackgroundTab = 'jobs' | 'files' | 'configuration';

const BACKGROUND_TABS: Array<{ id: BackgroundTab; label: string; testId: string }> = [
  { id: 'jobs', label: 'Jobs', testId: 'background-tab-jobs' },
  { id: 'files', label: 'Recent file activity', testId: 'background-tab-files' },
  { id: 'configuration', label: 'Configuration', testId: 'background-tab-configuration' }
];

const FILE_ACTIVITY_PAGE_SIZES = [25, 50, 100] as const;
type FileActivityPageSize = (typeof FILE_ACTIVITY_PAGE_SIZES)[number];
const DEFAULT_FILE_ACTIVITY_PAGE_SIZE: FileActivityPageSize = 25;
const BACKGROUND_POLL_MS = 5000;
const SEARCH_DEBOUNCE_MS = 300;

const FILE_STATUS_OPTIONS = [
  { value: 'pending', label: 'Pending' },
  { value: 'processing', label: 'Processing' },
  { value: 'extracted', label: 'Extracted' },
  { value: 'unsupported', label: 'Unsupported' },
  { value: 'stale', label: 'Stale' },
  { value: 'added', label: 'Added' },
  { value: 'unchanged', label: 'Unchanged' },
  { value: 'moved', label: 'Moved' },
  { value: 'renamed', label: 'Renamed' },
  { value: 'modified', label: 'Modified' },
  { value: 'duplicate', label: 'Duplicate' },
  { value: 'missing', label: 'Missing' },
  { value: 'reappeared', label: 'Reappeared' },
  { value: 'error', label: 'Error' },
  { value: 'failed', label: 'Failed' }
] as const;

const FILTER_FIELD_CLASS =
  'h-10 min-w-0 w-full rounded-md border border-input bg-background px-3 text-left text-sm text-foreground shadow-sm outline-none transition-colors hover:bg-muted focus-visible:border-ring focus-visible:ring-2 focus-visible:ring-ring/25';

type UpdatedFilter =
  | { mode: 'any' }
  | { mode: 'last1h' }
  | { mode: 'last7' }
  | { mode: 'last30' }
  | { mode: 'last90' }
  | { mode: 'on'; on: string }
  | { mode: 'range'; from: string; to: string };

export function BackgroundWorkHealthPanel({
  auth,
  onError
}: {
  auth?: AuthResponse;
  onError: (title: string, description?: string) => void;
}) {
  const [activeTab, setActiveTab] = useState<BackgroundTab>('jobs');

  return (
    <div className="grid gap-6">
      <div aria-label="Background work sections" className="flex flex-wrap gap-1" role="tablist">
        {BACKGROUND_TABS.map((tab) => {
          const selected = activeTab === tab.id;
          return (
            <button
              aria-controls={`background-panel-${tab.id}`}
              aria-selected={selected}
              className={cn(
                'rounded-md px-3 py-2 text-sm font-medium transition-colors',
                selected ? 'bg-muted text-foreground' : 'text-muted-foreground hover:bg-muted/60 hover:text-foreground'
              )}
              data-testid={tab.testId}
              id={`background-tab-${tab.id}`}
              key={tab.id}
              onClick={() => setActiveTab(tab.id)}
              role="tab"
              type="button"
            >
              {tab.label}
            </button>
          );
        })}
      </div>

      <div
        aria-labelledby={`background-tab-${activeTab}`}
        className="grid gap-6"
        id={`background-panel-${activeTab}`}
        role="tabpanel"
      >
        {activeTab === 'jobs' ? (
          <BackgroundJobsPanel auth={auth} onError={onError} />
        ) : activeTab === 'files' ? (
          <BackgroundFileActivityPanel auth={auth} onError={onError} />
        ) : (
          <BackgroundConfigurationPanel onError={onError} />
        )}
      </div>
    </div>
  );
}

function BackgroundJobsPanel({
  auth,
  onError
}: {
  auth?: AuthResponse;
  onError: (title: string, description?: string) => void;
}) {
  const [health, setHealth] = useState<BackgroundWorkHealth | null>(null);
  const [activity, setActivity] = useState<BackgroundWorkActivity | null>(null);
  const [expandedProblemIds, setExpandedProblemIds] = useState<Set<string>>(new Set());
  const [loading, setLoading] = useState(true);
  const [loadError, setLoadError] = useState<string | null>(null);
  const [recoveryMessage, setRecoveryMessage] = useState<string | null>(null);
  const [recovering, setRecovering] = useState(false);

  async function loadJobs(options: { showLoading?: boolean } = {}) {
    if (options.showLoading) {
      setLoading(true);
    }
    try {
      const [nextHealth, nextActivity] = await Promise.all([
        fetchBackgroundWorkHealth(),
        fetchBackgroundWorkActivity(100)
      ]);
      setHealth(nextHealth);
      setActivity(nextActivity);
      setLoadError(null);
    } catch (error) {
      const message = messageForError(error, 'Background work health could not be loaded.');
      setLoadError(message);
      onError('Background work health could not be loaded', message);
    } finally {
      if (options.showLoading) {
        setLoading(false);
      }
    }
  }

  useEffect(() => {
    void loadJobs({ showLoading: true });
  }, []);

  useEffect(() => {
    const timer = window.setInterval(() => {
      void loadJobs();
    }, BACKGROUND_POLL_MS);
    return () => window.clearInterval(timer);
  }, []);

  if (loading && health === null) {
    return <p className="text-sm text-muted-foreground">Loading background work health...</p>;
  }

  if (loadError || health === null || activity === null) {
    return <Alert>{loadError ?? 'Background work health could not be loaded.'}</Alert>;
  }

  const runningJobs = health.queues
    .filter((queue) => queue.status === 'running')
    .reduce((total, queue) => total + queue.count, 0);
  const failedJobs = health.queues
    .filter((queue) => queue.status === 'failed' || queue.status === 'dead_letter')
    .reduce((total, queue) => total + queue.count, 0);
  const watcherHealthy = health.watcher.status === 'healthy' || health.watcher.status === 'started';
  const activeJobs = activity.jobs.filter((job) => job.status === 'pending' || job.status === 'running');
  const hasDeadLetterMetadata = health.queues.some(
    (queue) => queue.jobType === 'asset-metadata-backfill' && queue.status === 'dead_letter' && queue.count > 0
  );
  const toggleProblem = (problemId: string) => {
    setExpandedProblemIds((current) => {
      const next = new Set(current);
      if (next.has(problemId)) {
        next.delete(problemId);
      } else {
        next.add(problemId);
      }
      return next;
    });
  };

  async function recoverMetadata() {
    if (!auth) {
      return;
    }
    setRecovering(true);
    setRecoveryMessage(null);
    try {
      const result = await recoverDeadLetterMetadata(auth.csrfToken);
      setRecoveryMessage(
        result.processedCount === 0
          ? 'No dead-letter metadata jobs were ready to retry.'
          : `${result.processedCount} metadata ${result.processedCount === 1 ? 'job was' : 'jobs were'} queued for retry.`
      );
      await loadJobs();
    } catch (error) {
      const message = messageForError(error, 'Metadata recovery could not be started.');
      onError('Metadata recovery could not be started', message);
    } finally {
      setRecovering(false);
    }
  }

  return (
    <>
      <section aria-label="Background work totals" className="grid gap-3 md:grid-cols-3">
        <SourceStat label="Running jobs" value={runningJobs} />
        <SourceStat label="Recent problems" value={health.recentProblems.length} warning={failedJobs > 0} />
        <div className="rounded-md border border-border bg-surface p-4">
          <p className="text-sm text-muted-foreground">Watcher</p>
          <p className="mt-2">
            <Badge variant={watcherHealthy ? 'success' : 'warning'}>{formatQueueStatus(health.watcher.status)}</Badge>
          </p>
        </div>
      </section>

      <Card>
        <CardHeader>
          <CardTitle>Queue health</CardTitle>
        </CardHeader>
        <CardContent>
          {health.queues.length === 0 ? (
            <p className="text-sm text-muted-foreground">No background jobs are currently visible.</p>
          ) : (
            <div className="overflow-x-auto">
              <Table>
                <TableHeader>
                  <TableRow>
                    <TableHead>Job type</TableHead>
                    <TableHead>Status</TableHead>
                    <TableHead>Count</TableHead>
                    <TableHead>Oldest queued</TableHead>
                    <TableHead>Last state change</TableHead>
                  </TableRow>
                </TableHeader>
                <TableBody>
                  {health.queues.map((queue) => (
                    <TableRow key={`${queue.jobType}-${queue.status}`}>
                      <TableCell className="font-mono text-xs">{queue.jobType}</TableCell>
                      <TableCell>
                        <Badge
                          variant={
                            queue.status === 'failed' || queue.status === 'dead_letter' ? 'warning' : 'secondary'
                          }
                        >
                          {formatQueueStatus(queue.status)}
                        </Badge>
                      </TableCell>
                      <TableCell>{queue.count}</TableCell>
                      <TableCell>{formatOptionalTimestamp(queue.oldestCreatedAt)}</TableCell>
                      <TableCell>{formatOptionalTimestamp(queue.latestUpdatedAt)}</TableCell>
                    </TableRow>
                  ))}
                </TableBody>
              </Table>
            </div>
          )}
        </CardContent>
      </Card>

      <Card>
        <CardHeader>
          <CardTitle>Active batches</CardTitle>
        </CardHeader>
        <CardContent>
          {activeJobs.length === 0 ? (
            <p className="text-sm text-muted-foreground">No active batches.</p>
          ) : (
            <div className="overflow-x-auto">
              <Table>
                <TableHeader>
                  <TableRow>
                    <TableHead>Batch</TableHead>
                    <TableHead>Status</TableHead>
                    <TableHead>Files</TableHead>
                    <TableHead>Attempts</TableHead>
                    <TableHead>Updated</TableHead>
                  </TableRow>
                </TableHeader>
                <TableBody>
                  {activeJobs.map((job) => (
                    <TableRow key={job.id}>
                      <TableCell>{job.batchLabel}</TableCell>
                      <TableCell>
                        <Badge variant={job.status === 'running' ? 'success' : 'secondary'}>
                          {formatQueueStatus(job.status)}
                        </Badge>
                      </TableCell>
                      <TableCell>{job.fileCount || '-'}</TableCell>
                      <TableCell>
                        {job.attempts}/{job.maxAttempts}
                      </TableCell>
                      <TableCell>{formatOptionalTimestamp(job.updatedAt)}</TableCell>
                    </TableRow>
                  ))}
                </TableBody>
              </Table>
            </div>
          )}
        </CardContent>
      </Card>

      <Card>
        <CardHeader>
          <CardTitle>Filesystem watcher</CardTitle>
        </CardHeader>
        <CardContent className="grid gap-4">
          <dl className="grid gap-3 text-sm md:grid-cols-3">
            <div className="grid gap-0.5">
              <dt className="text-muted-foreground">Registered roots</dt>
              <dd className="text-foreground">{health.watcher.registeredRootCount}</dd>
            </div>
            <div className="grid gap-0.5">
              <dt className="text-muted-foreground">Watched folders</dt>
              <dd className="text-foreground">{health.watcher.registeredDirectoryCount}</dd>
            </div>
            <div className="grid gap-0.5">
              <dt className="text-muted-foreground">Last folder check</dt>
              <dd className="text-foreground">{formatOptionalTimestamp(health.watcher.lastRegistrationRefreshAt)}</dd>
            </div>
          </dl>
          {health.watcher.lastErrorMessage && (
            <Alert>
              {formatQueueStatus(health.watcher.lastErrorCode ?? 'watcher_error')}: {health.watcher.lastErrorMessage}
            </Alert>
          )}
        </CardContent>
      </Card>

      <Card>
        <CardHeader>
          <CardTitle>Recent problems</CardTitle>
        </CardHeader>
        <CardContent>
          {hasDeadLetterMetadata && auth && (
            <div className="mb-4 flex flex-wrap items-center justify-between gap-3">
              <p className="text-sm text-muted-foreground">
                Retry metadata files that stopped after all attempts were used.
              </p>
              <Button disabled={recovering} onClick={() => void recoverMetadata()} type="button" variant="secondary">
                {recovering ? 'Retrying metadata...' : 'Retry failed metadata'}
              </Button>
            </div>
          )}
          {recoveryMessage && <Alert className="mb-4">{recoveryMessage}</Alert>}
          {health.recentProblems.length === 0 ? (
            <p className="text-sm text-muted-foreground">No recent queue problems.</p>
          ) : (
            <div className="overflow-x-auto">
              <Table>
                <TableHeader>
                  <TableRow>
                    <TableHead className="w-12">
                      <span className="sr-only">Details</span>
                    </TableHead>
                    <TableHead>Job type</TableHead>
                    <TableHead>File</TableHead>
                    <TableHead>Status</TableHead>
                    <TableHead>Attempts</TableHead>
                    <TableHead>Error</TableHead>
                    <TableHead>Updated</TableHead>
                  </TableRow>
                </TableHeader>
                <TableBody>
                  {health.recentProblems.map((problem) => {
                    const expanded = expandedProblemIds.has(problem.id);
                    const detailsId = `background-problem-details-${problem.id}`;
                    return (
                      <Fragment key={problem.id}>
                        <TableRow>
                          <TableCell>
                            <Button
                              aria-controls={detailsId}
                              aria-expanded={expanded}
                              aria-label={`${expanded ? 'Hide' : 'Show'} details for ${problem.jobType}`}
                              className="h-8 w-8"
                              data-testid={`background-problem-toggle-${problem.id}`}
                              onClick={() => toggleProblem(problem.id)}
                              size="icon"
                              type="button"
                              variant="ghost"
                            >
                              <ChevronRight
                                aria-hidden
                                className={cn('h-4 w-4 transition-transform', expanded && 'rotate-90')}
                              />
                            </Button>
                          </TableCell>
                          <TableCell className="font-mono text-xs">{problem.jobType}</TableCell>
                          <TableCell className="max-w-xs">
                            <span className="block truncate font-mono text-xs">{problemImpactedFile(problem)}</span>
                          </TableCell>
                          <TableCell>
                            <Badge variant="warning">{formatQueueStatus(problem.status)}</Badge>
                          </TableCell>
                          <TableCell>
                            {problem.attempts}/{problem.maxAttempts}
                          </TableCell>
                          <TableCell className="min-w-80 max-w-xl">
                            <span className="block whitespace-normal break-words">{problemErrorSummary(problem)}</span>
                          </TableCell>
                          <TableCell>{formatOptionalTimestamp(problem.updatedAt)}</TableCell>
                        </TableRow>
                        {expanded && (
                          <TableRow className="bg-muted/30 hover:bg-muted/30" id={detailsId}>
                            <TableCell colSpan={7}>
                              <ProblemDetails problem={problem} />
                            </TableCell>
                          </TableRow>
                        )}
                      </Fragment>
                    );
                  })}
                </TableBody>
              </Table>
            </div>
          )}
        </CardContent>
      </Card>
    </>
  );
}

function ProblemDetails({ problem }: { problem: BackgroundJobProblemSummary }) {
  const payload = prettyJson(problem.payloadJson);
  const error = problemErrorSummary(problem);

  return (
    <div className="grid gap-4 rounded-md border border-border bg-background p-4 text-sm">
      <dl className="grid gap-3 md:grid-cols-2 xl:grid-cols-4">
        <div className="grid gap-1">
          <dt className="text-xs font-semibold uppercase text-muted-foreground">Job id</dt>
          <dd className="break-all font-mono text-xs">{problem.id}</dd>
        </div>
        <div className="grid gap-1">
          <dt className="text-xs font-semibold uppercase text-muted-foreground">Impacted file</dt>
          <dd className="break-all font-mono text-xs">{problemImpactedFile(problem)}</dd>
        </div>
        <div className="grid gap-1">
          <dt className="text-xs font-semibold uppercase text-muted-foreground">Error type</dt>
          <dd className="break-all font-mono text-xs">{problem.lastErrorCode ?? 'unknown_error'}</dd>
        </div>
        <div className="grid gap-1">
          <dt className="text-xs font-semibold uppercase text-muted-foreground">Completed</dt>
          <dd>{formatOptionalTimestamp(problem.completedAt)}</dd>
        </div>
      </dl>
      <div className="grid gap-2">
        <h3 className="text-xs font-semibold uppercase text-muted-foreground">Full error</h3>
        <pre className="max-h-60 overflow-auto whitespace-pre-wrap break-words rounded-md border border-border bg-surface p-3 font-mono text-xs text-foreground">
          {error}
        </pre>
      </div>
      <div className="grid gap-2">
        <h3 className="text-xs font-semibold uppercase text-muted-foreground">Job payload</h3>
        <pre className="max-h-72 overflow-auto whitespace-pre-wrap break-words rounded-md border border-border bg-surface p-3 font-mono text-xs text-foreground">
          {payload ?? 'No payload was recorded for this job.'}
        </pre>
      </div>
    </div>
  );
}

function BackgroundFileActivityPanel({
  auth,
  onError
}: {
  auth?: AuthResponse;
  onError: (title: string, description?: string) => void;
}) {
  const [searchInput, setSearchInput] = useState('');
  const [debouncedQuery, setDebouncedQuery] = useState('');
  const [statuses, setStatuses] = useState<string[]>([]);
  const [updatedFilter, setUpdatedFilter] = useState<UpdatedFilter>({ mode: 'any' });
  const [page, setPage] = useState(0);
  const [pageSize, setPageSize] = useState<FileActivityPageSize>(DEFAULT_FILE_ACTIVITY_PAGE_SIZE);
  const [activity, setActivity] = useState<BackgroundFileActivityPage | null>(null);
  const [selectedAssetId, setSelectedAssetId] = useState<string | null>(null);
  const [selectedAsset, setSelectedAsset] = useState<AssetDetail | null>(null);
  const [loading, setLoading] = useState(true);
  const [loadError, setLoadError] = useState<string | null>(null);
  const [clearConfirmationOpen, setClearConfirmationOpen] = useState(false);
  const [clearing, setClearing] = useState(false);

  useEffect(() => {
    const nextQuery = searchInput.trim();
    if (nextQuery === debouncedQuery) {
      return;
    }
    const timer = window.setTimeout(() => {
      setPage(0);
      setDebouncedQuery(nextQuery);
    }, SEARCH_DEBOUNCE_MS);
    return () => window.clearTimeout(timer);
  }, [searchInput, debouncedQuery]);

  const statusesKey = statuses.join(',');
  const updatedKey = updatedFilterKey(updatedFilter);

  async function loadFiles(options: { showLoading?: boolean } = {}) {
    if (options.showLoading) {
      setLoading(true);
    }
    try {
      const updatedQuery = toUpdatedQuery(updatedFilter);
      const nextActivity = await fetchBackgroundWorkFiles({
        page,
        pageSize,
        q: debouncedQuery || undefined,
        statuses: statuses.length > 0 ? statuses : undefined,
        ...updatedQuery
      });
      setActivity(nextActivity);
      setLoadError(null);
    } catch (error) {
      const message = messageForError(error, 'File activity could not be loaded.');
      setLoadError(message);
      onError('File activity could not be loaded', message);
    } finally {
      if (options.showLoading) {
        setLoading(false);
      }
    }
  }

  useEffect(() => {
    void loadFiles({ showLoading: true });
  }, [page, pageSize, debouncedQuery, statusesKey, updatedKey]);

  useEffect(() => {
    const timer = window.setInterval(() => {
      void loadFiles();
    }, BACKGROUND_POLL_MS);
    return () => window.clearInterval(timer);
  }, [page, pageSize, debouncedQuery, statusesKey, updatedKey]);

  useEffect(() => {
    if (!selectedAssetId) {
      setSelectedAsset(null);
      return;
    }
    let ignore = false;
    setSelectedAsset(null);
    void fetchAsset(selectedAssetId)
      .then((asset) => {
        if (!ignore) {
          setSelectedAsset(asset);
        }
      })
      .catch((error) => {
        if (!ignore) {
          const message = messageForError(error, 'Photo could not be loaded.');
          setSelectedAssetId(null);
          onError('Photo could not be loaded', message);
        }
      });
    return () => {
      ignore = true;
    };
  }, [onError, selectedAssetId]);

  const totalPages = activity ? Math.max(1, Math.ceil(activity.totalCount / pageSize)) : 1;
  const viewerItems = activity?.items.filter((item) => item.assetId !== null) ?? [];
  const viewerIndex = selectedAssetId ? viewerItems.findIndex((item) => item.assetId === selectedAssetId) : -1;
  const showPrevious = viewerIndex > 0;
  const showNext = viewerIndex >= 0 && viewerIndex < viewerItems.length - 1;

  function openViewer(assetId: string) {
    setSelectedAssetId(assetId);
  }

  function showPreviousAsset() {
    if (viewerIndex > 0) {
      setSelectedAssetId(viewerItems[viewerIndex - 1].assetId!);
    }
  }

  function showNextAsset() {
    if (viewerIndex >= 0 && viewerIndex < viewerItems.length - 1) {
      setSelectedAssetId(viewerItems[viewerIndex + 1].assetId!);
    }
  }

  async function clearActivity() {
    if (!auth?.csrfToken) return;
    setClearing(true);
    try {
      await clearBackgroundWorkFiles(auth.csrfToken);
      setClearConfirmationOpen(false);
      setPage(0);
      await loadFiles({ showLoading: true });
    } catch (error) {
      const message = messageForError(error, 'Activity history could not be cleared.');
      onError('Activity history could not be cleared', message);
    } finally {
      setClearing(false);
    }
  }

  return (
    <>
      {selectedAssetId && (
        <div className="fixed inset-0 z-50 bg-background">
          <AssetFocus
            asset={selectedAsset}
            hasNext={showNext}
            hasPrevious={showPrevious}
            loading={selectedAsset === null}
            onClose={() => setSelectedAssetId(null)}
            onNext={showNextAsset}
            onPrevious={showPreviousAsset}
          />
        </div>
      )}
      <Card>
        <CardHeader>
          <div className="flex items-center justify-between gap-3">
            <CardTitle>Recent file activity</CardTitle>
            <Button
              data-testid="background-file-clear"
              disabled={!activity || activity.totalCount === 0}
              onClick={() => setClearConfirmationOpen(true)}
              size="sm"
              type="button"
              variant="ghost"
            >
              <Trash2 aria-hidden className="h-4 w-4" />
              Clear history
            </Button>
          </div>
        </CardHeader>
        <CardContent className="grid gap-4">
          <div className="grid gap-3">
            <div className="grid items-start gap-3 lg:grid-cols-3">
              <div className="grid gap-1.5">
                <Label htmlFor="background-file-search">Search file</Label>
                <Input
                  data-testid="background-file-search"
                  id="background-file-search"
                  onChange={(event) => setSearchInput(event.target.value)}
                  placeholder="Filename or path"
                  value={searchInput}
                />
              </div>
              <div className="grid gap-1.5">
                <Label id="background-file-status-label">Status</Label>
                <StatusMultiFilter
                  onChange={(next) => {
                    setStatuses(next);
                    setPage(0);
                  }}
                  values={statuses}
                />
              </div>
              <div className="grid gap-1.5">
                <Label id="background-file-updated-label">Updated</Label>
                <UpdatedDateFilter
                  onChange={(next) => {
                    setUpdatedFilter(next);
                    setPage(0);
                  }}
                  value={updatedFilter}
                />
              </div>
            </div>

            {statuses.length > 0 && (
              <StatusFilterPills
                onChange={(next) => {
                  setStatuses(next);
                  setPage(0);
                }}
                values={statuses}
              />
            )}

            {(updatedFilter.mode === 'on' || updatedFilter.mode === 'range') && (
              <UpdatedDateExtras
                onChange={(next) => {
                  setUpdatedFilter(next);
                  setPage(0);
                }}
                value={updatedFilter}
              />
            )}
          </div>

          {loading && activity === null ? (
            <p className="text-sm text-muted-foreground">Loading file activity...</p>
          ) : loadError || activity === null ? (
            <Alert>{loadError ?? 'File activity could not be loaded.'}</Alert>
          ) : (
            <>
              {activity.items.length === 0 ? (
                <p className="text-sm text-muted-foreground">No file activity matches the current filters.</p>
              ) : (
                <div className="overflow-x-auto">
                  <Table className="table-fixed min-w-[56rem]">
                    <colgroup>
                      <col className="w-[6%]" />
                      <col className="w-[41%]" />
                      <col className="w-[15%]" />
                      <col className="w-[12%]" />
                      <col className="w-[12%]" />
                      <col className="w-[18%]" />
                    </colgroup>
                    <TableHeader>
                      <TableRow>
                        <TableHead>
                          <span className="sr-only">View photo</span>
                        </TableHead>
                        <TableHead>File</TableHead>
                        <TableHead className="whitespace-nowrap">Status</TableHead>
                        <TableHead className="whitespace-nowrap">Batch</TableHead>
                        <TableHead className="whitespace-nowrap">Duration</TableHead>
                        <TableHead className="whitespace-nowrap">Updated</TableHead>
                      </TableRow>
                    </TableHeader>
                    <TableBody>
                      {activity.items.map((file, index) => (
                        <TableRow key={`${file.path ?? file.fileName}-${file.status}-${file.updatedAt}-${index}`}>
                          <TableCell className="align-top">
                            {file.assetId && (
                              <Button
                                aria-label={`Open ${file.fileName}`}
                                className="h-8 w-8"
                                onClick={() => openViewer(file.assetId!)}
                                size="icon"
                                title="Open photo"
                                type="button"
                                variant="ghost"
                              >
                                <Maximize2 aria-hidden className="h-4 w-4" />
                              </Button>
                            )}
                          </TableCell>
                          <TableCell className="min-w-[18rem] align-top">
                            <span className="block break-words">{file.fileName}</span>
                            {file.path && (
                              <span className="mt-0.5 block break-all font-mono text-xs text-muted-foreground">
                                {file.path}
                              </span>
                            )}
                            {file.message && (
                              <span className="mt-0.5 block break-words text-xs text-muted-foreground">
                                {file.message}
                              </span>
                            )}
                          </TableCell>
                          <TableCell className="whitespace-nowrap align-top">
                            <Badge variant={file.status === 'failed' ? 'warning' : 'secondary'}>
                              {formatQueueStatus(file.status)}
                            </Badge>
                          </TableCell>
                          <TableCell className="whitespace-nowrap align-top">{file.batchLabel ?? '-'}</TableCell>
                          <TableCell className="whitespace-nowrap align-top">
                            {formatDuration(file.durationMs)}
                          </TableCell>
                          <TableCell className="whitespace-nowrap align-top">
                            {formatOptionalTimestamp(file.updatedAt)}
                          </TableCell>
                        </TableRow>
                      ))}
                    </TableBody>
                  </Table>
                </div>
              )}

              <div className="flex flex-wrap items-center justify-between gap-3">
                <p className="text-sm text-muted-foreground">
                  Page {page + 1} of {totalPages} · {activity.totalCount} {activity.totalCount === 1 ? 'file' : 'files'}
                </p>
                <div className="flex flex-wrap items-center gap-2">
                  <label className="flex items-center gap-2 text-sm text-muted-foreground">
                    <span className="whitespace-nowrap">Per page</span>
                    <span className="relative inline-flex">
                      <select
                        aria-label="Files per page"
                        className={cn(FILTER_FIELD_CLASS, 'w-auto appearance-none pr-9')}
                        data-testid="background-file-page-size"
                        onChange={(event) => {
                          const nextSize = Number(event.target.value) as FileActivityPageSize;
                          setPageSize(nextSize);
                          setPage(0);
                        }}
                        value={pageSize}
                      >
                        {FILE_ACTIVITY_PAGE_SIZES.map((size) => (
                          <option key={size} value={size}>
                            {size}
                          </option>
                        ))}
                      </select>
                      <ChevronDown
                        aria-hidden
                        className="pointer-events-none absolute top-1/2 right-3 h-3.5 w-3.5 -translate-y-1/2 text-muted-foreground"
                      />
                    </span>
                  </label>
                  <Button
                    data-testid="background-file-prev"
                    disabled={page === 0 || loading}
                    onClick={() => setPage((current) => Math.max(0, current - 1))}
                    type="button"
                    variant="secondary"
                  >
                    Previous
                  </Button>
                  <Button
                    data-testid="background-file-next"
                    disabled={!activity.hasNext || loading}
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
        </CardContent>
      </Card>
      {clearConfirmationOpen && (
        <div className="fixed inset-0 z-50 grid place-items-center bg-black/60 p-4">
          <div
            aria-modal="true"
            aria-labelledby="clear-file-activity-title"
            className="grid w-full max-w-md gap-4 rounded-md border border-border bg-surface p-5 text-foreground shadow-lg"
            role="dialog"
          >
            <div className="grid gap-2">
              <h2 className="text-lg font-semibold" id="clear-file-activity-title">
                Clear activity history?
              </h2>
              <p className="text-sm text-muted-foreground">
                This permanently removes completed file activity. Active processing will remain visible.
              </p>
            </div>
            <div className="flex justify-end gap-2">
              <Button disabled={clearing} onClick={() => setClearConfirmationOpen(false)} type="button" variant="ghost">
                Cancel
              </Button>
              <Button disabled={clearing} onClick={() => void clearActivity()} type="button" variant="secondary">
                Clear history
              </Button>
            </div>
          </div>
        </div>
      )}
    </>
  );
}

function BackgroundConfigurationPanel({ onError }: { onError: (title: string, description?: string) => void }) {
  const [config, setConfig] = useState<BackgroundWorkConfig | null>(null);
  const [loading, setLoading] = useState(true);
  const [loadError, setLoadError] = useState<string | null>(null);

  useEffect(() => {
    let cancelled = false;
    setLoading(true);
    void fetchBackgroundWorkConfig()
      .then((nextConfig) => {
        if (cancelled) {
          return;
        }
        setConfig(nextConfig);
        setLoadError(null);
      })
      .catch((error: unknown) => {
        if (cancelled) {
          return;
        }
        const message = messageForError(error, 'Background work configuration could not be loaded.');
        setLoadError(message);
        onError('Background work configuration could not be loaded', message);
      })
      .finally(() => {
        if (!cancelled) {
          setLoading(false);
        }
      });
    return () => {
      cancelled = true;
    };
    // Load once when the Configuration tab is opened.
    // eslint-disable-next-line react-hooks/exhaustive-deps -- intentional one-shot fetch
  }, []);

  if (loading && config === null) {
    return <p className="text-sm text-muted-foreground">Loading configuration...</p>;
  }

  if (loadError || config === null) {
    return <Alert>{loadError ?? 'Background work configuration could not be loaded.'}</Alert>;
  }

  return (
    <Card>
      <CardHeader>
        <CardTitle>Worker configuration</CardTitle>
      </CardHeader>
      <CardContent className="grid gap-5">
        <dl className="grid gap-3 text-sm sm:grid-cols-2">
          <ConfigStat label="Max concurrent jobs" value={String(config.maxConcurrentJobs)} />
          <ConfigStat label="Max metadata jobs" value={String(config.maxConcurrentMetadataJobs)} />
          <ConfigStat label="Identity batch size" value={String(config.identityBatchSize)} />
          <ConfigStat label="Claim batch size" value={String(config.claimBatchSize)} />
          <ConfigStat label="Poll interval" value={`${config.pollIntervalMs} ms`} />
        </dl>
        <Alert data-testid="background-config-advice">
          These values are read-only here. Set them with environment variables such as
          `PIXIERGE_BACKGROUND_JOBS_MAX_CONCURRENT_JOBS`, `PIXIERGE_BACKGROUND_JOBS_MAX_CONCURRENT_METADATA_JOBS`,
          `PIXIERGE_BACKGROUND_JOBS_IDENTITY_BATCH_SIZE`, `PIXIERGE_BACKGROUND_JOBS_CLAIM_BATCH_SIZE`, and
          `PIXIERGE_BACKGROUND_JOBS_POLL_INTERVAL_MS` in `.env` or `docker-compose.yml`, then restart the API for
          changes to take effect.
        </Alert>
      </CardContent>
    </Card>
  );
}

function SourceStat({ label, value, warning = false }: { label: string; value: number; warning?: boolean }) {
  return (
    <div className="rounded-md border border-border bg-surface p-4">
      <p className="text-sm text-muted-foreground">{label}</p>
      <p className={cn('mt-1 text-2xl font-semibold text-foreground', warning && 'text-zinc-200')}>{value}</p>
    </div>
  );
}

function ConfigStat({ label, value }: { label: string; value: string }) {
  return (
    <div className="grid gap-0.5 rounded-md border border-border bg-surface p-4">
      <dt className="text-muted-foreground">{label}</dt>
      <dd className="text-lg font-semibold text-foreground">{value}</dd>
    </div>
  );
}

function StatusMultiFilter({ values, onChange }: { values: string[]; onChange: (values: string[]) => void }) {
  function toggle(status: string, enabled: boolean) {
    const without = values.filter((entry) => entry.toLowerCase() !== status.toLowerCase());
    onChange(enabled ? [...without, status] : without);
  }

  const summary =
    values.length === 0
      ? 'All statuses'
      : values
          .map(
            (status) =>
              FILE_STATUS_OPTIONS.find((option) => option.value === status)?.label ?? formatQueueStatus(status)
          )
          .join(', ');

  return (
    <DropdownMenu>
      <DropdownMenuTrigger asChild>
        <button
          aria-labelledby="background-file-status-label"
          className={cn('flex items-center justify-between gap-2', FILTER_FIELD_CLASS)}
          data-testid="background-file-status"
          type="button"
        >
          <span className="truncate text-left">{summary}</span>
          <ChevronDown className="h-3.5 w-3.5 shrink-0 text-muted-foreground" aria-hidden />
        </button>
      </DropdownMenuTrigger>
      <DropdownMenuContent
        align="start"
        className="min-w-[var(--radix-dropdown-menu-trigger-width)]"
        data-testid="background-file-status-menu"
        onCloseAutoFocus={(event) => event.preventDefault()}
      >
        <DropdownMenuItem
          onSelect={(event) => {
            event.preventDefault();
            onChange([]);
          }}
        >
          <Check aria-hidden className={cn('h-3.5 w-3.5', values.length > 0 && 'opacity-0')} />
          All statuses
        </DropdownMenuItem>
        {FILE_STATUS_OPTIONS.map((option) => {
          const active = values.some((value) => value.toLowerCase() === option.value);
          return (
            <DropdownMenuItem
              key={option.value}
              onSelect={(event) => {
                event.preventDefault();
                toggle(option.value, !active);
              }}
            >
              <Check aria-hidden className={cn('h-3.5 w-3.5', !active && 'opacity-0')} />
              {option.label}
            </DropdownMenuItem>
          );
        })}
      </DropdownMenuContent>
    </DropdownMenu>
  );
}

function StatusFilterPills({ values, onChange }: { values: string[]; onChange: (values: string[]) => void }) {
  return (
    <div className="flex flex-wrap gap-1.5" data-testid="background-file-status-pills">
      {values.map((status) => {
        const label = FILE_STATUS_OPTIONS.find((option) => option.value === status)?.label ?? formatQueueStatus(status);
        return (
          <Badge className="gap-1 pr-1 font-medium" key={status} variant="secondary">
            {label}
            <button
              aria-label={`Remove ${label}`}
              className="rounded-sm px-0.5 text-muted-foreground hover:text-foreground"
              onClick={() => onChange(values.filter((entry) => entry.toLowerCase() !== status.toLowerCase()))}
              type="button"
            >
              <X className="h-3 w-3" aria-hidden />
            </button>
          </Badge>
        );
      })}
    </div>
  );
}

function UpdatedDateFilter({ value, onChange }: { value: UpdatedFilter; onChange: (next: UpdatedFilter) => void }) {
  const [menuOpen, setMenuOpen] = useState(false);

  function applyPreset(mode: UpdatedFilter['mode']) {
    if (mode === 'any' || mode === 'last1h' || mode === 'last7' || mode === 'last30' || mode === 'last90') {
      onChange({ mode });
      setMenuOpen(false);
      return;
    }
    if (mode === 'on') {
      onChange({ mode: 'on', on: value.mode === 'on' ? value.on : '' });
      setMenuOpen(false);
      return;
    }
    const from = value.mode === 'range' ? value.from : '';
    const to = value.mode === 'range' ? value.to : '';
    const range = orderedRange(from, to);
    onChange({ mode: 'range', from: range.from, to: range.to });
    setMenuOpen(false);
  }

  return (
    <DropdownMenu open={menuOpen} onOpenChange={setMenuOpen}>
      <DropdownMenuTrigger asChild>
        <button
          aria-labelledby="background-file-updated-label"
          className={cn('flex items-center justify-between gap-2', FILTER_FIELD_CLASS)}
          data-testid="background-file-updated"
          type="button"
        >
          <span className="truncate">{updatedSummary(value)}</span>
          <ChevronDown className="h-3.5 w-3.5 shrink-0 text-muted-foreground" aria-hidden />
        </button>
      </DropdownMenuTrigger>
      <DropdownMenuContent align="start" className="w-64 p-1" data-testid="background-file-updated-menu">
        <DropdownMenuItem onSelect={() => applyPreset('any')}>Any date</DropdownMenuItem>
        <DropdownMenuItem onSelect={() => applyPreset('last1h')}>Last 1 hour</DropdownMenuItem>
        <DropdownMenuItem onSelect={() => applyPreset('last7')}>Last 7 days</DropdownMenuItem>
        <DropdownMenuItem onSelect={() => applyPreset('last30')}>Last 30 days</DropdownMenuItem>
        <DropdownMenuItem onSelect={() => applyPreset('last90')}>Last 90 days</DropdownMenuItem>
        <DropdownMenuItem onSelect={() => applyPreset('on')}>On date…</DropdownMenuItem>
        <DropdownMenuItem onSelect={() => applyPreset('range')}>Custom range…</DropdownMenuItem>
      </DropdownMenuContent>
    </DropdownMenu>
  );
}

function UpdatedDateExtras({
  value,
  onChange
}: {
  value: Extract<UpdatedFilter, { mode: 'on' } | { mode: 'range' }>;
  onChange: (next: UpdatedFilter) => void;
}) {
  if (value.mode === 'on') {
    return (
      <label className="grid max-w-sm min-w-0 gap-1 text-xs text-muted-foreground">
        <span>On</span>
        <DateInput
          aria-label="Updated on date"
          onChange={(next) => onChange({ mode: 'on', on: next })}
          value={value.on}
        />
      </label>
    );
  }

  return (
    <div className="grid min-w-0 gap-2 sm:grid-cols-2 lg:max-w-xl">
      <label className="grid min-w-0 gap-1 text-xs text-muted-foreground">
        <span>From</span>
        <DateInput
          aria-label="Updated from date"
          data-testid="background-file-updated-from"
          max={value.to || undefined}
          onChange={(next) => {
            const range = orderedRange(next, value.to);
            onChange({ mode: 'range', from: range.from, to: range.to });
          }}
          value={value.from}
        />
      </label>
      <label className="grid min-w-0 gap-1 text-xs text-muted-foreground">
        <span>To</span>
        <DateInput
          aria-label="Updated to date"
          data-testid="background-file-updated-to"
          min={value.from || undefined}
          onChange={(next) => {
            const range = orderedRange(value.from, next);
            onChange({ mode: 'range', from: range.from, to: range.to });
          }}
          value={value.to}
        />
      </label>
    </div>
  );
}

function DateInput({
  'aria-label': ariaLabel,
  'data-testid': dataTestId,
  max,
  min,
  onChange,
  value
}: {
  'aria-label': string;
  'data-testid'?: string;
  max?: string;
  min?: string;
  onChange: (value: string) => void;
  value: string;
}) {
  const inputRef = useRef<HTMLInputElement>(null);

  function openPicker() {
    const input = inputRef.current;
    if (!input) {
      return;
    }
    if (typeof input.showPicker === 'function') {
      try {
        input.showPicker();
        return;
      } catch {
        // NotAllowedError when not triggered by a user gesture, or unsupported.
      }
    }
    input.focus();
  }

  return (
    <div className="relative min-w-0">
      <Input
        ref={inputRef}
        aria-label={ariaLabel}
        className={cn(FILTER_FIELD_CLASS, 'pr-9 [&::-webkit-calendar-picker-indicator]:hidden')}
        data-testid={dataTestId}
        max={max}
        min={min}
        onChange={(event) => onChange(event.target.value)}
        type="date"
        value={value}
      />
      <button
        aria-label={`Open ${ariaLabel} calendar`}
        className="absolute inset-y-0 right-0 flex w-8 items-center justify-center text-muted-foreground transition-colors hover:text-foreground"
        onClick={openPicker}
        type="button"
      >
        <Calendar aria-hidden className="h-3.5 w-3.5" />
      </button>
    </div>
  );
}

function orderedRange(from: string, to: string): { from: string; to: string } {
  if (from && to && from > to) {
    return { from: to, to: from };
  }
  return { from, to };
}

function formatLocalDate(date: Date): string {
  const year = date.getFullYear();
  const month = String(date.getMonth() + 1).padStart(2, '0');
  const day = String(date.getDate()).padStart(2, '0');
  return `${year}-${month}-${day}`;
}

function daysAgo(days: number): string {
  const date = new Date();
  date.setHours(0, 0, 0, 0);
  date.setDate(date.getDate() - days);
  return formatLocalDate(date);
}

function hoursAgoIso(hours: number): string {
  return new Date(Date.now() - hours * 60 * 60 * 1000).toISOString();
}

function toUpdatedQuery(filter: UpdatedFilter): { updatedFrom?: string; updatedTo?: string } {
  switch (filter.mode) {
    case 'any':
      return {};
    case 'last1h':
      return { updatedFrom: hoursAgoIso(1) };
    case 'last7':
      return { updatedFrom: daysAgo(7) };
    case 'last30':
      return { updatedFrom: daysAgo(30) };
    case 'last90':
      return { updatedFrom: daysAgo(90) };
    case 'on':
      return filter.on ? { updatedFrom: filter.on, updatedTo: filter.on } : {};
    case 'range': {
      const range = orderedRange(filter.from, filter.to);
      return {
        updatedFrom: range.from || undefined,
        updatedTo: range.to || undefined
      };
    }
  }
}

function updatedFilterKey(filter: UpdatedFilter): string {
  switch (filter.mode) {
    case 'any':
    case 'last1h':
    case 'last7':
    case 'last30':
    case 'last90':
      return filter.mode;
    case 'on':
      return `on:${filter.on}`;
    case 'range':
      return `range:${filter.from}:${filter.to}`;
  }
}

function updatedSummary(filter: UpdatedFilter): string {
  switch (filter.mode) {
    case 'any':
      return 'Any date';
    case 'last1h':
      return 'Last 1 hour';
    case 'last7':
      return 'Last 7 days';
    case 'last30':
      return 'Last 30 days';
    case 'last90':
      return 'Last 90 days';
    case 'on':
      return filter.on ? `On ${filter.on}` : 'On date';
    case 'range':
      if (filter.from && filter.to) {
        return `${filter.from} → ${filter.to}`;
      }
      if (filter.from) {
        return `From ${filter.from}`;
      }
      if (filter.to) {
        return `Until ${filter.to}`;
      }
      return 'Custom range';
  }
}

type JsonRecord = Record<string, unknown>;

function problemErrorSummary(problem: BackgroundJobProblemSummary) {
  const code = problem.lastErrorCode?.trim();
  const message = problem.lastErrorMessage?.trim();
  if (code && message) {
    return `${code}: ${message}`;
  }
  return message ?? code ?? 'unknown_error';
}

function problemImpactedFile(problem: BackgroundJobProblemSummary) {
  const payload = parseJsonRecord(problem.payloadJson);
  if (!payload) {
    return 'Unknown file';
  }

  const directPath =
    stringProperty(payload, 'normalizedPath') ??
    stringProperty(payload, 'path') ??
    stringProperty(payload, 'displayPath') ??
    stringProperty(payload, 'absolutePath');
  if (directPath) {
    return directPath;
  }

  const fileName = stringProperty(payload, 'fileName') ?? stringProperty(payload, 'name');
  if (fileName) {
    return fileName;
  }

  const items =
    arrayProperty(payload, 'identityItems') ?? arrayProperty(payload, 'items') ?? arrayProperty(payload, 'files');
  const firstItemPath = firstItemFilePath(items);
  if (firstItemPath && items && items.length > 1) {
    return `${firstItemPath} (+${items.length - 1} more)`;
  }
  return firstItemPath ?? 'Unknown file';
}

function prettyJson(json: string | null) {
  if (json === null || json.trim() === '') {
    return null;
  }
  try {
    return JSON.stringify(JSON.parse(json), null, 2);
  } catch {
    return json;
  }
}

function parseJsonRecord(json: string | null): JsonRecord | null {
  if (json === null || json.trim() === '') {
    return null;
  }
  try {
    return asRecord(JSON.parse(json));
  } catch {
    return null;
  }
}

function firstItemFilePath(items: unknown[] | null | undefined) {
  if (!items || items.length === 0) {
    return null;
  }
  for (const item of items) {
    const record = asRecord(item);
    if (!record) {
      continue;
    }
    const path =
      stringProperty(record, 'normalizedPath') ??
      stringProperty(record, 'path') ??
      stringProperty(record, 'displayPath') ??
      stringProperty(record, 'absolutePath') ??
      stringProperty(record, 'fileName') ??
      stringProperty(record, 'name');
    if (path) {
      return path;
    }
  }
  return null;
}

function stringProperty(record: JsonRecord, key: string) {
  const value = record[key];
  return typeof value === 'string' && value.trim() !== '' ? value : null;
}

function arrayProperty(record: JsonRecord, key: string) {
  const value = record[key];
  return Array.isArray(value) ? value : null;
}

function asRecord(value: unknown): JsonRecord | null {
  return typeof value === 'object' && value !== null && !Array.isArray(value) ? (value as JsonRecord) : null;
}

function formatQueueStatus(status: string) {
  return status.replaceAll('_', ' ');
}

function formatOptionalTimestamp(value: string | null) {
  return value ? formatScanTimestamp(value) : 'Never';
}

function formatDuration(value: number | null | undefined) {
  if (value === null || value === undefined) {
    return '-';
  }
  if (value < 1000) {
    return `${Math.max(0, Math.round(value))} ms`;
  }
  return `${(value / 1000).toFixed(1)} s`;
}

function messageForError(error: unknown, fallback: string) {
  if (!(error instanceof ApiError)) {
    return fallback;
  }
  return error.message.startsWith('Request failed with ') ? fallback : error.message;
}
