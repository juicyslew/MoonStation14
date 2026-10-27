package com.juicyslew.moonstation14.ms14.hands.live;

import com.juicyslew.moonstation14.component.ModDataAttachments;
import com.juicyslew.moonstation14.gametest.support.GameTestTags;
import com.juicyslew.moonstation14.ms14.equipment.EquipmentItemData;
import com.juicyslew.moonstation14.ms14.equipment.ProtectedFromStepTriggers;
import com.juicyslew.moonstation14.ms14.hands.ItemToken;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@PrefixGameTestTemplate(false)
@GameTestTags("step-trigger-protection")
public final class EquipmentStepTriggerProtectionGameTests {
    private EquipmentStepTriggerProtectionGameTests() { }

    /** Test-only trusted fixture arrangement, retaining the ordinary equipment transfer owner. */
    public static void equipHardsuitFixture(net.minecraft.world.entity.LivingEntity body, String hand,
                                            String token, ItemStack coat) {
        var state = LiveHands.initialize(body).orElseThrow(() -> new GameTestAssertException("target hands initialize"));
        var staged = state.putWhole(state.revision(), hand, new ItemToken(token), coat);
        require(staged.succeeded() && LiveHandsCommitGateway.commitOwned(body, state, staged.state()),
                "trusted fixture stages coat through LiveHands commit gateway");
        var committed = body.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get());
        require(BodyEquipmentTransfer.transferOwned(body, hand, "outerClothing", token, committed.revision(), true,
                        () -> true) == BodyEquipmentTransfer.Result.SUCCESS,
                "ordinary equipment owner transfers coat to outer clothing");
        require(com.juicyslew.moonstation14.ms14.equipment.MeleeInjectionProtectionPolicy.protects(
                         com.juicyslew.moonstation14.ms14.equipment.ResolvedWornEquipment.entries(body), false),
                 "resolved worn hardsuit equipment is recognized");
    }

    /** Test-only owned hand setup for a separately enrolled target; production transfer rules remain unchanged. */
    public static void putOwnedHandFixture(net.minecraft.world.entity.LivingEntity body, String hand,
                                           String token, ItemStack stack) {
        var state = LiveHands.initialize(body).orElseThrow(() -> new GameTestAssertException("target hands initialize"));
        var staged = state.putWhole(state.revision(), hand, new ItemToken(token), stack);
        require(staged.succeeded() && LiveHandsCommitGateway.commitOwned(body, state, staged.state()),
                "fixture source commits through owned LiveHands gateway");
    }

    @GameTest(templateNamespace = "moonstation14", template = "empty", timeoutTicks = 40, batch = "catalog-equipment-step-protection-shoes")
    @com.juicyslew.moonstation14.gametest.support.GameTestTags({"merge-main-remaining"})
    public static void liveWornFeetProtectionQuery(GameTestHelper helper) {
        var bodies = new java.util.ArrayList<Villager>();
        helper.runAfterDelay(1, () -> {
            var catalog = com.juicyslew.moonstation14.ms14.equipment.ModEquipmentItems.serverCatalog();
            Map<net.minecraft.resources.ResourceLocation, EquipmentItemData> prior = new LinkedHashMap<>(catalog.asMap());
            try {
                var id = com.juicyslew.moonstation14.ms14.equipment.ModInventoryTemplates.id("black_shoes");
                var shoes = prior.get(id);
                require(shoes != null, "registered shoes profile exists");
                var updated = new LinkedHashMap<>(prior);
                updated.put(id, new EquipmentItemData(shoes.item(), shoes.clothing(), shoes.allowSuitStorage(), shoes.tags(),
                        shoes.armor(), shoes.supportedActionGrant(), shoes.noSlip(), Optional.of(ProtectedFromStepTriggers.DEFAULT),
                        shoes.contactDamageProtection()));
                var pocketItemId = com.juicyslew.moonstation14.ms14.equipment.ModInventoryTemplates.id("black_bandana");
                var pocketItem = prior.get(pocketItemId);
                require(pocketItem != null && pocketItem.clothing().isPresent()
                                && "Small".equals(pocketItem.item().size()),
                        "registered small clothing item profile exists for pocket admission");
                updated.put(pocketItemId, new EquipmentItemData(pocketItem.item(), pocketItem.clothing(),
                        pocketItem.allowSuitStorage(), pocketItem.tags(), pocketItem.armor(),
                        pocketItem.supportedActionGrant(), pocketItem.noSlip(), Optional.of(ProtectedFromStepTriggers.DEFAULT),
                        pocketItem.contactDamageProtection()));
                com.juicyslew.moonstation14.ms14.prototype.PrototypeRuntime.serverManager()
                        .publishDecoded(com.juicyslew.moonstation14.ms14.equipment.ModEquipmentItems.TYPE, updated);

                Villager held = body(helper, new BlockPos(1, 1, 1), bodies);
                putInHand(held, "shoes", new ItemStack(com.juicyslew.moonstation14.item.ModEquipmentItems.BLACK_SHOES.get()));
                require(!policy(held), "held shoes do not protect");

                Villager worn = body(helper, new BlockPos(3, 1, 1), bodies);
                putInHand(worn, "shoes", new ItemStack(com.juicyslew.moonstation14.item.ModEquipmentItems.BLACK_SHOES.get()));
                transfer(worn, "shoes", "shoes", "shoes", com.juicyslew.moonstation14.item.ModEquipmentItems.BLACK_SHOES.get());
                require(policy(worn), "shoes in declared FEET slot protect");

                Villager pocket = body(helper, new BlockPos(5, 1, 1), bodies);
                putInHand(pocket, "jumpsuit", new ItemStack(com.juicyslew.moonstation14.item.ModEquipmentItems.GREY_JUMPSUIT.get()));
                transfer(pocket, "jumpsuit", "jumpsuit", "jumpsuit", com.juicyslew.moonstation14.item.ModEquipmentItems.GREY_JUMPSUIT.get());
                 var resolvedPocketItem = com.juicyslew.moonstation14.ms14.equipment.ModEquipmentItems.serverCatalog().get(pocketItemId);
                 require(resolvedPocketItem != null && resolvedPocketItem.protectedFromStepTriggers().orElseThrow().slotFlags()
                                 .contains(com.juicyslew.moonstation14.ms14.equipment.InventoryTemplateData.SlotFlag.FEET),
                         "live resolved pocket item carries the FEET protection mask");
                 putInHand(pocket, "bandana", new ItemStack(com.juicyslew.moonstation14.item.ModEquipmentItems.BLACK_BANDANA.get()));
                 transfer(pocket, "pocket1", "bandana", "bandana", com.juicyslew.moonstation14.item.ModEquipmentItems.BLACK_BANDANA.get());
                 require(!policy(pocket), "FEET mask does not protect bandana legally owned in declared POCKET slot");
                helper.succeed();
            } finally {
                try { com.juicyslew.moonstation14.ms14.prototype.PrototypeRuntime.serverManager()
                        .publishDecoded(com.juicyslew.moonstation14.ms14.equipment.ModEquipmentItems.TYPE, prior); }
                finally { bodies.forEach(Villager::discard); }
            }
        });
    }

    private static boolean policy(Villager body) {
        return com.juicyslew.moonstation14.ms14.equipment.EquipmentStepTriggerProtectionPolicy.protects(body);
    }
    private static Villager body(GameTestHelper helper, BlockPos position, List<Villager> cleanup) {
        Villager body = helper.spawn(EntityType.VILLAGER, position);
        cleanup.add(body);
        if (LiveHands.initialize(body).isEmpty()) throw new GameTestAssertException("bound human inventory initializes");
        return body;
    }
    private static void putInHand(Villager body, String token, ItemStack stack) {
        putOwnedHandFixture(body, "left", token, stack);
    }
    private static void transfer(Villager body, String slotId, String token, String expectedToken, net.minecraft.world.item.Item item) {
        var before = body.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get());
        long revision = before.revision();
        require(BodyEquipmentTransfer.transferOwned(body, "left", slotId, token, revision, true, () -> true)
                == BodyEquipmentTransfer.Result.SUCCESS, "owned transfer succeeds to " + slotId);
        var state = body.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get());
        var slot = LiveHands.equipmentSnapshot(body).orElseThrow().slots().stream()
                .filter(value -> value.id().equals(slotId)).findFirst().orElseThrow();
        require(state.revision() == revision + 1, slotId + " increments revision once");
        require(state.stackCopy("left").isEmpty() && state.token("left").isEmpty(), slotId + " releases source hand");
        require(slot.token().equals(Optional.of(expectedToken)) && slot.stack().orElseThrow().is(item), slotId + " owns expected item and token");
    }
    private static void require(boolean condition, String message) {
        if (!condition) throw new GameTestAssertException(message);
    }
}
