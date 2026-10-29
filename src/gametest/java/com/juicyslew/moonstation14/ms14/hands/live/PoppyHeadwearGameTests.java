package com.juicyslew.moonstation14.ms14.hands.live;

import com.juicyslew.moonstation14.component.ModDataAttachments;
import com.juicyslew.moonstation14.gametest.support.GameTestTags;
import com.juicyslew.moonstation14.item.ModItems;
import com.juicyslew.moonstation14.ms14.hands.ItemToken;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@PrefixGameTestTemplate(false)
@GameTestTags({"equipment", "equipment-transfer", "flower-headwear-validation"})
public final class PoppyHeadwearGameTests {
    private PoppyHeadwearGameTests() { }

    @GameTest(templateNamespace = "moonstation14", template = "empty", timeoutTicks = 30)
    @GameTestTags({"flower-headwear-validation", "merge-main-remaining", "merge-main-stage-two"})
    public static void registeredPoppyManuallyEquipsHeadWithoutQuickEquip(GameTestHelper helper) {
        Villager body = helper.spawn(EntityType.VILLAGER, new BlockPos(1, 1, 1));
        helper.runAfterDelay(1, () -> {
            try {
                require(LiveHands.initialize(body).isPresent(), "registered player body initializes live hands");
                ItemStack poppy = new ItemStack(ModItems.POPPY.get());
                var food = poppy.get(net.minecraft.core.component.DataComponents.FOOD);
                put(body, "left", "flower", poppy);
                var before = body.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get());
                var resolved = com.juicyslew.moonstation14.ms14.equipment.ModEquipmentItems.serverCatalog()
                        .get(com.juicyslew.moonstation14.ms14.equipment.ModInventoryTemplates.id("poppy"));
                require(resolved != null && resolved.clothing().isPresent(), "registered poppy clothing profile resolves");
                var clothing = resolved.clothing().orElseThrow();
                require(!clothing.quickEquip(), "resolved poppy profile explicitly disables quick equip");
                require(clothing.slotFlags().contains(com.juicyslew.moonstation14.ms14.equipment.InventoryTemplateData.SlotFlag.HEAD),
                        "resolved poppy profile admits head slot");
                require(clothing.wornModel().isEmpty() && resolved.armor().isEmpty(), "poppy has no armor or worn model");
                var ordinaryCalls = new java.util.concurrent.atomic.AtomicInteger();
                require(com.juicyslew.moonstation14.ms14.hands.client.HeldUseRequestDispatch.routeActivation(
                        () -> com.juicyslew.moonstation14.ms14.equipment.EquipmentFitPolicy.quickEquip(body.level(), poppy),
                        () -> false, () -> { ordinaryCalls.incrementAndGet(); return true; }), "ordinary use dispatch remains available");
                require(ordinaryCalls.get() == 1, "ordinary callback runs exactly once when quick equip is disabled");
                require(BodyEquipmentTransfer.transferOwned(body, "left", "head", "flower", before.revision(), true, () -> true)
                        == BodyEquipmentTransfer.Result.SUCCESS, "manual transfer equips real poppy to head");
                var after = body.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get());
                var head = LiveHands.equipmentSnapshot(body).orElseThrow().slots().stream()
                        .filter(slot -> slot.id().equals("head")).findFirst().orElseThrow();
                ItemStack worn = head.stack().orElseThrow();
                require(after.revision() == before.revision() + 1 && after.stackCopy("left").isEmpty()
                        && after.token("left").isEmpty(), "transfer releases source hand and advances revision once");
                require(head.token().filter("flower"::equals).isPresent() && worn.is(ModItems.POPPY.get())
                        && worn.getCount() == 1 && worn.getComponents().equals(poppy.getComponents()),
                        "actual head slot owns same poppy identity and components");
                require(java.util.Objects.equals(food, worn.get(net.minecraft.core.component.DataComponents.FOOD)),
                        "poppy food/reagent component survives transfer");
                helper.succeed();
            } finally {
                body.discard();
            }
        });
    }

    private static void put(Villager body, String hand, String token, ItemStack stack) {
        var before = body.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get());
        var next = before.putWhole(before.revision(), hand, new ItemToken(token), stack).state();
        if (!LiveHandsCommitGateway.commitTrusted(body, before, next))
            throw new GameTestAssertException("fixture hand ownership commit");
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new GameTestAssertException(message);
    }
}
