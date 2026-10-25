package com.juicyslew.moonstation14.ms14.equipment;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;
import java.util.Optional;
import java.util.Map;
import net.minecraft.resources.ResourceLocation;
import com.juicyslew.moonstation14.ms14.player_body_control.lifecycle.character.PlayerCharacterBodyShape;

/** Resolved supported Item, Clothing and optional AllowSuitStorage composition. */
public record EquipmentItemData(Item item, Optional<Clothing> clothing,
                                Optional<AllowSuitStorage> allowSuitStorage, List<EquipmentTag> tags,
                                Optional<Armor> armor, Optional<SupportedActionGrant> supportedActionGrant) {
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
             WORN_MODEL_CODEC.optionalFieldOf("worn_model").forGetter(Clothing::wornModel),
              Codec.DOUBLE.optionalFieldOf("strip_delay", 0.0).forGetter(Clothing::stripDelay),
              InventoryTemplateData.SLOT_FLAG_CODEC.listOf().optionalFieldOf("quick_equip_preferred_slot_flags", List.of()).forGetter(Clothing::quickEquipPreferredSlotFlags)
    ).apply(instance, Clothing::new));
    public static final Codec<AllowSuitStorage> SUIT_STORAGE_CODEC = Codec.BOOL.xmap(
            AllowSuitStorage::new, AllowSuitStorage::enabled);
    public static final Codec<ArmorModifier> ARMOR_MODIFIER_CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.FLOAT.optionalFieldOf("flat_reduction", 0f).forGetter(ArmorModifier::flatReduction),
            Codec.FLOAT.optionalFieldOf("coefficient", 1f).forGetter(ArmorModifier::coefficient)
    ).apply(instance, ArmorModifier::new));
    public static final Codec<Armor> ARMOR_CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.unboundedMap(Codec.STRING.comapFlatMap(value -> {
                try {
                    com.juicyslew.moonstation14.ms14.damage.DamageKeys.requireCanonical(value);
                    return com.mojang.serialization.DataResult.success(value);
                } catch (IllegalArgumentException exception) {
                    return com.mojang.serialization.DataResult.error(exception::getMessage);
                }
            }, value -> value), ARMOR_MODIFIER_CODEC).fieldOf("modifiers").forGetter(Armor::modifiers)
    ).apply(instance, Armor::new));
    public static final Codec<ItemActionGrant> ITEM_ACTION_GRANT_CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.BOOL.fieldOf("active_if_worn").forGetter(ItemActionGrant::activeIfWorn)
    ).apply(instance, ItemActionGrant::new));
    public static final Codec<SupportedActionGrant> SUPPORTED_ACTION_GRANT_CODEC = RecordCodecBuilder.create(instance -> instance.group(
            ResourceLocation.CODEC.listOf().fieldOf("actions").forGetter(SupportedActionGrant::actions),
            ITEM_ACTION_GRANT_CODEC.fieldOf("item_action_grant").forGetter(SupportedActionGrant::itemActionGrant)
    ).apply(instance, SupportedActionGrant::new));
    public static final Codec<EquipmentItemData> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            ITEM_CODEC.fieldOf("item").forGetter(EquipmentItemData::item),
            CLOTHING_CODEC.optionalFieldOf("clothing").forGetter(EquipmentItemData::clothing),
            SUIT_STORAGE_CODEC.optionalFieldOf("allow_suit_storage").forGetter(EquipmentItemData::allowSuitStorage),
            EquipmentTag.CODEC.listOf().optionalFieldOf("tags", List.of()).forGetter(EquipmentItemData::tags),
            ARMOR_CODEC.optionalFieldOf("armor").forGetter(EquipmentItemData::armor),
            SUPPORTED_ACTION_GRANT_CODEC.optionalFieldOf("supported_action_grant").forGetter(EquipmentItemData::supportedActionGrant)
    ).apply(instance, EquipmentItemData::new));

    public EquipmentItemData {
        if (item == null || clothing == null || allowSuitStorage == null || tags == null || armor == null || supportedActionGrant == null)
            throw new IllegalArgumentException("null equipment composition");
        tags = List.copyOf(tags);
        supportedActionGrant = supportedActionGrant == null ? Optional.empty() : supportedActionGrant;
    }

    public EquipmentItemData(Item item, Optional<Clothing> clothing, Optional<AllowSuitStorage> allowSuitStorage, List<EquipmentTag> tags, Optional<Armor> armor) {
        this(item, clothing, allowSuitStorage, tags, armor, Optional.empty());
    }

    public EquipmentItemData(Item item, Optional<Clothing> clothing,
                             Optional<AllowSuitStorage> allowSuitStorage, List<EquipmentTag> tags) {
        this(item, clothing, allowSuitStorage, tags, Optional.empty());
    }

    public record SupportedActionGrant(List<ResourceLocation> actions, ItemActionGrant itemActionGrant) {
        public SupportedActionGrant {
            if (actions == null || itemActionGrant == null || new java.util.HashSet<>(actions).size() != actions.size()
                    || actions.stream().anyMatch(java.util.Objects::isNull))
                throw new IllegalArgumentException("invalid supported action grant");
            actions = List.copyOf(actions);
        }
    }
    public record ItemActionGrant(boolean activeIfWorn) {}

    /** Generic typed armor modifiers; absent type entries leave damage untouched. */
    public record Armor(Map<String, ArmorModifier> modifiers) {
        public Armor {
            if (modifiers == null) throw new IllegalArgumentException("invalid armor modifiers");
            var copy = new java.util.LinkedHashMap<String, ArmorModifier>();
            modifiers.forEach((key, modifier) -> {
                com.juicyslew.moonstation14.ms14.damage.DamageKeys.requireCanonical(key);
                if (modifier == null) throw new IllegalArgumentException("invalid armor modifier");
                copy.put(key, modifier);
            });
            modifiers = Map.copyOf(copy);
        }
    }
    public record ArmorModifier(float flatReduction, float coefficient) {
        public ArmorModifier {
            if (!Float.isFinite(flatReduction) || flatReduction < 0f
                    || !Float.isFinite(coefficient) || coefficient < 0f || coefficient > 1f)
                throw new IllegalArgumentException("invalid armor modifier values");
        }
    }

    public record Item(String size) {
        public Item {
            try {
                if (size == null) throw new IllegalArgumentException();
                com.juicyslew.moonstation14.ms14.storage.grid.ItemFootprintProfile.ItemSize.valueOf(size);
            } catch (IllegalArgumentException exception) {
                throw new IllegalArgumentException("invalid equipment size");
            }
        }
    }

    /** Stable renderer projection: region and packed ARGB tint. */
    public record Clothing(List<InventoryTemplateData.SlotFlag> slotFlags, AppearanceRegion region,
                           int color, int equipDelay, Optional<String> equipSound,
                            Optional<String> unequipSound, boolean quickEquip, Optional<WornModel> wornModel, double stripDelay,
                            List<InventoryTemplateData.SlotFlag> quickEquipPreferredSlotFlags) {
        public Clothing {
            slotFlags = List.copyOf(slotFlags);
            quickEquipPreferredSlotFlags = List.copyOf(quickEquipPreferredSlotFlags);
            if (slotFlags.isEmpty() || region == null || equipDelay < 0 || !Double.isFinite(stripDelay)
                    || new java.util.HashSet<>(quickEquipPreferredSlotFlags).size() != quickEquipPreferredSlotFlags.size()
                    || !slotFlags.containsAll(quickEquipPreferredSlotFlags))
                throw new IllegalArgumentException("invalid clothing");
            equipSound = equipSound == null ? Optional.empty() : equipSound;
            unequipSound = unequipSound == null ? Optional.empty() : unequipSound;
            wornModel = wornModel == null ? Optional.empty() : wornModel;
        }
        public Clothing(List<InventoryTemplateData.SlotFlag> slotFlags, AppearanceRegion region, int color, int equipDelay,
                        Optional<String> equipSound, Optional<String> unequipSound, boolean quickEquip,
                        Optional<WornModel> wornModel, double stripDelay) {
            this(slotFlags, region, color, equipDelay, equipSound, unequipSound, quickEquip, wornModel, stripDelay, List.of());
        }
        public Clothing(List<InventoryTemplateData.SlotFlag> slotFlags, AppearanceRegion region, int color, int equipDelay,
                        Optional<String> equipSound, Optional<String> unequipSound, boolean quickEquip) {
            this(slotFlags, region, color, equipDelay, equipSound, unequipSound, quickEquip, Optional.empty(), 0.0);
        }
        public Clothing(List<InventoryTemplateData.SlotFlag> slotFlags, AppearanceRegion region, int color,
                        int equipDelay, Optional<String> equipSound, Optional<String> unequipSound) {
            this(slotFlags, region, color, equipDelay, equipSound, unequipSound, true);
        }
        public Clothing(List<InventoryTemplateData.SlotFlag> slotFlags, AppearanceRegion region, int color, int equipDelay, Optional<String> equipSound, Optional<String> unequipSound, boolean quickEquip, Optional<WornModel> wornModel) { this(slotFlags,region,color,equipDelay,equipSound,unequipSound,quickEquip,wornModel,0.0); }
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
