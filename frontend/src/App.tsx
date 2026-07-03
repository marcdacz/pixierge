import { useEffect, useState } from 'react';
import { fetchHealth, type HealthResponse } from './api';

type HealthState =
  | { state: 'checking' }
  | { state: 'ready'; health: HealthResponse }
  | { state: 'unavailable'; health?: HealthResponse };

function databaseLabel(healthState: HealthState): string {
  if (healthState.state === 'checking') {
    return 'Checking';
  }

  if (healthState.state === 'ready') {
    return 'Ready';
  }

  return 'Unavailable';
}

export function App() {
  const [healthState, setHealthState] = useState<HealthState>({ state: 'checking' });

  useEffect(() => {
    let ignore = false;

    fetchHealth()
      .then((health) => {
        if (ignore) {
          return;
        }

        setHealthState(health.status === 'ok' ? { state: 'ready', health } : { state: 'unavailable', health });
      })
      .catch(() => {
        if (!ignore) {
          setHealthState({ state: 'unavailable' });
        }
      });

    return () => {
      ignore = true;
    };
  }, []);

  const apiStatus = healthState.state === 'ready' ? 'Connected' : healthState.state === 'checking' ? 'Checking' : 'Unavailable';

  return (
    <main className="app-shell">
      <section className="status-panel" aria-labelledby="app-title">
        <p className="eyebrow">Photo management</p>
        <h1 id="app-title">Pixierge</h1>
        <dl className="status-list">
          <div>
            <dt>API</dt>
            <dd>{apiStatus}</dd>
          </div>
          <div>
            <dt>Database</dt>
            <dd>{databaseLabel(healthState)}</dd>
          </div>
        </dl>
      </section>
    </main>
  );
}
