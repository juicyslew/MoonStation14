package com.juicyslew.moonstation14.ms14.combat.throwing;

import com.juicyslew.moonstation14.ms14.combat.MeleeWeaponProfile;
import com.juicyslew.moonstation14.ms14.combat.PositionalSoundCue;
import com.juicyslew.moonstation14.ms14.damage.DamageKeys;
import com.juicyslew.moonstation14.ms14.equipment.InventoryTemplateData;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import com.juicyslew.moonstation14.ms14.prototype.PrototypeCatalog;
import com.juicyslew.moonstation14.component.ModDataComponents;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/** Resolved, independent optional capabilities for item-ID keyed thrown impacts. */
public record ThrownItemProfile(Map<String, Float> damageOnHit, boolean ignoreResistances,
        Optional<EmbeddingData> embedding, Optional<InjectOnEmbedData> injectOnEmbed,
        Optional<EnsnaringData> ensnaring, Optional<ResourceLocation> throwSound, Optional<ResourceLocation> landSound,
        Optional<CollisionSoundData> collisionSound, float landSoundVolumeDb, boolean alignToVelocity) {
    public ThrownItemProfile(Map<String, Float> damageOnHit, boolean ignoreResistances, Optional<EmbeddingData> embedding,
            Optional<InjectOnEmbedData> injectOnEmbed, Optional<EnsnaringData> ensnaring,
            Optional<ResourceLocation> throwSound, Optional<ResourceLocation> landSound) {
        this(damageOnHit, ignoreResistances, embedding, injectOnEmbed, ensnaring, throwSound, landSound, Optional.empty(), 0f, false);
    }
    public ThrownItemProfile(Map<String, Float> damageOnHit, boolean ignoreResistances, Optional<EmbeddingData> embedding,
            Optional<InjectOnEmbedData> injectOnEmbed, Optional<EnsnaringData> ensnaring,
            Optional<ResourceLocation> throwSound, Optional<ResourceLocation> landSound,
            Optional<CollisionSoundData> collisionSound, float landSoundVolumeDb) {
        this(damageOnHit, ignoreResistances, embedding, injectOnEmbed, ensnaring, throwSound, landSound, collisionSound, landSoundVolumeDb, false);
    }
    public static final Codec<ThrownItemProfile> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.unboundedMap(Codec.STRING, Codec.FLOAT).optionalFieldOf("damage_on_hit", Map.of()).forGetter(ThrownItemProfile::damageOnHit),
            Codec.BOOL.optionalFieldOf("ignore_resistances", false).forGetter(ThrownItemProfile::ignoreResistances),
            EmbeddingData.CODEC.optionalFieldOf("embedding").forGetter(ThrownItemProfile::embedding),
            InjectOnEmbedData.CODEC.optionalFieldOf("inject_on_embed").forGetter(ThrownItemProfile::injectOnEmbed),
            EnsnaringData.CODEC.optionalFieldOf("ensnaring").forGetter(ThrownItemProfile::ensnaring),
            ResourceLocation.CODEC.optionalFieldOf("throw_sound").forGetter(ThrownItemProfile::throwSound),
            ResourceLocation.CODEC.optionalFieldOf("land_sound").forGetter(ThrownItemProfile::landSound),
            CollisionSoundData.CODEC.optionalFieldOf("collision_sound").forGetter(ThrownItemProfile::collisionSound),
            Codec.FLOAT.optionalFieldOf("land_sound_volume_db", 0f).forGetter(ThrownItemProfile::landSoundVolumeDb),
            Codec.BOOL.optionalFieldOf("align_to_velocity", false).forGetter(ThrownItemProfile::alignToVelocity)
    ).apply(i, ThrownItemProfile::new));
    public static Optional<ThrownItemProfile> resolveForStack(ItemStack stack, boolean client) {
        return resolveForStack(stack, client ? ModThrownItemProfiles.clientCatalog() : ModThrownItemProfiles.serverCatalog());
    }
    public static Optional<ThrownItemProfile> resolveForStack(ItemStack stack, PrototypeCatalog<ThrownItemProfile> catalog) {
        if (stack == null || stack.isEmpty()) return Optional.empty();
        ResourceLocation id = stack.getItem().builtInRegistryHolder().key().location();
        return Optional.ofNullable(catalog.get(id));
    }
    public ThrownItemProfile {
        if (damageOnHit != null && damageOnHit.values().stream().anyMatch(v -> v == null || !Float.isFinite(v) || v <= 0)) throw new IllegalArgumentException("hit damage must be finite and positive");
        damageOnHit = DamageKeys.validateState(damageOnHit);
        embedding = embedding == null ? Optional.empty() : embedding;
        injectOnEmbed = injectOnEmbed == null ? Optional.empty() : injectOnEmbed;
        ensnaring = ensnaring == null ? Optional.empty() : ensnaring;
        throwSound = throwSound == null ? Optional.empty() : throwSound;
        landSound = landSound == null ? Optional.empty() : landSound;
        collisionSound = collisionSound == null ? Optional.empty() : collisionSound;
        if (!Float.isFinite(landSoundVolumeDb)) throw new IllegalArgumentException("invalid landing sound volume");
    }
    public record CollisionSoundData(PositionalSoundCue cue, float minimumVelocity, float cooldownSeconds) {
        public static final Codec<CollisionSoundData> CODEC = RecordCodecBuilder.create(i -> i.group(
                PositionalSoundCue.codec(0f, 0f).fieldOf("cue").forGetter(CollisionSoundData::cue),
                Codec.FLOAT.optionalFieldOf("minimum_velocity", 3f).forGetter(CollisionSoundData::minimumVelocity),
                Codec.FLOAT.optionalFieldOf("cooldown_seconds", .2f).forGetter(CollisionSoundData::cooldownSeconds)
        ).apply(i, CollisionSoundData::new));
        public CollisionSoundData {
            java.util.Objects.requireNonNull(cue);
            if (!Float.isFinite(minimumVelocity) || minimumVelocity <= 0 || !Float.isFinite(cooldownSeconds) || cooldownSeconds <= 0)
                throw new IllegalArgumentException("invalid collision sound policy");
        }
    }
    public record EmbeddingData(float removeSeconds, boolean embedOnThrow, boolean deleteOnRemove, float offsetX, float offsetY, Optional<ResourceLocation> hitSound, float spriteForwardDegrees, Optional<SpriteTipData> spriteTipUv) {
        public EmbeddingData(float removeSeconds, boolean embedOnThrow, boolean deleteOnRemove, float offsetX, float offsetY, Optional<ResourceLocation> hitSound, float spriteForwardDegrees) { this(removeSeconds,embedOnThrow,deleteOnRemove,offsetX,offsetY,hitSound,spriteForwardDegrees,Optional.empty()); }
        public static final Codec<EmbeddingData> CODEC = RecordCodecBuilder.create(i -> i.group(Codec.FLOAT.optionalFieldOf("remove_seconds",3f).forGetter(EmbeddingData::removeSeconds), Codec.BOOL.optionalFieldOf("embed_on_throw",true).forGetter(EmbeddingData::embedOnThrow), Codec.BOOL.optionalFieldOf("delete_on_remove",false).forGetter(EmbeddingData::deleteOnRemove), Codec.FLOAT.optionalFieldOf("offset_x",0f).forGetter(EmbeddingData::offsetX), Codec.FLOAT.optionalFieldOf("offset_y",0f).forGetter(EmbeddingData::offsetY), ResourceLocation.CODEC.optionalFieldOf("hit_sound").forGetter(EmbeddingData::hitSound), Codec.FLOAT.optionalFieldOf("sprite_forward_degrees",0f).forGetter(EmbeddingData::spriteForwardDegrees), SpriteTipData.CODEC.optionalFieldOf("sprite_tip_uv").forGetter(EmbeddingData::spriteTipUv)).apply(i,EmbeddingData::new));
        public EmbeddingData { if (!Float.isFinite(removeSeconds) || removeSeconds < 0 || !Float.isFinite(offsetX) || !Float.isFinite(offsetY) || !Float.isFinite(spriteForwardDegrees)) throw new IllegalArgumentException("invalid embedding values"); hitSound = hitSound == null ? Optional.empty() : hitSound; spriteTipUv = spriteTipUv == null ? Optional.empty() : spriteTipUv; }
    }
    public record SpriteTipData(float u, float v) {
        static final Codec<SpriteTipData> CODEC = RecordCodecBuilder.create(i -> i.group(Codec.FLOAT.fieldOf("u").forGetter(SpriteTipData::u),Codec.FLOAT.fieldOf("v").forGetter(SpriteTipData::v)).apply(i,SpriteTipData::new));
        public SpriteTipData { if (!Float.isFinite(u)||!Float.isFinite(v)||u<0||u>1||v<0||v>1) throw new IllegalArgumentException("sprite tip UV must be within 0..1"); }
    }
    public record InjectOnEmbedData(MeleeWeaponProfile.MeleeChemicalInjectorData policy, Set<InventoryTemplateData.SlotFlag> blockSlots, float transferEfficiency) {
        public static final Codec<InjectOnEmbedData> CODEC = RecordCodecBuilder.create(i -> i.group(Codec.STRING.optionalFieldOf("solution_name","default").forGetter(InjectOnEmbedData::solutionName), Codec.INT.optionalFieldOf("transfer_cents",100).forGetter(InjectOnEmbedData::transferCents), Codec.BOOL.optionalFieldOf("pierce_armor",true).forGetter(InjectOnEmbedData::pierceArmor), InventoryTemplateData.SLOT_FLAG_CODEC.listOf().optionalFieldOf("block_slots", java.util.List.of()).forGetter(d->d.blockSlots.stream().toList()), Codec.FLOAT.optionalFieldOf("transfer_efficiency",1f).forGetter(InjectOnEmbedData::transferEfficiency)).apply(i,(name,cents,pierce,slots,eff)->new InjectOnEmbedData(new MeleeWeaponProfile.MeleeChemicalInjectorData(name,cents,pierce),Set.copyOf(slots),eff)));
        public String solutionName() { return policy.solutionName(); }
        public int transferCents() { return policy.transferCents(); }
        public boolean pierceArmor() { return policy.pierceArmor(); }
        public InjectOnEmbedData { java.util.Objects.requireNonNull(policy); blockSlots = blockSlots == null ? Set.of() : Set.copyOf(blockSlots); if (!Float.isFinite(transferEfficiency) || transferEfficiency < 0 || transferEfficiency > 1) throw new IllegalArgumentException("invalid transfer efficiency"); }
    }
    public record EnsnaringData(float freeSeconds, float breakoutSeconds, float walkMultiplier, float sprintMultiplier, int maxEnsnares, boolean canThrowTrigger, boolean canMoveBreakout, float staminaDamage, Optional<ResourceLocation> ensnareSound) {
        public static final Codec<EnsnaringData> CODEC = RecordCodecBuilder.create(i -> i.group(Codec.FLOAT.optionalFieldOf("free_seconds",2f).forGetter(EnsnaringData::freeSeconds),Codec.FLOAT.optionalFieldOf("breakout_seconds",3.5f).forGetter(EnsnaringData::breakoutSeconds),Codec.FLOAT.optionalFieldOf("walk_multiplier",.5f).forGetter(EnsnaringData::walkMultiplier),Codec.FLOAT.optionalFieldOf("sprint_multiplier",.5f).forGetter(EnsnaringData::sprintMultiplier),Codec.INT.optionalFieldOf("max_ensnares",1).forGetter(EnsnaringData::maxEnsnares),Codec.BOOL.optionalFieldOf("can_throw_trigger",true).forGetter(EnsnaringData::canThrowTrigger),Codec.BOOL.optionalFieldOf("can_move_breakout",true).forGetter(EnsnaringData::canMoveBreakout),Codec.FLOAT.optionalFieldOf("stamina_damage",0f).forGetter(EnsnaringData::staminaDamage),ResourceLocation.CODEC.optionalFieldOf("ensnare_sound").forGetter(EnsnaringData::ensnareSound)).apply(i,EnsnaringData::new));
        public EnsnaringData(float freeSeconds, float breakoutSeconds, float walkMultiplier, float sprintMultiplier, int maxEnsnares, boolean canThrowTrigger, boolean canMoveBreakout, float staminaDamage) { this(freeSeconds,breakoutSeconds,walkMultiplier,sprintMultiplier,maxEnsnares,canThrowTrigger,canMoveBreakout,staminaDamage,Optional.empty()); }
        public EnsnaringData { if (!Float.isFinite(freeSeconds) || freeSeconds < 0 || !Float.isFinite(breakoutSeconds) || breakoutSeconds < 0 || !Float.isFinite(walkMultiplier) || walkMultiplier < 0 || !Float.isFinite(sprintMultiplier) || sprintMultiplier < 0 || !Float.isFinite(staminaDamage) || staminaDamage < 0 || maxEnsnares < 1) throw new IllegalArgumentException("invalid ensnaring values"); ensnareSound = ensnareSound == null ? Optional.empty() : ensnareSound; }
    }
}
