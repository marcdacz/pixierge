# Pixierge Accessibility Notes

## Principles

Pixierge is a keyboard-operable photo-management app. The design system must preserve speed for pointer users while keeping the same workflows available to keyboard and assistive technology users.

## Navigation

- The app rail uses icon plus visible label, not icon alone.
- The app rail does not expand, so labels must fit within the fixed rail width.
- The topbar, app rail, context sidebar, and main content each need clear landmarks.
- Active navigation must be indicated by more than color: surface, marker, text weight, or `aria-current`.

## Focus

- Use a visible focus ring token on every interactive element.
- Do not suppress focus outlines without replacing them.
- Media tiles must show a clear focus state and a selected state.
- Focus order should move topbar, app rail, context sidebar, then content controls unless a modal/dialog is open.

## Media Grid

- Tiles need accessible names from file/title metadata.
- Selection must use `aria-selected` or an equivalent semantic pattern.
- Double-click open behavior must have a keyboard equivalent.
- Hover-only actions must also appear on focus.
- Text over media requires overlay treatment with sufficient contrast.

## Search And Filters

- Structured search chips need removable button names.
- Suggestions should use combobox/listbox semantics.
- Filter menus must be keyboard accessible and close predictably with Escape.
- Invalid search state must be announced with text, not color alone.

## Dialogs, Popovers, And Menus

- Dialogs trap focus and restore focus to the trigger.
- Popovers and menus close on Escape and outside click.
- Destructive actions need clear labels and recovery paths where practical.

## Status And Feedback

- Status must not rely on color alone.
- Use icons, text labels, and accessible names for missing, duplicate, pending, success, warning, and failure states.
- Async actions need immediate pending feedback and duplicate-submission prevention.
- Toasts should use appropriate live region politeness and should not be the only place critical failure information appears.

## Motion

- Respect `prefers-reduced-motion`.
- Keep motion short and state-driven.
- Avoid animation that interferes with rapid photo scanning.

## Responsive Requirements

- Mobile signed-in states need baseline coverage before Phase 1 implementation starts.
- Sidebars should become overlays or collapsible contextual panels on low-width screens.
- The app rail remains compact and labeled when space allows; if labels must be hidden at very narrow widths, tooltips or accessible labels remain required.
