package com.cotani.permission;

import com.cotani.permission.api.PermissionGroup;
import com.cotani.permission.api.PermissionRepository;
import com.cotani.permission.api.PermissionService;
import com.cotani.permission.internal.InMemoryPermissionService;
import com.cotani.permission.storage.StoragePermissionRepository;
import com.cotani.storage.api.CotaniStorage;
import com.cotani.storage.migration.Migration;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletionStage;

/** Factories for the {@code cotani-permission} module. */
public final class CotaniPermissions {
    private CotaniPermissions() {}

    /** Creates an isolated, thread-safe in-memory permission service. */
    public static PermissionService inMemory() {
        return new InMemoryPermissionService(List.of());
    }

    /** Creates an in-memory service with the supplied initial group definitions. */
    public static PermissionService inMemory(PermissionGroup... groups) {
        Objects.requireNonNull(groups, "groups");
        return new InMemoryPermissionService(Arrays.asList(groups.clone()));
    }

    /** Loads permission state asynchronously from a repository. */
    public static CompletionStage<PermissionService> fromRepositoryAsync(PermissionRepository repository) {
        Objects.requireNonNull(repository, "repository");
        return repository.loadAsync().thenApply(snapshot -> new InMemoryPermissionService(snapshot, repository));
    }

    /** Loads permission state asynchronously from Cotani Storage. */
    public static CompletionStage<PermissionService> storageAsync(CotaniStorage storage) {
        Objects.requireNonNull(storage, "storage");
        return fromRepositoryAsync(new StoragePermissionRepository(storage));
    }

    /** Returns the migrations required by the Cotani Storage repository. */
    public static List<Migration> migrations() {
        return StoragePermissionRepository.migrations();
    }
}
