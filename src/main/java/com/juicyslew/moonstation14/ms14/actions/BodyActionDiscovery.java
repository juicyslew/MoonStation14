package com.juicyslew.moonstation14.ms14.actions;

import com.juicyslew.moonstation14.component.codec.json.CharacterData;
import com.juicyslew.moonstation14.ms14.actions.prototype.ActionPrototype;
import com.juicyslew.moonstation14.ms14.actions.prototype.ModActionPrototypes;
import com.juicyslew.moonstation14.ms14.character.components.CombatModeComponent;
import com.juicyslew.moonstation14.ms14.character.components.VocalComponent;
import com.juicyslew.moonstation14.ms14.prototype.PrototypeCatalog;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.core.registries.BuiltInRegistries;
import com.juicyslew.moonstation14.ms14.hands.live.LiveHands;
import com.juicyslew.moonstation14.ms14.hands.live.BodyEquipmentTransfer;
import com.juicyslew.moonstation14.ms14.equipment.EquipmentItemData;
import com.juicyslew.moonstation14.ms14.equipment.ModEquipmentItems;

/** Derives body-composition grants on demand; grants are not persisted or cached. */
public final class BodyActionDiscovery {
    private BodyActionDiscovery() { }

    public record Entry(ResourceLocation actionId, String providerKey, ActionPrototype profile) {
        public Entry { Objects.requireNonNull(actionId); Objects.requireNonNull(providerKey); Objects.requireNonNull(profile); }
    }

    /** Discover grants from an authenticated current body and its resolved character data. */
    public static List<Entry> discover(UUID bodyId, CharacterData character) {
        return discover(bodyId, character, ModActionPrototypes.serverCatalog());
    }

    /** Pure resolved-data seam for focused tests and callers holding an already resolved catalog. */
    public static List<Entry> discover(UUID bodyId, CharacterData character,
                                       PrototypeCatalog<ActionPrototype> catalog) {
        Objects.requireNonNull(bodyId);
        Objects.requireNonNull(character);
        Objects.requireNonNull(catalog);
        List<Entry> result = new ArrayList<>(2);
        character.component(CombatModeComponent.class).ifPresent(c -> add(result, bodyId.toString(), c.combatToggleAction(), catalog));
        character.component(VocalComponent.class).ifPresent(c -> add(result, bodyId.toString(), c.screamAction(), catalog));
        return List.copyOf(result);
    }

    /** Discover composed body grants and supported actions from currently worn, body-owned stacks only. */
    public static List<Entry> discover(LivingEntity body, UUID bodyId, CharacterData character) {
        List<Entry> result = new ArrayList<>(discover(bodyId, character));
        var snapshot = LiveHands.equipmentSnapshot(body).orElse(null);
        var template = BodyEquipmentTransfer.templateForTransfer(body);
        if (snapshot == null || template == null) return List.copyOf(result);
        var equipment = ModEquipmentItems.catalog(body.level());
        for (var slot : snapshot.slots()) {
            if (slot.token().isEmpty() || slot.stack().isEmpty()) continue;
            var slotData = template.slot(slot.id()).orElse(null);
            if (slotData == null || slotData.flags().contains(com.juicyslew.moonstation14.ms14.equipment.InventoryTemplateData.SlotFlag.POCKET)) continue;
            var stack = slot.stack().orElseThrow();
            var data = equipment.get(BuiltInRegistries.ITEM.getKey(stack.getItem()));
            if (data == null) continue;
            data.supportedActionGrant().filter(grant -> grant.itemActionGrant().activeIfWorn())
                    .ifPresent(grant -> grant.actions().forEach(id -> add(result, "equipment:" + slot.id() + ":" + slot.token().orElseThrow(), id.toString(), ModActionPrototypes.serverCatalog())));
        }
        return List.copyOf(result);
    }

    private static void add(List<Entry> result, String providerKey, String reference,
                            PrototypeCatalog<ActionPrototype> catalog) {
        if (reference == null || reference.isBlank()) return;
        ResourceLocation id = ResourceLocation.tryParse(reference);
        if (id == null) return;
        ActionPrototype profile = catalog.get(id);
        if (profile != null) result.add(new Entry(id, providerKey, profile));
    }
}
