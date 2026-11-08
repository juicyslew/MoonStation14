package com.juicyslew.moonstation14.ms14.atmos.tank;

import com.juicyslew.moonstation14.component.ModDataComponents;
import com.juicyslew.moonstation14.gametest.support.GameTestTags;
import com.juicyslew.moonstation14.item.ModItems;
import com.juicyslew.moonstation14.item.custom.GasTankItem;
import com.juicyslew.moonstation14.ms14.atmos.core.GasType;
import com.juicyslew.moonstation14.ms14.hands.ItemToken;
import com.juicyslew.moonstation14.ms14.hands.transfer.ItemLocation;
import com.juicyslew.moonstation14.ms14.hands.transfer.ItemLocationLedger;
import com.juicyslew.moonstation14.ms14.machine.itemslots.MachineItemSlot;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.RegistryOps;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.UUID;

@GameTestTags({"gas-canister-domain"})
@PrefixGameTestTemplate(false)
public final class GasCanisterDomainPersistenceGameTests {
    @GameTest(template = "empty", timeoutTicks = 20, templateNamespace = "moonstation14")
    public static void occupiedTankSlotCanisterSnapshotRoundTripsOnce(GameTestHelper helper) {
        var level = helper.getLevel();
        var profile = ModGasCanisters.catalog(level).get(ModGasCanisters.PLASMA_ID);
        helper.assertTrue(profile != null, "registered canister profile is available");
        var stateOwner = UUID.randomUUID();
        var ledger = new ItemLocationLedger();
        var source = new ItemLocation.BodyHand(UUID.randomUUID(), "left");
        var token = new ItemToken("canister-domain-tank-" + UUID.randomUUID());
        helper.assertTrue(ledger.register(token, source).accepted(), "fixture source is a valid ledger location");

        ItemStack tank = GasTankItem.createStack(level, ModGasTanks.PLASMA_FILLED_ID);
        tank.set(DataComponents.CUSTOM_NAME, Component.literal("Domain persistence tank"));
        tank.set(DataComponents.CUSTOM_MODEL_DATA, new net.minecraft.world.item.component.CustomModelData(4));
        var initial = GasTankItem.contents(tank, level).orElseThrow().mixture();
        var state = new GasCanisterState(ModGasCanisters.PLASMA_ID, profile, stateOwner, ledger);
        var slot = state.slot();
        helper.assertTrue(slot.insert(0, ledger.snapshot().revision(), token, source, tank), "whole valid registered tank enters slot");
        var slotRevision = slot.snapshot().revision();
        var saved = state.snapshot();
        helper.assertTrue(state.suspend(), "snapshot owner suspends its active claim");
        helper.assertTrue(!ledger.snapshot().locations().containsKey(token), "suspension releases active location claim");

        var registryOps = RegistryOps.create(NbtOps.INSTANCE, level.registryAccess());
        var encoded = GasCanisterState.codec().encodeStart(registryOps, saved).getOrThrow();
        helper.assertTrue(encoded instanceof CompoundTag, "saved domain state encodes through level registry ops");
        var decoded = GasCanisterState.codec().parse(registryOps, encoded).getOrThrow();
        var restored = GasCanisterState.restore(decoded, profile, ledger);
        var restoredSlot = restored.slot();
        var contents = GasTankItem.contents(restoredSlot.snapshot().stack(), level).orElseThrow();
        helper.assertTrue(restored.owner().equals(stateOwner) && restoredSlot.snapshot().revision() == slotRevision,
                "stable owner and slot revision survive codec persistence");
        helper.assertTrue(restoredSlot.snapshot().token().equals(token)
                        && Math.abs(contents.mixture().moles(GasType.PLASMA) - initial.moles(GasType.PLASMA)) < 1e-6,
                "tank token and gas payload survive registry-aware stack codec");
        ItemStack restoredStack = restoredSlot.snapshot().stack();
        helper.assertTrue(restoredStack.is(ModItems.PLASMA_TANK.get()) && restoredStack.getCount() == tank.getCount()
                        && restoredStack.get(DataComponents.CUSTOM_NAME).equals(Component.literal("Domain persistence tank"))
                        && restoredStack.get(DataComponents.CUSTOM_MODEL_DATA).equals(new net.minecraft.world.item.component.CustomModelData(4)),
                "exact whole stack and complete metadata survive persistence");
        helper.assertTrue(ledger.snapshot().locations().get(token).equals(new ItemLocation.MachineSlot(stateOwner, "tank_slot")),
                "restored slot owns exactly one ledger claim");
        helper.assertTrue(ledger.snapshot().locations().size() == 1, "ledger has no duplicate or unrelated claims");
        boolean conflictRejected;
        try {
            GasCanisterState.restore(decoded, profile, ledger);
            conflictRejected = false;
        } catch (IllegalArgumentException expected) {
            conflictRejected = true;
        }
        helper.assertTrue(conflictRejected, "conflicting second restore cannot duplicate token ownership");
        helper.assertTrue(restored.resume() && restored.resume(), "resume is idempotent");
        helper.assertTrue(ledger.snapshot().locations().get(token).equals(new ItemLocation.MachineSlot(stateOwner, "tank_slot")),
                "idempotent resume preserves the single claim");
        helper.succeed();
    }
}
