package com.juicyslew.moonstation14.ms14.hands.live;

import com.juicyslew.moonstation14.block.ModBlocks;
import com.juicyslew.moonstation14.component.ModDataAttachments;
import com.juicyslew.moonstation14.gametest.support.GameTestTags;
import com.juicyslew.moonstation14.item.ModItems;
import com.juicyslew.moonstation14.item.custom.GasTankItem;
import com.juicyslew.moonstation14.ms14.atmos.core.GasMixture;
import com.juicyslew.moonstation14.ms14.atmos.core.GasType;
import com.juicyslew.moonstation14.ms14.atmos.tank.GasTankContents;
import com.juicyslew.moonstation14.ms14.atmos.tank.ModGasTanks;
import com.juicyslew.moonstation14.ms14.hands.ItemToken;
import com.juicyslew.moonstation14.ms14.hands.transfer.ItemLocation;
import com.juicyslew.moonstation14.ms14.hands.transfer.ItemLocationLedger;
import com.juicyslew.moonstation14.ms14.radiation.ModRadiationCollectors;
import com.juicyslew.moonstation14.ms14.radiation.RadiationCollectorBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Actual collector-owned slot composition against canonical trusted transfer cores (not auth-route coverage). */
@PrefixGameTestTemplate(false)
@GameTestTags({"collector_interaction", "machine-slots"})
public final class RadiationCollectorInteractionGameTests {
    private RadiationCollectorInteractionGameTests() { }

    @GameTest(templateNamespace = "moonstation14", template = "pa_contained_clear", timeoutTicks = 50)
    @GameTestTags({"collector-transfer-regression", "collector_interaction", "machine-slots", "hands"})
    public static void collectorTankInsertSwapAndEjectPreserveTypedGasPayloadGameTest(GameTestHelper helper) {
        collectorTankInsertSwapAndEjectPreserveTypedGasPayload(helper);
    }

    public static void collectorTankInsertSwapAndEjectPreserveTypedGasPayload(GameTestHelper helper) {
        BlockPos pos = helper.absolutePos(new BlockPos(4, 1, 4));
        helper.getLevel().setBlock(pos, ModBlocks.RADIATION_COLLECTOR.get().defaultBlockState(), 3);
        RadiationCollectorBlockEntity collector = (RadiationCollectorBlockEntity) helper.getLevel().getBlockEntity(pos);
        require(collector != null && collector.selectProfile(ModRadiationCollectors.NO_TANK_ID), "select resolved no-tank profile");
        var slot = collector.tankSlot(); // resolves/initializes the actual registered BE slot
        require(slot.snapshot().stack().isEmpty() && slot.snapshot().token() == null,
                "resolved no-tank profile initializes an actually empty slot before insertion");

        Villager body = helper.spawn(EntityType.VILLAGER, new BlockPos(2, 1, 2));
        body.setNoAi(true);
        BodyHandBootstrap.Result bootstrap = BodyHandBootstrap.ensure(body);
        require(bootstrap == BodyHandBootstrap.Result.INITIALIZED || bootstrap == BodyHandBootstrap.Result.PRESERVED,
                "body hands initialize through normal bootstrap");
        LiveHands hands = body.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get());
        require(hands != null && hands.compatible(body), "fixture body owns compatible live hands");
        ItemLocationLedger ledger = com.juicyslew.moonstation14.ms14.hands.transfer.LevelItemLocationLedgers.forLevel(
                (net.minecraft.server.level.ServerLevel) helper.getLevel());

