package com.juicyslew.moonstation14.ms14.disposal.item;

import com.juicyslew.moonstation14.ms14.hands.ItemToken;
import com.juicyslew.moonstation14.ms14.hands.transfer.ItemLocation;
import com.juicyslew.moonstation14.ms14.hands.transfer.ItemLocationLedger;
import net.minecraft.network.chat.Component;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import com.juicyslew.moonstation14.component.ModDataComponents;
import com.juicyslew.moonstation14.ms14.explosion.grenade.core.GrenadeFuse;
import com.juicyslew.moonstation14.ms14.explosion.grenade.core.GrenadeFuseData;
import com.juicyslew.moonstation14.item.ModItems;
import com.juicyslew.moonstation14.ms14.storage.grid.GridContents;
import com.juicyslew.moonstation14.ms14.storage.grid.GridGeometry;
import java.util.List;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class DisposalContentsTest {
    @Test void wholePayloadMovesBetweenOwnersAndEjectsWithoutLosingMetadataOrChildAnchors() {
        ItemLocationLedger ledger = new ItemLocationLedger();
        var source = new ItemLocation.BodyHand(UUID.randomUUID(), "right");
        ItemToken token = new ItemToken("bag-token");
        assertTrue(ledger.register(token, source).accepted());
        ItemStack stack = new ItemStack(Items.BUNDLE, 1);
        stack.set(DataComponents.CUSTOM_NAME, Component.literal("kept bag"));
        DisposalContents unit = new DisposalContents(UUID.randomUUID(), ledger);
        DisposalContents holder = new DisposalContents(UUID.randomUUID(), ledger);
        assertTrue(unit.accept(token, source, "payload-A", stack, ledger.snapshot().revision()));
        assertEquals("kept bag", unit.snapshot().get("payload-A").stack().getHoverName().getString());

        var child = new ItemToken("legitimate-child");
        var childCell = new ItemLocation.StorageCell(token, "main", ItemLocation.Orientation.NORTH, "0,0");
        assertTrue(ledger.register(child, childCell).accepted());
        long before = ledger.snapshot().revision();
        assertFalse(unit.accept(token, source, "duplicate", stack, before));
        assertEquals(before, ledger.snapshot().revision());

        var moved = unit.transferTo("payload-A", holder, "payload-A", ledger.snapshot().revision()).orElseThrow();
        assertEquals(token, moved.token());
        assertEquals(childCell, ledger.snapshot().locationOf(child).orElseThrow());
        var world = new ItemLocation.WorldEntity(UUID.randomUUID(), UUID.randomUUID());
        var ejected = holder.removeForEjection("payload-A", world, ledger.snapshot().revision()).orElseThrow();
        assertEquals("kept bag", ejected.stack().getHoverName().getString());
        assertEquals(world, ledger.snapshot().locationOf(token).orElseThrow());
        assertTrue(holder.snapshot().isEmpty());
    }

    @Test void staleSourceCannotClaimPayload() {
        var ledger = new ItemLocationLedger();
        ItemToken token = new ItemToken("stale");
        var real = new ItemLocation.BodyHand(UUID.randomUUID(), "left");
        assertTrue(ledger.register(token, real).accepted());
        var contents = new DisposalContents(UUID.randomUUID(), ledger);
        assertFalse(contents.accept(token, new ItemLocation.BodyHand(UUID.randomUUID(), "left"), "x",
                new ItemStack(Items.STONE), ledger.snapshot().revision()));
        assertTrue(contents.snapshot().isEmpty());
    }

    @Test void serializationValueAndOwnerTransferRetainStackComponentsAndAbsoluteFuseDeadline() {
        var ledger = new ItemLocationLedger();
        var source = new ItemLocation.BodyHand(UUID.randomUUID(), "left");
        var token = new ItemToken("armed-token");
        assertTrue(ledger.register(token, source).accepted());
        ItemStack grenade = new ItemStack(Items.BUNDLE, 1);
        grenade.set(DataComponents.CUSTOM_NAME, Component.literal("deadline-bearing payload"));
        grenade.set(ModDataComponents.GRENADE_FUSE.get(), GrenadeFuseData.fromFuse(GrenadeFuse.safe().prime(100)));
        var first = new DisposalContents(UUID.randomUUID(), ledger);
        var second = new DisposalContents(UUID.randomUUID(), ledger);
        assertTrue(first.accept(token, source, "fuse", grenade, ledger.snapshot().revision()));
        var transfer = first.transferTo("fuse", second, "fuse", ledger.snapshot().revision()).orElseThrow();
        assertEquals(160, transfer.stack().get(ModDataComponents.GRENADE_FUSE.get()).deadline());
        var serializedValue = second.encodedData();
        assertEquals("deadline-bearing payload", serializedValue.payloads().get("fuse").stack().getHoverName().getString());
        assertEquals(160, serializedValue.payloads().get("fuse").stack().get(ModDataComponents.GRENADE_FUSE.get()).deadline());
    }

    @Test void expiryConsumesExactLedgerOwnerBeforeCallbackAndCannotDetonateTwice() {
        var ledger = new ItemLocationLedger();
        var hand = new ItemLocation.BodyHand(UUID.randomUUID(), "left");
        var token = new ItemToken("expires");
        assertTrue(ledger.register(token, hand).accepted());
        ItemStack grenade = new ItemStack(ModItems.GRENADE.get(), 1);
        grenade.set(ModDataComponents.GRENADE_FUSE.get(), GrenadeFuseData.fromFuse(GrenadeFuse.safe().prime(10)));
        var owner = new DisposalContents(UUID.randomUUID(), ledger);
        assertTrue(owner.accept(token, hand, "entry", grenade, ledger.snapshot().revision()));
        int[] detonations = {0};
        var tick = owner.tickAt(70, ignored -> null, expired -> {
            detonations[0]++;
            assertTrue(ledger.snapshot().locationOf(expired.token()).isEmpty());
            assertFalse(owner.snapshot().containsKey(expired.entryId()));
        });
        assertEquals(1, tick.expired());
        owner.tickAt(71, ignored -> null, ignored -> detonations[0]++);
        assertEquals(1, detonations[0]);
    }

    @Test void staleExpiryCannotDetonateOrLosePayloadAndCanRetryAfterSourceRestored() {
        var ledger = new ItemLocationLedger();
        UUID ownerId = UUID.randomUUID();
        var hand = new ItemLocation.BodyHand(UUID.randomUUID(), "left");
        var token = new ItemToken("stale-expiry");
        assertTrue(ledger.register(token, hand).accepted());
        ItemStack grenade = new ItemStack(ModItems.GRENADE.get(), 1);
        grenade.set(ModDataComponents.GRENADE_FUSE.get(), GrenadeFuseData.fromFuse(GrenadeFuse.safe().prime(10)));
        var owner = new DisposalContents(ownerId, ledger);
        assertTrue(owner.accept(token, hand, "entry", grenade, ledger.snapshot().revision()));
        var disposal = new ItemLocation.DisposalContainer(ownerId, "entry");
        var other = new ItemLocation.WorldEntity(UUID.randomUUID(), UUID.randomUUID());
        assertTrue(ledger.compareAndMove(ledger.snapshot().revision(), token, disposal, other).accepted());
        int[] detonations = {0};
        owner.tickAt(70, ignored -> null, ignored -> detonations[0]++);
        assertEquals(0, detonations[0]);
        assertTrue(owner.snapshot().containsKey("entry"));
        assertTrue(ledger.compareAndMove(ledger.snapshot().revision(), token, other, disposal).accepted());
        owner.tickAt(70, ignored -> null, ignored -> detonations[0]++);
        assertEquals(1, detonations[0]);
    }

    @Test void bagGridComponentSurvivesPayloadCodecValueAndOwnerTransfer() {
        var ledger = new ItemLocationLedger();
        var hand = new ItemLocation.BodyHand(UUID.randomUUID(), "right");
        var token = new ItemToken("grid-bag");
        assertTrue(ledger.register(token, hand).accepted());
        ItemStack bag = new ItemStack(ModItems.BAG.get(), 1);
        var grid = GridContents.of(List.of(new GridContents.Entry("child-token", new ItemStack(Items.STICK),
                2, 1, GridGeometry.Rotation.DEG_0)));
        bag.set(ModDataComponents.GRID_CONTENTS.get(), grid);
        var a = new DisposalContents(UUID.randomUUID(), ledger);
        var b = new DisposalContents(UUID.randomUUID(), ledger);
        assertTrue(a.accept(token, hand, "bag", bag, ledger.snapshot().revision()));
        assertTrue(a.transferTo("bag", b, "bag", ledger.snapshot().revision()).isPresent());
        var persistedStack = b.encodedData().payloads().get("bag").stack();
        assertEquals(grid, persistedStack.get(ModDataComponents.GRID_CONTENTS.get()));
        var decoded = DisposalContents.decode(b.encodedData(), new ItemLocationLedger()).orElseThrow();
        assertEquals(grid, decoded.snapshot().get("bag").stack().get(ModDataComponents.GRID_CONTENTS.get()));
    }

    @Test void savedContentsRebindIdempotentlyWhenLevelLedgerAlreadyKnowsOwner() {
        var ledger = new ItemLocationLedger(); var hand = new ItemLocation.BodyHand(UUID.randomUUID(), "left");
        var token = new ItemToken("saved-owner"); assertTrue(ledger.register(token, hand).accepted());
        var owner = new DisposalContents(UUID.randomUUID(), ledger);
        assertTrue(owner.accept(token, hand, "persisted", new ItemStack(Items.STONE), ledger.snapshot().revision()));
        var decoded = DisposalContents.decode(owner.encodedData(), ledger).orElseThrow();
        assertEquals(1, decoded.snapshot().size());
        assertEquals(Items.STONE, decoded.snapshot().get("persisted").stack().getItem());
    }
}
