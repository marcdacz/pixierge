import { Settings, type LucideIcon } from 'lucide-react';
import { cn } from '@/lib/utils';

export type AppRailItem = {
  icon: LucideIcon;
  label: string;
  selected?: boolean;
  onSelect?: () => void;
  testId?: string;
};

export type AppRailProps = {
  items: AppRailItem[];
  settingsLabel?: string | null;
  onSettingsSelect?: () => void;
  settingsSelected?: boolean;
  settingsTestId?: string;
  className?: string;
};

const railButtonClass =
  'relative grid h-14 w-full min-w-0 place-items-center gap-0.5 rounded-md px-1.5 font-semibold tracking-normal transition-colors hover:bg-surface-hover hover:text-content focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-focus';
const selectedIndicatorClass = 'absolute top-2 bottom-2 left-1 w-0.5 rounded-full bg-info';
const railLabelClass = 'block w-full truncate text-center';
const railLabelStyle = { fontSize: '0.6875rem', lineHeight: '0.8125rem' };
const railIconClass = 'h-6 w-6';

export function AppRail({
  className,
  items,
  onSettingsSelect,
  settingsLabel = 'Settings',
  settingsSelected = false,
  settingsTestId
}: AppRailProps) {
  return (
    <nav
      aria-label="Primary"
      className={cn(
        'flex h-[calc(100vh-var(--topbar-height))] min-h-0 flex-col overflow-hidden border-r border-border bg-canvas-subtle px-1 py-4',
        className
      )}
    >
      <div className="grid min-h-0 flex-1 content-start gap-4 overflow-y-auto pb-3">
        {items.map(({ icon: Icon, label, onSelect, selected, testId }) => (
          <button
            aria-current={selected ? 'page' : undefined}
            className={cn(railButtonClass, selected ? 'bg-surface-active text-content' : 'text-content-muted')}
            key={label}
            onClick={onSelect}
            data-testid={testId}
            type="button"
          >
            {selected ? <span className={selectedIndicatorClass} /> : null}
            <Icon aria-hidden="true" className={railIconClass} strokeWidth={1.85} />
            <span className={railLabelClass} style={railLabelStyle}>
              {label}
            </span>
          </button>
        ))}
      </div>
      <div className="mt-auto grid gap-2">
        {settingsLabel ? (
          <nav aria-label="Utilities">
            <button
              aria-current={settingsSelected ? 'page' : undefined}
              className={cn(
                railButtonClass,
                'mt-auto shrink-0',
                settingsSelected ? 'bg-surface-active text-content' : 'text-content-muted'
              )}
              data-testid={settingsTestId}
              onClick={onSettingsSelect}
              type="button"
            >
              {settingsSelected ? <span className={selectedIndicatorClass} /> : null}
              <Settings aria-hidden="true" className={railIconClass} strokeWidth={1.85} />
              <span className={railLabelClass} style={railLabelStyle}>
                {settingsLabel}
              </span>
            </button>
          </nav>
        ) : null}
      </div>
    </nav>
  );
}
