import {
  ChevronDown,
  ChevronRight,
  Folder,
  FolderOpen,
  HardDrive,
  Pencil,
  SlidersHorizontal
} from 'lucide-react';
import { useEffect, useMemo, useState, type ComponentType } from 'react';
import {
  fetchAssets,
  fetchLibraryTree,
  renameLibraryFolder,
  ApiError,
  type AssetBrowseResponse,
  type AuthResponse,
  type LibrarySummary,
  type LibraryTreeNode
} from '@/api';
import { Alert } from '@/components/ui/alert';
import { Button } from '@/components/ui/button';
import { cn } from '@/lib/utils';
import {
  BrowseSidebar,
  BrowseSidebarShowControl,
  BROWSE_LAYOUT_HEIGHT_CLASS,
  BROWSE_SIDEBAR_COLLAPSED_KEYS,
  useBrowseSidebarState
} from '@/features/browse/browse-sidebar';
import { InlineNameField } from '@/features/browse/inline-name-field';
import { InlineEditableTitle } from '@/features/library/inline-editable-title';
import { EmptyPanel, mergeBrowseSections } from '@/features/library/photo-grid';
import { PhotoBrowser } from '@/features/library/photo-browser';

export type { AssetTileSize } from './photo-grid';
export { AssetGrid, AssetFocus, AssetTile } from './photo-grid';

const FOLDER_TREE_BASE_PADDING_REM = 0.5;
const FOLDER_TREE_INDENT_REM = 0.75;
const LIBRARY_STATUS_ACTIVE = 'active';
const PAGE_SIZE = 48;

type LibraryHomeProps = {
  auth?: AuthResponse;
  error?: string | null;
  libraries?: LibrarySummary[];
  loading?: boolean;
  onConfigureSources?: () => void;
  onError?: (title: string, description?: string) => void;
  onRenameLibrary?: (libraryId: string, name: string) => Promise<void>;
};

export function LibraryHome({
  auth,
  error = null,
  libraries = [],
  loading = false,
  onConfigureSources,
  onError,
  onRenameLibrary
}: LibraryHomeProps) {
  const librariesWithSources = libraries.filter(
    (library) => library.status === LIBRARY_STATUS_ACTIVE && library.sourceCount > 0
  );

  if (loading) {
    return <p className="text-sm text-muted-foreground">Loading libraries...</p>;
  }

  if (librariesWithSources.length === 0) {
    return (
      <div className="grid gap-8">
        <div className="grid gap-1">
          <h1 className="text-2xl font-semibold text-foreground">Libraries</h1>
          <p className="text-sm text-muted-foreground">No library sources have been added yet.</p>
        </div>
        {error && <Alert>{error}</Alert>}
        <EmptyPanel
          action={onConfigureSources ? (
            <Button className="mt-2" onClick={onConfigureSources} type="button">
              <SlidersHorizontal className="h-4 w-4" aria-hidden />
              Configure sources
            </Button>
          ) : null}
          description="Configure named libraries and source folders to start building your catalog."
          icon={FolderOpen}
          title="Empty library"
        />
      </div>
    );
  }

  return (
    <LibraryBrowser
      auth={auth}
      error={error}
      libraries={librariesWithSources}
      onError={onError}
      onRenameLibrary={onRenameLibrary}
    />
  );
}

