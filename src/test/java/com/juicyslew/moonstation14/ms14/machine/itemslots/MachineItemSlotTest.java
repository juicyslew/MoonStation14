package com.juicyslew.moonstation14.ms14.machine.itemslots;

import com.juicyslew.moonstation14.ms14.hands.ItemToken;
import com.juicyslew.moonstation14.ms14.hands.transfer.ItemLocation;
import com.juicyslew.moonstation14.ms14.hands.transfer.ItemLocationLedger;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.Test;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class MachineItemSlotTest {
    private static MachineItemSlot slot(UUID owner, String id, java.util.function.Predicate<ItemStack> policy, ItemLocationLedger ledger) {
        return new MachineItemSlot(owner, id, policy, ledger);
    }

    @Test void policyOccupiedAndStaleRejectionsDoNotMutateOwnership() {
        var ledger = new ItemLocationLedger(); var owner = UUID.randomUUID();
        var slot = slot(owner, "input", s -> s.is(Items.GLASS_BOTTLE), ledger);
        var hand = new ItemLocation.BodyHand(UUID.randomUUID(), "left");
        var token = new ItemToken("exact"); assertTrue(ledger.register(token, hand).accepted());
        var before = slot.snapshot(); var rev = ledger.snapshot().revision();
        assertFalse(slot.insert(0, rev, token, hand, new ItemStack(Items.STONE)));
        assertFalse(slot.insert(1, rev, token, hand, new ItemStack(Items.GLASS_BOTTLE)));
        assertEquals(before.revision(), slot.snapshot().revision());
        assertEquals(hand, ledger.snapshot().locations().get(token));
        assertTrue(slot.insert(0, rev, token, hand, new ItemStack(Items.GLASS_BOTTLE)));
        assertFalse(slot.insert(1, rev, new ItemToken("other"), hand, new ItemStack(Items.GLASS_BOTTLE)));
    }

    @Test void wholeComponentsTokenConditionalMutationAndEjectionAreExact() {
        var ledger = new ItemLocationLedger(); var owner = UUID.randomUUID();
        var slot = slot(owner, "one", s -> s.is(Items.GLASS_BOTTLE), ledger);
        var hand = new ItemLocation.BodyHand(UUID.randomUUID(), "right");
        var token = new ItemToken("payload-token"); ledger.register(token, hand);
        ItemStack named = new ItemStack(Items.GLASS_BOTTLE); named.set(DataComponents.CUSTOM_NAME, Component.literal("kept"));
        assertTrue(slot.insert(0, ledger.snapshot().revision(), token, hand, named));
        var stored = slot.snapshot();
        assertFalse(slot.mutate(stored.revision() - 1, token, s -> true));
        assertFalse(slot.mutate(stored.revision(), token, s -> { s.setCount(2); return true; }));
        assertTrue(slot.mutate(stored.revision(), token, s -> { s.set(DataComponents.CUSTOM_NAME, Component.literal("mutated")); return true; }));
        var updated = slot.snapshot(); assertEquals(token, updated.token());
        assertEquals(Component.literal("mutated"), updated.stack().get(DataComponents.CUSTOM_NAME));
        var world = new ItemLocation.WorldEntity(UUID.randomUUID(), UUID.randomUUID());
        assertTrue(slot.eject(updated.revision(), ledger.snapshot().revision() - 1, world).isEmpty());
        assertEquals(updated.revision(), slot.snapshot().revision());
        var payload = slot.eject(updated.revision(), ledger.snapshot().revision(), world).orElseThrow();
        assertEquals(token, payload.token()); assertEquals(Component.literal("mutated"), payload.stack().get(DataComponents.CUSTOM_NAME));
        assertTrue(slot.snapshot().stack().isEmpty());
    }

    @Test void codecRestoresAndRejectsConflictingDuplicateIdentityAndPoliciesAreIndependent() {
        var owner = UUID.randomUUID(); var ledger = new ItemLocationLedger();
        var first = slot(owner, "bottle", s -> s.is(Items.GLASS_BOTTLE), ledger);
        var data = first.encode();
        var codec = MachineItemSlot.codec();
        var encoded = codec.encodeStart(com.mojang.serialization.JsonOps.INSTANCE, data).result().orElseThrow();
        var decoded = codec.parse(com.mojang.serialization.JsonOps.INSTANCE, encoded).result().orElseThrow();
        assertTrue(slot(owner, "bottle", s -> s.is(Items.GLASS_BOTTLE), new ItemLocationLedger()).restore(decoded));
        assertFalse(slot(UUID.randomUUID(), "bottle", s -> true, ledger).restore(decoded));
        assertNotEquals(first.snapshot().slotId(), slot(owner, "other", s -> s.is(Items.STONE), ledger).snapshot().slotId());
        assertTrue(slot(owner, "other", s -> s.is(Items.STONE), ledger).snapshot().stack().isEmpty());
    }

    @Test void restoreRejectsTokenAlreadyOwnedElsewhereWithoutReplacingPayload() {
        var owner = UUID.randomUUID(); var ledger = new ItemLocationLedger();
        var token = new ItemToken("saved-token"); var source = new ItemLocation.BodyHand(UUID.randomUUID(), "left");
        var original = slot(owner, "saved", s -> s.is(Items.GLASS_BOTTLE), ledger);
        assertTrue(ledger.register(token, source).accepted());
        assertTrue(original.insert(0, ledger.snapshot().revision(), token, source, new ItemStack(Items.GLASS_BOTTLE)));
        MachineItemSlot.EncodedData saved = original.encode();
        var duplicate = slot(owner, "saved", s -> s.is(Items.GLASS_BOTTLE), ledger);
        assertFalse(duplicate.restore(saved));
        assertTrue(duplicate.snapshot().stack().isEmpty());
        assertEquals(new ItemLocation.MachineSlot(owner, "saved"), ledger.snapshot().locations().get(token));
    }

    @Test void releasingToUntrackedOwnersConsumesExactLedgerClaimsForRepeatedUse() {
        var ledger = new ItemLocationLedger(); var owner = UUID.randomUUID();
        var slot = slot(owner, "input", s -> s.is(Items.GLASS_BOTTLE), ledger);
        var hand = new ItemLocation.BodyHand(UUID.randomUUID(), "left");
        var first = new ItemToken("first"); assertTrue(ledger.register(first, hand).accepted());
        assertTrue(slot.insert(0, ledger.snapshot().revision(), first, hand, new ItemStack(Items.GLASS_BOTTLE)));
        var returned = slot.eject(1, ledger.snapshot().revision(), hand).orElseThrow();
        assertTrue(ledger.consume(ledger.snapshot().revision(), returned.token(), hand).accepted());
        assertFalse(ledger.snapshot().locations().containsKey(first));

        var second = new ItemToken("second"); assertTrue(ledger.register(second, hand).accepted());
        assertTrue(slot.insert(2, ledger.snapshot().revision(), second, hand, new ItemStack(Items.GLASS_BOTTLE)));
        var world = new ItemLocation.WorldEntity(UUID.randomUUID(), UUID.randomUUID());
        var dropped = slot.eject(3, ledger.snapshot().revision(), world).orElseThrow();
        assertTrue(ledger.consume(ledger.snapshot().revision(), dropped.token(), world).accepted());
        assertFalse(ledger.snapshot().locations().containsKey(second));
        var third = new ItemToken("third");
        assertTrue(ledger.register(third, hand).accepted());
        assertTrue(slot.insert(4, ledger.snapshot().revision(), third, hand, new ItemStack(Items.GLASS_BOTTLE)));
    }

    @Test void suspendedRuntimeOwnerCannotActAndFreshRestoreReclaimsExactSavedOwner() {
        var ledger = new ItemLocationLedger(); var owner = UUID.randomUUID();
        var slot = slot(owner, "input", s -> s.is(Items.GLASS_BOTTLE), ledger);
        var hand = new ItemLocation.BodyHand(UUID.randomUUID(), "left"); var token = new ItemToken("suspended");
        assertTrue(ledger.register(token, hand).accepted());
        assertTrue(slot.insert(0, ledger.snapshot().revision(), token, hand, new ItemStack(Items.GLASS_BOTTLE)));
        var encoded = slot.encode(); var current = slot.snapshot();
        assertTrue(slot.suspend());
        assertTrue(slot.isRetired());
        assertFalse(slot.canAccept(current.revision(), new ItemStack(Items.GLASS_BOTTLE)));
        assertFalse(slot.mutate(current.revision(), token, stack -> true));
        assertTrue(slot.eject(current.revision(), ledger.snapshot().revision(), hand).isEmpty());
        assertFalse(ledger.snapshot().locations().containsKey(token));

        var restored = slot(owner, "input", s -> s.is(Items.GLASS_BOTTLE), ledger);
        assertTrue(restored.restore(encoded));
        assertEquals(current.token(), restored.snapshot().token());
        assertTrue(ItemStack.matches(current.stack(), restored.snapshot().stack()));
        assertFalse(slot.resume()); // The exact claim now belongs to the fresh runtime owner.
        assertTrue(slot.isRetired());
    }
}
