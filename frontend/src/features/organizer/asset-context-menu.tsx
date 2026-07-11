import { useEffect, useRef } from 'react';

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

export function AssetContextMenu({ open, x, y, actions, onClose }: AssetContextMenuProps) {
  const menuRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
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
        onClose();
      }
    };
    window.addEventListener('mousedown', onPointerDown);
    window.addEventListener('keydown', onKeyDown);
    return () => {
      window.removeEventListener('mousedown', onPointerDown);
      window.removeEventListener('keydown', onKeyDown);
    };
  }, [open, onClose]);

  if (!open) {
    return null;
  }

  return (
    <div
      aria-label="Asset actions"
      className="fixed z-50 min-w-44 rounded-md border border-border bg-surface p-1 shadow-lg"
      ref={menuRef}
      role="menu"
      style={{ left: x, top: y }}
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
    </div>
  );
}
