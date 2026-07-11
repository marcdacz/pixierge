import { cleanup, render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { AssignmentPicker } from '@/features/organizer/assignment-picker';

describe('AssignmentPicker', () => {
  afterEach(() => {
    cleanup();
  });

  it('filters via autocomplete, creates inline, and applies on Enter', async () => {
    const user = userEvent.setup();
    const onCreate = vi.fn(async (name: string) => ({ id: 'new', name }));
    const onApply = vi.fn(async () => undefined);
    const onClose = vi.fn();

    render(
      <AssignmentPicker
        createVerb="album"
        destinations={[{ id: '1', name: 'Family Events' }, { id: '2', name: 'Travel' }]}
        onApply={onApply}
        onClose={onClose}
        onCreate={onCreate}
        open
        title="Add to albums"
      />
    );

    const input = screen.getByLabelText('Search add to albums');
    expect(screen.queryByRole('listbox')).not.toBeInTheDocument();

    await user.type(input, 'fam');
    expect(screen.getByRole('option', { name: /Family Events/i })).toBeInTheDocument();
    expect(screen.queryByRole('option', { name: /Travel/i })).not.toBeInTheDocument();

    await user.click(screen.getByRole('option', { name: /Family Events/i }));
    expect(screen.getByText('Family Events')).toBeInTheDocument();
    expect(screen.queryByRole('listbox')).not.toBeInTheDocument();

    await user.type(input, 'Best of 2026');
    await user.click(screen.getByRole('option', { name: /Create album “Best of 2026”/i }));
    expect(onCreate).toHaveBeenCalledWith('Best of 2026');
    expect(screen.getByText('Best of 2026')).toBeInTheDocument();

    await user.keyboard('{Enter}');
    expect(onApply).toHaveBeenCalledWith(expect.arrayContaining(['new', '1']));
    expect(onClose).toHaveBeenCalled();
  });

  it('cancels without applying', async () => {
    const user = userEvent.setup();
    const onApply = vi.fn(async () => undefined);
    const onClose = vi.fn();
    render(
      <AssignmentPicker
        createVerb="tag"
        destinations={[{ id: '1', name: 'Favourite' }]}
        onApply={onApply}
        onClose={onClose}
        onCreate={async (name) => ({ id: 'x', name })}
        open
        title="Add tags"
      />
    );
    await user.click(screen.getByLabelText('Search add tags'));
    await user.keyboard('{Escape}');
    expect(onApply).not.toHaveBeenCalled();
    expect(onClose).toHaveBeenCalled();
  });

  it('removes a selected pill', async () => {
    const user = userEvent.setup();
    render(
      <AssignmentPicker
        createVerb="album"
        destinations={[{ id: '1', name: 'Travel' }]}
        onApply={async () => undefined}
        onClose={() => undefined}
        onCreate={async (name) => ({ id: 'x', name })}
        open
        title="Add to albums"
      />
    );

    await user.type(screen.getByLabelText('Search add to albums'), 'Tra');
    await user.click(screen.getByRole('option', { name: /Travel/i }));
    await user.click(screen.getByRole('button', { name: 'Remove Travel' }));
    expect(screen.queryByText('Travel')).not.toBeInTheDocument();
  });
});
