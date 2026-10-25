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

    @Test void initializeClaimsOnlyCanonicalSlotOnceAndPreservesCopiedWholeStack() {
        var ledger = new ItemLocationLedger(); var owner = UUID.randomUUID();
        var slot = slot(owner, "tankSlot", s -> s.is(Items.GLASS_BOTTLE), ledger);
        ItemStack named = new ItemStack(Items.GLASS_BOTTLE);
        named.set(DataComponents.CUSTOM_NAME, Component.literal("finite plasma tank"));
        assertFalse(slot.initialize(1, named));
        assertFalse(slot.initialize(0, new ItemStack(Items.STONE)));
        assertTrue(ledger.snapshot().locations().isEmpty());
        assertTrue(slot.initialize(0, named));
        var stored = slot.snapshot();
        assertEquals(1, stored.revision());
        assertEquals(new ItemLocation.MachineSlot(owner, "tankSlot"), ledger.snapshot().locations().get(stored.token()));
        assertEquals(Component.literal("finite plasma tank"), stored.stack().get(DataComponents.CUSTOM_NAME));
        assertFalse(slot.initialize(1, named));
        assertFalse(slot.initialize(0, named));
        assertEquals(1, slot.snapshot().revision());
        assertEquals(1, ledger.snapshot().locations().size());
    }

    @Test void initializedSlotPersistenceReclaimsExactlyOneClaimAndSavedEmptyRemainsEmpty() {
        var ledger = new ItemLocationLedger(); var owner = UUID.randomUUID();
        var populated = slot(owner, "tankSlot", s -> s.is(Items.GLASS_BOTTLE), ledger);
        assertTrue(populated.initialize(0, new ItemStack(Items.GLASS_BOTTLE)));
        var encoded = populated.encode(); var token = populated.snapshot().token();
        assertTrue(populated.suspend());
        var restored = slot(owner, "tankSlot", s -> s.is(Items.GLASS_BOTTLE), ledger);
        assertTrue(restored.restore(encoded));
        assertEquals(new ItemLocation.MachineSlot(owner, "tankSlot"), ledger.snapshot().locations().get(token));
        assertFalse(populated.resume());

        var emptyOwner = UUID.randomUUID();
        var empty = slot(emptyOwner, "tankSlot", s -> s.is(Items.GLASS_BOTTLE), new ItemLocationLedger());
        var emptyCodec = MachineItemSlot.codec();
        var savedEmpty = emptyCodec.parse(com.mojang.serialization.JsonOps.INSTANCE,
                emptyCodec.encodeStart(com.mojang.serialization.JsonOps.INSTANCE, empty.encode()).result().orElseThrow())
                .result().orElseThrow();
        var emptyRestored = slot(emptyOwner, "tankSlot", s -> s.is(Items.GLASS_BOTTLE), new ItemLocationLedger());
        assertTrue(emptyRestored.restore(savedEmpty));
        assertTrue(emptyRestored.snapshot().stack().isEmpty());
    }

    @Test void mutationRequiresTheCurrentCanonicalLedgerClaim() {
        var ledger = new ItemLocationLedger(); var owner = UUID.randomUUID();
        var slot = slot(owner, "tankSlot", s -> s.is(Items.GLASS_BOTTLE), ledger);
        assertTrue(slot.initialize(0, new ItemStack(Items.GLASS_BOTTLE)));
        var before = slot.snapshot();
        assertTrue(ledger.consume(ledger.snapshot().revision(), before.token(),
                new ItemLocation.MachineSlot(owner, "tankSlot")).accepted());
        assertFalse(slot.mutate(before.revision(), before.token(), stack -> { stack.setCount(2); return true; }));
        assertEquals(before.revision(), slot.snapshot().revision());
        assertEquals(1, slot.snapshot().stack().getCount());
    }
    @Test void batchReplacesAndConsumesOnlyWithExactSlotAndLedgerProofs() {
        var ledger = new ItemLocationLedger(); var owner = UUID.randomUUID();
        var source = slot(owner, "source", s -> s.is(Items.GLASS_BOTTLE), ledger);
        var beaker = slot(owner, "beaker", s -> s.is(Items.GLASS_BOTTLE), ledger);
        var hand = new ItemLocation.BodyHand(UUID.randomUUID(), "left");
        var sourceToken = new ItemToken("source"); var beakerToken = new ItemToken("beaker");
        assertTrue(ledger.register(sourceToken, hand).accepted());
        assertTrue(source.insert(0, ledger.snapshot().revision(), sourceToken, hand, new ItemStack(Items.GLASS_BOTTLE)));
        assertTrue(ledger.register(beakerToken, hand).accepted());
        assertTrue(beaker.insert(0, ledger.snapshot().revision(), beakerToken, hand, new ItemStack(Items.GLASS_BOTTLE)));
        var src = source.snapshot(); var dst = beaker.snapshot(); long lr = ledger.snapshot().revision();
        var changed = dst.stack(); changed.set(DataComponents.CUSTOM_NAME, Component.literal("changed"));
        var batch = java.util.List.of(new MachineItemSlot.SlotChange(source, src.revision(), src.token(), src.stack(), ItemStack.EMPTY),
                new MachineItemSlot.SlotChange(beaker, dst.revision(), dst.token(), dst.stack(), changed));
        assertTrue(MachineItemSlot.commitBatch(lr, batch).accepted());
        assertNull(source.snapshot().token());
        assertFalse(ledger.snapshot().locations().containsKey(sourceToken));
        assertEquals(Component.literal("changed"), beaker.snapshot().stack().get(DataComponents.CUSTOM_NAME));
    }

    @Test void batchPartiallyConsumesOwnedStackWithComponentUpdateAndRetiresTokenOnlyOnFinalConsumption() {
        var ledger = new ItemLocationLedger(); var owner = UUID.randomUUID();
        var source = slot(owner, "source", s -> s.is(Items.GLASS_BOTTLE), ledger);
        var beaker = slot(owner, "beaker", s -> s.is(Items.GLASS_BOTTLE), ledger);
        var hand = new ItemLocation.BodyHand(UUID.randomUUID(), "left");
        var sourceToken = new ItemToken("source"); var beakerToken = new ItemToken("beaker");
        assertTrue(ledger.register(sourceToken, hand).accepted());
        var namedSource = new ItemStack(Items.GLASS_BOTTLE, 30);
        namedSource.set(DataComponents.CUSTOM_NAME, Component.literal("source metadata"));
        assertTrue(source.insert(0, ledger.snapshot().revision(), sourceToken, hand, namedSource));
        assertTrue(ledger.register(beakerToken, hand).accepted());
        assertTrue(beaker.insert(0, ledger.snapshot().revision(), beakerToken, hand, new ItemStack(Items.GLASS_BOTTLE)));

        var src = source.snapshot(); var dst = beaker.snapshot(); long ledgerRevision = ledger.snapshot().revision();
        var remaining = src.stack(); remaining.shrink(1);
        var updatedBeaker = dst.stack(); updatedBeaker.set(DataComponents.CUSTOM_NAME, Component.literal("updated"));
        var batch = java.util.List.of(
                new MachineItemSlot.SlotChange(source, src.revision(), src.token(), src.stack(), remaining),
                new MachineItemSlot.SlotChange(beaker, dst.revision(), dst.token(), dst.stack(), updatedBeaker));

        assertFalse(MachineItemSlot.commitBatch(ledgerRevision - 1, batch).accepted());
        assertFalse(MachineItemSlot.commitBatch(ledgerRevision, java.util.List.of(
                new MachineItemSlot.SlotChange(source, src.revision() - 1, src.token(), src.stack(), remaining))).accepted());
        assertEquals(30, source.snapshot().stack().getCount());
        assertNull(beaker.snapshot().stack().get(DataComponents.CUSTOM_NAME));

        assertTrue(MachineItemSlot.commitBatch(ledgerRevision, batch).accepted());
        var partial = source.snapshot();
        assertEquals(29, partial.stack().getCount());
        assertEquals(src.token(), partial.token());
        assertEquals(Component.literal("source metadata"), partial.stack().get(DataComponents.CUSTOM_NAME));
        assertEquals(new ItemLocation.MachineSlot(owner, "source"), ledger.snapshot().locations().get(sourceToken));
        assertEquals(Component.literal("updated"), beaker.snapshot().stack().get(DataComponents.CUSTOM_NAME));
        assertEquals(ledgerRevision, ledger.snapshot().revision());

        var increased = partial.stack(); increased.setCount(30);
        assertFalse(MachineItemSlot.commitBatch(ledger.snapshot().revision(), java.util.List.of(
                new MachineItemSlot.SlotChange(source, partial.revision(), partial.token(), partial.stack(), increased))).accepted());
        var wrongItem = new ItemStack(Items.STONE);
        assertFalse(MachineItemSlot.commitBatch(ledger.snapshot().revision(), java.util.List.of(
                new MachineItemSlot.SlotChange(source, partial.revision(), partial.token(), partial.stack(), wrongItem))).accepted());
        assertEquals(29, source.snapshot().stack().getCount());

        assertTrue(MachineItemSlot.commitBatch(ledger.snapshot().revision(), java.util.List.of(
                new MachineItemSlot.SlotChange(source, partial.revision(), partial.token(), partial.stack(), ItemStack.EMPTY))).accepted());
        assertTrue(source.snapshot().stack().isEmpty());
        assertFalse(ledger.snapshot().locations().containsKey(sourceToken));
        assertEquals(new ItemLocation.MachineSlot(owner, "beaker"), ledger.snapshot().locations().get(beakerToken));
    }

    @Test void batchRejectsStaleProofsAndInvalidReplacementWithoutPartialChanges() {
        var ledger = new ItemLocationLedger(); var owner = UUID.randomUUID();
        var a = slot(owner, "a", s -> s.is(Items.GLASS_BOTTLE), ledger);
        var b = slot(owner, "b", s -> s.is(Items.GLASS_BOTTLE), ledger);
        var hand = new ItemLocation.BodyHand(UUID.randomUUID(), "left"); var ta = new ItemToken("a"); var tb = new ItemToken("b");
        ledger.register(ta, hand); assertTrue(a.insert(0, ledger.snapshot().revision(), ta, hand, new ItemStack(Items.GLASS_BOTTLE)));
        ledger.register(tb, hand); assertTrue(b.insert(0, ledger.snapshot().revision(), tb, hand, new ItemStack(Items.GLASS_BOTTLE)));
        var sa = a.snapshot(); var sb = b.snapshot(); long lr = ledger.snapshot().revision();
        var countChange = new ItemStack(Items.GLASS_BOTTLE, 2);
        var changes = java.util.List.of(new MachineItemSlot.SlotChange(a, sa.revision(), ta, sa.stack(), ItemStack.EMPTY),
                new MachineItemSlot.SlotChange(b, sb.revision(), tb, sb.stack(), countChange));
        var beforeA = a.snapshot(); var beforeB = b.snapshot(); var beforeLedger = ledger.snapshot();
        assertFalse(MachineItemSlot.commitBatch(lr, changes).accepted());
        assertFalse(MachineItemSlot.commitBatch(lr - 1, java.util.List.of(new MachineItemSlot.SlotChange(a, sa.revision(), ta,
                sa.stack(), ItemStack.EMPTY))).accepted());
        assertEquals(beforeA.revision(), a.snapshot().revision()); assertEquals(beforeB.revision(), b.snapshot().revision());
        assertEquals(beforeLedger, ledger.snapshot());
        assertFalse(MachineItemSlot.commitBatch(lr, java.util.List.of(changes.get(0), changes.get(0))).accepted());
    }

    @Test void batchRejectsDuplicateClaimsAndReentrantPolicyMutation() {
        var ledger = new ItemLocationLedger(); var owner = UUID.randomUUID();
        var first = slot(owner, "first", s -> s.is(Items.GLASS_BOTTLE), ledger);
        var duplicateLocation = slot(owner, "first", s -> s.is(Items.GLASS_BOTTLE), ledger);
        var hand = new ItemLocation.BodyHand(UUID.randomUUID(), "left"); var token = new ItemToken("batch-proof");
        assertTrue(ledger.register(token, hand).accepted());
        assertTrue(first.insert(0, ledger.snapshot().revision(), token, hand, new ItemStack(Items.GLASS_BOTTLE)));
        var snapshot = first.snapshot(); var ledgerRevision = ledger.snapshot().revision();
        var duplicate = new MachineItemSlot.SlotChange(duplicateLocation, 0, token,
                new ItemStack(Items.GLASS_BOTTLE), ItemStack.EMPTY);
        assertFalse(MachineItemSlot.commitBatch(ledgerRevision, java.util.List.of(
                new MachineItemSlot.SlotChange(first, snapshot.revision(), token, snapshot.stack(), ItemStack.EMPTY), duplicate)).accepted());
        var uniqueLocation = slot(owner, "unique", s -> s.is(Items.GLASS_BOTTLE), ledger);
        var duplicateToken = new MachineItemSlot.SlotChange(uniqueLocation, 0, token,
                new ItemStack(Items.GLASS_BOTTLE), ItemStack.EMPTY);
        assertFalse(MachineItemSlot.commitBatch(ledgerRevision, java.util.List.of(
                new MachineItemSlot.SlotChange(first, snapshot.revision(), token, snapshot.stack(), ItemStack.EMPTY), duplicateToken)).accepted());

        var secondOwner = owner; var reenter = new java.util.concurrent.atomic.AtomicBoolean();
        var second = slot(secondOwner, "z-second", s -> {
            if (reenter.get()) first.mutate(first.snapshot().revision(), token,
                    value -> { value.set(DataComponents.CUSTOM_NAME, Component.literal("reentrant")); return true; });
            return s.is(Items.GLASS_BOTTLE);
        }, ledger);
        var secondToken = new ItemToken("batch-proof-second");
        assertTrue(ledger.register(secondToken, hand).accepted());
        assertTrue(second.insert(second.snapshot().revision(), ledger.snapshot().revision(), secondToken, hand,
                new ItemStack(Items.GLASS_BOTTLE)));
        var firstProof = first.snapshot(); var secondProof = second.snapshot(); ledgerRevision = ledger.snapshot().revision();
        ItemStack changed = firstProof.stack(); changed.set(DataComponents.CUSTOM_NAME, Component.literal("reentrant"));
        var batch = java.util.List.of(
                new MachineItemSlot.SlotChange(first, firstProof.revision(), token, firstProof.stack(), ItemStack.EMPTY),
                new MachineItemSlot.SlotChange(second, secondProof.revision(), secondToken, secondProof.stack(), secondProof.stack()));
        // This predicate runs late in stable slot order and mutates the already-visited source.
        reenter.set(true);
        assertFalse(MachineItemSlot.commitBatch(ledgerRevision, batch).accepted());
        assertEquals(Component.literal("reentrant"), first.snapshot().stack().get(DataComponents.CUSTOM_NAME));
        assertEquals(secondProof.revision(), second.snapshot().revision());
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

    @Test void permanentDisposalDeletesPayloadAndCanonicalClaimIdempotently() {
        var ledger = new ItemLocationLedger(); var owner = UUID.randomUUID();
        var slot = slot(owner, "tankSlot", s -> s.is(Items.GLASS_BOTTLE), ledger);
        assertTrue(slot.initialize(0, new ItemStack(Items.GLASS_BOTTLE)));
        var token = slot.snapshot().token();
        assertTrue(slot.dispose());
        assertTrue(slot.dispose());
        assertTrue(slot.snapshot().stack().isEmpty());
        assertNull(slot.snapshot().token());
        assertTrue(ledger.snapshot().locations().isEmpty());
        assertTrue(slot.isRetired());
        assertFalse(ledger.snapshot().locations().containsKey(token));
        assertFalse(slot.restore(slot.encode()));
    }
}
