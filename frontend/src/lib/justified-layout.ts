export type JustifiedInput<T> = {
  aspectRatio: number;
  item: T;
};

export type JustifiedItem<T> = JustifiedInput<T> & {
  height: number;
  width: number;
};

export type JustifiedRow<T> = {
  complete: boolean;
  height: number;
  items: JustifiedItem<T>[];
};

export type JustifiedLayoutOptions = {
  containerWidth: number;
  gap: number;
  justifyLastRow?: boolean;
  maxRowHeight?: number;
  minRowHeight?: number;
  targetRowHeight: number;
};

const DEFAULT_MIN_ROW_HEIGHT_RATIO = 0.72;
const DEFAULT_MAX_ROW_HEIGHT_RATIO = 1.28;
const FALLBACK_ASPECT_RATIO = 4 / 3;

export function createJustifiedRows<T>(
  items: JustifiedInput<T>[],
  { containerWidth, gap, justifyLastRow = false, maxRowHeight, minRowHeight, targetRowHeight }: JustifiedLayoutOptions
): JustifiedRow<T>[] {
  if (items.length === 0 || containerWidth <= 0 || targetRowHeight <= 0) {
    return [];
  }

  const minHeight = minRowHeight ?? targetRowHeight * DEFAULT_MIN_ROW_HEIGHT_RATIO;
  const maxHeight = maxRowHeight ?? targetRowHeight * DEFAULT_MAX_ROW_HEIGHT_RATIO;
  const rows: JustifiedRow<T>[] = [];
  let row: JustifiedInput<T>[] = [];
  let ratioSum = 0;

  function completeRow(rowItems: JustifiedInput<T>[], complete: boolean, height: number) {
    rows.push({
      complete,
      height,
      items: rowItems.map((entry) => ({
        ...entry,
        aspectRatio: normalizeAspectRatio(entry.aspectRatio),
        height,
        width: normalizeAspectRatio(entry.aspectRatio) * height
      }))
    });
  }

  items.forEach((entry) => {
    const normalizedEntry = { ...entry, aspectRatio: normalizeAspectRatio(entry.aspectRatio) };
    row.push(normalizedEntry);
    ratioSum += normalizedEntry.aspectRatio;

    const rowHeight = heightForRow(containerWidth, gap, row.length, ratioSum);
    if (rowHeight > targetRowHeight) {
      return;
    }

    if (row.length > 1 && rowHeight < minHeight) {
      const previousRow = row.slice(0, -1);
      const previousRatioSum = ratioSum - normalizedEntry.aspectRatio;
      const previousHeight = heightForRow(containerWidth, gap, previousRow.length, previousRatioSum);
      const previousDelta = Math.abs(previousHeight - targetRowHeight);
      const currentDelta = Math.abs(rowHeight - targetRowHeight);

      if (previousHeight <= maxHeight && previousDelta < currentDelta) {
        completeRow(previousRow, true, previousHeight);
        row = [normalizedEntry];
        ratioSum = normalizedEntry.aspectRatio;
        return;
      }
    }

    completeRow(row, true, rowHeight);
    row = [];
    ratioSum = 0;
  });

  if (row.length > 0) {
    const lastRowHeight = heightForRow(containerWidth, gap, row.length, ratioSum);
    const shouldJustifyLastRow = justifyLastRow && lastRowHeight >= minHeight && lastRowHeight <= maxHeight;
    completeRow(row, shouldJustifyLastRow, shouldJustifyLastRow ? lastRowHeight : targetRowHeight);
  }

  return rows;
}

function heightForRow(containerWidth: number, gap: number, itemCount: number, ratioSum: number) {
  return (containerWidth - gap * Math.max(0, itemCount - 1)) / ratioSum;
}

export function normalizeAspectRatio(aspectRatio: number) {
  return Number.isFinite(aspectRatio) && aspectRatio > 0 ? aspectRatio : FALLBACK_ASPECT_RATIO;
}
