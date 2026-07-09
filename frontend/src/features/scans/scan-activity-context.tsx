import { createContext, useCallback, useContext, useEffect, useMemo, useState, type ReactNode } from 'react';
import { fetchActiveScans, fetchScan, type ActiveScan, type ScanRun } from '@/api';
import { isScanInProgress } from '@/features/scans/scan-utils';

type ScanActivityContextValue = {
  activeScans: ActiveScan[];
  trackedScan: ScanRun | null;
  trackScan: (scan: ScanRun) => void;
  clearTrackedScan: () => void;
};

const ScanActivityContext = createContext<ScanActivityContextValue | null>(null);

const activePollMs = 1_200;
const idlePollMs = 5_000;
const trackedPollMs = 1_200;

export function ScanActivityProvider({ children }: { children: ReactNode }) {
  const [activeScans, setActiveScans] = useState<ActiveScan[]>([]);
  const [trackedScan, setTrackedScan] = useState<ScanRun | null>(null);
  const [trackedScanId, setTrackedScanId] = useState<string | null>(null);

  const trackScan = useCallback((scan: ScanRun) => {
    setTrackedScan(scan);
    setTrackedScanId(isScanInProgress(scan) ? scan.id : null);
  }, []);

  const clearTrackedScan = useCallback(() => {
    setTrackedScan(null);
    setTrackedScanId(null);
  }, []);

  useEffect(() => {
    let cancelled = false;
    let timer: ReturnType<typeof setTimeout> | undefined;

    async function pollActive() {
      try {
        const scans = await fetchActiveScans();
        if (!cancelled) {
          setActiveScans(scans);
          timer = setTimeout(pollActive, scans.length > 0 ? activePollMs : idlePollMs);
        }
      } catch {
        if (!cancelled) {
          timer = setTimeout(pollActive, idlePollMs);
        }
      }
    }

    void pollActive();

    return () => {
      cancelled = true;
      if (timer) {
        clearTimeout(timer);
      }
    };
  }, []);

  useEffect(() => {
    if (!trackedScanId) {
      return;
    }

    const scanId = trackedScanId;
    let cancelled = false;
    let timer: ReturnType<typeof setTimeout> | undefined;

    async function pollTracked() {
      try {
        const scan = await fetchScan(scanId);
        if (cancelled) {
          return;
        }
        setTrackedScan(scan);
        if (isScanInProgress(scan)) {
          timer = setTimeout(pollTracked, trackedPollMs);
        } else {
          setTrackedScanId(null);
        }
      } catch {
        if (!cancelled) {
          setTrackedScanId(null);
        }
      }
    }

    timer = setTimeout(pollTracked, 800);

    return () => {
      cancelled = true;
      if (timer) {
        clearTimeout(timer);
      }
    };
  }, [trackedScanId]);

  const value = useMemo(
    () => ({
      activeScans,
      trackedScan,
      trackScan,
      clearTrackedScan
    }),
    [activeScans, clearTrackedScan, trackScan, trackedScan]
  );

  return <ScanActivityContext.Provider value={value}>{children}</ScanActivityContext.Provider>;
}

export function useScanActivity() {
  const context = useContext(ScanActivityContext);
  if (!context) {
    throw new Error('useScanActivity must be used within ScanActivityProvider');
  }
  return context;
}
