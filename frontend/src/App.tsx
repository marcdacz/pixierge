import { useCallback, useEffect, useRef, useState } from 'react';
import { fetchLibraries, fetchSession, fetchSetupStatus, updateLibrary, type AuthResponse, type LibrarySummary } from '@/api';
import { AppFrame } from '@/components/app-frame';
import { ToastViewport, type ToastMessage } from '@/components/ui/toast';
import { LoginForm, SetupForm } from '@/features/identity/identity-forms';
import { LibraryHome } from '@/features/library/library-home';
import { AlbumsHome } from '@/features/albums/albums-home';
import { SearchHome } from '@/features/search/search-home';
import { StarredHome } from '@/features/starred/starred-home';
import { ScanActivityProvider } from '@/features/scans/scan-activity-context';
import { SettingsPage } from '@/features/settings/settings-page';
import { TagsHome } from '@/features/tags/tags-home';

export type AppView = 'search' | 'libraries' | 'starred' | 'albums' | 'tags' | 'settings';

type AppState =
  | { state: 'loading' }
  | { state: 'setup' }
  | { state: 'login'; notice?: string }
  | { state: 'app'; auth: AuthResponse };

const toastAutoDismissMs = 15_000;

function initialSearchQuery() {
  return new URLSearchParams(window.location.search).get('q') ?? '';
}

function syncSearchUrl(query: string) {
  const url = new URL(window.location.href);
  if (query) {
    url.searchParams.set('q', query);
  } else {
    url.searchParams.delete('q');
  }
  window.history.replaceState(window.history.state, '', url);
}

