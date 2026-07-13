import { useEffect, useState } from 'react';
import {
  fetchAssets,
  type AssetBrowseResponse,
  type AuthResponse,
  type LibrarySummary
} from '@/api';
import { Alert } from '@/components/ui/alert';
import {
  BrowseSidebar,
  BrowseSidebarShowControl,
  BROWSE_LAYOUT_HEIGHT_CLASS,
  BROWSE_SIDEBAR_COLLAPSED_KEYS,
  useBrowseSidebarState
} from '@/features/browse/browse-sidebar';
import { mergeBrowseSections } from '@/features/library/photo-grid';
import { PhotoBrowser } from '@/features/library/photo-browser';
import { SearchFilters } from '@/features/search/search-filters';
import { cn } from '@/lib/utils';

const PAGE_SIZE = 48;

type SearchHomeProps = {
  auth: AuthResponse;
  libraries: LibrarySummary[];
  query: string;
  onQueryChange: (query: string) => void;
};

export function SearchHome({ auth, libraries, query, onQueryChange }: SearchHomeProps) {
  const {
    collapsed: filtersCollapsed,
    setCollapsed: setFiltersCollapsed,
    isLowResolution
  } = useBrowseSidebarState(BROWSE_SIDEBAR_COLLAPSED_KEYS.search);
  const [page, setPage] = useState(0);
  const [assets, setAssets] = useState<AssetBrowseResponse | null>(null);
  const [loadingAssets, setLoadingAssets] = useState(false);
  const [loadingMore, setLoadingMore] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [assetFocused, setAssetFocused] = useState(false);
  const trimmedQuery = query.trim();
  const browseContextKey = `search:${trimmedQuery}`;
  const showControl =
    !assetFocused && filtersCollapsed ? (
      <BrowseSidebarShowControl onShow={() => setFiltersCollapsed(false)} title="Filters" />
    ) : null;

  useEffect(() => {
    setPage(0);
    setAssets(null);
  }, [browseContextKey]);

  useEffect(() => {
    if (!trimmedQuery) {
      setAssets(null);
      setLoadingAssets(false);
      setLoadingMore(false);
      setError(null);
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
    setError(null);

    fetchAssets({
      q: trimmedQuery,
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
          setError('Search results could not be loaded.');
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
  }, [browseContextKey, page, trimmedQuery]);

  return (
    <div
      className={cn(
        'relative grid gap-0 overflow-hidden',
        BROWSE_LAYOUT_HEIGHT_CLASS,
        'grid-rows-[minmax(0,1fr)]',
        !assetFocused && !isLowResolution && !filtersCollapsed && 'lg:grid-cols-[auto_minmax(0,1fr)]'
      )}
    >
      {!filtersCollapsed && (
        // Keep filters mounted while the photo viewer is open so closing does not
        // remount SearchFilters (empty state + album/tag refetch flash).
        <div className={assetFocused ? 'hidden' : 'contents'}>
          <BrowseSidebar
            isLowResolution={isLowResolution}
            onCloseOverlay={() => setFiltersCollapsed(true)}
            onHide={() => setFiltersCollapsed(true)}
            title="Filters"
          >
            <nav aria-label="Filters" className="flex min-h-0 flex-1 flex-col">
              <SearchFilters libraries={libraries} onQueryChange={onQueryChange} query={query} />
            </nav>
          </BrowseSidebar>
        </div>
      )}

      <div className="flex min-h-0 min-w-0 flex-col overflow-hidden">
        {error && !trimmedQuery && <Alert className="m-4">{error}</Alert>}
        {!trimmedQuery ? (
          <div className="grid gap-2 p-4 lg:p-6">
            <div className="flex items-center gap-2">
              {showControl}
              <h1 className="text-2xl font-semibold">Search</h1>
            </div>
            <p className="text-sm text-muted-foreground">
              Use the search bar or filters to find photos across every library.
            </p>
          </div>
        ) : (
          <PhotoBrowser
            key={browseContextKey}
            auth={auth}
            assets={assets}
            browseContextKey={browseContextKey}
            emptyDescription="Try a different query or adjust the filters."
            emptyTitle="No matching assets"
            error={error}
            leadingControls={showControl ?? undefined}
            loadingAssets={loadingAssets}
            loadingMore={loadingMore}
            onFocusChange={setAssetFocused}
            onLoadMore={() => setPage((current) => current + 1)}
            showSectionHeaders={false}
            title={<h2 className="truncate text-2xl font-semibold text-foreground">Search results</h2>}
          />
        )}
      </div>
    </div>
  );
}
