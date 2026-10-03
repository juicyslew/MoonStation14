package com.juicyslew.moonstation14.ms14.hands.client;

import com.juicyslew.moonstation14.ms14.hands.network.BodyHandStateSnapshot;
import com.juicyslew.moonstation14.ms14.storage.grid.GridGeometry;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class StoragePopupLifecycleTest {
    private static BodyHandClientPolicy.GridView grid(String token, String kind) {
        int width = kind.equals("bag") ? 7 : 8, height = kind.equals("bag") ? 4 : 2;
        var entries = token.equals("bag") ? List.of(new BodyHandStateSnapshot.GridEntry("child", "minecraft:stick", 1, 0, 0, 0)) : List.<BodyHandStateSnapshot.GridEntry>of();
        var host = new BodyHandStateSnapshot.GridHost(token, kind, width, height, entries);
        return new BodyHandClientPolicy.GridView(host,
                GridGeometry.Snapshot.empty(GridGeometry.StorageMask.rectangle(width, height)), true, true);
    }

    @Test void sameIdentityRefreshPreservesGestureAndSizesFromCellLayout() {
        var workspace = new StoragePopupWorkspace(500, 400);
        var key = StoragePopupWorkspace.HostKey.worn(StoragePopupWorkspace.HostKind.BAG, "bag");
        workspace.open(key, true, 10, 10, 1, 1);
        workspace.beginDrag(key, new GridGeometry.Cell(0, 0), GridGeometry.Rotation.DEG_0,
                new GridGeometry.Cell(1, 0), GridGeometry.Rotation.DEG_90);
        StoragePopupLifecycle.reconcile(workspace, true, token -> key, ignored -> grid("bag", "bag"));
        assertNotNull(workspace.window(key).drag());
        assertEquals(StoragePopupLayout.outerWidth(7), workspace.window(key).width());
        assertEquals(StoragePopupLayout.outerHeight(4), workspace.window(key).height());
    }

    @Test void missingMaterialGridRetainsPopupProvisionallyButMissingHostClosesIt() {
        var workspace = new StoragePopupWorkspace(500, 400);
        var key = StoragePopupWorkspace.HostKey.worn(StoragePopupWorkspace.HostKind.BAG, "bag");
        workspace.open(key, true, 10, 10, 100, 80);
        StoragePopupLifecycle.reconcile(workspace, true, token -> key, ignored -> null);
        assertNotNull(workspace.window(key));
        assertTrue(workspace.window(key).provisional());
        StoragePopupLifecycle.reconcile(workspace, true, token -> null, ignored -> null);
        assertNull(workspace.window(key));
    }

    @Test void resolverFindsWornHeldAndPristineHandHosts() {
        var hand = new BodyHandStateSnapshot.Hand("left", "held", "minecraft:stone", 1);
        var view = new BodyHandClientPolicy.InventoryView(hand, null, "left", null, null, null, null);
        var pristine = grid("held", "bag");
        assertEquals(StoragePopupWorkspace.HostKey.held("held"),
                StoragePopupLifecycle.resolveBody("held", view, key -> pristine));
        assertEquals(StoragePopupWorkspace.HostKey.held("held"),
                StoragePopupLifecycle.resolveBody("held", view, key -> null));
        assertNull(StoragePopupLifecycle.resolveBody("elsewhere", view, key -> pristine));
        var workspace = new StoragePopupWorkspace(500, 400);
        var heldKey = StoragePopupWorkspace.HostKey.held("held");
        workspace.open(heldKey, true, 10, 10, 100, 80);
        StoragePopupLifecycle.reconcile(workspace, true,
                token -> StoragePopupLifecycle.resolveBody(token, view, key -> null), key -> null);
        assertNotNull(workspace.window(heldKey));
        assertTrue(workspace.window(heldKey).provisional());
    }

    private static BodyHandClientPolicy.InventoryView observation(String firstToken, String secondToken,
            String beltToken, String backToken) {
        var first = new BodyHandStateSnapshot.Hand("left", firstToken, firstToken == null ? null : "minecraft:stone", firstToken == null ? 0 : 1);
        var second = new BodyHandStateSnapshot.Hand("right", secondToken, secondToken == null ? null : "minecraft:stone", secondToken == null ? 0 : 1);
        BodyHandClientPolicy.EquipmentView belt = beltToken == null ? null : new BodyHandClientPolicy.EquipmentView(
                new BodyHandStateSnapshot.EquipmentSlot("belt", beltToken, "moonstation14:belt", 1, null, null, 0), false, false, false, false);
        BodyHandClientPolicy.EquipmentView back = backToken == null ? null : new BodyHandClientPolicy.EquipmentView(
                new BodyHandStateSnapshot.EquipmentSlot("back", backToken, "moonstation14:bag", 1, null, null, 0), false, false, false, false);
        return new BodyHandClientPolicy.InventoryView(first, second, "left", belt, back, null, null);
    }

    @Test void wornBagHeldInEitherHandAndWornAgainRetainsLogicalWindow() {
        var workspace = new StoragePopupWorkspace(800, 600);
        var bag = StoragePopupWorkspace.HostKey.worn(StoragePopupWorkspace.HostKind.BAG, "bag");
        var held = StoragePopupWorkspace.HostKey.held("bag");
        var pristine = grid("bag", "bag");
        workspace.open(bag, true, 31, 42, 90, 80);
        workspace.select(bag, "child");
        workspace.scroll(bag, 17);
        var hidden = StoragePopupWorkspace.HostKey.worn(StoragePopupWorkspace.HostKind.BELT, "remembered");
        workspace.open(hidden, true, 200, 100, 80, 70); workspace.close(hidden);
        StoragePopupLifecycle.reconcile(workspace, true,
                token -> token.equals("remembered") ? hidden : StoragePopupLifecycle.resolveBody(token,
                        observation("bag", null, null, null), k -> pristine), k -> pristine);
        assertNull(workspace.window(bag));
        // A hidden remembered worn popup must not be reopened by host resolution.
        assertFalse(workspace.window(hidden).visible());
        StoragePopupLifecycle.reconcile(workspace, true,
                token -> token.equals("remembered") ? hidden : StoragePopupLifecycle.resolveBody(token,
                        observation(null, "bag", null, null), k -> pristine), k -> pristine);
        assertNotNull(workspace.window(held));
        assertEquals("child", workspace.window(held).selectedChild());
        assertEquals(17, workspace.window(held).scroll());
        assertEquals(StoragePopupLayout.outerWidth(7), workspace.window(held).width());
        StoragePopupLifecycle.reconcile(workspace, true,
                token -> token.equals("remembered") ? hidden : StoragePopupLifecycle.resolveBody(token,
                        observation(null, null, null, "bag"), k -> pristine), k -> pristine);
        var returned = workspace.window(bag);
        assertNotNull(returned); assertNull(workspace.window(held));
        assertEquals(31, returned.x()); assertEquals(42, returned.y());
        assertEquals("child", returned.selectedChild()); assertTrue(returned.visible());
        assertEquals(2, workspace.windows().size());
        assertFalse(workspace.window(hidden).visible());
    }

    @Test void equipmentResolverUsesEquipmentSlotTokenAndTracksWornToHeld() {
        var wornView = observation(null, null, null, "bag");
        var heldView = observation("bag", null, null, null);
        assertEquals(StoragePopupWorkspace.HostKey.worn(StoragePopupWorkspace.HostKind.BAG, "bag"),
                StoragePopupLifecycle.resolveBody("bag", wornView, key -> null));
        assertEquals(StoragePopupWorkspace.HostKey.held("bag"),
                StoragePopupLifecycle.resolveBody("bag", heldView, key -> null));
    }

    @Test void beltHostMovesThroughHandAndAuthoritativeAbsenceRemovesOnlyThatWindow() {
        var workspace = new StoragePopupWorkspace(800, 600);
        var belt = StoragePopupWorkspace.HostKey.worn(StoragePopupWorkspace.HostKind.BELT, "belt");
        var other = StoragePopupWorkspace.HostKey.worn(StoragePopupWorkspace.HostKind.BAG, "other");
        var held = StoragePopupWorkspace.HostKey.held("belt");
        var grid = grid("belt", "belt");
        workspace.open(belt, true, 15, 25, 80, 70); workspace.open(other, true, 250, 30, 80, 70);
        StoragePopupLifecycle.reconcile(workspace, false, token -> null, key -> null);
        assertNotNull(workspace.window(belt)); assertNotNull(workspace.window(other));
        StoragePopupLifecycle.reconcile(workspace, true,
                token -> StoragePopupLifecycle.resolveBody(token, observation("belt", null, null, "other"),
                        k -> grid(k.token(), k.token().equals("belt") ? "belt" : "bag")),
                k -> grid(k.token(), k.token().equals("belt") ? "belt" : "bag"));
        assertNotNull(workspace.window(held)); assertNull(workspace.window(belt));
        StoragePopupLifecycle.reconcile(workspace, true,
                token -> StoragePopupLifecycle.resolveBody(token, observation(null, null, "belt", "other"),
                        k -> grid(k.token(), k.token().equals("belt") ? "belt" : "bag")),
                k -> grid(k.token(), k.token().equals("belt") ? "belt" : "bag"));
        assertNotNull(workspace.window(belt)); assertNull(workspace.window(held));
        StoragePopupLifecycle.reconcile(workspace, true,
                token -> StoragePopupLifecycle.resolveBody(token, observation(null, null, null, "other"), k -> grid("other", "bag")), k -> grid("other", "bag"));
        assertNull(workspace.window(belt)); assertNotNull(workspace.window(other));
    }

    @Test void worldTransitionRequiresCorrelatedSuccessfulAuthorityMetadata() {
        var id = UUID.randomUUID();
        var world = new StoragePopupWorkspace.HostKey(StoragePopupWorkspace.HostKind.WORLD, null, id);
        var held = StoragePopupWorkspace.HostKey.held("token");
        var metadata = new com.juicyslew.moonstation14.ms14.hands.network.BodyHandActionResult.HostTransition(
                com.juicyslew.moonstation14.ms14.hands.network.BodyHandActionResult.HostTransition.Direction.PICKUP, id, "token");
        var accepted = new com.juicyslew.moonstation14.ms14.hands.network.BodyHandActionResult(1, 1, true,
                com.juicyslew.moonstation14.ms14.hands.network.BodyHandActionResult.Reason.OK, false, 0, null, null, null, 0, metadata);
        assertEquals(new StoragePopupLifecycle.Transition(world, held, metadata.direction()), StoragePopupLifecycle.transition(true, accepted));
        assertNull(StoragePopupLifecycle.transition(false, accepted));
        for (var reason : List.of(com.juicyslew.moonstation14.ms14.hands.network.BodyHandActionResult.Reason.DENIED,
                com.juicyslew.moonstation14.ms14.hands.network.BodyHandActionResult.Reason.RECOVERY_REQUIRED)) {
            assertNull(StoragePopupLifecycle.transition(true, new com.juicyslew.moonstation14.ms14.hands.network.BodyHandActionResult(
                    1, 2, false, reason, false, 0, null, null, null, 0)));
        }
    }
}