        ItemStack oxygenTank = tank(helper, GasType.OXYGEN, 4.25);
        String firstToken = "collector-oxygen-" + body.getUUID();
        String hand = hands.activeHand();
        long revision = BodyHandItemTransferGameTestAccess.holdItem(body, hand, firstToken, oxygenTank);
        BodyMachineItemSlotTransfer.Result insertion = BodyMachineItemSlotTransfer.insertOwnedForFixture(body, hand,
                firstToken, revision, slot, slot.snapshot().revision(), ledger.snapshot().revision(), ledger,
                () -> !collector.isRemoved());
        require(insertion == BodyMachineItemSlotTransfer.Result.SUCCESS,
                "canonical transfer inserts actual typed gas tank (result=" + insertion
                        + ", bodyLive=" + body.isAddedToLevel() + ", bodyId=" + body.getUUID()
                        + ", canAct=" + com.juicyslew.moonstation14.ms14.character.CharacterControlSystem.canAct(body)
                        + ", medical=" + com.juicyslew.moonstation14.ms14.mobs.MobStateSystem.isAlive(body)
                        + ", handsCompatible=" + hands.compatible(body) + ", handRevision=" + hands.revision()
                        + ", requestedHandRevision=" + revision + ", slot=" + slot.snapshot()
                        + ", ledgerRevision=" + ledger.snapshot().revision() + ")");
        var installed = slot.snapshot();
        var gasInstalled = GasTankItem.contents(installed.stack(), helper.getLevel()).orElseThrow();
        require(installed.token().equals(new ItemToken(firstToken)) && gasInstalled.mixture().moles(GasType.OXYGEN) == 4.25
                        && gasInstalled.mixture().moles(GasType.PLASMA) == 0 && !collector.enabled(),
                "oxygen payload and token retained; insertion does not toggle collector");

        ItemStack plasmaTank = tank(helper, GasType.PLASMA, 2.75);
        LiveHands current = body.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get());
        String secondToken = "collector-plasma-" + body.getUUID();
        revision = BodyHandItemTransferGameTestAccess.holdItem(body, current.activeHand(), secondToken, plasmaTank);
        require(BodyMachineItemSlotTransfer.swapActiveHandOwnedForFixture(body, secondToken, revision, slot,
                installed.revision(), ledger.snapshot().revision(), ledger, () -> !collector.isRemoved())
                == BodyMachineItemSlotTransfer.Result.SUCCESS, "canonical occupied-slot swap succeeds");
        current = body.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get());
        var returned = current.stackCopy(current.activeHand()).orElseThrow();
        var outgoingGas = GasTankItem.contents(returned, helper.getLevel()).orElseThrow();
        var replacement = slot.snapshot();
        var replacementGas = GasTankItem.contents(replacement.stack(), helper.getLevel()).orElseThrow();
        require(current.token(current.activeHand()).orElseThrow().equals(firstToken)
                        && outgoingGas.mixture().moles(GasType.OXYGEN) == 4.25
                        && replacement.token().equals(new ItemToken(secondToken))
                        && replacementGas.mixture().moles(GasType.PLASMA) == 2.75 && !collector.enabled(),
                "swap preserves both whole gas payloads and does not toggle machine");

        require(BodyMachineItemSlotTransfer.ejectToHandsOrWorldOwnedForFixture(body, slot, current.revision(),
                replacement.revision(), ledger.snapshot().revision(), ledger, () -> !collector.isRemoved())
                == BodyMachineItemSlotTransfer.Result.SUCCESS, "canonical owned fixture ejects collector slot to free hand");
        LiveHands ejected = body.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get());
        String destinationHand = ejected.handIds().stream()
                .filter(id -> ejected.token(id).map(secondToken::equals).orElse(false)).findFirst().orElseThrow();
        require(!destinationHand.equals(ejected.activeHand())
                        && ejected.token(destinationHand).orElseThrow().equals(secondToken)
                        && GasTankItem.contents(ejected.stackCopy(destinationHand).orElseThrow(), helper.getLevel())
                        .orElseThrow().mixture().moles(GasType.PLASMA) == 2.75,
                "eject preserves occupied active hand and returns exact installed tank to the other free hand");
        require(slot.dispose(), "empty collector slot disposes cleanly");
        body.discard();
        helper.getLevel().removeBlock(pos, false);
        helper.succeed();
    }

    private static ItemStack tank(GameTestHelper helper, GasType gas, double moles) {
        ItemStack stack = GasTankItem.createStack(helper.getLevel(), ModGasTanks.PLASMA_ID);
        var profile = GasTankItem.contents(stack, helper.getLevel()).orElseThrow();
        stack.set(com.juicyslew.moonstation14.component.ModDataComponents.GAS_TANK_CONTENTS.get(),
                new GasTankContents(profile.prototypeId(), new GasMixture(java.util.Map.of(gas, moles), 293.15,
                        profile.mixture().volumeCubicMeters())));
        require(stack.is(ModItems.PLASMA_TANK.get()) && GasTankItem.contents(stack, helper.getLevel()).isPresent(),
                "canonical gas tank item validates typed contents");
        return stack;
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new GameTestAssertException(message);
    }
}
