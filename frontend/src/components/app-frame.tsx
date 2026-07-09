import {
  ChevronLeft,
  ChevronRight,
  FolderOpen,
  Images,
  LogOut,
  Search,
  Settings,
  UserCircle
} from 'lucide-react';
import type { ComponentType, ReactNode } from 'react';
import { useEffect, useState } from 'react';
import { logout, type AuthResponse, type LibrarySummary } from '@/api';
import type { AppView } from '@/App';
import { Button } from '@/components/ui/button';
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuTrigger
} from '@/components/ui/dropdown-menu';
import { Input } from '@/components/ui/input';
import { Separator } from '@/components/ui/separator';
import {
  Tooltip,
  TooltipContent,
  TooltipProvider,
  TooltipTrigger
} from '@/components/ui/tooltip';
import { cn } from '@/lib/utils';
import { ScanActivityButton } from '@/features/scans/scan-activity-button';

type AppFrameProps = {
  auth: AuthResponse;
  children: ReactNode;
  contentMode?: 'constrained' | 'edge';
  currentView: AppView;
  libraries: LibrarySummary[];
  onLibrarySearchChange: (value: string) => void;
  onLogout: () => void;
  onOpenSettings: () => void;
  searchPlaceholder?: string;
  searchValue: string;
  showLibrarySearch?: boolean;
  onViewChange: (view: AppView) => void;
};

const primaryNav: NavItemDefinition[] = [
  { icon: Images, label: 'Albums', view: 'albums' }
];

const utilityNav: NavItemDefinition[] = [
  { icon: Settings, label: 'Settings', view: 'settings' }
];

type NavItemDefinition = {
  icon: ComponentType<{ className?: string }>;
  label: string;
  view: AppView;
};

const shellLayoutTokens = [
  '[--shell-header-height:4rem]',
  '[--shell-rail-width:4.5rem]',
  '[--shell-sidebar-width:13.75rem]',
  '[--settings-nav-width:13.75rem]'
];

const shellContentColumns = {
  collapsed: 'grid-cols-[var(--shell-rail-width)_minmax(0,1fr)]',
  expanded: 'grid-cols-[var(--shell-sidebar-width)_minmax(0,1fr)]'
};

const shellHeaderColumns = {
  collapsed: 'grid-cols-[var(--shell-rail-width)_minmax(0,1fr)_auto]',
  expanded: 'grid-cols-[var(--shell-sidebar-width)_minmax(0,1fr)_auto]'
};

export function AppFrame({
  auth,
  children,
  contentMode = 'constrained',
  currentView,
  libraries,
  onLibrarySearchChange,
  onLogout,
  onOpenSettings,
  searchPlaceholder = 'Search library...',
  searchValue,
  showLibrarySearch = false,
  onViewChange
}: AppFrameProps) {
  const [navExpanded, setNavExpanded] = useState(false);
  const [navAutoCollapsed, setNavAutoCollapsed] = useState(false);
  const effectiveNavExpanded = navExpanded && !navAutoCollapsed;

  useEffect(() => {
    if (typeof window.matchMedia !== 'function') {
      setNavAutoCollapsed(false);
      return;
    }

    const mediaQuery = window.matchMedia('(max-width: 1023px)');
    const syncAutoCollapse = () => {
      setNavAutoCollapsed(mediaQuery.matches);
    };

    syncAutoCollapse();
    mediaQuery.addEventListener('change', syncAutoCollapse);
    return () => {
      mediaQuery.removeEventListener('change', syncAutoCollapse);
    };
  }, []);

  return (
    <TooltipProvider>
      <main
        className={cn(
          'grid min-h-screen grid-rows-[var(--shell-header-height)_minmax(0,1fr)] bg-background text-foreground',
          shellLayoutTokens
        )}
      >
        <TopBar
          auth={auth}
          navExpanded={effectiveNavExpanded}
          onLibrarySearchChange={onLibrarySearchChange}
          onLogout={onLogout}
          onOpenSettings={onOpenSettings}
          searchPlaceholder={searchPlaceholder}
          searchValue={searchValue}
          showLibrarySearch={showLibrarySearch}
        />
        <div
          className={cn(
            'grid min-h-0',
            effectiveNavExpanded ? shellContentColumns.expanded : shellContentColumns.collapsed
          )}
        >
          <aside className="flex min-h-0 flex-col border-r border-border bg-sidebar px-3 py-4">
            <nav aria-label="Primary" className="grid gap-2">
              <LibraryNav
                active={currentView === 'libraries'}
                expanded={effectiveNavExpanded}
                onSelect={() => onViewChange('libraries')}
              />
              {primaryNav.map((item) => (
                <NavItem
                  active={currentView === item.view}
                  expanded={effectiveNavExpanded}
                  key={item.view}
                  item={item}
                  onSelect={() => onViewChange(item.view)}
                />
              ))}
            </nav>

            <div className="mt-auto grid gap-3">
              <Separator />
              <nav aria-label="Utilities" className="grid gap-2">
                {utilityNav.map((item) => (
                  <NavItem
                    active={currentView === item.view}
                    expanded={effectiveNavExpanded}
                    key={item.view}
                    item={item}
                    onSelect={() => onViewChange(item.view)}
                  />
                ))}
              </nav>
              <RailToggle expanded={effectiveNavExpanded} onToggle={() => setNavExpanded((expanded) => !expanded)} />
            </div>
          </aside>

          <section className="min-w-0 overflow-auto">
            <div
              className={cn(
                'min-h-full w-full p-6 lg:p-8',
                contentMode === 'constrained' ? 'mx-auto max-w-6xl' : 'max-w-none lg:px-5'
              )}
            >
              {children}
            </div>
          </section>
        </div>
      </main>
    </TooltipProvider>
  );
}

