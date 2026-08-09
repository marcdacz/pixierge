package com.pixierge.api.catalog;

import java.util.UUID;

/** System operations are durable audit events, not recoverable catalog mutations. */
public final class SystemCatalogChanges {
  private SystemCatalogChanges() {}

  public static CatalogChange changed(UUID id, String action, String value) {
    return new Change(id, action, value);
  }

  private record Change(UUID id, String action, String value) implements CatalogChange {
    @Override
    public String type() {
      return CatalogEventTypes.SYSTEM_CHANGED;
    }

    @Override
    public int version() {
      return 1;
    }

    @Override
    public String aggregateType() {
      return "system";
    }

    @Override
    public UUID aggregateId() {
      return id;
    }

    @Override
    public Object payload() {
      return new Payload(action, value);
    }
  }

  private record Payload(String action, String value) {}
}
