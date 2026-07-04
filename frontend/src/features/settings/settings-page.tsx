import { Archive, Blocks, SlidersHorizontal } from 'lucide-react';
import { useState, type ComponentType } from 'react';
import { cn } from '@/lib/utils';

type SettingsView = 'configuration' | 'plugins' | 'backups';

type SettingsItem = {
  description: string;
  icon: ComponentType<{ className?: string }>;
  label: string;
  view: SettingsView;
};

const settingsItems: SettingsItem[] = [
  {
    description: 'System configuration controls will be added here.',
    icon: SlidersHorizontal,
    label: 'Configuration',
    view: 'configuration'
  },
  {
    description: 'Plugin installation and lifecycle controls will live here.',
    icon: Blocks,
    label: 'Plugins',
    view: 'plugins'
  },
  {
    description: 'Backup health, schedules, and restore checks will live here.',
    icon: Archive,
    label: 'Backups',
    view: 'backups'
  }
];

export function SettingsPage() {
  const [currentView, setCurrentView] = useState<SettingsView>('configuration');
  const currentItem = settingsItems.find((item) => item.view === currentView) ?? settingsItems[0];

  return (
    <div className="grid min-h-full gap-8 lg:grid-cols-[var(--settings-nav-width)_minmax(0,1fr)]">
      <aside className="border-b border-border pb-4 lg:border-b-0 lg:border-r lg:pb-0 lg:pr-4">
        <div className="mb-4 grid gap-1">
          <h1 className="text-2xl font-semibold text-foreground">Settings</h1>
          <p className="text-sm text-muted-foreground">Configure Pixierge operations.</p>
        </div>

        <nav aria-label="Settings" className="grid gap-1">
          {settingsItems.map((item) => {
            const Icon = item.icon;
            const active = currentView === item.view;

            return (
              <button
                aria-current={active ? 'page' : undefined}
                className={cn(
                  'flex h-10 items-center gap-3 rounded-md px-3 text-left text-sm font-medium text-muted-foreground transition-colors hover:bg-muted hover:text-foreground',
                  active && 'bg-muted text-foreground'
                )}
                key={item.view}
                onClick={() => setCurrentView(item.view)}
                type="button"
              >
                <Icon className="h-4 w-4" aria-hidden />
                {item.label}
              </button>
            );
          })}
        </nav>
      </aside>

      <SettingsContent item={currentItem} />
    </div>
  );
}

function SettingsContent({ item }: { item: SettingsItem }) {
  const Icon = item.icon;

  return (
    <section aria-labelledby="settings-page-title" className="grid content-start gap-8">
      <div className="grid gap-2">
        <div className="flex items-center gap-3">
          <Icon className="h-5 w-5 text-muted-foreground" aria-hidden />
          <h2 id="settings-page-title" className="text-2xl font-semibold text-foreground">
            {item.label}
          </h2>
        </div>
        <p className="max-w-2xl text-sm text-muted-foreground">{item.description}</p>
      </div>

      <EmptySettingsPage label={item.label} />
    </section>
  );
}

function EmptySettingsPage({ label }: { label: string }) {
  return (
    <div className="grid min-h-96 place-items-center">
      <div className="grid max-w-md justify-items-center gap-2 text-center">
        <p className="text-sm font-medium text-foreground">{label} is empty</p>
        <p className="text-sm text-muted-foreground">Controls for this area will be added in a later slice.</p>
      </div>
    </div>
  );
}
