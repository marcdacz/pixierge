import '@testing-library/jest-dom/vitest';
import { cleanup, render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { InlineEditableTitle } from '@/features/library/inline-editable-title';

describe('InlineEditableTitle', () => {
  afterEach(() => {
    cleanup();
  });

  it('keeps the rename control collapsed until the name is hovered', () => {
    render(<InlineEditableTitle aria-label="Album name" onSave={vi.fn()} value="Summer" />);

    const rename = screen.getByRole('button', { name: 'Rename album name' });
    expect(rename).toHaveClass('w-0');
    expect(rename).toHaveClass('opacity-0');
    expect(rename).toHaveClass('group-hover:w-10');
    expect(rename).toHaveClass('group-hover:opacity-100');
  });

  it('enters edit mode from the pencil and saves on Enter', async () => {
    const onSave = vi.fn().mockResolvedValue(undefined);
    const user = userEvent.setup();
    render(<InlineEditableTitle aria-label="Album name" onSave={onSave} value="Summer" />);

    await user.click(screen.getByRole('button', { name: 'Rename album name' }));
    const input = screen.getByRole('textbox', { name: 'Album name' });
    await user.clear(input);
    await user.type(input, 'Winter{Enter}');

    expect(onSave).toHaveBeenCalledWith('Winter');
  });

  it('cancels on Escape without saving', async () => {
    const onSave = vi.fn().mockResolvedValue(undefined);
    const user = userEvent.setup();
    render(<InlineEditableTitle aria-label="Tag name" onSave={onSave} value="Favourite" />);

    await user.click(screen.getByRole('button', { name: 'Rename tag name' }));
    const input = screen.getByRole('textbox', { name: 'Tag name' });
    await user.clear(input);
    await user.type(input, 'Changed{Escape}');

    expect(onSave).not.toHaveBeenCalled();
    expect(screen.getByRole('heading', { name: 'Favourite' })).toBeInTheDocument();
  });
});
