package com.juicyslew.moonstation14.ms14.equipment;

import com.juicyslew.moonstation14.MoonStation14;
import com.juicyslew.moonstation14.ms14.prototype.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import java.util.Objects;

public final class ModEquipmentItems {
    private ModEquipmentItems() {}
    public static final PrototypeType<EquipmentItemData> TYPE = new PrototypeType<>(ResourceLocation.fromNamespaceAndPath(MoonStation14.MOD_ID, "equipment_item"), "moonstation14/equipment_item", EquipmentItemData.CODEC, new EquipmentItemMergeStrategy());
    public static PrototypeCatalog<EquipmentItemData> serverCatalog() { return PrototypeRuntime.serverEquipmentItems(); }
    public static PrototypeCatalog<EquipmentItemData> clientCatalog() { return PrototypeRuntime.clientEquipmentItems(); }
    public static PrototypeCatalog<EquipmentItemData> catalog(Level level) { Objects.requireNonNull(level); return level.isClientSide() ? clientCatalog() : serverCatalog(); }
    public static EquipmentItemData require(PrototypeCatalog<EquipmentItemData> catalog, ResourceLocation id) { EquipmentItemData data = Objects.requireNonNull(catalog).get(Objects.requireNonNull(id)); if (data == null) throw new IllegalArgumentException("Missing equipment item '" + id + "'"); return data; }
    public static void bootstrapClientBundled() {
        var raw = new java.util.LinkedHashMap<ResourceLocation, com.google.gson.JsonObject>();
        for (String name : java.util.List.of("base_item", "base_clothing", "clothing_uniform_base", "grey_cap", "debug_capability_cap", "poppy", "grey_jumpsuit", "simple_coat", "black_gloves", "black_shoes", "glasses", "red_tie", "black_bandana", "earpieces", "blank_id_card", "utility_case", "bag", "belt", "suit_rad", "hood_rad", "clothing_head_base", "eva_helmet_base", "eva_helmet", "eva_suit_base", "eva_suit", "breathing_mask_base", "breathing_mask_pullable_base", "breathing_mask", "gas_tank_equipment_base", "oxygen_tank", "emergency_oxygen_tank", "ambrosia_deus", "ambrosia_vulgaris", "lily", "clothing_head_hat_watermelon", "clothing_head_hat_holy_watermelon", "trash_banana_peel", "trash_mimana_peel", "carved_pumpkin", "armor_vest", "basic_helmet", "base_gun_small", "base_gun_long", "weapon_pistol_mk58", "weapon_revolver_inspector", "weapon_rifle_lecter", "weapon_shotgun_kammerer", "weapon_sniper_mosin", "weapon_laser_gun", "weapon_energy_shotgun", "weapon_laser_svalinn")) raw.put(ModInventoryTemplates.id(name), read(name));
        PrototypeRuntime.clientManager().reload(TYPE, raw);
    }
    private static com.google.gson.JsonObject read(String name) {
        String path = "/data/moonstation14/moonstation14/equipment_item/" + name + ".json";
        try (var stream = ModEquipmentItems.class.getResourceAsStream(path)) {
            if (stream == null) throw new IllegalStateException("Missing bundled prototype " + path);
            return com.google.gson.JsonParser.parseReader(new java.io.InputStreamReader(stream, java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();
        } catch (java.io.IOException e) { throw new IllegalStateException("Could not read " + path, e); }
    }
}
