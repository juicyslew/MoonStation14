package com.juicyslew.moonstation14.ms14.hands.client;

import com.juicyslew.moonstation14.ms14.hands.network.BodyHandActionResult;
import com.juicyslew.moonstation14.ms14.hands.network.BodyBlockUseResult;
import com.juicyslew.moonstation14.ms14.hands.network.BodyHandStateSnapshot;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class BodyHandClientPolicyTest {
    @Test void syringeGesturesRouteModeDoseAndBodyUseSeparately() {
        assertEquals(MedicalUseGesturePolicy.InHand.CYCLE_SYRINGE_MODE,
                MedicalUseGesturePolicy.inHand(MedicalUseGesturePolicy.Kind.SYRINGE, false, true));
        assertEquals(MedicalUseGesturePolicy.InHand.SYRINGE_SELF_USE,
                MedicalUseGesturePolicy.inHand(MedicalUseGesturePolicy.Kind.SYRINGE, true, true));
        assertTrue(MedicalUseGesturePolicy.primaryBodyUse(MedicalUseGesturePolicy.Kind.SYRINGE, true, false, false));
        assertFalse(MedicalUseGesturePolicy.primaryBodyUse(MedicalUseGesturePolicy.Kind.SYRINGE, true, true, false));
        assertEquals(com.juicyslew.moonstation14.ms14.hands.network.BodyHandActionRequest.Action.CYCLE_SYRINGE_AMOUNT,
                com.juicyslew.moonstation14.ms14.hands.network.BodyHandActionRequest.Action.values()[
                        com.juicyslew.moonstation14.ms14.hands.network.BodyHandActionRequest.Action.values().length - 1]);
    }
    @Test void configuredKeyMatcherHonorsRemappedKeyAndModifier() {
        var mapping=new net.minecraft.client.KeyMapping("test.binding",
                net.neoforged.neoforge.client.settings.KeyConflictContext.UNIVERSAL,
                net.neoforged.neoforge.client.settings.KeyModifier.CONTROL,
                com.mojang.blaze3d.platform.InputConstants.Type.KEYSYM,
                org.lwjgl.glfw.GLFW.GLFW_KEY_J,"test");
        assertTrue(BodyHandClient.matches(mapping,org.lwjgl.glfw.GLFW.GLFW_KEY_J,0,org.lwjgl.glfw.GLFW.GLFW_MOD_CONTROL));
        assertFalse(BodyHandClient.matches(mapping,org.lwjgl.glfw.GLFW.GLFW_KEY_J,0,0));
        assertFalse(BodyHandClient.matches(mapping,org.lwjgl.glfw.GLFW.GLFW_KEY_G,0,org.lwjgl.glfw.GLFW.GLFW_MOD_CONTROL));
    }
    @Test void deferredPlacementRequiresFreshSameIntentAndSource() {
        var body=java.util.UUID.randomUUID(); var pos=new net.minecraft.core.BlockPos(1,2,3);
        assertTrue(BodyHandClientPolicy.deferredPlacementStillValid(body,4,body,4,3,4,"main","main","root-token","root-token",
                "moonstation14:steel","moonstation14:steel",9,9,pos,pos,net.minecraft.core.Direction.UP,
                net.minecraft.core.Direction.UP,true,true));
        assertFalse(BodyHandClientPolicy.deferredPlacementStillValid(body,4,body,4,3,4,"main","off","root-token","root-token",
                "moonstation14:steel","moonstation14:steel",9,9,pos,pos,net.minecraft.core.Direction.UP,
                net.minecraft.core.Direction.UP,true,true));
        assertFalse(BodyHandClientPolicy.deferredPlacementStillValid(body,4,body,4,3,4,"main","main","root-token","root-token",
                "moonstation14:steel","moonstation14:steel",9,10,pos,pos,net.minecraft.core.Direction.UP,
                net.minecraft.core.Direction.UP,true,true));
        assertFalse(BodyHandClientPolicy.deferredPlacementStillValid(body,4,body,4,3,4,"main","main","root-token","root-token",
                "moonstation14:steel","moonstation14:steel",9,9,pos,pos,net.minecraft.core.Direction.UP,
                net.minecraft.core.Direction.NORTH,true,true));
        assertFalse(BodyHandClientPolicy.deferredPlacementStillValid(body,4,body,4,3,4,"main","main","root-token","root-token",
                "moonstation14:steel","moonstation14:steel",9,9,pos,pos,net.minecraft.core.Direction.UP,
                net.minecraft.core.Direction.UP,false,true));
        assertFalse(BodyHandClientPolicy.deferredPlacementStillValid(body,4,body,4,3,4,"main","main","root-token","different-stack",
                "moonstation14:steel","moonstation14:steel",9,9,pos,pos,net.minecraft.core.Direction.UP,
                net.minecraft.core.Direction.UP,true,true));
        assertFalse(BodyHandClientPolicy.deferredPlacementStillValid(body,4,java.util.UUID.randomUUID(),4,3,4,"main","main","root-token","root-token",
                "moonstation14:steel","moonstation14:steel",9,9,pos,pos,net.minecraft.core.Direction.UP,
                net.minecraft.core.Direction.UP,true,true));
        assertFalse(BodyHandClientPolicy.deferredPlacementStillValid(body,4,body,5,3,4,"main","main","root-token","root-token",
                "moonstation14:steel","moonstation14:steel",9,9,pos,pos,net.minecraft.core.Direction.UP,
                net.minecraft.core.Direction.UP,true,true));
        assertFalse(BodyHandClientPolicy.deferredPlacementStillValid(body,4,body,4,3,4,"main","main","root-token","root-token",
                "moonstation14:steel","minecraft:iron_ingot",9,9,pos,pos,net.minecraft.core.Direction.UP,
                net.minecraft.core.Direction.UP,true,true));
        assertFalse(BodyHandClientPolicy.deferredPlacementStillValid(body,4,body,4,3,4,"main","main","root-token","root-token",
                "moonstation14:steel","moonstation14:steel",9,9,pos,new net.minecraft.core.BlockPos(9,9,9),net.minecraft.core.Direction.UP,
                net.minecraft.core.Direction.UP,true,true));
    }
    @Test void constructionUseMayQueueAcrossEitherBlockUseBarrierForResolvedPipeRecipes() {
        var step = new com.juicyslew.moonstation14.ms14.disposal.prototype.DisposalEntityData.ConstructionStep(
                2, 0, "moonstation14:steel");
        for (String shape : List.of("disposal_pipe", "disposal_bend", "disposal_trunk",
                "disposal_junction", "disposal_junction_flipped", "disposal_tagger",
                "disposal_router", "disposal_router_flipped")) {
            String prototype = "moonstation14:" + shape;
            assertTrue(BodyHandClientPolicy.mayDeferConstructionUse(true, true, prototype, step,
                    "moonstation14:steel"), shape);
        }
        assertFalse(BodyHandClientPolicy.mayDeferConstructionUse(false, true, "moonstation14:disposal_bend",
                step, "moonstation14:steel"), "no pending-use or refresh barrier");
        assertFalse(BodyHandClientPolicy.mayDeferConstructionUse(true, true, "moonstation14:disposal_router",
                step, "minecraft:iron_ingot"), "wrong active material");
        assertFalse(BodyHandClientPolicy.mayDeferConstructionUse(true, false, "moonstation14:disposal_pipe",
                step, "moonstation14:steel"), "inactive construction mode");
    }
    private static final UUID ACCOUNT = UUID.randomUUID(), BODY = UUID.randomUUID();

    @Test void clientPullNominationUsesReadOnlyProjectionNotServerAuthorityResolver() throws Exception {
        Path relative = Path.of("src/main/java/com/juicyslew/moonstation14/ms14/hands/client/BodyHandClient.java");
        Path root = Path.of("").toAbsolutePath();
        while (root != null && !Files.isRegularFile(root.resolve(relative))) root = root.getParent();
        assertNotNull(root, "BodyHandClient source must be available for authority audit");
        String source = Files.readString(root.resolve(relative));
        assertTrue(source.contains(".projectForActor(entity)"));
        assertFalse(source.contains(".resolveForActor(entity)"));
    }

    @Test void mappedGesturesSelectDistinctRoutes() {
        assertEquals(BodyInputGesturePolicy.Route.SMART_BELT, BodyInputGesturePolicy.resolve(false, true, false, false));
        assertEquals(BodyInputGesturePolicy.Route.ALTERNATE_WORLD_USE, BodyInputGesturePolicy.resolve(false, false, true, false));
        assertEquals(BodyInputGesturePolicy.Route.NORMAL_WORLD_USE, BodyInputGesturePolicy.resolve(false, false, false, true));
        assertEquals(BodyInputGesturePolicy.Route.ALTERNATE_WORLD_USE,
                BodyInputGesturePolicy.resolve(false, false, false, false, true));
        assertEquals(BodyInputGesturePolicy.Route.SMART_BELT,
                BodyInputGesturePolicy.resolve(false, true, false, true, true));
        assertEquals(BodyInputGesturePolicy.Route.NONE, BodyInputGesturePolicy.resolve(false, false, false, false));
        // An independently rebound alternate mapping wins over the ordinary world-use mapping.
        assertEquals(BodyInputGesturePolicy.Route.ALTERNATE_WORLD_USE, BodyInputGesturePolicy.resolve(false, false, true, true));
        assertEquals(BodyInputGesturePolicy.Route.ALTERNATE_WORLD_USE,
                BodyInputGesturePolicy.dispatch(false, true, false, false, true, false));
        assertEquals(BodyInputGesturePolicy.Route.NONE,
                BodyInputGesturePolicy.dispatch(true, true, false, false, true, false));
        assertEquals(BodyInputGesturePolicy.Route.NONE,
                BodyInputGesturePolicy.dispatch(false, false, false, false, true, false));
        assertEquals(BodyInputGesturePolicy.Route.SMART_BAG,
                BodyInputGesturePolicy.dispatch(false, true, true, false, true, true));
        // The production seven-flag dispatch retains press-captured ALT+E even if neither
        // independently mapped click was queued by NeoForge.
        assertEquals(BodyInputGesturePolicy.Route.ALTERNATE_WORLD_USE,
                BodyInputGesturePolicy.dispatch(false, true, false, false, false, false, true));
        assertEquals(BodyInputGesturePolicy.Route.ALTERNATE_WORLD_USE,
                BodyInputGesturePolicy.dispatch(false, true, false, false, false, true, true));
        assertEquals(BodyInputGesturePolicy.Route.ALTERNATE_WORLD_USE,
                BodyInputGesturePolicy.dispatch(false, true, false, false, true, false, true));
        assertEquals(BodyInputGesturePolicy.Route.NONE,
                BodyInputGesturePolicy.dispatch(true, true, false, false, false, false, true));
        assertEquals(BodyInputGesturePolicy.Route.NONE,
                BodyInputGesturePolicy.dispatch(false, false, false, false, false, false, true));
        assertEquals(BodyInputGesturePolicy.Route.NORMAL_WORLD_USE,
                BodyInputGesturePolicy.dispatch(false, true, false, false, false, true, false));
        assertEquals(BodyInputGesturePolicy.Route.SMART_BELT,
                BodyInputGesturePolicy.dispatch(false, true, false, true, false, true, false));
    }

    @Test void nativeInventorySuppressionRequiresCommittedControlledBodyAndNativeScreen() {
        assertTrue(BodyInputGesturePolicy.suppressNativeInventory(true, true));
        assertFalse(BodyInputGesturePolicy.suppressNativeInventory(true, false));
        assertFalse(BodyInputGesturePolicy.suppressNativeInventory(false, true));
    }

    private static BodyHandStateSnapshot snapshot(long requested, long sequence, UUID account, UUID body, long epoch) {
        return new BodyHandStateSnapshot(requested, sequence, BodyHandStateSnapshot.Reason.OK,
                account, body, epoch, 3, "left", List.of(new BodyHandStateSnapshot.Hand("left", null, null, 0)));
    }

    @Test void pushedSnapshotsAdvanceWithoutStealingQueryOrActionCorrelations() {
        var policy = new BodyHandClientPolicy();
        policy.bind(ACCOUNT, BODY, 7);
        long q = policy.query(1);
        assertTrue(policy.accept(snapshot(0, q, ACCOUNT, BODY, 7)));
        long action = policy.action(2);
        var pushed = new BodyHandStateSnapshot(0, 0, BodyHandStateSnapshot.Reason.OK, ACCOUNT, BODY, 7, 5,
                "left", List.of(new BodyHandStateSnapshot.Hand("left", null, null, 0)), List.of(), List.of(), true);
        assertTrue(policy.accept(pushed));
        assertTrue(policy.result(result(7, action, BodyHandActionResult.Reason.OK), 3));
        long refresh = policy.query(4);
        assertFalse(policy.accept(snapshot(0, refresh, ACCOUNT, BODY, 7)), "older query cannot roll back push revision");
        assertEquals(5, policy.presentationSnapshot().revision());
    }

    @Test void pushRejectsWrongAuthorityEpochAndOlderRevision() {
        var policy = new BodyHandClientPolicy();
        policy.bind(ACCOUNT, BODY, 7);
        assertTrue(policy.accept(snapshot(0, policy.query(1), ACCOUNT, BODY, 7)));
        var newer = new BodyHandStateSnapshot(0, 0, BodyHandStateSnapshot.Reason.OK, ACCOUNT, BODY, 7, 5,
                "left", List.of(new BodyHandStateSnapshot.Hand("left", null, null, 0)), List.of(), List.of(), true);
        assertFalse(policy.accept(new BodyHandStateSnapshot(0, 0, BodyHandStateSnapshot.Reason.OK, UUID.randomUUID(), BODY, 7, 6,
                "left", newer.hands(), List.of(), List.of(), true)));
        assertFalse(policy.accept(new BodyHandStateSnapshot(0, 0, BodyHandStateSnapshot.Reason.OK, ACCOUNT, BODY, 8, 6,
                "left", newer.hands(), List.of(), List.of(), true)));
        assertTrue(policy.accept(newer));
        var older = new BodyHandStateSnapshot(0, 0, BodyHandStateSnapshot.Reason.OK, ACCOUNT, BODY, 7, 4,
                "left", newer.hands(), List.of(), List.of(), true);
        assertFalse(policy.accept(older));
        assertEquals(5, policy.snapshot().revision());
    }

    private static BodyHandActionResult result(long epoch, long sequence, BodyHandActionResult.Reason reason) {
        return new BodyHandActionResult(epoch, sequence, reason == BodyHandActionResult.Reason.OK,
                reason, false, 0, null, null, null, 0);
    }

    @Test void pullReservationIsNonItemMetadataAndOnlyBlocksItsOwnHandActions() {
        var reserved = new BodyHandStateSnapshot.Hand("left", null, null, 0,
                false, null, null, 0, "target");
        var free = new BodyHandStateSnapshot.Hand("right", null, null, 0);
        assertNull(reserved.token());
        assertEquals("target", reserved.pullTarget());
        assertFalse(BodyHandClientPolicy.handAvailableForItemAction(reserved));
        assertTrue(BodyHandClientPolicy.handAvailableForItemAction(free));
        assertFalse(BodyHandClientPolicy.nominatedGridTakeEligible(reserved,
                new BodyHandStateSnapshot.GridEntry("child", "minecraft:stick", 1, 0, 0, 0),
                new BodyHandStateSnapshot.GridEntry("child", "minecraft:stick", 1, 0, 0, 0), null));
        assertTrue(BodyHandClientPolicy.nominatedGridTakeEligible(free,
                new BodyHandStateSnapshot.GridEntry("child", "minecraft:stick", 1, 0, 0, 0),
                new BodyHandStateSnapshot.GridEntry("child", "minecraft:stick", 1, 0, 0, 0), null));
        assertFalse(BodyHandClientPolicy.nominatedGridStoreEligible(
                new BodyHandStateSnapshot.Hand("left", "item", "minecraft:stone", 1,
                        false, null, null, 0, "target"), "item", "minecraft:stone", 1));
        assertTrue(BodyHandClientPolicy.nominatedGridStoreEligible(
                new BodyHandStateSnapshot.Hand("right", "item", "minecraft:stone", 1),
                "item", "minecraft:stone", 1));
    }

    @Test void reservedActiveHandCannotTakeEquipmentButOtherHandCan() {
        var policy = new BodyHandClientPolicy();
        policy.bind(ACCOUNT, BODY, 7);
        var reserved = new BodyHandStateSnapshot.Hand("left", null, null, 0,
                false, null, null, 0, "target");
        var q = policy.query(1);
        assertTrue(policy.accept(equipmentSnapshot(0, q, reserved, List.of(slot("belt", true, true)))));
        assertFalse(policy.inventoryView().belt().canUnequip());
        policy.bind(ACCOUNT, BODY, 8);
        var free = new BodyHandStateSnapshot.Hand("left", null, null, 0);
        var otherQuery = policy.query(2);
        var otherBodySnapshot = new BodyHandStateSnapshot(0, otherQuery, BodyHandStateSnapshot.Reason.OK,
                ACCOUNT, BODY, 8, 3, "left", List.of(free,
                new BodyHandStateSnapshot.Hand("right", null, null, 0)), List.of(slot("belt", true, true)));
        assertTrue(policy.accept(otherBodySnapshot));
        assertTrue(policy.inventoryView().belt().canUnequip());
    }

    private static BodyHandStateSnapshot grenade(long requested, long sequence, long revision) {
        return new BodyHandStateSnapshot(requested, sequence, BodyHandStateSnapshot.Reason.OK,
                ACCOUNT, BODY, 7, revision, "left", List.of(new BodyHandStateSnapshot.Hand(
                "left", "grenade-token", "moonstation14:grenade", 1)));
    }

    @Test void standaloneThrowDoesNotQueueAnUncorrelatedGrenade() {
        var policy = new BodyHandClientPolicy();
        policy.bind(ACCOUNT, BODY, 7);
        assertTrue(policy.accept(grenade(0, policy.query(1), 3)));
        assertFalse(policy.requestThrowAfterPrime(2));
        assertTrue(policy.action(2) > 0); // caller may send THROW immediately; server decides if armed
    }

    @Test void pendingPrimeRejectsStaleSnapshotAndChangedHandOrToken() {
        var policy = new BodyHandClientPolicy();
        policy.bind(ACCOUNT, BODY, 7);
        assertTrue(policy.accept(grenade(0, policy.query(1), 3)));
        long prime = policy.action(2);
        policy.markPrime(prime);
        assertTrue(policy.requestThrowAfterPrime(2));
        assertTrue(policy.result(result(7, prime, BodyHandActionResult.Reason.OK), 3));
        assertFalse(policy.accept(grenade(0, prime, 4))); // stale pre-prime reply cannot settle refresh
        long refresh = policy.query(3);
        var changed = new BodyHandStateSnapshot(0, refresh, BodyHandStateSnapshot.Reason.OK,
                ACCOUNT, BODY, 7, 4, "right", List.of(
                new BodyHandStateSnapshot.Hand("left", "different-token", "moonstation14:grenade", 1),
                new BodyHandStateSnapshot.Hand("right", null, null, 0)));
        assertTrue(policy.accept(changed));
        assertFalse(policy.takeReadyThrow(4));
    }

    @Test void genericInHandUseCanQueueThrowUntilFreshProofAndDenialCancelsIt() {
        var policy = new BodyHandClientPolicy();
        policy.bind(ACCOUNT, BODY, 7);
        assertTrue(policy.accept(grenade(0, policy.query(1), 3)));
        long use = policy.action(2);
        policy.markUse(use);
        assertTrue(policy.requestThrowAfterPrime(2));
        assertTrue(policy.result(result(7, use, BodyHandActionResult.Reason.OK), 3));
        assertFalse(policy.takeReadyThrow(3));
        long refresh = policy.forceFreshQuery(3);
        assertTrue(policy.accept(grenade(0, refresh, 4)));
        assertTrue(policy.takeReadyThrow(4));
        policy.throwSent();

        policy.bind(ACCOUNT, BODY, 7);
        assertTrue(policy.accept(grenade(0, policy.forceFreshQuery(5), 4)));
        use = policy.action(6);
        policy.markUse(use);
        assertTrue(policy.requestThrowAfterPrime(6));
        assertTrue(policy.result(result(7, use, BodyHandActionResult.Reason.DENIED), 7));
        assertFalse(policy.takeReadyThrow(7));
        assertFalse(policy.requestThrowAfterPrime(7));
    }

    @Test void explicitThrowSurvivesPrimeResultAndWaitsForCorrelatedNewerSnapshot() {
        var policy = new BodyHandClientPolicy();
        policy.bind(ACCOUNT, BODY, 7);
        assertTrue(policy.accept(grenade(0, policy.query(1), 3)));
        long prime = policy.action(2);
        policy.markPrime(prime);
        assertTrue(policy.requestThrowAfterPrime(2));
        assertTrue(policy.result(result(7, prime, BodyHandActionResult.Reason.OK), 3));
        assertFalse(policy.takeReadyThrow(3));
        assertTrue(policy.requestThrowAfterPrime(4)); // M after PRIME result, while refresh is pending
        long refresh = policy.query(4);
        assertFalse(policy.accept(grenade(0, refresh + 1, 4)));
        assertTrue(policy.accept(grenade(0, refresh, 4)));
        assertTrue(policy.takeReadyThrow(5));
        assertTrue(policy.takeReadyThrow(5)); // readiness does not consume intent before send
        policy.throwSent();
        assertFalse(policy.takeReadyThrow(5));
    }

    @Test void explicitThrowCanBeCancelledByUiPresence() {
        var policy = new BodyHandClientPolicy();
        policy.bind(ACCOUNT, BODY, 7);
        assertTrue(policy.accept(grenade(0, policy.query(1), 3)));
        long prime = policy.action(2);
        policy.markPrime(prime);
        assertTrue(policy.requestThrowAfterPrime(2));
        assertTrue(policy.result(result(7, prime, BodyHandActionResult.Reason.OK), 3));

        policy.cancelThrowIntent(); // UI open, including the body inventory screen
        assertFalse(policy.requestThrowAfterPrime(4));
        long refresh = policy.query(4);
        assertTrue(policy.accept(grenade(0, refresh, 4)));
        assertFalse(policy.takeReadyThrow(5));
    }

    @Test void failedCorrelatedQueryCancelsExplicitThrowIntent() {
        var policy = new BodyHandClientPolicy();
        policy.bind(ACCOUNT, BODY, 7);
        assertTrue(policy.accept(grenade(0, policy.query(1), 3)));
        long prime = policy.action(2);
        policy.markPrime(prime);
        assertTrue(policy.requestThrowAfterPrime(2));
        assertTrue(policy.result(result(7, prime, BodyHandActionResult.Reason.OK), 3));

        long failedQuery = policy.query(3);
        var failed = new BodyHandStateSnapshot(0, failedQuery, BodyHandStateSnapshot.Reason.NO_AUTHORITY,
                null, null, 0, 0, null, List.of());
        assertFalse(policy.accept(failed));
        long unrelated = policy.query(43);
        assertTrue(policy.accept(grenade(0, unrelated, 4)));
        assertFalse(policy.takeReadyThrow(44));
    }

    @Test void throwIntentExpiresOrCancelsOnRejectedPrimeAndIdentityChange() {
        var policy = new BodyHandClientPolicy();
        policy.bind(ACCOUNT, BODY, 7);
        assertTrue(policy.accept(grenade(0, policy.query(1), 3)));
        long prime = policy.action(2);
        policy.markPrime(prime);
        assertTrue(policy.requestThrowAfterPrime(2));
        assertTrue(policy.result(result(7, prime, BodyHandActionResult.Reason.DENIED), 3));
        assertFalse(policy.requestThrowAfterPrime(4));

        policy.bind(ACCOUNT, BODY, 7);
        assertTrue(policy.accept(grenade(0, policy.query(5), 3)));
        prime = policy.action(6);
        policy.markPrime(prime);
        assertTrue(policy.requestThrowAfterPrime(6));
        assertTrue(policy.result(result(7, prime, BodyHandActionResult.Reason.OK), 7));
        assertFalse(policy.takeReadyThrow(46)); // expires after 40 ticks
        assertFalse(policy.requestThrowAfterPrime(47));
    }

    private static BodyHandStateSnapshot pouchSnapshot(long requested, long sequence, boolean filled,
                                                       BodyHandStateSnapshot.Hand other) {
        return new BodyHandStateSnapshot(requested, sequence, BodyHandStateSnapshot.Reason.OK,
                ACCOUNT, BODY, 7, 3, other.id(), List.of(other,
                new BodyHandStateSnapshot.Hand("pouch-hand", "pouch-token", "moonstation14:pouch", 1,
                        true, filled ? "child-token" : null, filled ? "minecraft:stick" : null,
                        filled ? 2 : 0)));
    }

    private static BodyHandStateSnapshot equipmentSnapshot(long requested, long sequence,
                                                             BodyHandStateSnapshot.Hand active,
                                                             List<BodyHandStateSnapshot.EquipmentSlot> equipment) {
        return new BodyHandStateSnapshot(requested, sequence, BodyHandStateSnapshot.Reason.OK,
                ACCOUNT, BODY, 7, 3, "left", List.of(active,
                new BodyHandStateSnapshot.Hand("right", null, null, 0)), equipment);
    }

    private static BodyHandStateSnapshot.EquipmentSlot slot(String id, boolean worn, boolean filled) {
        return new BodyHandStateSnapshot.EquipmentSlot(id, worn ? id + "-token" : null,
                worn ? (id.equals("belt") ? "moonstation14:belt" : "moonstation14:bag") : null,
                worn ? 1 : 0, filled ? id + "-child" : null,
                filled ? "minecraft:stick" : null, filled ? 2 : 0);
    }

    @Test void successfulSelectionKeepsPresentationOnlyUntilFreshSnapshot() {
        var policy = new BodyHandClientPolicy();
        policy.bind(ACCOUNT, BODY, 7);
        assertTrue(policy.accept(snapshot(0, policy.query(1), ACCOUNT, BODY, 7)));
        long select = policy.selectionAction(2);
        assertTrue(policy.presentationPending() == false);
        assertNull(policy.inventoryView());
        assertTrue(policy.result(result(7, select, BodyHandActionResult.Reason.OK), 3));
        assertNull(policy.snapshot());
        assertNull(policy.inventoryView());
        long refresh = policy.query(4);
        assertFalse(policy.presentationPending());
        assertTrue(policy.accept(snapshot(0, refresh, ACCOUNT, BODY, 7)));
        assertFalse(policy.presentationPending());
    }

    @Test void equipmentButtonsRequireDeclaredSlotActiveHandAndFreshProof() {
        var policy = new BodyHandClientPolicy();
        policy.bind(ACCOUNT, BODY, 7);
        var heldBelt = new BodyHandStateSnapshot.Hand("left", "belt-token", "moonstation14:belt", 1);
        long q = policy.query(1);
        assertFalse(policy.accept(equipmentSnapshot(0, q + 1, heldBelt, List.of(slot("belt", false, false)))));
        assertTrue(policy.accept(equipmentSnapshot(0, q, heldBelt, List.of(slot("belt", false, false)))));
        assertTrue(policy.inventoryView().belt().canEquip());
        assertNull(policy.inventoryView().back());
        long action = policy.action(2);
        assertNull(policy.inventoryView());
        assertTrue(policy.result(result(7, action, BodyHandActionResult.Reason.OK), 3));
        assertNull(policy.inventoryView());
        long refresh = policy.query(3);
        var empty = new BodyHandStateSnapshot.Hand("left", null, null, 0);
        assertTrue(policy.accept(equipmentSnapshot(0, refresh, empty, List.of(slot("belt", true, true)))));
        assertTrue(policy.inventoryView().belt().canUnequip());
        assertFalse(policy.inventoryView().belt().canStore());
        policy.bind(ACCOUNT, BODY, 8);
        assertNull(policy.inventoryView());
        policy.suspend();
        assertEquals(0, policy.action(4));
    }

    @Test void selectionPresentationExceptionClearsOnFailureTimeoutAndReset() {
        var policy = new BodyHandClientPolicy();
        policy.bind(ACCOUNT, BODY, 7);
        assertTrue(policy.accept(snapshot(0, policy.query(1), ACCOUNT, BODY, 7)));
        long denied = policy.selectionAction(2);
        assertTrue(policy.result(result(7, denied, BodyHandActionResult.Reason.DENIED), 3));
        assertFalse(policy.presentationPending()); // retained snapshot is normal presentation after failure
        long retry = policy.query(4);
        assertTrue(retry > 0);
        assertTrue(policy.presentationPending()); // stale retained snapshot dims during fresh query
        assertTrue(policy.accept(snapshot(0, retry, ACCOUNT, BODY, 7)));
        assertFalse(policy.presentationPending()); // confirmed presentation is retained once no query is pending
        long timed = policy.selectionAction(45);
        policy.timeout(125);
        assertFalse(policy.presentationPending());
        assertFalse(policy.result(result(7, timed, BodyHandActionResult.Reason.OK), 126));
        policy.reset();
        assertTrue(policy.presentationPending()); // reset removes presentation entirely
    }

    @Test void ordinaryMutationStillDimsPresentation() {
        var policy = new BodyHandClientPolicy();
        policy.bind(ACCOUNT, BODY, 7);
        assertTrue(policy.accept(snapshot(0, policy.query(1), ACCOUNT, BODY, 7)));
        assertTrue(policy.action(2) > 0);
        assertTrue(policy.presentationPending());
    }

    @Test void nominatedGridStoreUsesExactHandProofRatherThanActiveHand() {
        var nominated = new BodyHandStateSnapshot.Hand("inactive", "item-token", "minecraft:stone", 3);
        assertTrue(BodyHandClientPolicy.nominatedGridStoreEligible(nominated, "item-token", "minecraft:stone", 3));
        assertFalse(BodyHandClientPolicy.nominatedGridStoreEligible(nominated, "other-token", "minecraft:stone", 3));
        assertFalse(BodyHandClientPolicy.nominatedGridStoreEligible(nominated, "item-token", "minecraft:stone", 2));
    }



    @Test void hudHidesOldHandThroughActionAndRefreshWithoutSyncingText() {
        var policy = new BodyHandClientPolicy();
        policy.bind(ACCOUNT, BODY, 7);
        assertNull(policy.hudSnapshot());
        assertNull(policy.feedback());
        long initial = policy.query(1);
        assertTrue(policy.accept(snapshot(0, initial, ACCOUNT, BODY, 7)));
        assertNotNull(policy.hudSnapshot());

        long action = policy.action(2);
        assertNotNull(policy.hudSnapshot());
        assertNull(policy.feedback());
        policy.noItemUnderCursor(2); // repeated G while the first action is unresolved
        assertNull(policy.feedback());
        assertEquals(0, policy.query(2));
        assertFalse(policy.result(result(8, action, BodyHandActionResult.Reason.OK), 3));
        assertNotNull(policy.hudSnapshot());
        assertFalse(policy.result(result(7, action + 1, BodyHandActionResult.Reason.OK), 3));
        assertTrue(policy.result(result(7, action, BodyHandActionResult.Reason.OK), 4));
        assertNotNull(policy.hudSnapshot());
        assertNull(policy.feedback());
        assertEquals(0, policy.action(4));

        long refresh = policy.query(4);
        assertFalse(policy.accept(snapshot(0, initial, ACCOUNT, BODY, 7)));
        assertFalse(policy.accept(snapshot(7, refresh, ACCOUNT, BODY, 7)));
        assertNotNull(policy.hudSnapshot());
        assertTrue(policy.accept(snapshot(0, refresh, ACCOUNT, BODY, 7)));
        assertNotNull(policy.hudSnapshot());
        assertFalse(policy.result(result(7, action, BodyHandActionResult.Reason.DENIED), 5));
        assertNull(policy.feedback());
        assertTrue(policy.action(5) > action);
    }

    @Test void blockUseSequenceKeepsPresentationAndDoesNotReplaceActionResultCorrelation() {
        var policy = new BodyHandClientPolicy();
        policy.bind(ACCOUNT, BODY, 7);
        long initial = policy.query(1);
        var hands = new BodyHandStateSnapshot(0, initial, BodyHandStateSnapshot.Reason.OK,
                ACCOUNT, BODY, 7, 3, "left", List.of(
                new BodyHandStateSnapshot.Hand("left", "token", "minecraft:stick", 1),
                new BodyHandStateSnapshot.Hand("right", null, null, 0)));
        assertTrue(policy.accept(hands));
        long blockUse = policy.blockUseSequence(2);
        assertTrue(blockUse > 0);
        var pipeStep = new com.juicyslew.moonstation14.ms14.disposal.prototype.DisposalEntityData.ConstructionStep(2,0,"moonstation14:steel");
        assertTrue(policy.blockUseBarrier()); // request sent; awaiting result
        assertSame(hands, policy.confirmedSnapshot()); // read-only configurator proof survives the action-input barrier
        assertTrue(BodyHandClientPolicy.mayDeferConstructionUse(policy.blockUseBarrier(),true,
                "moonstation14:disposal_bend",pipeStep,"moonstation14:steel"));
        assertNotNull(policy.hudSnapshot());
        assertNull(policy.inventoryView()); // presentation remains visible, but pending block use grants no mutation authority
        assertEquals(0, policy.blockUseSequence(3)); // one outstanding use until correlated completion
        assertTrue(policy.blockUseResult(new com.juicyslew.moonstation14.ms14.hands.network.BodyBlockUseResult(
                7, blockUse, com.juicyslew.moonstation14.ms14.hands.network.BodyBlockUseResult.Outcome.PLACED)));
        assertNull(policy.confirmedSnapshot()); // mutating result invalidates the old authority snapshot
        assertTrue(policy.blockUseBarrier()); // result received; authoritative refresh still pending
        assertTrue(BodyHandClientPolicy.mayDeferConstructionUse(policy.blockUseBarrier(),true,
                "moonstation14:disposal_router",pipeStep,"moonstation14:steel"));
        assertNull(policy.hudSnapshot());
        long refresh = policy.query(3); // placement completion bypasses the periodic throttle
        assertTrue(refresh > initial);
        assertEquals(0, policy.blockUseSequence(3));
        assertTrue(policy.accept(new BodyHandStateSnapshot(0, refresh, BodyHandStateSnapshot.Reason.OK,
                ACCOUNT, BODY, 7, 4, "left", hands.hands())));
        assertFalse(policy.blockUseBarrier());

        long action = policy.action(4);
        assertTrue(action > blockUse);
        assertNotNull(policy.hudSnapshot()); // retained presentation is not proof of action authority
        assertNull(policy.inventoryView());
        assertEquals(0, policy.blockUseSequence(5)); // cannot overwrite an ordinary pending action
        assertFalse(policy.result(result(7, blockUse, BodyHandActionResult.Reason.OK), 6));
        assertTrue(policy.result(result(7, action, BodyHandActionResult.Reason.OK), 6));

        long actionRefresh = policy.query(6);
        assertTrue(actionRefresh > 0);
        assertNull(policy.inventoryView());
        assertTrue(policy.accept(new BodyHandStateSnapshot(0, actionRefresh, BodyHandStateSnapshot.Reason.OK,
                ACCOUNT, BODY, 7, 3, "left", hands.hands())));
        assertNotNull(policy.inventoryView());

        long query = policy.query(46);
        assertEquals(0, policy.blockUseSequence(47));
        assertTrue(policy.accept(new BodyHandStateSnapshot(7, query, BodyHandStateSnapshot.Reason.OK,
                ACCOUNT, BODY, 7, 3, "left", hands.hands())));
        policy.suspend();
        assertEquals(0, policy.blockUseSequence(48));
        assertNull(policy.confirmedSnapshot());
    }

    @Test void lostBlockUseAckAndQueryTimeoutFailClosedAndRetryOnce() {
        var policy = new BodyHandClientPolicy();
        policy.bind(ACCOUNT, BODY, 7);
        var hands = List.of(new BodyHandStateSnapshot.Hand("left", "token", "minecraft:stick", 1),
                new BodyHandStateSnapshot.Hand("right", null, null, 0));
        assertTrue(policy.accept(new BodyHandStateSnapshot(0, policy.query(1), BodyHandStateSnapshot.Reason.OK,
                ACCOUNT, BODY, 7, 3, "left", hands)));
        long use = policy.blockUseSequence(2);
        assertTrue(use > 0);
        policy.timeout(82);
        assertNotNull(policy.hudSnapshot()); // retained presentation is not proof of action authority
        assertNull(policy.inventoryView());
        assertEquals(0, policy.action(82));
        assertFalse(policy.blockUseResult(new BodyBlockUseResult(7, use, BodyBlockUseResult.Outcome.PLACED)));
        long fresh = policy.query(82);
        assertTrue(fresh > 0);
        assertEquals(0, policy.query(83));
        assertTrue(policy.accept(new BodyHandStateSnapshot(0, fresh, BodyHandStateSnapshot.Reason.OK,
                ACCOUNT, BODY, 7, 4, "left", hands)));
        assertNotNull(policy.hudSnapshot());

        long unanswered = policy.query(123);
        assertTrue(unanswered > 0);
        assertEquals(0, policy.query(124));
        policy.timeout(203);
        assertNotNull(policy.hudSnapshot()); // ordinary query timeout retains presentation, not action authority
        assertNull(policy.inventoryView());
        assertEquals(0, policy.blockUseSequence(203));
        assertEquals(0, policy.action(203));
        long retry = policy.query(204);
        assertTrue(retry > unanswered);
        assertEquals(0, policy.query(205));
        assertTrue(policy.accept(new BodyHandStateSnapshot(0, retry, BodyHandStateSnapshot.Reason.OK,
                ACCOUNT, BODY, 7, 4, "left", hands)));
        assertNotNull(policy.hudSnapshot());
    }

    @Test void externalTimedCommitRetainsHudButRejectsOldQueryAndRefreshesImmediately() {
        var policy = new BodyHandClientPolicy();
        policy.bind(ACCOUNT, BODY, 7);
        long first = policy.query(1);
        var state = snapshot(0, first, ACCOUNT, BODY, 7);
        assertTrue(policy.accept(state));
        long stale = policy.query(42);
        assertNotEquals(0, stale);
        policy.externalTimedCompletion();
        assertNull(policy.snapshot());
        assertSame(state, policy.hudSnapshot());
        long fresh = policy.query(43);
        assertTrue(fresh > stale);
        assertFalse(policy.accept(snapshot(0, stale, ACCOUNT, BODY, 7)));
        assertTrue(policy.accept(snapshot(0, fresh, ACCOUNT, BODY, 7)));
    }

    @Test void externalTimedCommitPreservesActionCorrelationUntilItsResult() {
        var policy = new BodyHandClientPolicy();
        policy.bind(ACCOUNT, BODY, 7);
        assertTrue(policy.accept(snapshot(0, policy.query(1), ACCOUNT, BODY, 7)));
        long action = policy.action(2);
        policy.externalTimedCompletion();
        assertEquals(0, policy.query(3));
        assertTrue(policy.result(result(7, action, BodyHandActionResult.Reason.OK), 4));
        assertNotEquals(0, policy.query(4));
    }

    @Test void handChangedBlockUseRequiresFreshProofBeforeAction() {
        var policy = new BodyHandClientPolicy();
        policy.bind(ACCOUNT, BODY, 7);
        var hands = List.of(new BodyHandStateSnapshot.Hand("left", "unarmed", "moonstation14:grenade", 1),
                new BodyHandStateSnapshot.Hand("right", null, null, 0));
        assertTrue(policy.accept(new BodyHandStateSnapshot(0, policy.query(1), BodyHandStateSnapshot.Reason.OK,
                ACCOUNT, BODY, 7, 3, "left", hands)));
        long use = policy.blockUseSequence(2);
        assertTrue(policy.blockUseResult(new BodyBlockUseResult(7, use, BodyBlockUseResult.Outcome.HAND_CHANGED)));
        assertNull(policy.inventoryView());
        assertEquals(0, policy.action(3));
        long refresh = policy.query(3);
        assertTrue(refresh > 0);
        var armed = List.of(new BodyHandStateSnapshot.Hand("left", "armed", "moonstation14:grenade", 1),
                new BodyHandStateSnapshot.Hand("right", null, null, 0));
        assertTrue(policy.accept(new BodyHandStateSnapshot(0, refresh, BodyHandStateSnapshot.Reason.OK,
                ACCOUNT, BODY, 7, 4, "left", armed)));
        assertTrue(policy.action(4) > use);
    }
    @Test void sharedConstructionDeferralAdmitsGirderAndPipeThenRequiresRemainingMaterial() {
        assertTrue(BodyHandClientPolicy.mayDeferConstructionUse(true, true,
                "moonstation14:steel", 2, "moonstation14:steel", 3));
        assertTrue(BodyHandClientPolicy.mayDeferConstructionUse(true, true,
                "moonstation14:steel", 2, "moonstation14:steel", 2));
        assertFalse(BodyHandClientPolicy.mayDeferConstructionUse(true, true,
                "moonstation14:steel", 2, "moonstation14:steel", 1));
        assertFalse(BodyHandClientPolicy.mayDeferConstructionUse(true, false,
                "moonstation14:steel", 2, "moonstation14:steel", 3));
        assertFalse(BodyHandClientPolicy.mayDeferConstructionUse(false, true,
                "moonstation14:steel", 2, "moonstation14:steel", 3));
    }

    @Test void acceptedTimedWorkInvalidatesReservedMaterialProofUntilFreshSnapshot() {
        var policy = new BodyHandClientPolicy();
        policy.bind(ACCOUNT, BODY, 7);
        var before = List.of(new BodyHandStateSnapshot.Hand("left", "steel-stack", "moonstation14:steel", 3),
                new BodyHandStateSnapshot.Hand("right", null, null, 0));
        assertTrue(policy.accept(new BodyHandStateSnapshot(0, policy.query(1), BodyHandStateSnapshot.Reason.OK,
                ACCOUNT, BODY, 7, 10, "left", before)));
        long use = policy.blockUseSequence(2);
        assertFalse(policy.blockUseResult(new BodyBlockUseResult(7, use + 1, BodyBlockUseResult.Outcome.WORK_STARTED)));
        assertTrue(policy.blockUseResult(new BodyBlockUseResult(7, use, BodyBlockUseResult.Outcome.WORK_STARTED)));
        assertTrue(policy.blockUseBarrier());
        assertNull(policy.snapshot());
        assertEquals(0, policy.blockUseSequence(3));
        long refresh = policy.query(3);
        var reserved = List.of(new BodyHandStateSnapshot.Hand("left", "steel-stack", "moonstation14:steel", 2),
                new BodyHandStateSnapshot.Hand("right", null, null, 0));
        assertFalse(policy.accept(new BodyHandStateSnapshot(0, refresh + 1, BodyHandStateSnapshot.Reason.OK,
                ACCOUNT, BODY, 7, 11, "left", reserved)));
        assertTrue(policy.accept(new BodyHandStateSnapshot(0, refresh, BodyHandStateSnapshot.Reason.OK,
                ACCOUNT, BODY, 7, 11, "left", reserved)));
        assertFalse(policy.blockUseBarrier());
        assertTrue(policy.blockUseSequence(4) > use);
    }

    @Test void timedOutActionRetriesQueryWithoutTransientStatus() {
        var policy = new BodyHandClientPolicy();
        policy.bind(ACCOUNT, BODY, 7);
        assertTrue(policy.accept(snapshot(0, policy.query(1), ACCOUNT, BODY, 7)));
        long action = policy.action(2);
        policy.timeout(82);
        assertNotNull(policy.hudSnapshot());
        assertNull(policy.feedback());
        assertFalse(policy.result(result(7, action, BodyHandActionResult.Reason.OK), 83));
        long retry = policy.query(83);
        assertTrue(retry > 0);
        assertTrue(policy.accept(snapshot(0, retry, ACCOUNT, BODY, 7)));
        assertNotNull(policy.hudSnapshot());
    }

    @Test void rejectionRemainsReadableWithoutStaleHandAndExpires() {
        var policy = new BodyHandClientPolicy();
        policy.bind(ACCOUNT, BODY, 7);
        assertTrue(policy.accept(snapshot(0, policy.query(1), ACCOUNT, BODY, 7)));
        long action = policy.action(2);
        assertTrue(policy.result(result(7, action, BodyHandActionResult.Reason.RECOVERY_REQUIRED), 10));
        assertNotNull(policy.hudSnapshot());
        assertEquals("Hands: RECOVERY_REQUIRED", policy.feedback());
        long refresh = policy.query(11);
        assertTrue(policy.accept(snapshot(0, refresh, ACCOUNT, BODY, 7)));
        policy.timeout(69);
        assertEquals("Hands: RECOVERY_REQUIRED", policy.feedback());
        policy.timeout(70);
        assertNull(policy.feedback());
        long denied = policy.action(71);
        assertTrue(policy.result(result(7, denied, BodyHandActionResult.Reason.DENIED), 72));
        assertNotNull(policy.hudSnapshot());
        assertEquals("Hands: server DENIED", policy.feedback());
        policy.timeout(132);
        assertNull(policy.feedback());
    }

    @Test void noInitialSnapshotNoActionAndStrictQueryCorrelation() {
        var policy = new BodyHandClientPolicy();
        policy.bind(ACCOUNT, BODY, 7);
        assertEquals(0, policy.action(1));
        long query = policy.query(1);
        assertEquals(0, policy.queryEpoch());
        assertFalse(policy.accept(snapshot(7, query, ACCOUNT, BODY, 7)));
        assertNull(policy.snapshot());
        assertEquals(0, policy.action(2));
        policy.timeout(81);
        long retry = policy.query(81);
        assertFalse(policy.accept(snapshot(0, query, ACCOUNT, BODY, 7)));
        assertFalse(policy.accept(snapshot(0, retry, ACCOUNT, BODY, 8)));
        policy.timeout(161);
        long next = policy.query(161);
        assertFalse(policy.accept(snapshot(0, next, ACCOUNT, UUID.randomUUID(), 7)));
        policy.timeout(241);
        long valid = policy.query(241);
        assertTrue(policy.accept(snapshot(0, valid, ACCOUNT, BODY, 7)));
        assertTrue(policy.action(122) > 0);
    }

    @Test void actionReplayAndRefreshBarrier() {
        var policy = new BodyHandClientPolicy();
        policy.bind(ACCOUNT, BODY, 7);
        assertTrue(policy.accept(snapshot(0, policy.query(1), ACCOUNT, BODY, 7)));
        long action = policy.action(2);
        assertEquals(0, policy.action(2));
        assertFalse(policy.result(result(8, action, BodyHandActionResult.Reason.OK), 2));
        assertFalse(policy.result(result(7, action + 1, BodyHandActionResult.Reason.OK), 2));
        assertTrue(policy.result(result(7, action, BodyHandActionResult.Reason.DENIED), 2));
        assertEquals("Hands: server DENIED", policy.feedback());
        assertNull(policy.snapshot());
        assertFalse(policy.result(result(7, action, BodyHandActionResult.Reason.OK), 3));
        assertEquals(0, policy.action(3));
        long fresh = policy.query(3);
        assertEquals(0, policy.queryEpoch()); // bootstrap until a full snapshot returns
        assertTrue(policy.accept(snapshot(0, fresh, ACCOUNT, BODY, 7)));
        assertEquals(action + 1, policy.action(4));
    }

    @Test void chatSafeHudLayoutAndCameraGate() {
        var position = BodyHandClientPolicy.handHudPosition(320, 240, 140, 9);
        assertEquals(new BodyHandClientPolicy.HudPosition(6, 54), position);
        assertTrue(position.y() + 11 < 100); // above the bottom-left chat region
        assertEquals(3, BodyHandClientPolicy.handHudPosition(100, 120, 95, 9).x());
        assertNull(BodyHandClientPolicy.handHudPosition(320, 90, 140, 9));
        assertFalse(BodyHandClientPolicy.showAimCue(false, true, true));
        assertFalse(BodyHandClientPolicy.showAimCue(true, false, true));
        assertFalse(BodyHandClientPolicy.showAimCue(true, true, false));
        assertTrue(BodyHandClientPolicy.showAimCue(true, true, true));
    }

    @Test void missingLocalCandidateIsTemporaryAndDistinctFromServerRejection() {
        var policy = new BodyHandClientPolicy();
        policy.noItemUnderCursor(1);
        assertNull(policy.feedback());
        policy.bind(ACCOUNT, BODY, 7);
        policy.noItemUnderCursor(2);
        assertNull(policy.feedback());
        assertTrue(policy.accept(snapshot(0, policy.query(3), ACCOUNT, BODY, 7)));
        policy.noItemUnderCursor(4);
        assertEquals("Hands: no item under cursor", policy.feedback());
        policy.timeout(64);
        assertNull(policy.feedback());
        long action = policy.action(65);
        assertTrue(policy.result(result(7, action, BodyHandActionResult.Reason.DENIED), 65));
        assertEquals("Hands: server DENIED", policy.feedback());
    }

    @Test void screenSuspensionKeepsReplayHighWaterAndDisablesKeysUntilRebound() {
        var policy = new BodyHandClientPolicy();
        policy.bind(ACCOUNT, BODY, 7);
        long first = policy.query(1);
        assertTrue(policy.accept(snapshot(0, first, ACCOUNT, BODY, 7)));
        long action = policy.action(2);
        assertTrue(policy.result(result(7, action, BodyHandActionResult.Reason.OK), 3));
        long beforeScreen = policy.query(3);
        policy.suspend(); // screen/camera transient: queued clicks and responses cannot trigger an action
        assertFalse(policy.accept(snapshot(0, beforeScreen, ACCOUNT, BODY, 7)));
        assertEquals(0, policy.query(43));
        assertEquals(0, policy.action(43));
        policy.bind(ACCOUNT, BODY, 7);
        long afterScreen = policy.query(44);
        assertTrue(afterScreen > beforeScreen);
        // Same gate, same bootstrap epoch: the fresh query is not a replay.
        assertTrue(policy.accept(snapshot(0, afterScreen, ACCOUNT, BODY, 7)));
        assertTrue(policy.action(45) > action);
    }

    @Test void resetAndEpochOrBodySwitchDiscardsPendingState() {
        var policy = new BodyHandClientPolicy();
        policy.bind(ACCOUNT, BODY, 7);
        long query = policy.query(1);
        UUID otherBody = UUID.randomUUID();
        policy.bind(ACCOUNT, otherBody, 7);
        assertFalse(policy.accept(snapshot(0, query, ACCOUNT, BODY, 7)));
        long switched = policy.query(2);
        assertTrue(switched > query);
        assertTrue(policy.accept(snapshot(0, switched, ACCOUNT, otherBody, 7)));
        long switchedAction = policy.action(2);
        assertTrue(switchedAction > 0);
        assertTrue(policy.result(result(7, switchedAction, BodyHandActionResult.Reason.OK), 3));
        policy.bind(ACCOUNT, BODY, 8);
        assertEquals(0, policy.action(3));
        long nextEpoch = policy.query(3);
        assertTrue(nextEpoch > switched);
        assertFalse(policy.accept(snapshot(0, switched, ACCOUNT, otherBody, 7)));
        assertTrue(policy.accept(snapshot(0, nextEpoch, ACCOUNT, BODY, 8)));
        assertTrue(policy.action(4) > switchedAction);
        policy.reset();
        assertNull(policy.snapshot());
        assertEquals(0, policy.query(4));
        assertEquals(0, policy.action(4));
        policy.bind(ACCOUNT, BODY, 8);
        long newConnection = policy.query(5);
        assertNotEquals(nextEpoch, newConnection);
        assertFalse(policy.accept(snapshot(0, nextEpoch, ACCOUNT, BODY, 8)));
        assertTrue(policy.accept(snapshot(0, newConnection, ACCOUNT, BODY, 8)));
    }
}
