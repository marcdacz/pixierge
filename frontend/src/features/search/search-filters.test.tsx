import '@testing-library/jest-dom/vitest';
import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { useState } from 'react';
import { afterEach, describe, expect, it, vi } from 'vitest';
import type { LibrarySummary } from '@/api';
import { SearchFilters } from './search-filters';

afterEach(() => {
  cleanup();
  vi.unstubAllGlobals();
});

const libraries: LibrarySummary[] = [
  {
    id: 'l1',
    name: 'Events',
    status: 'active',
    sourceCount: 1,
    availableSourceCount: 1,
    unavailableSourceCount: 0,
    createdAt: '',
    updatedAt: '',
    archivedAt: null,
    sources: [],
    exclusionPatterns: []
  },
  {
    id: 'l2',
    name: 'Japan',
    status: 'active',
    sourceCount: 1,
    availableSourceCount: 1,
    unavailableSourceCount: 0,
    createdAt: '',
    updatedAt: '',
    archivedAt: null,
    sources: [],
    exclusionPatterns: []
  }
];

describe('SearchFilters', () => {
  it('styles album rows like the organizer sidebar and hides available/missing/folder', async () => {
    stubSearchApis({
      albums: [
        {
          id: 'a1',
          name: 'amazing',
          coverAssetId: null,
          coverFileName: null,
          kind: 'user',
          itemCount: 0,
          sourceLibraryCount: 1,
          createdAt: '2026-01-01T00:00:00Z',
          updatedAt: '2026-01-01T00:00:00Z'
        }
      ],
      tags: [{ id: 't1', name: 'Family', assetCount: 3, createdAt: '', updatedAt: '' }]
    });

    render(<SearchFilters libraries={libraries} onQueryChange={vi.fn()} query="" />);

    expect(await screen.findByRole('button', { name: /amazing/ })).toBeInTheDocument();
    expect(screen.getByText('amazing').closest('span')).toHaveClass('truncate');
    expect(screen.queryByRole('button', { name: 'available' })).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'missing' })).not.toBeInTheDocument();
    expect(screen.queryByLabelText('Folder filter')).not.toBeInTheDocument();
    expect(screen.queryByLabelText('Camera filter')).not.toBeInTheDocument();
  });

  it('allows selecting multiple libraries', async () => {
    stubSearchApis();
    const onQueryChange = vi.fn();
    render(<FilterHarness libraries={libraries} onQueryChange={onQueryChange} />);

    await userEvent.click(screen.getByRole('button', { name: 'Events' }));
    expect(onQueryChange).toHaveBeenCalledWith('library:Events');

    await userEvent.click(screen.getByRole('button', { name: 'Japan' }));
    expect(onQueryChange).toHaveBeenCalledWith('library:Events,Japan');

    await userEvent.click(screen.getByRole('button', { name: 'Any library' }));
    expect(onQueryChange).toHaveBeenCalledWith('');
  });

  it('allows selecting multiple albums and tags', async () => {
    stubSearchApis({
      albums: [
        {
          id: 'a1',
          name: 'Summer',
          coverAssetId: null,
          coverFileName: null,
          kind: 'user',
          itemCount: 2,
          sourceLibraryCount: 1,
          createdAt: '2026-01-01T00:00:00Z',
          updatedAt: '2026-01-01T00:00:00Z'
        },
        {
          id: 'a2',
          name: 'Winter',
          coverAssetId: null,
          coverFileName: null,
          kind: 'user',
          itemCount: 1,
          sourceLibraryCount: 1,
          createdAt: '2026-01-01T00:00:00Z',
          updatedAt: '2026-01-01T00:00:00Z'
        }
      ],
      tags: [
        { id: 't1', name: 'Family', assetCount: 3, createdAt: '', updatedAt: '' },
        { id: 't2', name: 'Holiday', assetCount: 1, createdAt: '', updatedAt: '' }
      ]
    });
    const onQueryChange = vi.fn();
    render(<FilterHarness libraries={libraries} onQueryChange={onQueryChange} />);

    await userEvent.click(await screen.findByRole('button', { name: /Summer/ }));
    expect(onQueryChange).toHaveBeenCalledWith('album:Summer');
    await userEvent.click(screen.getByRole('button', { name: /Winter/ }));
    expect(onQueryChange).toHaveBeenCalledWith('album:Summer,Winter');

    await userEvent.click(screen.getByRole('button', { name: /Family/ }));
    expect(onQueryChange).toHaveBeenCalledWith('album:Summer,Winter tag:Family');
    await userEvent.click(screen.getByRole('button', { name: /Holiday/ }));
    expect(onQueryChange).toHaveBeenCalledWith('album:Summer,Winter tag:Family,Holiday');
  });

  it('applies captured date presets and multi-value extension filters', async () => {
    stubSearchApis();
    const onQueryChange = vi.fn();
    render(<FilterHarness onQueryChange={onQueryChange} />);

    await userEvent.click(screen.getByLabelText('Extension filter'));
    await userEvent.click(await screen.findByRole('menuitem', { name: /\.jpg/i }));
    expect(onQueryChange).toHaveBeenCalledWith('extension:.jpg');

    await userEvent.click(await screen.findByRole('menuitem', { name: /\.heic/i }));
    expect(onQueryChange).toHaveBeenCalledWith('extension:.jpg,.heic');

    await userEvent.type(screen.getByLabelText('Custom extension'), 'raw{Enter}');
    expect(onQueryChange).toHaveBeenCalledWith('extension:.jpg,.heic,.raw');
    await userEvent.keyboard('{Escape}');
    await waitFor(() => {
      expect(screen.queryByRole('menuitem', { name: /\.jpg/i })).not.toBeInTheDocument();
    });

    await userEvent.click(screen.getByLabelText('Captured date filter'));
    await userEvent.click(await screen.findByRole('menuitem', { name: 'Last 7 days' }));

    await waitFor(() => {
      const last = onQueryChange.mock.calls.at(-1)?.[0] as string;
      expect(last).toMatch(/^extension:\.jpg,\.heic,\.raw after:\d{4}-\d{2}-\d{2}$/);
    });

    await userEvent.click(screen.getByLabelText('Captured date filter'));
    await userEvent.click(await screen.findByRole('menuitem', { name: 'On date…' }));
    fireEvent.change(screen.getByLabelText('On date'), { target: { value: '2024-06-01' } });
    await waitFor(() => {
      expect(onQueryChange).toHaveBeenCalledWith(
        'extension:.jpg,.heic,.raw on:2024-06-01'
      );
    });

    expect(screen.getByRole('button', { name: 'Open On date calendar' })).toBeInTheDocument();

    await userEvent.click(screen.getByLabelText('Captured date filter'));
    await userEvent.click(await screen.findByRole('menuitem', { name: 'Custom range…' }));
    expect(screen.getByRole('button', { name: 'Open Captured after date calendar' })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Open Captured before date calendar' })).toBeInTheDocument();
    expect(screen.getByLabelText('Captured date filter')).toHaveTextContent('Custom');

    fireEvent.change(screen.getByLabelText('Captured after date'), { target: { value: '2026-07-15' } });
    fireEvent.change(screen.getByLabelText('Captured before date'), { target: { value: '2026-07-08' } });
    await waitFor(() => {
      expect(onQueryChange).toHaveBeenCalledWith(
        'extension:.jpg,.heic,.raw after:2026-07-08 before:2026-07-15'
      );
    });
    expect(screen.getByLabelText('Captured after date')).toHaveValue('2026-07-08');
    expect(screen.getByLabelText('Captured before date')).toHaveValue('2026-07-15');
    expect(screen.getByLabelText('Captured date filter')).toHaveTextContent('Custom');
  });

  it('collapses library album and tag sections', async () => {
    stubSearchApis();
    render(<SearchFilters libraries={[]} onQueryChange={vi.fn()} query="" />);

    const libraryToggle = await screen.findByRole('button', { name: 'Library' });
    expect(libraryToggle).toHaveAttribute('aria-expanded', 'true');
    await userEvent.click(libraryToggle);
    expect(libraryToggle).toHaveAttribute('aria-expanded', 'false');
    expect(screen.queryByRole('button', { name: 'Any library' })).not.toBeInTheDocument();

    await userEvent.click(screen.getByRole('button', { name: 'Album' }));
    expect(screen.queryByText('No albums yet.')).not.toBeInTheDocument();

    await userEvent.click(screen.getByRole('button', { name: 'Tag' }));
    expect(screen.queryByText('No tags yet.')).not.toBeInTheDocument();
  });
});

