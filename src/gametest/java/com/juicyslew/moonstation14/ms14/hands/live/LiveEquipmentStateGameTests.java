package com.juicyslew.moonstation14.ms14.hands.live;

import com.juicyslew.moonstation14.MoonStation14;
import com.juicyslew.moonstation14.component.ModDataAttachments;
import com.juicyslew.moonstation14.ms14.hands.ItemToken;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import com.juicyslew.moonstation14.gametest.support.GameTestTags;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.List;

@GameTestTags({"hands", "equipment"})
@PrefixGameTestTemplate(false)
public final class LiveEquipmentStateGameTests {
    private LiveEquipmentStateGameTests() { }

    @GameTest(templateNamespace = "moonstation14", template = "empty", batch = "equipment_state", timeoutTicks = 20)
    @GameTestTags("equipment-regression")
    public static void boundHumanOwnsOrderedEquipmentInSameAttachment(GameTestHelper helper) {
        var human = helper.spawn(EntityType.VILLAGER, new BlockPos(1, 1, 1));
        var pig = helper.spawn(EntityType.PIG, new BlockPos(2, 1, 1));
        helper.runAfterDelay(1, () -> {
            var initialized = LiveHands.initialize(human);
            var slotsResult = LiveHands.equipmentSnapshot(human);
            if (initialized.isEmpty() || slotsResult.isEmpty())
                throw new GameTestAssertException("equipment fixture resolution: " + equipmentResolution(human, initialized, slotsResult));
            LiveHands empty = initialized.orElseThrow();
            var slots = slotsResult.orElseThrow();
            require(slots.persisted() && slots.slots().stream().map(LiveHands.EquipmentSlot::id)
                    .toList().equals(com.juicyslew.moonstation14.ms14.equipment.EquipmentSlots.SLOT_ORDER)
                    && slots.slots().stream().allMatch(slot -> slot.token().isEmpty() && slot.stack().isEmpty()),
                    "human has saved, empty ordered slots");
            require(LiveHands.equipmentSnapshot(pig).isEmpty() && LiveHands.initialize(pig).isEmpty(),
                    "pig has no equipment capability");
            LiveHands held = empty.putWhole(0, "left", new ItemToken("owned"),
                    new ItemStack(Items.DIAMOND, 4)).state();
            human.setData(ModDataAttachments.LIVE_HANDS.get(), held);
            LiveHands equipped = held.equip(1, human, "left", "belt", "owned").orElseThrow();
            require(human.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get()) == held
                    && equipped.revision() == 2 && equipped.stackCopy("left").isEmpty(),
                    "pure transition does not publish partial ownership");
            human.setData(ModDataAttachments.LIVE_HANDS.get(), equipped);
            ItemStack copy = slot(LiveHands.equipmentSnapshot(human).orElseThrow(), "belt").stack().orElseThrow();
            copy.setCount(1);
            require(slot(LiveHands.equipmentSnapshot(human).orElseThrow(), "belt").stack().orElseThrow()
                    .getCount() == 4 && equipped.equip(2, human, "right", "belt", "owned").isEmpty(),
                    "defensive read and occupied denial");
            LiveHands removed = equipped.unequip(2, human, "belt", "right", "owned").orElseThrow();
            require(removed.revision() == 3 && removed.stackCopy("right").orElseThrow().getCount() == 4
                    && equipped.stackCopy("right").isEmpty(), "one revision preserves exact occupant");
            helper.succeed();
        });
    }

    private static LiveHands.EquipmentSlot slot(LiveHands.EquipmentSnapshot snapshot, String id) {
        return snapshot.slots().stream().filter(value -> value.id().equals(id)).findFirst().orElseThrow();
    }

    private static String equipmentResolution(net.minecraft.world.entity.LivingEntity body,
            java.util.Optional<LiveHands> initialized,
            java.util.Optional<LiveHands.EquipmentSnapshot> snapshot) {
        var identity = body.getExistingDataOrNull(ModDataAttachments.CHARACTER_IDENTITY.get());
        var characterId = identity == null || !identity.isBound() ? null : identity.characterId();
        var characters = com.juicyslew.moonstation14.ms14.character.ModCharacters.catalog(body.level());
        var character = com.juicyslew.moonstation14.ms14.character.CharacterIdentitySystem.resolveForActor(body);
        var equipment = character.flatMap(value -> value.component(
                com.juicyslew.moonstation14.ms14.character.components.EquipmentSlotsPrototypeComponent.class));
        var templateId = equipment.flatMap(com.juicyslew.moonstation14.ms14.character.components.EquipmentSlotsPrototypeComponent::template);
        var templates = com.juicyslew.moonstation14.ms14.equipment.ModInventoryTemplates.catalog(body.level());
        var resolvedSlots = com.juicyslew.moonstation14.ms14.hands.HandCapability.resolveEquipmentSlots(body);
        return "host=" + net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getKey(body.getType())
                + ", identityAttachment=" + (identity == null ? "absent" : "present")
                + ", boundCharacterId=" + characterId
                + ", characterCatalogContainsId=" + (characterId != null && characters.get(characterId) != null)
                + ", resolvedCharacter=" + character.map(value -> "present(components=" + value.components() + ")").orElse("empty")
                + ", equipmentSlotsComponent=" + equipment.map(value -> "present(template=" + value.template() + ", slots=" + value.slots() + ")").orElse("empty")
                + ", inventoryTemplateId=" + templateId
                + ", inventoryTemplateCatalogContainsId=" + templateId.map(id -> templates.get(id) != null).orElse(false)
                + ", HandCapability.resolveEquipmentSlots=" + resolvedSlots
                + ", LiveHands.initialize=" + initialized.map(value -> "present(revision=" + value.revision() + ")").orElse("empty")
                + ", equipmentSnapshot=" + snapshot.map(value -> "present(persisted=" + value.persisted() + ", slots=" + value.slots().size() + ")").orElse("empty");
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new GameTestAssertException(message);
    }
}
