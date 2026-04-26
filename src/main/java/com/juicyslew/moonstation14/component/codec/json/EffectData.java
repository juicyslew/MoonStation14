package com.juicyslew.moonstation14.component.codec.json;

import com.juicyslew.moonstation14.component.ModDataAttachments;
import com.juicyslew.moonstation14.component.codec.attachment.DamageData;
import com.juicyslew.moonstation14.effect.EffectContext;
import com.juicyslew.moonstation14.ms14.reagent.ModReagents;
import com.juicyslew.moonstation14.util.enums.DamageEnum;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.Entity;
import org.apache.commons.lang3.NotImplementedException;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.juicyslew.moonstation14.util.CodecHelpers.LENIENT_ID_CODEC;
import static java.lang.String.format;

public sealed interface EffectData permits
        EffectData.EvenHealthChange,
        EffectData.HealthChange,
        EffectData.Vomit,
        EffectData.Jitter,
        EffectData.Drunk,
        EffectData.ModifyBleed,
        EffectData.Oxygenate,
        EffectData.ModifyLungGas,
        EffectData.AdjustAlert,
        EffectData.SatiateHunger,
        EffectData.ModifyBloodLevel,
        EffectData.SatiateThirst,
        EffectData.PopupMessage,
        EffectData.Emote,
        EffectData.ModifyStatusEffect,
        EffectData.AdjustReagent,
        EffectData.CureZombieInfection,
        EffectData.ArtifactDurabilityRestore,
        EffectData.ArtifactUnlock,
        EffectData.GenericStatusEffect,
        EffectData.Flammable,
        EffectData.Ignite,
        EffectData.AdjustTemperature,
        EffectData.Extinguish,
        EffectData.MovementSpeedModifier,
        EffectData.CleanBloodstream,
        EffectData.MakeSentient,
        EffectData.Polymorph,
        EffectData.ResetNarcolepsy,
        EffectData.ModifyKnockdown,
        EffectData.Electrocute,
        EffectData.EyeDamage,
        EffectData.ReduceRotting,
        EffectData.CauseZombieInfection
{
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
                case "EvenHealthChange"                         -> EvenHealthChange.CODEC;
                case "HealthChange"                             -> HealthChange.CODEC;
                case "Vomit"                                    -> Vomit.CODEC;
                case "Jitter"                                   -> Jitter.CODEC;
                case "Drunk"                                    -> Drunk.CODEC;
                case "ModifyBleed"                              -> ModifyBleed.CODEC;
                case "Oxygenate"                                -> Oxygenate.CODEC;
                case "ModifyLungGas"                            -> ModifyLungGas.CODEC;
                case "AdjustAlert"                              -> AdjustAlert.CODEC;
                case "SatiateHunger"                            -> SatiateHunger.CODEC;
                case "ModifyBloodLevel"                         -> ModifyBloodLevel.CODEC;
                case "SatiateThirst"                            -> SatiateThirst.CODEC;
                case "PopupMessage"                             -> PopupMessage.CODEC;
                case "Emote"                                    -> Emote.CODEC;
                case "ModifyStatusEffect"                       -> ModifyStatusEffect.CODEC;
                case "AdjustReagent"                            -> AdjustReagent.CODEC;
                case "CureZombieInfection"                      -> CureZombieInfection.CODEC;
                case "ArtifactDurabilityRestore"                -> ArtifactDurabilityRestore.CODEC;
                case "ArtifactUnlock"                           -> ArtifactUnlock.CODEC;
                case "GenericStatusEffect"                      -> GenericStatusEffect.CODEC;
                case "Flammable"                                -> Flammable.CODEC;
                case "Ignite"                                   -> Ignite.CODEC;
                case "AdjustTemperature"                        -> AdjustTemperature.CODEC;
                case "Extinguish"                               -> Extinguish.CODEC;
                case "MovementSpeedModifier"                    -> MovementSpeedModifier.CODEC;
                case "CleanBloodstream"                         -> CleanBloodstream.CODEC;
                case "MakeSentient"                             -> MakeSentient.CODEC;
                case "Polymorph"                                -> Polymorph.CODEC;
                case "ResetNarcolepsy"                          -> ResetNarcolepsy.CODEC;
                case "ModifyKnockdown"                          -> ModifyKnockdown.CODEC;
                case "Electrocute"                              -> Electrocute.CODEC;
                case "EyeDamage"                                -> EyeDamage.CODEC;
                case "ReduceRotting"                            -> ReduceRotting.CODEC;
                case "CauseZombieInfection"                     -> CauseZombieInfection.CODEC;

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
    record Drunk(List<ConditionData> conditions, float boozePower) implements EffectData {
        public static final MapCodec<Drunk> CODEC = RecordCodecBuilder.mapCodec(inst -> inst.group(
                Codec.list(ConditionData.CODEC).optionalFieldOf("conditions", List.of()).forGetter(Drunk::conditions),
                Codec.FLOAT.optionalFieldOf("boozepower", 1f).forGetter(Drunk::boozePower)
        ).apply(inst, Drunk::new));

        @Override public String type() { return "Drunk"; }
        @Override public boolean shouldApply(EffectContext ctx) { return conditionsPass(ctx.entity(), conditions); }
        @Override public void apply(EffectContext ctx) {
            // Add to a set of active effects on the entity.
        }
    }

    record ModifyBleed(List<ConditionData> conditions, float amount) implements EffectData {
        public static final MapCodec<ModifyBleed> CODEC = RecordCodecBuilder.mapCodec(inst -> inst.group(
                Codec.list(ConditionData.CODEC).optionalFieldOf("conditions", List.of()).forGetter(ModifyBleed::conditions),
                Codec.FLOAT.fieldOf("amount").forGetter(ModifyBleed::amount)
        ).apply(inst, ModifyBleed::new));

        @Override public String type() { return "ModifyBleed"; }
        @Override public boolean shouldApply(EffectContext ctx) { return conditionsPass(ctx.entity(), conditions); }
        @Override public void apply(EffectContext ctx) {
            DamageData damageContainer = ctx.entity().getData(ModDataAttachments.DAMAGE.get());
            float scale = ctx.scale();
            damageContainer.specificAdd(DamageEnum.BLOODLOSS.getId(), amount * scale, 1000f);
        }
    }

    record Oxygenate(List<ConditionData> conditions, float factor) implements EffectData {
        public static final MapCodec<Oxygenate> CODEC = RecordCodecBuilder.mapCodec(inst -> inst.group(
                Codec.list(ConditionData.CODEC).optionalFieldOf("conditions", List.of()).forGetter(Oxygenate::conditions),
                Codec.FLOAT.optionalFieldOf("factor", 1f).forGetter(Oxygenate::factor)
        ).apply(inst, Oxygenate::new));

        @Override public String type() { return "ModifyBleed"; }
        @Override public boolean shouldApply(EffectContext ctx) { return conditionsPass(ctx.entity(), conditions); }
        @Override public void apply(EffectContext ctx) {
            // TODO: Implement
            return;
        }
    }

    record ModifyLungGas(List<ConditionData> conditions, Map<ResourceKey<ReagentData>, Float> ratios) implements EffectData {
        public static final MapCodec<ModifyLungGas> CODEC = RecordCodecBuilder.mapCodec(inst -> inst.group(
                Codec.list(ConditionData.CODEC).optionalFieldOf("conditions", List.of()).forGetter(ModifyLungGas::conditions),
                Codec.unboundedMap(LENIENT_ID_CODEC.xmap(
                        rl -> ResourceKey.create(ModReagents.REAGENT_REGISTRY_KEY, rl),
                        ResourceKey::location
                ), Codec.FLOAT).fieldOf("ratios").forGetter(ModifyLungGas::ratios)
        ).apply(inst, ModifyLungGas::new));

        @Override public String type() { return "ModifyLungGas"; }
        @Override public boolean shouldApply(EffectContext ctx) { return conditionsPass(ctx.entity(), conditions); }
        @Override public void apply(EffectContext ctx) {
            // TODO Implement
            return;
        }
    }

    record AdjustAlert(List<ConditionData> conditions, String alertType, float minScale, boolean clear, float time) implements EffectData {
        public static final MapCodec<AdjustAlert> CODEC = RecordCodecBuilder.mapCodec(inst -> inst.group(
                Codec.list(ConditionData.CODEC).optionalFieldOf("conditions", List.of()).forGetter(AdjustAlert::conditions),
                Codec.STRING.fieldOf("alerttype").forGetter(AdjustAlert::alertType), // This might be an enum!
                Codec.FLOAT.fieldOf("minscale").forGetter(AdjustAlert::minScale),
                Codec.BOOL.fieldOf("clear").forGetter(AdjustAlert::clear),
                Codec.FLOAT.fieldOf("time").forGetter(AdjustAlert::time)
        ).apply(inst, AdjustAlert::new));

        @Override public String type() { return "AdjustAlert"; }
        @Override public boolean shouldApply(EffectContext ctx) { return conditionsPass(ctx.entity(), conditions); }
        @Override public void apply(EffectContext ctx) {
            // TODO Implement
            return;
        }
    }

    record SatiateHunger(List<ConditionData> conditions, float factor) implements EffectData {
        public static final MapCodec<SatiateHunger> CODEC = RecordCodecBuilder.mapCodec(inst -> inst.group(
                Codec.list(ConditionData.CODEC).optionalFieldOf("conditions", List.of()).forGetter(SatiateHunger::conditions),
                Codec.FLOAT.optionalFieldOf("factor", 1f).forGetter(SatiateHunger::factor)
        ).apply(inst, SatiateHunger::new));

        @Override public String type() { return "SatiateHunger"; }
        @Override public boolean shouldApply(EffectContext ctx) { return conditionsPass(ctx.entity(), conditions); }
        @Override public void apply(EffectContext ctx) {
            // TODO Implement
            return;
        }
    }

    record ModifyBloodLevel(List<ConditionData> conditions, float amount) implements EffectData {
        public static final MapCodec<ModifyBloodLevel> CODEC = RecordCodecBuilder.mapCodec(inst -> inst.group(
                Codec.list(ConditionData.CODEC).optionalFieldOf("conditions", List.of()).forGetter(ModifyBloodLevel::conditions),
                Codec.FLOAT.fieldOf("amount").forGetter(ModifyBloodLevel::amount)
        ).apply(inst, ModifyBloodLevel::new));

        @Override public String type() { return "ModifyBloodLevel"; }
        @Override public boolean shouldApply(EffectContext ctx) { return conditionsPass(ctx.entity(), conditions); }
        @Override public void apply(EffectContext ctx) {
            DamageData damageContainer = ctx.entity().getData(ModDataAttachments.DAMAGE.get());
            float scale = ctx.scale();
            damageContainer.specificAdd(DamageEnum.BLOODLOSS.getId(), amount * scale, 1000f);
        }
    }

    record SatiateThirst(List<ConditionData> conditions, float factor) implements EffectData {
        public static final MapCodec<SatiateThirst> CODEC = RecordCodecBuilder.mapCodec(inst -> inst.group(
                Codec.list(ConditionData.CODEC).optionalFieldOf("conditions", List.of()).forGetter(SatiateThirst::conditions),
                Codec.FLOAT.optionalFieldOf("factor", 1f).forGetter(SatiateThirst::factor)
        ).apply(inst, SatiateThirst::new));

        @Override public String type() { return "SatiateThirst"; }
        @Override public boolean shouldApply(EffectContext ctx) { return conditionsPass(ctx.entity(), conditions); }
        @Override public void apply(EffectContext ctx) {
            // TODO Implement
            return;
        }
    }

    record PopupMessage(List<ConditionData> conditions, String subType, String visualType, List<String> messages, float probability) implements EffectData {
        public static final MapCodec<PopupMessage> CODEC = RecordCodecBuilder.mapCodec(inst -> inst.group(
                Codec.list(ConditionData.CODEC).optionalFieldOf("conditions", List.of()).forGetter(PopupMessage::conditions),
                Codec.STRING.fieldOf("subtype").forGetter(PopupMessage::subType),
                Codec.STRING.optionalFieldOf("visualtype", "unknown").forGetter(PopupMessage::visualType),
                Codec.list(Codec.STRING).fieldOf("messages").forGetter(PopupMessage::messages),
                Codec.FLOAT.optionalFieldOf("probability", 1f).forGetter(PopupMessage::probability)
        ).apply(inst, PopupMessage::new));

        @Override public String type() { return "PopupMessage"; }
        @Override public boolean shouldApply(EffectContext ctx) { return conditionsPass(ctx.entity(), conditions); }
        @Override public void apply(EffectContext ctx) {
            // TODO Implement
            return;
        }
    }

    record Emote(List<ConditionData> conditions, String emote, boolean showInGuidebook, float probability) implements EffectData {
        public static final MapCodec<Emote> CODEC = RecordCodecBuilder.mapCodec(inst -> inst.group(
                Codec.list(ConditionData.CODEC).optionalFieldOf("conditions", List.of()).forGetter(Emote::conditions),
                Codec.STRING.fieldOf("emote").forGetter(Emote::emote),
                Codec.BOOL.optionalFieldOf("showinguidebook", false).forGetter(Emote::showInGuidebook),
                Codec.FLOAT.optionalFieldOf("probability", 1f).forGetter(Emote::probability)
        ).apply(inst, Emote::new));

        @Override public String type() { return "Emote"; }
        @Override public boolean shouldApply(EffectContext ctx) { return conditionsPass(ctx.entity(), conditions); }
        @Override public void apply(EffectContext ctx) {
            // TODO Implement
            return;
        }
    }

    record ModifyStatusEffect(List<ConditionData> conditions, String effectKey, float time, String subType) implements EffectData {
        public static final MapCodec<ModifyStatusEffect> CODEC = RecordCodecBuilder.mapCodec(inst -> inst.group(
                Codec.list(ConditionData.CODEC).optionalFieldOf("conditions", List.of()).forGetter(ModifyStatusEffect::conditions),
                Codec.STRING.fieldOf("effectproto").forGetter(ModifyStatusEffect::effectKey), // TODO: Make this a registry lol.
                Codec.FLOAT.optionalFieldOf("time", 1f).forGetter(ModifyStatusEffect::time),
                Codec.STRING.optionalFieldOf("subtype", "update").forGetter(ModifyStatusEffect::subType) // TODO: Make this an enum (remove, add, update)
        ).apply(inst, ModifyStatusEffect::new));

        @Override public String type() { return "ModifyStatusEffect"; }
        @Override public boolean shouldApply(EffectContext ctx) { return conditionsPass(ctx.entity(), conditions); }
        @Override public void apply(EffectContext ctx) {
            // TODO Implement
            return;
        }
    }

    record AdjustReagent(List<ConditionData> conditions, ResourceKey<ReagentData> reagent, float amount) implements EffectData {
        public static final MapCodec<AdjustReagent> CODEC = RecordCodecBuilder.mapCodec(inst -> inst.group(
                Codec.list(ConditionData.CODEC).optionalFieldOf("conditions", List.of()).forGetter(AdjustReagent::conditions),
                LENIENT_ID_CODEC.xmap(
                        rl -> ResourceKey.create(ModReagents.REAGENT_REGISTRY_KEY, rl),
                        ResourceKey::location
                ).fieldOf("reagent").forGetter(AdjustReagent::reagent),
                Codec.FLOAT.fieldOf("amount").forGetter(AdjustReagent::amount)
        ).apply(inst, AdjustReagent::new));

        @Override public String type() { return "AdjustReagent"; }
        @Override public boolean shouldApply(EffectContext ctx) { return conditionsPass(ctx.entity(), conditions); }
        @Override public void apply(EffectContext ctx) {
            // TODO Implement
            return;
        }
    }

    record CureZombieInfection(List<ConditionData> conditions) implements EffectData {
        public static final MapCodec<CureZombieInfection> CODEC = RecordCodecBuilder.mapCodec(inst -> inst.group(
                Codec.list(ConditionData.CODEC).optionalFieldOf("conditions", List.of()).forGetter(CureZombieInfection::conditions)
        ).apply(inst, CureZombieInfection::new));

        @Override public String type() { return "CureZombieInfection"; }
        @Override public boolean shouldApply(EffectContext ctx) { return conditionsPass(ctx.entity(), conditions); }
        @Override public void apply(EffectContext ctx) {
            // TODO Implement
            return;
        }
    }

    record ArtifactDurabilityRestore(List<ConditionData> conditions, float minScale) implements EffectData {
        public static final MapCodec<ArtifactDurabilityRestore> CODEC = RecordCodecBuilder.mapCodec(inst -> inst.group(
                Codec.list(ConditionData.CODEC).optionalFieldOf("conditions", List.of()).forGetter(ArtifactDurabilityRestore::conditions),
                Codec.FLOAT.fieldOf("minscale").forGetter(ArtifactDurabilityRestore::minScale)
        ).apply(inst, ArtifactDurabilityRestore::new));

        @Override public String type() { return "ArtifactDurabilityRestore"; }
        @Override public boolean shouldApply(EffectContext ctx) { return conditionsPass(ctx.entity(), conditions); }
        @Override public void apply(EffectContext ctx) {
            // TODO Implement
            return;
        }
    }

    record ArtifactUnlock(List<ConditionData> conditions, float minScale) implements EffectData {
        public static final MapCodec<ArtifactUnlock> CODEC = RecordCodecBuilder.mapCodec(inst -> inst.group(
                Codec.list(ConditionData.CODEC).optionalFieldOf("conditions", List.of()).forGetter(ArtifactUnlock::conditions),
                Codec.FLOAT.fieldOf("minscale").forGetter(ArtifactUnlock::minScale)
        ).apply(inst, ArtifactUnlock::new));

        @Override public String type() { return "ArtifactUnlock"; }
        @Override public boolean shouldApply(EffectContext ctx) { return conditionsPass(ctx.entity(), conditions); }
        @Override public void apply(EffectContext ctx) {
            // TODO Implement
            return;
        }
    }

    record GenericStatusEffect(List<ConditionData> conditions, String effectKey, String component, String subType, float time) implements EffectData {
        public static final MapCodec<GenericStatusEffect> CODEC = RecordCodecBuilder.mapCodec(inst -> inst.group(
                Codec.list(ConditionData.CODEC).optionalFieldOf("conditions", List.of()).forGetter(GenericStatusEffect::conditions),
                Codec.STRING.fieldOf("key").forGetter(GenericStatusEffect::effectKey), // TODO: Make this a registry lol.
                Codec.STRING.optionalFieldOf("component", "None").forGetter(GenericStatusEffect::component), // TODO: Make this a registry lol.
                Codec.STRING.optionalFieldOf("subtype", "update").forGetter(GenericStatusEffect::subType), // TODO: Make this an enum (remove, add, update)
                Codec.FLOAT.optionalFieldOf("time", 1f).forGetter(GenericStatusEffect::time)
        ).apply(inst, GenericStatusEffect::new));

        @Override public String type() { return "GenericStatusEffect"; }
        @Override public boolean shouldApply(EffectContext ctx) { return conditionsPass(ctx.entity(), conditions); }
        @Override public void apply(EffectContext ctx) {
            // TODO Implement
            return;
        }
    }

    record Flammable(List<ConditionData> conditions, float multiplier) implements EffectData {
        public static final MapCodec<Flammable> CODEC = RecordCodecBuilder.mapCodec(inst -> inst.group(
                Codec.list(ConditionData.CODEC).optionalFieldOf("conditions", List.of()).forGetter(Flammable::conditions),
                Codec.FLOAT.optionalFieldOf("multiplier", 1f).forGetter(Flammable::multiplier)
        ).apply(inst, Flammable::new));

        @Override public String type() { return "Flammable"; }
        @Override public boolean shouldApply(EffectContext ctx) { return conditionsPass(ctx.entity(), conditions); }
        @Override public void apply(EffectContext ctx) {
            // TODO Implement
            return;
        }
    }

    record Ignite(List<ConditionData> conditions) implements EffectData {
        public static final MapCodec<Ignite> CODEC = RecordCodecBuilder.mapCodec(inst -> inst.group(
                Codec.list(ConditionData.CODEC).optionalFieldOf("conditions", List.of()).forGetter(Ignite::conditions)
        ).apply(inst, Ignite::new));

        @Override public String type() { return "Ignite"; }
        @Override public boolean shouldApply(EffectContext ctx) { return conditionsPass(ctx.entity(), conditions); }
        @Override public void apply(EffectContext ctx) {
            // TODO Implement
            return;
        }
    }

    record AdjustTemperature(List<ConditionData> conditions, float amount) implements EffectData {
        public static final MapCodec<AdjustTemperature> CODEC = RecordCodecBuilder.mapCodec(inst -> inst.group(
                Codec.list(ConditionData.CODEC).optionalFieldOf("conditions", List.of()).forGetter(AdjustTemperature::conditions),
                Codec.FLOAT.fieldOf("amount").forGetter(AdjustTemperature::amount)
        ).apply(inst, AdjustTemperature::new));

        @Override public String type() { return "AdjustTemperature"; }
        @Override public boolean shouldApply(EffectContext ctx) { return conditionsPass(ctx.entity(), conditions); }
        @Override public void apply(EffectContext ctx) {
            // TODO Implement
            return;
        }
    }

    record Extinguish(List<ConditionData> conditions) implements EffectData {
        public static final MapCodec<Extinguish> CODEC = RecordCodecBuilder.mapCodec(inst -> inst.group(
                Codec.list(ConditionData.CODEC).optionalFieldOf("conditions", List.of()).forGetter(Extinguish::conditions)
        ).apply(inst, Extinguish::new));

        @Override public String type() { return "Extinguish"; }
        @Override public boolean shouldApply(EffectContext ctx) { return conditionsPass(ctx.entity(), conditions); }
        @Override public void apply(EffectContext ctx) {
            // TODO Implement
            return;
        }
    }

    record MovementSpeedModifier(List<ConditionData> conditions, float minScale, float walkSpeedModifier, float sprintSpeedModifier, float time) implements EffectData {
        public static final MapCodec<MovementSpeedModifier> CODEC = RecordCodecBuilder.mapCodec(inst -> inst.group(
                Codec.list(ConditionData.CODEC).optionalFieldOf("conditions", List.of()).forGetter(MovementSpeedModifier::conditions),
                Codec.FLOAT.optionalFieldOf("minscale", 0f).forGetter(MovementSpeedModifier::minScale),
                Codec.FLOAT.fieldOf("walkspeedmodifier").forGetter(MovementSpeedModifier::walkSpeedModifier),
                Codec.FLOAT.fieldOf("sprintspeedmodifier").forGetter(MovementSpeedModifier::sprintSpeedModifier),
                Codec.FLOAT.optionalFieldOf("time", 5f).forGetter(MovementSpeedModifier::time)
        ).apply(inst, MovementSpeedModifier::new));

        @Override public String type() { return "MovementSpeedModifier"; }
        @Override public boolean shouldApply(EffectContext ctx) { return conditionsPass(ctx.entity(), conditions); }
        @Override public void apply(EffectContext ctx) {
            // TODO Implement
            return;
        }
    }

    record CleanBloodstream(List<ConditionData> conditions, ResourceKey<ReagentData> excluded, float cleanseRate) implements EffectData {
        public static final MapCodec<CleanBloodstream> CODEC = RecordCodecBuilder.mapCodec(inst -> inst.group(
                Codec.list(ConditionData.CODEC).optionalFieldOf("conditions", List.of()).forGetter(CleanBloodstream::conditions),
                LENIENT_ID_CODEC.xmap(
                        rl -> ResourceKey.create(ModReagents.REAGENT_REGISTRY_KEY, rl),
                        ResourceKey::location
                ).fieldOf("excluded").forGetter(CleanBloodstream::excluded),
                Codec.FLOAT.fieldOf("cleanserate").forGetter(CleanBloodstream::cleanseRate)
        ).apply(inst, CleanBloodstream::new));

        @Override public String type() { return "CleanBloodstream"; }
        @Override public boolean shouldApply(EffectContext ctx) { return conditionsPass(ctx.entity(), conditions); }
        @Override public void apply(EffectContext ctx) {
            // TODO Implement
            return;
        }
    }

    record MakeSentient(List<ConditionData> conditions) implements EffectData {
        public static final MapCodec<MakeSentient> CODEC = RecordCodecBuilder.mapCodec(inst -> inst.group(
                Codec.list(ConditionData.CODEC).optionalFieldOf("conditions", List.of()).forGetter(MakeSentient::conditions)
        ).apply(inst, MakeSentient::new));

        @Override public String type() { return "MakeSentient"; }
        @Override public boolean shouldApply(EffectContext ctx) { return conditionsPass(ctx.entity(), conditions); }
        @Override public void apply(EffectContext ctx) {
            // TODO Implement
            return;
        }
    }
    record Polymorph(List<ConditionData> conditions, String prototype) implements EffectData {
        public static final MapCodec<Polymorph> CODEC = RecordCodecBuilder.mapCodec(inst -> inst.group(
                Codec.list(ConditionData.CODEC).optionalFieldOf("conditions", List.of()).forGetter(Polymorph::conditions),
                Codec.STRING.fieldOf("prototype").forGetter(Polymorph::prototype)
        ).apply(inst, Polymorph::new));

        @Override public String type() { return "Polymorph"; }
        @Override public boolean shouldApply(EffectContext ctx) { return conditionsPass(ctx.entity(), conditions); }
        @Override public void apply(EffectContext ctx) {
            // TODO Implement
            return;
        }
    }

    record ResetNarcolepsy(List<ConditionData> conditions) implements EffectData {
        public static final MapCodec<ResetNarcolepsy> CODEC = RecordCodecBuilder.mapCodec(inst -> inst.group(
                Codec.list(ConditionData.CODEC).optionalFieldOf("conditions", List.of()).forGetter(ResetNarcolepsy::conditions)
        ).apply(inst, ResetNarcolepsy::new));

        @Override public String type() { return "ResetNarcolepsy"; }
        @Override public boolean shouldApply(EffectContext ctx) { return conditionsPass(ctx.entity(), conditions); }
        @Override public void apply(EffectContext ctx) {
            // TODO Implement
            return;
        }
    }

    record ModifyKnockdown(List<ConditionData> conditions, float time, String subType) implements EffectData {
        public static final MapCodec<ModifyKnockdown> CODEC = RecordCodecBuilder.mapCodec(inst -> inst.group(
                Codec.list(ConditionData.CODEC).optionalFieldOf("conditions", List.of()).forGetter(ModifyKnockdown::conditions),
                Codec.FLOAT.optionalFieldOf("time", 1f).forGetter(ModifyKnockdown::time),
                Codec.STRING.optionalFieldOf("subtype", "update").forGetter(ModifyKnockdown::subType) // TODO: Make this an enum (remove, add, update)
        ).apply(inst, ModifyKnockdown::new));

        @Override public String type() { return "ModifyKnockdown"; }
        @Override public boolean shouldApply(EffectContext ctx) { return conditionsPass(ctx.entity(), conditions); }
        @Override public void apply(EffectContext ctx) {
            // TODO Implement
            return;
        }
    }

    record Electrocute(List<ConditionData> conditions, float electrocuteTime, float siemensCoefficient, float probability) implements EffectData {
        public static final MapCodec<Electrocute> CODEC = RecordCodecBuilder.mapCodec(inst -> inst.group(
                Codec.list(ConditionData.CODEC).optionalFieldOf("conditions", List.of()).forGetter(Electrocute::conditions),
                Codec.FLOAT.optionalFieldOf("electrocutetime", 1f).forGetter(Electrocute::electrocuteTime),
                Codec.FLOAT.optionalFieldOf("siemenscoefficient", 1f).forGetter(Electrocute::siemensCoefficient), // TODO: This is a random default, find what SS14 uses.
                Codec.FLOAT.optionalFieldOf("probability", 1f).forGetter(Electrocute::probability)
        ).apply(inst, Electrocute::new));

        @Override public String type() { return "Electrocute"; }
        @Override public boolean shouldApply(EffectContext ctx) { return conditionsPass(ctx.entity(), conditions); }
        @Override public void apply(EffectContext ctx) {
            // TODO Implement
            return;
        }
    }

    record EyeDamage() implements EffectData {
        public static final MapCodec<EyeDamage> CODEC = MapCodec.unit(new EyeDamage());

        @Override public String type() { return "EyeDamage"; }
        @Override public boolean shouldApply(EffectContext ctx) { return true; }
        @Override public void apply(EffectContext ctx) {
            // TODO Implement
            return;
        }
    }

    record ReduceRotting(List<ConditionData> conditions, float seconds) implements EffectData {
        public static final MapCodec<ReduceRotting> CODEC = RecordCodecBuilder.mapCodec(inst -> inst.group(
                Codec.list(ConditionData.CODEC).optionalFieldOf("conditions", List.of()).forGetter(ReduceRotting::conditions),
                Codec.FLOAT.fieldOf("seconds").forGetter(ReduceRotting::seconds)
        ).apply(inst, ReduceRotting::new));

        @Override public String type() { return "ReduceRotting"; }
        @Override public boolean shouldApply(EffectContext ctx) { return conditionsPass(ctx.entity(), conditions); }
        @Override public void apply(EffectContext ctx) {
            // TODO Implement
            return;
        }
    }

    record CauseZombieInfection(List<ConditionData> conditions) implements EffectData {
        public static final MapCodec<CauseZombieInfection> CODEC = RecordCodecBuilder.mapCodec(inst -> inst.group(
                Codec.list(ConditionData.CODEC).optionalFieldOf("conditions", List.of()).forGetter(CauseZombieInfection::conditions)
        ).apply(inst, CauseZombieInfection::new));

        @Override public String type() { return "CauseZombieInfection"; }
        @Override public boolean shouldApply(EffectContext ctx) { return conditionsPass(ctx.entity(), conditions); }
        @Override public void apply(EffectContext ctx) {
            // TODO Implement
            return;
        }
    }
}
