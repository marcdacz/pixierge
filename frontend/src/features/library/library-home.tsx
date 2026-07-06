import { AlertTriangle, FolderOpen, Images, SlidersHorizontal } from 'lucide-react';
import type { LibrarySummary } from '@/api';
import { Alert } from '@/components/ui/alert';
import { Badge } from '@/components/ui/badge';
import { Button } from '@/components/ui/button';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';

type LibraryHomeProps = {
  error?: string | null;
  libraries?: LibrarySummary[];
  loading?: boolean;
  onConfigureSources?: () => void;
  variant?: 'libraries' | 'albums';
};

export function LibraryHome({
  error = null,
  libraries = [],
  loading = false,
  onConfigureSources,
  variant = 'libraries'
}: LibraryHomeProps) {
  const isAlbums = variant === 'albums';
  const Icon = isAlbums ? Images : FolderOpen;
  const librariesWithSources = libraries.filter((library) => library.status === 'active' && library.sourceCount > 0);

  return (
    <div className="grid gap-10">
      <div className="grid gap-1">
        <h1 className="text-2xl font-semibold text-foreground">{isAlbums ? 'Albums' : 'Libraries'}</h1>
        <p className="text-sm text-muted-foreground">
          {isAlbums
            ? 'Albums will appear here once they are created.'
            : librariesWithSources.length > 0
              ? 'Browse configured libraries and source health.'
              : 'No library sources have been added yet.'}
        </p>
      </div>

      {!isAlbums && error && <Alert>{error}</Alert>}

      {!isAlbums && loading && <p className="text-sm text-muted-foreground">Loading libraries...</p>}

      {!isAlbums && librariesWithSources.length > 0 && (
        <section aria-label="Configured libraries" className="grid gap-4 md:grid-cols-2 xl:grid-cols-3">
          {librariesWithSources.map((library) => (
            <Card key={library.id}>
              <CardHeader>
                <div className="flex items-start justify-between gap-3">
                  <CardTitle>{library.name}</CardTitle>
                  <Badge variant={library.unavailableSourceCount > 0 ? 'warning' : 'secondary'}>
                    {formatSourceCount(library.sourceCount)}
                  </Badge>
                </div>
              </CardHeader>
              <CardContent>
                <div className="flex flex-wrap gap-2 text-sm text-muted-foreground">
                  <span>{library.availableSourceCount} available</span>
                  {library.unavailableSourceCount > 0 && (
                    <span className="inline-flex items-center gap-1 text-foreground">
                      <AlertTriangle className="h-4 w-4" aria-hidden />
                      {library.unavailableSourceCount} unavailable
                    </span>
                  )}
                </div>
              </CardContent>
            </Card>
          ))}
        </section>
      )}

      {(isAlbums || (!loading && librariesWithSources.length === 0)) && (
        <section className="grid min-h-96 place-items-center">
          <div className="grid max-w-md justify-items-center gap-3 text-center">
            <Icon className="h-8 w-8 text-muted-foreground" aria-hidden />
            <h2 className="text-xl font-semibold text-foreground">
              {isAlbums ? 'No albums yet' : 'Empty library'}
            </h2>
            <p className="text-sm text-muted-foreground">
              {isAlbums
                ? 'Album creation is not available yet.'
                : 'Configure named libraries and source folders to start building your catalog.'}
            </p>
            {!isAlbums && onConfigureSources && (
              <Button className="mt-2" onClick={onConfigureSources} type="button">
                <SlidersHorizontal className="h-4 w-4" aria-hidden />
                Configure sources
              </Button>
            )}
          </div>
        </section>
      )}
    </div>
  );
}

function formatSourceCount(count: number) {
  return `${count} ${count === 1 ? 'source' : 'sources'}`;
}
