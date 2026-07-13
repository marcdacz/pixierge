import { cleanup, render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { Images, Star } from 'lucide-react';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { AssetContextMenu } from '@/features/organizer/asset-context-menu';

describe('AssetContextMenu', () => {
  afterEach(() => {
    cleanup();
  });

  it('renders action labels with icons and invokes selection', async () => {
    const user = userEvent.setup();
    const onClose = vi.fn();
    const onStarred = vi.fn();
    const onAlbums = vi.fn();

    render(
      <AssetContextMenu
        actions={[
          { id: 'starred', icon: Star, label: 'Add to starred', onSelect: onStarred },
          { id: 'albums', icon: Images, label: 'Add to albums…', onSelect: onAlbums }
        ]}
        onClose={onClose}
        open
        x={40}
        y={60}
      />
    );

    const starredItem = screen.getByRole('menuitem', { name: 'Add to starred' });
    expect(starredItem.querySelector('svg')).not.toBeNull();
    expect(screen.getByRole('menuitem', { name: 'Add to albums…' }).querySelector('svg')).not.toBeNull();

    await user.click(starredItem);
    expect(onStarred).toHaveBeenCalledOnce();
    expect(onClose).toHaveBeenCalledOnce();
  });
});
