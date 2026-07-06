import {
  AlertTriangle,
  Archive,
  Blocks,
  ChevronDown,
  ChevronRight,
  CheckCircle2,
  FolderOpen,
  Plus,
  RefreshCw,
  RotateCcw,
  SlidersHorizontal,
  Trash2
} from 'lucide-react';
import { useEffect, useState, type ComponentType, type FormEvent } from 'react';
import {
  addGlobalExclusionPattern,
  addLibraryExclusionPattern,
  addLibraryRoot,
  ApiError,
  archiveLibrary,
  createLibrary,
  deleteGlobalExclusionPattern,
  deleteLibraryExclusionPattern,
  deleteLibraryRoot,
  fetchGlobalExclusionPatterns,
  fetchScan,
  restoreLibrary,
  scanLibrary,
  scanLibraryRoot,
  type AuthResponse,
  type GlobalExclusionPattern,
  type LibraryExclusionPattern,
  type LibrarySummary,
  type LibrarySource,
  type ScanRun
} from '@/api';
import { Alert } from '@/components/ui/alert';
import { Badge } from '@/components/ui/badge';
import { Button } from '@/components/ui/button';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow
} from '@/components/ui/table';
import { cn } from '@/lib/utils';

type SettingsView = 'configuration' | 'plugins' | 'backups';

type SettingsItem = {
  description: string;
  icon: ComponentType<{ className?: string }>;
  label: string;
  view: SettingsView;
};

const settingsItems: SettingsItem[] = [
  {
    description: 'Manage named libraries and filesystem sources.',
    icon: SlidersHorizontal,
    label: 'Configuration',
    view: 'configuration'
  },
  {
    description: 'Plugin installation and lifecycle controls will live here.',
    icon: Blocks,
    label: 'Plugins',
    view: 'plugins'
  },
  {
    description: 'Backup health, schedules, and restore checks will live here.',
    icon: Archive,
    label: 'Backups',
    view: 'backups'
  }
];

type SettingsPageProps = {
  auth: AuthResponse;
  error?: string | null;
  libraries: LibrarySummary[];
  loading?: boolean;
  onError: (title: string, description?: string) => void;
  onLibrariesChange: () => Promise<void>;
};

type DirectoryPickerWindow = Window & {
  showDirectoryPicker?: () => Promise<{ name: string }>;
};

export function SettingsPage({
  auth,
  error = null,
  libraries,
  loading = false,
  onError,
  onLibrariesChange
}: SettingsPageProps) {
  const [currentView, setCurrentView] = useState<SettingsView>('configuration');
  const currentItem = settingsItems.find((item) => item.view === currentView) ?? settingsItems[0];

  return (
    <div className="grid min-h-full gap-8 lg:grid-cols-[var(--settings-nav-width)_minmax(0,1fr)]">
      <aside className="border-b border-border pb-4 lg:border-b-0 lg:border-r lg:pb-0 lg:pr-4">
        <div className="mb-4 grid gap-1">
          <h1 className="text-2xl font-semibold text-foreground">Settings</h1>
          <p className="text-sm text-muted-foreground">Configure Pixierge operations.</p>
        </div>

        <nav aria-label="Settings" className="grid gap-1">
          {settingsItems.map((item) => {
            const Icon = item.icon;
            const active = currentView === item.view;

            return (
              <button
                aria-current={active ? 'page' : undefined}
                className={cn(
                  'flex h-10 items-center gap-3 rounded-md px-3 text-left text-sm font-medium text-muted-foreground transition-colors hover:bg-muted hover:text-foreground',
                  active && 'bg-muted text-foreground'
                )}
                key={item.view}
                onClick={() => setCurrentView(item.view)}
                type="button"
              >
                <Icon className="h-4 w-4" aria-hidden />
                {item.label}
              </button>
            );
          })}
        </nav>
      </aside>

      <SettingsContent
        auth={auth}
        error={error}
        item={currentItem}
        libraries={libraries}
        loading={loading}
        onError={onError}
        onLibrariesChange={onLibrariesChange}
      />
    </div>
  );
}

