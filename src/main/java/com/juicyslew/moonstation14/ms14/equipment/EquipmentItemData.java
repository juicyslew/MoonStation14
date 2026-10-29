package com.juicyslew.moonstation14.ms14.equipment;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.juicyslew.moonstation14.ms14.damage.DamageKeys;
import com.juicyslew.moonstation14.ms14.damage.DamageModifierData;
import com.juicyslew.moonstation14.ms14.player_body_control.lifecycle.character.PlayerCharacterBodyShape;
import net.minecraft.resources.ResourceLocation;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Fully resolved equipment composition used by the shared worn-equipment owners. */
public record EquipmentItemData(Item item, Optional<Clothing> clothing,
                                Optional<AllowSuitStorage> allowSuitStorage, List<EquipmentTag> tags,
                                Optional<Armor> armor, Optional<SupportedActionGrant> supportedActionGrant,
                                Optional<NoSlip> noSlip,
                                Optional<ProtectedFromStepTriggers> protectedFromStepTriggers,
                                Optional<ContactDamageProtectionData> contactDamageProtection) {
    public enum AppearanceRegion { HEAD, TORSO, LEGS, FEET, HANDS, EYES, EARS, NECK, FACE }

    public static final Codec<AppearanceRegion> REGION_CODEC = Codec.STRING.comapFlatMap(value -> {
        try { return com.mojang.serialization.DataResult.success(AppearanceRegion.valueOf(value)); }
        catch (IllegalArgumentException exception) { return com.mojang.serialization.DataResult.error(() -> "unknown appearance region " + value); }
    }, AppearanceRegion::name);
    public static final Codec<Item> ITEM_CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.STRING.fieldOf("size").forGetter(Item::size)).apply(i, Item::new));
    public static final Codec<WornModel.Frame> WORN_MODEL_FRAME_CODEC = RecordCodecBuilder.create(i -> i.group(
            ResourceLocation.CODEC.fieldOf("mesh").forGetter(WornModel.Frame::mesh),
            Codec.DOUBLE.fieldOf("duration_seconds").forGetter(WornModel.Frame::durationSeconds)
    ).apply(i, WornModel.Frame::new));
    public static final Codec<WornModel> WORN_MODEL_CODEC = RecordCodecBuilder.create(i -> i.group(
            ResourceLocation.CODEC.fieldOf("mesh").forGetter(WornModel::mesh),
            Codec.STRING.comapFlatMap(value -> {
                try { return com.mojang.serialization.DataResult.success(PlayerCharacterBodyShape.valueOf(value)); }
                catch (IllegalArgumentException exception) { return com.mojang.serialization.DataResult.error(() -> "unknown body shape " + value); }
             }, PlayerCharacterBodyShape::name).fieldOf("body_shape").forGetter(WornModel::bodyShape),
            WORN_MODEL_FRAME_CODEC.listOf().optionalFieldOf("frames", List.of()).forGetter(WornModel::frames)
    ).apply(i, WornModel::new));
    public static final Codec<Clothing> CLOTHING_CODEC = RecordCodecBuilder.create(i -> i.group(
            InventoryTemplateData.SLOT_FLAG_CODEC.listOf().fieldOf("slot_flags").forGetter(Clothing::slotFlags),
            REGION_CODEC.fieldOf("region").forGetter(Clothing::region), Codec.INT.fieldOf("color").forGetter(Clothing::color),
            Codec.INT.optionalFieldOf("equip_delay", 0).forGetter(Clothing::equipDelay),
            Codec.STRING.optionalFieldOf("equip_sound").forGetter(Clothing::equipSound),
            Codec.STRING.optionalFieldOf("unequip_sound").forGetter(Clothing::unequipSound),
            Codec.BOOL.optionalFieldOf("quick_equip", true).forGetter(Clothing::quickEquip),
            WORN_MODEL_CODEC.optionalFieldOf("worn_model").forGetter(Clothing::wornModel),
            Codec.DOUBLE.optionalFieldOf("strip_delay", 0.0).forGetter(Clothing::stripDelay),
            InventoryTemplateData.SLOT_FLAG_CODEC.listOf().optionalFieldOf("quick_equip_preferred_slot_flags", List.of()).forGetter(Clothing::quickEquipPreferredSlotFlags)
    ).apply(i, Clothing::new));
    public static final Codec<AllowSuitStorage> SUIT_STORAGE_CODEC = Codec.BOOL.xmap(AllowSuitStorage::new, AllowSuitStorage::enabled);
    private static final Codec<Float> FINITE_NONNEGATIVE_FLOAT = Codec.FLOAT.comapFlatMap(value ->
            Float.isFinite(value) && value >= 0f
                    ? com.mojang.serialization.DataResult.success(value)
                    : com.mojang.serialization.DataResult.error(() -> "expected a finite nonnegative number"),
            value -> value);
    public static final Codec<ArmorModifier> ARMOR_MODIFIER_CODEC = RecordCodecBuilder.create(i -> i.group(
            FINITE_NONNEGATIVE_FLOAT.optionalFieldOf("flat_reduction", 0f).forGetter(ArmorModifier::flatReduction),
            FINITE_NONNEGATIVE_FLOAT.optionalFieldOf("coefficient", 1f).forGetter(ArmorModifier::coefficient)
    ).apply(i, ArmorModifier::new));
    private static final Codec<String> DAMAGE_KEY_CODEC = Codec.STRING.comapFlatMap(value -> {
        try { DamageKeys.requireCanonical(value); return com.mojang.serialization.DataResult.success(value); }
        catch (IllegalArgumentException exception) { return com.mojang.serialization.DataResult.error(exception::getMessage); }
    }, value -> value);
    /** Reads either authored format; canonical modifier entries override coefficient-only entries per key. */
    public static final Codec<Armor> ARMOR_CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.unboundedMap(DAMAGE_KEY_CODEC, ARMOR_MODIFIER_CODEC).optionalFieldOf("modifiers", Map.of()).forGetter(Armor::modifiers),
            Codec.unboundedMap(DAMAGE_KEY_CODEC, FINITE_NONNEGATIVE_FLOAT).optionalFieldOf("coefficients", Map.of()).forGetter(a -> Map.of())
    ).apply(i, Armor::fromAuthored));
    public static final Codec<NoSlip> NO_SLIP_CODEC = Codec.BOOL.comapFlatMap(value -> value
            ? com.mojang.serialization.DataResult.success(new NoSlip())
            : com.mojang.serialization.DataResult.error(() -> "NoSlip is presence-only"), ignored -> true);
    public static final Codec<ProtectedFromStepTriggers> PROTECTED_FROM_STEP_TRIGGERS_CODEC = ProtectedFromStepTriggers.CODEC;
    public static final Codec<ContactDamageProtectionData> CONTACT_DAMAGE_PROTECTION_CODEC = ContactDamageProtectionData.CODEC;
    public static final Codec<ItemActionGrant> ITEM_ACTION_GRANT_CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.BOOL.fieldOf("active_if_worn").forGetter(ItemActionGrant::activeIfWorn)
    ).apply(i, ItemActionGrant::new));
    public static final Codec<SupportedActionGrant> SUPPORTED_ACTION_GRANT_CODEC = RecordCodecBuilder.create(i -> i.group(
            ResourceLocation.CODEC.listOf().fieldOf("actions").forGetter(SupportedActionGrant::actions),
            ITEM_ACTION_GRANT_CODEC.fieldOf("item_action_grant").forGetter(SupportedActionGrant::itemActionGrant)
    ).apply(i, SupportedActionGrant::new));
    public static final Codec<EquipmentItemData> CODEC = RecordCodecBuilder.create(i -> i.group(
            ITEM_CODEC.fieldOf("item").forGetter(EquipmentItemData::item),
            CLOTHING_CODEC.optionalFieldOf("clothing").forGetter(EquipmentItemData::clothing),
            SUIT_STORAGE_CODEC.optionalFieldOf("allow_suit_storage").forGetter(EquipmentItemData::allowSuitStorage),
            EquipmentTag.CODEC.listOf().optionalFieldOf("tags", List.of()).forGetter(EquipmentItemData::tags),
            ARMOR_CODEC.optionalFieldOf("armor").forGetter(EquipmentItemData::armor),
            SUPPORTED_ACTION_GRANT_CODEC.optionalFieldOf("supported_action_grant").forGetter(EquipmentItemData::supportedActionGrant),
            NO_SLIP_CODEC.optionalFieldOf("no_slip").forGetter(EquipmentItemData::noSlip),
            PROTECTED_FROM_STEP_TRIGGERS_CODEC.optionalFieldOf("protected_from_step_triggers").forGetter(EquipmentItemData::protectedFromStepTriggers),
            CONTACT_DAMAGE_PROTECTION_CODEC.optionalFieldOf("damage_on_interact_protection").forGetter(EquipmentItemData::contactDamageProtection)
    ).apply(i, EquipmentItemData::new));

    public EquipmentItemData {
        if (item == null || clothing == null || allowSuitStorage == null || tags == null || armor == null || supportedActionGrant == null
                || noSlip == null || protectedFromStepTriggers == null || contactDamageProtection == null)
            throw new IllegalArgumentException("null equipment composition");
        tags = List.copyOf(tags);
        supportedActionGrant = supportedActionGrant == null ? Optional.empty() : supportedActionGrant;
    }
    public EquipmentItemData(Item item, Optional<Clothing> clothing, Optional<AllowSuitStorage> storage,
                             List<EquipmentTag> tags, Optional<Armor> armor) {
        this(item, clothing, storage, tags, armor, Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty());
    }
    public EquipmentItemData(Item item, Optional<Clothing> clothing, Optional<AllowSuitStorage> storage,
                             List<EquipmentTag> tags, Optional<Armor> armor, Optional<NoSlip> noSlip) {
        this(item, clothing, storage, tags, armor, Optional.empty(), noSlip, Optional.empty(), Optional.empty());
    }
    public EquipmentItemData(Item item, Optional<Clothing> clothing, Optional<AllowSuitStorage> storage,
                             List<EquipmentTag> tags, Optional<Armor> armor, Optional<NoSlip> noSlip,
                             Optional<ProtectedFromStepTriggers> protection) {
        this(item, clothing, storage, tags, armor, Optional.empty(), noSlip, protection, Optional.empty());
    }
    public EquipmentItemData(Item item, Optional<Clothing> clothing, Optional<AllowSuitStorage> storage,
                             List<EquipmentTag> tags, Optional<Armor> armor, Optional<NoSlip> noSlip,
                             Optional<ProtectedFromStepTriggers> protection,
                             Optional<ContactDamageProtectionData> contactDamageProtection) {
        this(item, clothing, storage, tags, armor, Optional.empty(), noSlip, protection, contactDamageProtection);
    }
    public EquipmentItemData(Item item, Optional<Clothing> clothing, Optional<AllowSuitStorage> storage, List<EquipmentTag> tags) {
        this(item, clothing, storage, tags, Optional.empty());
    }

    /** Single canonical armor map; coefficient-only botany data is an adapter into modifiers. */
    public record Armor(Map<String, EquipmentItemData.ArmorModifier> modifiers) {
        public Armor {
            if (modifiers == null) throw new IllegalArgumentException("invalid armor modifiers");
            var copy = new java.util.LinkedHashMap<String, EquipmentItemData.ArmorModifier>();
            modifiers.forEach((key, value) -> {
                DamageKeys.requireCanonical(key);
                if (value == null) throw new IllegalArgumentException("invalid armor modifier");
                copy.put(key, value);
            });
            modifiers = Map.copyOf(copy);
        }
        public static Armor fromCoefficients(Map<String, Float> coefficients) {
            if (coefficients == null) throw new IllegalArgumentException("null armor coefficients");
            var result = new java.util.LinkedHashMap<String, EquipmentItemData.ArmorModifier>();
            coefficients.forEach((key, value) -> result.put(key, new EquipmentItemData.ArmorModifier(0f, value)));
            return new Armor(result);
        }
        private static Armor fromAuthored(Map<String, EquipmentItemData.ArmorModifier> modifiers, Map<String, Float> coefficients) {
            var result = new java.util.LinkedHashMap<String, EquipmentItemData.ArmorModifier>();
            coefficients.forEach((key, value) -> result.put(key, new EquipmentItemData.ArmorModifier(0f, value)));
            result.putAll(modifiers);
            return new Armor(result);
        }
        /** Compatibility projection; full modifiers remain authoritative. */
        public Map<String, Float> coefficients() {
            var result = new java.util.LinkedHashMap<String, Float>();
            modifiers.forEach((key, value) -> result.put(key, value.coefficient()));
            return Map.copyOf(result);
        }
    }
    public record ArmorModifier(float flatReduction, float coefficient) {
        public ArmorModifier {
            if (!Float.isFinite(flatReduction) || flatReduction < 0f || !Float.isFinite(coefficient) || coefficient < 0f)
                throw new IllegalArgumentException("invalid armor modifier values");
        }
    }
    public record Item(String size) {
        public Item { try { if (size == null) throw new IllegalArgumentException(); com.juicyslew.moonstation14.ms14.storage.grid.ItemFootprintProfile.ItemSize.valueOf(size); }
            catch (IllegalArgumentException exception) { throw new IllegalArgumentException("invalid equipment size"); } }
    }
    public record Clothing(List<InventoryTemplateData.SlotFlag> slotFlags, AppearanceRegion region, int color, int equipDelay,
                           Optional<String> equipSound, Optional<String> unequipSound, boolean quickEquip,
                           Optional<WornModel> wornModel, double stripDelay, List<InventoryTemplateData.SlotFlag> quickEquipPreferredSlotFlags) {
        public Clothing {
            slotFlags = List.copyOf(slotFlags); quickEquipPreferredSlotFlags = List.copyOf(quickEquipPreferredSlotFlags);
            if (slotFlags.isEmpty() || region == null || equipDelay < 0 || !Double.isFinite(stripDelay)
                    || new java.util.HashSet<>(quickEquipPreferredSlotFlags).size() != quickEquipPreferredSlotFlags.size()
                    || !slotFlags.containsAll(quickEquipPreferredSlotFlags)) throw new IllegalArgumentException("invalid clothing");
            equipSound = equipSound == null ? Optional.empty() : equipSound; unequipSound = unequipSound == null ? Optional.empty() : unequipSound;
            wornModel = wornModel == null ? Optional.empty() : wornModel;
        }
        public Clothing(List<InventoryTemplateData.SlotFlag> flags, AppearanceRegion region, int color, int delay, Optional<String> equip, Optional<String> unequip, boolean quick, Optional<WornModel> model, double strip) { this(flags, region, color, delay, equip, unequip, quick, model, strip, List.of()); }
        public Clothing(List<InventoryTemplateData.SlotFlag> flags, AppearanceRegion region, int color, int delay, Optional<String> equip, Optional<String> unequip, boolean quick) { this(flags, region, color, delay, equip, unequip, quick, Optional.empty(), 0, List.of()); }
        public Clothing(List<InventoryTemplateData.SlotFlag> flags, AppearanceRegion region, int color, int delay, Optional<String> equip, Optional<String> unequip) { this(flags, region, color, delay, equip, unequip, true); }
        public Clothing(List<InventoryTemplateData.SlotFlag> flags, AppearanceRegion region, int color, int delay, Optional<String> equip, Optional<String> unequip, boolean quick, Optional<WornModel> model) { this(flags, region, color, delay, equip, unequip, quick, model, 0); }
    }
    public record WornModel(ResourceLocation mesh, PlayerCharacterBodyShape bodyShape, List<Frame> frames) {
        public WornModel { if (mesh == null || bodyShape == null || frames == null || (!frames.isEmpty() && !frames.get(0).mesh().equals(mesh))) throw new IllegalArgumentException("invalid worn model"); frames = List.copyOf(frames); }
        public WornModel(ResourceLocation mesh, PlayerCharacterBodyShape bodyShape) { this(mesh, bodyShape, List.of()); }
        public record Frame(ResourceLocation mesh, double durationSeconds) { public Frame { if (mesh == null || !Double.isFinite(durationSeconds) || durationSeconds <= 0) throw new IllegalArgumentException("invalid worn model frame"); } }
    }
    public record AllowSuitStorage(boolean enabled) { public AllowSuitStorage { if (!enabled) throw new IllegalArgumentException("AllowSuitStorage must be enabled when composed"); } }
    public record NoSlip() { }
    public record SupportedActionGrant(List<ResourceLocation> actions, ItemActionGrant itemActionGrant) {
        public SupportedActionGrant {
            if (actions == null || itemActionGrant == null || new java.util.HashSet<>(actions).size() != actions.size()
                    || actions.stream().anyMatch(java.util.Objects::isNull)) throw new IllegalArgumentException("invalid supported action grant");
            actions = List.copyOf(actions);
        }
    }
    public record ItemActionGrant(boolean activeIfWorn) { }
}
