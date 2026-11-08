package com.juicyslew.moonstation14.ms14.hands.live;

import com.juicyslew.moonstation14.ms14.combat.GunWorldUsePolicy;
import com.juicyslew.moonstation14.ms14.hands.ItemToken;
import com.juicyslew.moonstation14.ms14.hands.network.BodyHandActionRequest;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class BodyHeldItemInteractionTest {
    private static LiveHands hands(String active) {
        LiveHands state = LiveHands.CODEC.parse(com.mojang.serialization.JsonOps.INSTANCE,
                com.google.gson.JsonParser.parseString("{\"hands\":[{\"id\":\"left\"},{\"id\":\"right\"}],\"active\":\"" + active + "\",\"revision\":7}"))
                .getOrThrow();
        state = state.putWhole(7, "left", new ItemToken("source"), new ItemStack(Items.IRON_INGOT, 2)).state();
        return state.putWhole(8, "right", new ItemToken("target"), new ItemStack(Items.DIAMOND)).state();
    }

    private static BodyHandActionRequest request(long revision, String sourceHand, String targetHand, String targetToken) {
        return new BodyHandActionRequest(BodyHandActionRequest.Action.USE_ON_HAND, sourceHand, 1, 1, revision,
                null, "source", targetToken, -1, -1, -1, -1, -1, -1, null, null, targetHand);
    }

    private static GunWorldUsePolicy.InsertPlan plan() {
        return new GunWorldUsePolicy.InsertPlan(true, new ItemStack(Items.DIAMOND), new ItemStack(Items.IRON_INGOT),
                GunWorldUsePolicy.InsertStatus.ACCEPTED, null, null);
    }

    @Test
    void stagesSourceConsumptionAndTargetChangeWithoutMutatingOriginal() {
        LiveHands original = hands("left");
        LiveHands changed = BodyHeldItemInteraction.stage(original, request(9, "left", "right", "target"), plan())
                .orElseThrow();
        assertTrue(original.stackCopy("left").orElseThrow().is(Items.IRON_INGOT));
        assertEquals(2, original.stackCopy("left").orElseThrow().getCount());
        assertTrue(original.stackCopy("right").orElseThrow().is(Items.DIAMOND));
        assertEquals(1, original.stackCopy("right").orElseThrow().getCount());
        assertEquals(1, changed.stackCopy("left").orElseThrow().getCount());
        assertEquals(1, changed.stackCopy("right").orElseThrow().getCount());
        assertEquals("source", original.token("left").orElseThrow());
        assertEquals(9, original.revision());
    }

    @Test
    void stagesExplicitUseFromInactiveNamedHandAndPreservesSelection() {
        LiveHands original = hands("right");
        LiveHands changed = BodyHeldItemInteraction.stage(original, request(9, "left", "right", "target"), plan())
                .orElseThrow();
        assertEquals("right", changed.activeHand());
        assertEquals(1, changed.stackCopy("left").orElseThrow().getCount());
        assertEquals(1, changed.stackCopy("right").orElseThrow().getCount());
    }

    @Test
    void rejectsStaleRevisionWrongTargetSameHandAndReservations() {
        LiveHands state = hands("left");
        assertTrue(BodyHeldItemInteraction.stage(state, request(8, "left", "right", "target"), plan()).isEmpty());
        assertTrue(BodyHeldItemInteraction.stage(state, request(9, "left", "right", "wrong"), plan()).isEmpty());
        assertThrows(IllegalArgumentException.class, () -> request(9, "left", "left", "source"));
        assertTrue(BodyHeldItemInteraction.stage(state, request(9, "left", "right", "target"), plan()).isPresent());
        var staleSource = new BodyHandActionRequest(BodyHandActionRequest.Action.USE_ON_HAND, "left", 1, 1, 9,
                null, "stale-source", "target", -1, -1, -1, -1, -1, -1, null, null, "right");
        assertTrue(BodyHeldItemInteraction.stage(state, staleSource, plan()).isEmpty());
        LiveHands emptySource = LiveHands.CODEC.parse(com.mojang.serialization.JsonOps.INSTANCE,
                com.google.gson.JsonParser.parseString("{\"hands\":[{\"id\":\"left\"},{\"id\":\"right\"}],\"active\":\"right\",\"revision\":7}"))
                .getOrThrow().putWhole(7, "right", new ItemToken("target"), new ItemStack(Items.DIAMOND)).state();
        var reserved = emptySource.reserveHand(emptySource.revision(), "left", UUID.randomUUID()).orElseThrow();
        assertTrue(BodyHeldItemInteraction.stage(reserved, request(reserved.revision(), "left", "right", "target"), plan()).isEmpty());
        assertTrue(state.reserveHand(state.revision(), "left", UUID.randomUUID()).isEmpty(), "occupied source cannot be reserved");
        var staged = BodyHeldItemInteraction.stage(state, request(9, "left", "right", "target"), plan()).orElseThrow();
        assertTrue(BodyHeldItemInteraction.stage(staged, request(9, "left", "right", "target"), plan()).isEmpty(), "old proof cannot consume twice");
        assertEquals(2, state.stackCopy("left").orElseThrow().getCount());
        assertEquals(1, state.stackCopy("right").orElseThrow().getCount());
    }

    @Test void activeUseWrapperStillRejectsInactiveNamedHand() {
        LiveHands state = hands("right");
        assertTrue(state.replaceActiveUseStack(state.revision(), "left", "source",
                state.stackCopy("left").orElseThrow(), new ItemStack(Items.IRON_INGOT)).isEmpty());
    }
}
