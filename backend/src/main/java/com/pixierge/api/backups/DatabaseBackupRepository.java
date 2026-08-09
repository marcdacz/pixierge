package com.pixierge.api.backups;

import com.pixierge.api.db.QDatabaseBackups;
import com.querydsl.core.Tuple;
import com.querydsl.sql.SQLQueryFactory;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
@Transactional(readOnly = true)
class DatabaseBackupRepository {
  private static final QDatabaseBackups BACKUPS = QDatabaseBackups.databaseBackups;
  private final SQLQueryFactory queryFactory;

  DatabaseBackupRepository(SQLQueryFactory queryFactory) {
    this.queryFactory = queryFactory;
  }

  @Transactional
  void add(DatabaseBackup backup) {
    queryFactory
        .insert(BACKUPS)
        .set(BACKUPS.id, backup.id())
        .set(BACKUPS.createdAt, backup.createdAt())
        .set(BACKUPS.storagePath, backup.storagePath())
        .set(BACKUPS.checksum, backup.checksum())
        .set(BACKUPS.byteSize, backup.byteSize())
        .set(BACKUPS.postgresVersion, backup.postgresVersion())
        .set(BACKUPS.schemaVersion, backup.schemaVersion())
        .set(BACKUPS.status, backup.status())
        .set(BACKUPS.failureDetail, backup.failureDetail())
        .execute();
  }

  List<DatabaseBackup> history(int offset, int limit) {
    return queryFactory
        .select(
            BACKUPS.id,
            BACKUPS.createdAt,
            BACKUPS.storagePath,
            BACKUPS.checksum,
            BACKUPS.byteSize,
            BACKUPS.postgresVersion,
            BACKUPS.schemaVersion,
            BACKUPS.status,
            BACKUPS.failureDetail)
        .from(BACKUPS)
        .orderBy(BACKUPS.createdAt.desc())
        .offset(offset)
        .limit(limit)
        .fetch()
        .stream()
        .map(this::map)
        .toList();
  }

  Optional<DatabaseBackup> find(UUID id) {
    return Optional.ofNullable(
            queryFactory
                .select(
                    BACKUPS.id,
                    BACKUPS.createdAt,
                    BACKUPS.storagePath,
                    BACKUPS.checksum,
                    BACKUPS.byteSize,
                    BACKUPS.postgresVersion,
                    BACKUPS.schemaVersion,
                    BACKUPS.status,
                    BACKUPS.failureDetail)
                .from(BACKUPS)
                .where(BACKUPS.id.eq(id))
                .fetchOne())
        .map(this::map);
  }

  long count() {
    Long value = queryFactory.select(BACKUPS.id.count()).from(BACKUPS).fetchOne();
    return value == null ? 0 : value;
  }

  private DatabaseBackup map(Tuple row) {
    return new DatabaseBackup(
        row.get(BACKUPS.id),
        row.get(BACKUPS.createdAt),
        row.get(BACKUPS.storagePath),
        row.get(BACKUPS.checksum),
        row.get(BACKUPS.byteSize),
        row.get(BACKUPS.postgresVersion),
        row.get(BACKUPS.schemaVersion),
        row.get(BACKUPS.status),
        row.get(BACKUPS.failureDetail));
  }
}
