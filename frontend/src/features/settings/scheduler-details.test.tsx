import { cleanup, render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { SchedulerDetails } from '@/features/settings/scheduler-details';

const auth = {
  csrfToken: 'csrf-token',
  user: {
    id: 'user-1',
    username: 'admin',
    roles: ['ADMIN'],
    permissions: ['library:admin', 'library:read']
  }
};

function createMetadataJob() {
  return {
    id: 'job-1',
    jobKey: 'core.metadata-scan',
    displayName: 'Metadata scan',
    description: 'Extracts metadata',
    ownerType: 'core',
    enabled: false,
    cronExpression: '0 30 2 * * *',
    timezone: 'UTC',
    nextRunAt: null,
    lastRunAt: null,
    lastStatus: null,
    timeoutSeconds: 7200,
    concurrencyKey: 'core:metadata-scan'
  };
}

vi.mock('@/api', async () => {
  const actual = await vi.importActual<typeof import('@/api')>('@/api');
  return {
    ...actual,
    fetchSchedulerJobs: vi.fn(),
    runSchedulerJob: vi.fn(),
    updateSchedulerJob: vi.fn()
  };
});

import { fetchSchedulerJobs, runSchedulerJob, updateSchedulerJob } from '@/api';

describe('SchedulerDetails', () => {
  afterEach(() => {
    cleanup();
  });

  beforeEach(() => {
    vi.mocked(fetchSchedulerJobs).mockReset();
    vi.mocked(runSchedulerJob).mockReset();
    vi.mocked(updateSchedulerJob).mockReset();
    const metadataJob = createMetadataJob();
    vi.mocked(fetchSchedulerJobs).mockResolvedValue([metadataJob]);
    vi.mocked(runSchedulerJob).mockResolvedValue({
      id: 'run-1',
      jobId: 'job-1',
      triggerSource: 'manual',
      status: 'running',
      startedAt: '2026-07-11T00:00:00Z',
      finishedAt: null,
      durationMs: null,
      summaryJson: null,
      errorMessage: null
    });
    vi.mocked(updateSchedulerJob).mockImplementation(async (_id, input) => ({
      ...createMetadataJob(),
      ...input,
      enabled: input.enabled ?? false,
      cronExpression: input.cronExpression ?? metadataJob.cronExpression,
      timezone: input.timezone ?? metadataJob.timezone
    }));
  });

  it('lists jobs and allows run now while disabled', async () => {
    const user = userEvent.setup();
    const onError = vi.fn();
    const idleJob = createMetadataJob();
    const runningJob = { ...idleJob, lastStatus: 'running', lastRunAt: '2026-07-11T00:00:00Z' };
    vi.mocked(fetchSchedulerJobs)
      .mockResolvedValueOnce([idleJob])
      .mockResolvedValue([runningJob]);

    render(<SchedulerDetails auth={auth} onError={onError} />);

    expect(await screen.findByRole('switch', { name: /^Enable · Metadata scan$/ })).toHaveAttribute(
      'aria-checked',
      'false'
    );
    expect(screen.getByText('0 30 2 * * *')).toBeInTheDocument();
    expect(screen.getByText(/^Last:/)).toBeInTheDocument();
    expect(screen.getByText(/^Next:/)).toBeInTheDocument();

    await user.click(screen.getByRole('button', { name: /^Run now · Metadata scan$/ }));
    await waitFor(() => {
      expect(runSchedulerJob).toHaveBeenCalledWith('job-1', 'csrf-token');
    });
    expect(onError).not.toHaveBeenCalled();

    const runningButton = await screen.findByRole('button', { name: /^Running · Metadata scan$/ });
    expect(runningButton).toBeDisabled();
  });

  it('keeps run action disabled while lastStatus is running', async () => {
    vi.mocked(fetchSchedulerJobs).mockResolvedValue([
      {
        ...createMetadataJob(),
        lastStatus: 'running',
        lastRunAt: '2026-07-11T00:00:00Z'
      }
    ]);

    render(<SchedulerDetails auth={auth} onError={vi.fn()} />);

    const runningButton = await screen.findByRole('button', { name: /^Running · Metadata scan$/ });
    expect(runningButton).toBeDisabled();
    expect(screen.getByText('running')).toBeInTheDocument();
  });

  it('restores run action after the job finishes', async () => {
    vi.useFakeTimers({ shouldAdvanceTime: true });
    const user = userEvent.setup({ advanceTimers: vi.advanceTimersByTime });
    const idleJob = createMetadataJob();
    const runningJob = { ...idleJob, lastStatus: 'running', lastRunAt: '2026-07-11T00:00:00Z' };
    const completedJob = {
      ...idleJob,
      lastStatus: 'completed',
      lastRunAt: '2026-07-11T00:00:00Z'
    };

    vi.mocked(fetchSchedulerJobs)
      .mockResolvedValueOnce([idleJob])
      .mockResolvedValueOnce([runningJob])
      .mockResolvedValue([completedJob]);

    render(<SchedulerDetails auth={auth} onError={vi.fn()} />);

    await user.click(await screen.findByRole('button', { name: /^Run now · Metadata scan$/ }));
    expect(await screen.findByRole('button', { name: /^Running · Metadata scan$/ })).toBeDisabled();

    await vi.advanceTimersByTimeAsync(2_000);

    await waitFor(() => {
      expect(screen.getByRole('button', { name: /^Run now · Metadata scan$/ })).toBeEnabled();
    });

    vi.useRealTimers();
  });

  it('toggles enabled state', async () => {
    const user = userEvent.setup();
    render(<SchedulerDetails auth={auth} onError={vi.fn()} />);

    await user.click(await screen.findByRole('switch', { name: /^Enable · Metadata scan$/ }));
    await waitFor(() => {
      expect(updateSchedulerJob).toHaveBeenCalledWith('job-1', { enabled: true }, 'csrf-token');
    });
  });

  it('edits schedule through dialog', async () => {
    const user = userEvent.setup();
    render(<SchedulerDetails auth={auth} onError={vi.fn()} />);

    await screen.findByRole('switch', { name: /^Enable · Metadata scan$/ });
    await user.click(screen.getByRole('button', { name: /^Edit schedule · Metadata scan$/ }));

    await user.selectOptions(screen.getByLabelText('Schedule'), 'daily-8am');
    await user.selectOptions(screen.getByLabelText('Timezone'), 'Australia/Sydney');
    await user.click(screen.getByRole('button', { name: 'Save schedule' }));

    await waitFor(() => {
      expect(updateSchedulerJob).toHaveBeenCalledWith(
        'job-1',
        { cronExpression: '0 0 8 * * *', timezone: 'Australia/Sydney' },
        'csrf-token'
      );
    });
  });

  it('allows a custom cron expression', async () => {
    const user = userEvent.setup();
    render(<SchedulerDetails auth={auth} onError={vi.fn()} />);

    await screen.findByRole('switch', { name: /^Enable · Metadata scan$/ });
    await user.click(screen.getByRole('button', { name: /^Edit schedule · Metadata scan$/ }));

    expect(screen.getByLabelText('Schedule')).toHaveValue('custom');
    const cron = screen.getByLabelText('Custom cron expression');
    await user.clear(cron);
    await user.type(cron, '0 0 3 * * *');
    await user.click(screen.getByRole('button', { name: 'Save schedule' }));

    await waitFor(() => {
      expect(updateSchedulerJob).toHaveBeenCalledWith(
        'job-1',
        { cronExpression: '0 0 3 * * *', timezone: 'UTC' },
        'csrf-token'
      );
    });
  });
});
