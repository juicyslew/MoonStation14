package com.juicyslew.moonstation14.ms14.hands.client;

import com.juicyslew.moonstation14.ms14.hands.network.BodyHandActionResult;
import com.juicyslew.moonstation14.ms14.hands.network.BodyHandStateSnapshot;

import java.util.UUID;
import java.util.function.Function;
import java.util.List;

/** Logical popup continuity across authoritative changes of host location. */
final class StoragePopupLifecycle {
    private StoragePopupLifecycle() { }

    static Transition transition(boolean correlated, BodyHandActionResult result) {
        if (!correlated || result == null || !result.accepted() || result.reason() != BodyHandActionResult.Reason.OK)
            return null;
        var metadata = result.hostTransition();
        if (metadata == null || metadata.worldEntity() == null || metadata.hostToken() == null) return null;
        var world = StoragePopupWorkspace.HostKey.world(metadata.worldEntity());
        var held = StoragePopupWorkspace.HostKey.held(metadata.hostToken());
        return metadata.direction() == BodyHandActionResult.HostTransition.Direction.PICKUP
                ? new Transition(world, held, metadata.direction())
                : new Transition(held, world, metadata.direction());
    }

    /** Resolve a body host from one settled inventory observation, without consulting client state. */
    static StoragePopupWorkspace.HostKey resolveBody(String token, BodyHandClientPolicy.InventoryView view,
            Function<StoragePopupWorkspace.HostKey, BodyHandClientPolicy.GridView> gridResolver) {
        if (view == null || token == null) return null;
        if (view.back() != null && view.back().slot() != null && token.equals(view.back().slot().token()))
            return StoragePopupWorkspace.HostKey.worn(StoragePopupWorkspace.HostKind.BAG, token);
        if (view.belt() != null && view.belt().slot() != null && token.equals(view.belt().slot().token()))
            return StoragePopupWorkspace.HostKey.worn(StoragePopupWorkspace.HostKind.BELT, token);
        if ((view.first() != null && token.equals(view.first().token())
                || view.second() != null && token.equals(view.second().token()))) {
            return StoragePopupWorkspace.HostKey.held(token);
        }
        return null;
    }

    /** Reconcile every body-hosted popup against one complete inventory observation. */
    static void reconcile(StoragePopupWorkspace workspace, boolean authoritative,
                          Function<String, StoragePopupWorkspace.HostKey> hostResolver,
                          Function<StoragePopupWorkspace.HostKey, BodyHandClientPolicy.GridView> gridResolver) {
        if (!authoritative) return;
        for (var window : workspace.windows()) {
            var old = window.key();
            if (old.kind() == StoragePopupWorkspace.HostKind.WORLD) continue;
            var resolved = hostResolver.apply(old.token());
            if (resolved == null) { workspace.snapshot(old, true, false, List.of()); continue; }
            if (!old.equals(resolved)) workspace.rekey(old, resolved);
            var grid = gridResolver.apply(resolved);
            if (grid == null) workspace.snapshot(resolved, false, true, List.of());
            else {
                workspace.snapshot(resolved, true, true,
                        grid.host().entries().stream().map(BodyHandStateSnapshot.GridEntry::token).toList());
                workspace.resizeWindow(resolved, StoragePopupLayout.outerWidth(grid.host().width()),
                        StoragePopupLayout.outerHeight(grid.host().height()));
            }
        }
    }

    record Transition(StoragePopupWorkspace.HostKey source, StoragePopupWorkspace.HostKey destination,
                      BodyHandActionResult.HostTransition.Direction direction) { }
}

