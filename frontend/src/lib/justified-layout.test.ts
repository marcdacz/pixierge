import { describe, expect, it } from 'vitest';
import { createJustifiedRows, normalizeAspectRatio } from './justified-layout';

describe('createJustifiedRows', () => {
  it('fills complete rows by varying height around the target', () => {
    const rows = createJustifiedRows(
      [
        { item: 'landscape', aspectRatio: 3 / 2 },
        { item: 'portrait', aspectRatio: 2 / 3 },
        { item: 'square', aspectRatio: 1 },
        { item: 'wide', aspectRatio: 16 / 9 }
      ],
      { containerWidth: 800, gap: 4, targetRowHeight: 176 }
    );

    expect(rows[0].complete).toBe(true);
    expect(rows[0].items.reduce((sum, item) => sum + item.width, 0) + (rows[0].items.length - 1) * 4).toBeCloseTo(
      800,
      5
    );
    expect(rows[0].height).toBeGreaterThan(176 * 0.72);
    expect(rows[0].height).toBeLessThan(176 * 1.28);
  });

  it('keeps portrait items narrower than landscape items in the same row', () => {
    const [row] = createJustifiedRows(
      [
        { item: 'portrait', aspectRatio: 2 / 3 },
        { item: 'landscape', aspectRatio: 3 / 2 },
        { item: 'panorama', aspectRatio: 2 }
      ],
      { containerWidth: 760, gap: 4, targetRowHeight: 176 }
    );

    expect(row.items.find((item) => item.item === 'portrait')!.width).toBeLessThan(
      row.items.find((item) => item.item === 'landscape')!.width
    );
  });

  it('leaves the final widow row at the target height by default', () => {
    const rows = createJustifiedRows(
      [
        { item: 'a', aspectRatio: 16 / 9 },
        { item: 'b', aspectRatio: 4 / 3 },
        { item: 'c', aspectRatio: 1 }
      ],
      { containerWidth: 760, gap: 4, targetRowHeight: 176 }
    );

    expect(rows.at(-1)!.complete).toBe(false);
    expect(rows.at(-1)!.height).toBe(176);
  });

  it('normalizes unavailable aspect ratios to four-by-three', () => {
    expect(normalizeAspectRatio(0)).toBe(4 / 3);
    expect(normalizeAspectRatio(Number.NaN)).toBe(4 / 3);
  });
});
