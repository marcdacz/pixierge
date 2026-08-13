import type { ReactNode } from 'react';
import { Bell, ChevronDown, Search, SlidersHorizontal } from 'lucide-react';
import { Button } from '@/components/ui/button';
import { cn } from '@/lib/utils';

export type TopBarProps = {
  logo: ReactNode;
  searchLabel?: string;
  searchPlaceholder?: string;
  profile?: {
    name: string;
    initials: string;
  };
  onFilterClick?: () => void;
  onNotificationsClick?: () => void;
  onProfileClick?: () => void;
  className?: string;
};

export function TopBar({
  className,
  logo,
  onFilterClick,
  onNotificationsClick,
  onProfileClick,
  profile,
  searchLabel = 'Search library',
  searchPlaceholder = 'Search'
}: TopBarProps) {
  return (
    <header
      className={cn(
        'grid h-[var(--topbar-height)] grid-cols-[auto_minmax(0,1fr)_auto] items-center gap-3 border-b border-border bg-surface/92 px-3 md:grid-cols-[minmax(12rem,1fr)_minmax(20rem,40rem)_minmax(12rem,1fr)] md:gap-4 md:pl-8 md:pr-4',
        className
      )}
    >
      {logo}
      <div className="flex min-w-0 items-center gap-2">
        <label className="flex h-11 min-w-0 flex-1 items-center gap-3 rounded-lg border border-focus bg-surface-sunken px-4 text-sm text-content-muted">
          <Search aria-hidden="true" className="h-5 w-5 shrink-0" />
          <input
            aria-label={searchLabel}
            className="min-w-0 flex-1 bg-transparent text-content outline-none"
            placeholder={searchPlaceholder}
          />
        </label>
        <button
          aria-label="Search filters"
          className="hidden h-10 w-10 shrink-0 place-items-center rounded-lg border border-border bg-surface-raised text-content-muted transition-colors hover:bg-surface-hover hover:text-content focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-focus sm:grid"
          onClick={onFilterClick}
          type="button"
        >
          <SlidersHorizontal aria-hidden="true" className="h-5 w-5" />
        </button>
      </div>
      <div className="flex min-w-0 items-center justify-end gap-3">
        <Button variant="ghost" size="icon" aria-label="Notifications" onClick={onNotificationsClick}>
          <Bell aria-hidden="true" className="h-5 w-5" />
        </Button>
        {profile ? (
          <button
            className="hidden h-11 items-center gap-3 rounded-lg pl-2 pr-0 text-sm font-semibold text-content transition-colors hover:text-content-muted focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-focus sm:flex"
            onClick={onProfileClick}
            type="button"
          >
            <span className="flex h-8 w-8 items-center justify-center rounded-full bg-info text-xs text-content-inverse">
              {profile.initials}
            </span>
            <span>{profile.name}</span>
            <ChevronDown aria-hidden="true" className="h-4 w-4 text-content-muted" />
          </button>
        ) : null}
      </div>
    </header>
  );
}
