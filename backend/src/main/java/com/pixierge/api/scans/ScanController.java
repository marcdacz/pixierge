package com.pixierge.api.scans;

import com.pixierge.api.identity.AuthenticatedUser;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
public class ScanController {

    private final ScanService scanService;

    public ScanController(ScanService scanService) {
        this.scanService = scanService;
    }

    @PostMapping("/api/libraries/{libraryId}/scans")
    @ResponseStatus(HttpStatus.ACCEPTED)
    ScanRunResponse scanLibrary(
            @PathVariable UUID libraryId,
            @AuthenticationPrincipal AuthenticatedUser user
    ) {
        return scanService.scanLibrary(libraryId, user.id());
    }

    @PostMapping("/api/libraries/{libraryId}/roots/{rootId}/scans")
    @ResponseStatus(HttpStatus.ACCEPTED)
    ScanRunResponse scanRoot(
            @PathVariable UUID libraryId,
            @PathVariable UUID rootId,
            @AuthenticationPrincipal AuthenticatedUser user
    ) {
        return scanService.scanRoot(libraryId, rootId, user.id());
    }

    @GetMapping("/api/libraries/{libraryId}/scans")
    List<ScanRunResponse> listLibraryScans(@PathVariable UUID libraryId) {
        return scanService.listLibraryScans(libraryId);
    }

    @GetMapping("/api/scans/active")
    List<ActiveScanResponse> listActiveScans() {
        return scanService.listActiveScans();
    }

    @GetMapping("/api/scans/{scanRunId}")
    ScanRunResponse getScan(@PathVariable UUID scanRunId) {
        return scanService.getScan(scanRunId);
    }
}