function LibraryBrowser({
  auth,
  error,
  libraries,
  onError,
  onRenameLibrary
}: {
  auth?: AuthResponse;
  error: string | null;
  libraries: LibrarySummary[];
  onError?: (title: string, description?: string) => void;
  onRenameLibrary?: (libraryId: string, name: string) => Promise<void>;
}) {
  const defaultLibraryId = libraries.length === 1 ? libraries[0]?.id : undefined;
  const [tree, setTree] = useState<LibraryTreeNode[]>([]);
  const [libraryRootAssetCounts, setLibraryRootAssetCounts] = useState<Record<string, number>>({});
  const [libraryAssetCounts, setLibraryAssetCounts] = useState<Record<string, number>>({});
  const [selectedFolder, setSelectedFolder] = useState<string | null>(null);
  const [selectedLibraryId, setSelectedLibraryId] = useState<string | undefined>(defaultLibraryId);
  const [expandedPaths, setExpandedPaths] = useState<Set<string>>(() => new Set());
  const [expandedLibraries, setExpandedLibraries] = useState<Set<string>>(() => new Set());
  const { collapsed: treeCollapsed, setCollapsed: setTreeCollapsed, isLowResolution } = useBrowseSidebarState(
    BROWSE_SIDEBAR_COLLAPSED_KEYS.libraries
  );
  const [page, setPage] = useState(0);
  const [assets, setAssets] = useState<AssetBrowseResponse | null>(null);
  const [loadingTree, setLoadingTree] = useState(false);
  const [treeLoaded, setTreeLoaded] = useState(false);
  const [loadingAssets, setLoadingAssets] = useState(false);
  const [loadingMore, setLoadingMore] = useState(false);
  const [browseError, setBrowseError] = useState<string | null>(null);
  const [assetFocused, setAssetFocused] = useState(false);
  const [assetsRevision, setAssetsRevision] = useState(0);

  const selectedLibrary =
    libraries.find((library) => library.id === selectedLibraryId) ??
    libraries.find((library) => library.id === defaultLibraryId) ??
    libraries[0];
  const browseContextKey = `${selectedLibraryId ?? defaultLibraryId ?? ''}:${selectedFolder ?? ''}:${assetsRevision}`;
  const librarySections = useMemo(
    () => groupTreeByLibrary(tree, libraries, libraryRootAssetCounts, libraryAssetCounts),
    [libraries, libraryAssetCounts, libraryRootAssetCounts, tree]
  );

  useEffect(() => {
    setSelectedLibraryId(defaultLibraryId);
  }, [defaultLibraryId]);

  useEffect(() => {
    let ignore = false;
    setLoadingTree(true);
    setTreeLoaded(false);
    setBrowseError(null);
    setSelectedFolder(null);
    setExpandedPaths(new Set());
    setExpandedLibraries(new Set(libraries.map((library) => library.id)));

    fetchLibraryTree(defaultLibraryId)
      .then((response) => {
        if (ignore) {
          return;
        }
        setTree(response.roots);
        setLibraryRootAssetCounts(response.libraryRootAssetCounts ?? {});
        setLibraryAssetCounts(response.libraryAssetCounts ?? {});
        setExpandedPaths(new Set(collectExpandablePaths(response.roots)));
        setExpandedLibraries(new Set(libraries.map((library) => library.id)));
        setTreeLoaded(true);
      })
      .catch(() => {
        if (!ignore) {
          setBrowseError('Folders could not be loaded.');
          setTree([]);
          setLibraryRootAssetCounts({});
          setLibraryAssetCounts({});
          setTreeLoaded(true);
        }
      })
      .finally(() => {
        if (!ignore) {
          setLoadingTree(false);
        }
      });

    return () => {
      ignore = true;
    };
  }, [defaultLibraryId]);

  useEffect(() => {
    setPage(0);
    setAssets(null);
  }, [browseContextKey]);

  useEffect(() => {
    if (!treeLoaded) {
      return;
    }

    let ignore = false;
    const requestedPage = page;
    const requestedContext = browseContextKey;
    const isInitialLoad = requestedPage === 0;

    if (isInitialLoad) {
      setLoadingAssets(true);
    } else {
      setLoadingMore(true);
    }
    setBrowseError(null);

    fetchAssets({
      libraryId: selectedLibraryId ?? defaultLibraryId,
      folder: selectedFolder ?? undefined,
      includeDescendants: selectedFolder ? true : undefined,
      page: requestedPage,
      pageSize: PAGE_SIZE
    })
      .then((response) => {
        if (ignore || requestedContext !== browseContextKey) {
          return;
        }
        if (requestedPage === 0) {
          setAssets(response);
        } else {
          setAssets((current) =>
            current
              ? {
                  ...response,
                  sections: mergeBrowseSections(current.sections, response.sections)
                }
              : response
          );
        }
      })
      .catch(() => {
        if (!ignore && requestedContext === browseContextKey) {
          setBrowseError('Assets could not be loaded.');
          if (requestedPage === 0) {
            setAssets(null);
          }
        }
      })
      .finally(() => {
        if (!ignore && requestedContext === browseContextKey) {
          if (isInitialLoad) {
            setLoadingAssets(false);
          } else {
            setLoadingMore(false);
          }
        }
      });

    return () => {
      ignore = true;
    };
  }, [browseContextKey, defaultLibraryId, page, selectedFolder, selectedLibraryId, treeLoaded]);

  function toggleLibraryExpanded(libraryId: string) {
    setExpandedLibraries((current) => {
      const next = new Set(current);
      if (next.has(libraryId)) {
        next.delete(libraryId);
      } else {
        next.add(libraryId);
      }
      return next;
    });
  }

  function updateFolder(path: string | null, libraryId?: string) {
    setPage(0);
    setSelectedFolder(path);
    setSelectedLibraryId(libraryId ?? defaultLibraryId);
  }

  function toggleExpanded(path: string) {
    setExpandedPaths((current) => {
      const next = new Set(current);
      if (next.has(path)) {
        next.delete(path);
      } else {
        next.add(path);
      }
      return next;
    });
  }

  async function reloadTree(remapOldPath?: string, remapNewPath?: string) {
    const response = await fetchLibraryTree(defaultLibraryId);
    setTree(response.roots);
    setLibraryRootAssetCounts(response.libraryRootAssetCounts ?? {});
    setLibraryAssetCounts(response.libraryAssetCounts ?? {});
    if (remapOldPath && remapNewPath) {
      setSelectedFolder((current) => (current ? remapPathPrefix(current, remapOldPath, remapNewPath) : current));
      setExpandedPaths((current) => {
        const next = new Set<string>();
        for (const path of current) {
          next.add(remapPathPrefix(path, remapOldPath, remapNewPath));
        }
        return next;
      });
    } else {
      setExpandedPaths(new Set(collectExpandablePaths(response.roots)));
    }
  }

  async function renameFolder(libraryId: string, path: string, name: string) {
    if (!auth) {
      return;
    }
    try {
      const renamed = await renameLibraryFolder(libraryId, { path, name }, auth.csrfToken);
      await reloadTree(path, renamed.path);
      setAssetsRevision((current) => current + 1);
    } catch (renameError) {
      onError?.(
        'Folder could not be renamed',
        messageForError(renameError, 'Check that the library source is writable and try again.')
      );
      throw renameError;
    }
  }

  async function renameLibrary(libraryId: string, name: string) {
    if (!onRenameLibrary) {
      return;
    }
    try {
      await onRenameLibrary(libraryId, name);
    } catch (renameError) {
      onError?.(
        'Library could not be renamed',
        messageForError(renameError, 'Check the name and try again.')
      );
      throw renameError;
    }
  }

  const librarySubtitle = onRenameLibrary ? (
    <InlineEditableTitle
      aria-label="Library name"
      onSave={(name) => renameLibrary(selectedLibrary.id, name)}
      size="sm"
      value={selectedLibrary.name}
    />
  ) : (
    <p className="text-sm text-muted-foreground">{selectedLibrary.name}</p>
  );
  const showControl =
    !assetFocused && treeCollapsed ? (
      <BrowseSidebarShowControl onShow={() => setTreeCollapsed(false)} title="Folders" />
    ) : null;

  return (
    <div
      className={cn(
        'relative grid gap-0 overflow-hidden',
        BROWSE_LAYOUT_HEIGHT_CLASS,
        'grid-rows-[minmax(0,1fr)]',
        !assetFocused && !isLowResolution && !treeCollapsed && 'lg:grid-cols-[auto_minmax(0,1fr)]'
      )}
    >
      {!assetFocused && !treeCollapsed && (
        <BrowseSidebar
          isLowResolution={isLowResolution}
          onCloseOverlay={() => setTreeCollapsed(true)}
          onHide={() => setTreeCollapsed(true)}
          title="Folders"
        >
          <nav
            aria-label="Folders"
            className={cn('flex min-h-0 flex-1 flex-col gap-2 overflow-y-auto', isLowResolution ? 'pr-0' : 'pr-1')}
          >
            {loadingTree && <p className="px-2 py-2 text-sm text-muted-foreground">Loading folders...</p>}
            {!loadingTree && tree.length === 0 && (assets?.totalCount ?? 0) === 0 && (
              <p className="px-2 py-2 text-sm text-muted-foreground">No scanned folders yet.</p>
            )}
            {librarySections.map((section) => (
              <LibraryFolderSection
                expandedLibraries={expandedLibraries}
                expandedPaths={expandedPaths}
                key={section.libraryId}
                libraryId={section.libraryId}
                nodes={section.nodes}
                onClearSelection={() => updateFolder(null, section.libraryId)}
                onRenameFolder={
                  auth ? (path, name) => renameFolder(section.libraryId, path, name) : undefined
                }
                onRenameLibrary={
                  onRenameLibrary ? (name) => renameLibrary(section.libraryId, name) : undefined
                }
                onSelect={(path) => updateFolder(path, section.libraryId)}
                onToggleExpanded={toggleExpanded}
                onToggleLibraryExpanded={toggleLibraryExpanded}
                selectedFolder={selectedFolder}
                selectedLibraryId={selectedLibraryId}
                title={section.libraryName}
                totalCount={section.totalCount}
              />
            ))}
          </nav>
        </BrowseSidebar>
      )}

      <PhotoBrowser
        key={browseContextKey}
        auth={auth}
        assets={assets}
        browseContextKey={browseContextKey}
        emptyDescription="Run a scan from Settings, or adjust the current folder."
        emptyTitle="No assets found"
        error={error || browseError}
        leadingControls={showControl ?? undefined}
        loadingAssets={loadingAssets}
        loadingMore={loadingMore}
        onFocusChange={setAssetFocused}
        onLoadMore={() => setPage((current) => current + 1)}
        onRenameSection={
          auth
            ? (folderPath, name) => {
                const section = assets?.sections.find((candidate) => candidate.folderPath === folderPath);
                const libraryId = section?.assets[0]?.libraryId ?? selectedLibraryId ?? selectedLibrary.id;
                return renameFolder(libraryId, folderPath, name);
              }
            : undefined
        }
        showSectionHeaders={!selectedFolder || (assets?.sections.length ?? 0) > 1}
        subtitle={librarySubtitle}
        title={
          selectedFolder ? (
            auth ? (
              <InlineEditableTitle
                aria-label="Folder name"
                onSave={(name) => renameFolder(selectedLibrary.id, selectedFolder, name)}
                value={folderName(selectedFolder)}
              />
            ) : (
              <h2 className="truncate text-2xl font-semibold text-foreground">{folderName(selectedFolder)}</h2>
            )
          ) : (
            <h2 className="truncate text-2xl font-semibold text-foreground">All folders</h2>
          )
        }
      />
    </div>
  );
}

