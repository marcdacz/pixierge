import { cleanup, renderHook, act } from '@testing-library/react';
import { afterEach, describe, expect, it } from 'vitest';
import {
  BROWSE_SIDEBAR_COLLAPSED_KEYS,
  readStoredBrowseSidebarCollapsed,
  useBrowseSidebarState,
  writeStoredBrowseSidebarCollapsed
} from '@/features/browse/browse-sidebar';

describe('browse sidebar collapsed persistence', () => {
  afterEach(() => {
    cleanup();
    window.localStorage.removeItem(BROWSE_SIDEBAR_COLLAPSED_KEYS.tags);
  });

  it('persists and restores collapsed preference', () => {
    expect(readStoredBrowseSidebarCollapsed(BROWSE_SIDEBAR_COLLAPSED_KEYS.tags)).toBe(false);

    writeStoredBrowseSidebarCollapsed(BROWSE_SIDEBAR_COLLAPSED_KEYS.tags, true);
    expect(window.localStorage.getItem(BROWSE_SIDEBAR_COLLAPSED_KEYS.tags)).toBe('true');
    expect(readStoredBrowseSidebarCollapsed(BROWSE_SIDEBAR_COLLAPSED_KEYS.tags)).toBe(true);

    writeStoredBrowseSidebarCollapsed(BROWSE_SIDEBAR_COLLAPSED_KEYS.tags, false);
    expect(window.localStorage.getItem(BROWSE_SIDEBAR_COLLAPSED_KEYS.tags)).toBe('false');
    expect(readStoredBrowseSidebarCollapsed(BROWSE_SIDEBAR_COLLAPSED_KEYS.tags)).toBe(false);
  });

  it('reads the stored preference when the hook mounts', () => {
    window.localStorage.setItem(BROWSE_SIDEBAR_COLLAPSED_KEYS.tags, 'true');
    const { result } = renderHook(() => useBrowseSidebarState(BROWSE_SIDEBAR_COLLAPSED_KEYS.tags));
    expect(result.current.collapsed).toBe(true);
  });

  it('writes desktop collapse changes to localStorage', () => {
    const { result } = renderHook(() => useBrowseSidebarState(BROWSE_SIDEBAR_COLLAPSED_KEYS.tags));

    act(() => {
      result.current.setCollapsed(true);
    });

    expect(result.current.collapsed).toBe(true);
    expect(window.localStorage.getItem(BROWSE_SIDEBAR_COLLAPSED_KEYS.tags)).toBe('true');

    act(() => {
      result.current.setCollapsed(false);
    });

    expect(result.current.collapsed).toBe(false);
    expect(window.localStorage.getItem(BROWSE_SIDEBAR_COLLAPSED_KEYS.tags)).toBe('false');
  });
});
