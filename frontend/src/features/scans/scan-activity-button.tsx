import { RefreshCw, Settings } from 'lucide-react';
import type { ActiveScan, ScanError, ScanRun } from '@/api';
import { Button } from '@/components/ui/button';
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuTrigger
} from '@/components/ui/dropdown-menu';
import { useScanActivity } from '@/features/scans/scan-activity-context';
import {
  formatScanDuration,
  formatScanTimestamp,
  isScanInProgress
} from '@/features/scans/scan-utils';
import { ScanStatsGrid } from '@/features/scans/scan-stats-grid';
import { cn } from '@/lib/utils';

type ScanActivityButtonProps = {
  onOpenSettings: () => void;
};

type ScanActivityView = {
  id: string;
  libraryName: string;
  rootPath: string | null;
  status: ScanRun['status'];
  scannedFileCount: number;
  addedCount: number;
  unchangedCount: number;
  movedCount: number;
  modifiedCount: number;
  duplicateCount: number;
  missingCount: number;
  reappearedCount: number;
  errorCount: number;
  startedAt: string | null;
  completedAt: string | null;
  errors: ScanError[];
};

export function ScanActivityButton({ onOpenSettings }: ScanActivityButtonProps) {
  const { activeScans, trackedScan } = useScanActivity();
  const displayScans = useDisplayScans(activeScans, trackedScan);

  if (displayScans.length === 0) {
    return null;
  }

  const spinning = displayScans.some((scan) => isScanInProgress(scan));

  return (
    <DropdownMenu>
      <DropdownMenuTrigger asChild>
        <Button aria-label="Scan activity" size="icon" type="button" variant="ghost">
          <RefreshCw className={cn('h-4 w-4', spinning && 'animate-spin')} aria-hidden />
        </Button>
      </DropdownMenuTrigger>
      <DropdownMenuContent align="end" className="w-80">
        <div className="flex items-center justify-between gap-2 px-2 py-1.5">
          <p className="text-sm font-medium text-foreground">Scan activity</p>
          <div className="flex items-center gap-1">
            {spinning && <RefreshCw className="h-4 w-4 animate-spin text-muted-foreground" aria-hidden />}
            <Button
              aria-label="Open scan settings"
              className="h-8 w-8"
              onClick={() => onOpenSettings()}
              size="icon"
              type="button"
              variant="ghost"
            >
              <Settings className="h-4 w-4" aria-hidden />
            </Button>
          </div>
        </div>
        {displayScans.map((scan) => (
          <div className="grid gap-2 border-t border-border px-2 py-2 text-sm" key={scan.id}>
            <div className="font-medium text-foreground">{scan.libraryName}</div>
            <dl className="grid grid-cols-[auto_minmax(0,1fr)] gap-x-3 gap-y-1 text-xs text-muted-foreground">
              <dt>Started</dt>
              <dd className="text-foreground">{formatScanTimestamp(scan.startedAt)}</dd>
              <dt>Finished</dt>
              <dd className="text-foreground">
                {scan.completedAt ? formatScanTimestamp(scan.completedAt) : 'In progress'}
              </dd>
              <dt>Duration</dt>
              <dd className="text-foreground">{formatScanDuration(scan.startedAt, scan.completedAt)}</dd>
            </dl>
            <ScanStatsGrid className="text-xs" includeScanned scan={scan} />
            {scan.errors.length > 0 && (
              <div className="grid gap-1 rounded-md border border-border bg-surface p-2 text-xs">
                <span className="font-medium text-foreground">
                  {scan.errors.length === 1 ? 'Error' : `Errors (${scan.errors.length})`}
                </span>
                {scan.errors.slice(0, 3).map((error) => (
                  <div className="grid gap-0.5" key={error.id}>
                    {error.path && <span className="break-all text-muted-foreground">{error.path}</span>}
                    <span className="text-foreground">{error.message}</span>
                  </div>
                ))}
                {scan.errors.length > 3 && (
                  <span className="text-muted-foreground">
                    +{scan.errors.length - 3} more in scan settings
                  </span>
                )}
              </div>
            )}
          </div>
        ))}
      </DropdownMenuContent>
    </DropdownMenu>
  );
}

function useDisplayScans(activeScans: ActiveScan[], trackedScan: ScanRun | null): ScanActivityView[] {
  if (activeScans.length > 0) {
    return activeScans.map(fromActiveScan);
  }
  if (!trackedScan) {
    return [];
  }
  if (
    isScanInProgress(trackedScan)
    || trackedScan.status === 'completed_with_errors'
    || trackedScan.status === 'failed'
  ) {
    return [fromTrackedScan(trackedScan)];
  }
  return [];
}

function fromActiveScan(scan: ActiveScan): ScanActivityView {
  return {
    id: scan.id,
    libraryName: scan.libraryName,
    rootPath: scan.rootPath,
    status: scan.status,
    scannedFileCount: scan.scannedFileCount,
    addedCount: scan.addedCount,
    unchangedCount: scan.unchangedCount,
    movedCount: scan.movedCount,
    modifiedCount: scan.modifiedCount,
    duplicateCount: scan.duplicateCount,
    missingCount: scan.missingCount,
    reappearedCount: scan.reappearedCount,
    errorCount: scan.errorCount,
    startedAt: scan.startedAt || null,
    completedAt: null,
    errors: []
  };
}

function fromTrackedScan(scan: ScanRun): ScanActivityView {
  return {
    id: scan.id,
    libraryName: 'Selected library',
    rootPath: null,
    status: scan.status,
    scannedFileCount: scan.scannedFileCount,
    addedCount: scan.addedCount,
    unchangedCount: scan.unchangedCount,
    movedCount: scan.movedCount,
    modifiedCount: scan.modifiedCount,
    duplicateCount: scan.duplicateCount,
    missingCount: scan.missingCount,
    reappearedCount: scan.reappearedCount,
    errorCount: scan.errorCount,
    startedAt: scan.startedAt || null,
    completedAt: scan.completedAt,
    errors: scan.errors
  };
}
