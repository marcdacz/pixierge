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
  username: string;
  roles: string[];
  permissions: string[];
};

export type AuthResponse = {
  user: CurrentUser;
  csrfToken: string;
};

export type UserSummary = {
  id: string;
  username: string;
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

export type LibrarySource = {
  id: string;
  path: string;
  available: boolean;
  unavailableReason: string | null;
  createdAt: string;
};

export type LibraryExclusionPattern = {
  id: string;
  pattern: string;
  createdAt: string;
};

export type GlobalExclusionPattern = LibraryExclusionPattern;

export type LibrarySummary = {
  id: string;
  name: string;
  status: 'active' | 'archived';
  sourceCount: number;
  availableSourceCount: number;
  unavailableSourceCount: number;
  createdAt: string;
  updatedAt: string;
  archivedAt: string | null;
  sources: LibrarySource[];
  exclusionPatterns: LibraryExclusionPattern[];
};

export type ScanError = {
  id: string;
  path: string | null;
  errorCode: string;
  message: string;
  createdAt: string;
};

export type ScanRun = {
  id: string;
  libraryId: string;
  rootId: string | null;
  status: 'running' | 'completed' | 'completed_with_errors' | 'failed' | 'cancelled' | 'queued';
  startedAt: string;
  completedAt: string | null;
  scannedFileCount: number;
  addedCount: number;
  unchangedCount: number;
  movedCount: number;
  modifiedCount: number;
  duplicateCount: number;
  missingCount: number;
  reappearedCount: number;
  errorCount: number;
  errors: ScanError[];
};

export type LibraryTreeNode = {
  id: string;
  libraryId: string;
  libraryName: string;
  path: string;
  name: string;
  assetCount: number;
  childCount: number;
  children: LibraryTreeNode[];
};

export type LibraryTreeResponse = {
  roots: LibraryTreeNode[];
  libraryRootAssetCounts: Record<string, number>;
};

export type AssetSummary = {
  id: string;
  fileName: string;
  displayPath: string;
  folderPath: string;
  libraryId: string;
  libraryName: string;
  availability: 'available' | 'missing';
  duplicateCount: number;
  capturedAt: string | null;
  observedAt: string;
  mediaType: string;
  mimeType: string | null;
  width: number | null;
  height: number | null;
  previewable: boolean;
};

export type AssetSection = {
  folderPath: string;
  folderName: string;
  assets: AssetSummary[];
};

export type AssetBrowseResponse = {
  sections: AssetSection[];
  totalCount: number;
  page: number;
  pageSize: number;
  hasNext: boolean;
};

export type AssetFileOccurrence = {
  id: string;
  libraryId: string;
  libraryName: string;
  path: string;
  folderPath: string;
  fileName: string;
  sizeBytes: number;
  modifiedAt: string;
  status: 'active' | 'missing' | 'superseded';
};

export type AssetMetadata = {
  capturedAt: string | null;
  width: number | null;
  height: number | null;
  fileExtension: string | null;
  mimeType: string | null;
  extractionStatus: 'pending' | 'extracted' | 'unsupported' | 'failed' | null;
  extractedAt: string | null;
  errorMessage: string | null;
};

export type AssetDetail = {
  id: string;
  contentHash: string;
  mediaType: string;
  availability: 'available' | 'missing';
  duplicateCount: number;
  metadata: AssetMetadata;
  files: AssetFileOccurrence[];
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
  username: string;
  password: string;
}): Promise<AuthResponse> {
  return requestJson<AuthResponse>('/api/setup/admin', {
    method: 'POST',
    body: JSON.stringify(input)
  });
}

