package com.juicyslew.moonstation14.component.codec.json;

import com.juicyslew.moonstation14.component.ModDataAttachments;
import com.juicyslew.moonstation14.component.codec.attachment.DamageData;
import com.juicyslew.moonstation14.effect.EffectContext;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.world.entity.Entity;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public sealed interface EffectData permits EffectData.EvenHealthChange, EffectData.HealthChange, EffectData.Vomit, EffectData.Jitter, EffectData.Drunk {
    String type();
    boolean shouldApply(EffectContext context);
    void apply(EffectContext context);

    default boolean conditionsPass(Entity entity, List<ConditionData> conditions){
        for (ConditionData condition : conditions) {
            if (!condition.test(entity)){
                return false;
            }
        }
        return true;
    }

    // dispatch by "type" field in JSON
    Codec<EffectData> CODEC = Codec.STRING.dispatch(
            "type",              // The JSON field name
            EffectData::type,    // How to get the string from the object (for encoding)
            type -> switch (type) { // How to get the codec from the string (for decoding)
                case "EvenHealthChange" -> EffectData.EvenHealthChange.CODEC;
                case "HealthChange"     -> EffectData.HealthChange.CODEC;
                case "Vomit"            -> EffectData.Vomit.CODEC;
                case "Jitter"           -> EffectData.Jitter.CODEC;
                case "Drunk"            -> EffectData.Drunk.CODEC;
                default -> throw new IllegalStateException("Unknown effect type: " + type);
            }
    );
    // EvenHealthChange
    record EvenHealthChange(List<ConditionData> conditions, Map<String, Float> damage) implements EffectData {
        public static final MapCodec<EvenHealthChange> CODEC = RecordCodecBuilder.mapCodec(inst -> inst.group(
                Codec.list(ConditionData.CODEC).optionalFieldOf("conditions", List.of()).forGetter(EvenHealthChange::conditions),
                Codec.unboundedMap(Codec.STRING, Codec.FLOAT).fieldOf("damage").forGetter(EvenHealthChange::damage)
                // TODO: Codec.simpleMap can use REGISTRIES as the KEYABLE ARGUMENT! So, if I end up registering more things, like damage types and what-not, this would be the way to define what values are allowed.
        ).apply(inst, EvenHealthChange::new));

        @Override public String type() { return "EvenHealthChange"; }
        @Override public boolean shouldApply(EffectContext ctx) { return conditionsPass(ctx.entity(), conditions); }
        private static final Map<String, List<String>> damageGroupToType = Map.of(
                "brute", List.of("blunt", "pierce", "slash"),
                "burn", List.of("heat", "cold", "shock", "caustic"),
                "airloss", List.of("asphyxiation", "bloodloss"),
                "toxin", List.of("poison", "radiation"),
                "genetic", List.of("cellular"),
                "metaphysical", List.of("holy")
        );
        @Override public void apply(EffectContext ctx) {
            DamageData damageContainer = ctx.entity().getData(ModDataAttachments.DAMAGE.get());
            Map<String, Float> damageMap = damageContainer.getMap();
            // Need Better naming convention.
            // damage here is a map to damage groups, NOT damage types.

            // TODO: Check if even is damageable.

            // Get our total damage, or heal if we're below a certain amount.
            // TODO: Use a registry to convert damage groups to types.
            // TODO: Provide helper classes in DamageData ("Entity damage container") to get damage by group.

            // make sure damageChange has the same damage types as damage
            damage.forEach(
                (group_name, amount) ->{
                    List<String> keys = new ArrayList<>(damageGroupToType.get(group_name));
                    var damageChange = new HashMap<String, Float>();
                    for (String key : keys)
                    {
                        damageChange.put(key, 0f);
                    }
                    float remaining = -amount * ctx.scale();
                    while (remaining > 0){
                        int count = keys.size();
                        if (count == 0) break;
                        float maxHeal = remaining / count;
                        for (int i = count-1; i >=0; i--) {
                            String key = keys.get(i);
                            // TODO: Use helper damage function? SpecificAdd?
                            float heal = maxHeal;
                            float current_damage = damageMap.getOrDefault(key,0f);
                            if (heal >= current_damage) {
                                heal = current_damage;
                                keys.remove(i);
                            }
                            if (heal >= remaining) {
                                remaining = 0;
                                damageChange.put(key, damageChange.get(key) - heal);
                                break;
                            }
                            remaining -= heal;
                            damageChange.put(key, damageChange.get(key) - heal);
                        }
                    }
                    damageContainer.mergeAdd(damageChange, 1000f);
                    // TODO: Events. Probably wanna shoot one off when damage is changed. Future concern.
                    // TODO: Add Resistances / Damage Modifiers here.
                }
            );
        }
    }

    // HealthChange with optional conditions and a nested damage object
    record HealthChange(List<ConditionData> conditions, boolean ignoreResistances, DamageSpecifierData damage) implements EffectData {
        public static final MapCodec<HealthChange> CODEC = RecordCodecBuilder.mapCodec(inst -> inst.group(
                Codec.list(ConditionData.CODEC).optionalFieldOf("conditions", List.of()).forGetter(HealthChange::conditions),
                Codec.BOOL.optionalFieldOf("ignoreResistances", true).forGetter(HealthChange::ignoreResistances),
                DamageSpecifierData.CODEC.fieldOf("damage").forGetter(HealthChange::damage)
                // TODO: Handle Damage Groups.
        ).apply(inst, HealthChange::new));

        @Override public String type() { return "HealthChange"; }
        @Override public boolean shouldApply(EffectContext ctx) { return conditionsPass(ctx.entity(), conditions); }
        @Override public void apply(EffectContext ctx) {
            // TODO: implement ignoreResistances
            DamageData damageContainer = ctx.entity().getData(ModDataAttachments.DAMAGE.get());
            float scale = ctx.scale();
            if (scale == 1f){
                // I highly doubt this is saving that much computation time in practice lol.
                damageContainer.mergeAdd(damage.types(), 1000f);
                return;
            }
            HashMap<String, Float> dS = new HashMap(damage.types());
            dS.replaceAll((k, v) -> dS.get(k) * scale);
            damageContainer.mergeAdd(dS, 1000f);
        }
    }

    // Vomit effect with optional conditions and probability
    record Vomit(List<ConditionData> conditions, Float probability) implements EffectData {
        public static final MapCodec<Vomit> CODEC = RecordCodecBuilder.mapCodec(inst -> inst.group(
                Codec.list(ConditionData.CODEC).optionalFieldOf("conditions", List.of()).forGetter(Vomit::conditions),
                Codec.FLOAT.optionalFieldOf("probability", 1.0f).forGetter(Vomit::probability)
        ).apply(inst, Vomit::new));

        @Override public String type() { return "Vomit"; }
        @Override public boolean shouldApply(EffectContext ctx) {
            // It's so unlikely this ever matters, but here we scale probability by scale so that any reagent that has a probability of making vomitting happen will reduce chance if reagent would've been done metabolizing partway through the tick window.
            return Math.random() < (ctx.scale() * probability) && conditionsPass(ctx.entity(), conditions);
        }
        @Override public void apply(EffectContext ctx) {
            // Chance to trigger vomit action based on probability.
        }
    }

    // Jitter with optional conditions
    record Jitter(List<ConditionData> conditions) implements EffectData {
        public static final MapCodec<Jitter> CODEC = RecordCodecBuilder.mapCodec(inst -> inst.group(
                Codec.list(ConditionData.CODEC).optionalFieldOf("conditions", List.of()).forGetter(Jitter::conditions)
        ).apply(inst, Jitter::new));

        @Override public String type() { return "Jitter"; }
        @Override public boolean shouldApply(EffectContext ctx) { return conditionsPass(ctx.entity(), conditions); }
        @Override public void apply(EffectContext ctx) {
            // Add to a set of active effects on the entity.
        }
    }

    // Drunk with no extra fields
    record Drunk(List<ConditionData> conditions) implements EffectData {
        public static final MapCodec<Drunk> CODEC = RecordCodecBuilder.mapCodec(inst -> inst.group(
                Codec.list(ConditionData.CODEC).optionalFieldOf("conditions", List.of()).forGetter(Drunk::conditions)
        ).apply(inst, Drunk::new));

        @Override public String type() { return "Drunk"; }
        @Override public boolean shouldApply(EffectContext ctx) { return conditionsPass(ctx.entity(), conditions); }
        @Override public void apply(EffectContext ctx) {
            // Add to a set of active effects on the entity.
        }
    }
}
