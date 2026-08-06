package com.pixierge.api.catalog;

import java.util.List;
import java.util.UUID;

/** Tag and asset-tagging intent, represented without transient asset identifiers. */
public final class TagCatalogChanges {
  private TagCatalogChanges() {}

  public static CatalogChange changed(UUID tagId, String action, Object value) {
    return new Change(tagId, action, value);
  }

  public static CatalogChange assignmentsAdded(UUID tagId, List<CatalogAssetReference> assets) {
    return changed(tagId, "assignments_added", assets);
  }

  private record Change(UUID tagId, String action, Object value) implements CatalogChange {
    @Override
    public String type() {
      return CatalogEventTypes.TAG_CHANGED;
    }

    @Override
    public int version() {
      return 1;
    }

    @Override
    public String aggregateType() {
      return "tag";
    }

    @Override
    public UUID aggregateId() {
      return tagId;
    }

    @Override
    public Object payload() {
      return new Payload(action, value);
    }
  }

  private record Payload(String action, Object value) {}
}
