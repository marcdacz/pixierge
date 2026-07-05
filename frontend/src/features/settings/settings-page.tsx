import {
  AlertTriangle,
  Archive,
  Blocks,
  CheckCircle2,
  FolderOpen,
  Plus,
  SlidersHorizontal,
  Trash2
} from 'lucide-react';
import { useState, type ComponentType, type FormEvent } from 'react';
import {
  addLibraryRoot,
  createLibrary,
  deleteLibraryRoot,
  type AuthResponse,
  type LibrarySummary,
  type LibrarySource
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
  onLibrariesChange
}: SettingsPageProps) {
  const [libraryName, setLibraryName] = useState('');
  const [formError, setFormError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);
  const sourceCount = libraries.reduce((total, library) => total + library.sourceCount, 0);
  const unavailableCount = libraries.reduce((total, library) => total + library.unavailableSourceCount, 0);

  async function submitLibrary(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setFormError(null);
    setSubmitting(true);

    try {
      await createLibrary({ name: libraryName }, auth.csrfToken);
      setLibraryName('');
      await onLibrariesChange();
    } catch (submitError) {
      setFormError('Library could not be created.');
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <div className="grid gap-6">
      {error && <Alert>{error}</Alert>}

      <section aria-label="Source totals" className="grid gap-3 md:grid-cols-3">
        <SourceStat label="Libraries" value={libraries.length} />
        <SourceStat label="Sources" value={sourceCount} />
        <SourceStat label="Unavailable" value={unavailableCount} warning={unavailableCount > 0} />
      </section>

      <Card>
        <CardHeader>
          <CardTitle>Create library</CardTitle>
        </CardHeader>
        <CardContent>
          <form className="grid gap-3 sm:grid-cols-[minmax(0,1fr)_auto]" onSubmit={submitLibrary}>
            <div className="grid gap-2">
              <Label htmlFor="library-name">Library name</Label>
              <Input
                id="library-name"
                onChange={(event) => setLibraryName(event.target.value)}
                placeholder="Family Photos"
                value={libraryName}
              />
            </div>
            <Button className="self-end" disabled={submitting || libraryName.trim() === ''} type="submit">
              <Plus className="h-4 w-4" aria-hidden />
              Create
            </Button>
          </form>
          {formError && <p className="mt-3 text-sm text-muted-foreground">{formError}</p>}
        </CardContent>
      </Card>

      {loading && <p className="text-sm text-muted-foreground">Loading sources...</p>}

      {!loading && libraries.length === 0 && (
        <div className="grid min-h-60 place-items-center rounded-md border border-dashed border-border">
          <div className="grid max-w-md justify-items-center gap-2 text-center">
            <SlidersHorizontal className="h-7 w-7 text-muted-foreground" aria-hidden />
            <p className="text-sm font-medium text-foreground">No libraries configured</p>
            <p className="text-sm text-muted-foreground">Create a named library, then add one or more source paths.</p>
          </div>
        </div>
      )}

      {libraries.map((library) => (
        <LibrarySourceCard
          auth={auth}
          key={library.id}
          library={library}
          onLibrariesChange={onLibrariesChange}
        />
      ))}
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

function LibrarySourceCard({
  auth,
  library,
  onLibrariesChange
}: {
  auth: AuthResponse;
  library: LibrarySummary;
  onLibrariesChange: () => Promise<void>;
}) {
  const [path, setPath] = useState('');
  const [sourceHint, setSourceHint] = useState<string | null>(null);
  const [formError, setFormError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);

  async function submitSource(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setFormError(null);
    setSubmitting(true);

    try {
      await addLibraryRoot(library.id, { path }, auth.csrfToken);
      setPath('');
      await onLibrariesChange();
    } catch (submitError) {
      setFormError('Source path could not be added.');
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
    } catch (submitError) {
      setFormError('Source path could not be removed.');
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
        </div>
      </CardHeader>
      <CardContent className="grid gap-4">
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
                  <TableHead className="w-16">Actions</TableHead>
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
                    <TableCell>
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
      </CardContent>
    </Card>
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

function EmptySettingsPage({ label }: { label: string }) {
  return (
    <div className="grid min-h-96 place-items-center">
      <div className="grid max-w-md justify-items-center gap-2 text-center">
        <p className="text-sm font-medium text-foreground">{label} is empty</p>
        <p className="text-sm text-muted-foreground">Controls for this area will be added in a later slice.</p>
      </div>
    </div>
  );
}
