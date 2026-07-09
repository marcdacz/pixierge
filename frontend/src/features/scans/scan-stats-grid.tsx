import { cn } from '@/lib/utils';

export type ScanStatCounts = {
  scannedFileCount: number;
  addedCount: number;
  unchangedCount: number;
  movedCount: number;
  modifiedCount: number;
  duplicateCount: number;
  missingCount: number;
  reappearedCount: number;
  errorCount: number;
};

type ScanStatsGridProps = {
  scan: ScanStatCounts;
  includeScanned?: boolean;
  className?: string;
};

export function ScanStatsGrid({ scan, includeScanned = false, className }: ScanStatsGridProps) {
  return (
    <div
      className={cn(
        'grid grid-cols-1 gap-y-1 text-muted-foreground',
        className
      )}
    >
      {includeScanned && <span>Scanned {scan.scannedFileCount}</span>}
      <span>Added {scan.addedCount}</span>
      <span>Unchanged {scan.unchangedCount}</span>
      <span>Moved {scan.movedCount}</span>
      <span>Modified {scan.modifiedCount}</span>
      <span>Duplicates {scan.duplicateCount}</span>
      <span>Missing {scan.missingCount}</span>
      <span>Reappeared {scan.reappearedCount}</span>
      <span>Errors {scan.errorCount}</span>
    </div>
  );
}
