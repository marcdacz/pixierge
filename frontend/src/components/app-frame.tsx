import { FolderOpen, Images, Search, Star, LogOut, Settings, Tags, UserCircle, type LucideIcon } from 'lucide-react';
import type { ReactNode } from 'react';
import { logout, type AuthResponse, type LibrarySummary } from '@/api';
import type { AppView } from '@/App';
import { Button } from '@/components/ui/button';
import { PixiergeLogoMark } from '@/components/pixierge-logo-mark';
import { AppRail, TopBar as DesignTopBar } from '@/design-system/patterns';
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuTrigger
} from '@/components/ui/dropdown-menu';
import { Tooltip, TooltipContent, TooltipProvider, TooltipTrigger } from '@/components/ui/tooltip';
import { cn } from '@/lib/utils';
import { ScanActivityButton } from '@/features/scans/scan-activity-button';
import { StructuredSearch } from '@/features/search/structured-search';

type AppFrameProps = {
  auth: AuthResponse;
  canOpenSettings?: boolean;
  children: ReactNode;
  contentMode?: 'constrained' | 'edge';
  currentView: AppView;
  libraries: LibrarySummary[];
  onLibrarySearchChange: (value: string) => void;
  onLibrarySearchQueryChange: (value: string) => void;
  onLogout: () => void;
  onOpenSettings: () => void;
  searchPlaceholder?: string;
  searchValue: string;
  showLibrarySearch?: boolean;
  onViewChange: (view: AppView) => void;
};

const primaryNav: NavItemDefinition[] = [
  { icon: Star, label: 'Starred', view: 'starred' },
  { icon: Images, label: 'Albums', view: 'albums' },
  { icon: Tags, label: 'Tags', view: 'tags' }
];

const utilityNav: NavItemDefinition[] = [{ icon: Settings, label: 'Settings', view: 'settings' }];

type NavItemDefinition = {
  icon: LucideIcon;
  label: string;
  view: AppView;
};

export function AppFrame({
  auth,
  canOpenSettings = true,
  children,
  contentMode = 'constrained',
  currentView,
  onLibrarySearchChange,
  onLibrarySearchQueryChange,
  onLogout,
  onOpenSettings,
  searchPlaceholder = 'Search',
  searchValue,
  showLibrarySearch = false,
  onViewChange
}: AppFrameProps) {
  const utilityItems = canOpenSettings ? utilityNav : [];

  return (
    <TooltipProvider>
      <main
        className={cn(
          'grid h-dvh grid-rows-[var(--topbar-height)_minmax(0,1fr)] overflow-hidden overscroll-none bg-canvas text-content'
        )}
      >
        <AppTopBar
          auth={auth}
          canOpenSettings={canOpenSettings}
          onLibrarySearchChange={onLibrarySearchChange}
          onLibrarySearchQueryChange={onLibrarySearchQueryChange}
          onLogout={onLogout}
          onOpenSettings={onOpenSettings}
          searchPlaceholder={searchPlaceholder}
          searchValue={searchValue}
          showLibrarySearch={showLibrarySearch}
        />
        <div className="grid min-h-0 grid-cols-[var(--rail-width)_minmax(0,1fr)] overflow-hidden">
          <AppRail
            items={[
              navItem({
                active: currentView === 'search',
                item: { icon: Search, label: 'Search', view: 'search' },
                onViewChange
              }),
              navItem({
                active: currentView === 'libraries',
                item: { icon: FolderOpen, label: 'Libraries', view: 'libraries' },
                onViewChange
              }),
              ...primaryNav.map((item) => navItem({ active: currentView === item.view, item, onViewChange }))
            ]}
            onSettingsSelect={utilityItems.length > 0 ? () => onViewChange('settings') : undefined}
            settingsLabel={utilityItems.length > 0 ? 'Settings' : null}
            settingsSelected={currentView === 'settings'}
            settingsTestId="primary-nav-settings"
          />

          <section className="min-h-0 min-w-0 overflow-hidden overscroll-none">
            <div
              className={cn(
                'h-full min-h-0 overflow-hidden p-4 md:p-6',
                contentMode === 'constrained' ? 'mx-auto max-w-6xl' : 'max-w-none'
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

function navItem({
  active,
  item,
  onViewChange
}: {
  active: boolean;
  item: NavItemDefinition;
  onViewChange: (view: AppView) => void;
}) {
  return {
    icon: item.icon,
    label: item.label,
    selected: active,
    testId: `primary-nav-${item.view}`,
    onSelect: () => onViewChange(item.view)
  };
}

function AppTopBar({
  auth,
  canOpenSettings,
  onLibrarySearchChange,
  onLibrarySearchQueryChange,
  onLogout,
  onOpenSettings,
  searchPlaceholder,
  searchValue,
  showLibrarySearch
}: {
  auth: AuthResponse;
  canOpenSettings: boolean;
  onLibrarySearchChange: (value: string) => void;
  onLibrarySearchQueryChange: (value: string) => void;
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
    <DesignTopBar
      logo={<PixiergeLogoMark className="w-36 min-w-0 md:w-44" showWordmark />}
      search={
        <StructuredSearch
          disabled={!showLibrarySearch}
          onChange={onLibrarySearchChange}
          onValidQueryChange={onLibrarySearchQueryChange}
          placeholder={searchPlaceholder}
          value={searchValue}
        />
      }
      utilityActions={
        <>
          <ScanActivityButton canOpenSettings={canOpenSettings} onOpenSettings={onOpenSettings} />
          {canOpenSettings && (
            <Tooltip>
              <TooltipTrigger asChild>
                <Button
                  aria-label="Settings"
                  data-testid="app-shell-settings"
                  size="icon"
                  type="button"
                  variant="ghost"
                  onClick={onOpenSettings}
                >
                  <Settings className="h-4 w-4" aria-hidden />
                </Button>
              </TooltipTrigger>
              <TooltipContent>Settings</TooltipContent>
            </Tooltip>
          )}
        </>
      }
      profileMenu={
        <DropdownMenu>
          <DropdownMenuTrigger asChild>
            <Button aria-label="Profile" data-testid="app-shell-profile" size="icon" type="button" variant="ghost">
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
      }
    />
  );
}
