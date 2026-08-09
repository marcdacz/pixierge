import { cleanup, render, screen } from '@testing-library/react';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { AuditLogPanel } from '@/features/settings/audit-log-panel';

const { fetchAuditEvents } = vi.hoisted(() => ({ fetchAuditEvents: vi.fn() }));

vi.mock('@/api', async () => {
  const actual = await vi.importActual<typeof import('@/api')>('@/api');
  return { ...actual, fetchAuditEvents };
});

describe('AuditLogPanel', () => {
  beforeEach(() => {
    cleanup();
    vi.clearAllMocks();
    fetchAuditEvents.mockResolvedValue({
      items: [
        {
          id: 1,
          createdAt: '2026-08-08T12:30:57Z',
          actorUserId: '5156c00a-49eb-40e4-b77f-28cdca7a7c98',
          actorUsername: 'marc',
          area: 'content',
          action: 'album.changed',
          resourceType: 'album',
          resourceId: 'album-1',
          details: {}
        },
        {
          id: 2,
          createdAt: '2026-08-08T12:31:57Z',
          actorUserId: null,
          actorUsername: null,
          area: 'system',
          action: 'backup.created',
          resourceType: 'backup',
          resourceId: 'backup-1',
          details: {}
        }
      ],
      page: 0,
      pageSize: 25,
      totalCount: 2,
      hasNext: false
    });
  });

  it('shows usernames while retaining a safe system fallback', async () => {
    render(<AuditLogPanel onError={vi.fn()} />);

    expect(await screen.findByRole('cell', { name: 'marc' })).toHaveAttribute(
      'title',
      '5156c00a-49eb-40e4-b77f-28cdca7a7c98'
    );
    expect(screen.getByRole('cell', { name: 'System' })).toBeInTheDocument();
    expect(screen.queryByText('5156c00a-49eb-40e4-b77f-28cdca7a7c98')).not.toBeInTheDocument();
  });
});
