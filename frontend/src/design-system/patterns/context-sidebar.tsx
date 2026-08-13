import { ChevronDown, Folder, type LucideIcon } from 'lucide-react';
import { cn } from '@/lib/utils';

export type ContextSidebarItem = {
  icon: LucideIcon;
  label: string;
  count: string;
  selected?: boolean;
  onSelect?: () => void;
};

export type ContextSidebarFolderGroup = {
  title: string;
  children: Array<{
    label: string;
    count: string;
    onSelect?: () => void;
  }>;
};

export type ContextSidebarProps = {
  title: string;
  items: ContextSidebarItem[];
  folderGroups?: ContextSidebarFolderGroup[];
  foldersTitle?: string;
  onAddFolder?: () => void;
  className?: string;
};

export function ContextSidebar({
  className,
  folderGroups = [],
  foldersTitle = 'My folders',
  items,
  onAddFolder,
  title
}: ContextSidebarProps) {
  return (
    <aside
      className={cn(
        'hidden min-h-0 overflow-auto border-r border-border bg-surface px-4 py-5 lg:grid lg:content-start lg:gap-6',
        className
      )}
    >
      <section className="grid gap-3">
        <h2 className="text-sm font-semibold text-content">{title}</h2>
        <div className="grid gap-1">
          {items.map(({ icon: Icon, label, count, onSelect, selected }) => (
            <button
              aria-current={selected ? 'page' : undefined}
              className={cn(
                'grid h-10 grid-cols-[1.25rem_minmax(0,1fr)_3.25rem] items-center gap-3 rounded-md px-3 text-sm transition-colors focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-focus',
                selected
                  ? 'bg-surface-active text-content'
                  : 'text-content-muted hover:bg-surface-hover hover:text-content'
              )}
              key={label}
              onClick={onSelect}
              type="button"
            >
              <Icon aria-hidden="true" className="h-4 w-4" />
              <span className="min-w-0 flex-1 truncate text-left">{label}</span>
              <CountPill>{count}</CountPill>
            </button>
          ))}
        </div>
      </section>

      {folderGroups.length > 0 ? (
        <section className="grid gap-3">
          <div className="grid h-10 grid-cols-[minmax(0,1fr)_3.25rem] items-center gap-3 px-3">
            <h3 className="text-sm font-semibold text-content">{foldersTitle}</h3>
            <button
              aria-label="Add folder"
              className="grid h-6 min-w-8 place-items-center justify-self-end rounded bg-surface-hover px-1.5 text-sm font-semibold leading-none text-content-subtle transition-colors hover:text-content focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-focus"
              onClick={onAddFolder}
              type="button"
            >
              +
            </button>
          </div>
          {folderGroups.map((group) => (
            <div className="grid gap-1" key={group.title}>
              <div className="flex h-9 items-center gap-2 rounded-md px-3 text-sm text-content-muted transition-colors hover:bg-surface-hover hover:text-content focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-focus">
                <ChevronDown aria-hidden="true" className="h-4 w-4" />
                <Folder aria-hidden="true" className="h-4 w-4" />
                <span>{group.title}</span>
              </div>
              {group.children.map(({ count, label, onSelect }) => (
                <button
                  className="ml-7 grid h-8 grid-cols-[1.25rem_minmax(0,1fr)_3.25rem] items-center gap-2 rounded-md px-3 text-sm text-content-muted transition-colors hover:bg-surface-hover hover:text-content focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-focus"
                  key={label}
                  onClick={onSelect}
                  type="button"
                >
                  <Folder aria-hidden="true" className="h-4 w-4" />
                  <span className="min-w-0 flex-1 truncate text-left">{label}</span>
                  <CountPill>{count}</CountPill>
                </button>
              ))}
            </div>
          ))}
        </section>
      ) : null}
    </aside>
  );
}

function CountPill({ children }: { children: string }) {
  return (
    <span className="justify-self-end rounded bg-surface-hover px-1.5 py-0.5 text-xs tabular-nums text-content-subtle">
      {children}
    </span>
  );
}
