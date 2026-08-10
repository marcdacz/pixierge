# Pixierge Design System Migration Log

This log records design-system migration decisions and completed slices.

## Phase 0

- Design direction approved on 2026-08-10.
- Public design-system docs created for principles, tokens, components, accessibility, and migration tracking.
- Local-only evidence and exploration artifacts are excluded from git.

## Current Open Decisions

- Storybook review target: local only, CI artifact, Chromatic, or another internal host.
- Whether the first production slice ships dark-only UI with light tokens prepared, or both themes visible immediately.
- Mobile signed-in baseline capture approach.
- Final production logo source and asset format.

## Migration Slices

### Tokens And Global Foundations

- Added semantic Tailwind token layer in `frontend/src/styles.css`.
- Added primitive Pixierge color scales for violet, cyan, rose, green, yellow, and neutral.
- Added semantic dark and light theme variables for canvas, surfaces, content, borders, focus, action, and status.
- Added typography, spacing, radius, motion, z-index, and shell layout tokens.
- Preserved existing legacy utility aliases so current feature screens can migrate incrementally.
- Added global selection, reduced-motion, and focus-visible foundations.
- Moved shared `Badge` and `Alert` status styling onto semantic status utilities.
- Added `frontend/src/design-system/tokens/README.md` as the target structure marker.

No broad feature migration has started yet.

Planned order:

1. Tokens and global foundations.
2. Storybook foundation stories.
3. App shell and library/photo browser.
4. Search and structured filters.
5. Organizer selection and assignment flows.
6. Albums, tags, and starred views.
7. Settings and administration surfaces.

## Temporary Exceptions

None recorded yet.
