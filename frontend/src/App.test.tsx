import '@testing-library/jest-dom/vitest';
import { cleanup, render, screen } from '@testing-library/react';
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
    email: 'admin@example.com',
    displayName: 'Admin User',
    roles: ['ADMIN'],
    permissions: ['identity:admin', 'identity:read']
  }
};

const usersBody = [
  {
    id: 'user-1',
    email: 'admin@example.com',
    displayName: 'Admin User',
    status: 'active',
    roles: ['ADMIN'],
    createdAt: '2026-07-03T00:00:00Z'
  }
];

const rolesBody = [
  {
    key: 'ADMIN',
    name: 'Admin',
    description: 'Full local administration role',
    permissions: ['identity:admin', 'identity:read']
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
      { status: 200, body: { status: 'ok', database: 'ready', app: 'pixierge-api' } },
      { status: 200, body: { required: true } }
    ]);

    render(<App />);

    expect(screen.getByRole('heading', { name: 'Pixierge' })).toBeInTheDocument();
    expect(await screen.findByRole('heading', { name: 'Create admin account' })).toBeInTheDocument();
    expect(screen.getByText('Connected')).toBeInTheDocument();
  });

  it('creates the first admin and loads identity data', async () => {
    const fetchMock = mockFetch([
      { status: 200, body: { status: 'ok', database: 'ready', app: 'pixierge-api' } },
      { status: 200, body: { required: true } },
      { status: 200, body: authBody },
      { status: 200, body: usersBody },
      { status: 200, body: rolesBody }
    ]);

    render(<App />);

    await userEvent.type(await screen.findByLabelText('Email'), 'admin@example.com');
    await userEvent.type(screen.getByLabelText('Display name'), 'Admin User');
    await userEvent.type(screen.getByLabelText('Password'), 'correct horse battery staple');
    await userEvent.click(screen.getByRole('button', { name: 'Create admin' }));

    expect(await screen.findByRole('heading', { name: 'Admin' })).toBeInTheDocument();
    expect(screen.getByText('admin@example.com')).toBeInTheDocument();
    expect(screen.getByText('identity:admin, identity:read')).toBeInTheDocument();
    expect(fetchMock).toHaveBeenCalledWith(
      'http://localhost:8080/api/setup/admin',
      expect.objectContaining({ credentials: 'include', method: 'POST' })
    );
  });

  it('signs in and sends the csrf token on logout', async () => {
    const fetchMock = mockFetch([
      { status: 200, body: { status: 'ok', database: 'ready', app: 'pixierge-api' } },
      { status: 200, body: { required: false } },
      { status: 401, body: {} },
      { status: 200, body: authBody },
      { status: 200, body: usersBody },
      { status: 200, body: rolesBody },
      { status: 200 }
    ]);

    render(<App />);

    await userEvent.type(await screen.findByLabelText('Email'), 'admin@example.com');
    await userEvent.type(screen.getByLabelText('Password'), 'correct horse battery staple');
    await userEvent.click(screen.getByRole('button', { name: 'Sign in' }));

    expect(await screen.findByRole('heading', { name: 'Admin' })).toBeInTheDocument();
    await userEvent.click(screen.getByRole('button', { name: 'Sign out' }));

    expect(await screen.findByRole('heading', { name: 'Admin sign in' })).toBeInTheDocument();
    expect(fetchMock).toHaveBeenLastCalledWith(
      'http://localhost:8080/api/auth/logout',
      expect.objectContaining({
        credentials: 'include',
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
