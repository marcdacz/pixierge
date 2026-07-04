import { FolderOpen, Images } from 'lucide-react';

export function LibraryHome({ variant = 'libraries' }: { variant?: 'libraries' | 'albums' }) {
  const isAlbums = variant === 'albums';
  const Icon = isAlbums ? Images : FolderOpen;

  return (
    <div className="grid gap-10">
      <div className="grid gap-1">
        <h1 className="text-2xl font-semibold text-foreground">{isAlbums ? 'Albums' : 'Libraries'}</h1>
        <p className="text-sm text-muted-foreground">
          {isAlbums ? 'Albums will appear here once they are created.' : 'No libraries have been added yet.'}
        </p>
      </div>

      <section className="grid min-h-96 place-items-center">
        <div className="grid max-w-md justify-items-center gap-3 text-center">
          <Icon className="h-8 w-8 text-muted-foreground" aria-hidden />
          <h2 className="text-xl font-semibold text-foreground">
            {isAlbums ? 'No albums yet' : 'Empty library'}
          </h2>
          <p className="text-sm text-muted-foreground">
            {isAlbums
              ? 'Album creation will be added in a later slice.'
              : 'Go to settings when you are ready to configure Pixierge.'}
          </p>
        </div>
      </section>
    </div>
  );
}
