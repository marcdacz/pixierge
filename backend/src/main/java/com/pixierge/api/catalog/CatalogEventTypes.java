package com.pixierge.api.catalog;

/** Stable names for the recovery-journal contract. Never reuse or silently rename a value. */
public final class CatalogEventTypes {

  public static final String USER_CREATED = "user.created";
  public static final String USER_STATUS_CHANGED = "user.status_changed";
  public static final String USER_OWNERSHIP_TRANSFERRED = "user.ownership_transferred";
  public static final String LIBRARY_CHANGED = "library.changed";
  public static final String ALBUM_CHANGED = "album.changed";
  public static final String TAG_CHANGED = "tag.changed";

  private CatalogEventTypes() {}
}
