package com.juicyslew.moonstation14.ms14.combat;

import com.juicyslew.moonstation14.ms14.damage.DamageKeys;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Map;
import net.minecraft.resources.ResourceLocation;

/** Resolved lightweight melee weapon component values. */
public record MeleeWeaponData(Map<String, Float> damage, float attackRate, float range,
                              boolean resistanceBypass, boolean autoAttack,
                               float clickDamageModifier, boolean mustBeEquippedToUse, MeleeSoundData audio,
                               ResourceLocation arc, float animationOffset, float wideAnimationRotation,
                                  float bluntStaminaDamageFactor, boolean resetOnHandSelected, float angleDegrees, ResourceLocation wideArc, boolean altDisarm,
                                  float wideOriginHeightRatio) {
    /** Compatibility constructor for callers using the original complete data shape. */
    public MeleeWeaponData(Map<String, Float> damage, float attackRate, float range, boolean resistanceBypass,
                           boolean autoAttack, float clickDamageModifier, boolean mustBeEquippedToUse,
                           MeleeSoundData audio, ResourceLocation arc, float animationOffset, float wideAnimationRotation,
                           float bluntStaminaDamageFactor, boolean resetOnHandSelected, float angleDegrees,
                           ResourceLocation wideArc, boolean altDisarm) {
        this(damage, attackRate, range, resistanceBypass, autoAttack, clickDamageModifier, mustBeEquippedToUse,
                audio, arc, animationOffset, wideAnimationRotation, bluntStaminaDamageFactor, resetOnHandSelected,
                angleDegrees, wideArc, altDisarm, 0.6f);
    }
    public MeleeWeaponData(Map<String, Float> damage, float attackRate, float range, boolean resistanceBypass,
                           boolean autoAttack, float clickDamageModifier, boolean mustBeEquippedToUse) {
        this(damage, attackRate, range, resistanceBypass, autoAttack, clickDamageModifier, mustBeEquippedToUse, MeleeSoundData.DEFAULT,
                  ResourceLocation.fromNamespaceAndPath("moonstation14", "weapon_arc_thrust"), 1f, 0f, 0.5f, true, 60f, ResourceLocation.fromNamespaceAndPath("moonstation14", "weapon_arc_slash"), true);
    }
    public MeleeWeaponData(Map<String, Float> damage, float attackRate, float range, boolean resistanceBypass,
                           boolean autoAttack, float clickDamageModifier, boolean mustBeEquippedToUse, MeleeSoundData audio) {
        this(damage, attackRate, range, resistanceBypass, autoAttack, clickDamageModifier, mustBeEquippedToUse, audio,
                  ResourceLocation.fromNamespaceAndPath("moonstation14", "weapon_arc_thrust"), 1f, 0f, 0.5f, true, 60f, ResourceLocation.fromNamespaceAndPath("moonstation14", "weapon_arc_slash"), true);
    }
    public MeleeWeaponData(Map<String, Float> damage, float attackRate, float range, boolean resistanceBypass,
                           boolean autoAttack, float clickDamageModifier, boolean mustBeEquippedToUse,
                           MeleeSoundData audio, ResourceLocation arc, float animationOffset, float wideAnimationRotation) {
        this(damage, attackRate, range, resistanceBypass, autoAttack, clickDamageModifier, mustBeEquippedToUse,
                  audio, arc, animationOffset, wideAnimationRotation, 0.5f, true, 60f, ResourceLocation.fromNamespaceAndPath("moonstation14", "weapon_arc_slash"), true);
    }
    public MeleeWeaponData(Map<String, Float> damage, float attackRate, float range, boolean resistanceBypass,
                           boolean autoAttack, float clickDamageModifier, boolean mustBeEquippedToUse,
                           MeleeSoundData audio, ResourceLocation arc, float animationOffset,
                           float wideAnimationRotation, float bluntStaminaDamageFactor) {
        this(damage, attackRate, range, resistanceBypass, autoAttack, clickDamageModifier,
                 mustBeEquippedToUse, audio, arc, animationOffset, wideAnimationRotation, bluntStaminaDamageFactor, true, 60f, ResourceLocation.fromNamespaceAndPath("moonstation14", "weapon_arc_slash"), true);
    }
    public MeleeWeaponData(Map<String, Float> damage, float attackRate, float range, boolean resistanceBypass,
                           boolean autoAttack, float clickDamageModifier, boolean mustBeEquippedToUse,
                           MeleeSoundData audio, ResourceLocation arc, float animationOffset,
                           float wideAnimationRotation, float bluntStaminaDamageFactor, boolean resetOnHandSelected) {
        this(damage, attackRate, range, resistanceBypass, autoAttack, clickDamageModifier, mustBeEquippedToUse,
                audio, arc, animationOffset, wideAnimationRotation, bluntStaminaDamageFactor, resetOnHandSelected,
                60f, ResourceLocation.fromNamespaceAndPath("moonstation14", "weapon_arc_slash"), true);
    }
    private record WideFields(float angleDegrees, ResourceLocation wideArc, boolean altDisarm,
                              float wideOriginHeightRatio) { }

    private static final MapCodec<WideFields> WIDE_FIELDS = RecordCodecBuilder.mapCodec(i -> i.group(
            Codec.FLOAT.optionalFieldOf("angle_degrees", 60f).forGetter(WideFields::angleDegrees),
            ResourceLocation.CODEC.optionalFieldOf("wide_arc", ResourceLocation.fromNamespaceAndPath("moonstation14", "weapon_arc_slash")).forGetter(WideFields::wideArc),
            Codec.BOOL.optionalFieldOf("alt_disarm", true).forGetter(WideFields::altDisarm),
            Codec.FLOAT.validate(value -> Float.isFinite(value) && value >= 0f && value <= 1f
                    ? com.mojang.serialization.DataResult.success(value)
                    : com.mojang.serialization.DataResult.error(() -> "wide origin height ratio must be finite and within 0..1"))
                    .optionalFieldOf("wide_origin_height_ratio", 0.6f).forGetter(WideFields::wideOriginHeightRatio)
    ).apply(i, WideFields::new));

    public static final Codec<MeleeWeaponData> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.unboundedMap(Codec.STRING, Codec.FLOAT).fieldOf("damage").forGetter(MeleeWeaponData::damage),
            Codec.FLOAT.optionalFieldOf("attack_rate", 1f).forGetter(MeleeWeaponData::attackRate),
            Codec.FLOAT.optionalFieldOf("range", 1.5f).forGetter(MeleeWeaponData::range),
            Codec.BOOL.optionalFieldOf("resistance_bypass", false).forGetter(MeleeWeaponData::resistanceBypass),
            Codec.BOOL.optionalFieldOf("auto_attack", false).forGetter(MeleeWeaponData::autoAttack),
            Codec.FLOAT.optionalFieldOf("click_damage_modifier", 1f).forGetter(MeleeWeaponData::clickDamageModifier),
            Codec.BOOL.optionalFieldOf("must_be_equipped_to_use", false).forGetter(MeleeWeaponData::mustBeEquippedToUse),
            MeleeSoundData.CODEC.optionalFieldOf("audio", MeleeSoundData.DEFAULT).forGetter(MeleeWeaponData::audio),
            ResourceLocation.CODEC.optionalFieldOf("arc", ResourceLocation.fromNamespaceAndPath("moonstation14", "weapon_arc_thrust")).forGetter(MeleeWeaponData::arc),
            Codec.FLOAT.optionalFieldOf("animation_offset", 1f).forGetter(MeleeWeaponData::animationOffset),
             Codec.FLOAT.optionalFieldOf("wide_animation_rotation", 0f).forGetter(MeleeWeaponData::wideAnimationRotation),
             Codec.FLOAT.optionalFieldOf("blunt_stamina_damage_factor", 0.5f).forGetter(MeleeWeaponData::bluntStaminaDamageFactor),
              Codec.BOOL.optionalFieldOf("reset_on_hand_selected", true).forGetter(MeleeWeaponData::resetOnHandSelected),
              WIDE_FIELDS.forGetter(data -> new WideFields(data.angleDegrees(), data.wideArc(), data.altDisarm(), data.wideOriginHeightRatio()))
      ).apply(i, (damage, rate, range, bypass, auto, modifier, equipped, audio, arc, offset, rotation, stamina, reset, wide) ->
              new MeleeWeaponData(damage, rate, range, bypass, auto, modifier, equipped, audio, arc, offset, rotation,
                      stamina, reset, wide.angleDegrees(), wide.wideArc(), wide.altDisarm(), wide.wideOriginHeightRatio())));

    public MeleeWeaponData {
        damage = DamageKeys.validateState(damage);
        audio = audio == null ? MeleeSoundData.DEFAULT : audio;
        arc = arc == null ? ResourceLocation.fromNamespaceAndPath("moonstation14", "weapon_arc_thrust") : arc;
        wideArc = wideArc == null ? ResourceLocation.fromNamespaceAndPath("moonstation14", "weapon_arc_slash") : wideArc;
        if (!Float.isFinite(angleDegrees) || angleDegrees < 0 || angleDegrees > 360) throw new IllegalArgumentException("melee angle must be finite and within 0..360");
        if (!Float.isFinite(wideOriginHeightRatio) || wideOriginHeightRatio < 0f || wideOriginHeightRatio > 1f) throw new IllegalArgumentException("wide origin height ratio must be finite and within 0..1");
        if (!Float.isFinite(animationOffset) || !Float.isFinite(wideAnimationRotation)) throw new IllegalArgumentException("melee animation values must be finite");
        if (!Float.isFinite(attackRate) || attackRate <= 0f) throw new IllegalArgumentException("attack rate must be finite and positive");
        if (!Float.isFinite(range) || range <= 0f) throw new IllegalArgumentException("range must be finite and positive");
        if (!Float.isFinite(clickDamageModifier) || clickDamageModifier < 0f) throw new IllegalArgumentException("click damage modifier must be finite and nonnegative");
        if (!Float.isFinite(bluntStaminaDamageFactor) || bluntStaminaDamageFactor < 0f) throw new IllegalArgumentException("blunt stamina damage factor must be finite and nonnegative");
    }
}