function LibraryFolderSection({
  expandedLibraries,
  expandedPaths,
  libraryId,
  nodes,
  onClearSelection,
  onRenameFolder,
  onRenameLibrary,
  onSelect,
  onToggleExpanded,
  onToggleLibraryExpanded,
  selectedFolder,
  selectedLibraryId,
  title,
  totalCount
}: {
  expandedLibraries: Set<string>;
  expandedPaths: Set<string>;
  libraryId: string;
  nodes: LibraryTreeNode[];
  onClearSelection: () => void;
  onRenameFolder?: (path: string, name: string) => Promise<void> | void;
  onRenameLibrary?: (name: string) => Promise<void> | void;
  onSelect: (path: string) => void;
  onToggleExpanded: (path: string) => void;
  onToggleLibraryExpanded: (libraryId: string) => void;
  selectedFolder: string | null;
  selectedLibraryId?: string;
  title: string;
  totalCount: number;
}) {
  const hasChildren = nodes.length > 0;
  const expanded = expandedLibraries.has(libraryId);
  const active = selectedFolder === null && selectedLibraryId === libraryId;

  return (
    <section className="grid gap-0.5">
      <FolderTreeRow
        active={active}
        count={totalCount}
        depth={0}
        expanded={expanded}
        hasChildren={hasChildren}
        icon={HardDrive}
        label={title}
        onRename={onRenameLibrary}
        onSelect={onClearSelection}
        onToggleExpanded={() => onToggleLibraryExpanded(libraryId)}
        renameAriaLabel="Library name"
      />
      {hasChildren && expanded && (
        <div className="grid gap-0.5">
          {nodes.map((node) => (
            <FolderTreeNode
              depth={1}
              expandedPaths={expandedPaths}
              key={node.id}
              node={node}
              onRename={onRenameFolder}
              onSelect={onSelect}
              onToggleExpanded={onToggleExpanded}
              selectedFolder={selectedFolder}
            />
          ))}
        </div>
      )}
    </section>
  );
}

