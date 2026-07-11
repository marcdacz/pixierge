import { ChevronDown, Pencil, Play, RefreshCw } from 'lucide-react';
import { useEffect, useState, type FormEvent, type SelectHTMLAttributes } from 'react';
import {
  ApiError,
  fetchSchedulerJobs,
  runSchedulerJob,
  updateSchedulerJob,
  type AuthResponse,
  type SchedulerJob
} from '@/api';
import { Badge } from '@/components/ui/badge';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow
} from '@/components/ui/table';
import { Tooltip, TooltipContent, TooltipProvider, TooltipTrigger } from '@/components/ui/tooltip';
import { cn } from '@/lib/utils';

type SchedulerDetailsProps = {
  auth: AuthResponse;
  onError: (title: string, description?: string) => void;
};

export function SchedulerDetails({ auth, onError }: SchedulerDetailsProps) {
  const [jobs, setJobs] = useState<SchedulerJob[]>([]);
  const [loading, setLoading] = useState(true);
  const [startingJobIds, setStartingJobIds] = useState<Set<string>>(() => new Set());
  const [editingJob, setEditingJob] = useState<SchedulerJob | null>(null);
  const [busyJobId, setBusyJobId] = useState<string | null>(null);

  async function refreshJobs(options: { showLoading?: boolean } = {}) {
    if (options.showLoading) {
      setLoading(true);
    }
    try {
      setJobs(await fetchSchedulerJobs());
    } catch (error) {
      onError('Unable to load scheduler jobs', error instanceof ApiError ? error.message : undefined);
    } finally {
      if (options.showLoading) {
        setLoading(false);
      }
    }
  }

  useEffect(() => {
    void refreshJobs({ showLoading: true });
  }, []);

  const hasActiveJobs = jobs.some((job) => isJobRunning(job)) || startingJobIds.size > 0;

  useEffect(() => {
    if (!hasActiveJobs) {
      return;
    }

    const timer = window.setInterval(() => {
      void refreshJobs();
    }, JOB_STATUS_POLL_MS);

    return () => window.clearInterval(timer);
  }, [hasActiveJobs]);

  async function handleRunNow(job: SchedulerJob) {
    if (isJobBusy(job, startingJobIds)) {
      return;
    }
    setStartingJobIds((current) => new Set(current).add(job.id));
    try {
      const run = await runSchedulerJob(job.id, auth.csrfToken);
      setJobs((current) =>
        current.map((item) =>
          item.id === job.id
            ? {
                ...item,
                lastStatus: run.status,
                lastRunAt: run.startedAt
              }
            : item
        )
      );
      void refreshJobs();
    } catch (error) {
      onError('Unable to run job', error instanceof ApiError ? error.message : undefined);
    } finally {
      setStartingJobIds((current) => {
        const next = new Set(current);
        next.delete(job.id);
        return next;
      });
    }
  }

  async function handleToggleEnabled(job: SchedulerJob) {
    setBusyJobId(job.id);
    try {
      const updated = await updateSchedulerJob(job.id, { enabled: !job.enabled }, auth.csrfToken);
      setJobs((current) => current.map((item) => (item.id === updated.id ? updated : item)));
    } catch (error) {
      onError('Unable to update job', error instanceof ApiError ? error.message : undefined);
    } finally {
      setBusyJobId(null);
    }
  }

  async function handleSaveSchedule(input: { cronExpression: string; timezone: string }) {
    if (!editingJob) {
      return;
    }
    setBusyJobId(editingJob.id);
    try {
      const updated = await updateSchedulerJob(editingJob.id, input, auth.csrfToken);
      setJobs((current) => current.map((item) => (item.id === updated.id ? updated : item)));
      setEditingJob(null);
    } catch (error) {
      onError('Unable to update schedule', error instanceof ApiError ? error.message : undefined);
    } finally {
      setBusyJobId(null);
    }
  }

  if (loading) {
    return <p className="text-sm text-muted-foreground">Loading scheduler jobs…</p>;
  }

  if (jobs.length === 0) {
    return <p className="text-sm text-muted-foreground">No scheduler jobs are registered yet.</p>;
  }

  return (
    <TooltipProvider>
      <div className="grid gap-4">
        <div className="overflow-x-auto rounded-md border border-border">
          <Table>
            <TableHeader>
              <TableRow>
                <TableHead className="min-w-[22rem]">Job</TableHead>
                <TableHead className="min-w-[16rem]">Schedule</TableHead>
                <TableHead className="w-[9.5rem]">Actions</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {jobs.map((job) => {
                const jobBusy = isJobBusy(job, startingJobIds);
                const runNowLabel = jobBusy
                  ? `Running · ${job.displayName}`
                  : `Run now · ${job.displayName}`;
                const editScheduleLabel = `Edit schedule · ${job.displayName}`;
                const toggleEnabledLabel = job.enabled
                  ? `Disable · ${job.displayName}`
                  : `Enable · ${job.displayName}`;
                return (
                  <TableRow key={job.id}>
                    <TableCell className="min-w-[22rem] align-top">
                      <div className="grid max-w-xl gap-1">
                        <span className="font-medium text-foreground">{job.displayName}</span>
                        <span className="text-sm text-muted-foreground">{job.description}</span>
                      </div>
                    </TableCell>
                    <TableCell className="min-w-[16rem] align-top">
                      <div className="grid gap-1.5 text-sm">
                        <div className="grid gap-0.5 font-mono text-sm">
                          <span>{job.cronExpression}</span>
                          <span className="text-muted-foreground">{job.timezone}</span>
                        </div>
                        <div className="grid gap-0.5 text-muted-foreground">
                          <span>Last: {formatTimestamp(job.lastRunAt)}</span>
                          <span>Next: {job.enabled ? formatTimestamp(job.nextRunAt) : '—'}</span>
                        </div>
                        {job.lastStatus && (
                          <Badge className="w-fit" variant="secondary">
                            {job.lastStatus}
                          </Badge>
                        )}
                      </div>
                    </TableCell>
                    <TableCell className="align-top">
                      <div className="flex flex-nowrap items-center gap-2">
                        <Tooltip>
                          <TooltipTrigger asChild>
                            <span className="inline-flex">
                              <EnabledToggle
                                busy={busyJobId === job.id}
                                checked={job.enabled}
                                label={toggleEnabledLabel}
                                onToggle={() => void handleToggleEnabled(job)}
                              />
                            </span>
                          </TooltipTrigger>
                          <TooltipContent>{job.enabled ? 'Disable' : 'Enable'}</TooltipContent>
                        </Tooltip>
                        <Tooltip>
                          <TooltipTrigger asChild>
                            <Button
                              aria-label={runNowLabel}
                              disabled={jobBusy}
                              onClick={() => void handleRunNow(job)}
                              size="icon"
                              type="button"
                              variant="ghost"
                            >
                              {jobBusy ? (
                                <RefreshCw className="h-4 w-4 animate-spin" aria-hidden />
                              ) : (
                                <Play className="h-4 w-4" aria-hidden />
                              )}
                            </Button>
                          </TooltipTrigger>
                          <TooltipContent>{jobBusy ? 'Running' : 'Run now'}</TooltipContent>
                        </Tooltip>
                        <Tooltip>
                          <TooltipTrigger asChild>
                            <Button
                              aria-label={editScheduleLabel}
                              disabled={busyJobId === job.id}
                              onClick={() => setEditingJob(job)}
                              size="icon"
                              type="button"
                              variant="ghost"
                            >
                              <Pencil className="h-4 w-4" aria-hidden />
                            </Button>
                          </TooltipTrigger>
                          <TooltipContent>Edit schedule</TooltipContent>
                        </Tooltip>
                      </div>
                    </TableCell>
                  </TableRow>
                );
              })}
            </TableBody>
          </Table>
        </div>

        {editingJob && (
          <EditScheduleDialog
            busy={busyJobId === editingJob.id}
            job={editingJob}
            onCancel={() => setEditingJob(null)}
            onSave={handleSaveSchedule}
          />
        )}
      </div>
    </TooltipProvider>
  );
}

