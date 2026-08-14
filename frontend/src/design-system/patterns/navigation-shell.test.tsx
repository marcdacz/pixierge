import { render, screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { Folder, Search } from 'lucide-react';
import { describe, expect, it, vi } from 'vitest';
import { PixiergeLogoMark } from '@/components/pixierge-logo-mark';
import { AppRail, ContextSidebar, TopBar } from './index';

describe('TopBar', () => {
  it('keeps the Pixierge wordmark at compact app-shell scale', () => {
    render(<PixiergeLogoMark showWordmark />);

    expect(screen.getByText('PIXIERGE')).toHaveClass('text-lg');
    expect(document.querySelector('img')).toHaveClass('h-10');
  });

  it('keeps global search centered with adjacent filter, notifications, and profile controls', async () => {
    const user = userEvent.setup();
    const onFilterClick = vi.fn();
    const onNotificationsClick = vi.fn();
    const onProfileClick = vi.fn();

    render(
      <TopBar
        logo={<span>Pixierge</span>}
        onFilterClick={onFilterClick}
        onNotificationsClick={onNotificationsClick}
        onProfileClick={onProfileClick}
        profile={{ initials: 'JL', name: 'Jessica Lau' }}
        searchLabel="Search library"
        searchPlaceholder='Search "mountains"'
      />
    );

    expect(screen.getByText('Pixierge')).toBeInTheDocument();
    expect(screen.getByRole('textbox', { name: 'Search library' })).toHaveAttribute(
      'placeholder',
      'Search "mountains"'
    );

    await user.click(screen.getByRole('button', { name: 'Search filters' }));
    await user.click(screen.getByRole('button', { name: 'Notifications' }));
    await user.click(screen.getByRole('button', { name: /Jessica Lau/ }));

    expect(onFilterClick).toHaveBeenCalledTimes(1);
    expect(onNotificationsClick).toHaveBeenCalledTimes(1);
    expect(onProfileClick).toHaveBeenCalledTimes(1);
  });
});

describe('AppRail', () => {
  it('marks the selected rail item and keeps settings pinned at the bottom', () => {
    render(
      <AppRail
        items={[
          { icon: Folder, label: 'Library', selected: true },
          { icon: Search, label: 'Search' }
        ]}
      />
    );

    const nav = screen.getByRole('navigation', { name: 'Primary' });
    const selected = within(nav).getByRole('button', { name: 'Library' });
    const settings = within(nav).getByRole('button', { name: 'Settings' });
    const selectedLabel = within(selected).getByText('Library');

    expect(selected).toHaveAttribute('aria-current', 'page');
    expect(selected).toHaveClass('h-14', 'w-full', 'px-1.5', 'bg-surface-active');
    expect(nav).toHaveClass('px-1');
    expect(selected.querySelector('.absolute')).toHaveClass('top-2', 'bottom-2', 'left-1', 'bg-info');
    expect(selectedLabel).toHaveClass('w-full', 'truncate', 'text-center');
    expect(selectedLabel).toHaveStyle({ fontSize: '0.6875rem', lineHeight: '0.8125rem' });
    expect(settings).toHaveClass('mt-auto', 'h-14', 'shrink-0');
  });
});

describe('ContextSidebar', () => {
  it('aligns count pills and the folder add button on the same right-edge grid track', () => {
    render(
      <ContextSidebar
        title="Library"
        items={[{ icon: Folder, label: 'All folders', count: '12,842', selected: true }]}
        folderGroups={[
          {
            title: 'Projects',
            children: [{ label: 'Website Redesign', count: '1,128' }]
          }
        ]}
      />
    );

    const allFolders = screen.getByRole('button', { name: /All folders/ });
    const addFolder = screen.getByRole('button', { name: 'Add folder' });
    const childFolder = screen.getByRole('button', { name: /Website Redesign/ });

    expect(allFolders).toHaveClass('grid-cols-[1.25rem_minmax(0,1fr)_3.25rem]');
    expect(within(allFolders).getByText('12,842')).toHaveClass('justify-self-end');
    expect(screen.queryByRole('button', { name: 'Projects' })).not.toBeInTheDocument();
    expect(addFolder.parentElement).toHaveClass('grid-cols-[minmax(0,1fr)_3.25rem]');
    expect(addFolder).toHaveClass('justify-self-end');
    expect(childFolder).toHaveClass('grid-cols-[1.25rem_minmax(0,1fr)_3.25rem]');
    expect(within(childFolder).getByText('1,128')).toHaveClass('justify-self-end');
  });
});
