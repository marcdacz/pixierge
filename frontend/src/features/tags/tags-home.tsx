import { useEffect, useState } from 'react';
import { Hash } from 'lucide-react';
import {
  addAssetTags,
  createTag,
  fetchTagAssets,
  fetchTags,
  updateTag,
  type AssetBrowseResponse,
  type AuthResponse,
  type TagSummary
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

export function TagsHome({ auth }: { auth: AuthResponse }) {
  const [tags, setTags] = useState<TagSummary[]>([]);
  const [activeId, setActiveId] = useState<string | null>(null);
  const [assets, setAssets] = useState<AssetBrowseResponse | null>(null);
  const [page, setPage] = useState(0);
  const [loading, setLoading] = useState(true);
  const [loadingAssets, setLoadingAssets] = useState(false);
  const [loadingMore, setLoadingMore] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [assetFocused, setAssetFocused] = useState(false);
  const { collapsed, setCollapsed, isLowResolution, clearExpandTimer, handleShowControlDragOver } =
    useBrowseSidebarState(BROWSE_SIDEBAR_COLLAPSED_KEYS.tags);
  const active = tags.find((tag) => tag.id === activeId) ?? null;
  const browseContextKey = `tag:${activeId ?? ''}`;
  const showControl =
    !assetFocused && collapsed ? (
      <BrowseSidebarShowControl
        onDragLeave={clearExpandTimer}
        onDragOver={handleShowControlDragOver}
        onShow={() => setCollapsed(false)}
        title="Tags"
      />
    ) : null;

  async function loadTags() {
    setLoading(true);
    try {
      const next = await fetchTags();
      setTags(next);
      setActiveId((current) => (current && next.some((tag) => tag.id === current) ? current : next[0]?.id ?? null));
      setError(null);
    } catch {
      setError('Tags could not be loaded.');
    } finally {
      setLoading(false);
    }
  }

  useEffect(() => {
    void loadTags();
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

    fetchTagAssets(activeId, requestedPage, PAGE_SIZE)
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
          setError('Tag assets could not be loaded.');
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
    const tag = await createTag({ name }, auth.csrfToken);
    setTags((current) => [...current, tag]);
    setActiveId(tag.id);
  }

  async function rename(id: string, name: string) {
    const updated = await updateTag(id, { name }, auth.csrfToken);
    setTags((current) => current.map((tag) => (tag.id === updated.id ? updated : tag)));
  }

  async function dropOntoTag(
    tagId: string,
    _assetIds: string[],
    items: Array<{ assetId: string; sourceLibraryId: string }>
  ) {
    await addAssetTags([tagId], items, auth.csrfToken);
    await loadTags();
    if (tagId === activeId) {
      setPage(0);
      setAssets(await fetchTagAssets(tagId, 0, PAGE_SIZE));
    }
  }

  return (
    <div
      className={cn(
        'relative grid min-h-0 overflow-hidden',
        BROWSE_LAYOUT_HEIGHT_CLASS,
        'grid-rows-[minmax(0,1fr)]',
        !assetFocused && !isLowResolution && !collapsed && 'lg:grid-cols-[auto_minmax(0,1fr)]'
      )}
    >
      {!assetFocused && (
        <OrganizerSidebar
          activeRowId={activeId}
          addLabel="Add tag"
          collapsed={collapsed}
          createPlaceholder="Tag name"
          emptyLabel="No tags yet."
          isLowResolution={isLowResolution}
          loading={loading}
          onCollapsedChange={setCollapsed}
          onCreate={(name) => create(name)}
          onDropAssets={(rowId, assetIds, items) => void dropOntoTag(rowId, assetIds, items)}
          onRename={(id, name) => rename(id, name)}
          onSelect={setActiveId}
          rowIcon={Hash}
          rows={tags.map((tag) => ({ id: tag.id, label: tag.name, count: tag.assetCount }))}
          title="Tags"
        />
      )}
      <div className="flex min-h-0 min-w-0 flex-col overflow-hidden">
        {error && !active && <Alert className="m-4">{error}</Alert>}
        {!active && (
          <div className="grid gap-2 p-4 lg:p-6">
            <div className="flex items-center gap-2">
              {showControl}
              <h1 className="text-2xl font-semibold">Tags</h1>
            </div>
            <p className="text-sm text-muted-foreground">
              Use + in the sidebar to create a tag, or select photos in Libraries to assign tags.
            </p>
          </div>
        )}
        {active && (
          <PhotoBrowser
            auth={auth}
            browseContextKey={browseContextKey}
            assets={assets}
            emptyDescription="Select photos in Libraries and assign this tag."
            emptyTitle="No assets with this tag"
            error={error}
            key={browseContextKey}
            leadingControls={showControl ?? undefined}
            loadingAssets={loadingAssets}
            loadingMore={loadingMore}
            onAssignmentsChanged={() => void loadTags()}
            onFocusChange={setAssetFocused}
            onLoadMore={() => setPage((current) => current + 1)}
            showSectionHeaders={false}
            title={
              <InlineEditableTitle
                aria-label="Tag name"
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
