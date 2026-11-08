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

    /** Discover composed grants from worn equipment and directly carried body-owned stacks. */
    public static List<Entry> discover(LivingEntity body, UUID bodyId, CharacterData character) {
        return discover(body, bodyId, character, ModActionPrototypes.serverCatalog());
    }

    /** Active-state seam; pure UUID/data discovery deliberately does not assume ensnare state. */
    public static List<Entry> discover(LivingEntity body, UUID bodyId, CharacterData character,
                                       PrototypeCatalog<ActionPrototype> catalog) {
        return discoverEquipped(body, bodyId, character, catalog);
    }

    public static List<Entry> discover(UUID bodyId, CharacterData character, PrototypeCatalog<ActionPrototype> catalog,
                                       boolean currentlyEnsnared) {
        return discover(bodyId, character, catalog);
    }

    public static List<Entry> discoverActive(UUID bodyId, CharacterData character,
                                             PrototypeCatalog<ActionPrototype> catalog, boolean currentlyEnsnared) {
        return discover(bodyId, character, catalog, currentlyEnsnared);
    }

    private static List<Entry> discoverEquipped(LivingEntity body, UUID bodyId, CharacterData character,
                                                  PrototypeCatalog<ActionPrototype> catalog) {
        List<Entry> result = new ArrayList<>(discover(bodyId, character, catalog));
        if (com.juicyslew.moonstation14.entities.ItemAttachmentQueries.attachedTo(body).stream()
                .anyMatch(item -> item.attachment().filter(a -> a.targetUUID().equals(bodyId)).isPresent()))
            add(result, bodyId.toString(), "moonstation14:remove_attached_item", catalog);
        var snapshot = LiveHands.equipmentSnapshot(body).orElse(null);
        var template = BodyEquipmentTransfer.templateForTransfer(body);
        if (snapshot == null || template == null) return List.copyOf(result);
        var equipment = ModEquipmentItems.catalog(body.level());
        var actionCatalog = ModActionPrototypes.serverCatalog();
        for (var slot : snapshot.slots()) {
            if (slot.token().isEmpty() || slot.stack().isEmpty()) continue;
            var slotData = template.slot(slot.id()).orElse(null);
            if (slotData == null) continue;
            var stack = slot.stack().orElseThrow();
            var data = equipment.get(BuiltInRegistries.ITEM.getKey(stack.getItem()));
            if (data == null) continue;
            boolean worn = !slotData.flags().contains(com.juicyslew.moonstation14.ms14.equipment.InventoryTemplateData.SlotFlag.POCKET);
            addItemGrant(result, "equipment:" + slot.id() + ":" + slot.token().orElseThrow(), data, worn, true, actionCatalog);
        }
        var hands = body.getExistingDataOrNull(com.juicyslew.moonstation14.component.ModDataAttachments.LIVE_HANDS.get());
        if (hands != null && hands.compatible(body)) for (String hand : hands.handIds()) {
            String token = hands.token(hand).orElse(null);
            var stack = hands.stackCopy(hand).orElse(net.minecraft.world.item.ItemStack.EMPTY);
            if (token == null || stack.isEmpty()) continue;
            var data = equipment.get(BuiltInRegistries.ITEM.getKey(stack.getItem()));
            if (data != null) addItemGrant(result, "hand:" + hand + ":" + token, data, false, true, actionCatalog);
        }
        return List.copyOf(result);
    }

    /** Pure provider grant resolver shared by equipment and held-item discovery. */
    static void addItemGrant(List<Entry> result, String providerKey, EquipmentItemData data,
                             boolean worn, boolean carried, PrototypeCatalog<ActionPrototype> catalog) {
        if (data == null || data.supportedActionGrant().isEmpty()) return;
        var grant = data.supportedActionGrant().orElseThrow();
        if (!(worn && grant.itemActionGrant().activeIfWorn()) && !(carried && grant.itemActionGrant().activeIfCarried())) return;
        grant.actions().forEach(id -> add(result, providerKey, id.toString(), catalog));
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
