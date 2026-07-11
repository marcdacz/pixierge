import { act, renderHook } from '@testing-library/react';
import { describe, expect, it } from 'vitest';
import { useAssetSelection } from '@/features/organizer/use-asset-selection';

describe('useAssetSelection', () => {
  const orderedIds = ['a', 'b', 'c', 'd'];

  it('replaces selection on plain click', () => {
    const { result } = renderHook(() => useAssetSelection('ctx'));
    act(() => result.current.selectClick('b', 1, { metaKey: false, ctrlKey: false, shiftKey: false }, orderedIds));
    expect([...result.current.selectedIds]).toEqual(['b']);
    act(() => result.current.selectClick('d', 3, { metaKey: false, ctrlKey: false, shiftKey: false }, orderedIds));
    expect([...result.current.selectedIds]).toEqual(['d']);
  });

  it('toggles with meta/ctrl click', () => {
    const { result } = renderHook(() => useAssetSelection('ctx'));
    act(() => result.current.selectClick('a', 0, { metaKey: false, ctrlKey: false, shiftKey: false }, orderedIds));
    act(() => result.current.selectClick('c', 2, { metaKey: true, ctrlKey: false, shiftKey: false }, orderedIds));
    expect(result.current.selectedIds.has('a')).toBe(true);
    expect(result.current.selectedIds.has('c')).toBe(true);
    act(() => result.current.selectClick('a', 0, { metaKey: true, ctrlKey: false, shiftKey: false }, orderedIds));
    expect(result.current.selectedIds.has('a')).toBe(false);
  });

  it('selects a range with shift click', () => {
    const { result } = renderHook(() => useAssetSelection('ctx'));
    act(() => result.current.selectClick('a', 0, { metaKey: false, ctrlKey: false, shiftKey: false }, orderedIds));
    act(() => result.current.selectClick('c', 2, { metaKey: false, ctrlKey: false, shiftKey: true }, orderedIds));
    expect([...result.current.selectedIds]).toEqual(['a', 'b', 'c']);
  });

  it('clears when browse context changes', () => {
    const { result, rerender } = renderHook(({ key }) => useAssetSelection(key), {
      initialProps: { key: 'one' }
    });
    act(() => result.current.selectOnly('a'));
    expect(result.current.selectedIds.size).toBe(1);
    rerender({ key: 'two' });
    expect(result.current.selectedIds.size).toBe(0);
  });
});
