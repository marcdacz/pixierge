package com.pixierge.api.identity;

import com.pixierge.api.db.QPasswordCredentials;
import com.pixierge.api.db.QPermissions;
import com.pixierge.api.db.QRolePermissions;
import com.pixierge.api.db.QRoles;
import com.pixierge.api.db.QSetupLocks;
import com.pixierge.api.db.QUserRoles;
import com.pixierge.api.db.QUsers;
import com.querydsl.core.Tuple;
import com.querydsl.sql.SQLQueryFactory;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

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

@Repository
public class UserRepository {

    private static final QUsers USERS = QUsers.users;
    private static final QPasswordCredentials PASSWORD_CREDENTIALS = QPasswordCredentials.passwordCredentials;
    private static final QRoles ROLES = QRoles.roles;
    private static final QPermissions PERMISSIONS = QPermissions.permissions;
    private static final QUserRoles USER_ROLES = QUserRoles.userRoles;
    private static final QRolePermissions ROLE_PERMISSIONS = QRolePermissions.rolePermissions;
    private static final QSetupLocks SETUP_LOCKS = QSetupLocks.setupLocks;

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

    public UUID createUser(String email, String displayName, String passwordHash) {
        UUID userId = UUID.randomUUID();
        OffsetDateTime now = OffsetDateTime.now();

        queryFactory.insert(USERS)
                .set(USERS.id, userId)
                .set(USERS.email, normalizeEmail(email))
                .set(USERS.displayName, displayName)
                .set(USERS.status, IdentityConstants.USER_STATUS_ACTIVE)
                .set(USERS.createdAt, now)
                .set(USERS.updatedAt, now)
                .execute();

        queryFactory.insert(PASSWORD_CREDENTIALS)
                .set(PASSWORD_CREDENTIALS.userId, userId)
                .set(PASSWORD_CREDENTIALS.passwordHash, passwordHash)
                .set(PASSWORD_CREDENTIALS.createdAt, now)
                .set(PASSWORD_CREDENTIALS.updatedAt, now)
                .execute();

        return userId;
    }

    public void assignRole(UUID userId, String roleKey) {
        UUID roleId = queryFactory
                .select(ROLES.id)
                .from(ROLES)
                .where(ROLES.roleKey.eq(roleKey))
                .fetchOne();

        if (roleId == null) {
            throw new IllegalStateException("Role does not exist: " + roleKey);
        }

        queryFactory.insert(USER_ROLES)
                .set(USER_ROLES.userId, userId)
                .set(USER_ROLES.roleId, roleId)
                .execute();
    }

    @Transactional(readOnly = true)
    public Optional<LoginCredential> findLoginCredential(String email) {
        Tuple row = queryFactory
                .select(USERS.id, PASSWORD_CREDENTIALS.passwordHash)
                .from(USERS)
                .join(PASSWORD_CREDENTIALS).on(PASSWORD_CREDENTIALS.userId.eq(USERS.id))
                .where(USERS.email.eq(normalizeEmail(email)).and(USERS.status.eq(IdentityConstants.USER_STATUS_ACTIVE)))
                .fetchFirst();

        if (row == null) {
            return Optional.empty();
        }

        return Optional.of(new LoginCredential(row.get(USERS.id), row.get(PASSWORD_CREDENTIALS.passwordHash)));
    }

    @Transactional(readOnly = true)
    public Optional<AuthenticatedUser> findAuthenticatedUser(UUID userId, String csrfToken) {
        Tuple userRow = queryFactory
                .select(USERS.id, USERS.email, USERS.displayName)
                .from(USERS)
                .where(USERS.id.eq(userId).and(USERS.status.eq(IdentityConstants.USER_STATUS_ACTIVE)))
                .fetchOne();

        if (userRow == null) {
            return Optional.empty();
        }

        return Optional.of(new AuthenticatedUser(
                userRow.get(USERS.id),
                userRow.get(USERS.email),
                userRow.get(USERS.displayName),
                findRoleKeys(userId),
                findPermissionKeys(userId),
                csrfToken
        ));
    }

