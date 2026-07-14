package com.pixierge.api.tags;

import com.pixierge.api.assets.AssetBrowseResponse;
import com.pixierge.api.assets.AssetService;
import com.pixierge.api.identity.AuthenticatedUser;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TagServiceTest {

    private static final UUID USER_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID TAG_ID = UUID.fromString("00000000-0000-0000-0000-000000000002");
    private static final UUID SECOND_TAG_ID = UUID.fromString("00000000-0000-0000-0000-000000000003");
    private static final UUID ASSET_ID = UUID.fromString("00000000-0000-0000-0000-000000000004");
    private static final UUID SECOND_ASSET_ID = UUID.fromString("00000000-0000-0000-0000-000000000005");
    private static final UUID LIBRARY_ID = UUID.fromString("00000000-0000-0000-0000-000000000006");

    private final AuthenticatedUser user = new AuthenticatedUser(USER_ID, "admin", Set.of(), Set.of(), "csrf");

    @Test
    void listReturnsOwnedTagsInRepositoryOrder() {
        FakeTagRepository repository = new FakeTagRepository();
        repository.tags.add(tag(TAG_ID, "Family"));
        repository.tags.add(tag(SECOND_TAG_ID, "Travel"));
        TagService service = new TagService(repository, new FakeAssetService());

        List<TagResponse> tags = service.list(user);

        assertThat(tags).extracting(TagResponse::id, TagResponse::name)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple(TAG_ID, "Family"),
                        org.assertj.core.groups.Tuple.tuple(SECOND_TAG_ID, "Travel")
                );
        assertThat(repository.lastListedOwner).isEqualTo(USER_ID);
    }

    @Test
    void createTrimsAndNormalizesNamesBeforeReturningCreatedTag() {
        FakeTagRepository repository = new FakeTagRepository();
        repository.nextCreatedId = TAG_ID;
        repository.tags.add(tag(TAG_ID, "Family"));
        TagService service = new TagService(repository, new FakeAssetService());

        TagResponse response = service.create(new CreateTagRequest("  Family  "), user);

        assertThat(response.id()).isEqualTo(TAG_ID);
        assertThat(response.name()).isEqualTo("Family");
        assertThat(repository.created).containsExactly("Family:family");
    }

    @Test
    void createRejectsBlankAndTooLongNames() {
        TagService service = new TagService(new FakeTagRepository(), new FakeAssetService());

        assertThatThrownBy(() -> service.create(new CreateTagRequest("   "), user))
                .isInstanceOfSatisfying(ResponseStatusException.class, exception ->
                        assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST));

        assertThatThrownBy(() -> service.create(new CreateTagRequest("x".repeat(81)), user))
                .isInstanceOfSatisfying(ResponseStatusException.class, exception ->
                        assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST));
    }

    @Test
    void createMapsDuplicateNamesToConflict() {
        FakeTagRepository repository = new FakeTagRepository();
        repository.createFailure = new DataIntegrityViolationException("duplicate key value violates unique constraint");
        TagService service = new TagService(repository, new FakeAssetService());

        assertThatThrownBy(() -> service.create(new CreateTagRequest("Family"), user))
                .isInstanceOfSatisfying(ResponseStatusException.class, exception ->
                        assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.CONFLICT));
    }

    @Test
    void renameRequiresExistingOwnedTag() {
        FakeTagRepository repository = new FakeTagRepository();
        repository.renameResult = false;
        TagService service = new TagService(repository, new FakeAssetService());

        assertThatThrownBy(() -> service.rename(TAG_ID, new UpdateTagRequest("Travel"), user))
                .isInstanceOfSatisfying(ResponseStatusException.class, exception ->
                        assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND));
    }

    @Test
    void renameTrimsNameAndReturnsUpdatedTag() {
        FakeTagRepository repository = new FakeTagRepository();
        repository.tags.add(tag(TAG_ID, "Travel"));
        TagService service = new TagService(repository, new FakeAssetService());

        TagResponse response = service.rename(TAG_ID, new UpdateTagRequest("  Travel  "), user);

        assertThat(response.name()).isEqualTo("Travel");
        assertThat(repository.renamed).containsExactly(TAG_ID + ":Travel:travel:" + USER_ID);
    }

    @Test
    void renameMapsDuplicateNamesToConflictAndRethrowsUnexpectedIntegrityFailures() {
        FakeTagRepository duplicateRepository = new FakeTagRepository();
        duplicateRepository.renameFailure =
                new DataIntegrityViolationException("duplicate key value violates unique constraint");
        TagService duplicateService = new TagService(duplicateRepository, new FakeAssetService());

        assertThatThrownBy(() -> duplicateService.rename(TAG_ID, new UpdateTagRequest("Travel"), user))
                .isInstanceOfSatisfying(ResponseStatusException.class, exception ->
                        assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.CONFLICT));

        FakeTagRepository unexpectedRepository = new FakeTagRepository();
        unexpectedRepository.renameFailure = new DataIntegrityViolationException("foreign key violation");
        TagService unexpectedService = new TagService(unexpectedRepository, new FakeAssetService());

        assertThatThrownBy(() -> unexpectedService.rename(TAG_ID, new UpdateTagRequest("Travel"), user))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void deleteRequiresExistingOwnedTag() {
        FakeTagRepository repository = new FakeTagRepository();
        repository.deleteResult = false;
        TagService service = new TagService(repository, new FakeAssetService());

        assertThatThrownBy(() -> service.delete(TAG_ID, user))
                .isInstanceOfSatisfying(ResponseStatusException.class, exception ->
                        assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND));
    }

    @Test
    void addAssignmentsDeduplicatesTagsAndRequiresReadableAssets() {
        FakeTagRepository repository = new FakeTagRepository();
        repository.ownsAllResult = true;
        FakeAssetService assetService = new FakeAssetService();
        TagService service = new TagService(repository, assetService);

        service.addAssignments(new AssignAssetTagsRequest(
                List.of(TAG_ID, TAG_ID, SECOND_TAG_ID),
                List.of(new AssetItemRequest(ASSET_ID, LIBRARY_ID), new AssetItemRequest(SECOND_ASSET_ID, LIBRARY_ID))
        ), user);

        assertThat(repository.ownershipChecks).containsExactly(List.of(TAG_ID, SECOND_TAG_ID));
        assertThat(assetService.readableChecks).containsExactly(
                ASSET_ID + ":" + LIBRARY_ID,
                SECOND_ASSET_ID + ":" + LIBRARY_ID
        );
        assertThat(repository.addedAssignments).containsExactly(
                TAG_ID + ":" + ASSET_ID + ":" + LIBRARY_ID + ":" + USER_ID,
                TAG_ID + ":" + SECOND_ASSET_ID + ":" + LIBRARY_ID + ":" + USER_ID,
                SECOND_TAG_ID + ":" + ASSET_ID + ":" + LIBRARY_ID + ":" + USER_ID,
                SECOND_TAG_ID + ":" + SECOND_ASSET_ID + ":" + LIBRARY_ID + ":" + USER_ID
        );
    }

    @Test
    void addAssignmentsRejectsMissingTagsOrItems() {
        TagService service = new TagService(new FakeTagRepository(), new FakeAssetService());

        assertThatThrownBy(() -> service.addAssignments(new AssignAssetTagsRequest(List.of(), List.of()), user))
                .isInstanceOfSatisfying(ResponseStatusException.class, exception ->
                        assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST));
    }

    @Test
    void addAssignmentsRejectsTagsNotOwnedByUser() {
        FakeTagRepository repository = new FakeTagRepository();
        repository.ownsAllResult = false;
        TagService service = new TagService(repository, new FakeAssetService());

        assertThatThrownBy(() -> service.addAssignments(new AssignAssetTagsRequest(
                List.of(TAG_ID),
                List.of(new AssetItemRequest(ASSET_ID, LIBRARY_ID))
        ), user))
                .isInstanceOfSatisfying(ResponseStatusException.class, exception ->
                        assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND));
    }

    @Test
    void addAssignmentsIgnoresDuplicateAssignmentConflictsOnly() {
        FakeTagRepository repository = new FakeTagRepository();
        repository.ownsAllResult = true;
        repository.addFailure = new DataIntegrityViolationException("duplicate key value violates unique constraint");
        TagService service = new TagService(repository, new FakeAssetService());

        service.addAssignments(new AssignAssetTagsRequest(
                List.of(TAG_ID),
                List.of(new AssetItemRequest(ASSET_ID, LIBRARY_ID))
        ), user);

        assertThat(repository.addAttempts).isEqualTo(1);
    }

    @Test
    void addAssignmentsRethrowsUnexpectedIntegrityFailures() {
        FakeTagRepository repository = new FakeTagRepository();
        repository.ownsAllResult = true;
        repository.addFailure = new DataIntegrityViolationException("foreign key violation");
        TagService service = new TagService(repository, new FakeAssetService());

        assertThatThrownBy(() -> service.addAssignments(new AssignAssetTagsRequest(
                List.of(TAG_ID),
                List.of(new AssetItemRequest(ASSET_ID, LIBRARY_ID))
        ), user))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void deleteAssignmentsDeduplicatesIdsAndRequiresOwnership() {
        FakeTagRepository repository = new FakeTagRepository();
        repository.ownsAllResult = true;
        TagService service = new TagService(repository, new FakeAssetService());

        service.deleteAssignments(new DeleteAssetTagsRequest(
                List.of(TAG_ID, TAG_ID, SECOND_TAG_ID),
                List.of(ASSET_ID, ASSET_ID, SECOND_ASSET_ID)
        ), user);

        assertThat(repository.ownershipChecks).containsExactly(List.of(TAG_ID, SECOND_TAG_ID));
        assertThat(repository.deletedAssignments).containsExactly(
                USER_ID,
                TAG_ID,
                SECOND_TAG_ID,
                ASSET_ID,
                SECOND_ASSET_ID
        );
    }

    @Test
    void deleteAssignmentsRejectsMissingTagsOrAssetsAndRequiresOwnership() {
        TagService badRequestService = new TagService(new FakeTagRepository(), new FakeAssetService());

        assertThatThrownBy(() -> badRequestService.deleteAssignments(
                new DeleteAssetTagsRequest(List.of(), List.of(ASSET_ID)), user))
                .isInstanceOfSatisfying(ResponseStatusException.class, exception ->
                        assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST));

        FakeTagRepository repository = new FakeTagRepository();
        repository.ownsAllResult = false;
        TagService notFoundService = new TagService(repository, new FakeAssetService());

        assertThatThrownBy(() -> notFoundService.deleteAssignments(
                new DeleteAssetTagsRequest(List.of(TAG_ID), List.of(ASSET_ID)), user))
                .isInstanceOfSatisfying(ResponseStatusException.class, exception ->
                        assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND));
    }

    @Test
    void browseAssetsRequiresTheTagBeforeDelegating() {
        FakeTagRepository repository = new FakeTagRepository();
        repository.tags.add(tag(TAG_ID, "Family"));
        FakeAssetService assetService = new FakeAssetService();
        TagService service = new TagService(repository, assetService);

        service.browseAssets(TAG_ID, user, 2, 12);

        assertThat(assetService.browsedTags).containsExactly(TAG_ID + ":2:12");
    }

    private static TagRepository.TagRecord tag(UUID id, String name) {
        OffsetDateTime now = OffsetDateTime.parse("2026-07-13T00:00:00Z");
        return new TagRepository.TagRecord(id, name, now, now, 0);
    }

    private static final class FakeTagRepository extends TagRepository {

        private final List<TagRecord> tags = new ArrayList<>();
        private final List<String> created = new ArrayList<>();
        private final List<String> renamed = new ArrayList<>();
        private final List<List<UUID>> ownershipChecks = new ArrayList<>();
        private final List<String> addedAssignments = new ArrayList<>();
        private final List<Object> deletedAssignments = new ArrayList<>();
        private UUID lastListedOwner;
        private UUID nextCreatedId = UUID.randomUUID();
        private boolean ownsAllResult = true;
        private boolean renameResult = true;
        private boolean deleteResult = true;
        private DataIntegrityViolationException createFailure;
        private DataIntegrityViolationException renameFailure;
        private DataIntegrityViolationException addFailure;
        private int addAttempts;

        private FakeTagRepository() {
            super(null);
        }

        @Override
        public List<TagRecord> list(UUID ownerUserId) {
            lastListedOwner = ownerUserId;
            return tags;
        }

        @Override
        public Optional<TagRecord> find(UUID tagId, UUID ownerUserId) {
            return tags.stream().filter(tag -> tag.id().equals(tagId)).findFirst();
        }

        @Override
        public UUID create(UUID ownerUserId, String name, String normalizedName) {
            if (createFailure != null) {
                throw createFailure;
            }
            created.add(name + ":" + normalizedName);
            return nextCreatedId;
        }

        @Override
        public boolean rename(UUID id, UUID ownerUserId, String name, String normalizedName) {
            if (renameFailure != null) {
                throw renameFailure;
            }
            renamed.add(id + ":" + name + ":" + normalizedName + ":" + ownerUserId);
            return renameResult;
        }

        @Override
        public boolean delete(UUID id, UUID ownerUserId) {
            return deleteResult;
        }

        @Override
        public boolean ownsAll(UUID ownerUserId, List<UUID> tagIds) {
            ownershipChecks.add(tagIds);
            return ownsAllResult;
        }

        @Override
        public void add(UUID tagId, UUID assetId, UUID libraryId, UUID userId) {
            addAttempts++;
            if (addFailure != null) {
                throw addFailure;
            }
            addedAssignments.add(tagId + ":" + assetId + ":" + libraryId + ":" + userId);
        }

        @Override
        public void deleteAssignments(UUID ownerUserId, List<UUID> tagIds, List<UUID> assetIds) {
            deletedAssignments.addAll(List.of(ownerUserId));
            deletedAssignments.addAll(tagIds);
            deletedAssignments.addAll(assetIds);
        }
    }

    private static final class FakeAssetService extends AssetService {

        private final List<String> readableChecks = new ArrayList<>();
        private final List<String> browsedTags = new ArrayList<>();

        private FakeAssetService() {
            super(null, null, null, null);
        }

        @Override
        public void requireReadableAssetInLibrary(AuthenticatedUser user, UUID assetId, UUID libraryId) {
            readableChecks.add(assetId + ":" + libraryId);
        }

        @Override
        public AssetBrowseResponse browseTagAssets(AuthenticatedUser user, UUID tagId, Integer page, Integer pageSize) {
            browsedTags.add(tagId + ":" + page + ":" + pageSize);
            return new AssetBrowseResponse(List.of(), 0, page, pageSize, false);
        }
    }
}