export function App() {
  const initialQuery = initialSearchQuery();
  const [appState, setAppState] = useState<AppState>({ state: 'loading' });
  const [currentView, setCurrentView] = useState<AppView>(initialQuery ? 'search' : 'libraries');
  const [libraries, setLibraries] = useState<LibrarySummary[]>([]);
  const [librariesLoading, setLibrariesLoading] = useState(false);
  const [librariesError, setLibrariesError] = useState<string | null>(null);
  const [retainedSearchQuery, setRetainedSearchQuery] = useState(initialQuery);
  const [searchInput, setSearchInput] = useState(initialQuery);
  const [activeSearchQuery, setActiveSearchQuery] = useState(initialQuery);
  const [toasts, setToasts] = useState<ToastMessage[]>([]);
  const toastTimeouts = useRef(new Map<string, ReturnType<typeof setTimeout>>());

  const dismissToast = useCallback((id: string) => {
    const timeout = toastTimeouts.current.get(id);
    if (timeout) {
      clearTimeout(timeout);
      toastTimeouts.current.delete(id);
    }
    setToasts((currentToasts) => currentToasts.filter((toast) => toast.id !== id));
  }, []);

  const showErrorToast = useCallback((title: string, description?: string) => {
    const id = globalThis.crypto?.randomUUID?.() ?? `${Date.now()}-${Math.random()}`;
    const timeout = setTimeout(() => {
      toastTimeouts.current.delete(id);
      setToasts((currentToasts) => currentToasts.filter((toast) => toast.id !== id));
    }, toastAutoDismissMs);

    toastTimeouts.current.set(id, timeout);
    setToasts((currentToasts) => [
      ...currentToasts,
      {
        id,
        title,
        description,
        variant: 'error'
      }
    ]);
  }, []);

  useEffect(() => {
    return () => {
      toastTimeouts.current.forEach((timeout) => clearTimeout(timeout));
      toastTimeouts.current.clear();
    };
  }, []);

  const loadLibraries = useCallback(async () => {
    setLibrariesLoading(true);
    setLibrariesError(null);

    try {
      setLibraries(await fetchLibraries());
    } catch (error) {
      setLibrariesError('Library sources could not be loaded.');
    } finally {
      setLibrariesLoading(false);
    }
  }, []);

  useEffect(() => {
    let ignore = false;

    async function load() {
      try {
        const setupStatus = await fetchSetupStatus();
        if (ignore) {
          return;
        }

        if (setupStatus.required) {
          setAppState({ state: 'setup' });
          return;
        }

        try {
          const auth = await fetchSession();
          if (!ignore) {
            setAppState({ state: 'app', auth });
          }
        } catch (error) {
          if (!ignore) {
            setAppState({ state: 'login' });
          }
        }
      } catch (error) {
        if (!ignore) {
          setAppState({ state: 'login' });
        }
      }
    }

    void load();

    return () => {
      ignore = true;
    };
  }, []);

  useEffect(() => {
    if (appState.state === 'app') {
      void loadLibraries();
    } else {
      setLibraries([]);
    }
  }, [appState.state, loadLibraries]);

  const handleSearchInputChange = useCallback((value: string) => {
    setSearchInput(value);
    if (value.trim() && currentView !== 'search') {
      setCurrentView('search');
    }
  }, [currentView]);

  const handleValidSearchQuery = useCallback((value: string) => {
    setActiveSearchQuery(value);
    setRetainedSearchQuery(value);
    syncSearchUrl(value);
    if (value.trim()) {
      setCurrentView('search');
    }
  }, []);

  const handleSearchPageQueryChange = useCallback((value: string) => {
    setSearchInput(value);
    setActiveSearchQuery(value);
    setRetainedSearchQuery(value);
    syncSearchUrl(value);
    setCurrentView('search');
  }, []);

  const handleViewChange = useCallback((view: AppView) => {
    if (view === currentView) {
      return;
    }
    if (currentView === 'search' && view !== 'search') {
      setSearchInput('');
      setActiveSearchQuery('');
      syncSearchUrl('');
    }
    if (view === 'search') {
      setSearchInput(retainedSearchQuery);
      setActiveSearchQuery(retainedSearchQuery);
      syncSearchUrl(retainedSearchQuery);
    }
    setCurrentView(view);
  }, [currentView, retainedSearchQuery]);

  if (appState.state === 'loading') {
    return <AppLoading />;
  }

  if (appState.state === 'setup') {
    return (
      <SetupForm
        onSetup={(auth) => {
          setCurrentView('libraries');
          setAppState({ state: 'app', auth });
        }}
      />
    );
  }

  if (appState.state === 'login') {
    return (
      <LoginForm
        notice={appState.notice}
        onLogin={(auth) => {
          setCurrentView('libraries');
          setAppState({ state: 'app', auth });
        }}
      />
    );
  }

  return (
    <>
      <ScanActivityProvider>
        <AppFrame
          auth={appState.auth}
          contentMode={currentView === 'settings' || currentView === 'search' || currentView === 'libraries' || currentView === 'starred' || currentView === 'albums' || currentView === 'tags' ? 'edge' : 'constrained'}
          currentView={currentView}
          libraries={libraries}
          onLibrarySearchChange={handleSearchInputChange}
          onLibrarySearchQueryChange={handleValidSearchQuery}
          onLogout={() => setAppState({ state: 'login' })}
          onOpenSettings={() => handleViewChange('settings')}
          searchPlaceholder="Search"
          searchValue={searchInput}
          showLibrarySearch
          onViewChange={handleViewChange}
        >
          {currentView === 'search' && (
            <SearchHome
              auth={appState.auth}
              libraries={libraries}
              onQueryChange={handleSearchPageQueryChange}
              query={activeSearchQuery}
            />
          )}
          {currentView === 'libraries' && (
            <LibraryHome
              error={librariesError}
              auth={appState.auth}
              libraries={libraries}
              loading={librariesLoading}
              onConfigureSources={() => setCurrentView('settings')}
              onError={showErrorToast}
              onRenameLibrary={async (libraryId, name) => {
                const updated = await updateLibrary(libraryId, { name }, appState.auth.csrfToken);
                setLibraries((current) => current.map((library) => (library.id === updated.id ? updated : library)));
              }}
            />
          )}
          {currentView === 'starred' && <StarredHome auth={appState.auth} />}
          {currentView === 'albums' && <AlbumsHome auth={appState.auth} />}
          {currentView === 'tags' && <TagsHome auth={appState.auth} />}
          {currentView === 'settings' && (
            <SettingsPage
              auth={appState.auth}
              error={librariesError}
              libraries={libraries}
              loading={librariesLoading}
              onError={showErrorToast}
              onLibrariesChange={loadLibraries}
            />
          )}
        </AppFrame>
      </ScanActivityProvider>
      <ToastViewport onDismiss={dismissToast} toasts={toasts} />
    </>
  );
}

function AppLoading() {
  return (
    <main className="grid min-h-screen place-items-center bg-background p-6 text-foreground">
      <div className="grid gap-2 text-center">
        <p className="text-xs font-medium uppercase text-muted-foreground">pixierge</p>
        <h1 className="text-2xl font-semibold">Preparing workspace</h1>
      </div>
    </main>
  );
}