function FolderTreeNode({
  depth,
  expandedPaths,
  node,
  onRename,
  onSelect,
  onToggleExpanded,
  selectedFolder
}: {
  depth: number;
  expandedPaths: Set<string>;
  node: LibraryTreeNode;
  onRename?: (path: string, name: string) => Promise<void> | void;
  onSelect: (path: string) => void;
  onToggleExpanded: (path: string) => void;
  selectedFolder: string | null;
}) {
  const hasChildren = node.children.length > 0;
  const expanded = expandedPaths.has(node.path);
  const active = selectedFolder === node.path;

  return (
    <div className="grid gap-0.5">
      <FolderTreeRow
        active={active}
        count={node.assetCount}
        depth={depth}
        expanded={expanded}
        hasChildren={hasChildren}
        label={node.name}
        onRename={onRename ? (name) => onRename(node.path, name) : undefined}
        onSelect={() => onSelect(node.path)}
        onToggleExpanded={() => onToggleExpanded(node.path)}
        renameAriaLabel="Folder name"
      />
      {hasChildren && expanded && node.children.map((child) => (
        <FolderTreeNode
          depth={depth + 1}
          expandedPaths={expandedPaths}
          key={child.id}
          node={child}
          onRename={onRename}
          onSelect={onSelect}
          onToggleExpanded={onToggleExpanded}
          selectedFolder={selectedFolder}
        />
      ))}
    </div>
  );
}

