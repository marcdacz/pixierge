import '@testing-library/jest-dom/vitest';
import { cleanup, render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { StrictMode, useCallback, useState } from 'react';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { StructuredSearch } from './structured-search';

afterEach(() => {
  cleanup();
  vi.unstubAllGlobals();
});

describe('StructuredSearch', () => {
  it('hydrates structured clauses from the initial value into pills', async () => {
    const onChange = vi.fn();
    vi.stubGlobal('fetch', vi.fn(async () => jsonResponse({
      query: 'extension:.png extension:.jpg',
      freeText: '',
      clauses: [
        { field: 'extension', value: '.png', negated: false, start: 0, end: 15, label: 'extension: .png' },
        { field: 'extension', value: '.jpg', negated: false, start: 16, end: 31, label: 'extension: .jpg' }
      ],
      errors: [],
      valid: true
    })));

    render(
      <StrictMode>
        <StructuredSearch
          onChange={onChange}
          onValidQueryChange={vi.fn()}
          value="extension:.png extension:.jpg"
        />
      </StrictMode>
    );

    expect(await screen.findByRole('button', { name: 'Remove extension: .png,.jpg' })).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Remove extension: .png' })).not.toBeInTheDocument();
    expect(screen.getByLabelText('Search')).toHaveValue('');
    await waitFor(() => expect(onChange).toHaveBeenCalledWith('extension:.png,.jpg'));
  });

  it('collapses repeated library clauses into one comma-separated pill', async () => {
    const onChange = vi.fn();
    vi.stubGlobal('fetch', vi.fn(async () => jsonResponse({
      query: 'library:Events library:Japan',
      freeText: '',
      clauses: [
        { field: 'library', value: 'Events', negated: false, start: 0, end: 15, label: 'library: Events' },
        { field: 'library', value: 'Japan', negated: false, start: 16, end: 30, label: 'library: Japan' }
      ],
      errors: [],
      valid: true
    })));

    render(
      <StructuredSearch
        onChange={onChange}
        onValidQueryChange={vi.fn()}
        value="library:Events library:Japan"
      />
    );

    expect(await screen.findByRole('button', { name: 'Remove library: Events,Japan' })).toBeInTheDocument();
    await waitFor(() => expect(onChange).toHaveBeenCalledWith('library:Events,Japan'));
  });

  it('commits a value suggestion as an inline pill and keeps free text editable', async () => {
    const validQuery = vi.fn();
    vi.stubGlobal('fetch', vi.fn(async (input: RequestInfo | URL) => {
      const url = String(input);
      if (url.includes('/api/search/suggestions')) {
        return jsonResponse([{ value: 'Family', label: 'Family' }]);
      }
      return jsonResponse({
        query: url.includes('q=tag') ? decodeURIComponent(url.split('q=')[1] ?? '') : '',
        freeText: '',
        clauses: [{ field: 'tag', value: 'Family', negated: false, start: 0, end: 10, label: 'tag: Family' }],
        errors: [],
        valid: true
      });
    }));

    render(<Harness onValidQueryChange={validQuery} />);
    const input = screen.getByLabelText('Search');
    await userEvent.type(input, 'tag:fa');
    expect(await screen.findByRole('option', { name: 'Family' })).toBeInTheDocument();
    await userEvent.keyboard('{Enter}');

    expect(screen.getByRole('button', { name: 'Remove tag: Family' })).toBeInTheDocument();
    expect(input).toHaveValue('');
    await waitFor(() => expect(validQuery).toHaveBeenCalledWith('tag:Family'));

    await userEvent.type(input, 'beach');
    await waitFor(() => expect(validQuery).toHaveBeenCalledWith('tag:Family beach'));
  });

  it('removes an inline pill with the clear button or backspace', async () => {
    const validQuery = vi.fn();
    vi.stubGlobal('fetch', vi.fn(async () => jsonResponse({
      query: 'is:starred',
      freeText: '',
      clauses: [{ field: 'is', value: 'starred', negated: false, start: 0, end: 10, label: 'is: starred' }],
      errors: [],
      valid: true
    })));

    render(<Harness onValidQueryChange={validQuery} />);
    const input = screen.getByLabelText('Search');
    await userEvent.type(input, 'st');
    expect(await screen.findByRole('option', { name: 'starred' })).toBeInTheDocument();
    await userEvent.keyboard('{Enter}');
    expect(screen.getByRole('button', { name: 'Remove is: starred' })).toBeInTheDocument();
    expect(input).toHaveValue('');

    await userEvent.keyboard('{Backspace}');
    expect(screen.queryByRole('button', { name: 'Remove is: starred' })).not.toBeInTheDocument();
  });

  it('supports keyboard field autocomplete for partial matches only', async () => {
    render(<Harness onValidQueryChange={vi.fn()} />);
    const input = screen.getByLabelText('Search');

    await userEvent.click(input);
    expect(screen.queryByRole('listbox')).not.toBeInTheDocument();

    await userEvent.type(input, 'al');
    expect(await screen.findByRole('option', { name: 'album:' })).toBeInTheDocument();
    expect(screen.queryByRole('option', { name: 'folder:' })).not.toBeInTheDocument();
    await userEvent.keyboard('{Enter}');
    expect(input).toHaveValue('album:');
    expect(screen.queryByRole('button', { name: /Remove/ })).not.toBeInTheDocument();
  });

  it('suggests starred as a top-level keyword and commits a pill', async () => {
    vi.stubGlobal('fetch', vi.fn(async () => jsonResponse({
      query: 'is:starred',
      freeText: '',
      clauses: [{ field: 'is', value: 'starred', negated: false, start: 0, end: 10, label: 'is: starred' }],
      errors: [],
      valid: true
    })));
    render(<Harness onValidQueryChange={vi.fn()} />);
    const input = screen.getByLabelText('Search');
    await userEvent.type(input, 'st');
    expect(await screen.findByRole('option', { name: 'starred' })).toBeInTheDocument();
    await userEvent.keyboard('{Enter}');
    expect(screen.getByRole('button', { name: 'Remove is: starred' })).toBeInTheDocument();
    expect(input).toHaveValue('');
  });

  it('commits a typed field:value as an inline pill on Space or Enter', async () => {
    vi.stubGlobal('fetch', vi.fn(async (input: RequestInfo | URL) => {
      const url = String(input);
      if (url.includes('/api/search/suggestions')) {
        return jsonResponse([]);
      }
      return jsonResponse({
        query: 'library:japan',
        freeText: '',
        clauses: [{ field: 'library', value: 'japan', negated: false, start: 0, end: 13, label: 'library: japan' }],
        errors: [],
        valid: true
      });
    }));

    render(<Harness onValidQueryChange={vi.fn()} />);
    const input = screen.getByLabelText('Search');
    await userEvent.type(input, 'library:japan');
    expect(await screen.findByRole('option', { name: /library: japan/i })).toBeInTheDocument();
    await userEvent.keyboard('{Enter}');

    expect(screen.getByRole('button', { name: 'Remove library: japan' })).toBeInTheDocument();
    expect(input).toHaveValue('');
  });

  it('styles keyword prefixes in field suggestions', async () => {
    render(<Harness onValidQueryChange={vi.fn()} />);
    const input = screen.getByLabelText('Search');
    await userEvent.type(input, 'ext');
    const option = await screen.findByRole('option', { name: 'extension:' });
    expect(option.querySelector('.italic')).toHaveTextContent('extension:');
  });

  it('shows an inline error and does not commit an invalid query', async () => {
    const validQuery = vi.fn();
    vi.stubGlobal('fetch', vi.fn(async () => jsonResponse({
      query: 'person:Alice',
      freeText: '',
      clauses: [],
      errors: [{ code: 'UNKNOWN_FIELD', message: "Search field 'person' is unavailable until a plugin provides it", start: 0, end: 12 }],
      valid: false
    })));
    render(<Harness onValidQueryChange={validQuery} />);
    const input = screen.getByLabelText('Search');
    await userEvent.type(input, 'person:Alice');

    expect(await screen.findByText(/unavailable until a plugin/)).toBeInTheDocument();
    expect(input).toHaveAttribute('aria-invalid', 'true');
    expect(validQuery).not.toHaveBeenCalledWith('person:Alice');
  });
});

function Harness({ onValidQueryChange }: { onValidQueryChange: (value: string) => void }) {
  const [value, setValue] = useState('');
  const commit = useCallback(onValidQueryChange, [onValidQueryChange]);
  return <StructuredSearch onChange={setValue} onValidQueryChange={commit} value={value} />;
}

function jsonResponse(body: unknown) {
  return new Response(JSON.stringify(body), {
    status: 200,
    headers: { 'Content-Type': 'application/json' }
  });
}
