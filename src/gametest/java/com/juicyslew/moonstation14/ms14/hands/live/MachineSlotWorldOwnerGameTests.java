package com.juicyslew.moonstation14.ms14.hands.live;

import com.juicyslew.moonstation14.component.ModDataComponents;
import com.juicyslew.moonstation14.entities.LooseItemEntity;
import com.juicyslew.moonstation14.gametest.support.GameTestTags;
import com.juicyslew.moonstation14.item.ModItems;
import com.juicyslew.moonstation14.ms14.atmos.tank.ModGasTanks;
import com.juicyslew.moonstation14.ms14.hands.ItemToken;
import com.juicyslew.moonstation14.ms14.hands.transfer.ItemLocation;
import com.juicyslew.moonstation14.ms14.hands.transfer.ItemLocationLedger;
import com.juicyslew.moonstation14.ms14.machine.itemslots.MachineItemSlot;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

import java.util.UUID;

@GameTestTags({"machine-slot-world-owner", "machine-slots"})
public final class MachineSlotWorldOwnerGameTests {
    private MachineSlotWorldOwnerGameTests() { }

    public static void canonicalReleaseFromNativeEntityOwner(GameTestHelper helper) {
        Fixture f = fixture(helper);
        require(BodyMachineItemSlotTransfer.ejectToWorldOwned(f.owner, f.slot, 1,
                f.ledger.snapshot().revision(), f.ledger, () -> true) == BodyMachineItemSlotTransfer.Result.SUCCESS,
                "registered native owner releases payload");
        require(f.slot.snapshot().token() == null && f.slot.snapshot().stack().isEmpty(), "slot emptied");
        require(!f.ledger.snapshot().locations().containsKey(f.token), "ownership token retired");
        var output = helper.getLevel().getEntitiesOfClass(LooseItemEntity.class,
                f.owner.getBoundingBox().inflate(8), e -> e.getItem().is(ModItems.PLASMA_TANK.get()));
        require(output.size() == 1 && ItemStack.matches(f.payload, output.getFirst().getItem()),
                "one canonical loose entity retains exact tank stack/components/count/name");
        require(output.getFirst().getItem().get(ModDataComponents.GAS_TANK_CONTENTS.get()) != null,
                "gas contents component preserved");
        require(BodyMachineItemSlotTransfer.ejectToWorldOwned(f.owner, f.slot, f.slot.snapshot().revision(),
                f.ledger.snapshot().revision(), f.ledger, () -> true) == BodyMachineItemSlotTransfer.Result.DENIED,
                "second release denied");
        require(helper.getLevel().getEntitiesOfClass(LooseItemEntity.class,
                f.owner.getBoundingBox().inflate(8), e -> e.getItem().is(ModItems.PLASMA_TANK.get())).size() == 1,
                "no duplicate output");
        f.owner.discard(); output.forEach(LooseItemEntity::discard); helper.succeed();
    }

    public static void invalidOwnerAndRevisionsAreDenied(GameTestHelper helper) {
        Fixture f = fixture(helper);
        ItemStack before = f.slot.snapshot().stack();
        long ledgerRevision = f.ledger.snapshot().revision();
        require(BodyMachineItemSlotTransfer.ejectToWorldOwned(f.owner, f.slot, 1,
                ledgerRevision, f.ledger, () -> false) == BodyMachineItemSlotTransfer.Result.DENIED,
                "false caller context denied");
        require(BodyMachineItemSlotTransfer.ejectToWorldOwned(f.owner, f.slot, 0,
                ledgerRevision, f.ledger, () -> true) == BodyMachineItemSlotTransfer.Result.DENIED,
                "stale slot revision denied");
        require(BodyMachineItemSlotTransfer.ejectToWorldOwned(f.owner, f.slot, 1,
                ledgerRevision + 1, f.ledger, () -> true) == BodyMachineItemSlotTransfer.Result.DENIED,
                "stale ledger revision denied");
        MachineItemSlot wrongOwner = new MachineItemSlot(UUID.randomUUID(), "input", stack -> true, f.ledger);
        require(BodyMachineItemSlotTransfer.ejectToWorldOwned(f.owner, wrongOwner, 1,
                ledgerRevision, f.ledger, () -> true) == BodyMachineItemSlotTransfer.Result.DENIED,
                "owner UUID mismatch denied");
        require(ItemStack.matches(before, f.slot.snapshot().stack()) && f.slot.snapshot().revision() == 1
                && f.ledger.snapshot().revision() == ledgerRevision,
                "all denials leave slot and ledger unchanged");
        require(helper.getLevel().getEntitiesOfClass(LooseItemEntity.class,
                f.owner.getBoundingBox().inflate(8), e -> e.getItem().is(ModItems.PLASMA_TANK.get())).isEmpty(),
                "denials publish nothing");
        f.owner.discard(); helper.succeed();
    }

    private static Fixture fixture(GameTestHelper helper) {
        LooseItemEntity owner = LooseItemEntity.create(helper.getLevel(), helper.absolutePos(new BlockPos(3, 1, 3)).getCenter(), new ItemStack(net.minecraft.world.item.Items.STICK));
        owner.setUUID(UUID.randomUUID());
        require(helper.getLevel().addFreshEntity(owner), "native owner registered");
        ItemStack payload = com.juicyslew.moonstation14.item.custom.GasTankItem.createStack(helper.getLevel(), ModGasTanks.PLASMA_ID);
        payload.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME, Component.literal("native tank payload"));
        payload.setCount(2);
        ItemLocationLedger ledger = new ItemLocationLedger();
        MachineItemSlot slot = new MachineItemSlot(owner.getUUID(), "input",
                stack -> stack.is(ModItems.PLASMA_TANK.get()), ledger);
        ItemToken token = new ItemToken("native-owner-token");
        ItemLocation source = new ItemLocation.WorldEntity(UUID.randomUUID(), owner.getUUID());
        require(ledger.register(token, source).accepted() && slot.insert(0, ledger.snapshot().revision(), token,
                source, payload), "real slot seeded with actual gas tank");
        return new Fixture(owner, slot, ledger, token, payload);
    }

    private record Fixture(LooseItemEntity owner, MachineItemSlot slot, ItemLocationLedger ledger,
                           ItemToken token, ItemStack payload) { }
    private static void require(boolean condition, String message) {
        if (!condition) throw new GameTestAssertException(message);
    }
}
