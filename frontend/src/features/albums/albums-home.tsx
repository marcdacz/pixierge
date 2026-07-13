import { useEffect, useState } from 'react';
import { FolderMinus, Images, Star } from 'lucide-react';
import {
  addAlbumItems,
  assetThumbnailUrl,
  createAlbum,
  fetchAlbumAssets,
  fetchAlbums,
  removeAlbumItems,
  updateAlbum,
  type AlbumSummary,
  type AssetBrowseResponse,
  type AuthResponse
} from '@/api';
import { Alert } from '@/components/ui/alert';
import { cn } from '@/lib/utils';
import { InlineEditableTitle } from '@/features/library/inline-editable-title';
import { mergeBrowseSections } from '@/features/library/photo-grid';
import { PhotoBrowser } from '@/features/library/photo-browser';
import {
  BrowseSidebarShowControl,
  BROWSE_LAYOUT_HEIGHT_CLASS,
  BROWSE_SIDEBAR_COLLAPSED_KEYS,
  OrganizerSidebar,
  useBrowseSidebarState
} from '@/features/organizer/organizer-sidebar';

const PAGE_SIZE = 48;

export function AlbumsHome({ auth }: { auth: AuthResponse }) {
  const [albums, setAlbums] = useState<AlbumSummary[]>([]);
  const [activeId, setActiveId] = useState<string | null>(null);
  const [assets, setAssets] = useState<AssetBrowseResponse | null>(null);
  const [page, setPage] = useState(0);
  const [loading, setLoading] = useState(true);
  const [loadingAssets, setLoadingAssets] = useState(false);
  const [loadingMore, setLoadingMore] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [assetFocused, setAssetFocused] = useState(false);
  const sidebar = useBrowseSidebarState(BROWSE_SIDEBAR_COLLAPSED_KEYS.albums);
  const active = albums.find((album) => album.id === activeId) ?? null;
  const browseContextKey = `album:${activeId ?? ''}`;
  const showControl =
    !assetFocused && sidebar.collapsed ? (
      <BrowseSidebarShowControl
        onDragLeave={sidebar.clearExpandTimer}
        onDragOver={sidebar.handleShowControlDragOver}
        onShow={() => sidebar.setCollapsed(false)}
        title="Albums"
      />
    ) : null;

  async function loadAlbums() {
    setLoading(true);
    try {
      const next = await fetchAlbums();
      setAlbums(next);
      setActiveId((current) => (current && next.some((album) => album.id === current) ? current : next[0]?.id ?? null));
      setError(null);
    } catch {
      setError('Albums could not be loaded.');
    } finally {
      setLoading(false);
    }
  }

  useEffect(() => {
    void loadAlbums();
  }, []);

  useEffect(() => {
    setPage(0);
    setAssets(null);
  }, [browseContextKey]);

  useEffect(() => {
    if (!activeId) {
      setAssets(null);
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

    fetchAlbumAssets(activeId, requestedPage, PAGE_SIZE)
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
        setError(null);
      })
      .catch(() => {
        if (!ignore && requestedContext === browseContextKey) {
          setError('Album assets could not be loaded.');
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
  }, [activeId, browseContextKey, page]);

  async function create(name: string) {
    const album = await createAlbum({ name }, auth.csrfToken);
    setAlbums((current) => [...current, album]);
    setActiveId(album.id);
  }

  async function rename(id: string, name: string) {
    const updated = await updateAlbum(id, { name }, auth.csrfToken);
    setAlbums((current) => current.map((album) => (album.id === updated.id ? updated : album)));
  }

  async function setKeyPhoto(assetId: string) {
    if (!active) {
      return;
    }
    const updated = await updateAlbum(active.id, { coverAssetId: assetId }, auth.csrfToken);
    setAlbums((current) => current.map((album) => (album.id === updated.id ? updated : album)));
  }

  async function dropOntoAlbum(
    albumId: string,
    _assetIds: string[],
    items: Array<{ assetId: string; sourceLibraryId: string }>
  ) {
    await addAlbumItems([albumId], items, auth.csrfToken);
    await loadAlbums();
    if (albumId === activeId) {
      setPage(0);
      setAssets(await fetchAlbumAssets(albumId, 0, PAGE_SIZE));
    }
  }

  async function reloadActiveAssets() {
    if (!activeId) {
      return;
    }
    setPage(0);
    setAssets(await fetchAlbumAssets(activeId, 0, PAGE_SIZE));
    await loadAlbums();
  }

  return (
    <div
      className={cn(
        'relative grid gap-0 overflow-hidden',
        BROWSE_LAYOUT_HEIGHT_CLASS,
        'grid-rows-[minmax(0,1fr)]',
        !assetFocused && !sidebar.isLowResolution && !sidebar.collapsed && 'lg:grid-cols-[auto_minmax(0,1fr)]'
      )}
    >
      {!assetFocused && (
        <OrganizerSidebar
          activeRowId={activeId}
          addLabel="Add album"
          collapsed={sidebar.collapsed}
          createPlaceholder="Album name"
          emptyLabel="No albums yet."
          isLowResolution={sidebar.isLowResolution}
          loading={loading}
          onCollapsedChange={sidebar.setCollapsed}
          onCreate={(name) => create(name)}
          onDropAssets={(rowId, assetIds, items) => void dropOntoAlbum(rowId, assetIds, items)}
          onRename={(id, name) => rename(id, name)}
          onSelect={setActiveId}
          rowIcon={Images}
          rows={albums.map((album) => ({
            id: album.id,
            label: album.name,
            count: album.itemCount,
            imageUrl: album.coverAssetId ? assetThumbnailUrl(album.coverAssetId, 'tiny') : null
          }))}
          title="Albums"
        />
      )}
      <div className="flex min-h-0 min-w-0 flex-col overflow-hidden">
        {error && !active && <Alert className="m-4">{error}</Alert>}
        {!active && (
          <div className="grid gap-2 p-4 lg:p-6">
            <div className="flex items-center gap-2">
              {showControl}
              <h1 className="text-2xl font-semibold">Albums</h1>
            </div>
            <p className="text-sm text-muted-foreground">
              Use + in the sidebar to create an album, or select photos in Libraries to add them here.
            </p>
          </div>
        )}
        {active && (
          <PhotoBrowser
            auth={auth}
            browseContextKey={browseContextKey}
            assets={assets}
            emptyDescription="Select photos in Libraries and add them to this album."
            emptyTitle="No assets in this album"
            error={error}
            extraContextActions={({ selectedIds, contextAsset, clearSelection }) => [
              ...(contextAsset
                ? [
                    {
                      id: 'set-key-photo',
                      icon: Star,
                      label: 'Set as key photo',
                      onSelect: () => {
                        void (async () => {
                          await setKeyPhoto(contextAsset.id);
                        })();
                      }
                    }
                  ]
                : []),
              {
                id: 'remove-album',
                icon: FolderMinus,
                label: 'Remove from album',
                onSelect: () => {
                  void (async () => {
                    await removeAlbumItems(active.id, [...selectedIds], auth.csrfToken);
                    clearSelection();
                    await reloadActiveAssets();
                  })();
                }
              }
            ]}
            key={browseContextKey}
            leadingControls={showControl ?? undefined}
            loadingAssets={loadingAssets}
            loadingMore={loadingMore}
            onAssignmentsChanged={() => void loadAlbums()}
            onFocusChange={setAssetFocused}
            onLoadMore={() => setPage((current) => current + 1)}
            showSectionHeaders={false}
            title={
              <InlineEditableTitle
                aria-label="Album name"
                onSave={async (name) => {
                  await rename(active.id, name);
                }}
                value={active.name}
              />
            }
          />
        )}
      </div>
    </div>
  );
}
