import { useCallback, useEffect, useRef, useState } from 'react';
import { fetchLibraries, fetchSession, fetchSetupStatus, type AuthResponse, type LibrarySummary } from '@/api';
import { AppFrame } from '@/components/app-frame';
import { ToastViewport, type ToastMessage } from '@/components/ui/toast';
import { LoginForm, SetupForm } from '@/features/identity/identity-forms';
import { LibraryHome } from '@/features/library/library-home';
import { SettingsPage } from '@/features/settings/settings-page';

export type AppView = 'libraries' | 'albums' | 'settings';

type AppState =
  | { state: 'loading' }
  | { state: 'setup' }
  | { state: 'login'; notice?: string }
  | { state: 'app'; auth: AuthResponse };

const toastAutoDismissMs = 15_000;
const librarySearchDebounceMs = 250;

export function App() {
  const [appState, setAppState] = useState<AppState>({ state: 'loading' });
  const [currentView, setCurrentView] = useState<AppView>('libraries');
  const [libraries, setLibraries] = useState<LibrarySummary[]>([]);
  const [librariesLoading, setLibrariesLoading] = useState(false);
  const [librariesError, setLibrariesError] = useState<string | null>(null);
  const [librarySearchInput, setLibrarySearchInput] = useState('');
  const [librarySearchQuery, setLibrarySearchQuery] = useState('');
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

  useEffect(() => {
    const handle = window.setTimeout(() => {
      setLibrarySearchQuery(librarySearchInput.trim());
    }, librarySearchDebounceMs);

    return () => {
      window.clearTimeout(handle);
    };
  }, [librarySearchInput]);

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
      <AppFrame
        auth={appState.auth}
        contentMode={currentView === 'settings' || currentView === 'libraries' ? 'edge' : 'constrained'}
        currentView={currentView}
        libraries={libraries}
        onLibrarySearchChange={setLibrarySearchInput}
        onLogout={() => setAppState({ state: 'login' })}
        searchPlaceholder="Search this folder"
        searchValue={librarySearchInput}
        showLibrarySearch={currentView === 'libraries'}
        onViewChange={setCurrentView}
      >
        {currentView === 'libraries' && (
          <LibraryHome
            error={librariesError}
            libraries={libraries}
            loading={librariesLoading}
            onConfigureSources={() => setCurrentView('settings')}
            searchQuery={librarySearchQuery}
          />
        )}
        {currentView === 'albums' && <LibraryHome variant="albums" />}
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
