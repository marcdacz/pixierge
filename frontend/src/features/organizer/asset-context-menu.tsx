import { useLayoutEffect, useRef, useState } from 'react';
import { createPortal } from 'react-dom';

export type AssetContextMenuAction = {
  id: string;
  label: string;
  onSelect: () => void;
};

type AssetContextMenuProps = {
  open: boolean;
  x: number;
  y: number;
  actions: AssetContextMenuAction[];
  onClose: () => void;
};

const VIEWPORT_PADDING = 8;

function clampMenuPosition(
  x: number,
  y: number,
  width: number,
  height: number
): { left: number; top: number } {
  const maxLeft = Math.max(VIEWPORT_PADDING, window.innerWidth - width - VIEWPORT_PADDING);
  const maxTop = Math.max(VIEWPORT_PADDING, window.innerHeight - height - VIEWPORT_PADDING);
  return {
    left: Math.min(Math.max(x, VIEWPORT_PADDING), maxLeft),
    top: Math.min(Math.max(y, VIEWPORT_PADDING), maxTop)
  };
}

export function AssetContextMenu({ open, x, y, actions, onClose }: AssetContextMenuProps) {
  const menuRef = useRef<HTMLDivElement>(null);
  const [position, setPosition] = useState({ left: x, top: y });

  useLayoutEffect(() => {
    if (!open) {
      return;
    }
    setPosition({ left: x, top: y });
    const menu = menuRef.current;
    if (!menu) {
      return;
    }
    const { width, height } = menu.getBoundingClientRect();
    setPosition(clampMenuPosition(x, y, width, height));
  }, [open, x, y, actions.length]);

  useLayoutEffect(() => {
    if (!open) {
      return;
    }
    const onPointerDown = (event: MouseEvent) => {
      if (!menuRef.current?.contains(event.target as Node)) {
        onClose();
      }
    };
    const onKeyDown = (event: KeyboardEvent) => {
      if (event.key === 'Escape') {
        event.preventDefault();
        event.stopImmediatePropagation();
        onClose();
      }
    };
    window.addEventListener('mousedown', onPointerDown);
    window.addEventListener('keydown', onKeyDown, true);
    return () => {
      window.removeEventListener('mousedown', onPointerDown);
      window.removeEventListener('keydown', onKeyDown, true);
    };
  }, [open, onClose]);

  if (!open || typeof document === 'undefined') {
    return null;
  }

  return createPortal(
    <div
      aria-label="Asset actions"
      className="fixed z-50 min-w-44 rounded-md border border-border bg-surface p-1 shadow-lg"
      ref={menuRef}
      role="menu"
      style={{ left: position.left, top: position.top }}
    >
      {actions.map((action) => (
        <button
          className="flex w-full rounded-sm px-3 py-2 text-left text-sm hover:bg-muted"
          key={action.id}
          onClick={() => {
            action.onSelect();
            onClose();
          }}
          role="menuitem"
          type="button"
        >
          {action.label}
        </button>
      ))}
    </div>,
    document.body
  );
}