function FilterHarness({
  libraries = [],
  onQueryChange
}: {
  libraries?: LibrarySummary[];
  onQueryChange: (query: string) => void;
}) {
  const [query, setQuery] = useState('');
  return (
    <SearchFilters
      libraries={libraries}
      onQueryChange={(next) => {
        setQuery(next);
        onQueryChange(next);
      }}
      query={query}
    />
  );
}

function stubSearchApis({
  albums = [],
  tags = []
}: {
  albums?: unknown[];
  tags?: unknown[];
} = {}) {
  vi.stubGlobal(
    'fetch',
    vi.fn(async (input: RequestInfo | URL) => {
      const url = String(input);
      if (url.includes('/api/albums')) return jsonResponse(albums);
      if (url.includes('/api/tags')) return jsonResponse(tags);
      if (url.includes('/api/search/suggestions')) return jsonResponse([]);
      if (url.includes('/api/search/parse')) {
        const query = new URL(url, 'http://localhost').searchParams.get('q') ?? '';
        return jsonResponse(parseStub(query));
      }
      return jsonResponse({ query: '', freeText: '', clauses: [], errors: [], valid: true });
    })
  );
}

function parseStub(query: string) {
  const clauses: Array<{ field: string; value: string; negated: boolean; start: number; end: number; label: string }> = [];
  for (const match of query.matchAll(/(?:^|\s)([a-z]+):([^\s]+)/g)) {
    const field = match[1];
    const value = match[2];
    clauses.push({
      field,
      value,
      negated: false,
      start: match.index ?? 0,
      end: (match.index ?? 0) + match[0].length,
      label: `${field}: ${value}`
    });
  }
  return { query, freeText: '', clauses, errors: [], valid: true };
}

function jsonResponse(body: unknown) {
  return new Response(JSON.stringify(body), {
    status: 200,
    headers: { 'Content-Type': 'application/json' }
  });
}