const JOB_STATUS_POLL_MS = 2_000;

function isJobRunning(job: SchedulerJob): boolean {
  return job.lastStatus === 'queued' || job.lastStatus === 'running';
}

function isJobBusy(job: SchedulerJob, startingJobIds: Set<string>): boolean {
  return startingJobIds.has(job.id) || isJobRunning(job);
}

function EnabledToggle({
  busy,
  checked,
  label,
  onToggle
}: {
  busy: boolean;
  checked: boolean;
  label: string;
  onToggle: () => void;
}) {
  return (
    <button
      aria-checked={checked}
      aria-label={label}
      className={cn(
        'relative inline-flex h-6 w-11 shrink-0 items-center rounded-md border p-0.5 transition-colors',
        'focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring focus-visible:ring-offset-2 focus-visible:ring-offset-background',
        'disabled:pointer-events-none disabled:opacity-60',
        checked ? 'border-zinc-500 bg-zinc-700' : 'border-border bg-background'
      )}
      disabled={busy}
      onClick={onToggle}
      role="switch"
      type="button"
    >
      <span
        aria-hidden
        className={cn(
          'block h-4 w-4 rounded-sm bg-zinc-100 shadow-sm transition-transform',
          checked ? 'translate-x-5' : 'translate-x-0'
        )}
      />
    </button>
  );
}

