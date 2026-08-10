# Pixierge Design System Principles

## Product Direction

Pixierge is a dense photo-management application. The approved direction is dark-first, photo-first, and operational. It preserves Pixierge's current information architecture while introducing a stronger product shell:

- app-level top navbar with Pixierge branding, global search, notifications, and user profile;
- permanently compact left rail with icon labels;
- second-level contextual sidebar for library, folders, albums, tags, search filters, and settings sections;
- dense media canvas with grouped chronology, selected state, timeline scanning, and bulk actions;
- Pixierge-owned violet, cyan/teal, and rose brand accents;
- token support for both dark and light themes.

## Direction Attributes

- Compact labeled navigation with strong active states.
- Clear separation between global navigation and contextual workspace navigation.
- Friendly product typography with readable tabs, filters, sort controls, and management tables.
- Photo-first browsing with dense media layout, date grouping, right-side chronology, and low-friction scanning.
- A Pixierge-owned violet-cyan-rose color personality.

## What We Keep From Pixierge

- Existing route and workflow semantics.
- Library/folder browsing as the primary workflow.
- Search, organizer, albums, tags, starred, settings, and scan activity surfaces.
- Keyboard/focus access, app-owned `data-testid` contracts, and deterministic tests.
- Dense app headings at or below 24px.
- Lucide as the icon family.

## Non-Goals

- Do not copy third-party logos, exact artwork, exact color values, product-specific content, or proprietary illustration.
- Do not turn Pixierge into a marketing page or decorative dashboard.
- Do not replace the whole frontend in one change.
- Do not create a separate design-system package before there is a second consumer.
- Do not ship review imagery as production product assets without an ownership/licensing review.

## Layout Model

The product shell has three layers:

1. App top bar: global brand, global search, notification, account/profile, and app-level utility actions.
2. App rail: permanently compact navigation with icon plus label. It does not expand.
3. Context area: a second-level sidebar plus the main content canvas.

The main content canvas should be photo-first in library workflows and management-first in settings/dashboard workflows. Use a contained workspace surface only when it clarifies the relationship between controls and content.

## Theme Policy

Dark mode is the first implementation target. Light mode is required in the token model from the start.

Theme-specific choices belong in semantic variables. Components should consume semantic tokens such as canvas, surface, content, border, action, focus, and status rather than raw color values.

No theme toggle is required in Phase 1 unless separately requested.

## Design QA Checklist

Before a migrated slice is handed off, verify:

- hierarchy supports the user's primary task;
- spacing follows the 4-point scale;
- text fits at desktop and mobile widths;
- focus is visible and keyboard operation is complete;
- target sizes are usable;
- status is not communicated by color alone;
- loading, empty, error, success, disabled, selected, hover, pressed, and open states are covered where applicable;
- visual snapshots are reviewed intentionally, not blindly updated.
