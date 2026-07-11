import { cleanup, render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { Hash, Images } from 'lucide-react';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { OrganizerSidebar } from '@/features/organizer/organizer-sidebar';

describe('OrganizerSidebar', () => {
  afterEach(() => {
    cleanup();
  });

  it('renders rows, add action, and selection', async () => {
    const user = userEvent.setup();
    const onSelect = vi.fn();
    const onCreate = vi.fn();
    render(
      <OrganizerSidebar
        activeRowId="a1"
        addLabel="Add album"
        collapsed={false}
        isLowResolution={false}
        onCollapsedChange={() => undefined}
        onCreate={onCreate}
        onSelect={onSelect}
        rowIcon={Images}
        rows={[
          { id: 'a1', label: 'Summer', count: 24 },
          { id: 'a2', label: 'Family', count: 81 }
        ]}
        title="Albums"
      />
    );

    expect(screen.getByRole('navigation', { name: 'Albums' })).toBeInTheDocument();
    expect(screen.getByText('(24)')).toBeInTheDocument();
    await user.click(screen.getByRole('button', { name: /Family/i }));
    expect(onSelect).toHaveBeenCalledWith('a2');
    await user.click(screen.getByRole('button', { name: 'Add album' }));
    expect(screen.getByRole('textbox', { name: 'New album name' })).toBeInTheDocument();
  });

  it('creates a row from the inline name field', async () => {
    const user = userEvent.setup();
    const onCreate = vi.fn().mockResolvedValue(undefined);
    render(
      <OrganizerSidebar
        activeRowId={null}
        addLabel="Add tag"
        collapsed={false}
        createPlaceholder="Tag name"
        isLowResolution={false}
        onCollapsedChange={() => undefined}
        onCreate={onCreate}
        onSelect={() => undefined}
        rowIcon={Hash}
        rows={[]}
        title="Tags"
      />
    );

    await user.click(screen.getByRole('button', { name: 'Add tag' }));
    const input = screen.getByRole('textbox', { name: 'New tag name' });
    await user.type(input, 'Favourite{Enter}');
    expect(onCreate).toHaveBeenCalledWith('Favourite');
  });

  it('renames a row from the hover edit control', async () => {
    const user = userEvent.setup();
    const onRename = vi.fn().mockResolvedValue(undefined);
    render(
      <OrganizerSidebar
        activeRowId="t1"
        collapsed={false}
        isLowResolution={false}
        onCollapsedChange={() => undefined}
        onRename={onRename}
        onSelect={() => undefined}
        rowIcon={Hash}
        rows={[{ id: 't1', label: 'Favourite', count: 3 }]}
        title="Tags"
      />
    );

    await user.click(screen.getByRole('button', { name: 'Rename Favourite' }));
    const input = screen.getByRole('textbox', { name: 'Tag name' });
    await user.clear(input);
    await user.type(input, 'Family{Enter}');
    expect(onRename).toHaveBeenCalledWith('t1', 'Family');
  });

  it('uses the row image when one is available', () => {
    const { container } = render(
      <OrganizerSidebar
        activeRowId="a1"
        collapsed={false}
        isLowResolution={false}
        onCollapsedChange={() => undefined}
        onSelect={() => undefined}
        rowIcon={Images}
        rows={[{ id: 'a1', label: 'Summer', count: 24, imageUrl: '/api/assets/asset-1/thumbnail?size=tiny' }]}
        title="Albums"
      />
    );

    expect(container.querySelector('img')).toHaveAttribute('src', '/api/assets/asset-1/thumbnail?size=tiny');
  });

  it('cancels create on Escape', async () => {
    const user = userEvent.setup();
    const onCreate = vi.fn();
    render(
      <OrganizerSidebar
        activeRowId={null}
        addLabel="Add album"
        collapsed={false}
        isLowResolution={false}
        onCollapsedChange={() => undefined}
        onCreate={onCreate}
        onSelect={() => undefined}
        rowIcon={Images}
        rows={[]}
        title="Albums"
      />
    );

    await user.click(screen.getByRole('button', { name: 'Add album' }));
    await user.type(screen.getByRole('textbox', { name: 'New album name' }), 'Draft{Escape}');
    expect(onCreate).not.toHaveBeenCalled();
    expect(screen.queryByRole('textbox', { name: 'New album name' })).not.toBeInTheDocument();
  });

  it('collapses via hide control', async () => {
    const user = userEvent.setup();
    const onCollapsedChange = vi.fn();
    render(
      <OrganizerSidebar
        activeRowId={null}
        collapsed={false}
        isLowResolution={false}
        onCollapsedChange={onCollapsedChange}
        onSelect={() => undefined}
        rowIcon={Hash}
        rows={[{ id: 't1', label: 'Favourite', count: 3 }]}
        title="Tags"
      />
    );
    await user.click(screen.getByRole('button', { name: 'Hide tags' }));
    expect(onCollapsedChange).toHaveBeenCalledWith(true);
  });

  it('renders nothing when collapsed', () => {
    const { container } = render(
      <OrganizerSidebar
        activeRowId={null}
        collapsed
        isLowResolution={false}
        onCollapsedChange={() => undefined}
        onSelect={() => undefined}
        rowIcon={Hash}
        rows={[{ id: 't1', label: 'Favourite', count: 3 }]}
        title="Tags"
      />
    );
    expect(container).toBeEmptyDOMElement();
  });
});
