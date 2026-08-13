import { Grid3X3, List } from 'lucide-react';
import { cn } from '@/lib/utils';

export type ToolbarView = 'grid' | 'list';

export type ToolbarViewToggleProps = {
  value: ToolbarView;
  onChange?: (value: ToolbarView) => void;
  className?: string;
};

export function ToolbarViewToggle({ className, onChange, value }: ToolbarViewToggleProps) {
  return (
    <div
      aria-label="View mode"
      className={cn(
        'hidden h-12 items-center rounded-lg border border-border bg-surface-raised p-1 sm:flex',
        className
      )}
      role="group"
    >
      <ViewButton label="Grid view" selected={value === 'grid'} onClick={() => onChange?.('grid')}>
        <Grid3X3 aria-hidden="true" className="h-4 w-4" strokeWidth={2.4} />
      </ViewButton>
      <ViewButton label="List view" selected={value === 'list'} onClick={() => onChange?.('list')}>
        <List aria-hidden="true" className="h-4 w-4" />
      </ViewButton>
    </div>
  );
}

function ViewButton({
  children,
  label,
  onClick,
  selected
}: {
  children: React.ReactNode;
  label: string;
  onClick: () => void;
  selected: boolean;
}) {
  return (
    <button
      aria-label={label}
      aria-pressed={selected}
      className={cn(
        'grid h-10 w-10 place-items-center rounded-md text-content-muted transition-colors hover:bg-surface-hover hover:text-content focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-focus',
        selected &&
          'border border-border-strong bg-surface-sunken text-content shadow-[inset_0_0_0_1px_var(--color-border)]'
      )}
      onClick={onClick}
      type="button"
    >
      {children}
    </button>
  );
}
