const colorRoles = [
  { className: 'bg-canvas text-content border-border', label: 'Canvas' },
  { className: 'bg-canvas-subtle text-content border-border', label: 'Canvas subtle' },
  { className: 'bg-surface text-content border-border', label: 'Surface' },
  { className: 'bg-surface-raised text-content border-border-strong', label: 'Raised' },
  { className: 'bg-surface-sunken text-content border-border-subtle', label: 'Sunken' },
  { className: 'bg-action text-action-content border-action', label: 'Action' }
];

const statusRoles = [
  { className: 'border-info bg-info-surface text-info-content', label: 'Info' },
  { className: 'border-success bg-success-surface text-success-content', label: 'Success' },
  { className: 'border-warning bg-warning-surface text-warning-content', label: 'Warning' },
  { className: 'border-danger bg-danger-surface text-danger-content', label: 'Danger' },
  { className: 'border-processing bg-processing-surface text-processing-content', label: 'Processing' },
  { className: 'border-unavailable bg-unavailable-surface text-unavailable-content', label: 'Unavailable' }
];

const layoutTokens = [
  ['Top bar', 'var(--topbar-height)'],
  ['Collapsed rail', 'var(--rail-width)'],
  ['Context sidebar', 'var(--context-sidebar-width)'],
  ['Media gap', 'var(--media-grid-gap)']
];

export function TokenPreview() {
  return (
    <section className="grid gap-6 bg-canvas p-6 font-sans text-content">
      <div className="grid gap-2">
        <p className="text-xs font-semibold tracking-label text-content-subtle uppercase">Pixierge tokens</p>
        <h2 className="text-2xl font-semibold leading-tight text-content">Foundation preview</h2>
        <p className="max-w-2xl text-sm leading-readable text-content-muted">
          This surface uses semantic tokens only, making theme roles, status treatment, focus, and component geometry
          easy to inspect in one place.
        </p>
      </div>

      <div className="grid gap-3 md:grid-cols-3">
        {colorRoles.map((role) => (
          <div
            className={`grid min-h-24 content-between rounded-lg border p-4 shadow-[var(--shadow-raised)] ${role.className}`}
            key={role.label}
          >
            <span className="text-sm font-semibold">{role.label}</span>
            <span className="text-xs text-content-muted">Semantic surface</span>
          </div>
        ))}
      </div>

      <div className="flex flex-wrap gap-2">
        {statusRoles.map((role) => (
          <span
            className={`inline-flex min-h-6 items-center rounded-chip border px-2 py-0.5 text-xs font-semibold ${role.className}`}
            key={role.label}
          >
            {role.label}
          </span>
        ))}
      </div>

      <div className="grid gap-3 rounded-lg border border-border bg-surface p-4">
        <div className="grid gap-1">
          <h3 className="text-base font-semibold text-content">App geometry</h3>
          <p className="text-sm text-content-muted">
            North Star shell dimensions use named layout tokens instead of screen-local magic values.
          </p>
        </div>
        <div className="grid gap-2 md:grid-cols-4">
          {layoutTokens.map(([label, value]) => (
            <div className="grid gap-1 rounded-md border border-border bg-surface-raised p-3" key={label}>
              <span className="text-xs font-semibold tracking-label text-content-subtle uppercase">{label}</span>
              <code className="text-sm text-content">{value}</code>
            </div>
          ))}
        </div>
      </div>

      <div className="grid gap-3 rounded-xl border border-border bg-surface-raised p-4 shadow-[var(--shadow-floating)]">
        <label className="grid gap-2 text-sm font-medium text-content">
          Search preview
          <input
            className="h-10 rounded-md border border-border-strong bg-surface-sunken px-3 text-sm text-content outline-none transition-colors placeholder:text-content-subtle focus-visible:border-focus focus-visible:ring-2 focus-visible:ring-focus-shadow"
            placeholder="Search your library"
          />
        </label>
        <div className="flex flex-wrap gap-2">
          <button
            className="h-10 rounded-md bg-action px-4 text-sm font-semibold text-action-content transition-colors hover:bg-action-hover active:bg-action-pressed"
            type="button"
          >
            Primary action
          </button>
          <button
            className="h-10 rounded-md border border-border bg-surface px-4 text-sm font-semibold text-content transition-colors hover:bg-surface-hover"
            type="button"
          >
            Secondary action
          </button>
        </div>
      </div>
    </section>
  );
}
