import { Briefcase, Folder, Images, Search, Settings, Star, Tags } from 'lucide-react';
import type { Meta, StoryObj } from '@storybook/react-vite';
import { LibraryNorthStar } from '../screens/library-north-star.stories';

const meta = {
  title: 'Foundations/System Examples',
  parameters: {
    layout: 'fullscreen'
  }
} satisfies Meta;

export default meta;

type Story = StoryObj<typeof meta>;

const iconSet = [
  { icon: Folder, label: 'Library' },
  { icon: Search, label: 'Search' },
  { icon: Star, label: 'Starred' },
  { icon: Images, label: 'Albums' },
  { icon: Tags, label: 'Tags' },
  { icon: Briefcase, label: 'Jobs' },
  { icon: Settings, label: 'Settings' }
];

export const TypeAndSpacing: Story = {
  render: () => (
    <div className="grid gap-8 bg-canvas p-8 font-sans text-content">
      <section className="grid max-w-4xl gap-3">
        <p className="text-xs font-semibold tracking-label text-content-subtle uppercase">Typography</p>
        <h1 className="text-2xl font-semibold leading-tight tracking-display">Library review</h1>
        <p className="max-w-2xl text-sm leading-readable text-content-muted">
          Dense app surfaces keep headings compact, preserve scan paths, and reserve larger type for product moments.
        </p>
      </section>

      <section className="grid max-w-4xl gap-4 md:grid-cols-3">
        {[
          ['12px relationship', 'Eyebrow, heading, and support copy use tight vertical rhythm.'],
          ['24px grouping', 'Controls and summary regions separate without adding decorative containers.'],
          ['32px section step', 'Major work areas get enough breathing room for repeated use.']
        ].map(([title, copy]) => (
          <article className="grid gap-2 rounded-lg border border-border bg-surface p-4" key={title}>
            <h2 className="text-base font-semibold text-content">{title}</h2>
            <p className="text-sm leading-readable text-content-muted">{copy}</p>
          </article>
        ))}
      </section>
    </div>
  )
};

export const ElevationAndThemes: Story = {
  render: () => (
    <div className="grid gap-6 bg-canvas p-8 font-sans text-content lg:grid-cols-2">
      {[
        ['Dark', undefined],
        ['Light', 'light']
      ].map(([label, theme]) => (
        <section
          className="grid gap-4 rounded-xl border border-border bg-canvas-subtle p-4"
          data-theme={theme}
          key={label}
        >
          <div>
            <p className="text-xs font-semibold tracking-label text-content-subtle uppercase">{label} theme</p>
            <h2 className="text-xl font-semibold text-content">Elevation stack</h2>
          </div>
          <div className="grid gap-3">
            <div className="rounded-lg border border-border bg-surface-sunken p-4 text-sm text-content-muted">
              Sunken
            </div>
            <div className="rounded-lg border border-border bg-surface p-4 text-sm text-content">Surface</div>
            <div className="rounded-lg border border-border-strong bg-surface-raised p-4 text-sm text-content shadow-[var(--shadow-raised)]">
              Raised
            </div>
          </div>
        </section>
      ))}
    </div>
  )
};

export const IconAndFocusStates: Story = {
  render: () => (
    <div className="grid gap-8 bg-canvas p-8 font-sans text-content">
      <section className="grid gap-3">
        <p className="text-xs font-semibold tracking-label text-content-subtle uppercase">Icon scale</p>
        <div className="flex flex-wrap gap-3">
          {iconSet.map(({ icon: Icon, label }, index) => (
            <button
              className={`relative grid h-[3.75rem] w-20 place-items-center gap-0.5 rounded-md border border-border text-[11px] font-semibold leading-none transition-colors hover:bg-surface-hover hover:text-content focus-visible:ring-2 focus-visible:ring-focus ${
                index === 0 ? 'bg-surface-active text-content' : 'bg-surface text-content-muted'
              }`}
              key={label}
              type="button"
              aria-pressed={index === 0 ? 'true' : undefined}
            >
              {index === 0 ? <span className="absolute top-1 bottom-1 left-0 w-0.5 rounded-full bg-info" /> : null}
              <Icon aria-hidden="true" className="h-6 w-6" strokeWidth={1.85} />
              <span>{label}</span>
            </button>
          ))}
        </div>
      </section>

      <section className="flex flex-wrap gap-3">
        <button className="h-10 rounded-md bg-action px-4 text-sm font-semibold text-action-content" type="button">
          Default
        </button>
        <button
          className="h-10 rounded-md bg-action-hover px-4 text-sm font-semibold text-action-content ring-2 ring-focus ring-offset-2 ring-offset-canvas"
          type="button"
        >
          Focus visible
        </button>
        <button
          className="h-10 rounded-md bg-action-pressed px-4 text-sm font-semibold text-action-content"
          type="button"
        >
          Pressed
        </button>
        <button
          className="h-10 rounded-md bg-action px-4 text-sm font-semibold text-action-content opacity-60"
          disabled
          type="button"
        >
          Disabled
        </button>
      </section>
    </div>
  )
};

export const ImageOverlayContrast: Story = {
  render: () => (
    <div className="grid gap-4 bg-canvas p-8 font-sans text-content md:grid-cols-3">
      {[
        'from-cyan-300 via-violet-500 to-rose-500',
        'from-green-300 via-cyan-700 to-violet-950',
        'from-yellow-300 via-rose-600 to-neutral-950'
      ].map((tone, index) => (
        <article className={`relative aspect-[4/3] overflow-hidden rounded-lg bg-gradient-to-br ${tone}`} key={tone}>
          <div className="absolute inset-0 bg-gradient-to-t from-black/82 via-black/22 to-transparent" />
          <div className="absolute inset-x-0 bottom-0 grid gap-1 p-4 text-white">
            <span className="text-xs font-semibold uppercase tracking-label text-white/72">Album {index + 1}</span>
            <h2 className="text-lg font-semibold leading-tight">Protected overlay title</h2>
            <p className="text-sm text-white/80">Text remains legible on uncontrolled media.</p>
          </div>
        </article>
      ))}
    </div>
  )
};

export const ResponsiveLayout: Story = {
  render: () => <LibraryNorthStar />
};
