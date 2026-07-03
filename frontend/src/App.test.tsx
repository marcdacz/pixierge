import '@testing-library/jest-dom/vitest';
import { render, screen } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { App } from './App';

describe('App', () => {
  beforeEach(() => {
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue({
        ok: true,
        status: 200,
        json: async () => ({ status: 'ok', database: 'ready', app: 'pixierge-api' })
      })
    );
  });

  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it('shows the API and database status after loading health', async () => {
    render(<App />);

    expect(screen.getByRole('heading', { name: 'Pixierge' })).toBeInTheDocument();
    expect(screen.getAllByText('Checking')).toHaveLength(2);

    expect(await screen.findByText('Connected')).toBeInTheDocument();
    expect(screen.getByText('Ready')).toBeInTheDocument();
  });
});
