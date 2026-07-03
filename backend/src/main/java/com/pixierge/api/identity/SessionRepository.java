package com.pixierge.api.identity;

import com.pixierge.api.db.QSessions;
import com.querydsl.core.Tuple;
import com.querydsl.sql.SQLQueryFactory;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

@Repository
public class SessionRepository {

    private static final QSessions SESSIONS = QSessions.sessions;

    private final SQLQueryFactory queryFactory;

    public SessionRepository(SQLQueryFactory queryFactory) {
        this.queryFactory = queryFactory;
    }

    public void createSession(UUID userId, String tokenHash, String csrfToken, OffsetDateTime expiresAt) {
        OffsetDateTime now = OffsetDateTime.now();

        queryFactory.insert(SESSIONS)
                .set(SESSIONS.id, UUID.randomUUID())
                .set(SESSIONS.userId, userId)
                .set(SESSIONS.tokenHash, tokenHash)
                .set(SESSIONS.csrfToken, csrfToken)
                .set(SESSIONS.expiresAt, expiresAt)
                .set(SESSIONS.createdAt, now)
                .set(SESSIONS.lastSeenAt, now)
                .execute();
    }

    @Transactional
    public Optional<SessionRecord> findActiveByTokenHash(String tokenHash) {
        OffsetDateTime now = OffsetDateTime.now();
        Tuple row = queryFactory
                .select(SESSIONS.userId, SESSIONS.csrfToken)
                .from(SESSIONS)
                .where(SESSIONS.tokenHash.eq(tokenHash)
                        .and(SESSIONS.revokedAt.isNull())
                        .and(SESSIONS.expiresAt.after(now)))
                .fetchFirst();

        if (row == null) {
            return Optional.empty();
        }

        queryFactory.update(SESSIONS)
                .set(SESSIONS.lastSeenAt, now)
                .where(SESSIONS.tokenHash.eq(tokenHash))
                .execute();

        return Optional.of(new SessionRecord(row.get(SESSIONS.userId), row.get(SESSIONS.csrfToken)));
    }

    @Transactional
    public void revokeByTokenHash(String tokenHash) {
        queryFactory.update(SESSIONS)
                .set(SESSIONS.revokedAt, OffsetDateTime.now())
                .where(SESSIONS.tokenHash.eq(tokenHash).and(SESSIONS.revokedAt.isNull()))
                .execute();
    }

    record SessionRecord(UUID userId, String csrfToken) {
    }
}
