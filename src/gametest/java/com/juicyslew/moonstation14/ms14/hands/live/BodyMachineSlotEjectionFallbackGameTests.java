package com.juicyslew.moonstation14.ms14.hands.live;

import com.juicyslew.moonstation14.component.ModDataAttachments;
import com.juicyslew.moonstation14.entities.LooseItemEntity;
import com.juicyslew.moonstation14.gametest.support.GameTestTags;
import com.juicyslew.moonstation14.ms14.hands.ItemToken;
import com.juicyslew.moonstation14.ms14.hands.transfer.ItemLocation;
import com.juicyslew.moonstation14.ms14.hands.transfer.ItemLocationLedger;
import com.juicyslew.moonstation14.ms14.machine.itemslots.MachineItemSlot;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@PrefixGameTestTemplate(false)
@GameTestTags({"machine-slots", "chemmaster", "hands"})
public final class BodyMachineSlotEjectionFallbackGameTests {
    private BodyMachineSlotEjectionFallbackGameTests() { }

    public static void activeOccupiedFallsBackToOtherEligibleHand(GameTestHelper helper) {
        Villager body = body(helper);
        ItemLocationLedger ledger = new ItemLocationLedger();
        MachineItemSlot slot = slot(body, ledger);
        ItemToken token = seed(body, slot, ledger, "other-hand");
        LiveHands initial = body.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get());
        String active = initial.activeHand();
        LiveHands occupied = initial.putWhole(initial.revision(), active, new ItemToken("active-occupant"), new ItemStack(Items.STICK)).state();
        body.setData(ModDataAttachments.LIVE_HANDS.get(), occupied);
        require(BodyMachineItemSlotTransfer.ejectToHandsOrWorldOwnedForFixture(body, slot, occupied.revision(),
                slot.snapshot().revision(), ledger.snapshot().revision(), ledger, () -> true)
                == BodyMachineItemSlotTransfer.Result.SUCCESS, "shared ejection policy chooses another empty hand");
        LiveHands result = body.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get());
        String other = result.handIds().stream().filter(hand -> !hand.equals(active)).findFirst().orElseThrow();
        require(result.token(other).filter(token.value()::equals).isPresent()
                && result.token(active).filter("active-occupant"::equals).isPresent(), "payload in other hand; active unchanged");
        require(slot.snapshot().stack().isEmpty() && !ledger.snapshot().locations().containsKey(token), "slot/claim retired once");
        cleanup(helper, body);
        helper.succeed();
    }

    public static void bothHandsFullUseCanonicalWorldRelease(GameTestHelper helper) {
        Villager body = body(helper);
        ItemLocationLedger ledger = new ItemLocationLedger();
        MachineItemSlot slot = slot(body, ledger);
        ItemToken token = seed(body, slot, ledger, "world-fallback");
        LiveHands initial = body.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get());
        LiveHands full = initial.putWhole(initial.revision(), "left", new ItemToken("left-occupant"), new ItemStack(Items.STICK)).state()
                .putWhole(initial.revision() + 1, "right", new ItemToken("right-occupant"), new ItemStack(Items.APPLE)).state();
        body.setData(ModDataAttachments.LIVE_HANDS.get(), full);
        require(BodyMachineItemSlotTransfer.ejectToHandsOrWorldOwnedForFixture(body, slot, full.revision(),
                slot.snapshot().revision(), ledger.snapshot().revision(), ledger, () -> true)
                == BodyMachineItemSlotTransfer.Result.SUCCESS, "shared policy uses canonical world fallback");
        var drops = helper.getLevel().getEntitiesOfClass(LooseItemEntity.class, helper.getBounds(), e -> e.getItem().is(Items.DIAMOND));
        require(drops.size() == 1 && ItemStack.matches(payload(), drops.getFirst().getItem()), "one exact canonical payload entity");
        BlockPos origin = helper.absolutePos(BlockPos.ZERO);
        BlockPos at = drops.getFirst().blockPosition();
        require(at.getX() >= origin.getX() && at.getX() < origin.getX() + 16
                && at.getY() >= origin.getY() && at.getY() < origin.getY() + 10
                && at.getZ() >= origin.getZ() && at.getZ() < origin.getZ() + 16, "drop remains within fixture bounds");
        require(slot.snapshot().stack().isEmpty() && !ledger.snapshot().locations().containsKey(token), "no slot or ledger claim remains");
        cleanup(helper, body);
        helper.succeed();
    }

    private static Villager body(GameTestHelper helper) {
        Villager body = helper.spawn(EntityType.VILLAGER, new BlockPos(2, 1, 2));
        body.setNoAi(true);
        require(BodyHandBootstrap.ensure(body) == BodyHandBootstrap.Result.INITIALIZED, "compatible fixture hands initialized");
        return body;
    }
    private static MachineItemSlot slot(Villager body, ItemLocationLedger ledger) {
        return new MachineItemSlot(body.getUUID(), "input", stack -> stack.is(Items.DIAMOND), ledger);
    }
    private static ItemToken seed(Villager body, MachineItemSlot slot, ItemLocationLedger ledger, String id) {
        ItemToken token = new ItemToken(id);
        ItemLocation source = new ItemLocation.BodyHand(body.getUUID(), "left");
        require(ledger.register(token, source).accepted() && slot.insert(0, ledger.snapshot().revision(), token, source, payload()), "slot payload seeded");
        return token;
    }
    private static ItemStack payload() {
        ItemStack stack = new ItemStack(Items.DIAMOND, 3);
        stack.set(DataComponents.CUSTOM_NAME, Component.literal("ejection fallback exact payload"));
        return stack;
    }
    private static void cleanup(GameTestHelper helper, Villager body) {
        body.discard();
        helper.getLevel().getEntitiesOfClass(LooseItemEntity.class, helper.getBounds(), ignored -> true).forEach(LooseItemEntity::discard);
    }
    private static void require(boolean condition, String message) {
        if (!condition) throw new GameTestAssertException(message);
    }
}
