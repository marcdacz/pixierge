import { useEffect, useState } from 'react';
import {
  fetchFavourites,
  fetchFavouritesAssets,
  type AlbumSummary,
  type AssetBrowseResponse,
  type AuthResponse
} from '@/api';
import { Alert } from '@/components/ui/alert';
import { cn } from '@/lib/utils';
import { mergeBrowseSections } from '@/features/library/photo-grid';
import { PhotoBrowser } from '@/features/library/photo-browser';
import { BROWSE_LAYOUT_HEIGHT_CLASS } from '@/features/organizer/organizer-sidebar';

const PAGE_SIZE = 48;

export function FavouritesHome({ auth }: { auth: AuthResponse }) {
  const [favourites, setFavourites] = useState<AlbumSummary | null>(null);
  const [assets, setAssets] = useState<AssetBrowseResponse | null>(null);
  const [page, setPage] = useState(0);
  const [loadingAssets, setLoadingAssets] = useState(true);
  const [loadingMore, setLoadingMore] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const browseContextKey = `favourites:${favourites?.id ?? ''}`;

  useEffect(() => {
    let ignore = false;
    fetchFavourites()
      .then((next) => {
        if (!ignore) {
          setFavourites(next);
          setError(null);
        }
      })
      .catch(() => {
        if (!ignore) {
          setError('Favourites could not be loaded.');
          setLoadingAssets(false);
        }
      });
    return () => {
      ignore = true;
    };
  }, []);

  useEffect(() => {
    setPage(0);
    setAssets(null);
  }, [browseContextKey]);

  useEffect(() => {
    if (!favourites) {
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

    fetchFavouritesAssets(requestedPage, PAGE_SIZE)
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
          setError('Favourites assets could not be loaded.');
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
  }, [favourites, browseContextKey, page]);

  async function reloadAssets() {
    if (!favourites) {
      return;
    }
    setPage(0);
    setAssets(await fetchFavouritesAssets(0, PAGE_SIZE));
    setFavourites(await fetchFavourites());
  }

  return (
    <div className={cn('relative grid gap-0 overflow-hidden', BROWSE_LAYOUT_HEIGHT_CLASS, 'grid-rows-[minmax(0,1fr)]')}>
      {error && !favourites && <Alert className="m-4">{error}</Alert>}
      {favourites && (
        <PhotoBrowser
          auth={auth}
          browseContextKey={browseContextKey}
          assets={assets}
          emptyDescription="Right-click photos in Libraries, Albums, or Tags to add them to Favourites."
          emptyTitle="No favourites yet"
          error={error}
          key={browseContextKey}
          loadingAssets={loadingAssets}
          loadingMore={loadingMore}
          onAssignmentsChanged={() => void reloadAssets()}
          onLoadMore={() => setPage((current) => current + 1)}
          showSectionHeaders={false}
          title={<h1 className="text-2xl font-semibold">Favourites</h1>}
        />
      )}
    </div>
  );
}
