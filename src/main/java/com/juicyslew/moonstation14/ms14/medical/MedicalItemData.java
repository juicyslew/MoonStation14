package com.juicyslew.moonstation14.ms14.medical;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.juicyslew.moonstation14.ms14.damage.DamageKeys;
import net.minecraft.resources.ResourceLocation;

import java.util.Map;
import java.util.Optional;
import java.util.Set;

/** Resolved composition of supported medical item policies. */
public record MedicalItemData(Item item, Optional<Stack> stack, Optional<HealingPolicy> healing,
                              Optional<HealthAnalyzerPolicy> healthAnalyzer,
                              Optional<UiPolicy> ui, Optional<TogglePolicy> toggle,
                              Optional<SyringePolicy> syringePolicy) {
    public static final Codec<Item> ITEM_CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.STRING.fieldOf("size").forGetter(Item::size)
    ).apply(i, Item::new));
    public static final Codec<Stack> STACK_CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.INT.fieldOf("count").forGetter(Stack::count),
            Codec.INT.optionalFieldOf("max_count", 10).forGetter(Stack::maxCount)
    ).apply(i, Stack::new));
    public static final Codec<HealingPolicy> HEALING_CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.unboundedMap(Codec.STRING, Codec.FLOAT).fieldOf("damage").forGetter(HealingPolicy::damage),
            Codec.STRING.listOf().fieldOf("allowed_damage_containers").forGetter(HealingPolicy::allowedDamageContainers),
            Codec.FLOAT.fieldOf("bleed_modifier").forGetter(HealingPolicy::bleedModifier),
            Codec.FLOAT.fieldOf("delay").forGetter(HealingPolicy::delay),
            Codec.FLOAT.fieldOf("self_penalty").forGetter(HealingPolicy::selfPenalty),
            ResourceLocation.CODEC.optionalFieldOf("begin_sound").forGetter(HealingPolicy::beginSound),
            ResourceLocation.CODEC.optionalFieldOf("end_sound").forGetter(HealingPolicy::endSound)
    ).apply(i, HealingPolicy::new));
    public static final Codec<HealthAnalyzerPolicy> ANALYZER_CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.FLOAT.fieldOf("scan_delay").forGetter(HealthAnalyzerPolicy::scanDelay),
            Codec.FLOAT.fieldOf("scan_range").forGetter(HealthAnalyzerPolicy::scanRange),
            Codec.FLOAT.fieldOf("update_interval").forGetter(HealthAnalyzerPolicy::updateInterval),
            ResourceLocation.CODEC.optionalFieldOf("scan_sound").forGetter(HealthAnalyzerPolicy::scanSound)
    ).apply(i, HealthAnalyzerPolicy::new));
    public static final Codec<UiPolicy> UI_CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.FLOAT.fieldOf("range").forGetter(UiPolicy::range)
    ).apply(i, UiPolicy::new));
    public static final Codec<TogglePolicy> TOGGLE_CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.BOOL.fieldOf("on_use").forGetter(TogglePolicy::onUse)
    ).apply(i, TogglePolicy::new));
    public static final Codec<SyringePolicy> SYRINGE_CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.FLOAT.fieldOf("capacity_units").forGetter(SyringePolicy::capacityUnits),
            ResourceLocation.CODEC.fieldOf("initial_mode").forGetter(SyringePolicy::initialMode),
            ResourceLocation.CODEC.listOf().fieldOf("allowed_modes").forGetter(SyringePolicy::allowedModes),
            Codec.FLOAT.fieldOf("default_transfer_units").forGetter(SyringePolicy::defaultTransferUnits),
            Codec.BOOL.fieldOf("need_hand").forGetter(SyringePolicy::needHand),
            Codec.BOOL.fieldOf("break_on_hand_change").forGetter(SyringePolicy::breakOnHandChange),
            Codec.FLOAT.fieldOf("movement_threshold").forGetter(SyringePolicy::movementThreshold),
            Codec.BOOL.fieldOf("ignore_mobs").forGetter(SyringePolicy::ignoreMobs),
            Codec.BOOL.fieldOf("ignore_closed").forGetter(SyringePolicy::ignoreClosed)
    ).apply(i, SyringePolicy::new));
    public static final Codec<MedicalItemData> CODEC = RecordCodecBuilder.create(i -> i.group(
            ITEM_CODEC.fieldOf("item").forGetter(MedicalItemData::item),
            STACK_CODEC.optionalFieldOf("stack").forGetter(MedicalItemData::stack),
            HEALING_CODEC.optionalFieldOf("healing").forGetter(MedicalItemData::healing),
            ANALYZER_CODEC.optionalFieldOf("health_analyzer").forGetter(MedicalItemData::healthAnalyzer),
            UI_CODEC.optionalFieldOf("ui").forGetter(MedicalItemData::ui),
            TOGGLE_CODEC.optionalFieldOf("toggle").forGetter(MedicalItemData::toggle),
            SYRINGE_CODEC.optionalFieldOf("syringe_policy").forGetter(MedicalItemData::syringePolicy)
    ).apply(i, MedicalItemData::new));

    public MedicalItemData {
        if (item == null || stack == null || healing == null || healthAnalyzer == null || ui == null || toggle == null || syringePolicy == null)
            throw new IllegalArgumentException("medical components cannot be null");
        if (healing.isPresent() && (healthAnalyzer.isPresent() || ui.isPresent() || toggle.isPresent() || syringePolicy.isPresent()))
            throw new IllegalArgumentException("healing and analyzer compositions are mutually exclusive");
        if (!healthAnalyzer.isPresent() && (ui.isPresent() || toggle.isPresent()))
            throw new IllegalArgumentException("UI/toggle composition requires HealthAnalyzer");
        if (healthAnalyzer.isPresent() && (ui.isEmpty() || toggle.isEmpty()))
            throw new IllegalArgumentException("HealthAnalyzer requires UI and toggle policies");
        if (syringePolicy.isPresent() && (healthAnalyzer.isPresent() || ui.isPresent() || toggle.isPresent()))
            throw new IllegalArgumentException("Syringe and analyzer compositions are mutually exclusive");
    }
    public record SyringePolicy(float capacityUnits, ResourceLocation initialMode, java.util.List<ResourceLocation> allowedModes,
                                float defaultTransferUnits, boolean needHand, boolean breakOnHandChange, float movementThreshold,
                                boolean ignoreMobs, boolean ignoreClosed) {
        public SyringePolicy {
            allowedModes = java.util.List.copyOf(allowedModes);
            if (!positive(capacityUnits) || initialMode == null || allowedModes.isEmpty() || !allowedModes.contains(initialMode)
                    || !positive(defaultTransferUnits) || defaultTransferUnits > capacityUnits || !positive(movementThreshold))
                throw new IllegalArgumentException("invalid syringe policy");
            if (!needHand || !breakOnHandChange || ignoreMobs || !ignoreClosed)
                throw new IllegalArgumentException("unsupported syringe policy variant; basic injectors require a hand, cancel on hand change, allow mob targets, and ignore closed-container gating");
        }
    }
    public record Item(String size) {
        public Item { if (!Set.of("Small", "Tiny").contains(size)) throw new IllegalArgumentException("unsupported medical item size"); }
    }
    public record Stack(int count, int maxCount) {
        public Stack(int count) { this(count, 10); }
        public Stack { if (maxCount < 1 || maxCount > 99 || count < 1 || count > maxCount) throw new IllegalArgumentException("stack count/max_count out of range"); }
    }
    public record HealingPolicy(Map<String, Float> damage, java.util.List<String> allowedDamageContainers,
                                float bleedModifier, float delay, float selfPenalty,
                                Optional<ResourceLocation> beginSound, Optional<ResourceLocation> endSound) {
        public HealingPolicy {
            damage = Map.copyOf(damage);
            allowedDamageContainers = java.util.List.copyOf(allowedDamageContainers);
            beginSound = beginSound == null ? Optional.empty() : beginSound;
            endSound = endSound == null ? Optional.empty() : endSound;
            if (allowedDamageContainers.isEmpty() || allowedDamageContainers.stream().anyMatch(s -> !s.equals("biological")) || !finite(bleedModifier) || !positive(delay) || !Float.isFinite(selfPenalty) || selfPenalty < 1)
                throw new IllegalArgumentException("invalid healing policy");
            for (var e : damage.entrySet()) if (!DamageKeys.ALL.contains(e.getKey()) || e.getValue() == null || !Float.isFinite(e.getValue()) || e.getValue() >= 0)
                throw new IllegalArgumentException("healing damage must be known negative deltas: " + e.getKey());
        }
    }
    public record HealthAnalyzerPolicy(float scanDelay, float scanRange, float updateInterval, Optional<ResourceLocation> scanSound) {
        public HealthAnalyzerPolicy {
            if (!positive(scanDelay) || !positive(scanRange) || !positive(updateInterval)) throw new IllegalArgumentException("invalid HealthAnalyzer policy");
            scanSound = scanSound == null ? Optional.empty() : scanSound;
        }
        public HealthAnalyzerPolicy(float scanDelay, float scanRange, float updateInterval) { this(scanDelay, scanRange, updateInterval, Optional.empty()); }
    }
    public record UiPolicy(float range) { public UiPolicy { if (!positive(range)) throw new IllegalArgumentException("invalid UI range"); } }
    public record TogglePolicy(boolean onUse) { }
    public MedicalItemData(Item item, Optional<Stack> stack, Optional<HealingPolicy> healing, Optional<HealthAnalyzerPolicy> healthAnalyzer, Optional<UiPolicy> ui, Optional<TogglePolicy> toggle) {
        this(item, stack, healing, healthAnalyzer, ui, toggle, Optional.empty());
    }
    private static boolean positive(float value) { return Float.isFinite(value) && value > 0; }
    private static boolean finite(float value) { return Float.isFinite(value); }
}

