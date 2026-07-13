import { useEffect, useState } from 'react';
import {
  fetchStarred,
  fetchStarredAssets,
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

export function StarredHome({ auth }: { auth: AuthResponse }) {
  const [starred, setStarred] = useState<AlbumSummary | null>(null);
  const [assets, setAssets] = useState<AssetBrowseResponse | null>(null);
  const [page, setPage] = useState(0);
  const [loadingAssets, setLoadingAssets] = useState(true);
  const [loadingMore, setLoadingMore] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const browseContextKey = `starred:${starred?.id ?? ''}`;

  useEffect(() => {
    let ignore = false;
    fetchStarred()
      .then((next) => {
        if (!ignore) {
          setStarred(next);
          setError(null);
        }
      })
      .catch(() => {
        if (!ignore) {
          setError('Starred could not be loaded.');
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
    if (!starred) {
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

    fetchStarredAssets(requestedPage, PAGE_SIZE)
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
          setError('Starred assets could not be loaded.');
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
  }, [starred, browseContextKey, page]);

  async function reloadAssets() {
    if (!starred) {
      return;
    }
    setPage(0);
    setAssets(await fetchStarredAssets(0, PAGE_SIZE));
    setStarred(await fetchStarred());
  }

  return (
    <div className={cn('relative grid gap-0 overflow-hidden', BROWSE_LAYOUT_HEIGHT_CLASS, 'grid-rows-[minmax(0,1fr)]')}>
      {error && !starred && <Alert className="m-4">{error}</Alert>}
      {starred && (
        <PhotoBrowser
          auth={auth}
          browseContextKey={browseContextKey}
          assets={assets}
          emptyDescription="Right-click photos in Libraries, Albums, or Tags to add them to Starred."
          emptyTitle="No starred photos yet"
          error={error}
          key={browseContextKey}
          loadingAssets={loadingAssets}
          loadingMore={loadingMore}
          onAssignmentsChanged={() => void reloadAssets()}
          onLoadMore={() => setPage((current) => current + 1)}
          showSectionHeaders={false}
          title={<h1 className="text-2xl font-semibold">Starred</h1>}
        />
      )}
    </div>
  );
}
