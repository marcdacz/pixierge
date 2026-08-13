# Pixierge Design System Migration Log

This log records design-system migration decisions and completed slices.

## Phase 0

- Design direction approved on 2026-08-10.
- Public design-system docs created for principles, tokens, components, accessibility, and migration tracking.
- Local-only evidence and exploration artifacts are excluded from git.

## Current Open Decisions

- Storybook review target remains local/build-artifact only until hosted review tooling is selected.
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
- Added `TokenPreview` as a token-only foundation surface for Storybook reuse.
- Added token contract tests for key dark and light contrast combinations.

No broad feature migration has started yet.

Validation:

- `npm run build` passes.
- `npm run test:unit` passes.
- Targeted ESLint for changed TSX files passes.
- Full `npm run lint` is still blocked by pre-existing app-wide lint findings outside this slice.

### Storybook Contract

- Installed Storybook for the existing Vite, React, and TypeScript frontend.
- Added `.storybook/main.ts` and `.storybook/preview.ts` using the app's global styles and accessibility addon.
- Added local and build commands: `npm run storybook` and `npm run build-storybook`.
- Added foundation stories for dark/light tokens, type hierarchy, spacing rhythm, elevation, icon scale, focus states, image-overlay contrast, and responsive shell layout.
- Added primitive component stories for buttons, badges, inputs, alerts, tables, skeleton loading, empty table state, and tooltip behavior.
- Added north-star library/app-shell screen stories in dark and light themes using deterministic mock data and no backend dependency.
- Aligned primitive defaults and Storybook examples with the approved north-star shell: semantic action/surface tokens, compact status chips, selected toolbar state, zoom control, rail icon states, selection action bar, and app-geometry token preview.
- Ignored generated `frontend/storybook-static/` output in git and ESLint.

Validation:

- `npm run build-storybook` passes.
- `npm run build` passes.
- `npm run test:unit` passes.
- Targeted ESLint for the new Storybook/config/design-system files passes.
- Full `npm run lint` is still blocked by pre-existing app-wide findings outside this slice.

Planned order:

1. Tokens and global foundations.
2. Storybook foundation stories.
3. App shell and library/photo browser.
4. Search and structured filters.
5. Organizer selection and assignment flows.
6. Albums, tags, and starred views.
7. Settings and administration surfaces.

### Phase 3 Shell Navigation Patterns

- Added `TopBar`, `AppRail`, and `ContextSidebar` under `frontend/src/design-system/patterns/`.
- Added `PageHeader`, `ToolbarViewToggle`, `ThumbnailSizeControl`, `MediaGrid`, `MediaTile`, `TimelineRail`, and `BulkActionBar` under `frontend/src/design-system/patterns/`.
- Updated the north-star library screen story to compose extracted patterns instead of local one-off shell/media markup.
- Preserved the approved shell contracts: centered global search with adjacent filter action, compact app rail with icon plus label, selected rail indicator geometry, bottom-pinned settings, and context-sidebar right-edge count/add alignment.
- Preserved the approved media-browser contracts: equal-height toolbar controls, selected grid state, zoom slider, timeline year/month line/dot rhythm, and bottom bulk action bar with Star rather than Heart.
- Tightened shell accessibility: `TopBar` search now has a programmatic label, profile identity is passed by the caller instead of mocked by default, and context-sidebar folder group headers are not rendered as fake disclosure buttons.
- Added dedicated `Components/Patterns` Storybook stories for shell navigation and media-browser patterns.
- Added focused unit coverage for TopBar actions, selected rail state, pinned settings geometry, context-sidebar count/add alignment, media selected state, timeline active state, toolbar selection, zoom callbacks, and bulk action callbacks.
- Added a north-star Storybook render smoke test so missing imports and other runtime story failures are caught by the unit gate.
- Added a pattern-story render smoke test so dedicated pattern Storybook stories also catch runtime reference failures.

Validation:

- `npm run test:unit -- src/design-system/patterns/navigation-shell.test.tsx src/design-system/patterns/media-browser-patterns.test.tsx src/design-system/patterns/library-north-star-story.test.tsx src/design-system/patterns/patterns-story.test.tsx` passes.
- `npm run test:unit` passes.
- `npm run build` passes.
- `npm run build-storybook` passes.
- Targeted ESLint for the pattern files and updated Storybook stories passes.
- Targeted Prettier check for the pattern files, updated Storybook stories, and design-system docs passes.
- Built Storybook north-star story was reviewed through local desktop and mobile screenshots; mobile topbar and bulk-action-bar responsive sizing were adjusted during review.

## Temporary Exceptions

None recorded yet.
