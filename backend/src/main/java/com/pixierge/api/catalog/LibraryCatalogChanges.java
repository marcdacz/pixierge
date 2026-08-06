package com.pixierge.api.catalog;

import java.util.UUID;

/** Library configuration changes; paths are configuration, never media contents. */
public final class LibraryCatalogChanges {
  private LibraryCatalogChanges() {}

  public static CatalogChange changed(UUID libraryId, String action, Object value) {
    return new Change(libraryId, action, value);
  }

  private record Change(UUID libraryId, String action, Object value) implements CatalogChange {
    @Override
    public String type() {
      return CatalogEventTypes.LIBRARY_CHANGED;
    }

    @Override
    public int version() {
      return 1;
    }

    @Override
    public String aggregateType() {
      return "library";
    }

    @Override
    public UUID aggregateId() {
      return libraryId;
    }

    @Override
    public Object payload() {
      return new Payload(action, value);
    }
  }

  private record Payload(String action, Object value) {}
}
