package com.juicyslew.moonstation14.ms14.equipment;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import net.minecraft.resources.ResourceLocation;
import com.juicyslew.moonstation14.ms14.player_body_control.lifecycle.character.PlayerCharacterBodyShape;

/** Resolved supported Item, Clothing and optional AllowSuitStorage composition. */
public record EquipmentItemData(Item item, Optional<Clothing> clothing,
                                Optional<AllowSuitStorage> allowSuitStorage, List<EquipmentTag> tags) {
    public enum AppearanceRegion { HEAD, TORSO, LEGS, FEET, HANDS, EYES, EARS, NECK, FACE }

    public static final Codec<AppearanceRegion> REGION_CODEC = Codec.STRING.comapFlatMap(value -> {
        try {
            return com.mojang.serialization.DataResult.success(AppearanceRegion.valueOf(value));
        } catch (IllegalArgumentException exception) {
            return com.mojang.serialization.DataResult.error(() -> "unknown appearance region " + value);
        }
    }, AppearanceRegion::name);

    public static final Codec<Item> ITEM_CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.STRING.fieldOf("size").forGetter(Item::size)
    ).apply(instance, Item::new));
    public static final Codec<WornModel> WORN_MODEL_CODEC = RecordCodecBuilder.create(instance -> instance.group(
            ResourceLocation.CODEC.fieldOf("mesh").forGetter(WornModel::mesh),
            Codec.STRING.comapFlatMap(value -> {
                try { return com.mojang.serialization.DataResult.success(PlayerCharacterBodyShape.valueOf(value)); }
                catch (IllegalArgumentException exception) { return com.mojang.serialization.DataResult.error(() -> "unknown body shape " + value); }
            }, PlayerCharacterBodyShape::name).fieldOf("body_shape").forGetter(WornModel::bodyShape)
    ).apply(instance, WornModel::new));
    public static final Codec<Clothing> CLOTHING_CODEC = RecordCodecBuilder.create(instance -> instance.group(
            InventoryTemplateData.SLOT_FLAG_CODEC.listOf().fieldOf("slot_flags").forGetter(Clothing::slotFlags),
            REGION_CODEC.fieldOf("region").forGetter(Clothing::region),
            Codec.INT.fieldOf("color").forGetter(Clothing::color),
            Codec.INT.optionalFieldOf("equip_delay", 0).forGetter(Clothing::equipDelay),
            Codec.STRING.optionalFieldOf("equip_sound").forGetter(Clothing::equipSound),
            Codec.STRING.optionalFieldOf("unequip_sound").forGetter(Clothing::unequipSound)
             , Codec.BOOL.optionalFieldOf("quick_equip", true).forGetter(Clothing::quickEquip),
             WORN_MODEL_CODEC.optionalFieldOf("worn_model").forGetter(Clothing::wornModel)
    ).apply(instance, Clothing::new));
    public static final Codec<AllowSuitStorage> SUIT_STORAGE_CODEC = Codec.BOOL.xmap(
            AllowSuitStorage::new, AllowSuitStorage::enabled);
    public static final Codec<EquipmentItemData> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            ITEM_CODEC.fieldOf("item").forGetter(EquipmentItemData::item),
            CLOTHING_CODEC.optionalFieldOf("clothing").forGetter(EquipmentItemData::clothing),
            SUIT_STORAGE_CODEC.optionalFieldOf("allow_suit_storage").forGetter(EquipmentItemData::allowSuitStorage),
            EquipmentTag.CODEC.listOf().optionalFieldOf("tags", List.of()).forGetter(EquipmentItemData::tags)
    ).apply(instance, EquipmentItemData::new));

    public EquipmentItemData {
        if (item == null || clothing == null || allowSuitStorage == null || tags == null)
            throw new IllegalArgumentException("null equipment composition");
        tags = List.copyOf(tags);
    }

    public record Item(String size) {
        public Item {
            if (!Set.of("Tiny", "Small", "Normal", "Large", "Huge").contains(size))
                throw new IllegalArgumentException("invalid equipment size");
        }
    }

    /** Stable renderer projection: region and packed ARGB tint. */
    public record Clothing(List<InventoryTemplateData.SlotFlag> slotFlags, AppearanceRegion region,
                           int color, int equipDelay, Optional<String> equipSound,
                            Optional<String> unequipSound, boolean quickEquip, Optional<WornModel> wornModel) {
        public Clothing {
            slotFlags = List.copyOf(slotFlags);
            if (slotFlags.isEmpty() || region == null || equipDelay < 0)
                throw new IllegalArgumentException("invalid clothing");
            equipSound = equipSound == null ? Optional.empty() : equipSound;
            unequipSound = unequipSound == null ? Optional.empty() : unequipSound;
            wornModel = wornModel == null ? Optional.empty() : wornModel;
        }
        public Clothing(List<InventoryTemplateData.SlotFlag> slotFlags, AppearanceRegion region, int color, int equipDelay,
                        Optional<String> equipSound, Optional<String> unequipSound, boolean quickEquip) {
            this(slotFlags, region, color, equipDelay, equipSound, unequipSound, quickEquip, Optional.empty());
        }
        public Clothing(List<InventoryTemplateData.SlotFlag> slotFlags, AppearanceRegion region, int color,
                        int equipDelay, Optional<String> equipSound, Optional<String> unequipSound) {
            this(slotFlags, region, color, equipDelay, equipSound, unequipSound, true);
        }
    }

    public record WornModel(ResourceLocation mesh, PlayerCharacterBodyShape bodyShape) {
        public WornModel { if (mesh == null || bodyShape == null) throw new IllegalArgumentException("invalid worn model"); }
    }

    /** Local starter projection is presence-only; upstream supports component whitelist configuration. */
    public record AllowSuitStorage(boolean enabled) {
        public AllowSuitStorage {
            if (!enabled) throw new IllegalArgumentException("AllowSuitStorage must be enabled when composed");
        }
    }
}
