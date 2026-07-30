import '@testing-library/jest-dom/vitest';
import { cleanup, render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { UsersSettings } from '@/features/settings/settings-page';

const auth = {
  csrfToken: 'csrf-token',
  user: {
    id: 'user-1',
    username: 'admin',
    roles: ['ADMIN'],
    permissions: ['identity:admin']
  }
};

type MockUser = {
  id: string;
  username: string;
  status: 'active' | 'disabled';
  roles: string[];
  createdAt: string;
};

const adminUser: MockUser = {
  id: 'user-1',
  username: 'admin',
  status: 'active',
  roles: ['ADMIN'],
  createdAt: '2026-07-30T00:00:00Z'
};

const samUser: MockUser = {
  id: 'user-2',
  username: 'sam',
  status: 'active',
  roles: ['USER'],
  createdAt: '2026-07-30T00:00:00Z'
};

const leeUser: MockUser = {
  id: 'user-3',
  username: 'lee',
  status: 'active',
  roles: ['USER'],
  createdAt: '2026-07-30T00:00:00Z'
};

describe('UsersSettings', () => {
  beforeEach(() => {
    vi.restoreAllMocks();
  });

  afterEach(() => {
    cleanup();
  });

  it('creates users and refreshes the table', async () => {
    let users = [adminUser];
    const requests: Array<{ method: string; path: string; body: unknown }> = [];
    mockFetch(async (path, init) => {
      if (path === '/api/admin/users' && method(init) === 'GET') {
        return json(users);
      }
      if (path === '/api/admin/users' && method(init) === 'POST') {
        const body = JSON.parse(String(init?.body));
        requests.push({ method: 'POST', path, body });
        users = [...users, samUser];
        return json(samUser);
      }
      return json({}, 404);
    });

    render(<UsersSettings auth={auth} onError={vi.fn()} />);

    expect(await screen.findByText('admin')).toBeInTheDocument();
    await userEvent.type(screen.getByLabelText('Username'), 'sam');
    await userEvent.type(screen.getByLabelText('Password'), 'a secure password');
    await userEvent.click(screen.getByRole('button', { name: 'Create user' }));

    expect(await screen.findByText('sam created.')).toBeInTheDocument();
    expect(screen.getByText('sam')).toBeInTheDocument();
    expect(requests).toEqual([
      {
        method: 'POST',
        path: '/api/admin/users',
        body: { username: 'sam', password: 'a secure password' }
      }
    ]);
  });

  it('requires replacement selection and typed confirmation before deleting', async () => {
    let users = [adminUser, samUser, leeUser];
    const requests: Array<{ method: string; path: string; body: unknown }> = [];
    mockFetch(async (path, init) => {
      if (path === '/api/admin/users' && method(init) === 'GET') {
        return json(users);
      }
      if (path === '/api/admin/users/user-2' && method(init) === 'DELETE') {
        const body = JSON.parse(String(init?.body));
        requests.push({ method: 'DELETE', path, body });
        users = users.filter((user) => user.id !== 'user-2');
        return empty();
      }
      return json({}, 404);
    });

    render(<UsersSettings auth={auth} onError={vi.fn()} />);

    const samRow = await screen.findByRole('row', { name: /sam active USER/ });
    await userEvent.click(within(samRow).getByRole('button', { name: 'Delete' }));
    const dialog = screen.getByRole('dialog', { name: 'Delete sam?' });
    const deleteButton = within(dialog).getByRole('button', { name: 'Delete user' });

    expect(deleteButton).toBeDisabled();
    await userEvent.selectOptions(within(dialog).getByLabelText('Replacement user'), 'user-3');
    await userEvent.type(within(dialog).getByLabelText('Type sam to confirm'), 'wrong');
    expect(deleteButton).toBeDisabled();
    await userEvent.clear(within(dialog).getByLabelText('Type sam to confirm'));
    await userEvent.type(within(dialog).getByLabelText('Type sam to confirm'), 'sam');
    await userEvent.click(deleteButton);

    expect(await screen.findByText('sam deleted.')).toBeInTheDocument();
    await waitFor(() => expect(screen.queryByRole('dialog', { name: 'Delete sam?' })).not.toBeInTheDocument());
    expect(requests).toEqual([
      {
        method: 'DELETE',
        path: '/api/admin/users/user-2',
        body: { replacementUserId: 'user-3' }
      }
    ]);
  });

  it('surfaces reset errors and status changes', async () => {
    let users = [adminUser, samUser];
    const onError = vi.fn();
    mockFetch(async (path, init) => {
      if (path === '/api/admin/users' && method(init) === 'GET') {
        return json(users);
      }
      if (path === '/api/admin/users/user-2/reset-password' && method(init) === 'POST') {
        return json({ detail: 'Password must be at least 12 characters' }, 400);
      }
      if (path === '/api/admin/users/user-2' && method(init) === 'PATCH') {
        users = users.map((user) => user.id === 'user-2' ? { ...user, status: 'disabled' as const } : user);
        return json(users.find((user) => user.id === 'user-2'));
      }
      return json({}, 404);
    });

    render(<UsersSettings auth={auth} onError={onError} />);

    const samRow = await screen.findByRole('row', { name: /sam active USER/ });
    await userEvent.click(within(samRow).getByRole('button', { name: 'Reset' }));
    await userEvent.type(screen.getByLabelText('New password for sam'), 'short but long');
    await userEvent.click(screen.getByRole('button', { name: 'Reset password' }));
    expect(await screen.findByText('Password must be at least 12 characters')).toBeInTheDocument();
    expect(onError).toHaveBeenCalledWith('Password could not be reset', 'Password must be at least 12 characters');

    await userEvent.click(within(samRow).getByRole('button', { name: 'Deactivate' }));
    expect(await screen.findByText('sam is disabled.')).toBeInTheDocument();
    expect(await screen.findByRole('row', { name: /sam disabled USER/ })).toBeInTheDocument();
  });
});

function mockFetch(handler: (path: string, init?: RequestInit) => Promise<Response>) {
  vi.spyOn(globalThis, 'fetch').mockImplementation((async (input, init) => {
    const url = typeof input === 'string' ? input : input instanceof Request ? input.url : input.toString();
    return handler(new URL(url).pathname, init);
  }) as typeof fetch);
}

function method(init?: RequestInit) {
  return init?.method ?? 'GET';
}

function json(body: unknown, status = 200) {
  return Promise.resolve(new Response(JSON.stringify(body), {
    status,
    headers: { 'Content-Type': 'application/json' }
  }));
}

function empty() {
  return Promise.resolve(new Response('', { status: 200 }));
}
