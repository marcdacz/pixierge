import { ChevronsLeft, ChevronsRight } from 'lucide-react';
import type { DragEvent, ReactNode } from 'react';
import { Button } from '@/components/ui/button';
import { cn } from '@/lib/utils';

export const BROWSE_LAYOUT_HEIGHT_CLASS =
  'h-[calc(100vh-var(--shell-header-height)-3rem)] max-h-[calc(100vh-var(--shell-header-height)-3rem)] lg:h-[calc(100vh-var(--shell-header-height)-4rem)] lg:max-h-[calc(100vh-var(--shell-header-height)-4rem)]';

export const BROWSE_TREE_WIDTH_TOKEN = '[--browse-tree-width:16rem]';

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
  return (
    <Button
      aria-label={`Show ${title.toLowerCase()}`}
      onClick={onShow}
      onDragLeave={onDragLeave}
      onDragOver={onDragOver}
      size="icon"
      type="button"
      variant="ghost"
    >
      <ChevronsRight className="h-4 w-4" aria-hidden />
    </Button>
  );
}
