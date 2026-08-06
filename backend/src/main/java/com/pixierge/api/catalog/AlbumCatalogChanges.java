package com.pixierge.api.catalog;

import java.util.List;
import java.util.UUID;

/** Album and sharing intent, represented without transient asset identifiers. */
public final class AlbumCatalogChanges {
  private AlbumCatalogChanges() {}

  public static CatalogChange changed(UUID albumId, String action, Object value) {
    return new Change(albumId, action, value);
  }

  public static CatalogChange itemsAdded(UUID albumId, List<CatalogAssetReference> items) {
    return changed(albumId, "items_added", items);
  }

  private record Change(UUID albumId, String action, Object value) implements CatalogChange {
    @Override
    public String type() {
      return CatalogEventTypes.ALBUM_CHANGED;
    }

    @Override
    public int version() {
      return 1;
    }

    @Override
    public String aggregateType() {
      return "album";
    }

    @Override
    public UUID aggregateId() {
      return albumId;
    }

    @Override
    public Object payload() {
      return new Payload(action, value);
    }
  }

  private record Payload(String action, Object value) {}
}
