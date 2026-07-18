package com.pixierge.api.filesystem;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pixierge.api.background.BackgroundJobCreate;
import com.pixierge.api.background.BackgroundJobService;
import com.pixierge.api.libraries.LibraryRepository;
import com.pixierge.api.scans.MediaFileSupport;
import com.pixierge.api.scans.ScanJobTypes;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.ClosedWatchServiceException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardWatchEventKinds;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

@Component
@ConditionalOnProperty(prefix = "pixierge.filesystem-watcher", name = "enabled", havingValue = "true", matchIfMissing = true)
class LibraryFilesystemWatcher implements DisposableBean {

    private static final Logger log = LoggerFactory.getLogger(LibraryFilesystemWatcher.class);

    private final LibraryRepository libraryRepository;
    private final BackgroundJobService backgroundJobService;
    private final ObjectMapper objectMapper;
    private final FilesystemWatcherHealth watcherHealth;
    private final Duration debounce;
    private final Duration registrationRefreshInterval;
    private final Map<WatchKey, WatchedDirectory> watchedDirectories = new ConcurrentHashMap<>();
    private final Map<Path, WatchedDirectory> watchedPaths = new ConcurrentHashMap<>();
    private volatile boolean running;
    private WatchService watchService;
    private Thread watcherThread;
    private OffsetDateTime nextRegistrationRefresh = OffsetDateTime.MIN;

    LibraryFilesystemWatcher(
            LibraryRepository libraryRepository,
            BackgroundJobService backgroundJobService,
            ObjectMapper objectMapper,
            FilesystemWatcherHealth watcherHealth,
            @Value("${pixierge.filesystem-watcher.debounce:2s}") Duration debounce,
            @Value("${pixierge.filesystem-watcher.registration-refresh-interval:30s}") Duration registrationRefreshInterval
    ) {
        this.libraryRepository = libraryRepository;
        this.backgroundJobService = backgroundJobService;
        this.objectMapper = objectMapper;
        this.watcherHealth = watcherHealth;
        this.debounce = debounce;
        this.registrationRefreshInterval = registrationRefreshInterval;
    }

    @EventListener(ApplicationReadyEvent.class)
    void start() {
        if (running) {
            return;
        }
        try {
            watchService = FileSystems.getDefault().newWatchService();
        } catch (IOException exception) {
            log.warn("Filesystem watcher could not start", exception);
            watcherHealth.recordDegraded("watcher_start_failed", exception.getMessage());
            return;
        }
        running = true;
        watcherHealth.recordStarted();
        watcherThread = new Thread(this::runLoop, "pixierge-filesystem-watcher");
        watcherThread.setDaemon(true);
        watcherThread.start();
    }

    private void runLoop() {
        while (running) {
            refreshRegistrationsIfDue();
            try {
                WatchKey key = watchService.poll(1, TimeUnit.SECONDS);
                if (key == null) {
                    continue;
                }
                handleKey(key);
            } catch (ClosedWatchServiceException exception) {
                return;
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                return;
            } catch (RuntimeException exception) {
                log.warn("Filesystem watcher loop failed", exception);
                watcherHealth.recordDegraded("watcher_loop_failed", exception.getMessage());
            }
        }
    }

    private void refreshRegistrationsIfDue() {
        OffsetDateTime now = OffsetDateTime.now();
        if (now.isBefore(nextRegistrationRefresh)) {
            return;
        }
        nextRegistrationRefresh = now.plus(registrationRefreshInterval);
        int rootCount = 0;
        boolean healthy = true;
        for (LibraryRepository.LibraryRecord library : libraryRepository.listLibraries()) {
            if (!"active".equals(library.status())) {
                continue;
            }
            for (LibraryRepository.LibraryRootRecord root : library.roots()) {
                rootCount++;
                healthy = registerTree(library.id(), root.id(), Path.of(root.normalizedPath())) && healthy;
            }
        }
        watcherHealth.recordRegistrationRefresh(rootCount, watchedPaths.size(), healthy);
    }

    private boolean registerTree(UUID libraryId, UUID rootId, Path rootPath) {
        if (!Files.isDirectory(rootPath) || !Files.isReadable(rootPath)) {
            watcherHealth.recordDegraded("root_unavailable", "Source root is unavailable: " + rootPath);
            enqueueChange(libraryId, rootId, rootPath, "root_unavailable");
            return false;
        }
        try (var paths = Files.walk(rootPath)) {
            paths.filter(Files::isDirectory)
                    .forEach(path -> registerDirectory(libraryId, rootId, rootPath, path));
            return true;
        } catch (IOException | SecurityException exception) {
            log.debug("Could not register filesystem watcher path {}", rootPath, exception);
            watcherHealth.recordDegraded("root_registration_failed", exception.getMessage());
            enqueueChange(libraryId, rootId, rootPath, "root_registration_failed");
            return false;
        }
    }