export async function login(input: { username: string; password: string }): Promise<AuthResponse> {
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

export async function fetchLibraries(): Promise<LibrarySummary[]> {
  return requestJson<LibrarySummary[]>('/api/libraries');
}

export async function fetchGlobalExclusionPatterns(): Promise<GlobalExclusionPattern[]> {
  return requestJson<GlobalExclusionPattern[]>('/api/settings/global-exclusion-patterns');
}

export async function createLibrary(input: { name: string }, csrfToken: string): Promise<LibrarySummary> {
  return requestJson<LibrarySummary>('/api/libraries', {
    method: 'POST',
    body: JSON.stringify(input),
    csrfToken
  });
}

export async function addLibraryRoot(
  libraryId: string,
  input: { path: string },
  csrfToken: string
): Promise<LibrarySummary> {
  return requestJson<LibrarySummary>(`/api/libraries/${libraryId}/roots`, {
    method: 'POST',
    body: JSON.stringify(input),
    csrfToken
  });
}

export async function deleteLibraryRoot(libraryId: string, rootId: string, csrfToken: string): Promise<void> {
  await requestWithoutBody(`/api/libraries/${libraryId}/roots/${rootId}`, {
    method: 'DELETE',
    csrfToken
  });
}

export async function archiveLibrary(libraryId: string, csrfToken: string): Promise<LibrarySummary> {
  return requestJson<LibrarySummary>(`/api/libraries/${libraryId}/archive`, {
    method: 'POST',
    csrfToken
  });
}

export async function restoreLibrary(libraryId: string, csrfToken: string): Promise<LibrarySummary> {
  return requestJson<LibrarySummary>(`/api/libraries/${libraryId}/restore`, {
    method: 'POST',
    csrfToken
  });
}

export async function addLibraryExclusionPattern(
  libraryId: string,
  input: { pattern: string },
  csrfToken: string
): Promise<LibrarySummary> {
  return requestJson<LibrarySummary>(`/api/libraries/${libraryId}/exclusion-patterns`, {
    method: 'POST',
    body: JSON.stringify(input),
    csrfToken
  });
}

export async function deleteLibraryExclusionPattern(
  libraryId: string,
  patternId: string,
  csrfToken: string
): Promise<void> {
  await requestWithoutBody(`/api/libraries/${libraryId}/exclusion-patterns/${patternId}`, {
    method: 'DELETE',
    csrfToken
  });
}

export async function addGlobalExclusionPattern(
  input: { pattern: string },
  csrfToken: string
): Promise<GlobalExclusionPattern> {
  return requestJson<GlobalExclusionPattern>('/api/settings/global-exclusion-patterns', {
    method: 'POST',
    body: JSON.stringify(input),
    csrfToken
  });
}

export async function deleteGlobalExclusionPattern(patternId: string, csrfToken: string): Promise<void> {
  await requestWithoutBody(`/api/settings/global-exclusion-patterns/${patternId}`, {
    method: 'DELETE',
    csrfToken
  });
}

export async function scanLibrary(libraryId: string, csrfToken: string): Promise<ScanRun> {
  return requestJson<ScanRun>(`/api/libraries/${libraryId}/scans`, {
    method: 'POST',
    csrfToken
  });
}

export async function scanLibraryRoot(libraryId: string, rootId: string, csrfToken: string): Promise<ScanRun> {
  return requestJson<ScanRun>(`/api/libraries/${libraryId}/roots/${rootId}/scans`, {
    method: 'POST',
    csrfToken
  });
}

export async function fetchScan(scanRunId: string): Promise<ScanRun> {
  return requestJson<ScanRun>(`/api/scans/${scanRunId}`);
}

export async function fetchLibraryTree(libraryId?: string): Promise<LibraryTreeResponse> {
  const params = new URLSearchParams();
  if (libraryId) {
    params.set('libraryId', libraryId);
  }
  return requestJson<LibraryTreeResponse>(`/api/library-tree${queryString(params)}`);
}

export async function fetchAssets(input: {
  libraryId?: string;
  folder?: string;
  includeDescendants?: boolean;
  q?: string;
  availability?: string;
  fileType?: string;
  duplicatesOnly?: boolean;
  page?: number;
  pageSize?: number;
} = {}): Promise<AssetBrowseResponse> {
  const params = new URLSearchParams();
  if (input.libraryId) {
    params.set('libraryId', input.libraryId);
  }
  if (input.folder) {
    params.set('folder', input.folder);
  }
  if (input.includeDescendants !== undefined) {
    params.set('includeDescendants', String(input.includeDescendants));
  }
  if (input.q) {
    params.set('q', input.q);
  }
  if (input.availability) {
    params.set('availability', input.availability);
  }
  if (input.fileType) {
    params.set('fileType', input.fileType);
  }
  if (input.duplicatesOnly) {
    params.set('duplicatesOnly', 'true');
  }
  if (input.page !== undefined) {
    params.set('page', String(input.page));
  }
  if (input.pageSize !== undefined) {
    params.set('pageSize', String(input.pageSize));
  }
  return requestJson<AssetBrowseResponse>(`/api/assets${queryString(params)}`);
}

export async function fetchAsset(assetId: string): Promise<AssetDetail> {
  return requestJson<AssetDetail>(`/api/assets/${assetId}`);
}

export async function backfillAssetMetadata(csrfToken: string): Promise<{ processedCount: number; failedCount: number }> {
  return requestJson<{ processedCount: number; failedCount: number }>('/api/assets/metadata/backfill', {
    method: 'POST',
    csrfToken
  });
}

export function assetFileUrl(assetId: string): string {
  return `${apiBaseUrl}/api/assets/${assetId}/file`;
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
    throw new ApiError(response.status, await responseErrorMessage(response));
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
    throw new ApiError(response.status, await responseErrorMessage(response));
  }
}

async function responseErrorMessage(response: Response): Promise<string> {
  const fallback = `Request failed with ${response.status}`;
  const contentType = response.headers.get('Content-Type') ?? '';

  if (!contentType.includes('json')) {
    return fallback;
  }

  try {
    const body = await response.json() as {
      detail?: unknown;
      error?: unknown;
      message?: unknown;
      title?: unknown;
    };
    return stringValue(body.detail)
      ?? stringValue(body.message)
      ?? stringValue(body.error)
      ?? nonGenericTitle(body.title, response.status)
      ?? fallback;
  } catch (error) {
    return fallback;
  }
}

function nonGenericTitle(value: unknown, status: number): string | null {
  const title = stringValue(value);
  if (title === null) {
    return null;
  }
  if (title.toLowerCase() === genericStatusTitle(status)) {
    return null;
  }
  return title;
}

function genericStatusTitle(status: number): string {
  if (status === 400) {
    return 'bad request';
  }
  if (status === 401) {
    return 'unauthorized';
  }
  if (status === 403) {
    return 'forbidden';
  }
  if (status === 404) {
    return 'not found';
  }
  if (status === 409) {
    return 'conflict';
  }
  return '';
}

function queryString(params: URLSearchParams): string {
  const value = params.toString();
  return value ? `?${value}` : '';
}

function stringValue(value: unknown): string | null {
  return typeof value === 'string' && value.trim() !== '' ? value : null;
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
