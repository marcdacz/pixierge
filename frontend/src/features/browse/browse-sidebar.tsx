import { ChevronsLeft, ChevronsRight } from 'lucide-react';
import { useEffect, useRef, useState, type DragEvent, type ReactNode } from 'react';
import { Button } from '@/components/ui/button';
import { Tooltip, TooltipContent, TooltipTrigger } from '@/components/ui/tooltip';
import { cn } from '@/lib/utils';

export const BROWSE_LAYOUT_HEIGHT_CLASS =
  'h-[calc(100vh-var(--shell-header-height)-3rem)] max-h-[calc(100vh-var(--shell-header-height)-3rem)] lg:h-[calc(100vh-var(--shell-header-height)-4rem)] lg:max-h-[calc(100vh-var(--shell-header-height)-4rem)]';

export const BROWSE_TREE_WIDTH_TOKEN = '[--browse-tree-width:16rem]';

export const BROWSE_SIDEBAR_COLLAPSED_KEYS = {
  libraries: 'pixierge.browseSidebar.libraries.collapsed',
  albums: 'pixierge.browseSidebar.albums.collapsed',
  tags: 'pixierge.browseSidebar.tags.collapsed',
  search: 'pixierge.browseSidebar.search.collapsed'
} as const;

const DRAG_EXPAND_DELAY_MS = 400;

export function readStoredBrowseSidebarCollapsed(storageKey: string): boolean {
  try {
    return window.localStorage.getItem(storageKey) === 'true';
  } catch {
    return false;
  }
}

export function writeStoredBrowseSidebarCollapsed(storageKey: string, collapsed: boolean): void {
  try {
    window.localStorage.setItem(storageKey, String(collapsed));
  } catch {
    // Ignore quota / private-mode failures; in-memory state still applies for the session.
  }
}

export function useBrowseSidebarState(storageKey: string) {
  const [collapsed, setCollapsedState] = useState(() => readStoredBrowseSidebarCollapsed(storageKey));
  const [isLowResolution, setIsLowResolution] = useState(false);
  const preferredCollapsedRef = useRef(collapsed);
  const isLowResolutionRef = useRef(false);
  const expandTimerRef = useRef<ReturnType<typeof setTimeout> | null>(null);

  useEffect(() => {
    const query = window.matchMedia?.('(max-width: 1023px)');
    if (!query) {
      return;
    }
    const sync = () => {
      const low = query.matches;
      isLowResolutionRef.current = low;
      setIsLowResolution(low);
      if (low) {
        setCollapsedState(true);
      } else {
        setCollapsedState(preferredCollapsedRef.current);
      }
    };
    sync();
    query.addEventListener('change', sync);
    return () => query.removeEventListener('change', sync);
  }, []);

  useEffect(
    () => () => {
      if (expandTimerRef.current) {
        clearTimeout(expandTimerRef.current);
      }
    },
    []
  );

  function setCollapsed(next: boolean) {
    setCollapsedState(next);
    if (!isLowResolutionRef.current) {
      preferredCollapsedRef.current = next;
      writeStoredBrowseSidebarCollapsed(storageKey, next);
    }
  }

  function clearExpandTimer() {
    if (expandTimerRef.current) {
      clearTimeout(expandTimerRef.current);
      expandTimerRef.current = null;
    }
  }

  function handleShowControlDragOver(event: DragEvent) {
    if (isLowResolutionRef.current) {
      return;
    }
    event.preventDefault();
    if (!expandTimerRef.current) {
      expandTimerRef.current = setTimeout(() => {
        setCollapsed(false);
        expandTimerRef.current = null;
      }, DRAG_EXPAND_DELAY_MS);
    }
  }

  return {
    collapsed,
    setCollapsed,
    isLowResolution,
    clearExpandTimer,
    handleShowControlDragOver
  };
}

type BrowseSidebarProps = {
  title: string;
  isLowResolution: boolean;
  onHide: () => void;
  onCloseOverlay: () => void;
  headerActions?: ReactNode;
  children: ReactNode;
};

export function BrowseSidebar({
  title,
  isLowResolution,
  onHide,
  onCloseOverlay,
  headerActions,
  children
}: BrowseSidebarProps) {
  return (
    <>
      {isLowResolution && (
        <button
          aria-label={`Close ${title.toLowerCase()}`}
          className="absolute inset-0 z-10 bg-background/40 backdrop-blur-[1px]"
          onClick={onCloseOverlay}
          type="button"
        />
      )}
      <aside
        className={cn(
          'flex min-h-0 flex-col',
          isLowResolution
            ? 'absolute inset-y-0 left-0 z-20 w-[min(20rem,85vw)] border-r border-border bg-background px-3 py-4 shadow-xl'
            : 'w-full border-b border-border pb-4 lg:w-[var(--browse-tree-width)] lg:border-b-0 lg:border-r lg:pb-0 lg:pr-4',
          BROWSE_TREE_WIDTH_TOKEN
        )}
      >
        <div className="mb-3 flex shrink-0 items-center justify-between gap-2">
          <h2 className="text-sm font-medium text-foreground">{title}</h2>
          <div className="flex shrink-0 items-center gap-1">
            {headerActions}
            <Button aria-label={`Hide ${title.toLowerCase()}`} onClick={onHide} size="icon" type="button" variant="ghost">
              <ChevronsLeft className="h-4 w-4" aria-hidden />
            </Button>
          </div>
        </div>
        {children}
      </aside>
    </>
  );
}

type BrowseSidebarShowControlProps = {
  title: string;
  onShow: () => void;
  onDragOver?: (event: DragEvent) => void;
  onDragLeave?: () => void;
};

export function BrowseSidebarShowControl({ title, onShow, onDragOver, onDragLeave }: BrowseSidebarShowControlProps) {
  const label = `Show ${title.toLowerCase()}`;

  return (
    <Tooltip>
      <TooltipTrigger asChild>
        <Button
          aria-label={label}
          onClick={onShow}
          onDragLeave={onDragLeave}
          onDragOver={onDragOver}
          size="icon"
          type="button"
          variant="ghost"
        >
          <ChevronsRight className="h-4 w-4" aria-hidden />
        </Button>
      </TooltipTrigger>
      <TooltipContent side="bottom">{label}</TooltipContent>
    </Tooltip>
  );
}