    private void registerDirectory(UUID libraryId, UUID rootId, Path rootPath, Path directory) {
        Path normalized = directory.toAbsolutePath().normalize();
        if (watchedPaths.containsKey(normalized)) {
            return;
        }
        try {
            WatchKey key = normalized.register(
                    watchService,
                    StandardWatchEventKinds.ENTRY_CREATE,
                    StandardWatchEventKinds.ENTRY_MODIFY,
                    StandardWatchEventKinds.OVERFLOW
            );
            WatchedDirectory watched = new WatchedDirectory(
                    libraryId,
                    rootId,
                    rootPath.toAbsolutePath().normalize(),
                    normalized
            );
            watchedPaths.put(normalized, watched);
            watchedDirectories.put(key, watched);
        } catch (IOException | SecurityException exception) {
            log.debug("Could not register filesystem watcher directory {}", normalized, exception);
            watcherHealth.recordDegraded("directory_registration_failed", exception.getMessage());
            enqueueChange(libraryId, rootId, rootPath, "directory_registration_failed");
        }
    }

    private void handleKey(WatchKey key) {
        WatchedDirectory watched = watchedDirectories.get(key);
        if (watched == null) {
            key.reset();
            return;
        }
        for (WatchEvent<?> event : key.pollEvents()) {
            if (event.kind() == StandardWatchEventKinds.OVERFLOW) {
                watcherHealth.recordOverflow("Filesystem watcher overflow under " + watched.rootPath());
                enqueueChange(watched, watched.rootPath(), "overflow");
                continue;
            }
            Path changed = watched.path().resolve((Path) event.context()).toAbsolutePath().normalize();
            if (event.kind() == StandardWatchEventKinds.ENTRY_CREATE && Files.isDirectory(changed)) {
                registerTree(watched.libraryId(), watched.rootId(), watched.rootPath(), changed);
                enqueueChange(watched, changed, "directory_created");
                continue;
            }
            if (Files.isRegularFile(changed) && MediaFileSupport.isSupportedMedia(changed)) {
                enqueueChange(watched, changed, event.kind() == StandardWatchEventKinds.ENTRY_CREATE
                        ? "file_created"
                        : "file_modified");
            }
        }
        if (!key.reset()) {
            watchedDirectories.remove(key);
            watchedPaths.remove(watched.path());
        }
    }

    private void registerTree(UUID libraryId, UUID rootId, Path rootPath, Path newSubtree) {
        if (!Files.isDirectory(newSubtree) || !Files.isReadable(newSubtree)) {
            watcherHealth.recordDegraded("directory_unavailable", "Directory is unavailable: " + newSubtree);
            return;
        }
        try (var paths = Files.walk(newSubtree)) {
            paths.filter(Files::isDirectory)
                    .forEach(path -> registerDirectory(libraryId, rootId, rootPath, path));
        } catch (IOException | SecurityException exception) {
            log.debug("Could not register filesystem watcher subtree {}", newSubtree, exception);
            watcherHealth.recordDegraded("directory_registration_failed", exception.getMessage());
            enqueueChange(libraryId, rootId, rootPath, "directory_registration_failed");
        }
    }

    private void enqueueChange(WatchedDirectory watched, Path changed, String eventType) {
        enqueueChange(watched.libraryId(), watched.rootId(), changed, eventType);
    }

    void enqueueChange(UUID libraryId, UUID rootId, Path changed, String eventType) {
        try {
            String payload = objectMapper.writeValueAsString(new FilesystemChangeJobPayload(
                    libraryId,
                    rootId,
                    changed.toString(),
                    eventType
            ));
            backgroundJobService.enqueue(new BackgroundJobCreate(
                    ScanJobTypes.FILESYSTEM_CHANGE_EVENT,
                    payload,
                    10,
                    3,
                    OffsetDateTime.now().plus(debounce),
                    "filesystem-change:" + libraryId,
                    ScanJobTypes.FILESYSTEM_CHANGE_EVENT + ":" + rootId + ":" + changed
            ));
        } catch (JsonProcessingException exception) {
            log.warn("Could not enqueue filesystem change event for {}", changed, exception);
        }
    }

    @Override
    public void destroy() throws Exception {
        running = false;
        if (watchService != null) {
            watchService.close();
        }
        if (watcherThread != null) {
            watcherThread.interrupt();
        }
        watcherHealth.recordStopped();
    }

    private record WatchedDirectory(UUID libraryId, UUID rootId, Path rootPath, Path path) {
    }
}
