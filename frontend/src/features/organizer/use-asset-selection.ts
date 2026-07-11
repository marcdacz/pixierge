import { useCallback, useEffect, useState } from 'react';

type ClickModifiers = Pick<MouseEvent, 'metaKey' | 'ctrlKey' | 'shiftKey'>;

export function useAssetSelection(browseContextKey: string) {
  const [selectedIds, setSelectedIds] = useState<Set<string>>(() => new Set());
  const [anchorId, setAnchorId] = useState<string | null>(null);

  const clear = useCallback(() => {
    setSelectedIds(new Set());
    setAnchorId(null);
  }, []);

  useEffect(clear, [browseContextKey, clear]);

  const selectOnly = useCallback((assetId: string) => {
    setSelectedIds(new Set([assetId]));
    setAnchorId(assetId);
  }, []);

  const selectClick = useCallback((
    assetId: string,
    index: number,
    modifiers: ClickModifiers,
    orderedIds: string[]
  ) => {
    if (modifiers.shiftKey && anchorId) {
      const anchorIndex = orderedIds.indexOf(anchorId);
      if (anchorIndex >= 0) {
        const [start, end] = [anchorIndex, index].sort((left, right) => left - right);
        setSelectedIds(new Set(orderedIds.slice(start, end + 1)));
        return;
      }
    }

    if (modifiers.metaKey || modifiers.ctrlKey) {
      setSelectedIds((current) => {
        const next = new Set(current);
        next.has(assetId) ? next.delete(assetId) : next.add(assetId);
        return next;
      });
      setAnchorId(assetId);
      return;
    }

    selectOnly(assetId);
  }, [anchorId, selectOnly]);

  return { selectedIds, anchorId, clear, isSelected: (id: string) => selectedIds.has(id), selectOnly, selectClick };
}
