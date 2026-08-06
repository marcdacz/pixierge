package com.pixierge.api.identity;

import com.pixierge.api.db.QAlbumItems;
import com.pixierge.api.db.QAlbums;
import com.pixierge.api.db.QAssetTags;
import com.pixierge.api.db.QLibraries;
import com.pixierge.api.db.QLibraryMembers;
import com.pixierge.api.db.QPasswordCredentials;
import com.pixierge.api.db.QPermissions;
import com.pixierge.api.db.QRolePermissions;
import com.pixierge.api.db.QRoles;
import com.pixierge.api.db.QSetupLocks;
import com.pixierge.api.db.QTags;
import com.pixierge.api.db.QUserRoles;
import com.pixierge.api.db.QUsers;
import com.querydsl.core.Tuple;
import com.querydsl.sql.SQLQueryFactory;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class UserRepository {

  private static final QUsers USERS = QUsers.users;
  private static final QPasswordCredentials PASSWORD_CREDENTIALS =
      QPasswordCredentials.passwordCredentials;
  private static final QRoles ROLES = QRoles.roles;
  private static final QPermissions PERMISSIONS = QPermissions.permissions;
  private static final QUserRoles USER_ROLES = QUserRoles.userRoles;
  private static final QRolePermissions ROLE_PERMISSIONS = QRolePermissions.rolePermissions;
  private static final QSetupLocks SETUP_LOCKS = QSetupLocks.setupLocks;
  private static final QAlbumItems ALBUM_ITEMS = QAlbumItems.albumItems;
  private static final QAlbums ALBUMS = QAlbums.albums;
  private static final QAssetTags ASSET_TAGS = QAssetTags.assetTags;
  private static final QTags TAGS = QTags.tags;
  private static final QLibraries LIBRARIES = QLibraries.libraries;
  private static final QLibraryMembers LIBRARY_MEMBERS = QLibraryMembers.libraryMembers;

  private final SQLQueryFactory queryFactory;

  public UserRepository(SQLQueryFactory queryFactory) {
    this.queryFactory = queryFactory;
  }

  @Transactional(readOnly = true)
  public long countUsers() {
    Long count = queryFactory.select(USERS.id.count()).from(USERS).fetchOne();
    return count == null ? 0 : count;
  }

  public void lockFirstAdminSetup() {
    queryFactory
        .select(SETUP_LOCKS.lockKey)
        .from(SETUP_LOCKS)
        .where(SETUP_LOCKS.lockKey.eq("first_admin"))
        .forUpdate()
        .fetchOne();
  }

  public UUID createUser(String username, String passwordHash) {
    UUID userId = UUID.randomUUID();
    OffsetDateTime now = OffsetDateTime.now();

    queryFactory
        .insert(USERS)
        .set(USERS.id, userId)
        .set(USERS.username, normalizeUsername(username))
        .set(USERS.status, IdentityConstants.USER_STATUS_ACTIVE)
        .set(USERS.createdAt, now)
        .set(USERS.updatedAt, now)
        .execute();

    queryFactory
        .insert(PASSWORD_CREDENTIALS)
        .set(PASSWORD_CREDENTIALS.userId, userId)
        .set(PASSWORD_CREDENTIALS.passwordHash, passwordHash)
        .set(PASSWORD_CREDENTIALS.createdAt, now)
        .set(PASSWORD_CREDENTIALS.updatedAt, now)
        .execute();

    return userId;
  }

  public void assignRole(UUID userId, String roleKey) {
    UUID roleId =
        queryFactory.select(ROLES.id).from(ROLES).where(ROLES.roleKey.eq(roleKey)).fetchOne();

    if (roleId == null) {
      throw new IllegalStateException("Role does not exist: " + roleKey);
    }

    queryFactory
        .insert(USER_ROLES)
        .set(USER_ROLES.userId, userId)
        .set(USER_ROLES.roleId, roleId)
        .execute();
  }

  public boolean usernameExists(String username) {
    return queryFactory
            .selectOne()
            .from(USERS)
            .where(USERS.username.eq(normalizeUsername(username)))
            .fetchFirst()
        != null;
  }

  public Optional<UserRecord> findUser(UUID userId) {
    Tuple row =
        queryFactory
            .select(USERS.id, USERS.username, USERS.status)
            .from(USERS)
            .where(USERS.id.eq(userId))
            .fetchOne();
    return row == null
        ? Optional.empty()
        : Optional.of(
            new UserRecord(
                row.get(USERS.id),
                row.get(USERS.username),
                row.get(USERS.status),
                findRoleKeys(userId)));
  }

  public UserSummaryResponse requireUserSummary(UUID userId) {
    return listUsers().stream()
        .filter(user -> user.id().equals(userId))
        .findFirst()
        .orElseThrow(() -> new IllegalStateException("User not found after mutation"));
  }

  public void updatePassword(UUID userId, String passwordHash) {
    queryFactory
        .update(PASSWORD_CREDENTIALS)
        .set(PASSWORD_CREDENTIALS.passwordHash, passwordHash)
        .set(PASSWORD_CREDENTIALS.updatedAt, OffsetDateTime.now())
        .where(PASSWORD_CREDENTIALS.userId.eq(userId))
        .execute();
  }

  public void updateStatus(UUID userId, String status) {
    queryFactory
        .update(USERS)
        .set(USERS.status, status)
        .set(USERS.updatedAt, OffsetDateTime.now())
        .where(USERS.id.eq(userId))
        .execute();
  }

  public boolean isLastActiveAdmin(UUID userId) {
    Long count =
        queryFactory
            .select(USERS.id.countDistinct())
            .from(USERS)
            .join(USER_ROLES)
            .on(USER_ROLES.userId.eq(USERS.id))
            .join(ROLES)
            .on(ROLES.id.eq(USER_ROLES.roleId))
            .where(
                USERS
                    .status
                    .eq(IdentityConstants.USER_STATUS_ACTIVE)
                    .and(ROLES.roleKey.eq(IdentityConstants.ROLE_ADMIN)))
            .fetchOne();
    return count != null
        && count == 1
        && findRoleKeys(userId).contains(IdentityConstants.ROLE_ADMIN);
  }

  public void transferOwnership(UUID userId, UUID replacementUserId) {
    queryFactory
        .update(ALBUMS)
        .set(ALBUMS.ownerUserId, replacementUserId)
        .where(ALBUMS.ownerUserId.eq(userId))
        .execute();
    queryFactory
        .update(ALBUM_ITEMS)
        .set(ALBUM_ITEMS.addedBy, replacementUserId)
        .where(ALBUM_ITEMS.addedBy.eq(userId))
        .execute();
    queryFactory
        .update(TAGS)
        .set(TAGS.ownerUserId, replacementUserId)
        .set(TAGS.createdBy, replacementUserId)
        .where(TAGS.ownerUserId.eq(userId))
        .execute();
    queryFactory
        .update(ASSET_TAGS)
        .set(ASSET_TAGS.addedBy, replacementUserId)
        .where(ASSET_TAGS.addedBy.eq(userId))
        .execute();
    queryFactory
        .update(LIBRARIES)
        .set(LIBRARIES.createdBy, replacementUserId)
        .where(LIBRARIES.createdBy.eq(userId))
        .execute();
    List<UUID> ownedLibraryIds =
        queryFactory
            .select(LIBRARY_MEMBERS.libraryId)
            .from(LIBRARY_MEMBERS)
            .where(LIBRARY_MEMBERS.userId.eq(userId).and(LIBRARY_MEMBERS.memberRole.eq("owner")))
            .fetch();
    for (UUID libraryId : ownedLibraryIds) {
      long existing =
          queryFactory
              .select(LIBRARY_MEMBERS.userId.count())
              .from(LIBRARY_MEMBERS)
              .where(
                  LIBRARY_MEMBERS
                      .libraryId
                      .eq(libraryId)
                      .and(LIBRARY_MEMBERS.userId.eq(replacementUserId)))
              .fetchOne();
      if (existing == 0) {
        queryFactory
            .insert(LIBRARY_MEMBERS)
            .set(LIBRARY_MEMBERS.libraryId, libraryId)
            .set(LIBRARY_MEMBERS.userId, replacementUserId)
            .set(LIBRARY_MEMBERS.memberRole, "owner")
            .set(LIBRARY_MEMBERS.createdAt, OffsetDateTime.now())
            .execute();
      } else {
        queryFactory
            .update(LIBRARY_MEMBERS)
            .set(LIBRARY_MEMBERS.memberRole, "owner")
            .where(
                LIBRARY_MEMBERS
                    .libraryId
                    .eq(libraryId)
                    .and(LIBRARY_MEMBERS.userId.eq(replacementUserId)))
            .execute();
      }
    }
  }

  public void deleteUser(UUID userId) {
    queryFactory.delete(USERS).where(USERS.id.eq(userId)).execute();
  }

  @Transactional(readOnly = true)
  public Optional<LoginCredential> findLoginCredential(String username) {
    Tuple row =
        queryFactory
            .select(USERS.id, PASSWORD_CREDENTIALS.passwordHash)
            .from(USERS)
            .join(PASSWORD_CREDENTIALS)
            .on(PASSWORD_CREDENTIALS.userId.eq(USERS.id))
            .where(
                USERS
                    .username
                    .eq(normalizeUsername(username))
                    .and(USERS.status.eq(IdentityConstants.USER_STATUS_ACTIVE)))
            .fetchFirst();

    if (row == null) {
      return Optional.empty();
    }

    return Optional.of(
        new LoginCredential(row.get(USERS.id), row.get(PASSWORD_CREDENTIALS.passwordHash)));
  }

  @Transactional(readOnly = true)
  public Optional<AuthenticatedUser> findAuthenticatedUser(UUID userId, String csrfToken) {
    Tuple userRow =
        queryFactory
            .select(USERS.id, USERS.username)
            .from(USERS)
            .where(USERS.id.eq(userId).and(USERS.status.eq(IdentityConstants.USER_STATUS_ACTIVE)))
            .fetchOne();

    if (userRow == null) {
      return Optional.empty();
    }

    Set<String> permissions = findPermissionKeys(userId);
    if (hasLibraryMembership(userId)) {
      permissions.add("library:read");
    }
    if (hasLibraryManagementMembership(userId)) {
      permissions.add("sharing:write");
    }
    return Optional.of(
        new AuthenticatedUser(
            userRow.get(USERS.id),
            userRow.get(USERS.username),
            findRoleKeys(userId),
            permissions,
            csrfToken));
  }

  @Transactional(readOnly = true)
  public List<UserSummaryResponse> listUsers() {
    List<Tuple> userRows =
        queryFactory
            .select(USERS.id, USERS.username, USERS.status, USERS.createdAt)
            .from(USERS)
            .orderBy(USERS.createdAt.asc(), USERS.username.asc())
            .fetch();

    Map<UUID, Set<String>> rolesByUser = rolesByUser();
    List<UserSummaryResponse> users = new ArrayList<>();

    for (Tuple row : userRows) {
      UUID userId = row.get(USERS.id);
      users.add(
          new UserSummaryResponse(
              userId,
              row.get(USERS.username),
              row.get(USERS.status),
              rolesByUser.getOrDefault(userId, Set.of()),
              row.get(USERS.createdAt)));
    }

    return users;
  }

  @Transactional(readOnly = true)
  public List<RoleSummaryResponse> listRoles() {
    List<Tuple> roleRows =
        queryFactory
            .select(ROLES.id, ROLES.roleKey, ROLES.name, ROLES.description)
            .from(ROLES)
            .orderBy(ROLES.roleKey.asc())
            .fetch();

    Map<UUID, Set<String>> permissionsByRole = permissionsByRole();
    List<RoleSummaryResponse> roles = new ArrayList<>();

    for (Tuple row : roleRows) {
      UUID roleId = row.get(ROLES.id);
      roles.add(
          new RoleSummaryResponse(
              row.get(ROLES.roleKey),
              row.get(ROLES.name),
              row.get(ROLES.description),
              permissionsByRole.getOrDefault(roleId, Set.of())));
    }

    return roles;
  }

  private Set<String> findRoleKeys(UUID userId) {
    return new LinkedHashSet<>(
        queryFactory
            .select(ROLES.roleKey)
            .from(USER_ROLES)
            .join(ROLES)
            .on(ROLES.id.eq(USER_ROLES.roleId))
            .where(USER_ROLES.userId.eq(userId))
            .orderBy(ROLES.roleKey.asc())
            .fetch());
  }

  private Set<String> findPermissionKeys(UUID userId) {
    return new LinkedHashSet<>(
        queryFactory
            .select(PERMISSIONS.permissionKey)
            .from(USER_ROLES)
            .join(ROLE_PERMISSIONS)
            .on(ROLE_PERMISSIONS.roleId.eq(USER_ROLES.roleId))
            .join(PERMISSIONS)
            .on(PERMISSIONS.id.eq(ROLE_PERMISSIONS.permissionId))
            .where(USER_ROLES.userId.eq(userId))
            .orderBy(PERMISSIONS.permissionKey.asc())
            .fetch());
  }

  private boolean hasLibraryMembership(UUID userId) {
    Integer membership =
        queryFactory
            .selectOne()
            .from(LIBRARY_MEMBERS)
            .where(LIBRARY_MEMBERS.userId.eq(userId))
            .fetchFirst();
    return membership != null;
  }

  private boolean hasLibraryManagementMembership(UUID userId) {
    Integer membership =
        queryFactory
            .selectOne()
            .from(LIBRARY_MEMBERS)
            .where(
                LIBRARY_MEMBERS
                    .userId
                    .eq(userId)
                    .and(LIBRARY_MEMBERS.memberRole.in("owner", "admin")))
            .fetchFirst();
    return membership != null;
  }

  private Map<UUID, Set<String>> rolesByUser() {
    List<Tuple> rows =
        queryFactory
            .select(USER_ROLES.userId, ROLES.roleKey)
            .from(USER_ROLES)
            .join(ROLES)
            .on(ROLES.id.eq(USER_ROLES.roleId))
            .orderBy(ROLES.roleKey.asc())
            .fetch();

    Map<UUID, Set<String>> rolesByUser = new LinkedHashMap<>();
    for (Tuple row : rows) {
      rolesByUser
          .computeIfAbsent(row.get(USER_ROLES.userId), ignored -> new LinkedHashSet<>())
          .add(row.get(ROLES.roleKey));
    }
    return rolesByUser;
  }

  private Map<UUID, Set<String>> permissionsByRole() {
    List<Tuple> rows =
        queryFactory
            .select(ROLE_PERMISSIONS.roleId, PERMISSIONS.permissionKey)
            .from(ROLE_PERMISSIONS)
            .join(PERMISSIONS)
            .on(PERMISSIONS.id.eq(ROLE_PERMISSIONS.permissionId))
            .orderBy(PERMISSIONS.permissionKey.asc())
            .fetch();

    Map<UUID, Set<String>> permissionsByRole = new LinkedHashMap<>();
    for (Tuple row : rows) {
      permissionsByRole
          .computeIfAbsent(row.get(ROLE_PERMISSIONS.roleId), ignored -> new LinkedHashSet<>())
          .add(row.get(PERMISSIONS.permissionKey));
    }
    return permissionsByRole;
  }

  static String normalizeUsername(String username) {
    return username == null ? "" : username.trim().toLowerCase(Locale.ROOT);
  }

  record LoginCredential(UUID userId, String passwordHash) {}

  record UserRecord(UUID id, String username, String status, Set<String> roles) {}
}