function SettingsContent({
  auth,
  error,
  item,
  libraries,
  loading,
  onError,
  onLibrariesChange
}: SettingsPageProps & { item: SettingsItem }) {
  const Icon = item.icon;

  return (
    <section aria-labelledby="settings-page-title" className="grid content-start gap-8">
      <div className="grid gap-2">
        <div className="flex items-center gap-3">
          <Icon className="h-5 w-5 text-muted-foreground" aria-hidden />
          <h2 id="settings-page-title" className="text-2xl font-semibold text-foreground">
            {item.label}
          </h2>
        </div>
        <p className="max-w-2xl text-sm text-muted-foreground">{item.description}</p>
      </div>

      {item.view === 'configuration' ? (
        <SourcesSettings
          auth={auth}
          error={error}
          libraries={libraries}
          loading={loading}
          onError={onError}
          onLibrariesChange={onLibrariesChange}
        />
      ) : (
        <EmptySettingsPage label={item.label} />
      )}
    </section>
  );
}

function SourcesSettings({
  auth,
  error,
  libraries,
  loading,
  onError,
  onLibrariesChange
}: SettingsPageProps) {
  const [libraryName, setLibraryName] = useState('');
  const [selectedLibraryId, setSelectedLibraryId] = useState<string | null>(libraries[0]?.id ?? null);
  const [showArchived, setShowArchived] = useState(false);
  const [globalExclusionPatterns, setGlobalExclusionPatterns] = useState<GlobalExclusionPattern[]>([]);
  const [globalExclusionsLoading, setGlobalExclusionsLoading] = useState(false);
  const [globalExclusionsOpen, setGlobalExclusionsOpen] = useState(false);
  const [formError, setFormError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);
  const activeLibraries = libraries.filter((library) => library.status === 'active');
  const visibleLibraries = showArchived ? libraries : activeLibraries;
  const selectedLibrary = visibleLibraries.find((library) => library.id === selectedLibraryId) ?? visibleLibraries[0] ?? null;
  const sourceCount = activeLibraries.reduce((total, library) => total + library.sourceCount, 0);
  const archivedCount = libraries.length - activeLibraries.length;

  useEffect(() => {
    if (selectedLibraryId && visibleLibraries.some((library) => library.id === selectedLibraryId)) {
      return;
    }
    setSelectedLibraryId(visibleLibraries[0]?.id ?? null);
  }, [selectedLibraryId, visibleLibraries]);

  async function loadGlobalExclusions() {
    setGlobalExclusionsLoading(true);
    try {
      setGlobalExclusionPatterns(await fetchGlobalExclusionPatterns());
    } catch (loadError) {
      const message = messageForError(loadError, 'Global exclusions could not be loaded.');
      setFormError(message);
      onError('Global exclusions could not be loaded', message);
    } finally {
      setGlobalExclusionsLoading(false);
    }
  }

  useEffect(() => {
    void loadGlobalExclusions();
  }, []);

  async function submitLibrary(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setFormError(null);
    setSubmitting(true);

    try {
      const created = await createLibrary({ name: libraryName }, auth.csrfToken);
      setLibraryName('');
      setSelectedLibraryId(created.id);
      await onLibrariesChange();
    } catch (submitError) {
      const message = messageForError(submitError, 'Library could not be created.');
      setFormError(message);
      onError('Library could not be created', message);
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <div className="grid gap-6">
      {error && <Alert>{error}</Alert>}

      <section aria-label="Source totals" className="grid gap-3 md:grid-cols-3">
        <SourceStat label="Active libraries" value={activeLibraries.length} />
        <SourceStat label="Sources" value={sourceCount} />
        <SourceStat label="Archived" value={archivedCount} warning={archivedCount > 0} />
      </section>

      {loading && <p className="text-sm text-muted-foreground">Loading sources...</p>}

      <GlobalExclusionsPanel
        auth={auth}
        loading={globalExclusionsLoading}
        onChange={loadGlobalExclusions}
        onError={onError}
        open={globalExclusionsOpen}
        patterns={globalExclusionPatterns}
        setOpen={setGlobalExclusionsOpen}
      />

      {!loading && libraries.length === 0 && (
        <div className="grid min-h-60 place-items-center rounded-md border border-dashed border-border">
          <div className="grid max-w-md justify-items-center gap-2 text-center">
            <SlidersHorizontal className="h-7 w-7 text-muted-foreground" aria-hidden />
            <p className="text-sm font-medium text-foreground">No libraries configured</p>
            <p className="text-sm text-muted-foreground">Create a named library, then add one or more source paths.</p>
          </div>
        </div>
      )}

      {!loading && (
        <div className="grid gap-5 xl:grid-cols-[20rem_minmax(0,1fr)]">
          <aside className="grid content-start gap-4">
            <div className="rounded-md border border-border p-4">
              <form className="grid gap-3" onSubmit={submitLibrary}>
                <div className="grid gap-2">
                  <Label htmlFor="library-name">Library name</Label>
                  <Input
                    id="library-name"
                    onChange={(event) => setLibraryName(event.target.value)}
                    placeholder="Family Photos"
                    value={libraryName}
                  />
                </div>
                <Button disabled={submitting || libraryName.trim() === ''} type="submit">
                  <Plus className="h-4 w-4" aria-hidden />
                  Create
                </Button>
              </form>
              {formError && <p className="mt-3 text-sm text-muted-foreground">{formError}</p>}
            </div>

            <label className="flex min-h-10 items-center gap-2 rounded-md border border-border px-3 text-sm text-muted-foreground">
              <input
                checked={showArchived}
                className="h-4 w-4"
                onChange={(event) => setShowArchived(event.target.checked)}
                type="checkbox"
              />
              Show archived
            </label>

            <nav aria-label="Libraries" className="grid gap-2">
              {visibleLibraries.map((library) => (
                <button
                  aria-current={selectedLibrary?.id === library.id ? 'page' : undefined}
                  className={cn(
                    'grid min-h-16 gap-2 rounded-md border border-border px-3 py-2 text-left transition-colors hover:bg-muted',
                    selectedLibrary?.id === library.id && 'bg-muted'
                  )}
                  key={library.id}
                  onClick={() => setSelectedLibraryId(library.id)}
                  type="button"
                >
                  <span className="flex min-w-0 items-center justify-between gap-2">
                    <span className="truncate text-sm font-medium text-foreground">{library.name}</span>
                    {library.status === 'archived' && <Badge variant="warning">Archived</Badge>}
                  </span>
                  <span className="flex flex-wrap gap-2 text-xs text-muted-foreground">
                    <span>{formatSourceCount(library.sourceCount)}</span>
                    <span>{library.availableSourceCount} available</span>
                    {library.unavailableSourceCount > 0 && <span>{library.unavailableSourceCount} unavailable</span>}
                  </span>
                </button>
              ))}
            </nav>
          </aside>

          {selectedLibrary ? (
            <LibrarySourceCard
              auth={auth}
              key={selectedLibrary.id}
              library={selectedLibrary}
              onError={onError}
              onLibrariesChange={onLibrariesChange}
            />
          ) : (
            <div className="grid min-h-80 place-items-center rounded-md border border-dashed border-border">
              <p className="text-sm text-muted-foreground">No libraries match the current view.</p>
            </div>
          )}
        </div>
      )}
    </div>
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

function GlobalExclusionsPanel({
  auth,
  loading,
  onChange,
  onError,
  open,
  patterns,
  setOpen
}: {
  auth: AuthResponse;
  loading: boolean;
  onChange: () => Promise<void>;
  onError: (title: string, description?: string) => void;
  open: boolean;
  patterns: GlobalExclusionPattern[];
  setOpen: (open: boolean) => void;
}) {
  const [pattern, setPattern] = useState('');
  const [submitting, setSubmitting] = useState(false);
  const [formError, setFormError] = useState<string | null>(null);

  async function submitPattern(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setFormError(null);
    setSubmitting(true);

    try {
      await addGlobalExclusionPattern({ pattern }, auth.csrfToken);
      setPattern('');
      await onChange();
    } catch (submitError) {
      const message = messageForError(submitError, 'Global exclusion pattern could not be added.');
      setFormError(message);
      onError('Global exclusion pattern could not be added', message);
    } finally {
      setSubmitting(false);
    }
  }

  async function removePattern(exclusion: GlobalExclusionPattern) {
    setFormError(null);

    try {
      await deleteGlobalExclusionPattern(exclusion.id, auth.csrfToken);
      await onChange();
    } catch (submitError) {
      const message = messageForError(submitError, 'Global exclusion pattern could not be removed.');
      setFormError(message);
      onError('Global exclusion pattern could not be removed', message);
    }
  }

  return (
    <div className="rounded-md border border-border">
      <button
        aria-expanded={open}
        className="flex min-h-12 w-full items-center justify-between gap-3 px-4 text-left text-sm font-medium text-foreground"
        onClick={() => setOpen(!open)}
        type="button"
      >
        <span className="inline-flex items-center gap-2">
          {open ? (
            <ChevronDown className="h-4 w-4 text-muted-foreground" aria-hidden />
          ) : (
            <ChevronRight className="h-4 w-4 text-muted-foreground" aria-hidden />
          )}
          Global exclusions
        </span>
        <Badge variant="secondary">{patterns.length}</Badge>
      </button>

      {open && (
        <div className="grid gap-3 border-t border-border p-4">
          {loading && <p className="text-sm text-muted-foreground">Loading global exclusions...</p>}
          <form className="grid gap-3 lg:grid-cols-[minmax(0,1fr)_auto]" onSubmit={submitPattern}>
            <div className="grid gap-2">
              <Label htmlFor="global-exclusion-pattern">Exclusion pattern</Label>
              <Input
                id="global-exclusion-pattern"
                onChange={(event) => setPattern(event.target.value)}
                placeholder="**/.cache/**"
                value={pattern}
              />
            </div>
            <Button
              className="self-end"
              disabled={submitting || pattern.trim() === ''}
              type="submit"
              variant="secondary"
            >
              <Plus className="h-4 w-4" aria-hidden />
              Add exclusion
            </Button>
          </form>
          {formError && <p className="text-sm text-muted-foreground">{formError}</p>}
          {patterns.length === 0 ? (
            <p className="text-sm text-muted-foreground">No global exclusion patterns configured.</p>
          ) : (
            <div className="flex flex-wrap gap-2">
              {patterns.map((exclusion) => (
                <span
                  className="inline-flex min-h-9 items-center gap-2 rounded-md border border-border px-2 text-sm"
                  key={exclusion.id}
                >
                  <span className="font-mono text-xs">{exclusion.pattern}</span>
                  <Button
                    aria-label={`Remove global exclusion ${exclusion.pattern}`}
                    onClick={() => void removePattern(exclusion)}
                    size="icon"
                    type="button"
                    variant="ghost"
                  >
                    <Trash2 className="h-4 w-4" aria-hidden />
                  </Button>
                </span>
              ))}
            </div>
          )}
        </div>
      )}
    </div>
  );
}

function LibrarySourceCard({
  auth,
  library,
  onError,
  onLibrariesChange
}: {
  auth: AuthResponse;
  library: LibrarySummary;
  onError: (title: string, description?: string) => void;
  onLibrariesChange: () => Promise<void>;
}) {
  const [path, setPath] = useState('');
  const [exclusionPattern, setExclusionPattern] = useState('');
  const [sourceHint, setSourceHint] = useState<string | null>(null);
  const [formError, setFormError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);
  const [exclusionSubmitting, setExclusionSubmitting] = useState(false);
  const [libraryLifecycleSubmitting, setLibraryLifecycleSubmitting] = useState(false);
  const [archiveConfirmationOpen, setArchiveConfirmationOpen] = useState(false);
  const [exclusionsOpen, setExclusionsOpen] = useState(false);
  const [scanNeeded, setScanNeeded] = useState(false);
  const [scanRunning, setScanRunning] = useState<string | null>(null);
  const [scanResult, setScanResult] = useState<ScanRun | null>(null);
  const [pollingScanId, setPollingScanId] = useState<string | null>(null);
  const activeScanRunning = scanResult ? isScanInProgress(scanResult) : false;
  const scanDisabled = scanRunning !== null || activeScanRunning || library.status !== 'active';

  useEffect(() => {
    if (!pollingScanId) {
      return;
    }

    const scanId = pollingScanId;
    let cancelled = false;
    let timer: ReturnType<typeof setTimeout> | undefined;

    async function pollScan() {
      try {
        const result = await fetchScan(scanId);
        if (cancelled) {
          return;
        }
        setScanResult(result);
        if (isScanInProgress(result)) {
          timer = setTimeout(pollScan, 1_200);
        } else {
          setPollingScanId(null);
        }
      } catch (error) {
        if (!cancelled) {
          const message = messageForError(error, 'Scan status could not be refreshed.');
          setFormError(message);
          onError('Scan status could not be refreshed', message);
          setPollingScanId(null);
        }
      }
    }

    timer = setTimeout(pollScan, 800);

    return () => {
      cancelled = true;
      if (timer) {
        clearTimeout(timer);
      }
    };
  }, [onError, pollingScanId]);

  async function submitSource(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setFormError(null);
    setSubmitting(true);

    try {
      await addLibraryRoot(library.id, { path }, auth.csrfToken);
      setPath('');
      await onLibrariesChange();
      setScanNeeded(true);
    } catch (submitError) {
      const message = messageForError(
        submitError,
        'Enter an absolute path to an existing readable directory mounted into Pixierge.'
      );
      setFormError(message);
      onError('Source path could not be added', message);
    } finally {
      setSubmitting(false);
    }
  }

  async function browseLocalFolder() {
    setFormError(null);
    const directoryPicker = (window as DirectoryPickerWindow).showDirectoryPicker;
    if (!directoryPicker) {
      setSourceHint('This browser cannot expose folder paths. Mount a host folder into Docker and use a /photos/... path.');
      return;
    }

    try {
      const handle = await directoryPicker();
      setPath('/photos/pictures');
      setSourceHint(`Selected "${handle.name}". In Docker, mount that folder under /photos, then add its container path.`);
    } catch (error) {
      if (error instanceof DOMException && error.name === 'AbortError') {
        return;
      }
      setSourceHint('Folder selection was cancelled or blocked. You can still enter a /photos/... path manually.');
    }
  }

  async function removeSource(source: LibrarySource) {
    setFormError(null);

    try {
      await deleteLibraryRoot(library.id, source.id, auth.csrfToken);
      await onLibrariesChange();
      setScanNeeded(true);
    } catch (submitError) {
      const message = messageForError(submitError, 'Source path could not be removed.');
      setFormError(message);
      onError('Source path could not be removed', message);
    }
  }

  async function submitExclusionPattern(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setFormError(null);
    setExclusionSubmitting(true);

    try {
      await addLibraryExclusionPattern(library.id, { pattern: exclusionPattern }, auth.csrfToken);
      setExclusionPattern('');
      await onLibrariesChange();
      setScanNeeded(true);
    } catch (submitError) {
      const message = messageForError(submitError, 'Exclusion pattern could not be added.');
      setFormError(message);
      onError('Exclusion pattern could not be added', message);
    } finally {
      setExclusionSubmitting(false);
    }
  }

  async function removeExclusionPattern(pattern: LibraryExclusionPattern) {
    setFormError(null);

    try {
      await deleteLibraryExclusionPattern(library.id, pattern.id, auth.csrfToken);
      await onLibrariesChange();
      setScanNeeded(true);
    } catch (submitError) {
      const message = messageForError(submitError, 'Exclusion pattern could not be removed.');
      setFormError(message);
      onError('Exclusion pattern could not be removed', message);
    }
  }

  async function runLibraryScan() {
    setFormError(null);
    setScanRunning('library');

    try {
      const result = await scanLibrary(library.id, auth.csrfToken);
      setScanResult(result);
      setScanNeeded(false);
      setPollingScanId(isScanInProgress(result) ? result.id : null);
    } catch (submitError) {
      const message = messageForError(submitError, 'Library scan could not be started.');
      setFormError(message);
      onError('Library scan could not be started', message);
    } finally {
      setScanRunning(null);
    }
  }

  async function runSourceScan(source: LibrarySource) {
    setFormError(null);
    setScanRunning(source.id);

    try {
      const result = await scanLibraryRoot(library.id, source.id, auth.csrfToken);
      setScanResult(result);
      setScanNeeded(false);
      setPollingScanId(isScanInProgress(result) ? result.id : null);
    } catch (submitError) {
      const message = messageForError(submitError, 'Source scan could not be started.');
      setFormError(message);
      onError('Source scan could not be started', message);
    } finally {
      setScanRunning(null);
    }
  }

  async function submitArchiveLibrary() {
    setFormError(null);
    setLibraryLifecycleSubmitting(true);

    try {
      await archiveLibrary(library.id, auth.csrfToken);
      await onLibrariesChange();
    } catch (submitError) {
      const message = messageForError(submitError, 'Library could not be archived.');
      setFormError(message);
      onError('Library could not be archived', message);
    } finally {
      setArchiveConfirmationOpen(false);
      setLibraryLifecycleSubmitting(false);
    }
  }

  async function submitUnarchiveLibrary() {
    setFormError(null);
    setLibraryLifecycleSubmitting(true);

    try {
      await restoreLibrary(library.id, auth.csrfToken);
      await onLibrariesChange();
    } catch (submitError) {
      const message = messageForError(submitError, 'Library could not be unarchived.');
      setFormError(message);
      onError('Library could not be unarchived', message);
    } finally {
      setLibraryLifecycleSubmitting(false);
    }
  }

  return (
    <Card>
      <CardHeader>
        <div className="flex flex-wrap items-start justify-between gap-3">
          <div className="grid gap-1">
            <CardTitle>{library.name}</CardTitle>
            <div className="flex flex-wrap gap-2">
              <Badge variant="secondary">{formatSourceCount(library.sourceCount)}</Badge>
              <Badge variant="success">{library.availableSourceCount} available</Badge>
              {library.unavailableSourceCount > 0 && (
                <Badge variant="warning">{library.unavailableSourceCount} unavailable</Badge>
              )}
            </div>
          </div>
          {library.status === 'archived' ? (
            <Button
              disabled={libraryLifecycleSubmitting}
              onClick={() => void submitUnarchiveLibrary()}
              type="button"
              variant="secondary"
            >
              <RotateCcw className="h-4 w-4" aria-hidden />
              Unarchive
            </Button>
          ) : (
            <div className="flex flex-wrap gap-2">
              <Button
                disabled={scanDisabled || library.sourceCount === 0}
                onClick={() => void runLibraryScan()}
                type="button"
                variant="secondary"
              >
                <RefreshCw className={cn('h-4 w-4', activeScanRunning && 'animate-spin')} aria-hidden />
                Scan library
              </Button>
              <Button
                disabled={libraryLifecycleSubmitting || activeScanRunning}
                onClick={() => setArchiveConfirmationOpen(true)}
                type="button"
                variant="secondary"
              >
                <Archive className="h-4 w-4" aria-hidden />
                Archive
              </Button>
            </div>
          )}
        </div>
      </CardHeader>
      <CardContent className="grid gap-4">
        {library.status === 'archived' && (
          <Alert>Archived libraries are hidden from normal browsing and cannot be scanned.</Alert>
        )}
        {scanNeeded && (
          <Alert>
            <div className="flex flex-wrap items-center justify-between gap-3">
              <span>Library settings changed.</span>
              <span className="flex gap-2">
                <Button
                  disabled={scanDisabled || library.sourceCount === 0}
                  onClick={() => void runLibraryScan()}
                  size="sm"
                  type="button"
                >
                  <RefreshCw className={cn('h-4 w-4', activeScanRunning && 'animate-spin')} aria-hidden />
                  Run scan now
                </Button>
                <Button onClick={() => setScanNeeded(false)} size="sm" type="button" variant="ghost">
                  Later
                </Button>
              </span>
            </div>
          </Alert>
        )}
        {scanResult && <ScanSummary scan={scanResult} />}
        <form className="grid gap-3 lg:grid-cols-[minmax(0,1fr)_auto_auto]" onSubmit={submitSource}>
          <div className="grid gap-2">
            <Label htmlFor={`source-path-${library.id}`}>Source path</Label>
            <Input
              id={`source-path-${library.id}`}
              onChange={(event) => setPath(event.target.value)}
              placeholder="/photos/pictures"
              value={path}
            />
          </div>
          <Button className="self-end" onClick={() => void browseLocalFolder()} type="button" variant="secondary">
            <FolderOpen className="h-4 w-4" aria-hidden />
            Browse
          </Button>
          <Button className="self-end" disabled={submitting || path.trim() === ''} type="submit">
            <Plus className="h-4 w-4" aria-hidden />
            Add source
          </Button>
        </form>
        {formError && <p className="text-sm text-muted-foreground">{formError}</p>}
        {sourceHint && <p className="text-sm text-muted-foreground">{sourceHint}</p>}
        <p className="text-sm text-muted-foreground">
          Docker sources must use container paths. Mount your folders under <span className="font-mono">/photos</span>, then add paths like <span className="font-mono">/photos/pictures</span> or <span className="font-mono">/photos/archive</span>.
        </p>

        {library.sources.length === 0 ? (
          <p className="text-sm text-muted-foreground">No source paths have been added to this library.</p>
        ) : (
          <div className="overflow-x-auto">
            <Table>
              <TableHeader>
                <TableRow>
                  <TableHead>Path</TableHead>
                  <TableHead>Health</TableHead>
                  <TableHead className="w-28">Actions</TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {library.sources.map((source) => (
                  <TableRow key={source.id}>
                    <TableCell className="max-w-0">
                      <span className="block truncate font-mono text-xs">{source.path}</span>
                    </TableCell>
                    <TableCell>
                      <SourceHealth source={source} />
                    </TableCell>
                    <TableCell className="flex gap-1">
                      <Button
                        aria-label={`Scan ${source.path}`}
                        disabled={scanDisabled || !source.available}
                        onClick={() => void runSourceScan(source)}
                        size="icon"
                        type="button"
                        variant="ghost"
                      >
                        <RefreshCw className={cn('h-4 w-4', activeScanRunning && 'animate-spin')} aria-hidden />
                      </Button>
                      <Button
                        aria-label={`Remove ${source.path}`}
                        onClick={() => void removeSource(source)}
                        size="icon"
                        type="button"
                        variant="ghost"
                      >
                        <Trash2 className="h-4 w-4" aria-hidden />
                      </Button>
                    </TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          </div>
        )}
        <div className="rounded-md border border-border">
          <button
            aria-expanded={exclusionsOpen}
            className="flex min-h-12 w-full items-center justify-between gap-3 px-4 text-left text-sm font-medium text-foreground"
            onClick={() => setExclusionsOpen((open) => !open)}
            type="button"
          >
            <span className="inline-flex items-center gap-2">
              {exclusionsOpen ? (
                <ChevronDown className="h-4 w-4 text-muted-foreground" aria-hidden />
              ) : (
                <ChevronRight className="h-4 w-4 text-muted-foreground" aria-hidden />
              )}
              Library exclusions
            </span>
            <Badge variant="secondary">{library.exclusionPatterns.length}</Badge>
          </button>

          {exclusionsOpen && (
            <div className="grid gap-3 border-t border-border p-4">
              <form className="grid gap-3 lg:grid-cols-[minmax(0,1fr)_auto]" onSubmit={submitExclusionPattern}>
                <div className="grid gap-2">
                  <Label htmlFor={`exclusion-pattern-${library.id}`}>Exclusion pattern</Label>
                  <Input
                    id={`exclusion-pattern-${library.id}`}
                    onChange={(event) => setExclusionPattern(event.target.value)}
                    placeholder="**/.cache/**"
                    value={exclusionPattern}
                  />
                </div>
                <Button
                  className="self-end"
                  disabled={exclusionSubmitting || exclusionPattern.trim() === ''}
                  type="submit"
                  variant="secondary"
                >
                  <Plus className="h-4 w-4" aria-hidden />
                  Add exclusion
                </Button>
              </form>

              {library.exclusionPatterns.length === 0 ? (
                <p className="text-sm text-muted-foreground">No exclusion patterns configured.</p>
              ) : (
                <div className="flex flex-wrap gap-2">
                  {library.exclusionPatterns.map((pattern) => (
                    <span
                      className="inline-flex min-h-9 items-center gap-2 rounded-md border border-border px-2 text-sm"
                      key={pattern.id}
                    >
                      <span className="font-mono text-xs">{pattern.pattern}</span>
                      <Button
                        aria-label={`Remove exclusion ${pattern.pattern}`}
                        onClick={() => void removeExclusionPattern(pattern)}
                        size="icon"
                        type="button"
                        variant="ghost"
                      >
                        <Trash2 className="h-4 w-4" aria-hidden />
                      </Button>
                    </span>
                  ))}
                </div>
              )}
            </div>
          )}
        </div>
        {archiveConfirmationOpen && (
          <ArchiveLibraryDialog
            confirming={libraryLifecycleSubmitting}
            libraryId={library.id}
            libraryName={library.name}
            onCancel={() => setArchiveConfirmationOpen(false)}
            onConfirm={() => void submitArchiveLibrary()}
          />
        )}
      </CardContent>
    </Card>
  );
}

function ArchiveLibraryDialog({
  confirming,
  libraryId,
  libraryName,
  onCancel,
  onConfirm
}: {
  confirming: boolean;
  libraryId: string;
  libraryName: string;
  onCancel: () => void;
  onConfirm: () => void;
}) {
  const titleId = `archive-library-title-${libraryId}`;

  return (
    <div className="fixed inset-0 z-50 grid place-items-center bg-black/60 p-4">
      <div
        aria-labelledby={titleId}
        aria-modal="true"
        className="grid w-full max-w-md gap-4 rounded-md border border-border bg-surface p-5 text-foreground shadow-lg"
        role="dialog"
      >
        <div className="grid gap-2">
          <h2 className="text-lg font-semibold" id={titleId}>
            Archive {libraryName}?
          </h2>
          <p className="text-sm text-muted-foreground">
            Archived libraries are hidden from normal browsing and cannot be scanned until they are unarchived.
          </p>
        </div>
        <div className="flex flex-wrap justify-end gap-2">
          <Button disabled={confirming} onClick={onCancel} type="button" variant="ghost">
            Cancel
          </Button>
          <Button disabled={confirming} onClick={onConfirm} type="button" variant="secondary">
            <Archive className="h-4 w-4" aria-hidden />
            Archive
          </Button>
        </div>
      </div>
    </div>
  );
}

function ScanSummary({ scan }: { scan: ScanRun }) {
  const running = isScanInProgress(scan);
  return (
    <div className="rounded-md border border-border p-4" role="status">
      <div className="flex flex-wrap items-center justify-between gap-2">
        <p className="inline-flex items-center gap-2 text-sm font-medium text-foreground">
          {running && <RefreshCw className="h-4 w-4 animate-spin text-muted-foreground" aria-hidden />}
          {formatScanStatus(scan.status)}
        </p>
        <Badge variant={scan.errorCount > 0 ? 'warning' : 'success'}>{scan.scannedFileCount} scanned</Badge>
      </div>
      <div className="mt-3 grid gap-2 text-sm text-muted-foreground sm:grid-cols-3 lg:grid-cols-4 xl:grid-cols-8">
        <span>Added {scan.addedCount}</span>
        <span>Unchanged {scan.unchangedCount}</span>
        <span>Moved {scan.movedCount}</span>
        <span>Modified {scan.modifiedCount}</span>
        <span>Duplicates {scan.duplicateCount}</span>
        <span>Missing {scan.missingCount}</span>
        <span>Reappeared {scan.reappearedCount}</span>
        <span>Errors {scan.errorCount}</span>
      </div>
    </div>
  );
}

function SourceHealth({ source }: { source: LibrarySource }) {
  if (source.available) {
    return (
      <span className="inline-flex items-center gap-2 text-sm text-foreground">
        <CheckCircle2 className="h-4 w-4 text-muted-foreground" aria-hidden />
        Available
      </span>
    );
  }

  return (
    <span className="inline-flex items-center gap-2 text-sm text-foreground">
      <AlertTriangle className="h-4 w-4 text-muted-foreground" aria-hidden />
      Unavailable{source.unavailableReason ? `: ${formatUnavailableReason(source.unavailableReason)}` : ''}
    </span>
  );
}

function formatSourceCount(count: number) {
  return `${count} ${count === 1 ? 'source' : 'sources'}`;
}

function formatUnavailableReason(reason: string) {
  return reason.replaceAll('_', ' ');
}

function formatScanStatus(status: ScanRun['status']) {
  if (status === 'running' || status === 'queued') {
    return 'Scan running';
  }
  if (status === 'completed_with_errors') {
    return 'Scan completed with errors';
  }
  return `Scan ${status.replaceAll('_', ' ')}`;
}

function isScanInProgress(scan: ScanRun) {
  return scan.status === 'running' || scan.status === 'queued';
}

function messageForError(error: unknown, fallback: string) {
  if (!(error instanceof ApiError)) {
    return fallback;
  }
  return error.message.startsWith('Request failed with ') ? fallback : error.message;
}

function EmptySettingsPage({ label }: { label: string }) {
  return (
    <div className="grid min-h-96 place-items-center">
      <div className="grid max-w-md justify-items-center gap-2 text-center">
        <p className="text-sm font-medium text-foreground">{label} is empty</p>
        <p className="text-sm text-muted-foreground">Controls for this area are not available yet.</p>
      </div>
    </div>
  );
}
