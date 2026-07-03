import { FormEvent, useEffect, useState } from 'react';
import {
  ApiError,
  createFirstAdmin,
  fetchHealth,
  fetchRoles,
  fetchSession,
  fetchSetupStatus,
  fetchUsers,
  login,
  logout,
  type AuthResponse,
  type HealthResponse,
  type RoleSummary,
  type UserSummary
} from './api';

type AppState =
  | { state: 'loading'; health?: HealthResponse }
  | { state: 'setup'; health?: HealthResponse }
  | { state: 'login'; health?: HealthResponse }
  | { state: 'admin'; auth: AuthResponse; health?: HealthResponse };

type AdminDataState =
  | { state: 'loading' }
  | { state: 'ready'; users: UserSummary[]; roles: RoleSummary[] }
  | { state: 'error' };

function healthLabel(health?: HealthResponse): string {
  if (!health) {
    return 'Checking';
  }

  return health.status === 'ok' ? 'Connected' : 'Degraded';
}

function asErrorMessage(error: unknown): string {
  if (error instanceof ApiError && error.status === 401) {
    return 'Email or password was not accepted.';
  }

  if (error instanceof ApiError && error.status === 409) {
    return 'Setup has already been completed.';
  }

  return 'Something went wrong. Please try again.';
}

export function App() {
  const [appState, setAppState] = useState<AppState>({ state: 'loading' });

  useEffect(() => {
    let ignore = false;

    async function load() {
      const health = await fetchHealth().catch(() => undefined);

      try {
        const setupStatus = await fetchSetupStatus();
        if (ignore) {
          return;
        }

        if (setupStatus.required) {
          setAppState({ state: 'setup', health });
          return;
        }

        try {
          const auth = await fetchSession();
          if (!ignore) {
            setAppState({ state: 'admin', auth, health });
          }
        } catch (error) {
          if (!ignore) {
            setAppState({ state: 'login', health });
          }
        }
      } catch (error) {
        if (!ignore) {
          setAppState({ state: 'login', health });
        }
      }
    }

    void load();

    return () => {
      ignore = true;
    };
  }, []);

  return (
    <main className="app-shell">
      <section className="identity-surface" aria-labelledby="app-title">
        <header className="app-header">
          <div>
            <p className="eyebrow">Photo management</p>
            <h1 id="app-title">Pixierge</h1>
          </div>
          <dl className="connection-status" aria-label="System status">
            <div>
              <dt>API</dt>
              <dd>{healthLabel(appState.health)}</dd>
            </div>
          </dl>
        </header>

        {appState.state === 'loading' && <p className="muted">Checking Pixierge status...</p>}

        {appState.state === 'setup' && (
          <SetupForm
            onSetup={(auth) => setAppState({ state: 'admin', auth, health: appState.health })}
          />
        )}

        {appState.state === 'login' && (
          <LoginForm
            onLogin={(auth) => setAppState({ state: 'admin', auth, health: appState.health })}
          />
        )}

        {appState.state === 'admin' && (
          <AdminShell
            auth={appState.auth}
            onLogout={() => setAppState({ state: 'login', health: appState.health })}
          />
        )}
      </section>
    </main>
  );
}

function SetupForm({ onSetup }: { onSetup: (auth: AuthResponse) => void }) {
  const [email, setEmail] = useState('');
  const [displayName, setDisplayName] = useState('');
  const [password, setPassword] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setError(null);
    setSubmitting(true);

    try {
      onSetup(await createFirstAdmin({ email, displayName, password }));
    } catch (error) {
      setError(asErrorMessage(error));
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <form className="auth-form" onSubmit={submit}>
      <div>
        <h2>Create admin account</h2>
        <p className="muted">Set up the first local administrator.</p>
      </div>
      <label>
        Email
        <input autoComplete="email" name="email" type="email" value={email} onChange={(event) => setEmail(event.target.value)} required />
      </label>
      <label>
        Display name
        <input autoComplete="name" name="displayName" value={displayName} onChange={(event) => setDisplayName(event.target.value)} required />
      </label>
      <label>
        Password
        <input autoComplete="new-password" name="password" type="password" minLength={12} value={password} onChange={(event) => setPassword(event.target.value)} required />
      </label>
      {error && <p className="form-error">{error}</p>}
      <button type="submit" disabled={submitting}>{submitting ? 'Creating...' : 'Create admin'}</button>
    </form>
  );
}

function LoginForm({ onLogin }: { onLogin: (auth: AuthResponse) => void }) {
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setError(null);
    setSubmitting(true);

    try {
      onLogin(await login({ email, password }));
    } catch (error) {
      setError(asErrorMessage(error));
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <form className="auth-form" onSubmit={submit}>
      <div>
        <h2>Admin sign in</h2>
        <p className="muted">Use your Pixierge admin account.</p>
      </div>
      <label>
        Email
        <input autoComplete="email" name="email" type="email" value={email} onChange={(event) => setEmail(event.target.value)} required />
      </label>
      <label>
        Password
        <input autoComplete="current-password" name="password" type="password" value={password} onChange={(event) => setPassword(event.target.value)} required />
      </label>
      {error && <p className="form-error">{error}</p>}
      <button type="submit" disabled={submitting}>{submitting ? 'Signing in...' : 'Sign in'}</button>
    </form>
  );
}

function AdminShell({ auth, onLogout }: { auth: AuthResponse; onLogout: () => void }) {
  const [adminData, setAdminData] = useState<AdminDataState>({ state: 'loading' });

  useEffect(() => {
    let ignore = false;

    async function loadAdminData() {
      try {
        const [users, roles] = await Promise.all([fetchUsers(), fetchRoles()]);
        if (!ignore) {
          setAdminData({ state: 'ready', users, roles });
        }
      } catch (error) {
        if (!ignore) {
          setAdminData({ state: 'error' });
        }
      }
    }

    void loadAdminData();

    return () => {
      ignore = true;
    };
  }, []);

  async function submitLogout() {
    await logout(auth.csrfToken);
    onLogout();
  }

  return (
    <div className="admin-shell">
      <div className="admin-topbar">
        <div>
          <h2>Admin</h2>
          <p className="muted">Signed in as {auth.user.displayName}</p>
        </div>
        <button className="secondary-button" type="button" onClick={submitLogout}>Sign out</button>
      </div>

      {adminData.state === 'loading' && <p className="muted">Loading identity data...</p>}
      {adminData.state === 'error' && <p className="form-error">Identity data could not be loaded.</p>}
      {adminData.state === 'ready' && (
        <div className="admin-grid">
          <section aria-labelledby="users-heading">
            <h3 id="users-heading">Users</h3>
            <div className="table-wrap">
              <table>
                <thead>
                  <tr>
                    <th>Name</th>
                    <th>Email</th>
                    <th>Roles</th>
                  </tr>
                </thead>
                <tbody>
                  {adminData.users.map((user) => (
                    <tr key={user.id}>
                      <td>{user.displayName}</td>
                      <td>{user.email}</td>
                      <td>{user.roles.join(', ')}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          </section>

          <section aria-labelledby="roles-heading">
            <h3 id="roles-heading">Roles</h3>
            <div className="role-list">
              {adminData.roles.map((role) => (
                <article key={role.key}>
                  <strong>{role.name}</strong>
                  <span>{role.permissions.join(', ') || 'No permissions'}</span>
                </article>
              ))}
            </div>
          </section>
        </div>
      )}
    </div>
  );
}