function EditScheduleDialog({
  busy,
  job,
  onCancel,
  onSave
}: {
  busy: boolean;
  job: SchedulerJob;
  onCancel: () => void;
  onSave: (input: { cronExpression: string; timezone: string }) => Promise<void>;
}) {
  const matchedPreset = CRON_PRESETS.find((preset) => preset.cron === job.cronExpression);
  const [presetId, setPresetId] = useState(matchedPreset?.id ?? CUSTOM_CRON_PRESET_ID);
  const [cronExpression, setCronExpression] = useState(job.cronExpression);
  const [timezone, setTimezone] = useState(job.timezone);
  const titleId = `edit-schedule-${job.id}`;
  const isCustomCron = presetId === CUSTOM_CRON_PRESET_ID;

  function handlePresetChange(nextPresetId: string) {
    setPresetId(nextPresetId);
    if (nextPresetId === CUSTOM_CRON_PRESET_ID) {
      return;
    }
    const preset = CRON_PRESETS.find((item) => item.id === nextPresetId);
    if (preset?.cron) {
      setCronExpression(preset.cron);
    }
  }

  function handleSubmit(event: FormEvent) {
    event.preventDefault();
    void onSave({ cronExpression, timezone });
  }

  return (
    <div className="fixed inset-0 z-50 grid place-items-center bg-black/60 p-4">
      <form
        aria-labelledby={titleId}
        aria-modal="true"
        className="grid w-full max-w-md gap-4 rounded-md border border-border bg-surface p-5 text-foreground shadow-lg"
        onSubmit={handleSubmit}
        role="dialog"
      >
        <div className="grid gap-2">
          <h2 className="text-lg font-semibold" id={titleId}>
            Edit schedule · {job.displayName}
          </h2>
          <p className="text-sm text-muted-foreground">
            Choose a schedule preset and timezone, or enter a custom Spring 6-field cron expression.
          </p>
        </div>
        <div className="grid gap-2">
          <Label htmlFor="scheduler-cron-preset">Schedule</Label>
          <SelectField
            id="scheduler-cron-preset"
            onChange={(event) => handlePresetChange(event.target.value)}
            value={presetId}
          >
            {CRON_PRESETS.map((preset) => (
              <option key={preset.id} value={preset.id}>
                {preset.label}
              </option>
            ))}
          </SelectField>
        </div>
        {isCustomCron && (
          <div className="grid gap-2">
            <Label htmlFor="scheduler-cron">Custom cron expression</Label>
            <Input
              id="scheduler-cron"
              onChange={(event) => setCronExpression(event.target.value)}
              placeholder="0 0 8 * * *"
              required
              value={cronExpression}
            />
          </div>
        )}
        <div className="grid gap-2">
          <Label htmlFor="scheduler-timezone">Timezone</Label>
          <SelectField
            id="scheduler-timezone"
            onChange={(event) => setTimezone(event.target.value)}
            required
            value={timezone}
          >
            {timezoneOptions(job.timezone).map((zone) => (
              <option key={zone} value={zone}>
                {zone}
              </option>
            ))}
          </SelectField>
        </div>
        <div className="flex flex-wrap justify-end gap-2">
          <Button disabled={busy} onClick={onCancel} type="button" variant="ghost">
            Cancel
          </Button>
          <Button disabled={busy} type="submit" variant="secondary">
            Save schedule
          </Button>
        </div>
      </form>
    </div>
  );
}

const CUSTOM_CRON_PRESET_ID = 'custom';

