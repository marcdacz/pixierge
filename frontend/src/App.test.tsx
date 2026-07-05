import '@testing-library/jest-dom/vitest';
import { cleanup, render, screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { App } from './App';

type MockResponse = {
  status: number;
  body?: unknown;
};

const authBody = {
  csrfToken: 'csrf-token',
  user: {
    id: 'user-1',
    username: 'admin',
    roles: ['ADMIN'],
    permissions: ['identity:admin', 'identity:read', 'library:admin', 'library:read']
  }
};

const configuredLibraries = [
  {
    id: 'library-1',
    name: 'Family Photos',
    sourceCount: 3,
    availableSourceCount: 2,
    unavailableSourceCount: 1,
    createdAt: '2026-07-04T00:00:00Z',
    updatedAt: '2026-07-04T00:00:00Z',
    sources: [
      {
        id: 'source-1',
        path: '/photos/family',
        available: true,
        unavailableReason: null,
        createdAt: '2026-07-04T00:00:00Z'
      },
      {
        id: 'source-2',
        path: '/photos/scans',
        available: false,
        unavailableReason: 'missing',
        createdAt: '2026-07-04T00:00:00Z'
      }
    ]
  }
];

describe('App', () => {
  beforeEach(() => {
    vi.restoreAllMocks();
  });

  afterEach(() => {
    cleanup();
    vi.unstubAllGlobals();
  });

  it('shows first-admin setup when no users exist', async () => {
    mockFetch([
      { status: 200, body: { required: true } }
    ]);

    render(<App />);

    expect(await screen.findByRole('heading', { name: 'Create admin account' })).toBeInTheDocument();
    expect(screen.queryByRole('navigation', { name: 'Primary' })).not.toBeInTheDocument();
  });

  it('creates the first admin and opens the empty library', async () => {
    const fetchMock = mockFetch([
      { status: 200, body: { required: true } },
      { status: 200, body: authBody },
      { status: 200, body: [] }
    ]);

    render(<App />);

    await userEvent.type(await screen.findByLabelText('Username'), 'admin');
    await userEvent.type(screen.getByLabelText('Password'), 'correct horse battery staple');
    await userEvent.click(screen.getByRole('button', { name: 'Create admin' }));

    expect(await screen.findByRole('heading', { name: 'Libraries' })).toBeInTheDocument();
    expect(await screen.findByText('No library sources have been added yet.')).toBeInTheDocument();
    expect(fetchMock).toHaveBeenCalledWith(
      'http://localhost:8080/api/setup/admin',
      expect.objectContaining({ credentials: 'include', method: 'POST' })
    );
  });

  it('signs in and sends the csrf token on logout', async () => {
    const fetchMock = mockFetch([
      { status: 200, body: { required: false } },
      { status: 401, body: {} },
      { status: 200, body: authBody },
      { status: 200, body: [] },
      { status: 200 }
    ]);

    render(<App />);

    await userEvent.type(await screen.findByLabelText('Username'), 'admin');
    await userEvent.type(screen.getByLabelText('Password'), 'correct horse battery staple');
    await userEvent.click(screen.getByRole('button', { name: 'Sign in' }));

    expect(await screen.findByRole('heading', { name: 'Libraries' })).toBeInTheDocument();
    expect(await screen.findByText('No library sources have been added yet.')).toBeInTheDocument();

    await userEvent.click(screen.getByRole('button', { name: 'Profile' }));
    await userEvent.click(await screen.findByRole('menuitem', { name: 'Log out' }));

    expect(await screen.findByRole('heading', { name: 'Sign in' })).toBeInTheDocument();
    expect(fetchMock).toHaveBeenLastCalledWith(
      'http://localhost:8080/api/auth/logout',
      expect.objectContaining({
        credentials: 'include',
        headers: expect.objectContaining({ 'X-Pixierge-Csrf': 'csrf-token' }),
        method: 'POST'
      })
    );
  });

  it('routes the empty library CTA to source configuration', async () => {
    mockFetch([
      { status: 200, body: { required: false } },
      { status: 200, body: authBody },
      { status: 200, body: [] }
    ]);

    render(<App />);

    await userEvent.click(await screen.findByRole('button', { name: 'Configure sources' }));

    expect(await screen.findByRole('heading', { name: 'Settings' })).toBeInTheDocument();
    expect(screen.getByRole('heading', { name: 'Configuration' })).toBeInTheDocument();
    expect(screen.getByRole('heading', { name: 'Create library' })).toBeInTheDocument();
  });

  it('shows library source counts in the expanded nav and configuration health totals', async () => {
    mockFetch([
      { status: 200, body: { required: false } },
      { status: 200, body: authBody },
      { status: 200, body: configuredLibraries }
    ]);

    render(<App />);

    await userEvent.click(await screen.findByRole('button', { name: 'Expand navigation' }));

    const primaryNav = screen.getByRole('navigation', { name: 'Primary' });
    expect(await within(primaryNav).findByText('Family Photos')).toBeInTheDocument();
    expect(within(primaryNav).getByText('3 sources')).toBeInTheDocument();

    await userEvent.click(
      within(screen.getByRole('navigation', { name: 'Utilities' })).getByRole('button', { name: 'Settings' })
    );

    expect(await screen.findByText('2 available')).toBeInTheDocument();
    expect(screen.getByText('1 unavailable')).toBeInTheDocument();
    expect(screen.getByText('Unavailable: missing')).toBeInTheDocument();
  });

  it('creates libraries and adds sources from configuration', async () => {
    const fetchMock = mockFetch([
      { status: 200, body: { required: false } },
      { status: 200, body: authBody },
      { status: 200, body: [] },
      { status: 201, body: { ...configuredLibraries[0], sourceCount: 0, availableSourceCount: 0, unavailableSourceCount: 0, sources: [] } },
      { status: 200, body: [{ ...configuredLibraries[0], sourceCount: 0, availableSourceCount: 0, unavailableSourceCount: 0, sources: [] }] },
      { status: 201, body: configuredLibraries[0] },
      { status: 200, body: configuredLibraries }
    ]);

    render(<App />);

    await userEvent.click(await screen.findByRole('button', { name: 'Configure sources' }));
    await userEvent.type(screen.getByLabelText('Library name'), 'Family Photos');
    await userEvent.click(screen.getByRole('button', { name: 'Create' }));

    expect(await screen.findByText('0 sources')).toBeInTheDocument();

    await userEvent.type(screen.getByLabelText('Source path'), '/photos/family');
    expect(screen.getByRole('button', { name: 'Browse' })).toBeInTheDocument();
    expect(screen.getByText(/Docker sources must use container paths/)).toBeInTheDocument();

    await userEvent.click(screen.getByRole('button', { name: 'Add source' }));
    expect(await screen.findByText('/photos/family')).toBeInTheDocument();
    expect(fetchMock).toHaveBeenCalledWith(
      'http://localhost:8080/api/libraries',
      expect.objectContaining({
        headers: expect.objectContaining({ 'X-Pixierge-Csrf': 'csrf-token' }),
        method: 'POST'
      })
    );
    expect(fetchMock).toHaveBeenCalledWith(
      'http://localhost:8080/api/libraries/library-1/roots',
      expect.objectContaining({
        headers: expect.objectContaining({ 'X-Pixierge-Csrf': 'csrf-token' }),
        method: 'POST'
      })
    );
  });
});

function mockFetch(responses: MockResponse[]) {
  const fetchMock = vi.fn(async () => {
    const next = responses.shift();
    if (!next) {
      throw new Error('Unexpected fetch call');
    }

    return {
      ok: next.status >= 200 && next.status < 300,
      status: next.status,
      json: async () => next.body
    };
  });

  vi.stubGlobal('fetch', fetchMock);
  return fetchMock;
}
