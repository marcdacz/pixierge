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
    username: 'admin',
    roles: ['ADMIN'],
    permissions: ['identity:admin', 'identity:read']
  }
};

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
      { status: 200, body: authBody }
    ]);

    render(<App />);

    await userEvent.type(await screen.findByLabelText('Username'), 'admin');
    await userEvent.type(screen.getByLabelText('Password'), 'correct horse battery staple');
    await userEvent.click(screen.getByRole('button', { name: 'Create admin' }));

    expect(await screen.findByRole('heading', { name: 'Libraries' })).toBeInTheDocument();
    expect(screen.getByText('No libraries have been added yet.')).toBeInTheDocument();
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
      { status: 200 }
    ]);

    render(<App />);

    await userEvent.type(await screen.findByLabelText('Username'), 'admin');
    await userEvent.type(screen.getByLabelText('Password'), 'correct horse battery staple');
    await userEvent.click(screen.getByRole('button', { name: 'Sign in' }));

    expect(await screen.findByRole('heading', { name: 'Libraries' })).toBeInTheDocument();
    expect(screen.getByText('No libraries have been added yet.')).toBeInTheDocument();

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
