import { Pencil, Plus, type LucideIcon } from 'lucide-react';
import { useState } from 'react';
import { Button } from '@/components/ui/button';
import { BrowseSidebar, BROWSE_LAYOUT_HEIGHT_CLASS } from '@/features/browse/browse-sidebar';
import { InlineNameField } from '@/features/browse/inline-name-field';
import { readAssetDragData } from '@/features/organizer/drag-types';
import { cn } from '@/lib/utils';

export { BROWSE_LAYOUT_HEIGHT_CLASS };
export {
  BrowseSidebarShowControl,
  BROWSE_SIDEBAR_COLLAPSED_KEYS,
  useBrowseSidebarState
} from '@/features/browse/browse-sidebar';

export type OrganizerRow = { id: string; label: string; count?: number; imageUrl?: string | null };

type OrganizerSidebarProps = {
  title: string;
  rows: OrganizerRow[];
  activeRowId: string | null;
  onSelect: (id: string) => void;
  rowIcon: LucideIcon;
  onCreate?: (name: string) => Promise<void> | void;
  onRename?: (id: string, name: string) => Promise<void> | void;
  addLabel?: string;
  createPlaceholder?: string;
  loading?: boolean;
  error?: string | null;
  emptyLabel?: string;
  onDropAssets?: (rowId: string, assetIds: string[], items: Array<{ assetId: string; sourceLibraryId: string }>) => void;
  collapsed: boolean;
  onCollapsedChange: (collapsed: boolean) => void;
  isLowResolution: boolean;
};

const ROW_BASE_PADDING_REM = 0.5;

export function OrganizerSidebar({
  title,
  rows,
  activeRowId,
  onSelect,
  rowIcon: RowIcon,
  onCreate,
  onRename,
  addLabel = 'Add',
  createPlaceholder,
  loading,
  error,
  emptyLabel,
  onDropAssets,
  collapsed,
  onCollapsedChange,
  isLowResolution
}: OrganizerSidebarProps) {
  const [dropTargetId, setDropTargetId] = useState<string | null>(null);
  const [creating, setCreating] = useState(false);
  const [renamingId, setRenamingId] = useState<string | null>(null);

  if (collapsed) {
    return null;
  }

  const singular = title.endsWith('s') ? title.slice(0, -1) : title;
  const namePlaceholder = createPlaceholder ?? `${singular} name`;

  return (
    <BrowseSidebar
      headerActions={
        onCreate ? (
          <Button
            aria-label={addLabel}
            onClick={() => {
              setRenamingId(null);
              setCreating(true);
            }}
            size="icon"
            type="button"
            variant="ghost"
          >
            <Plus className="h-4 w-4" aria-hidden />
          </Button>
        ) : undefined
      }
      isLowResolution={isLowResolution}
      onCloseOverlay={() => onCollapsedChange(true)}
      onHide={() => onCollapsedChange(true)}
      title={title}
    >
      {loading && <p className="px-2 py-2 text-sm text-muted-foreground">Loading {title.toLowerCase()}...</p>}
      {error && <p className="px-2 py-2 text-sm text-destructive">{error}</p>}
      {!loading && !error && rows.length === 0 && !creating && (
        <p className="px-2 py-2 text-sm text-muted-foreground">{emptyLabel ?? `No ${title.toLowerCase()} yet.`}</p>
      )}
      <nav
        aria-label={title}
        className={cn('flex min-h-0 flex-1 flex-col gap-0.5 overflow-y-auto', isLowResolution ? 'pr-0' : 'pr-1')}
      >
        {creating && onCreate && (
          <div
            className="flex min-h-7 items-center gap-2 rounded-md bg-muted pr-2 text-sm text-foreground"
            style={{ paddingLeft: `${ROW_BASE_PADDING_REM}rem` }}
          >
            <RowIcon className="h-4 w-4 shrink-0 text-muted-foreground" aria-hidden />
            <InlineNameField
              ariaLabel={`New ${singular.toLowerCase()} name`}
              onCancel={() => setCreating(false)}
              onCommit={async (name) => {
                await onCreate(name);
                setCreating(false);
              }}
              placeholder={namePlaceholder}
            />
          </div>
        )}
        {rows.map((row) => {
          const active = row.id === activeRowId;
          const count = row.count ?? 0;
          const renaming = renamingId === row.id;
          return (
            <div
              className={cn(
                'group flex min-h-7 items-center gap-1 rounded-md pr-2 text-sm text-muted-foreground transition-colors hover:bg-muted hover:text-foreground',
                active && 'bg-muted text-foreground',
                dropTargetId === row.id && 'ring-2 ring-muted-foreground'
              )}
              key={row.id}
              onDragEnter={() => setDropTargetId(row.id)}
              onDragLeave={() => setDropTargetId((current) => (current === row.id ? null : current))}
              onDragOver={(event) => {
                if (onDropAssets) {
                  event.preventDefault();
                  event.dataTransfer.dropEffect = 'copy';
                }
              }}
              onDrop={(event) => {
                event.preventDefault();
                setDropTargetId(null);
                const payload = readAssetDragData(event.dataTransfer);
                if (payload && onDropAssets) {
                  onDropAssets(row.id, payload.assetIds, payload.items);
                }
              }}
              style={{ paddingLeft: `${ROW_BASE_PADDING_REM}rem` }}
            >
              {renaming && onRename ? (
                <>
                  <RowIcon className="h-4 w-4 shrink-0" aria-hidden />
                  <InlineNameField
                    ariaLabel={`${singular} name`}
                    initialValue={row.label}
                    onCancel={() => setRenamingId(null)}
                    onCommit={async (name) => {
                      await onRename(row.id, name);
                      setRenamingId(null);
                    }}
                    placeholder={namePlaceholder}
                  />
                </>
              ) : (
                <div className="flex min-w-0 flex-1 items-center gap-0.5">
                  <button
                    className="flex min-h-7 min-w-0 items-center gap-2 overflow-hidden text-left"
                    onClick={() => onSelect(row.id)}
                    type="button"
                  >
                    <OrganizerRowIcon icon={RowIcon} imageUrl={row.imageUrl} />
                    <span className="truncate">
                      {row.label} <span className="text-xs tabular-nums">({count})</span>
                    </span>
                  </button>
                  {onRename && (
                    <Button
                      aria-label={`Rename ${row.label}`}
                      className="size-6 shrink-0 opacity-0 transition-opacity group-hover:opacity-100 focus-visible:opacity-100 group-focus-within:opacity-100"
                      onClick={(event) => {
                        event.stopPropagation();
                        setCreating(false);
                        setRenamingId(row.id);
                      }}
                      size="icon"
                      type="button"
                      variant="ghost"
                    >
                      <Pencil className="h-3 w-3" aria-hidden />
                    </Button>
                  )}
                </div>
              )}
            </div>
          );
        })}
      </nav>
    </BrowseSidebar>
  );
}

function OrganizerRowIcon({ icon: RowIcon, imageUrl }: { icon: LucideIcon; imageUrl?: string | null }) {
  const [failedUrl, setFailedUrl] = useState<string | null>(null);
  const showImage = imageUrl && imageUrl !== failedUrl;

  if (showImage) {
    return (
      <img
        alt=""
        aria-hidden
        className="h-5 w-5 shrink-0 rounded-sm object-cover"
        onError={() => setFailedUrl(imageUrl)}
        src={imageUrl}
      />
    );
  }

  return <RowIcon className="h-4 w-4 shrink-0" aria-hidden />;
}
