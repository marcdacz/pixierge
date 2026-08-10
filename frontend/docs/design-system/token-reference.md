# Pixierge Token Reference

Phase 1 tokens are implemented in `frontend/src/styles.css` and exposed to Tailwind v4 through `@theme`.

## Naming Layers

- Primitive tokens describe raw scales: `--px-violet-600`, `--space-4`, `--radius-sm`.
- Semantic utilities describe product meaning: `bg-canvas`, `bg-action`, `text-content`, `border-border`, `ring-focus`.
- Component tokens are allowed only when a component has a repeated, stable need: `--rail-width`, `--media-tile-min`, `--topbar-height`.

Feature code should consume semantic or component tokens. Raw primitive values should stay in the token layer.

## Primitive Color Seeds

Pixierge brand colors should use an owned violet-cyan-rose direction:

- Violet: primary brand, active rail states, primary action, focus partner.
- Cyan/teal: selection, focus, active media state, progress, link/trust accents.
- Rose: destructive or critical accents.
- Neutral: canvas, surfaces, borders, text, overlays.

Proposed primitive families:

- `--px-violet-50` through `--px-violet-950`
- `--px-cyan-50` through `--px-cyan-950`
- `--px-rose-50` through `--px-rose-950`
- `--px-green-50` through `--px-green-950`
- `--px-yellow-50` through `--px-yellow-950`
- `--px-neutral-0` through `--px-neutral-1000`

## Semantic Color Tokens

Canvas and surfaces:

- `bg-canvas`: app background.
- `bg-canvas-subtle`: rail/topbar background variation.
- `bg-surface`: default contained surface.
- `bg-surface-raised`: popovers, dialogs, floating bars.
- `bg-surface-sunken`: media canvas, recessed panes.
- `bg-surface-hover`: hover surface.
- `bg-surface-active`: selected navigation or active row surface.

Content:

- `text-content`: primary text/icons.
- `text-content-muted`: secondary text/icons.
- `text-content-subtle`: low-emphasis labels.
- `text-content-inverse`: text on strong actions or media overlays.

Borders and focus:

- `border-border`: default separator.
- `border-border-strong`: prominent divider or table boundary.
- `border-border-subtle`: low-emphasis separators.
- `ring-focus`: visible focus ring.
- `ring-focus-shadow`: translucent focus glow.

Actions:

- `--color-action`: primary action or active brand accent.
- `--color-action-hover`
- `--color-action-pressed`
- `--color-action-content`
- `--color-link`
- `--color-link-hover`

Status:

- `--color-status-info`
- `--color-status-success`
- `--color-status-warning`
- `--color-status-danger`
- `--color-status-processing`
- `--color-status-unavailable`

Every status token has a paired content utility and a subtle surface utility, for example:

- `bg-danger-surface`
- `border-danger`
- `text-danger-content`

## Dark Theme Intent

The selected north star uses:

- aubergine-black canvas rather than pure black;
- slightly lighter topbar and sidebar surfaces;
- cyan active media/focus outlines;
- violet selected navigation and action accents;
- rose destructive action;
- low-opacity borders and progressively lighter surfaces for depth.

## Light Theme Intent

Light mode should keep the same brand relationships:

- near-white canvas with faint violet/cyan tint;
- white or lightly tinted surfaces;
- violet active nav and primary action;
- cyan focus, selection, and progress;
- rose destructive states;
- neutral text with strong contrast.

## Typography Tokens

Use one primary sans-serif family:

- `--font-sans`: Inter, ui-sans-serif, system-ui, -apple-system, BlinkMacSystemFont, Segoe UI, sans-serif.

Limit production type to six sizes:

- `--font-size-xs`: 12px
- `--font-size-sm`: 14px
- `--font-size-md`: 16px
- `--font-size-lg`: 18px
- `--font-size-xl`: 20px
- `--font-size-2xl`: 24px

Line heights:

- `--line-height-tight`: 1.2
- `--line-height-snug`: 1.35
- `--line-height-normal`: 1.5
- `--line-height-readable`: 1.6

Tracking:

- `--letter-spacing-normal`: 0
- `--letter-spacing-display`: -0.02em, for rare display moments only
- `--letter-spacing-label`: 0.02em, for uppercase utility labels only

Do not apply negative tracking to dense labels, body text, controls, or table text.

## Spacing Tokens

Use a 4-point scale:

- `--space-0`: 0
- `--space-1`: 4px
- `--space-2`: 8px
- `--space-3`: 12px
- `--space-4`: 16px
- `--space-6`: 24px
- `--space-8`: 32px
- `--space-10`: 40px
- `--space-12`: 48px
- `--space-16`: 64px

Repeated exceptions must become documented component tokens.

## Radius Tokens

- `--radius-none`: 0
- `--radius-xs`: 4px
- `--radius-sm`: 6px
- `--radius-md`: 8px
- `--radius-chip`: 10px
- `--radius-lg`: 12px
- `--radius-xl`: 16px, reserved for larger workspace surfaces and dialogs
- `--radius-full`: 999px

Use `--radius-chip` for small tags/chips.

## Elevation Tokens

Light mode:

- `--shadow-raised`: low-opacity neutral shadow, blur at least 24px.
- `--shadow-floating`: popovers, dialogs, bulk action bars.

Dark mode:

- Prefer `--color-surface-raised`, borders, and overlay scrims.
- Shadows should be subtle and never hard black outlines.

## Layout Tokens

- `--topbar-height`: 72px
- `--rail-width`: 88px, compact icon plus label rail
- `--context-sidebar-width`: 272px
- `--settings-sidebar-width`: 272px
- `--content-max-width`: use only for constrained admin/documentation pages
- `--media-grid-gap`: 4px or 8px depending on density
- `--media-tile-min`: component-controlled thumbnail minimum

Breakpoints should align with Tailwind defaults unless a real Pixierge workflow requires a custom breakpoint.

## Motion Tokens

- `--duration-fast`: 120ms
- `--duration-normal`: 180ms
- `--duration-slow`: 300ms
- `--easing-standard`: cubic-bezier(0.2, 0, 0, 1)

Respect reduced-motion preferences. Motion should clarify state changes, not decorate routine browsing.

## Tailwind Delivery

Phase 1 exposes semantic tokens through Tailwind v4 `@theme`, then migration slices should prefer classes such as:

- `bg-canvas`
- `bg-surface`
- `bg-surface-raised`
- `text-content`
- `text-content-muted`
- `border-border`
- `ring-focus`
- `bg-action`
- `text-action-content`

Avoid recurring arbitrary values once a token exists.

## Legacy Aliases

The existing utilities remain available while feature slices migrate:

- `bg-background`
- `text-foreground`
- `bg-muted`
- `text-muted-foreground`
- `bg-primary`
- `text-primary-foreground`
- `bg-sidebar`
- `text-sidebar-foreground`
- `bg-sidebar-accent`
- `border-input`
- `ring-ring`

Do not add new code against these aliases when a new semantic utility expresses the intent more clearly.
