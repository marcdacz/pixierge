import type { ScanRun } from '@/api';

export function isScanInProgress(scan: Pick<ScanRun, 'status'>) {
  return scan.status === 'running' || scan.status === 'queued';
}

export function formatScanStatus(status: ScanRun['status']) {
  if (status === 'running' || status === 'queued') {
    return 'Scan running';
  }
  if (status === 'completed_with_errors') {
    return 'Scan completed with errors';
  }
  if (status === 'failed') {
    return 'Scan failed';
  }
  return `Scan ${status.replaceAll('_', ' ')}`;
}

export function formatScanTimestamp(value: string | null | undefined): string {
  if (!value) {
    return '—';
  }
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) {
    return '—';
  }
  return date.toLocaleString();
}

export function formatScanDuration(
  startedAt: string | null | undefined,
  completedAt: string | null | undefined
): string {
  if (!startedAt) {
    return '—';
  }
  const start = new Date(startedAt).getTime();
  if (Number.isNaN(start)) {
    return '—';
  }
  const end = completedAt ? new Date(completedAt).getTime() : Date.now();
  if (Number.isNaN(end) || end < start) {
    return '—';
  }

  const totalSeconds = Math.round((end - start) / 1000);
  const hours = Math.floor(totalSeconds / 3600);
  const minutes = Math.floor((totalSeconds % 3600) / 60);
  const seconds = totalSeconds % 60;

  const parts: string[] = [];
  if (hours > 0) {
    parts.push(`${hours}h`);
  }
  if (hours > 0 || minutes > 0) {
    parts.push(`${minutes}m`);
  }
  parts.push(`${seconds}s`);
  return parts.join(' ');
}