function FolderTreeRow({
  active,
  count,
  depth,
  expanded,
  hasChildren,
  icon,
  label,
  onRename,
  onSelect,
  onToggleExpanded,
  renameAriaLabel = 'Name'
}: {
  active: boolean;
  count: number;
  depth: number;
  expanded: boolean;
  hasChildren: boolean;
  icon?: ComponentType<{ className?: string }>;
  label: string;
  onRename?: (name: string) => Promise<void> | void;
  onSelect: () => void;
  onToggleExpanded: () => void;
  renameAriaLabel?: string;
}) {
  const [renaming, setRenaming] = useState(false);
  const DefaultIcon = active ? FolderOpen : Folder;
  const RowIcon = icon ?? DefaultIcon;

  return (
    <div
      className={cn(
        'group flex min-h-7 items-center gap-1 rounded-md pr-2 text-sm text-muted-foreground transition-colors hover:bg-muted hover:text-foreground',
        active && 'bg-muted text-foreground'
      )}
      style={{ paddingLeft: `${FOLDER_TREE_BASE_PADDING_REM + depth * FOLDER_TREE_INDENT_REM}rem` }}
    >
      {hasChildren ? (
        <button
          aria-expanded={expanded}
          aria-label={expanded ? `Collapse ${label}` : `Expand ${label}`}
          className="grid h-6 w-5 shrink-0 place-items-center rounded-sm text-muted-foreground hover:text-foreground"
          onClick={(event) => {
            event.stopPropagation();
            onToggleExpanded();
          }}
          type="button"
        >
          {expanded ? (
            <ChevronDown className="h-3.5 w-3.5" aria-hidden />
          ) : (
            <ChevronRight className="h-3.5 w-3.5" aria-hidden />
          )}
        </button>
      ) : (
        <span className="w-5 shrink-0" aria-hidden />
      )}
      {renaming && onRename ? (
        <>
          <RowIcon className="h-4 w-4 shrink-0" aria-hidden />
          <InlineNameField
            ariaLabel={renameAriaLabel}
            initialValue={label}
            onCancel={() => setRenaming(false)}
            onCommit={async (name) => {
              await onRename(name);
              setRenaming(false);
            }}
            placeholder={renameAriaLabel}
          />
        </>
      ) : (
        <div className="flex min-w-0 flex-1 items-center gap-0.5">
          <button
            className="flex min-h-7 min-w-0 items-center gap-2 overflow-hidden text-left"
            onClick={onSelect}
            type="button"
          >
            <RowIcon className="h-4 w-4 shrink-0" aria-hidden />
            <span className="truncate">
              {label} <span className="text-xs tabular-nums">({count})</span>
            </span>
          </button>
          {onRename && (
            <Button
              aria-label={`Rename ${label}`}
              className="size-6 shrink-0 opacity-0 transition-opacity group-hover:opacity-100 focus-visible:opacity-100 group-focus-within:opacity-100"
              onClick={(event) => {
                event.stopPropagation();
                setRenaming(true);
              }}
              size="icon"
              type="button"
              variant="ghost"
            >
              <Pencil className="h-3 w-3" aria-hidden />
            </Button>
          )}
        </div>
      )}
    </div>
  );
}

