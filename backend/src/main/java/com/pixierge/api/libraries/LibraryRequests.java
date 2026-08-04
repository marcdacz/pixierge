package com.pixierge.api.libraries;

record CreateLibraryRequest(String name) {
}

record UpdateLibraryRequest(String name) {
}

record AddLibraryRootRequest(String path) {
}

record AddExclusionPatternRequest(String pattern) {
}

record RenameFolderRequest(String path, String name) {
}

record AddLibraryMemberRequest(java.util.UUID userId, String role) {
}

record ChangeLibraryMemberRoleRequest(String role) {
}
