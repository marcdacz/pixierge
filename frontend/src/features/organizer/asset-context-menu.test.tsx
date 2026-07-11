import { cleanup, render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { Heart, Images } from 'lucide-react';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { AssetContextMenu } from '@/features/organizer/asset-context-menu';

describe('AssetContextMenu', () => {
  afterEach(() => {
    cleanup();
  });

  it('renders action labels with icons and invokes selection', async () => {
    const user = userEvent.setup();
    const onClose = vi.fn();
    const onFavourites = vi.fn();
    const onAlbums = vi.fn();

    render(
      <AssetContextMenu
        actions={[
          { id: 'favourites', icon: Heart, label: 'Add to favourites', onSelect: onFavourites },
          { id: 'albums', icon: Images, label: 'Add to albums…', onSelect: onAlbums }
        ]}
        onClose={onClose}
        open
        x={40}
        y={60}
      />
    );

    const favouritesItem = screen.getByRole('menuitem', { name: 'Add to favourites' });
    expect(favouritesItem.querySelector('svg')).not.toBeNull();
    expect(screen.getByRole('menuitem', { name: 'Add to albums…' }).querySelector('svg')).not.toBeNull();

    await user.click(favouritesItem);
    expect(onFavourites).toHaveBeenCalledOnce();
    expect(onClose).toHaveBeenCalledOnce();
  });
});
