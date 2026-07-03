package com.pixierge.api.db;

import com.querydsl.core.types.dsl.ComparablePath;
import com.querydsl.core.types.dsl.DateTimePath;
import com.querydsl.core.types.dsl.StringPath;
import com.querydsl.sql.ColumnMetadata;
import com.querydsl.sql.RelationalPathBase;
import com.querydsl.sql.SchemaAndTable;

import java.sql.Types;
import java.time.OffsetDateTime;
import java.util.UUID;

import static com.querydsl.core.types.PathMetadataFactory.forVariable;

public class QSessions extends RelationalPathBase<QSessions> {

    public static final QSessions sessions = new QSessions("sessions");

    public final ComparablePath<UUID> id = createComparable("id", UUID.class);
    public final ComparablePath<UUID> userId = createComparable("userId", UUID.class);
    public final StringPath tokenHash = createString("tokenHash");
    public final StringPath csrfToken = createString("csrfToken");
    public final DateTimePath<OffsetDateTime> expiresAt = createDateTime("expiresAt", OffsetDateTime.class);
    public final DateTimePath<OffsetDateTime> revokedAt = createDateTime("revokedAt", OffsetDateTime.class);
    public final DateTimePath<OffsetDateTime> createdAt = createDateTime("createdAt", OffsetDateTime.class);
    public final DateTimePath<OffsetDateTime> lastSeenAt = createDateTime("lastSeenAt", OffsetDateTime.class);

    public QSessions(String variable) {
        super(QSessions.class, forVariable(variable), null, "sessions");
        addMetadata();
    }

    private void addMetadata() {
        addMetadata(id, ColumnMetadata.named("id").withIndex(1).ofType(Types.OTHER).notNull());
        addMetadata(userId, ColumnMetadata.named("user_id").withIndex(2).ofType(Types.OTHER).notNull());
        addMetadata(tokenHash, ColumnMetadata.named("token_hash").withIndex(3).ofType(Types.VARCHAR).notNull());
        addMetadata(csrfToken, ColumnMetadata.named("csrf_token").withIndex(4).ofType(Types.VARCHAR).notNull());
        addMetadata(expiresAt, ColumnMetadata.named("expires_at").withIndex(5).ofType(Types.TIMESTAMP_WITH_TIMEZONE).notNull());
        addMetadata(revokedAt, ColumnMetadata.named("revoked_at").withIndex(6).ofType(Types.TIMESTAMP_WITH_TIMEZONE));
        addMetadata(createdAt, ColumnMetadata.named("created_at").withIndex(7).ofType(Types.TIMESTAMP_WITH_TIMEZONE).notNull());
        addMetadata(lastSeenAt, ColumnMetadata.named("last_seen_at").withIndex(8).ofType(Types.TIMESTAMP_WITH_TIMEZONE).notNull());
    }

    @Override
    public SchemaAndTable getSchemaAndTable() {
        return new SchemaAndTable(null, "sessions");
    }
}