    @Transactional(readOnly = true)
    public List<UserSummaryResponse> listUsers() {
        List<Tuple> userRows = queryFactory
                .select(USERS.id, USERS.email, USERS.displayName, USERS.status, USERS.createdAt)
                .from(USERS)
                .orderBy(USERS.createdAt.asc(), USERS.email.asc())
                .fetch();

        Map<UUID, Set<String>> rolesByUser = rolesByUser();
        List<UserSummaryResponse> users = new ArrayList<>();

        for (Tuple row : userRows) {
            UUID userId = row.get(USERS.id);
            users.add(new UserSummaryResponse(
                    userId,
                    row.get(USERS.email),
                    row.get(USERS.displayName),
                    row.get(USERS.status),
                    rolesByUser.getOrDefault(userId, Set.of()),
                    row.get(USERS.createdAt)
            ));
        }

        return users;
    }

    @Transactional(readOnly = true)
    public List<RoleSummaryResponse> listRoles() {
        List<Tuple> roleRows = queryFactory
                .select(ROLES.id, ROLES.roleKey, ROLES.name, ROLES.description)
                .from(ROLES)
                .orderBy(ROLES.roleKey.asc())
                .fetch();

        Map<UUID, Set<String>> permissionsByRole = permissionsByRole();
        List<RoleSummaryResponse> roles = new ArrayList<>();

        for (Tuple row : roleRows) {
            UUID roleId = row.get(ROLES.id);
            roles.add(new RoleSummaryResponse(
                    row.get(ROLES.roleKey),
                    row.get(ROLES.name),
                    row.get(ROLES.description),
                    permissionsByRole.getOrDefault(roleId, Set.of())
            ));
        }

        return roles;
    }

    private Set<String> findRoleKeys(UUID userId) {
        return new LinkedHashSet<>(queryFactory
                .select(ROLES.roleKey)
                .from(USER_ROLES)
                .join(ROLES).on(ROLES.id.eq(USER_ROLES.roleId))
                .where(USER_ROLES.userId.eq(userId))
                .orderBy(ROLES.roleKey.asc())
                .fetch());
    }

    private Set<String> findPermissionKeys(UUID userId) {
        return new LinkedHashSet<>(queryFactory
                .select(PERMISSIONS.permissionKey)
                .from(USER_ROLES)
                .join(ROLE_PERMISSIONS).on(ROLE_PERMISSIONS.roleId.eq(USER_ROLES.roleId))
                .join(PERMISSIONS).on(PERMISSIONS.id.eq(ROLE_PERMISSIONS.permissionId))
                .where(USER_ROLES.userId.eq(userId))
                .orderBy(PERMISSIONS.permissionKey.asc())
                .fetch());
    }

    private Map<UUID, Set<String>> rolesByUser() {
        List<Tuple> rows = queryFactory
                .select(USER_ROLES.userId, ROLES.roleKey)
                .from(USER_ROLES)
                .join(ROLES).on(ROLES.id.eq(USER_ROLES.roleId))
                .orderBy(ROLES.roleKey.asc())
                .fetch();

        Map<UUID, Set<String>> rolesByUser = new LinkedHashMap<>();
        for (Tuple row : rows) {
            rolesByUser.computeIfAbsent(row.get(USER_ROLES.userId), ignored -> new LinkedHashSet<>())
                    .add(row.get(ROLES.roleKey));
        }
        return rolesByUser;
    }

    private Map<UUID, Set<String>> permissionsByRole() {
        List<Tuple> rows = queryFactory
                .select(ROLE_PERMISSIONS.roleId, PERMISSIONS.permissionKey)
                .from(ROLE_PERMISSIONS)
                .join(PERMISSIONS).on(PERMISSIONS.id.eq(ROLE_PERMISSIONS.permissionId))
                .orderBy(PERMISSIONS.permissionKey.asc())
                .fetch();

        Map<UUID, Set<String>> permissionsByRole = new LinkedHashMap<>();
        for (Tuple row : rows) {
            permissionsByRole.computeIfAbsent(row.get(ROLE_PERMISSIONS.roleId), ignored -> new LinkedHashSet<>())
                    .add(row.get(PERMISSIONS.permissionKey));
        }
        return permissionsByRole;
    }

    static String normalizeEmail(String email) {
        return email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
    }

    record LoginCredential(UUID userId, String passwordHash) {
    }
}
