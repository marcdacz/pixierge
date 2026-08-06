package com.pixierge.api.health;

import com.pixierge.api.db.QAppMetadata;
import com.querydsl.sql.SQLQueryFactory;
import java.util.Optional;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class AppMetadataRepository {

  private static final QAppMetadata APP_METADATA = QAppMetadata.appMetadata;

  private final SQLQueryFactory queryFactory;

  public AppMetadataRepository(SQLQueryFactory queryFactory) {
    this.queryFactory = queryFactory;
  }

  @Transactional(readOnly = true)
  public Optional<String> findValue(String key) {
    return Optional.ofNullable(
        queryFactory
            .select(APP_METADATA.metadataValue)
            .from(APP_METADATA)
            .where(APP_METADATA.metadataKey.eq(key))
            .fetchFirst());
  }
}
