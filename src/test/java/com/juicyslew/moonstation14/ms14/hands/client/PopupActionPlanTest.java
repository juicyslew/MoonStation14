package com.juicyslew.moonstation14.ms14.hands.client;

import com.juicyslew.moonstation14.ms14.storage.grid.GridGeometry;
import com.juicyslew.moonstation14.ms14.hands.network.BodyHandActionRequest;
import com.juicyslew.moonstation14.ms14.hands.network.BodyHandStateSnapshot;
import com.juicyslew.moonstation14.ms14.hands.network.BodyWorldCrossAction;
import com.juicyslew.moonstation14.ms14.hands.network.BodyWorldGridSnapshot;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class PopupActionPlanTest {
    private static final UUID ACCOUNT = UUID.randomUUID(), BODY = UUID.randomUUID();
    private static final StoragePopupWorkspace.HostKey BELT = StoragePopupWorkspace.HostKey.worn(StoragePopupWorkspace.HostKind.BELT, "belt-host");
    private static final StoragePopupWorkspace.HostKey BAG = StoragePopupWorkspace.HostKey.worn(StoragePopupWorkspace.HostKind.BAG, "bag-host");
    private static final StoragePopupWorkspace.HostKey LEFT = StoragePopupWorkspace.HostKey.held("left-bag");
    private static final StoragePopupWorkspace.HostKey RIGHT = StoragePopupWorkspace.HostKey.held("right-belt");
    private static final BodyHandStateSnapshot.GridEntry CHILD = new BodyHandStateSnapshot.GridEntry("child", "minecraft:stone", 1, 2, 0, 90);

    private static BodyHandStateSnapshot proof(long revision, String belt, String bag,
                                                BodyHandStateSnapshot.GridEntry child,
                                                List<BodyHandStateSnapshot.GridEntry> bagEntries) {
        return new BodyHandStateSnapshot(0, 1, BodyHandStateSnapshot.Reason.OK, ACCOUNT, BODY, 7, revision, "left",
                List.of(new BodyHandStateSnapshot.Hand("left", "occupied", "minecraft:dirt", 1),
                        new BodyHandStateSnapshot.Hand("right", null, null, 0)),
                List.of(new BodyHandStateSnapshot.EquipmentSlot("belt", belt, "moonstation14:belt", 1, null, null, 0),
                        new BodyHandStateSnapshot.EquipmentSlot("back", bag, "moonstation14:bag", 1, null, null, 0)),
                List.of(new BodyHandStateSnapshot.GridHost(belt, "belt", 8, 2, child == null ? List.of() : List.of(child)),
                        new BodyHandStateSnapshot.GridHost(bag, "bag", 7, 4, bagEntries)));
    }

    private static BodyHandStateSnapshot bodyProof(List<BodyHandStateSnapshot.GridEntry> beltEntries,
                                                    List<BodyHandStateSnapshot.GridEntry> bagEntries,
                                                    List<BodyHandStateSnapshot.GridEntry> leftEntries,
                                                    List<BodyHandStateSnapshot.GridEntry> rightEntries) {
        return new BodyHandStateSnapshot(0, 1, BodyHandStateSnapshot.Reason.OK, ACCOUNT, BODY, 7, 3, "right",
                List.of(new BodyHandStateSnapshot.Hand("left", LEFT.token(), "moonstation14:bag", 1),
                        new BodyHandStateSnapshot.Hand("right", RIGHT.token(), "moonstation14:belt", 1)),
                List.of(new BodyHandStateSnapshot.EquipmentSlot("belt", BELT.token(), "moonstation14:belt", 1, null, null, 0),
                        new BodyHandStateSnapshot.EquipmentSlot("back", BAG.token(), "moonstation14:bag", 1, null, null, 0)),
                List.of(new BodyHandStateSnapshot.GridHost(BELT.token(), "belt", 8, 2, beltEntries),
                        new BodyHandStateSnapshot.GridHost(BAG.token(), "bag", 7, 4, bagEntries),
                        new BodyHandStateSnapshot.GridHost(LEFT.token(), "bag", 7, 4, leftEntries),
                        new BodyHandStateSnapshot.GridHost(RIGHT.token(), "belt", 8, 2, rightEntries)));
    }

    private static final UUID WORLD_TARGET = UUID.randomUUID(), SESSION = UUID.randomUUID();
    @Test void sameWorldAllowsGridActionButDistinctWorldDropCancelsFallback() {
        var source = StoragePopupWorkspace.HostKey.world(WORLD_TARGET);
        var sameWorld = StoragePopupWorkspace.HostKey.world(WORLD_TARGET);
        var otherWorld = StoragePopupWorkspace.HostKey.world(UUID.randomUUID());
        assertTrue(PopupActionPlan.allowWorldGridFallback(source, sameWorld));
        assertFalse(PopupActionPlan.allowWorldGridFallback(source, otherWorld));
        assertTrue(PopupActionPlan.allowWorldGridFallback(source, null));
    }

    private static final BodyWorldGridSnapshot.Entry WORLD_CHILD =
            new BodyWorldGridSnapshot.Entry("world-child", "minecraft:stone", 1, 2, 0, 90);

    private static WorldGridClientPolicy.GridView world(List<BodyWorldGridSnapshot.Entry> entries, long revision,
                                                         long handRevision) {
        var policy = new WorldGridClientPolicy();
        policy.bind(ACCOUNT, BODY, 7);
        var query = policy.query(WORLD_TARGET, handRevision).orElseThrow();
        assertTrue(policy.accept(new BodyWorldGridSnapshot(7, query.sequence(), BodyWorldGridSnapshot.Reason.OK,
                ACCOUNT, BODY, 7, handRevision, WORLD_TARGET, "bag", 7, 4, entries, SESSION, revision), 1));
        return policy.view(WORLD_TARGET);
    }

    @Test void worldBodyPlannerUsesBothHostProofsWithOccupiedAndInactiveHands() {
        var state = bodyProof(List.of(), List.of(), List.of(), List.of()); // both hands occupied by containers
        var world = world(List.of(WORLD_CHILD), 12, 3);
        var origin = PopupActionPlan.worldCrossSource(state, world, WORLD_CHILD);
        assertNotNull(origin);
        for (var host : List.of(BELT, BAG, LEFT, RIGHT)) {
            var plan = PopupActionPlan.worldCrossPlan(origin, host, state, world, true,
                    new GridGeometry.Cell(0, 0), GridGeometry.Rotation.DEG_270, 0);
            assertNotNull(plan, host.toString());
            assertEquals(BodyWorldCrossAction.Direction.WORLD_TO_BODY, plan.direction());
            assertEquals(host.token(), plan.hostToken());
            assertEquals(host.kind() != StoragePopupWorkspace.HostKind.HELD, plan.worn());
            assertEquals(host == BELT ? "belt" : host == BAG ? "back" : host == LEFT ? "left" : "right", plan.hostId());
            assertEquals(origin.oldAnchor(), plan.oldAnchor());
            assertEquals(GridGeometry.Rotation.DEG_270, plan.newRotation());
        }
        assertNull(PopupActionPlan.worldCrossPlan(origin, BAG, state, world, true,
                new GridGeometry.Cell(0, 0), GridGeometry.Rotation.DEG_0, 1));
        assertNull(PopupActionPlan.worldCrossPlan(origin, BAG, state, world, false,
                new GridGeometry.Cell(0, 0), GridGeometry.Rotation.DEG_0, 0));
        var wrongNonce = new PopupActionPlan.WorldCrossSource(origin.account(), origin.body(), origin.epoch(),
                origin.bodyRevision(), origin.worldTarget(), UUID.randomUUID(), origin.worldRevision(),
                origin.host(), origin.hostId(), origin.child(), origin.itemId(), origin.count(),
                origin.oldAnchor(), origin.oldRotation());
        assertNull(PopupActionPlan.worldCrossPlan(wrongNonce, BAG, state, world, true,
                new GridGeometry.Cell(0, 0), GridGeometry.Rotation.DEG_0, 0));
        var moved = new PopupActionPlan.WorldCrossSource(origin.account(), origin.body(), origin.epoch(),
                origin.bodyRevision(), origin.worldTarget(), origin.session(), origin.worldRevision(),
                origin.host(), origin.hostId(), origin.child(), origin.itemId(), origin.count(),
                new GridGeometry.Cell(3, 0), origin.oldRotation());
        assertNull(PopupActionPlan.worldCrossPlan(moved, BAG, state, world, true,
                new GridGeometry.Cell(0, 0), GridGeometry.Rotation.DEG_0, 0));
    }

    @Test void distinctWorldPlannerUsesPinnedSourceAndDestinationGeometry() {
        var state = bodyProof(List.of(), List.of(), List.of(), List.of());
        var source = world(List.of(WORLD_CHILD), 12, 3);
        UUID destinationId = UUID.randomUUID();
        var policy = new WorldGridClientPolicy();
        policy.bind(ACCOUNT, BODY, 7);
        var query = policy.query(destinationId, 3).orElseThrow();
        assertTrue(policy.accept(new BodyWorldGridSnapshot(7, query.sequence(), BodyWorldGridSnapshot.Reason.OK,
                ACCOUNT, BODY, 7, 3, destinationId, "bag", 7, 4, List.of(), UUID.randomUUID(), 13), 1));
        var destination = policy.view(destinationId);
        var captured = PopupActionPlan.worldCrossSource(state, source, WORLD_CHILD);
        var anchor = new GridGeometry.Cell(1, 1);
        var plan = PopupActionPlan.worldWorldPlan(captured, source, destination, state, anchor,
                GridGeometry.Rotation.DEG_90, true, 0);
        assertNotNull(plan);
        assertEquals(WORLD_TARGET, plan.source());
        assertEquals(destinationId, plan.destination());
        assertEquals(WORLD_CHILD.token(), plan.child());
        assertEquals(anchor, plan.anchor());
        assertEquals(GridGeometry.Rotation.DEG_90, plan.rotation());
        assertNull(PopupActionPlan.worldWorldPlan(captured, source, destination, state, anchor,
                GridGeometry.Rotation.DEG_0, true, 1));
        assertNull(PopupActionPlan.worldWorldPlan(captured, source, source, state, anchor,
                GridGeometry.Rotation.DEG_0, true, 0));
        assertNull(PopupActionPlan.worldWorldPlan(captured, source, destination, state,
                new GridGeometry.Cell(7, 0), GridGeometry.Rotation.DEG_0, true, 0));
    }

    @Test void bodyWorldPlannerChecksNonceRevisionsOriginalGeometryAndFit() {
        for (var host : List.of(BELT, BAG, LEFT, RIGHT)) {
            var slots = new java.util.ArrayList<List<BodyHandStateSnapshot.GridEntry>>(List.of(List.of(), List.of(), List.of(), List.of()));
            slots.set(List.of(BELT, BAG, LEFT, RIGHT).indexOf(host), List.of(CHILD));
            var state = bodyProof(slots.get(0), slots.get(1), slots.get(2), slots.get(3));
            var world = world(List.of(), 12, 3);
            var source = PopupActionPlan.bodyWorldSource(state, world, PopupActionPlan.crossSource(state, host, CHILD));
            assertNotNull(source);
            var target = StoragePopupWorkspace.HostKey.world(WORLD_TARGET);
            var anchor = new GridGeometry.Cell(3, 1);
            var plan = PopupActionPlan.worldCrossPlan(source, target, state, world, true, anchor, GridGeometry.Rotation.DEG_0, 0);
            assertNotNull(plan);
            assertEquals(BodyWorldCrossAction.Direction.BODY_TO_WORLD, plan.direction());
            assertEquals(CHILD.x(), plan.oldAnchor().x());
            assertEquals(CHILD.rotation(), plan.oldRotation().degrees());
            assertEquals(anchor, plan.newAnchor());
            assertNull(PopupActionPlan.worldCrossPlan(source, target, state, world(List.of(), 13, 3), true, anchor, GridGeometry.Rotation.DEG_0, 0));
            assertNull(PopupActionPlan.worldCrossPlan(source, target, state, world(List.of(), 12, 4), true, anchor, GridGeometry.Rotation.DEG_0, 0));
            var changed = new BodyHandStateSnapshot(0, 1, BodyHandStateSnapshot.Reason.OK, ACCOUNT, BODY, 7, 4,
                    state.activeHand(), state.hands(), state.equipment(), state.grids());
            assertNull(PopupActionPlan.worldCrossPlan(source, target, changed, world, true, anchor, GridGeometry.Rotation.DEG_0, 0));
            assertNull(PopupActionPlan.worldCrossPlan(source, target, state, world, true,
                    new GridGeometry.Cell(7, 0), GridGeometry.Rotation.DEG_0, 0));
            assertNull(PopupActionPlan.worldCrossPlan(source, target, state, world, true, anchor, GridGeometry.Rotation.DEG_0, 1));
            var blocked = world(List.of(new BodyWorldGridSnapshot.Entry("block", "minecraft:dirt", 1, 3, 1, 0)), 12, 3);
            assertNull(PopupActionPlan.worldCrossPlan(source, target, state, blocked, true, anchor, GridGeometry.Rotation.DEG_0, 0));
        }
    }

    @Test void worldCrossTargetRespectsTopmostFrameAndVisibleCells() {
        var state = bodyProof(List.of(), List.of(), List.of(), List.of());
        var source = PopupActionPlan.worldCrossSource(state, world(List.of(WORLD_CHILD), 12, 3), WORLD_CHILD);
        var space = new StoragePopupWorkspace(400, 300);
        var worldKey = StoragePopupWorkspace.HostKey.world(WORLD_TARGET);
        space.open(worldKey, true, 0, 0, 136, 72);
        space.open(BAG, true, 100, 40, 136, 72);
        var bagLayout = StoragePopupLayout.at(StoragePopupLayout.Kind.BAG, 100, 40);
        var bagGrid = bagLayout.visibleGrid(136, 72);
        int cellX = bagGrid.x() + bagGrid.width() / 2, cellY = bagGrid.y() + bagGrid.height() / 2;
        assertEquals(BAG, PopupActionPlan.worldCrossTarget(space, source, cellX, cellY).key());
        var grip = bagLayout.gripBounds();
        assertNull(PopupActionPlan.worldCrossTarget(space, source, grip.x(), grip.y()));
        space.open(LEFT, true, 100, 40, 136, 72);
        assertEquals(LEFT, PopupActionPlan.worldCrossTarget(space, source, cellX, cellY).key());
        space.close(LEFT);
        space.close(BAG);
        assertNull(PopupActionPlan.worldCrossTarget(space, source, cellX, cellY));
        var occupied = bodyProof(List.of(CHILD), List.of(), List.of(), List.of());
        var bodySource = PopupActionPlan.bodyWorldSource(occupied, world(List.of(), 12, 3),
                PopupActionPlan.crossSource(occupied, BELT, CHILD));
        // A body drag can land only on the captured world, not on another world session.
        assertNotNull(bodySource);
        var otherWorld = StoragePopupWorkspace.HostKey.world(UUID.randomUUID());
        space.open(otherWorld, true, 100, 40, 136, 72);
        assertNull(PopupActionPlan.worldCrossTarget(space, bodySource, cellX, cellY));
    }

    @Test void everyDistinctBodyHostDirectionUsesDirectRequestAndExactHostIds() {
        var hosts = List.of(BELT, BAG, LEFT, RIGHT);
        for (int from = 0; from < hosts.size(); from++) for (int to = 0; to < hosts.size(); to++) {
            if (from == to) continue;
            var entries = new java.util.ArrayList<List<BodyHandStateSnapshot.GridEntry>>(List.of(List.of(), List.of(), List.of(), List.of()));
            entries.set(from, List.of(CHILD));
            var state = bodyProof(entries.get(0), entries.get(1), entries.get(2), entries.get(3));
            var source = PopupActionPlan.crossSource(state, hosts.get(from), CHILD);
            assertNotNull(source, "source " + from);
            var request = PopupActionPlan.crossRequest(source, hosts.get(to), state,
                    new GridGeometry.Cell(0, 0), GridGeometry.Rotation.DEG_180, 0, 42);
            assertNotNull(request, from + " to " + to);
            boolean fromHeld = from >= 2, toHeld = to >= 2;
            assertEquals(fromHeld ? toHeld ? BodyHandActionRequest.Action.GRID_BODY_HELD_TO_HELD
                    : BodyHandActionRequest.Action.GRID_BODY_HELD_TO_WORN
                    : toHeld ? BodyHandActionRequest.Action.GRID_BODY_WORN_TO_HELD
                    : from == 0 ? BodyHandActionRequest.Action.GRID_CROSS_BELT_TO_BACK
                    : BodyHandActionRequest.Action.GRID_CROSS_BACK_TO_BELT, request.action());
            assertEquals(hosts.get(from).token(), request.expectedToken());
            assertEquals(hosts.get(to).token(), request.destinationHostToken());
            assertEquals("child", request.expectedChildOrSourceToken());
            assertEquals(2, request.oldCellX()); assertEquals(90, request.oldRotation());
            assertEquals(180, request.rotation()); assertEquals(42, request.sequence());
            assertEquals(fromHeld || toHeld ? from == 0 ? "belt" : from == 1 ? "back" : from == 2 ? "left" : "right" : null,
                    request.sourceHostId());
            assertEquals(fromHeld || toHeld ? to == 0 ? "belt" : to == 1 ? "back" : to == 2 ? "left" : "right" : null,
                    request.destinationHostId());
            if (fromHeld) assertEquals(request.sourceHostId(), request.handId());
        }
    }

    @Test void heldProofRejectsStaleIdentityGeometryAndOccupiedDestinations() {
        var state = bodyProof(List.of(), List.of(), List.of(CHILD), List.of());
        var source = PopupActionPlan.crossSource(state, LEFT, CHILD);
        var anchor = new GridGeometry.Cell(1, 0);
        assertNotNull(PopupActionPlan.crossRequest(source, RIGHT, state, anchor, GridGeometry.Rotation.DEG_0, 0, 2));
        var stale = new BodyHandStateSnapshot(0, 1, BodyHandStateSnapshot.Reason.OK, ACCOUNT, BODY, 7, 4,
                state.activeHand(), state.hands(), state.equipment(), state.grids());
        assertNull(PopupActionPlan.crossRequest(source, RIGHT, stale, anchor, GridGeometry.Rotation.DEG_0, 0, 2));
        var changedHands = List.of(new BodyHandStateSnapshot.Hand("left", "replaced", "moonstation14:bag", 1), state.hands().get(1));
        var changedGrids = new java.util.ArrayList<>(state.grids());
        changedGrids.set(2, new BodyHandStateSnapshot.GridHost("replaced", "bag", 7, 4, List.of(CHILD)));
        var replaced = new BodyHandStateSnapshot(0, 1, BodyHandStateSnapshot.Reason.OK, ACCOUNT, BODY, 7, 3,
                state.activeHand(), changedHands, state.equipment(), changedGrids);
        assertNull(PopupActionPlan.crossRequest(source, RIGHT, replaced, anchor, GridGeometry.Rotation.DEG_0, 0, 2));
        assertNull(PopupActionPlan.crossSource(replaced, LEFT, CHILD));
        var moved = bodyProof(List.of(), List.of(), List.of(new BodyHandStateSnapshot.GridEntry("child", "minecraft:stone", 1, 3, 0, 90)), List.of());
        assertNull(PopupActionPlan.crossRequest(source, RIGHT, moved, anchor, GridGeometry.Rotation.DEG_0, 0, 2));
        var changedChild = bodyProof(List.of(), List.of(),
                List.of(new BodyHandStateSnapshot.GridEntry("child", "minecraft:dirt", 2, 2, 0, 90)), List.of());
        assertNull(PopupActionPlan.crossRequest(source, RIGHT, changedChild, anchor, GridGeometry.Rotation.DEG_0, 0, 2));
        var replacedTargetHands = List.of(state.hands().get(0),
                new BodyHandStateSnapshot.Hand("right", "new-right", "moonstation14:belt", 1));
        var replacedTargetGrids = new java.util.ArrayList<>(state.grids());
        replacedTargetGrids.set(3, new BodyHandStateSnapshot.GridHost("new-right", "belt", 8, 2, List.of()));
        var replacedTarget = new BodyHandStateSnapshot(0, 1, BodyHandStateSnapshot.Reason.OK, ACCOUNT, BODY, 7, 3,
                state.activeHand(), replacedTargetHands, state.equipment(), replacedTargetGrids);
        assertNull(PopupActionPlan.crossRequest(source, RIGHT, replacedTarget, anchor, GridGeometry.Rotation.DEG_0, 0, 2));
        var collision = bodyProof(List.of(), List.of(), List.of(CHILD),
                List.of(new BodyHandStateSnapshot.GridEntry("obstacle", "minecraft:dirt", 1, 1, 0, 0)));
        assertNull(PopupActionPlan.crossRequest(source, RIGHT, collision, anchor, GridGeometry.Rotation.DEG_0, 0, 2));
        var full = new java.util.ArrayList<BodyHandStateSnapshot.GridEntry>();
        for (int y = 0; y < 2; y++) for (int x = 0; x < 8; x++)
            full.add(new BodyHandStateSnapshot.GridEntry("full-" + x + "-" + y, "minecraft:dirt", 1, x, y, 0));
        assertNull(PopupActionPlan.crossRequest(source, RIGHT, bodyProof(List.of(), List.of(), List.of(CHILD), full),
                anchor, GridGeometry.Rotation.DEG_0, 0, 2));
        assertNull(PopupActionPlan.crossRequest(source, RIGHT, state, anchor, GridGeometry.Rotation.DEG_0, 1, 2));
    }

    @Test void heldTopmostOccludesEvenWhenItsBorderIsNotGrid() {
        var state = bodyProof(List.of(CHILD), List.of(), List.of(), List.of());
        var source = PopupActionPlan.crossSource(state, BELT, CHILD);
        var workspace = new StoragePopupWorkspace(400, 300);
        workspace.resize(400, 300);
        workspace.open(BELT, true, 0, 0, 152, 40);
        workspace.open(BAG, true, 100, 40, 136, 72);
        workspace.open(LEFT, true, 100, 40, 136, 72);
        var bagLayout = StoragePopupLayout.at(StoragePopupLayout.Kind.BAG, 100, 40);
        var bagGrid = bagLayout.visibleGrid(136, 72);
        int cellX = bagGrid.x() + bagGrid.width() / 2, cellY = bagGrid.y() + bagGrid.height() / 2;
        assertEquals(LEFT, PopupActionPlan.crossTarget(workspace, source, cellX, cellY).key());
        var grip = StoragePopupLayout.at(StoragePopupLayout.Kind.BAG, 100, 40).gripBounds();
        assertNull(PopupActionPlan.crossTarget(workspace, source, grip.x(), grip.y()));
        workspace.close(LEFT);
        assertEquals(BAG, PopupActionPlan.crossTarget(workspace, source, cellX, cellY).key());
        workspace.open(RIGHT, true, 100, 40, 152, 40);
        var beltGrid = StoragePopupLayout.at(StoragePopupLayout.Kind.BELT, 100, 40).visibleGrid(152, 40);
        assertEquals(RIGHT, PopupActionPlan.crossTarget(workspace, source, beltGrid.x() + beltGrid.width() / 2,
                beltGrid.y() + beltGrid.height() / 2).key());
    }

    @Test void crossRequestCarriesBothHostsAndOldAndNewGeometryWithOccupiedHand() {
        var state = proof(3, "belt-host", "bag-host", CHILD, List.of());
        var source = PopupActionPlan.crossSource(state, BELT, CHILD);
        var request = PopupActionPlan.crossRequest(source, BAG, state, new GridGeometry.Cell(4, 2), GridGeometry.Rotation.DEG_270, 0, 42);
        assertNotNull(request);
        assertEquals(BodyHandActionRequest.Action.GRID_CROSS_BELT_TO_BACK, request.action());
        assertEquals("left", request.handId());
        assertEquals("belt-host", request.expectedToken());
        assertEquals("bag-host", request.destinationHostToken());
        assertEquals("child", request.expectedChildOrSourceToken());
        assertEquals(2, request.oldCellX()); assertEquals(0, request.oldCellY()); assertEquals(90, request.oldRotation());
        assertEquals(4, request.cellX()); assertEquals(2, request.cellY()); assertEquals(270, request.rotation());
        assertEquals(7, request.epoch()); assertEquals(3, request.expectedRevision()); assertEquals(42, request.sequence());
        assertNull(request.pickupTarget());
        var reverse = proof(3, "belt-host", "bag-host", null, List.of(new BodyHandStateSnapshot.GridEntry("reverse", "minecraft:dirt", 1, 1, 1, 0)));
        var fromBag = PopupActionPlan.crossSource(reverse, BAG, reverse.grids().get(1).entries().getFirst());
        assertEquals(BodyHandActionRequest.Action.GRID_CROSS_BACK_TO_BELT,
                PopupActionPlan.crossRequest(fromBag, BELT, reverse, new GridGeometry.Cell(0, 0), GridGeometry.Rotation.DEG_0, 0, 43).action());
    }

    @Test void crossRequiresCurrentTokensEntryAndOldGeometryAndActualFit() {
        var state = proof(3, "belt-host", "bag-host", CHILD, List.of());
        var source = PopupActionPlan.crossSource(state, BELT, CHILD);
        var anchor = new GridGeometry.Cell(1, 1);
        assertNull(PopupActionPlan.crossRequest(source, BAG, proof(4, "belt-host", "bag-host", CHILD, List.of()), anchor, GridGeometry.Rotation.DEG_0, 0, 2));
        var otherActor = new BodyHandStateSnapshot(0, 1, BodyHandStateSnapshot.Reason.OK, UUID.randomUUID(), BODY,
                state.epoch(), state.revision(), state.activeHand(), state.hands(), state.equipment(), state.grids());
        assertNull(PopupActionPlan.crossRequest(source, BAG, otherActor, anchor, GridGeometry.Rotation.DEG_0, 0, 2));
        var otherEpoch = new BodyHandStateSnapshot(0, 1, BodyHandStateSnapshot.Reason.OK, ACCOUNT, BODY,
                state.epoch() + 1, state.revision(), state.activeHand(), state.hands(), state.equipment(), state.grids());
        assertNull(PopupActionPlan.crossRequest(source, BAG, otherEpoch, anchor, GridGeometry.Rotation.DEG_0, 0, 2));
        assertNull(PopupActionPlan.crossRequest(source, BAG, proof(3, "new-belt", "bag-host", CHILD, List.of()), anchor, GridGeometry.Rotation.DEG_0, 0, 2));
        assertNull(PopupActionPlan.crossRequest(source, BAG, proof(3, "belt-host", "new-bag", CHILD, List.of()), anchor, GridGeometry.Rotation.DEG_0, 0, 2));
        assertNull(PopupActionPlan.crossRequest(source, BAG, proof(3, "belt-host", "bag-host", null, List.of()), anchor, GridGeometry.Rotation.DEG_0, 0, 2));
        assertNull(PopupActionPlan.crossRequest(source, BAG, proof(3, "belt-host", "bag-host",
                new BodyHandStateSnapshot.GridEntry("child", "minecraft:stone", 1, 3, 0, 90), List.of()), anchor, GridGeometry.Rotation.DEG_0, 0, 2));
        assertNull(PopupActionPlan.crossRequest(source, BAG, proof(3, "belt-host", "bag-host",
                new BodyHandStateSnapshot.GridEntry("child", "minecraft:stone", 1, 2, 0, 0), List.of()), anchor, GridGeometry.Rotation.DEG_0, 0, 2));
        assertNull(PopupActionPlan.crossRequest(source, BAG, state, anchor, GridGeometry.Rotation.DEG_0, 1, 2));
        assertNull(PopupActionPlan.crossRequest(source, BELT, state, anchor, GridGeometry.Rotation.DEG_0, 0, 2));
        assertNull(PopupActionPlan.crossRequest(source, StoragePopupWorkspace.HostKey.held("held"), state, anchor, GridGeometry.Rotation.DEG_0, 0, 2));
        assertNull(PopupActionPlan.crossRequest(source, BAG, state, new GridGeometry.Cell(7, 0), GridGeometry.Rotation.DEG_0, 0, 2));
        assertNull(PopupActionPlan.crossRequest(source, BAG, state, new GridGeometry.Cell(-1, 0), GridGeometry.Rotation.DEG_0, 0, 2));
        assertNull(PopupActionPlan.crossRequest(source, BAG,
                proof(3, "belt-host", "bag-host", CHILD, List.of(new BodyHandStateSnapshot.GridEntry("obstacle", "minecraft:dirt", 1, 1, 1, 0))),
                anchor, GridGeometry.Rotation.DEG_0, 0, 2));
        var full = new java.util.ArrayList<BodyHandStateSnapshot.GridEntry>();
        for (int y = 0; y < 4; y++) for (int x = 0; x < 7; x++)
            full.add(new BodyHandStateSnapshot.GridEntry("entry-" + x + "-" + y, "minecraft:dirt", 1, x, y, 0));
        assertNull(PopupActionPlan.crossRequest(source, BAG, proof(3, "belt-host", "bag-host", CHILD, full), anchor, GridGeometry.Rotation.DEG_0, 0, 2));
    }

    @Test void topmostWindowMustBeVisibleWornGrid() {
        var state = proof(3, "belt-host", "bag-host", CHILD, List.of());
        var source = PopupActionPlan.crossSource(state, BELT, CHILD);
        var workspace = new StoragePopupWorkspace(400, 300);
        workspace.open(BELT, true, 0, 0, 152, 40);
        workspace.open(BAG, true, 100, 40, 136, 72);
        var bagGrid = StoragePopupLayout.at(StoragePopupLayout.Kind.BAG, 100, 40).visibleGrid(136, 72);
        int cellX = bagGrid.x() + bagGrid.width() / 2, cellY = bagGrid.y() + bagGrid.height() / 2;
        assertEquals(BAG, PopupActionPlan.crossTarget(workspace, source, cellX, cellY).key());
        var world = StoragePopupWorkspace.HostKey.world(UUID.randomUUID());
        workspace.open(world, true, 100, 40, 136, 72);
        assertNull(PopupActionPlan.crossTarget(workspace, source, cellX, cellY));
        workspace.close(world);
        var grip = StoragePopupLayout.at(StoragePopupLayout.Kind.BAG, 100, 40).gripBounds();
        assertNull(PopupActionPlan.crossTarget(workspace, source, grip.x(), grip.y())); // upper window frame, not a cell
        workspace.close(BAG);
        assertNull(PopupActionPlan.crossTarget(workspace, source, cellX, cellY));
        workspace.clear();
        workspace.open(BAG, false, 100, 40, 136, 72);
        assertNull(PopupActionPlan.crossTarget(workspace, source, cellX, cellY));
    }
    @Test void routesOnceAndPreservesModifiers() {
        assertEquals(PopupActionPlan.Action.DROP, PopupActionPlan.plan(true, false, true, false, false, false, false, false, false));
        assertEquals(PopupActionPlan.Action.NONE, PopupActionPlan.plan(false, false, true, false, false, false, false, false, false));
        assertEquals(PopupActionPlan.Action.SELECT, PopupActionPlan.plan(true, false, false, true, false, false, false, false, false));
        assertEquals(PopupActionPlan.Action.BAG, PopupActionPlan.plan(true, false, false, false, true, false, false, false, false));
        assertEquals(PopupActionPlan.Action.BELT, PopupActionPlan.plan(true, false, false, false, true, true, false, false, false));
        assertEquals(PopupActionPlan.Action.SELECT, PopupActionPlan.plan(true, false, false, false, true, false, true, false, false));
        assertEquals(PopupActionPlan.Action.NONE, PopupActionPlan.plan(true, false, false, false, true, true, true, false, false));
        assertEquals(PopupActionPlan.Action.NONE, PopupActionPlan.plan(true, true, false, false, false, false, false, false, true));
        assertEquals(PopupActionPlan.Action.POINTER, PopupActionPlan.plan(true, true, false, false, false, false, false, false, false));
    }
    @Test void movementBeforeAltDoesNotCountAsChordAndRepeatsDoNotToggle() {
        assertEquals(PopupActionPlan.Action.POINTER, PopupActionPlan.plan(true, true, false, false, false,
                false, false, false, false));
        assertEquals(PopupActionPlan.Action.NONE, PopupActionPlan.plan(false, true, false, false, false,
                false, false, false, false));
        assertEquals(PopupActionPlan.Action.NONE, PopupActionPlan.plan(true, true, false, false, false,
                false, false, false, true));
    }

    @Test void tinyPointerMotionIsStillClick() {
        assertFalse(PopupActionPlan.moved(20, 30, 22, 32));
        assertTrue(PopupActionPlan.moved(20, 30, 24, 30));
    }

    @Test void anchorAndNonAnchorClicksTakeButOnlyLeftDragMoves() {
        var anchor = new GridGeometry.Cell(2, 1);
        var rotation = GridGeometry.Rotation.DEG_0;
        var click = new StoragePopupWorkspace.Drag(anchor, rotation, anchor, rotation);
        assertEquals(anchor, PopupActionPlan.grabbedAnchor(anchor, 0, 0));
        assertEquals(anchor, PopupActionPlan.grabbedAnchor(new GridGeometry.Cell(3, 1), 1, 0));
        assertEquals(new GridGeometry.Cell(3, 1),
                PopupActionPlan.grabbedAnchor(new GridGeometry.Cell(4, 1), 1, 0));
        assertEquals(PopupActionPlan.GridIntent.TAKE, PopupActionPlan.gridIntent(click, false, false, 0));
        assertEquals(PopupActionPlan.GridIntent.CANCEL, PopupActionPlan.gridIntent(click, false, false, 1));
        assertEquals(PopupActionPlan.GridIntent.CANCEL, PopupActionPlan.gridIntent(click, false, true, 0));
        var relocated = new StoragePopupWorkspace.Drag(anchor, rotation, new GridGeometry.Cell(3, 1), rotation);
        assertEquals(PopupActionPlan.GridIntent.MOVE, PopupActionPlan.gridIntent(relocated, false, true, 0));
        assertEquals(PopupActionPlan.GridIntent.CANCEL, PopupActionPlan.gridIntent(relocated, false, true, 1));
    }

    @Test void rightRotationDuringLeftDragKeepsLatestPointerAnchorAndGrabOffset() {
        var original = new GridGeometry.Cell(2, 1);
        var rotation = GridGeometry.Rotation.DEG_0;
        double startX = 40, startY = 20;
        // Movement reaches a cell without relying on a mouseDragged(left) callback.
        assertTrue(PopupActionPlan.moved(startX, startY, 64, 20));
        var pointerCell = new GridGeometry.Cell(4, 1);
        var anchor = PopupActionPlan.grabbedAnchor(pointerCell, 1, 0);
        var rotated = new StoragePopupWorkspace.Drag(original, rotation, anchor,
                GridGeometry.Rotation.DEG_90);
        assertEquals(new GridGeometry.Cell(3, 1), rotated.proposedAnchor());
        assertEquals(PopupActionPlan.GridIntent.MOVE, PopupActionPlan.gridIntent(rotated, false, true, 0));
    }

    @Test void rightRotationWithoutPointerMovementIsNotAnAccidentalTake() {
        var anchor = new GridGeometry.Cell(2, 1);
        var rotated = new StoragePopupWorkspace.Drag(anchor, GridGeometry.Rotation.DEG_0,
                anchor, GridGeometry.Rotation.DEG_90);
        assertEquals(PopupActionPlan.GridIntent.MOVE, PopupActionPlan.gridIntent(rotated, false, false, 0));
        assertEquals(PopupActionPlan.GridIntent.CANCEL, PopupActionPlan.gridIntent(rotated, false, false, 1));
    }
}
