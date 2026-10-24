package com.juicyslew.moonstation14.ms14.equipment;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.juicyslew.moonstation14.ms14.damage.DamageKeys;
import java.util.Map;
import java.util.List;
import java.util.Optional;
import net.minecraft.resources.ResourceLocation;
import com.juicyslew.moonstation14.ms14.player_body_control.lifecycle.character.PlayerCharacterBodyShape;

/** Resolved supported Item, Clothing and optional composition markers. */
public record EquipmentItemData(Item item, Optional<Clothing> clothing,
                                Optional<AllowSuitStorage> allowSuitStorage, List<EquipmentTag> tags,
                                Optional<Armor> armor, Optional<NoSlip> noSlip, Optional<ProtectedFromStepTriggers> protectedFromStepTriggers,
                                Optional<ContactDamageProtectionData> contactDamageProtection) {
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
    public static final Codec<NoSlip> NO_SLIP_CODEC = Codec.BOOL.comapFlatMap(value -> value
            ? com.mojang.serialization.DataResult.success(new NoSlip())
            : com.mojang.serialization.DataResult.error(() -> "NoSlip is presence-only"), ignored -> true);
    public static final Codec<Armor> ARMOR_CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.unboundedMap(Codec.STRING, Codec.FLOAT).fieldOf("coefficients").forGetter(Armor::coefficients)
    ).apply(instance, Armor::new));
    public static final Codec<ProtectedFromStepTriggers> PROTECTED_FROM_STEP_TRIGGERS_CODEC = ProtectedFromStepTriggers.CODEC;
    public static final Codec<ContactDamageProtectionData> CONTACT_DAMAGE_PROTECTION_CODEC = ContactDamageProtectionData.CODEC;
    public static final Codec<EquipmentItemData> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            ITEM_CODEC.fieldOf("item").forGetter(EquipmentItemData::item),
            CLOTHING_CODEC.optionalFieldOf("clothing").forGetter(EquipmentItemData::clothing),
            SUIT_STORAGE_CODEC.optionalFieldOf("allow_suit_storage").forGetter(EquipmentItemData::allowSuitStorage),
            EquipmentTag.CODEC.listOf().optionalFieldOf("tags", List.of()).forGetter(EquipmentItemData::tags),
            ARMOR_CODEC.optionalFieldOf("armor").forGetter(EquipmentItemData::armor),
            NO_SLIP_CODEC.optionalFieldOf("no_slip").forGetter(EquipmentItemData::noSlip),
            PROTECTED_FROM_STEP_TRIGGERS_CODEC.optionalFieldOf("protected_from_step_triggers").forGetter(EquipmentItemData::protectedFromStepTriggers),
            CONTACT_DAMAGE_PROTECTION_CODEC.optionalFieldOf("damage_on_interact_protection").forGetter(EquipmentItemData::contactDamageProtection)
    ).apply(instance, EquipmentItemData::new));

    public EquipmentItemData {
        if (item == null || clothing == null || allowSuitStorage == null || tags == null || armor == null || noSlip == null || protectedFromStepTriggers == null || contactDamageProtection == null)
            throw new IllegalArgumentException("null equipment composition");
        tags = List.copyOf(tags);
    }

    public EquipmentItemData(Item item, Optional<Clothing> clothing, Optional<AllowSuitStorage> allowSuitStorage,
                             List<EquipmentTag> tags, Optional<Armor> armor) {
        this(item, clothing, allowSuitStorage, tags, armor, Optional.empty(), Optional.empty(), Optional.empty());
    }
    public EquipmentItemData(Item item, Optional<Clothing> clothing, Optional<AllowSuitStorage> allowSuitStorage, List<EquipmentTag> tags, Optional<Armor> armor, Optional<NoSlip> noSlip) { this(item, clothing, allowSuitStorage, tags, armor, noSlip, Optional.empty(), Optional.empty()); }
    public EquipmentItemData(Item item, Optional<Clothing> clothing, Optional<AllowSuitStorage> allowSuitStorage, List<EquipmentTag> tags, Optional<Armor> armor, Optional<NoSlip> noSlip, Optional<ProtectedFromStepTriggers> step) { this(item, clothing, allowSuitStorage, tags, armor, noSlip, step, Optional.empty()); }

    public record Armor(Map<String, Float> coefficients) {
        public Armor {
            if (coefficients == null) throw new IllegalArgumentException("null armor coefficients");
            var copy = new java.util.LinkedHashMap<String, Float>();
            coefficients.forEach((key, value) -> {
                DamageKeys.requireCanonical(key);
                if (value == null || !Float.isFinite(value) || value < 0f)
                    throw new IllegalArgumentException("armor coefficients must be finite and nonnegative");
                copy.put(key, value);
            });
            coefficients = Map.copyOf(copy);
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
    /** Upstream NoSlipComponent is an empty networked marker; true-only local presence encoding. */
    public record NoSlip() { }
}
