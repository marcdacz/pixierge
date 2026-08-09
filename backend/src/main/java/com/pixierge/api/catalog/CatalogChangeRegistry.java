package com.pixierge.api.catalog;

import java.util.Set;

/** Guards the persisted event contract so unknown or malformed changes never reach an export. */
final class CatalogChangeRegistry {
  private static final Set<String> SUPPORTED =
      Set.of(
          key(CatalogEventTypes.USER_CREATED, 1),
          key(CatalogEventTypes.USER_STATUS_CHANGED, 1),
          key(CatalogEventTypes.USER_OWNERSHIP_TRANSFERRED, 1),
          key(CatalogEventTypes.LIBRARY_CHANGED, 1),
          key(CatalogEventTypes.ALBUM_CHANGED, 1),
          key(CatalogEventTypes.TAG_CHANGED, 1),
          key(CatalogEventTypes.SYSTEM_CHANGED, 1));

  void validate(CatalogChange change) {
    if (change == null
        || change.aggregateId() == null
        || change.aggregateType() == null
        || change.aggregateType().isBlank()
        || change.payload() == null
        || !SUPPORTED.contains(key(change.type(), change.version()))) {
      throw new IllegalArgumentException("Unsupported or invalid catalog change");
    }
  }

  private static String key(String type, int version) {
    return type + "@" + version;
  }
}
