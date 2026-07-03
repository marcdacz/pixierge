export type HealthResponse = {
  status: 'ok' | 'degraded';
  database: 'ready' | 'unavailable';
  app: string;
};

export type SetupStatusResponse = {
  required: boolean;
};

export type CurrentUser = {
  id: string;
  email: string;
  displayName: string;
  roles: string[];
  permissions: string[];
};

export type AuthResponse = {
  user: CurrentUser;
  csrfToken: string;
};

export type UserSummary = {
  id: string;
  email: string;
  displayName: string;
  status: 'active' | 'disabled';
  roles: string[];
  createdAt: string;
};

export type RoleSummary = {
  key: string;
  name: string;
  description: string;
  permissions: string[];
};

export class ApiError extends Error {
  readonly status: number;

  constructor(status: number, message: string) {
    super(message);
    this.status = status;
  }
}

const apiBaseUrl = import.meta.env.VITE_API_BASE_URL ?? 'http://localhost:8080';

export async function fetchHealth(): Promise<HealthResponse> {
  const response = await fetch(`${apiBaseUrl}/api/health`, { credentials: 'include' });

  if (!response.ok && response.status !== 503) {
    throw new Error(`Health check failed with ${response.status}`);
  }

  return response.json() as Promise<HealthResponse>;
}

export async function fetchSetupStatus(): Promise<SetupStatusResponse> {
  return requestJson<SetupStatusResponse>('/api/setup/status');
}

export async function createFirstAdmin(input: {
  email: string;
  displayName: string;
  password: string;
}): Promise<AuthResponse> {
  return requestJson<AuthResponse>('/api/setup/admin', {
    method: 'POST',
    body: JSON.stringify(input)
  });
}

export async function login(input: { email: string; password: string }): Promise<AuthResponse> {
  return requestJson<AuthResponse>('/api/auth/login', {
    method: 'POST',
    body: JSON.stringify(input)
  });
}

export async function logout(csrfToken: string): Promise<void> {
  await requestWithoutBody('/api/auth/logout', {
    method: 'POST',
    csrfToken
  });
}

export async function fetchSession(): Promise<AuthResponse> {
  return requestJson<AuthResponse>('/api/auth/session');
}

export async function fetchUsers(): Promise<UserSummary[]> {
  return requestJson<UserSummary[]>('/api/admin/users');
}

export async function fetchRoles(): Promise<RoleSummary[]> {
  return requestJson<RoleSummary[]>('/api/admin/roles');
}

async function requestJson<T>(
  path: string,
  options: { method?: string; body?: string; csrfToken?: string } = {}
): Promise<T> {
  const response = await fetch(`${apiBaseUrl}${path}`, {
    method: options.method ?? 'GET',
    credentials: 'include',
    headers: requestHeaders(options),
    body: options.body
  });

  if (!response.ok) {
    throw new ApiError(response.status, `Request failed with ${response.status}`);
  }

  return response.json() as Promise<T>;
}

async function requestWithoutBody(
  path: string,
  options: { method: string; csrfToken?: string }
): Promise<void> {
  const response = await fetch(`${apiBaseUrl}${path}`, {
    method: options.method,
    credentials: 'include',
    headers: requestHeaders(options)
  });

  if (!response.ok) {
    throw new ApiError(response.status, `Request failed with ${response.status}`);
  }
}

function requestHeaders(options: { body?: string; csrfToken?: string }): HeadersInit {
  const headers: Record<string, string> = {};

  if (options.body !== undefined) {
    headers['Content-Type'] = 'application/json';
  }
  if (options.csrfToken) {
    headers['X-Pixierge-Csrf'] = options.csrfToken;
  }

  return headers;
}
