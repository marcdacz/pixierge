package com.pixierge.api.catalog;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

/** User-recovery events. Payload records deliberately exclude credentials and session state. */
public final class UserCatalogChanges {

  private UserCatalogChanges() {}

  public static CatalogChange created(UUID userId, String username, Collection<String> roles) {
    return new UserCreated(userId, username, roles.stream().sorted().toList());
  }

  public static CatalogChange statusChanged(UUID userId, String status) {
    return new UserStatusChanged(userId, status);
  }

  public static CatalogChange ownershipTransferred(UUID userId, UUID replacementUserId) {
    return new UserOwnershipTransferred(userId, replacementUserId);
  }

  private record UserCreated(UUID userId, String username, List<String> roles)
      implements CatalogChange {
    @Override
    public String type() {
      return CatalogEventTypes.USER_CREATED;
    }

    @Override
    public int version() {
      return 1;
    }

    @Override
    public String aggregateType() {
      return "user";
    }

    @Override
    public UUID aggregateId() {
      return userId;
    }

    @Override
    public Object payload() {
      return new CreatedPayload(username, roles);
    }
  }

  private record UserStatusChanged(UUID userId, String status) implements CatalogChange {
    @Override
    public String type() {
      return CatalogEventTypes.USER_STATUS_CHANGED;
    }

    @Override
    public int version() {
      return 1;
    }

    @Override
    public String aggregateType() {
      return "user";
    }

    @Override
    public UUID aggregateId() {
      return userId;
    }

    @Override
    public Object payload() {
      return new StatusPayload(status);
    }
  }

  private record UserOwnershipTransferred(UUID userId, UUID replacementUserId)
      implements CatalogChange {
    @Override
    public String type() {
      return CatalogEventTypes.USER_OWNERSHIP_TRANSFERRED;
    }

    @Override
    public int version() {
      return 1;
    }

    @Override
    public String aggregateType() {
      return "user";
    }

    @Override
    public UUID aggregateId() {
      return userId;
    }

    @Override
    public Object payload() {
      return new OwnershipTransferredPayload(replacementUserId);
    }
  }

  private record CreatedPayload(String username, List<String> roles) {}

  private record StatusPayload(String status) {}

  private record OwnershipTransferredPayload(UUID replacementUserId) {}
}
