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
import { useState } from 'react';
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

type AppFrameProps = {
  auth: AuthResponse;
  children: ReactNode;
  contentMode?: 'constrained' | 'edge';
  currentView: AppView;
  libraries: LibrarySummary[];
  onLogout: () => void;
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
  onLogout,
  onViewChange
}: AppFrameProps) {
  const [navExpanded, setNavExpanded] = useState(false);

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
          navExpanded={navExpanded}
          onLogout={onLogout}
          onSettings={() => onViewChange('settings')}
        />
        <div className={cn('grid min-h-0', navExpanded ? shellContentColumns.expanded : shellContentColumns.collapsed)}>
          <aside className="flex min-h-0 flex-col border-r border-border bg-sidebar px-3 py-4">
            <nav aria-label="Primary" className="grid gap-2">
              <LibraryNav
                active={currentView === 'libraries'}
                expanded={navExpanded}
                libraries={libraries}
                onSelect={() => onViewChange('libraries')}
              />
              {primaryNav.map((item) => (
                <NavItem
                  active={currentView === item.view}
                  expanded={navExpanded}
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
                    expanded={navExpanded}
                    key={item.view}
                    item={item}
                    onSelect={() => onViewChange(item.view)}
                  />
                ))}
              </nav>
              <RailToggle expanded={navExpanded} onToggle={() => setNavExpanded((expanded) => !expanded)} />
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
  onLogout,
  onSettings
}: {
  auth: AuthResponse;
  navExpanded: boolean;
  onLogout: () => void;
  onSettings: () => void;
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
          <Input aria-label="Search" className="h-9 bg-surface pl-9" placeholder="Search library..." />
        </label>
      </div>
      <div className="flex items-center gap-2 px-4">
        <Tooltip>
          <TooltipTrigger asChild>
            <Button aria-label="Settings" size="icon" type="button" variant="ghost" onClick={onSettings}>
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
  libraries,
  onSelect
}: {
  active: boolean;
  expanded: boolean;
  libraries: LibrarySummary[];
  onSelect: () => void;
}) {
  const librariesWithSources = libraries.filter((library) => library.status === 'active' && library.sourceCount > 0);

  return (
    <div className="grid gap-1">
      <NavItem
        active={active}
        expanded={expanded}
        item={{ icon: FolderOpen, label: 'Libraries', view: 'libraries' }}
        onSelect={onSelect}
      />
      {expanded && librariesWithSources.length > 0 && (
        <div className="grid gap-1 pl-4">
          {librariesWithSources.map((library) => (
            <button
              className="grid min-h-8 grid-cols-[minmax(0,1fr)_auto] items-center gap-2 rounded-md px-3 text-left text-xs text-muted-foreground transition-colors hover:bg-sidebar-accent hover:text-sidebar-foreground"
              key={library.id}
              onClick={onSelect}
              type="button"
            >
              <span className="truncate">{library.name}</span>
              <span className="shrink-0 rounded-md bg-muted px-1.5 py-0.5 font-medium text-foreground">
                {formatSourceCount(library.sourceCount)}
              </span>
            </button>
          ))}
        </div>
      )}
    </div>
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

function formatSourceCount(count: number) {
  return `${count} ${count === 1 ? 'source' : 'sources'}`;
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