const CRON_PRESETS: Array<{ id: string; label: string; cron: string | null }> = [
  { id: CUSTOM_CRON_PRESET_ID, label: 'Custom', cron: null },
  { id: 'every-5-mins', label: 'Every 5 minutes', cron: '0 */5 * * * *' },
  { id: 'every-15-mins', label: 'Every 15 minutes', cron: '0 */15 * * * *' },
  { id: 'every-30-mins', label: 'Every 30 minutes', cron: '0 */30 * * * *' },
  { id: 'every-1-hour', label: 'Every 1 hour', cron: '0 0 * * * *' },
  { id: 'every-2-hours', label: 'Every 2 hours', cron: '0 0 */2 * * *' },
  { id: 'every-3-hours', label: 'Every 3 hours', cron: '0 0 */3 * * *' },
  { id: 'every-4-hours', label: 'Every 4 hours', cron: '0 0 */4 * * *' },
  { id: 'every-6-hours', label: 'Every 6 hours', cron: '0 0 */6 * * *' },
  { id: 'every-8-hours', label: 'Every 8 hours', cron: '0 0 */8 * * *' },
  { id: 'every-12-hours', label: 'Every 12 hours', cron: '0 0 */12 * * *' },
  { id: 'daily-midnight', label: 'Daily at midnight', cron: '0 0 0 * * *' },
  { id: 'daily-2am', label: 'Daily at 2 AM', cron: '0 0 2 * * *' },
  { id: 'daily-3am', label: 'Daily at 3 AM', cron: '0 0 3 * * *' },
  { id: 'daily-6am', label: 'Daily at 6 AM', cron: '0 0 6 * * *' },
  { id: 'daily-8am', label: 'Daily at 8 AM', cron: '0 0 8 * * *' },
  { id: 'daily-noon', label: 'Daily at noon', cron: '0 0 12 * * *' },
  { id: 'daily-6pm', label: 'Daily at 6 PM', cron: '0 0 18 * * *' },
  { id: 'daily-9pm', label: 'Daily at 9 PM', cron: '0 0 21 * * *' },
  { id: 'weekdays-6am', label: 'Weekdays at 6 AM', cron: '0 0 6 * * MON-FRI' },
  { id: 'weekdays-9am', label: 'Weekdays at 9 AM', cron: '0 0 9 * * MON-FRI' },
  { id: 'weekdays-6pm', label: 'Weekdays at 6 PM', cron: '0 0 18 * * MON-FRI' },
  { id: 'weekends-10am', label: 'Weekends at 10 AM', cron: '0 0 10 * * SAT,SUN' },
  { id: 'weekly-sunday-2am', label: 'Weekly on Sunday at 2 AM', cron: '0 0 2 * * SUN' },
  { id: 'weekly-monday-9am', label: 'Weekly on Monday at 9 AM', cron: '0 0 9 * * MON' },
  { id: 'weekly-friday-6pm', label: 'Weekly on Friday at 6 PM', cron: '0 0 18 * * FRI' },
  { id: 'monthly-1st-midnight', label: 'Monthly on the 1st at midnight', cron: '0 0 0 1 * *' },
  { id: 'monthly-1st-2am', label: 'Monthly on the 1st at 2 AM', cron: '0 0 2 1 * *' },
  { id: 'monthly-15th-2am', label: 'Monthly on the 15th at 2 AM', cron: '0 0 2 15 * *' }
];

const CURATED_TIMEZONES = [
  'UTC',
  'Pacific/Auckland',
  'Australia/Sydney',
  'Australia/Melbourne',
  'Australia/Brisbane',
  'Australia/Adelaide',
  'Australia/Perth',
  'Asia/Tokyo',
  'Asia/Singapore',
  'Asia/Hong_Kong',
  'Asia/Shanghai',
  'Asia/Kolkata',
  'Asia/Dubai',
  'Europe/London',
  'Europe/Paris',
  'Europe/Berlin',
  'Europe/Amsterdam',
  'America/New_York',
  'America/Chicago',
  'America/Denver',
  'America/Los_Angeles',
  'America/Toronto',
  'America/Sao_Paulo',
  'Africa/Johannesburg',
  'Pacific/Honolulu'
] as const;

function SelectField({ className, ...props }: SelectHTMLAttributes<HTMLSelectElement>) {
  return (
    <div className="relative">
      <select
        className={cn(
          'h-10 w-full appearance-none rounded-md border border-input bg-background px-3 pr-10 text-sm text-foreground shadow-sm outline-none transition-colors focus-visible:border-ring focus-visible:ring-2 focus-visible:ring-ring/25 disabled:cursor-not-allowed disabled:opacity-60',
          className
        )}
        {...props}
      />
      <ChevronDown
        aria-hidden
        className="pointer-events-none absolute top-1/2 right-3 h-4 w-4 -translate-y-1/2 text-muted-foreground"
      />
    </div>
  );
}

function timezoneOptions(currentTimezone: string): string[] {
  const zones = new Set<string>(CURATED_TIMEZONES);
  if (currentTimezone) {
    zones.add(currentTimezone);
  }

  return [...zones].sort((left, right) => {
    if (left === 'UTC') {
      return -1;
    }
    if (right === 'UTC') {
      return 1;
    }
    return left.localeCompare(right);
  });
}

function formatTimestamp(value: string | null): string {
  if (!value) {
    return '—';
  }
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) {
    return value;
  }
  return date.toLocaleString();
}

