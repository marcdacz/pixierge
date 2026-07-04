import { useEffect, useState } from 'react';
import { fetchSession, fetchSetupStatus, type AuthResponse } from '@/api';
import { AppFrame } from '@/components/app-frame';
import { LoginForm, SetupForm } from '@/features/identity/identity-forms';
import { LibraryHome } from '@/features/library/library-home';
import { SettingsPage } from '@/features/settings/settings-page';

export type AppView = 'libraries' | 'albums' | 'settings';

type AppState =
  | { state: 'loading' }
  | { state: 'setup' }
  | { state: 'login'; notice?: string }
  | { state: 'app'; auth: AuthResponse };

export function App() {
  const [appState, setAppState] = useState<AppState>({ state: 'loading' });
  const [currentView, setCurrentView] = useState<AppView>('libraries');

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
    <AppFrame
      auth={appState.auth}
      contentMode={currentView === 'settings' ? 'edge' : 'constrained'}
      currentView={currentView}
      onLogout={() => setAppState({ state: 'login' })}
      onViewChange={setCurrentView}
    >
      {currentView === 'libraries' && <LibraryHome />}
      {currentView === 'albums' && <LibraryHome variant="albums" />}
      {currentView === 'settings' && <SettingsPage />}
    </AppFrame>
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
