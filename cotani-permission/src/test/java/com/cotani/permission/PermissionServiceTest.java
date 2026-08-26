package com.cotani.permission;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.cotani.permission.api.PermissionAssignments;
import com.cotani.permission.api.PermissionGroup;
import com.cotani.permission.api.PermissionNode;
import com.cotani.permission.api.PermissionOrigin;
import com.cotani.permission.api.PermissionRepository;
import com.cotani.permission.api.PermissionSnapshot;
import com.cotani.permission.api.PermissionState;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.Test;

class PermissionServiceTest {
    @Test
    void resolvesUserOverrideAndGroupWildcard() {
        var group = PermissionGroup.builder("moderator")
                .priority(10)
                .allow("server.moderation.*")
                .deny("server.moderation.ban")
                .build();
        var service = CotaniPermissions.inMemory(group);
        var userId = UUID.randomUUID();

        service.assignGroupAsync(userId, "moderator").toCompletableFuture().join();

        var allowed = service.checkAsync(userId, "server.moderation.kick")
                .toCompletableFuture()
                .join();
        var denied = service.checkAsync(userId, "server.moderation.ban")
                .toCompletableFuture()
                .join();

        assertTrue(allowed.allowed());
        assertEquals(PermissionOrigin.GROUP, allowed.origin());
        assertFalse(denied.allowed());
        assertEquals(PermissionState.DENY, denied.state());

        service.allowAsync(userId, "server.moderation.ban")
                .toCompletableFuture()
                .join();
        var userDecision = service.checkAsync(userId, "server.moderation.ban")
                .toCompletableFuture()
                .join();
        assertTrue(userDecision.allowed());
        assertEquals(PermissionOrigin.USER, userDecision.origin());
    }

    @Test
    void resolvesGroupPriorityDeterministically() {
        var low = PermissionGroup.builder("low").priority(1).allow("chat.*").build();
        var high = PermissionGroup.builder("high").priority(2).deny("chat.*").build();
        var service = CotaniPermissions.inMemory(low, high);
        var userId = UUID.randomUUID();

        service.assignGroupAsync(userId, "low").toCompletableFuture().join();
        service.assignGroupAsync(userId, "high").toCompletableFuture().join();

        var decision =
                service.checkAsync(userId, "chat.send").toCompletableFuture().join();

        assertEquals(PermissionState.DENY, decision.state());
        assertEquals("high", decision.sourceId());
        assertEquals(PermissionNode.of("chat.*"), decision.matchedPermission());
    }

    @Test
    void returnsUnsetAndSupportsRemoval() {
        var service = CotaniPermissions.inMemory();
        var userId = UUID.randomUUID();

        service.allowAsync(userId, "feature.use").toCompletableFuture().join();
        assertTrue(service.checkAsync(userId, "feature.use")
                .toCompletableFuture()
                .join()
                .allowed());

        service.unsetAsync(userId, "feature.use").toCompletableFuture().join();

        var decision =
                service.checkAsync(userId, "feature.use").toCompletableFuture().join();
        assertEquals(PermissionState.UNSET, decision.state());
        assertEquals(PermissionOrigin.DEFAULT, decision.origin());
    }

    @Test
    void normalizesGroupNamesAndRemovesAssignmentsWithTheGroup() {
        var service = CotaniPermissions.inMemory(
                PermissionGroup.builder("Moderator").allow("chat.*").build());
        var userId = UUID.randomUUID();

        service.assignGroupAsync(userId, " MODERATOR ").toCompletableFuture().join();
        assertEquals(
                Set.of("moderator"),
                service.groupsAsync(userId).toCompletableFuture().join());

        service.unregisterGroupAsync(" MODERATOR ").toCompletableFuture().join();

        assertTrue(service.groupsAsync(userId).toCompletableFuture().join().isEmpty());
        assertEquals(
                PermissionState.UNSET,
                service.checkAsync(userId, "chat.send")
                        .toCompletableFuture()
                        .join()
                        .state());
    }

    @Test
    void concurrentAssignmentAndUnregisterCannotLeaveAStaleGroupAssignment() {
        var service = CotaniPermissions.inMemory(
                PermissionGroup.builder("temporary").allow("feature.*").build());
        var userId = UUID.randomUUID();
        var executor = Executors.newFixedThreadPool(2);

        try {
            var unregister = CompletableFuture.supplyAsync(
                    () -> service.unregisterGroupAsync("temporary")
                            .toCompletableFuture()
                            .join(),
                    executor);
            var assign = CompletableFuture.supplyAsync(
                    () -> service.assignGroupAsync(userId, "temporary")
                            .toCompletableFuture()
                            .join(),
                    executor);

            CompletableFuture.allOf(unregister, assign)
                    .handle((ignored, failure) -> null)
                    .join();

            assertTrue(service.groupsAsync(userId).toCompletableFuture().join().isEmpty());
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void returnsFailedStagesAfterClose() {
        var service = CotaniPermissions.inMemory();
        service.closeAsync().toCompletableFuture().join();

        var failure = assertThrows(
                CompletionException.class,
                () -> service.checkAsync(UUID.randomUUID(), "anything")
                        .toCompletableFuture()
                        .join());

        assertTrue(failure.getCause() instanceof IllegalStateException);
    }

    @Test
    void persistsEachMutationInOrderThroughRepository() {
        var repository = new RecordingRepository();
        var service = CotaniPermissions.fromRepositoryAsync(repository)
                .toCompletableFuture()
                .join();
        var userId = UUID.randomUUID();

        service.allowAsync(userId, "feature.use").toCompletableFuture().join();
        service.denyAsync(userId, "feature.use").toCompletableFuture().join();

        assertEquals(2, repository.saved().size());
        assertEquals(
                PermissionState.DENY,
                repository
                        .saved()
                        .get(repository.saved().size() - 1)
                        .users()
                        .getOrDefault(userId, PermissionAssignments.empty())
                        .permissions()
                        .getOrDefault(PermissionNode.of("feature.use"), PermissionState.UNSET));
    }

    private static final class RecordingRepository implements PermissionRepository {
        private final java.util.List<PermissionSnapshot> saved = new java.util.ArrayList<>();

        @Override
        public CompletionStage<PermissionSnapshot> loadAsync() {
            return CompletableFuture.completedFuture(PermissionSnapshot.empty());
        }

        @Override
        public CompletionStage<Void> saveAsync(PermissionSnapshot snapshot) {
            saved.add(snapshot);
            return CompletableFuture.completedFuture(null);
        }

        private java.util.List<PermissionSnapshot> saved() {
            return saved;
        }
    }
}
