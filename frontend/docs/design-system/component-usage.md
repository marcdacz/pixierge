# Pixierge Component Inventory And Storybook Plan

This inventory maps the approved design direction to existing code and identifies reusable components to migrate or create. Do not move code until a product slice adopts the new system.

## Existing Primitives To Migrate

- Button: keep semantic variants, add loading and pressed state documentation.
- Badge: add semantic status variants, 10px chip radius, removable chip composition.
- Input: preserve accessible labels, add invalid, disabled, focused, and loading-adjacent examples.
- Label: keep native label behavior.
- Card: narrow usage to individual repeated items, dialogs, and genuinely framed tools.
- DropdownMenu: keep Radix primitive, document keyboard behavior and selected/disabled states.
- Tooltip: keep Radix primitive, use for icon-only or unclear controls.
- Table: use for settings, backup, audit, scheduler, catalog, and future metadata tables.
- Alert: map to status tokens and include icon/text, not color alone.
- Toast: use for async completion/failure feedback.
- Skeleton: use for loading layout stability.
- Separator: use for lightweight grouping when spacing is not enough.

## Missing Or Expanded Primitives

- IconButton: button primitive with icon-only and icon-plus-label rail support.
- Link: semantic text/link treatment for inline navigation and external links.
- Spinner: small loading indicator for in-place async work.
- EmptyState: reusable empty/first-run state with icon, title, description, and optional action.
- Dialog: Radix-backed modal pattern for blocking/high-intent flows.
- Popover: lightweight contextual panel pattern.
- Tabs: keyboard-accessible tabs for photo/dashboard sections.
- Select or Combobox: shared filter/sort picker pattern.
- Checkbox: shared selection control for tables and bulk actions.
- Switch: shared boolean setting control.
- Field: label, help, validation, disabled, and error layout wrapper.

## Pixierge Patterns

App shell:

- TopBar: logo/brand, centered global search, adjacent search-filter action, notifications, profile menu trigger, and utility actions. It is a fixed app-shell pattern with a three-column layout: brand, search, account/status controls. Keyboard users can tab through search, filters, notifications, and profile in visual order.
- AppRail: permanently compact icon plus label navigation; no expand state. The selected item uses `aria-current="page"`, a semantic active surface, and the cyan left indicator measured from the approved north-star tile. Settings is a separate bottom-pinned action.
- ContextSidebar: folder/filter/settings navigation surface. Count pills and the `My folders` add action share a fixed right-edge grid track so quantities and creation controls align while labels truncate.
- PageHeader: title, count/supporting metadata, and right-aligned action cluster. Use it for dense app screens, keeping headings at or below the documented app scale.
- ToolbarViewToggle: equal-height grid/list view selector. Use `aria-pressed` to expose selected state and keep it grouped with the thumbnail-size controls.
- ThumbnailSizeControl: media zoom slider plus optional fullscreen action. It must always have a programmatic label and use immediate local feedback when wired to state.

Photo browsing:

- MediaGrid: dense adaptive grouped media layout with stable tile sizing and date/count headings.
- MediaTile: thumbnail surface with image overlay, selected state, accessible image label, and contextual action affordance.
- TimelineRail: right-side chronology scanner for years/months. The active month uses `aria-current="date"` and the cyan line/dot rhythm from the north-star contract.
- BulkActionBar: bottom selection surface with selected count, primary actions, destructive action, more action, and close/clear control. Keep Star as the first favoriting action in library selection contexts.
- MetadataPanel: file, capture, identity, duplicate, source, and extraction status.

Search and organization:

- GlobalSearch: app-level search entry.
- StructuredSearch: tokenized query input with suggestions and chips.
- FilterBar: filter chips, sort menu, view toggle, add filter action.
- AssignmentPicker: combobox-style picker for albums/tags/starred flows.
- ContextMenu: asset action menu with keyboard and pointer support.

Settings and management:

- SettingsSection: heading, description, actions, and content region.
- StatusSummary: compact stat item with status semantics.
- DataTable: sortable/filterable table surface.
- SchedulerRow: job state, run action, enable switch, schedule edit.
- AuditLogFilters: reusable management filter layout.

## Component State Requirements

Interactive components should document:

- default
- hover
- focus-visible
- pressed or selected
- disabled
- loading or pending when async
- error or invalid when applicable
- empty and success states when the component owns data feedback

Async actions should prevent duplicate submission and provide local pending state immediately.

## Storybook Foundations

Add stories under `frontend/stories/foundations/`:

- Color roles: dark and light semantic colors, status surfaces, focus treatment.
- Type scale: six sizes, weights, line heights, label usage.
- Spacing: 4-point rhythm, dense app grouping, sidebar/content spacing.
- Radius and elevation: rail, surface, dialog, popover, chip, media tile.
- Icon usage: Lucide sizing, stroke, labels, tooltip expectations.
- Focus states: keyboard-visible focus across buttons, fields, tiles, tabs.
- Image overlay contrast: metadata over uncontrolled imagery.
- Responsive layout: topbar, rail, context sidebar, and media grid behavior.

## Storybook Component Stories

Initial stories should cover:

- Button and IconButton
- Badge and removable chip
- Input, Field, Select/Combobox, Checkbox, Switch
- Tooltip, DropdownMenu, Popover, Dialog, Tabs
- Alert, Toast, Skeleton, EmptyState
- Table and DataTable
- TopBar, AppRail, ContextSidebar, PageHeader
- FilterBar, StructuredSearch, AssignmentPicker
- MediaGrid, MediaTile, TimelineRail, BulkActionBar, MetadataPanel

## Storybook Screen Stories

Add deterministic screen stories:

- Library photo browser, desktop dark.
- Library photo browser, mobile signed-in.
- Library empty state.
- Library loading state.
- Library error state.
- Search with chips and filters.
- Settings dashboard/table surface.
- Assignment picker open over selected media.

Stories must use deterministic mock data and app-owned review assets. They must not require a backend, user session, or local photo library.

## Migration Mapping

| Current code                                   | New system destination                                             | Notes                                                                                    |
| ---------------------------------------------- | ------------------------------------------------------------------ | ---------------------------------------------------------------------------------------- |
| `src/components/app-frame.tsx`                 | `src/design-system/patterns/AppShell`                              | Split topbar, rail, and content frame as part of app-frame migration.                    |
| `src/features/browse/browse-sidebar.tsx`       | `src/design-system/patterns/ContextSidebar`                        | Keep overlay behavior for mobile; align width/radii tokens.                              |
| `src/features/library/photo-grid.tsx`          | `src/design-system/patterns/MediaGrid`, `MediaTile`, `MediaViewer` | Preserve keyboard/open/selection behavior and tests.                                     |
| `src/features/search/structured-search.tsx`    | `src/design-system/patterns/StructuredSearch`                      | Keep parser behavior; restyle chips/suggestions through tokens.                          |
| `src/features/organizer/assignment-picker.tsx` | `src/design-system/patterns/AssignmentPicker`                      | Keep combobox keyboard behavior; replace local modal styling with Dialog/Popover tokens. |
| `src/components/ui/*`                          | `src/design-system/components/*`                                   | Move only when materially changed or adopted by a migrated slice.                        |
