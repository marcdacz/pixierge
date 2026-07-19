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
import { SettingsPage, type SettingsView } from '@/features/settings/settings-page';
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
  url.pathname = '/';
  if (query) {
    url.searchParams.set('q', query);
  } else {
    url.searchParams.delete('q');
  }
  window.history.replaceState(window.history.state, '', url);
}

const settingsViews: SettingsView[] = ['configuration', 'scheduler', 'background', 'plugins', 'backups'];

function settingsViewFromPath(pathname: string): SettingsView {
  const section = pathname.split('/')[2];
  return settingsViews.includes(section as SettingsView) ? section as SettingsView : 'configuration';
}

function appViewFromLocation() {
  if (window.location.pathname.startsWith('/settings')) {
    return 'settings' as AppView;
  }
  return initialSearchQuery() ? 'search' as AppView : 'libraries' as AppView;
}

function pushAppPath(view: AppView, settingsView: SettingsView = 'configuration') {
  const url = new URL(window.location.href);
  url.search = '';
  url.pathname = view === 'settings' ? `/settings/${settingsView}` : '/';
  window.history.pushState(window.history.state, '', url);
}

export function App() {
  const initialQuery = initialSearchQuery();
  const [appState, setAppState] = useState<AppState>({ state: 'loading' });
  const [currentView, setCurrentView] = useState<AppView>(appViewFromLocation());
  const [currentSettingsView, setCurrentSettingsView] = useState<SettingsView>(settingsViewFromPath(window.location.pathname));
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

  useEffect(() => {
    function handlePopState() {
      const nextView = appViewFromLocation();
      const query = initialSearchQuery();
      setCurrentView(nextView);
      setCurrentSettingsView(settingsViewFromPath(window.location.pathname));
      if (nextView === 'search') {
        setSearchInput(query);
        setActiveSearchQuery(query);
        setRetainedSearchQuery(query);
      } else {
        setSearchInput('');
        setActiveSearchQuery('');
      }
    }

    window.addEventListener('popstate', handlePopState);
    return () => {
      window.removeEventListener('popstate', handlePopState);
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
      pushAppPath('search');
      setCurrentView('search');
    }
  }, [currentView]);

  const handleValidSearchQuery = useCallback((value: string) => {
    if (!value.trim() && currentView !== 'search') {
      return;
    }
    setActiveSearchQuery(value);
    setRetainedSearchQuery(value);
    syncSearchUrl(value);
    if (value.trim()) {
      setCurrentView('search');
    }
  }, [currentView]);

  const handleSearchPageQueryChange = useCallback((value: string) => {
    setSearchInput(value);
    setActiveSearchQuery(value);
    setRetainedSearchQuery(value);
    syncSearchUrl(value);
    setCurrentView('search');
  }, []);

  const handleViewChange = useCallback((view: AppView) => {
    if (view === currentView) {
      if (view === 'settings') {
        pushAppPath('settings', currentSettingsView);
      }
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
    } else {
      pushAppPath(view, currentSettingsView);
    }
    setCurrentView(view);
  }, [currentSettingsView, currentView, retainedSearchQuery]);

  const handleSettingsViewChange = useCallback((view: SettingsView) => {
    setSearchInput('');
    setActiveSearchQuery('');
    setCurrentSettingsView(view);
    setCurrentView('settings');
    pushAppPath('settings', view);
  }, []);

  if (appState.state === 'loading') {
    return <AppLoading />;
  }

  if (appState.state === 'setup') {
    return (
      <SetupForm
        onSetup={(auth) => {
          setCurrentView('libraries');
          pushAppPath('libraries');
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
          pushAppPath('libraries');
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
          onOpenSettings={() => handleSettingsViewChange(currentSettingsView)}
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
              onConfigureSources={() => handleSettingsViewChange('configuration')}
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
              currentView={currentSettingsView}
              error={librariesError}
              libraries={libraries}
              loading={librariesLoading}
              onError={showErrorToast}
              onLibrariesChange={loadLibraries}
              onViewChange={handleSettingsViewChange}
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
