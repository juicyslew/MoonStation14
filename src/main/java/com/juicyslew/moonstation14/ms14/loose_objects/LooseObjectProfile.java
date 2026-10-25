package com.juicyslew.moonstation14.ms14.loose_objects;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.juicyslew.moonstation14.ms14.prototype.PrototypeRuntime;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.BlockItem;
import com.juicyslew.moonstation14.component.ModDataComponents;
import com.juicyslew.moonstation14.ms14.damage.DamageKeys;
import com.juicyslew.moonstation14.ms14.slip.ItemSlipperyComposition;
import com.juicyslew.moonstation14.ms14.slip.ItemStepTriggerData;
import com.juicyslew.moonstation14.ms14.slip.SlipResponseData;
import java.util.Map;
import java.util.Set;

/** Physical/presentation tuning for a loose object. Values are Minecraft blocks and ticks. */
public record LooseObjectProfile(float mass, float friction, float restitution, float throwSpeed,
                                 float throwSpin, String geometry, String renderKind,
                                 boolean breakOnImpact, float breakThreshold,
                                  float throwRange, float minimumThrowRange, int throwCooldown,
                                      float physicalWidth, float physicalHeight, VisualTuning visual,
                                     ItemDamageComposition itemDamage, ItemStepTriggerData stepTrigger, SlipResponseData slippery,
                                       boolean preventableStepTrigger, StepDamageComposition stepDamage, DamageOnInteractData damageOnInteract, float disarmMalus) {
    public record DamageOnInteractData(Map<String, Float> damage, boolean damageActive, float interactTimerSeconds,
                                       boolean ignoreResistances, ResourceLocation soundEvent, String popupText,
                                       float stunChance, float stunSeconds, boolean throwSource, float throwSpeed) {
        public static final ResourceLocation DEFAULT_SOUND = ResourceLocation.fromNamespaceAndPath("moonstation14", "ss14/effects/lightburn");
        public static final Codec<DamageOnInteractData> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.unboundedMap(Codec.STRING, Codec.FLOAT).fieldOf("damage").forGetter(DamageOnInteractData::damage),
                Codec.BOOL.optionalFieldOf("damage_active", true).forGetter(DamageOnInteractData::damageActive),
                Codec.FLOAT.optionalFieldOf("interact_timer_seconds", 0f).forGetter(DamageOnInteractData::interactTimerSeconds),
                Codec.BOOL.optionalFieldOf("ignore_resistances", false).forGetter(DamageOnInteractData::ignoreResistances),
                ResourceLocation.CODEC.optionalFieldOf("sound_event", DEFAULT_SOUND).forGetter(DamageOnInteractData::soundEvent),
                Codec.STRING.optionalFieldOf("popup_text").forGetter(d -> java.util.Optional.ofNullable(d.popupText)),
                Codec.FLOAT.optionalFieldOf("stun_chance", 0f).forGetter(DamageOnInteractData::stunChance),
                Codec.FLOAT.optionalFieldOf("stun_seconds", 0f).forGetter(DamageOnInteractData::stunSeconds),
                Codec.BOOL.optionalFieldOf("throw_source", false).forGetter(DamageOnInteractData::throwSource),
                Codec.FLOAT.optionalFieldOf("throw_speed", 10f).forGetter(DamageOnInteractData::throwSpeed)
        ).apply(i, (damage, active, timer, ignore, sound, popup, chance, stun, throwing, speed) ->
                new DamageOnInteractData(damage, active, timer, ignore, sound, popup.orElse(null), chance, stun, throwing, speed)));
        public DamageOnInteractData {
            damage = DamageKeys.validateDelta(damage);
            if (!Float.isFinite(interactTimerSeconds) || interactTimerSeconds < 0 || soundEvent == null
                    || !Float.isFinite(stunChance) || stunChance < 0 || stunChance > 1
                    || !Float.isFinite(stunSeconds) || stunSeconds < 0 || !Float.isFinite(throwSpeed) || throwSpeed < 0)
                throw new IllegalArgumentException("invalid damage-on-interact data");
        }
    }
    public record StepDamageComposition(Map<String, Float> damage, boolean deleteSource) {
        public StepDamageComposition { damage = DamageKeys.validateDelta(damage); }
        public static final Codec<StepDamageComposition> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.unboundedMap(Codec.STRING, Codec.FLOAT).fieldOf("damage").forGetter(StepDamageComposition::damage),
                Codec.BOOL.optionalFieldOf("delete_source", false).forGetter(StepDamageComposition::deleteSource)
        ).apply(i, StepDamageComposition::new));
    }
    public record ItemDamageComposition(Set<String> allowedGroups, Set<String> allowedTypes, Map<String, Float> highSpeedImpact,
                                        float minimumImpactSpeed, float impactFactor, int impactCooldownTicks,
                                         Map<String, Float> throwLandDamage, float destructionThreshold,
                                          ResourceLocation destructionSound, boolean spillCurrentReagents,
                                          java.util.List<com.juicyslew.moonstation14.ms14.item_processing.ItemProcessingProfile.ItemOutput> destructionOutputs,
                                           com.juicyslew.moonstation14.ms14.explosion.prototype.ExplosiveSourceData destructionExplosion,
                                            float destructionSoundVolumeDb, com.juicyslew.moonstation14.ms14.damage.DamageModifierData damageModifiers) {
        public static final Codec<ItemDamageComposition> CODEC = RecordCodecBuilder.create(i -> i.group(
                 Codec.STRING.listOf().fieldOf("allowed_groups").forGetter(c -> c.allowedGroups.stream().toList()),
                 Codec.STRING.listOf().optionalFieldOf("allowed_types", java.util.List.of()).forGetter(c -> c.allowedTypes.stream().toList()),
                Codec.unboundedMap(Codec.STRING, Codec.FLOAT).optionalFieldOf("high_speed_impact", Map.of()).forGetter(ItemDamageComposition::highSpeedImpact),
                 Codec.FLOAT.optionalFieldOf("minimum_impact_speed", 20f).forGetter(ItemDamageComposition::minimumImpactSpeed),
                Codec.FLOAT.optionalFieldOf("impact_factor", 0f).forGetter(ItemDamageComposition::impactFactor),
                Codec.INT.optionalFieldOf("impact_cooldown_ticks", 0).forGetter(ItemDamageComposition::impactCooldownTicks),
                Codec.unboundedMap(Codec.STRING, Codec.FLOAT).optionalFieldOf("throw_land_damage", Map.of()).forGetter(ItemDamageComposition::throwLandDamage),
                Codec.FLOAT.optionalFieldOf("destruction_threshold", 0f).forGetter(ItemDamageComposition::destructionThreshold),
                ResourceLocation.CODEC.optionalFieldOf("destruction_sound").forGetter(c -> java.util.Optional.ofNullable(c.destructionSound)),
                Codec.BOOL.optionalFieldOf("spill_current_reagents", false).forGetter(ItemDamageComposition::spillCurrentReagents),
                  com.juicyslew.moonstation14.ms14.item_processing.ItemProcessingProfile.ItemOutput.CODEC.listOf()
                          .optionalFieldOf("destruction_outputs", java.util.List.of()).forGetter(ItemDamageComposition::destructionOutputs),
                 com.juicyslew.moonstation14.ms14.explosion.prototype.ExplosiveSourceData.CODEC.optionalFieldOf("destruction_explosion")
                          .forGetter(c -> java.util.Optional.ofNullable(c.destructionExplosion)),
                   Codec.FLOAT.optionalFieldOf("destruction_sound_volume_db", 0f).forGetter(ItemDamageComposition::destructionSoundVolumeDb),
                   com.juicyslew.moonstation14.ms14.damage.DamageModifierData.CODEC.optionalFieldOf("damage_modifiers", com.juicyslew.moonstation14.ms14.damage.DamageModifierData.EMPTY).forGetter(ItemDamageComposition::damageModifiers)
            ).apply(i, (groups, types, impact, minimum, factor, cooldown, land, threshold, sound, spill, outputs, explosion, volumeDb, modifiers) ->
                    new ItemDamageComposition(Set.copyOf(groups), Set.copyOf(types), impact, minimum, factor, cooldown, land,
                           threshold, sound.orElse(null), spill, outputs, explosion.orElse(null), volumeDb, modifiers)));
        public ItemDamageComposition {
              if (damageModifiers == null) damageModifiers = com.juicyslew.moonstation14.ms14.damage.DamageModifierData.EMPTY;
             allowedGroups = Set.copyOf(allowedGroups);
             allowedTypes = Set.copyOf(allowedTypes);
             allowedTypes.forEach(DamageKeys::requireCanonical);
            destructionOutputs = java.util.List.copyOf(destructionOutputs);
            highSpeedImpact = DamageKeys.validateDelta(highSpeedImpact);
            throwLandDamage = DamageKeys.validateDelta(throwLandDamage);
             if (!DamageKeys.GROUPS.keySet().containsAll(allowedGroups)
                     || !Float.isFinite(minimumImpactSpeed) || minimumImpactSpeed <= 0
                    || !Float.isFinite(impactFactor) || impactFactor < 0 || impactCooldownTicks < 0
                     || !Float.isFinite(destructionThreshold) || destructionThreshold < 0
                     || !Float.isFinite(destructionSoundVolumeDb)
                     || !Float.isFinite((float) Math.pow(10d, destructionSoundVolumeDb / 20d)))
                throw new IllegalArgumentException("invalid item damage composition");
            long outputCount = 0;
            for (var output : destructionOutputs) {
                if (output == null) throw new IllegalArgumentException("invalid destruction output");
                output.validateStructure();
                outputCount += output.count();
            }
            if (outputCount > 65535) throw new IllegalArgumentException("destruction output count exceeds 65535");
        }
        public float destructionSoundGain() { return (float) Math.pow(10d, destructionSoundVolumeDb / 20d); }
        public ItemDamageComposition(Set<String> allowedGroups, Set<String> allowedTypes, Map<String, Float> highSpeedImpact,
                                     float minimumImpactSpeed, float impactFactor, int impactCooldownTicks, Map<String, Float> throwLandDamage,
                                     float destructionThreshold, ResourceLocation destructionSound, boolean spillCurrentReagents,
                                     java.util.List<com.juicyslew.moonstation14.ms14.item_processing.ItemProcessingProfile.ItemOutput> destructionOutputs,
                                     com.juicyslew.moonstation14.ms14.explosion.prototype.ExplosiveSourceData destructionExplosion, float volumeDb) {
            this(allowedGroups, allowedTypes, highSpeedImpact, minimumImpactSpeed, impactFactor, impactCooldownTicks, throwLandDamage,
                    destructionThreshold, destructionSound, spillCurrentReagents, destructionOutputs, destructionExplosion, volumeDb,
                    com.juicyslew.moonstation14.ms14.damage.DamageModifierData.EMPTY);
        }
        public ItemDamageComposition(Set<String> allowedGroups, Set<String> allowedTypes, Map<String, Float> highSpeedImpact,
                                     float minimumImpactSpeed, float impactFactor, int impactCooldownTicks,
                                     Map<String, Float> throwLandDamage, float destructionThreshold, ResourceLocation destructionSound,
                                     boolean spillCurrentReagents, java.util.List<com.juicyslew.moonstation14.ms14.item_processing.ItemProcessingProfile.ItemOutput> destructionOutputs,
                                     com.juicyslew.moonstation14.ms14.explosion.prototype.ExplosiveSourceData destructionExplosion) {
            this(allowedGroups, allowedTypes, highSpeedImpact, minimumImpactSpeed, impactFactor, impactCooldownTicks, throwLandDamage,
                    destructionThreshold, destructionSound, spillCurrentReagents, destructionOutputs, destructionExplosion, 0f);
        }
        public ItemDamageComposition(Set<String> allowedGroups, Map<String, Float> highSpeedImpact,
                                      float minimumImpactSpeed, float impactFactor, int impactCooldownTicks,
                                      Map<String, Float> throwLandDamage, float destructionThreshold,
                                      ResourceLocation destructionSound, boolean spillCurrentReagents,
                                      java.util.List<com.juicyslew.moonstation14.ms14.item_processing.ItemProcessingProfile.ItemOutput> destructionOutputs,
                                      com.juicyslew.moonstation14.ms14.explosion.prototype.ExplosiveSourceData destructionExplosion) {
            this(allowedGroups, Set.of(), highSpeedImpact, minimumImpactSpeed, impactFactor, impactCooldownTicks,
                    throwLandDamage, destructionThreshold, destructionSound, spillCurrentReagents, destructionOutputs, destructionExplosion);
        }
        public ItemDamageComposition(Set<String> allowedGroups, Map<String, Float> highSpeedImpact,
                                     float minimumImpactSpeed, float impactFactor, int impactCooldownTicks,
                                     Map<String, Float> throwLandDamage, float destructionThreshold,
                                     ResourceLocation destructionSound, boolean spillCurrentReagents,
                                     java.util.List<com.juicyslew.moonstation14.ms14.item_processing.ItemProcessingProfile.ItemOutput> destructionOutputs) {
            this(allowedGroups, highSpeedImpact, minimumImpactSpeed, impactFactor, impactCooldownTicks,
                    throwLandDamage, destructionThreshold, destructionSound, spillCurrentReagents, destructionOutputs, null);
        }
    }
    public record VisualTuning(float spriteScaleX, float spriteScaleY, float spriteOffsetX,
                               float spriteOffsetY, float modelScale, float modelFloorOffset) {
        public static final Codec<VisualTuning> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.FLOAT.fieldOf("sprite_scale_x").forGetter(VisualTuning::spriteScaleX),
                Codec.FLOAT.fieldOf("sprite_scale_y").forGetter(VisualTuning::spriteScaleY),
                Codec.FLOAT.fieldOf("sprite_offset_x").forGetter(VisualTuning::spriteOffsetX),
                Codec.FLOAT.fieldOf("sprite_offset_y").forGetter(VisualTuning::spriteOffsetY),
                Codec.FLOAT.fieldOf("model_scale").forGetter(VisualTuning::modelScale),
                Codec.FLOAT.fieldOf("model_floor_offset").forGetter(VisualTuning::modelFloorOffset)
        ).apply(i, VisualTuning::new));
        public VisualTuning {
            if (!bounded(spriteScaleX, 0, 4) || !bounded(spriteScaleY, 0, 4)
                    || !bounded(spriteOffsetX, -2, 2) || !bounded(spriteOffsetY, -2, 2)
                    || !bounded(modelScale, 0, 4) || !Float.isFinite(modelFloorOffset)
                    || modelFloorOffset < 0 || modelFloorOffset > 2)
                throw new IllegalArgumentException("invalid visual tuning");
        }
    }
    private static boolean bounded(float value, float min, float max) {
        return Float.isFinite(value) && value > min && value <= max;
    }
    public float visualWidth() { return visual.spriteScaleX(); }
    public float visualHeight() { return visual.spriteScaleY(); }
    public float modelScale() { return visual.modelScale(); }
    public float modelFloorOffset() { return visual.modelFloorOffset(); }
    // Keep these independent of ModLooseObjectProfiles: its prototype type is
    // constructed with CODEC, so referring back during profile initialization
    // would expose CODEC before it has been assigned.
    public static final ResourceLocation GENERIC_ID = ResourceLocation.fromNamespaceAndPath("moonstation14", "generic");
    public static final ResourceLocation JUG_ID = ResourceLocation.fromNamespaceAndPath("moonstation14", "jug");
    public static final ResourceLocation GRENADE_ID = ResourceLocation.fromNamespaceAndPath("moonstation14", "grenade");
    public static final ResourceLocation GAS_CANISTER_ID = ResourceLocation.fromNamespaceAndPath("moonstation14", "gas_canister");
    public static final ResourceLocation BLOCK_MODEL_ID = ResourceLocation.fromNamespaceAndPath("moonstation14", "block_model");
    public static final ResourceLocation PORTABLE_BODY_KEY_ID = ResourceLocation.fromNamespaceAndPath("moonstation14", "portable_body_key");
    public static final ResourceLocation MOUSE_TINY_ID = ResourceLocation.fromNamespaceAndPath("moonstation14", "mouse_tiny");
    private static final MapCodec<LooseObjectProfile> BASE_MAP_CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            Codec.FLOAT.fieldOf("mass").forGetter(LooseObjectProfile::mass),
            Codec.FLOAT.fieldOf("friction").forGetter(LooseObjectProfile::friction),
            Codec.FLOAT.fieldOf("restitution").forGetter(LooseObjectProfile::restitution),
            Codec.FLOAT.fieldOf("throw_speed").forGetter(LooseObjectProfile::throwSpeed),
            Codec.FLOAT.fieldOf("throw_spin").forGetter(LooseObjectProfile::throwSpin),
            Codec.STRING.fieldOf("geometry").forGetter(LooseObjectProfile::geometry),
            Codec.STRING.fieldOf("render_kind").forGetter(LooseObjectProfile::renderKind),
            Codec.BOOL.fieldOf("break_on_impact").forGetter(LooseObjectProfile::breakOnImpact),
            Codec.FLOAT.fieldOf("break_threshold").forGetter(LooseObjectProfile::breakThreshold),
            Codec.FLOAT.fieldOf("throw_range").forGetter(LooseObjectProfile::throwRange),
            Codec.FLOAT.fieldOf("minimum_throw_range").forGetter(LooseObjectProfile::minimumThrowRange),
            Codec.INT.fieldOf("throw_cooldown").forGetter(LooseObjectProfile::throwCooldown),
            Codec.FLOAT.fieldOf("physical_width").forGetter(LooseObjectProfile::physicalWidth),
            Codec.FLOAT.fieldOf("physical_height").forGetter(LooseObjectProfile::physicalHeight),
            VisualTuning.CODEC.fieldOf("visual").forGetter(LooseObjectProfile::visual)
             , ItemDamageComposition.CODEC.optionalFieldOf("item_damage").forGetter(p -> java.util.Optional.ofNullable(p.itemDamage))
    ).apply(i, (mass, friction, restitution, throwSpeed, throwSpin, geometry, renderKind, breakOnImpact,
                breakThreshold, throwRange, minimumThrowRange, throwCooldown, physicalWidth, physicalHeight,
                  visual, damage) -> new LooseObjectProfile(mass, friction, restitution, throwSpeed, throwSpin,
                     geometry, renderKind, breakOnImpact, breakThreshold, throwRange, minimumThrowRange,
                         throwCooldown, physicalWidth, physicalHeight, visual, damage.orElse(null), null, null, false, null, null, 0f)));

    public static final Codec<LooseObjectProfile> CODEC = RecordCodecBuilder.create(i -> i.group(
            BASE_MAP_CODEC.forGetter(p -> p),
             SlipResponseData.CODEC.codec().optionalFieldOf("slippery")
                     .forGetter(p -> java.util.Optional.ofNullable(p.slippery)),
             ItemStepTriggerData.CODEC.codec().optionalFieldOf("step_trigger")
                     .forGetter(p -> java.util.Optional.ofNullable(p.stepTrigger)),
              Codec.BOOL.optionalFieldOf("preventable_step_trigger", false).forGetter(LooseObjectProfile::preventableStepTrigger),
               StepDamageComposition.CODEC.optionalFieldOf("step_damage").forGetter(p -> java.util.Optional.ofNullable(p.stepDamage)),
                DamageOnInteractData.CODEC.optionalFieldOf("damage_on_interact").forGetter(p -> java.util.Optional.ofNullable(p.damageOnInteract)),
                Codec.FLOAT.optionalFieldOf("disarm_malus", 0f).forGetter(LooseObjectProfile::disarmMalus)
        ).apply(i, (base, slippery, trigger, preventable, stepDamage, contactDamage, malus) -> new LooseObjectProfile(base.mass, base.friction, base.restitution,
            base.throwSpeed, base.throwSpin, base.geometry, base.renderKind, base.breakOnImpact, base.breakThreshold,
            base.throwRange, base.minimumThrowRange, base.throwCooldown, base.physicalWidth, base.physicalHeight,
                base.visual, base.itemDamage, trigger.orElse(null), slippery.orElse(null), preventable, stepDamage.orElse(null), contactDamage.orElse(null), malus)));

    public LooseObjectProfile(float mass, float friction, float restitution, float throwSpeed, float throwSpin,
                              String geometry, String renderKind, boolean breakOnImpact, float breakThreshold,
                              float throwRange, float minimumThrowRange, int throwCooldown, float physicalWidth,
                              float physicalHeight, VisualTuning visual) {
        this(mass, friction, restitution, throwSpeed, throwSpin, geometry, renderKind, breakOnImpact,
                  breakThreshold, throwRange, minimumThrowRange, throwCooldown, physicalWidth, physicalHeight, visual, null, null, null, false, null, null);
    }

    public LooseObjectProfile(float mass, float friction, float restitution, float throwSpeed, float throwSpin,
                              String geometry, String renderKind, boolean breakOnImpact, float breakThreshold,
                              float throwRange, float minimumThrowRange, int throwCooldown, float physicalWidth,
                              float physicalHeight, VisualTuning visual, ItemDamageComposition itemDamage,
                              ItemStepTriggerData stepTrigger, SlipResponseData slippery, boolean preventableStepTrigger,
                              StepDamageComposition stepDamage, DamageOnInteractData damageOnInteract) {
        this(mass, friction, restitution, throwSpeed, throwSpin, geometry, renderKind, breakOnImpact, breakThreshold,
                throwRange, minimumThrowRange, throwCooldown, physicalWidth, physicalHeight, visual, itemDamage,
                stepTrigger, slippery, preventableStepTrigger, stepDamage, damageOnInteract, 0f);
    }

    public LooseObjectProfile(float mass, float friction, float restitution, float throwSpeed, float throwSpin,
                              String geometry, String renderKind, boolean breakOnImpact, float breakThreshold,
                              float throwRange, float minimumThrowRange, int throwCooldown, float physicalWidth,
                              float physicalHeight, VisualTuning visual, ItemDamageComposition itemDamage,
                              ItemStepTriggerData stepTrigger, SlipResponseData slippery, boolean preventableStepTrigger) {
        this(mass, friction, restitution, throwSpeed, throwSpin, geometry, renderKind, breakOnImpact, breakThreshold,
                throwRange, minimumThrowRange, throwCooldown, physicalWidth, physicalHeight, visual, itemDamage,
                 stepTrigger, slippery, preventableStepTrigger, null, null);
    }

    public LooseObjectProfile(float mass, float friction, float restitution, float throwSpeed, float throwSpin,
                              String geometry, String renderKind, boolean breakOnImpact, float breakThreshold,
                              float throwRange, float minimumThrowRange, int throwCooldown, float physicalWidth,
                              float physicalHeight, VisualTuning visual, ItemDamageComposition itemDamage) {
        this(mass, friction, restitution, throwSpeed, throwSpin, geometry, renderKind, breakOnImpact,
                breakThreshold, throwRange, minimumThrowRange, throwCooldown, physicalWidth, physicalHeight,
                  visual, itemDamage, null, null, false, null, null);
    }

    public LooseObjectProfile(float mass, float friction, float restitution, float throwSpeed, float throwSpin,
                              String geometry, String renderKind, boolean breakOnImpact, float breakThreshold,
                              float throwRange, float minimumThrowRange, int throwCooldown, float physicalWidth,
                              float physicalHeight, VisualTuning visual, ItemDamageComposition itemDamage,
                              ItemStepTriggerData stepTrigger, SlipResponseData slippery) {
        this(mass, friction, restitution, throwSpeed, throwSpin, geometry, renderKind, breakOnImpact, breakThreshold,
                throwRange, minimumThrowRange, throwCooldown, physicalWidth, physicalHeight, visual,
                  itemDamage, stepTrigger, slippery, false, null, null);
    }

    public LooseObjectProfile withItemDamage(ItemDamageComposition damage) {
        return new LooseObjectProfile(mass, friction, restitution, throwSpeed, throwSpin, geometry, renderKind,
                 breakOnImpact, breakThreshold, throwRange, minimumThrowRange, throwCooldown, physicalWidth,
                  physicalHeight, visual, damage, stepTrigger, slippery, preventableStepTrigger, stepDamage, damageOnInteract);
    }

    public LooseObjectProfile {
        if (!Float.isFinite(mass) || mass <= 0 || !Float.isFinite(friction) || friction < 0
                || !Float.isFinite(restitution) || restitution < 0 || !Float.isFinite(throwSpeed) || throwSpeed < 0
                || !Float.isFinite(throwSpin) || !Float.isFinite(breakThreshold) || breakThreshold < 0
                || !Float.isFinite(throwRange) || throwRange <= 0 || !Float.isFinite(minimumThrowRange)
                || minimumThrowRange < 0 || minimumThrowRange > throwRange || throwCooldown < 0
                || !Float.isFinite(physicalWidth) || physicalWidth <= 0 || !Float.isFinite(physicalHeight) || physicalHeight <= 0
                || visual == null || !Float.isFinite(disarmMalus))
            throw new IllegalArgumentException("invalid loose object profile values");
    }

    public static LooseObjectProfile resolve(ResourceLocation id) {
        return ModLooseObjectProfiles.require(PrototypeRuntime.serverLooseObjectProfiles(), id);
    }

    public static LooseObjectProfile resolveClient(ResourceLocation id) {
        return ModLooseObjectProfiles.require(PrototypeRuntime.clientLooseObjectProfiles(), id);
    }

    /** Resolves stack classification without changing its authoritative data components. */
    public static LooseObjectProfile resolveForStack(ItemStack stack, boolean client) {
        ResourceLocation id = stack.get(ModDataComponents.LOOSE_OBJECT_PROFILE.get());
        if (id == null && stack.getItem() instanceof BlockItem) id = BLOCK_MODEL_ID;
        if (id == null) id = GENERIC_ID;
        return client ? resolveClient(id) : resolve(id);
    }

    /** The profile's resolved root step trigger is authoritative when present; stack data supplies a trigger only when absent and always overrides the response. As with SS14 EnsureComp, a static response alone does not create a step-trigger component. Null means no combined contact. */
    public static ItemSlipperyComposition resolveSlipperyForStack(ItemStack stack, boolean client) {
        ItemSlipperyComposition override = stack.get(ModDataComponents.ITEM_SLIPPERY_COMPOSITION.get());
        LooseObjectProfile profile = resolveForStack(stack, client);
        ItemStepTriggerData trigger = profile.stepTrigger != null ? profile.stepTrigger : override == null ? null : override.stepTrigger();
        SlipResponseData response = override != null ? override.response() : profile.slippery;
        return trigger == null || response == null ? null : new ItemSlipperyComposition(trigger, response);
    }

    public static ItemStepTriggerData resolveStepTriggerForStack(ItemStack stack, boolean client) {
        ItemStepTriggerData trigger = resolveForStack(stack, client).stepTrigger;
        if (trigger != null) return trigger;
        ItemSlipperyComposition override = stack.get(ModDataComponents.ITEM_SLIPPERY_COMPOSITION.get());
        return override == null ? null : override.stepTrigger();
    }
}
