import { Settings, type LucideIcon } from 'lucide-react';
import { cn } from '@/lib/utils';

export type AppRailItem = {
  icon: LucideIcon;
  label: string;
  selected?: boolean;
  onSelect?: () => void;
};

export type AppRailProps = {
  items: AppRailItem[];
  settingsLabel?: string;
  onSettingsSelect?: () => void;
  className?: string;
};

export function AppRail({ className, items, onSettingsSelect, settingsLabel = 'Settings' }: AppRailProps) {
  return (
    <nav
      aria-label="Primary"
      className={cn(
        'flex h-[calc(100vh-var(--topbar-height))] min-h-0 flex-col overflow-hidden border-r border-border bg-canvas-subtle px-2 py-4',
        className
      )}
    >
      <div className="grid min-h-0 flex-1 content-start gap-4 overflow-y-auto pb-3">
        {items.map(({ icon: Icon, label, onSelect, selected }) => (
          <button
            aria-current={selected ? 'page' : undefined}
            className={cn(
              'relative grid h-[3.75rem] place-items-center gap-0.5 rounded-md text-[11px] font-semibold leading-none transition-colors hover:bg-surface-hover hover:text-content focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-focus',
              selected ? 'bg-surface-active text-content' : 'text-content-muted'
            )}
            key={label}
            onClick={onSelect}
            type="button"
          >
            {selected ? <span className="absolute top-1 bottom-1 left-0 w-0.5 rounded-full bg-info" /> : null}
            <Icon aria-hidden="true" className="h-6 w-6" strokeWidth={1.85} />
            <span>{label}</span>
          </button>
        ))}
      </div>
      <button
        className="mt-auto grid h-[3.75rem] shrink-0 place-items-center gap-0.5 rounded-md text-[11px] font-semibold leading-none text-content-muted transition-colors hover:bg-surface-hover hover:text-content focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-focus"
        onClick={onSettingsSelect}
        type="button"
      >
        <Settings aria-hidden="true" className="h-6 w-6" strokeWidth={1.85} />
        <span>{settingsLabel}</span>
      </button>
    </nav>
  );
}
