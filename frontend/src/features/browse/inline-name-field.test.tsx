import { cleanup, render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { InlineNameField } from '@/features/browse/inline-name-field';

describe('InlineNameField', () => {
  afterEach(() => {
    cleanup();
  });

  it('commits a trimmed name on Enter', async () => {
    const onCommit = vi.fn().mockResolvedValue(undefined);
    const user = userEvent.setup();
    render(
      <InlineNameField ariaLabel="Folder name" initialValue="Events" onCancel={vi.fn()} onCommit={onCommit} />
    );

    const input = screen.getByRole('textbox', { name: 'Folder name' });
    await user.clear(input);
    await user.type(input, 'Parties{Enter}');
    expect(onCommit).toHaveBeenCalledWith('Parties');
  });

  it('cancels on Escape without committing', async () => {
    const onCommit = vi.fn();
    const onCancel = vi.fn();
    const user = userEvent.setup();
    render(
      <InlineNameField ariaLabel="Library name" initialValue="fambam" onCancel={onCancel} onCommit={onCommit} />
    );

    await user.type(screen.getByRole('textbox', { name: 'Library name' }), 'Changed{Escape}');
    expect(onCommit).not.toHaveBeenCalled();
    expect(onCancel).toHaveBeenCalled();
  });
});