function TopBar({
  auth,
  navExpanded,
  onLibrarySearchChange,
  onLogout,
  onOpenSettings,
  searchPlaceholder,
  searchValue,
  showLibrarySearch
}: {
  auth: AuthResponse;
  navExpanded: boolean;
  onLibrarySearchChange: (value: string) => void;
  onLogout: () => void;
  onOpenSettings: () => void;
  searchPlaceholder: string;
  searchValue: string;
  showLibrarySearch: boolean;
}) {
  async function submitLogout() {
    await logout(auth.csrfToken);
    onLogout();
  }

  return (
    <header
      className={cn(
        'grid items-center border-b border-border bg-background',
        navExpanded ? shellHeaderColumns.expanded : shellHeaderColumns.collapsed
      )}
    >
      <div className={cn('flex h-full items-center', navExpanded ? 'justify-start px-4' : 'justify-center')}>
        <span className="text-xs font-semibold uppercase tracking-normal text-foreground">pixierge</span>
      </div>
      <div className="flex justify-center px-4">
        <label className="relative block w-full max-w-3xl">
          <Search className="pointer-events-none absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-muted-foreground" aria-hidden />
          <Input
            aria-label="Search"
            className="h-9 bg-surface pl-9"
            disabled={!showLibrarySearch}
            onChange={(event) => onLibrarySearchChange(event.target.value)}
            placeholder={searchPlaceholder}
            value={searchValue}
          />
        </label>
      </div>
      <div className="flex items-center gap-2 px-4">
        <ScanActivityButton onOpenSettings={onOpenSettings} />
        <Tooltip>
          <TooltipTrigger asChild>
            <Button aria-label="Settings" size="icon" type="button" variant="ghost" onClick={onOpenSettings}>
              <Settings className="h-4 w-4" aria-hidden />
            </Button>
          </TooltipTrigger>
          <TooltipContent>Settings</TooltipContent>
        </Tooltip>

        <DropdownMenu>
          <DropdownMenuTrigger asChild>
            <Button aria-label="Profile" size="icon" type="button" variant="ghost">
              <UserCircle className="h-5 w-5" aria-hidden />
            </Button>
          </DropdownMenuTrigger>
          <DropdownMenuContent align="end">
            <DropdownMenuItem disabled>{auth.user.username}</DropdownMenuItem>
            <DropdownMenuItem onSelect={submitLogout}>
              <LogOut className="h-4 w-4" aria-hidden />
              Log out
            </DropdownMenuItem>
          </DropdownMenuContent>
        </DropdownMenu>
      </div>
    </header>
  );
}

function LibraryNav({
  active,
  expanded,
  onSelect
}: {
  active: boolean;
  expanded: boolean;
  onSelect: () => void;
}) {
  return (
    <NavItem
      active={active}
      expanded={expanded}
      item={{ icon: FolderOpen, label: 'Libraries', view: 'libraries' }}
      onSelect={onSelect}
    />
  );
}

function NavItem({
  active,
  expanded,
  item,
  onSelect
}: {
  active: boolean;
  expanded: boolean;
  item: NavItemDefinition;
  onSelect: () => void;
}) {
  const Icon = item.icon;

  return (
    <Tooltip>
      <TooltipTrigger asChild>
        <button
          aria-label={item.label}
          aria-current={active ? 'page' : undefined}
          className={cn(
            'flex h-11 items-center rounded-md text-muted-foreground transition-colors hover:bg-sidebar-accent hover:text-sidebar-foreground',
            expanded ? 'w-full justify-start gap-3 px-3 text-sm font-medium' : 'w-11 justify-center',
            active && 'bg-sidebar-accent text-sidebar-foreground'
          )}
          onClick={onSelect}
          type="button"
        >
          <Icon className="h-5 w-5" aria-hidden />
          {expanded && <span>{item.label}</span>}
        </button>
      </TooltipTrigger>
      {!expanded && <TooltipContent side="right">{item.label}</TooltipContent>}
    </Tooltip>
  );
}

function RailToggle({ expanded, onToggle }: { expanded: boolean; onToggle: () => void }) {
  const Icon = expanded ? ChevronLeft : ChevronRight;
  const label = expanded ? 'Collapse navigation' : 'Expand navigation';

  return (
    <Tooltip>
      <TooltipTrigger asChild>
        <button
          aria-label={label}
          className={cn(
            'flex h-11 items-center rounded-md text-muted-foreground transition-colors hover:bg-sidebar-accent hover:text-sidebar-foreground',
            expanded ? 'w-full justify-start gap-3 px-3 text-sm font-medium' : 'w-11 justify-center'
          )}
          onClick={onToggle}
          type="button"
        >
          <Icon className="h-5 w-5" aria-hidden />
          {expanded && <span>Collapse</span>}
        </button>
      </TooltipTrigger>
      {!expanded && <TooltipContent side="right">Expand navigation</TooltipContent>}
    </Tooltip>
  );
}