function folderName(path: string) {
  const index = path.lastIndexOf('/');
  return index >= 0 ? path.slice(index + 1) : path;
}

function messageForError(error: unknown, fallback: string) {
  if (!(error instanceof ApiError)) {
    return fallback;
  }
  return error.message.startsWith('Request failed with ') ? fallback : error.message;
}

function remapPathPrefix(path: string, oldPrefix: string, newPrefix: string) {
  if (path === oldPrefix) {
    return newPrefix;
  }
  if (path.startsWith(`${oldPrefix}/`)) {
    return `${newPrefix}${path.slice(oldPrefix.length)}`;
  }
  return path;
}

function collectExpandablePaths(nodes: LibraryTreeNode[]): string[] {
  return nodes.flatMap((node) => [
    ...(node.children.length > 0 ? [node.path] : []),
    ...collectExpandablePaths(node.children)
  ]);
}

function groupTreeByLibrary(
  nodes: LibraryTreeNode[],
  libraries: LibrarySummary[],
  libraryRootAssetCounts: Record<string, number> = {},
  libraryAssetCounts: Record<string, number> = {}
) {
  const nodesByLibrary = new Map<string, LibraryTreeNode[]>();
  for (const node of nodes) {
    const current = nodesByLibrary.get(node.libraryId) ?? [];
    current.push(node);
    nodesByLibrary.set(node.libraryId, current);
  }

  return libraries.map((library) => {
    const libraryNodes = nodesByLibrary.get(library.id) ?? [];
    const rootAssetCount = libraryRootAssetCounts[library.id] ?? 0;
    const totalCount =
      libraryAssetCounts[library.id] ??
      libraryNodes.reduce((total, node) => total + node.assetCount, 0) + rootAssetCount;
    return {
      libraryId: library.id,
      libraryName: library.name,
      totalCount,
      nodes: libraryNodes
    };
  });
}
