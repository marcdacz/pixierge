# Pixierge Tokens

Phase 1 tokens currently live in `frontend/src/styles.css` so Tailwind v4 can expose them through one global `@theme` block.

Use this folder for token-specific modules only when splitting the stylesheet reduces real complexity. Keep feature code on semantic utilities such as `bg-canvas`, `bg-surface`, `text-content`, `border-border`, `ring-focus`, and status utilities rather than raw color values.
