package com.juicyslew.moonstation14.ms14.character.components;

import com.juicyslew.moonstation14.component.codec.json.DamageSpecifierData;
import com.juicyslew.moonstation14.ms14.damage.DamageKeys;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/** Resolved native-style unarmed melee statistics; no weapon item or native MC attack attribute required. */
public record MeleeAttackComponent(DamageSpecifierData damage, float attackRate, float range)
        implements CharacterComponent {
    public static final String TYPE = "MeleeAttack";
    public static final DamageSpecifierData DEFAULT_DAMAGE = new DamageSpecifierData(java.util.Map.of(DamageKeys.BLUNT, 5f));
    public static final float DEFAULT_ATTACK_RATE = 1f;
    public static final float DEFAULT_RANGE = 1.5f;
    private static final Codec<Float> POSITIVE_FLOAT = Codec.FLOAT.comapFlatMap(value ->
            Float.isFinite(value) && value > 0 && value <= 1000 ? DataResult.success(value)
                    : DataResult.error(() -> "must be finite and in (0, 1000]"), value -> value);
    private static final Codec<DamageSpecifierData> DAMAGE_CODEC = DamageSpecifierData.CODEC.comapFlatMap(value ->
            value != null && value.types().values().stream().allMatch(v -> Float.isFinite(v) && v >= 0 && v <= 1_000_000)
                    ? DataResult.success(value) : DataResult.error(() -> "melee damage must be finite and nonnegative"),
            value -> value);
    public static final Codec<MeleeAttackComponent> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.STRING.fieldOf("type").forGetter(MeleeAttackComponent::type),
            DAMAGE_CODEC.fieldOf("damage").forGetter(MeleeAttackComponent::damage),
            POSITIVE_FLOAT.optionalFieldOf("attackRate", DEFAULT_ATTACK_RATE).forGetter(MeleeAttackComponent::attackRate),
            POSITIVE_FLOAT.optionalFieldOf("range", DEFAULT_RANGE).forGetter(MeleeAttackComponent::range)
    ).apply(instance, (type, damage, rate, range) -> {
        if (!TYPE.equals(type)) throw new IllegalArgumentException("unknown component type: " + type);
        return new MeleeAttackComponent(damage, rate, range);
    }));

    public MeleeAttackComponent {
        // DamageSpecifierData normalizes explicit zero entries away. Empty is a valid
        // zero-damage melee profile (e.g. passive cockroach/mothroach host mappings).
        if (damage == null || damage.types().values().stream()
                .anyMatch(value -> !Float.isFinite(value) || value < 0 || value > 1_000_000)
                || !Float.isFinite(attackRate) || attackRate <= 0 || attackRate > 1000
                || !Float.isFinite(range) || range <= 0 || range > 1000)
            throw new IllegalArgumentException("invalid melee attack policy");
    }
    @Override public String type() { return TYPE; }
    public long cooldownTicks() { return Math.max(1L, (long) Math.ceil(20d / attackRate)); }
}
