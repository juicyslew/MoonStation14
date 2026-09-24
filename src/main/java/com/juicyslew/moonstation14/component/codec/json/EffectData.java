package com.juicyslew.moonstation14.component.codec.json;

import com.juicyslew.moonstation14.ms14.reagent.ModReagents;
import com.juicyslew.moonstation14.ms14.alert.ModAlerts;
import com.juicyslew.moonstation14.ms14.status_effect.StatusEffectOperation;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.ResourceKey;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import static com.juicyslew.moonstation14.util.CodecHelpers.LENIENT_ID_CODEC;

/** Data-only reagent effects.  Execution remains owned by the existing systems. */
public sealed interface EffectData permits
        EffectData.EvenHealthChange, EffectData.HealthChange, EffectData.Vomit, EffectData.Jitter,
        EffectData.Drunk, EffectData.ModifyBleed, EffectData.Oxygenate, EffectData.ModifyLungGas,
        EffectData.AdjustAlert, EffectData.SatiateHunger, EffectData.ModifyBloodLevel,
        EffectData.SatiateThirst, EffectData.PopupMessage, EffectData.Emote, EffectData.ModifyStatusEffect,
        EffectData.AdjustReagent, EffectData.CureZombieInfection, EffectData.ArtifactDurabilityRestore,
        EffectData.ArtifactUnlock, EffectData.GenericStatusEffect, EffectData.Flammable, EffectData.Ignite,
        EffectData.AdjustTemperature, EffectData.Extinguish, EffectData.MovementSpeedModifier,
        EffectData.CleanBloodstream, EffectData.MakeSentient, EffectData.Polymorph, EffectData.ResetNarcolepsy,
        EffectData.ModifyKnockdown, EffectData.Electrocute, EffectData.EyeDamage, EffectData.ReduceRotting,
        EffectData.CauseZombieInfection {

    EffectCommonData common();
    String type();

    default List<ConditionData> conditions() { return common().conditions(); }
    default float probability() { return common().probability(); }
    default float minScale() { return common().minScale(); }
    default boolean scaling() { return common().scaling(); }

    enum PopupRecipients {
        Pvs("pvs"),
        Local("local");

        private final String serializedName;

        PopupRecipients(String serializedName) {
            this.serializedName = serializedName;
        }

        static final Codec<PopupRecipients> CODEC = EffectCodecHelpers.closedString(
                Map.of("pvs", Pvs, "local", Local), value -> value.serializedName);
    }

    enum PopupMethod {
        PopupEntity("popupentity"),
        PopupCoordinates("popupcoordinates");

        private final String serializedName;

        PopupMethod(String serializedName) {
            this.serializedName = serializedName;
        }

        static final Codec<PopupMethod> CODEC = EffectCodecHelpers.closedString(
                Map.of("popupentity", PopupEntity, "popupcoordinates", PopupCoordinates),
                value -> value.serializedName);
    }

    enum PopupVisualType {
        Small("small"),
        SmallCaution("smallcaution"),
        Medium("medium"),
        MediumCaution("mediumcaution"),
        Large("large"),
        LargeCaution("largecaution");

        private final String serializedName;

        PopupVisualType(String serializedName) {
            this.serializedName = serializedName;
        }

        static final Codec<PopupVisualType> CODEC = EffectCodecHelpers.closedString(
                Map.of("small", Small, "smallcaution", SmallCaution, "medium", Medium,
                        "mediumcaution", MediumCaution, "large", Large, "largecaution", LargeCaution),
                value -> value.serializedName);
    }

    Codec<EffectData> CODEC = Codec.STRING.dispatch(
            "type", EffectData::type, type -> switch (type) {
                case "EvenHealthChange" -> EvenHealthChange.CODEC;
                case "HealthChange" -> HealthChange.CODEC;
                case "Vomit" -> Vomit.CODEC;
                case "Jitter" -> Jitter.CODEC;
                case "Drunk" -> Drunk.CODEC;
                case "ModifyBleed" -> ModifyBleed.CODEC;
                case "Oxygenate" -> Oxygenate.CODEC;
                case "ModifyLungGas" -> ModifyLungGas.CODEC;
                case "AdjustAlert" -> AdjustAlert.CODEC;
                case "SatiateHunger" -> SatiateHunger.CODEC;
                case "ModifyBloodLevel" -> ModifyBloodLevel.CODEC;
                case "SatiateThirst" -> SatiateThirst.CODEC;
                case "PopupMessage" -> PopupMessage.CODEC;
                case "Emote" -> Emote.CODEC;
                case "ModifyStatusEffect" -> ModifyStatusEffect.CODEC;
                case "AdjustReagent" -> AdjustReagent.CODEC;
                case "CureZombieInfection" -> CureZombieInfection.CODEC;
                case "ArtifactDurabilityRestore" -> ArtifactDurabilityRestore.CODEC;
                case "ArtifactUnlock" -> ArtifactUnlock.CODEC;
                case "GenericStatusEffect" -> GenericStatusEffect.CODEC;
                case "Flammable" -> Flammable.CODEC;
                case "Ignite" -> Ignite.CODEC;
                case "AdjustTemperature" -> AdjustTemperature.CODEC;
                case "Extinguish" -> Extinguish.CODEC;
                case "MovementSpeedModifier" -> MovementSpeedModifier.CODEC;
                case "CleanBloodstream" -> CleanBloodstream.CODEC;
                case "MakeSentient" -> MakeSentient.CODEC;
                case "Polymorph" -> Polymorph.CODEC;
                case "ResetNarcolepsy" -> ResetNarcolepsy.CODEC;
                case "ModifyKnockdown" -> ModifyKnockdown.CODEC;
                case "Electrocute" -> Electrocute.CODEC;
                case "EyeDamage" -> EyeDamage.CODEC;
                case "ReduceRotting" -> ReduceRotting.CODEC;
                case "CauseZombieInfection" -> CauseZombieInfection.CODEC;
                default -> throw new IllegalStateException("Unknown effect type: " + type);
            });

    record EvenHealthChange(EffectCommonData common, boolean ignoreResistances, Map<String, Float> damage) implements EffectData {
        public EvenHealthChange {
            damage = Map.copyOf(Objects.requireNonNull(damage, "damage"));
        }

        public static final MapCodec<EvenHealthChange> CODEC = EffectCommonData.withCommon(RecordCodecBuilder.<EvenHealthChange>mapCodec(i -> i.group(
                Codec.BOOL.optionalFieldOf("ignoreresistances", true).forGetter(EvenHealthChange::ignoreResistances),
                Codec.unboundedMap(EffectCodecHelpers.closedString(Set.of(
                                "brute", "burn", "airloss", "toxin", "genetic", "metaphysical")),
                        EffectCodecHelpers.FINITE_FLOAT).fieldOf("damage").forGetter(EvenHealthChange::damage)
        ).apply(i, (ignore, damage) -> new EvenHealthChange(EffectCommonData.DEFAULT, ignore, damage))),
                EvenHealthChange::common,
                (value, common) -> new EvenHealthChange(
                        common, value.ignoreResistances(), value.damage()));
        public EvenHealthChange(List<ConditionData> conditions, Map<String, Float> damage) {
            this(new EffectCommonData(conditions, 1f, 0f, true), true, damage);
        }
        public EvenHealthChange(Map<String, Float> damage) { this(EffectCommonData.DEFAULT, true, damage); }
        @Override public String type() { return "EvenHealthChange"; }
    }

    record HealthChange(EffectCommonData common, boolean ignoreResistances, DamageSpecifierData damage) implements EffectData {
        public static final MapCodec<HealthChange> CODEC = EffectCommonData.withCommon(RecordCodecBuilder.<HealthChange>mapCodec(i -> i.group(
                Codec.BOOL.optionalFieldOf("ignoreresistances", true).forGetter(HealthChange::ignoreResistances),
                DamageSpecifierData.CODEC.fieldOf("damage").forGetter(HealthChange::damage)
        ).apply(i, (ignore, damage) -> new HealthChange(EffectCommonData.DEFAULT, ignore, damage))),
                HealthChange::common,
                (value, common) -> new HealthChange(
                        common, value.ignoreResistances(), value.damage()));
        public HealthChange(List<ConditionData> conditions, boolean ignoreResistances,
                            DamageSpecifierData damage) {
            this(new EffectCommonData(conditions, 1f, 0f, true), ignoreResistances, damage);
        }
        public HealthChange(boolean ignoreResistances, DamageSpecifierData damage) { this(EffectCommonData.DEFAULT, ignoreResistances, damage); }
        @Override public String type() { return "HealthChange"; }
    }

    record Vomit(EffectCommonData common) implements EffectData {
        public static final MapCodec<Vomit> CODEC = unit(Vomit::new);
        public Vomit(List<ConditionData> conditions, Float probability) {
            this(new EffectCommonData(conditions, probability, 0f, true));
        }
        @Override public String type() { return "Vomit"; }
    }

    record Jitter(EffectCommonData common, float amplitude, float frequency, float time, boolean refresh) implements EffectData {
        public static final MapCodec<Jitter> CODEC = EffectCommonData.withCommon(RecordCodecBuilder.<Jitter>mapCodec(i -> i.group(
                        EffectCodecHelpers.NONNEGATIVE_FLOAT.optionalFieldOf("amplitude", 10f).forGetter(Jitter::amplitude),
                        EffectCodecHelpers.NONNEGATIVE_FLOAT.optionalFieldOf("frequency", 4f).forGetter(Jitter::frequency),
                        EffectCodecHelpers.NONNEGATIVE_FLOAT.optionalFieldOf("time", 2f).forGetter(Jitter::time),
                        Codec.BOOL.optionalFieldOf("refresh", true).forGetter(Jitter::refresh))
                .apply(i, (amplitude, frequency, time, refresh) ->
                        new Jitter(EffectCommonData.DEFAULT, amplitude, frequency, time, refresh))),
                Jitter::common,
                (value, common) -> new Jitter(common, value.amplitude(), value.frequency(), value.time(), value.refresh()));
        public Jitter(EffectCommonData common) {
            this(common, 10f, 4f, 2f, true);
        }

        public Jitter(List<ConditionData> conditions) {
            this(new EffectCommonData(conditions, 1f, 0f, true));
        }
        @Override public String type() { return "Jitter"; }
    }

    record Drunk(EffectCommonData common, float boozePower) implements EffectData {
        public static final MapCodec<Drunk> CODEC = EffectData.common(RecordCodecBuilder.<Drunk>mapCodec(i -> i.group(EffectCodecHelpers.NONNEGATIVE_FLOAT.optionalFieldOf("boozepower", 3f).forGetter(Drunk::boozePower)).apply(i, power -> new Drunk(EffectCommonData.DEFAULT, power))), Drunk::new);
        public Drunk(List<ConditionData> conditions, float boozePower) {
            this(new EffectCommonData(conditions, 1f, 0f, true), boozePower);
        }
        private Drunk(EffectCommonData common, Drunk value) { this(common, value.boozePower()); }
        @Override public String type() { return "Drunk"; }
    }

    record ModifyBleed(EffectCommonData common, float amount) implements EffectData {
        public static final MapCodec<ModifyBleed> CODEC = EffectData.common(RecordCodecBuilder.<ModifyBleed>mapCodec(i -> i.group(Codec.FLOAT.fieldOf("amount").forGetter(ModifyBleed::amount)).apply(i, amount -> new ModifyBleed(EffectCommonData.DEFAULT, amount))), ModifyBleed::new);
        public ModifyBleed(List<ConditionData> conditions, float amount) {
            this(new EffectCommonData(conditions, 1f, 0f, true), amount);
        }
        private ModifyBleed(EffectCommonData common, ModifyBleed value) { this(common, value.amount()); }
        @Override public String type() { return "ModifyBleed"; }
    }

    record Oxygenate(EffectCommonData common, float factor) implements EffectData {
        public static final MapCodec<Oxygenate> CODEC = EffectData.common(RecordCodecBuilder.<Oxygenate>mapCodec(i -> i.group(Codec.FLOAT.optionalFieldOf("factor", 1f).forGetter(Oxygenate::factor)).apply(i, factor -> new Oxygenate(EffectCommonData.DEFAULT, factor))), Oxygenate::new);
        public Oxygenate(List<ConditionData> conditions, float factor) {
            this(new EffectCommonData(conditions, 1f, 0f, true), factor);
        }
        private Oxygenate(EffectCommonData common, Oxygenate value) { this(common, value.factor()); }
        @Override public String type() { return "Oxygenate"; }
    }

    record ModifyLungGas(EffectCommonData common, Map<ResourceKey<ReagentData>, Float> ratios) implements EffectData {
        public static final MapCodec<ModifyLungGas> CODEC = EffectData.common(RecordCodecBuilder.<ModifyLungGas>mapCodec(i -> i.group(Codec.unboundedMap(REAGENT_KEY, Codec.FLOAT).fieldOf("ratios").forGetter(ModifyLungGas::ratios)).apply(i, ratios -> new ModifyLungGas(EffectCommonData.DEFAULT, ratios))), ModifyLungGas::new);
        public ModifyLungGas(List<ConditionData> conditions, Map<ResourceKey<ReagentData>, Float> ratios) {
            this(new EffectCommonData(conditions, 1f, 0f, true), ratios);
        }
        private ModifyLungGas(EffectCommonData common, ModifyLungGas value) { this(common, value.ratios()); }
        @Override public String type() { return "ModifyLungGas"; }
    }

    record AdjustAlert(EffectCommonData common, ResourceKey<AlertData> alertType, boolean clear, boolean showCooldown, float time) implements EffectData {
        public static final MapCodec<AdjustAlert> CODEC = EffectData.common(RecordCodecBuilder.<AdjustAlert>mapCodec(i -> i.group(ModAlerts.ALERT_KEY_CODEC.fieldOf("alerttype").forGetter(AdjustAlert::alertType), Codec.BOOL.optionalFieldOf("clear", false).forGetter(AdjustAlert::clear), Codec.BOOL.optionalFieldOf("showcooldown", false).forGetter(value -> value.showCooldown()), EffectCodecHelpers.NONNEGATIVE_FLOAT.optionalFieldOf("time", 0f).forGetter(AdjustAlert::time)).apply(i, (alert, clear, showCooldown, time) -> new AdjustAlert(EffectCommonData.DEFAULT, alert, clear, showCooldown, time))), AdjustAlert::new);
        public AdjustAlert(List<ConditionData> conditions, ResourceKey<AlertData> alertType, float minScale,
                           boolean clear, float time) {
            this(new EffectCommonData(conditions, 1f, minScale, true), alertType, clear, false, time);
        }
        public AdjustAlert(EffectCommonData common, ResourceKey<AlertData> alertType, boolean clear, float time) {
            this(common, alertType, clear, false, time);
        }
        public AdjustAlert(List<ConditionData> conditions, String alertType, float minScale,
                           boolean clear, float time) {
            this(conditions, ModAlerts.createKeyFromReference(alertType), minScale, clear, time);
        }
        public AdjustAlert(EffectCommonData common, String alertType, boolean clear,
                           boolean showCooldown, float time) {
            this(common, ModAlerts.createKeyFromReference(alertType), clear, showCooldown, time);
        }
        public AdjustAlert(EffectCommonData common, String alertType, boolean clear, float time) {
            this(common, ModAlerts.createKeyFromReference(alertType), clear, false, time);
        }
        private AdjustAlert(EffectCommonData common, AdjustAlert value) { this(common, value.alertType(), value.clear(), value.showCooldown(), value.time()); }
        @Override public String type() { return "AdjustAlert"; }
    }

    record SatiateHunger(EffectCommonData common, float factor) implements EffectData {
        public static final MapCodec<SatiateHunger> CODEC = EffectData.common(RecordCodecBuilder.<SatiateHunger>mapCodec(i -> i.group(EffectCodecHelpers.FINITE_FLOAT.optionalFieldOf("factor", 1.5f).forGetter(SatiateHunger::factor)).apply(i, factor -> new SatiateHunger(EffectCommonData.DEFAULT, factor))), SatiateHunger::new);
        public SatiateHunger(List<ConditionData> conditions, float factor) {
            this(new EffectCommonData(conditions, 1f, 0f, true), factor);
        }
        private SatiateHunger(EffectCommonData common, SatiateHunger value) { this(common, value.factor()); }
        @Override public String type() { return "SatiateHunger"; }
    }

    record ModifyBloodLevel(EffectCommonData common, float amount) implements EffectData {
        public static final MapCodec<ModifyBloodLevel> CODEC = EffectData.common(RecordCodecBuilder.<ModifyBloodLevel>mapCodec(i -> i.group(Codec.FLOAT.fieldOf("amount").forGetter(ModifyBloodLevel::amount)).apply(i, amount -> new ModifyBloodLevel(EffectCommonData.DEFAULT, amount))), ModifyBloodLevel::new);
        public ModifyBloodLevel(List<ConditionData> conditions, float amount) {
            this(new EffectCommonData(conditions, 1f, 0f, true), amount);
        }
        private ModifyBloodLevel(EffectCommonData common, ModifyBloodLevel value) { this(common, value.amount()); }
        @Override public String type() { return "ModifyBloodLevel"; }
    }

    record SatiateThirst(EffectCommonData common, float factor) implements EffectData {
        public static final MapCodec<SatiateThirst> CODEC = EffectData.common(RecordCodecBuilder.<SatiateThirst>mapCodec(i -> i.group(EffectCodecHelpers.FINITE_FLOAT.optionalFieldOf("factor", 1.5f).forGetter(SatiateThirst::factor)).apply(i, factor -> new SatiateThirst(EffectCommonData.DEFAULT, factor))), SatiateThirst::new);
        public SatiateThirst(List<ConditionData> conditions, float factor) {
            this(new EffectCommonData(conditions, 1f, 0f, true), factor);
        }
        private SatiateThirst(EffectCommonData common, SatiateThirst value) { this(common, value.factor()); }
        @Override public String type() { return "SatiateThirst"; }
    }

    record PopupMessage(EffectCommonData common, PopupRecipients subType, PopupMethod method,
                        PopupVisualType visualType, List<String> messages) implements EffectData {
        public PopupMessage {
            messages = List.copyOf(Objects.requireNonNull(messages, "messages"));
            if (messages.isEmpty() || messages.stream().anyMatch(String::isBlank)) {
                throw new IllegalArgumentException("messages must be nonempty and contain no blank keys");
            }
        }

        public static final MapCodec<PopupMessage> CODEC = EffectData.common(RecordCodecBuilder.<PopupMessage>mapCodec(i -> i.group(
                PopupRecipients.CODEC.optionalFieldOf("subtype", PopupRecipients.Local).forGetter(PopupMessage::subType),
                PopupMethod.CODEC.optionalFieldOf("method", PopupMethod.PopupEntity).forGetter(PopupMessage::method),
                PopupVisualType.CODEC.optionalFieldOf("visualtype", PopupVisualType.Small).forGetter(PopupMessage::visualType),
                 Codec.list(EffectCodecHelpers.NONBLANK_STRING).validate(messages -> !messages.isEmpty()
                         ? com.mojang.serialization.DataResult.success(messages)
                         : com.mojang.serialization.DataResult.error(() -> "messages must not be empty"))
                        .fieldOf("messages").forGetter(PopupMessage::messages))
                .apply(i, (sub, method, visual, messages) -> new PopupMessage(
                        EffectCommonData.DEFAULT, sub, method, visual, messages))), PopupMessage::new);
        public PopupMessage(List<ConditionData> conditions, String subType, String visualType,
                            List<String> messages, float probability) {
                this(new EffectCommonData(conditions, probability, 0f, true),
                    popupRecipient(subType), PopupMethod.PopupEntity, popupVisual(visualType), messages);
        }
        public PopupMessage(EffectCommonData common, String subType, String visualType,
                            List<String> messages) {
            this(common, popupRecipient(subType), PopupMethod.PopupEntity, popupVisual(visualType), messages);
        }
        private PopupMessage(EffectCommonData common, PopupMessage value) {
            this(common, value.subType(), value.method(), value.visualType(), value.messages());
        }
        @Override public String type() { return "PopupMessage"; }

        private static PopupRecipients popupRecipient(String value) {
            return switch (value) {
                case "local" -> PopupRecipients.Local;
                case "pvs" -> PopupRecipients.Pvs;
                default -> throw new IllegalArgumentException("Unknown popup recipient: " + value);
            };
        }

        private static PopupVisualType popupVisual(String value) {
            return switch (value) {
                case "small" -> PopupVisualType.Small;
                case "smallcaution" -> PopupVisualType.SmallCaution;
                case "medium" -> PopupVisualType.Medium;
                case "mediumcaution" -> PopupVisualType.MediumCaution;
                case "large" -> PopupVisualType.Large;
                case "largecaution" -> PopupVisualType.LargeCaution;
                default -> throw new IllegalArgumentException("Unknown popup visual type: " + value);
            };
        }
    }

    record Emote(EffectCommonData common, String emote, boolean showInGuidebook, boolean showInChat, boolean force) implements EffectData {
        public static final MapCodec<Emote> CODEC = EffectData.common(RecordCodecBuilder.<Emote>mapCodec(i -> i.group(EmoteRegistry.CODEC.fieldOf("emote").forGetter(Emote::emote), Codec.BOOL.optionalFieldOf("showinguidebook", false).forGetter(Emote::showInGuidebook), Codec.BOOL.optionalFieldOf("showinchat", false).forGetter(Emote::showInChat), Codec.BOOL.optionalFieldOf("force", false).forGetter(Emote::force)).apply(i, (emote, guide, chat, force) -> new Emote(EffectCommonData.DEFAULT, emote, guide, chat, force))), Emote::new);
        public Emote(List<ConditionData> conditions, String emote, boolean showInGuidebook,
                     float probability) {
            this(new EffectCommonData(conditions, probability, 0f, true), emote,
                    showInGuidebook, false, false);
        }
        public Emote {
            Objects.requireNonNull(common, "common");
            Objects.requireNonNull(emote, "emote");
        }
        private Emote(EffectCommonData common, Emote value) { this(common, value.emote(), value.showInGuidebook(), value.showInChat(), value.force()); }
        @Override public String type() { return "Emote"; }
    }

    record ModifyStatusEffect(EffectCommonData common, String effectKey, StatusEffectDuration time,
                              StatusEffectOperation subType, float delay) implements EffectData {
        public static final MapCodec<ModifyStatusEffect> CODEC = EffectData.common(
                RecordCodecBuilder.<ModifyStatusEffect>mapCodec(i -> i.group(
                        EffectCodecHelpers.NONBLANK_STRING.fieldOf("effectproto")
                                .forGetter(ModifyStatusEffect::effectKey),
                        StatusEffectDuration.fieldOf("time")
                                .forGetter(ModifyStatusEffect::time),
                        StatusEffectOperation.CODEC.optionalFieldOf("subtype", StatusEffectOperation.UPDATE)
                                .forGetter(ModifyStatusEffect::subType),
                        EffectCodecHelpers.NONNEGATIVE_FLOAT.optionalFieldOf("delay", 0f)
                                .forGetter(ModifyStatusEffect::delay)
                ).apply(i, (key, time, sub, delay) -> new ModifyStatusEffect(
                        EffectCommonData.DEFAULT, key, time, sub, delay))),
                ModifyStatusEffect::new);

        public ModifyStatusEffect {
            common = Objects.requireNonNull(common, "common");
            effectKey = requireNonBlank(effectKey, "effectKey");
            Objects.requireNonNull(time, "time");
            Objects.requireNonNull(subType, "subType");
            if (!Float.isFinite(delay) || delay < 0f) {
                throw new IllegalArgumentException("delay must be finite and nonnegative");
            }
        }

        public ModifyStatusEffect(List<ConditionData> conditions, String effectKey,
                                   float time, String subType) {
            this(new EffectCommonData(conditions, 1f, 0f, true), effectKey,
                    StatusEffectDuration.finite(time), operation(subType), 0f);
        }
        public ModifyStatusEffect(List<ConditionData> conditions, String effectKey,
                                  StatusEffectDuration time, StatusEffectOperation subType) {
            this(new EffectCommonData(conditions, 1f, 0f, true), effectKey, time, subType, 0f);
        }
        private ModifyStatusEffect(EffectCommonData common, ModifyStatusEffect value) {
            this(common, value.effectKey(), value.time(), value.subType(), value.delay());
        }
        @Override public String type() { return "ModifyStatusEffect"; }
    }

    record AdjustReagent(EffectCommonData common, ResourceKey<ReagentData> reagent, float amount) implements EffectData {
        public static final MapCodec<AdjustReagent> CODEC = EffectData.common(RecordCodecBuilder.<AdjustReagent>mapCodec(i -> i.group(REAGENT_KEY.fieldOf("reagent").forGetter(AdjustReagent::reagent), EffectCodecHelpers.FINITE_FLOAT.fieldOf("amount").forGetter(AdjustReagent::amount)).apply(i, (reagent, amount) -> new AdjustReagent(EffectCommonData.DEFAULT, reagent, amount))), AdjustReagent::new);
        public AdjustReagent(List<ConditionData> conditions, ResourceKey<ReagentData> reagent,
                             float amount) {
            this(new EffectCommonData(conditions, 1f, 0f, true), reagent, amount);
        }
        private AdjustReagent(EffectCommonData common, AdjustReagent value) { this(common, value.reagent(), value.amount()); }
        @Override public String type() { return "AdjustReagent"; }
    }

    record CureZombieInfection(EffectCommonData common, boolean innoculate) implements EffectData {
        public static final MapCodec<CureZombieInfection> CODEC = EffectData.common(RecordCodecBuilder.<CureZombieInfection>mapCodec(i -> i.group(Codec.BOOL.optionalFieldOf("innoculate", false).forGetter(CureZombieInfection::innoculate)).apply(i, value -> new CureZombieInfection(EffectCommonData.DEFAULT, value))), CureZombieInfection::new);
        public CureZombieInfection(List<ConditionData> conditions) {
            this(new EffectCommonData(conditions, 1f, 0f, true), false);
        }
        public CureZombieInfection(boolean innoculate) {
            this(EffectCommonData.DEFAULT, innoculate);
        }
        private CureZombieInfection(EffectCommonData common, CureZombieInfection value) { this(common, value.innoculate()); }
        @Override public String type() { return "CureZombieInfection"; }
    }

    record ArtifactDurabilityRestore(EffectCommonData common) implements EffectData {
        public static final MapCodec<ArtifactDurabilityRestore> CODEC = unit(ArtifactDurabilityRestore::new);

        public ArtifactDurabilityRestore(List<ConditionData> conditions, float minScale) {
            this(new EffectCommonData(conditions, 1f, minScale, true));
        }

        @Override public String type() { return "ArtifactDurabilityRestore"; }
    }

    record ArtifactUnlock(EffectCommonData common) implements EffectData {
        public static final MapCodec<ArtifactUnlock> CODEC = unit(ArtifactUnlock::new);

        public ArtifactUnlock(List<ConditionData> conditions, float minScale) {
            this(new EffectCommonData(conditions, 1f, minScale, true));
        }

        @Override public String type() { return "ArtifactUnlock"; }
    }

    record GenericStatusEffect(EffectCommonData common, String effectKey, String component,
                               StatusEffectOperation subType, float time) implements EffectData {
        public static final MapCodec<GenericStatusEffect> CODEC = EffectData.common(
                RecordCodecBuilder.<GenericStatusEffect>mapCodec(i -> i.group(
                        EffectCodecHelpers.NONBLANK_STRING.fieldOf("key").forGetter(GenericStatusEffect::effectKey),
                        Codec.STRING.optionalFieldOf("component", "")
                                .forGetter(GenericStatusEffect::component),
                        StatusEffectOperation.CODEC.optionalFieldOf("subtype", StatusEffectOperation.UPDATE)
                                .forGetter(GenericStatusEffect::subType),
                        EffectCodecHelpers.NONNEGATIVE_FLOAT.optionalFieldOf("time", 2f)
                                .forGetter(GenericStatusEffect::time)
                ).apply(i, (key, component, sub, time) -> new GenericStatusEffect(
                        EffectCommonData.DEFAULT, key, component, sub, time))),
                GenericStatusEffect::new);

        public GenericStatusEffect {
            common = Objects.requireNonNull(common, "common");
            effectKey = requireNonBlank(effectKey, "effectKey");
            Objects.requireNonNull(component, "component");
            Objects.requireNonNull(subType, "subType");
            if (!Float.isFinite(time) || time < 0f) {
                throw new IllegalArgumentException("time must be finite and nonnegative");
            }
        }

        public GenericStatusEffect(List<ConditionData> conditions, String effectKey,
                                   String component, String subType, float time) {
            this(new EffectCommonData(conditions, 1f, 0f, true), effectKey, component,
                    operation(subType), time);
        }
        public GenericStatusEffect(List<ConditionData> conditions, String effectKey,
                                   String component, StatusEffectOperation subType, float time) {
            this(new EffectCommonData(conditions, 1f, 0f, true), effectKey, component, subType, time);
        }
        private GenericStatusEffect(EffectCommonData common, GenericStatusEffect v) {
            this(common, v.effectKey(), v.component(), v.subType(), v.time());
        }
        @Override public String type() { return "GenericStatusEffect"; }
    }
    record Flammable(EffectCommonData common, float multiplier, Float multiplierOnExisting) implements EffectData {
        public static final MapCodec<Flammable> CODEC = EffectData.common(
                RecordCodecBuilder.<Flammable>mapCodec(i -> i.group(
                        EffectCodecHelpers.FINITE_FLOAT.optionalFieldOf("multiplier", 0.05f)
                                .forGetter(Flammable::multiplier),
                        // Absence, rather than JSON null, is the canonical null representation.
                        EffectCodecHelpers.FINITE_FLOAT.optionalFieldOf("multiplieronexisting")
                                .forGetter(value -> java.util.Optional.ofNullable(value.multiplierOnExisting()))
                ).apply(i, (multiplier, onExisting) -> new Flammable(
                        EffectCommonData.DEFAULT, multiplier, onExisting.orElse(null)))),
                Flammable::new);

        public Flammable(List<ConditionData> conditions, float multiplier) {
            this(new EffectCommonData(conditions, 1f, 0f, true), multiplier, null);
        }
        public Flammable(EffectCommonData common, float multiplier) {
            this(common, multiplier, null);
        }

        private Flammable(EffectCommonData common, Flammable value) {
            this(common, value.multiplier(), value.multiplierOnExisting());
        }

        @Override public String type() { return "Flammable"; }
    }

    record Ignite(EffectCommonData common) implements EffectData {
        public static final MapCodec<Ignite> CODEC = unit(Ignite::new);

        public Ignite(List<ConditionData> conditions) {
            this(new EffectCommonData(conditions, 1f, 0f, true));
        }

        @Override public String type() { return "Ignite"; }
    }

    record AdjustTemperature(EffectCommonData common, float amount) implements EffectData {
        public static final MapCodec<AdjustTemperature> CODEC = EffectData.common(
                RecordCodecBuilder.<AdjustTemperature>mapCodec(i -> i.group(
                        Codec.FLOAT.fieldOf("amount").forGetter(AdjustTemperature::amount)
                ).apply(i, value -> new AdjustTemperature(EffectCommonData.DEFAULT, value))),
                AdjustTemperature::new);

        public AdjustTemperature(List<ConditionData> conditions, float amount) {
            this(new EffectCommonData(conditions, 1f, 0f, true), amount);
        }

        private AdjustTemperature(EffectCommonData common, AdjustTemperature value) {
            this(common, value.amount());
        }

        @Override public String type() { return "AdjustTemperature"; }
    }

    record Extinguish(EffectCommonData common, float fireStacksAdjustment) implements EffectData {
        public static final MapCodec<Extinguish> CODEC = EffectData.common(
                RecordCodecBuilder.<Extinguish>mapCodec(i -> i.group(
                        EffectCodecHelpers.FINITE_FLOAT.optionalFieldOf("firestacksadjustment", -1.5f)
                                .forGetter(Extinguish::fireStacksAdjustment)
                ).apply(i, value -> new Extinguish(EffectCommonData.DEFAULT, value))),
                Extinguish::new);

        public Extinguish(List<ConditionData> conditions) {
            this(new EffectCommonData(conditions, 1f, 0f, true), -1.5f);
        }
        public Extinguish(EffectCommonData common) {
            this(common, -1.5f);
        }

        private Extinguish(EffectCommonData common, Extinguish value) {
            this(common, value.fireStacksAdjustment());
        }

        @Override public String type() { return "Extinguish"; }
    }

    record MovementSpeedModifier(EffectCommonData common, float walkSpeedModifier,
                                 float sprintSpeedModifier, String effectProto,
                                 StatusEffectDuration time, StatusEffectOperation subType,
                                 float delay) implements EffectData {
        public static final MapCodec<MovementSpeedModifier> CODEC = EffectData.common(
                RecordCodecBuilder.<MovementSpeedModifier>mapCodec(i -> i.group(
                        EffectCodecHelpers.NONNEGATIVE_FLOAT.optionalFieldOf("walkspeedmodifier", 1f)
                                .forGetter(MovementSpeedModifier::walkSpeedModifier),
                        EffectCodecHelpers.NONNEGATIVE_FLOAT.optionalFieldOf("sprintspeedmodifier", 1f)
                                .forGetter(MovementSpeedModifier::sprintSpeedModifier),
                        // Upstream MovementModStatusSystem.ReagentSpeed is
                        // Upstream serializes the omitted default as
                        // ReagentSpeedStatusEffect. Runtime key creation and
                        // reference validation canonicalize it to the
                        // lowercase prototype without changing this DTO.
                        EffectCodecHelpers.NONBLANK_STRING.optionalFieldOf("effectproto", "ReagentSpeedStatusEffect")
                                .forGetter(MovementSpeedModifier::effectProto),
                        StatusEffectDuration.fieldOf("time")
                                .forGetter(MovementSpeedModifier::time),
                        StatusEffectOperation.CODEC.optionalFieldOf("subtype", StatusEffectOperation.UPDATE)
                                .forGetter(MovementSpeedModifier::subType),
                        EffectCodecHelpers.NONNEGATIVE_FLOAT.optionalFieldOf("delay", 0f)
                                .forGetter(MovementSpeedModifier::delay)
                ).apply(i, (walk, sprint, proto, time, sub, delay) -> new MovementSpeedModifier(
                        EffectCommonData.DEFAULT, walk, sprint, proto, time, sub, delay))),
                MovementSpeedModifier::new);

        public MovementSpeedModifier {
            common = Objects.requireNonNull(common, "common");
            Objects.requireNonNull(effectProto, "effectProto");
            if (effectProto.isBlank()) {
                throw new IllegalArgumentException("effectProto must not be blank");
            }
            if (!Float.isFinite(walkSpeedModifier) || walkSpeedModifier < 0f
                    || !Float.isFinite(sprintSpeedModifier) || sprintSpeedModifier < 0f) {
                throw new IllegalArgumentException("speed modifiers must be finite and nonnegative");
            }
            Objects.requireNonNull(time, "time");
            Objects.requireNonNull(subType, "subType");
            if (!Float.isFinite(delay) || delay < 0f) {
                throw new IllegalArgumentException("delay must be finite and nonnegative");
            }
        }

        public MovementSpeedModifier(List<ConditionData> conditions, float minScale,
                                      float walkSpeedModifier, float sprintSpeedModifier,
                                      float time) {
            this(new EffectCommonData(conditions, 1f, minScale, true),
                    walkSpeedModifier, sprintSpeedModifier, "ReagentSpeedStatusEffect",
                    StatusEffectDuration.finite(time), StatusEffectOperation.UPDATE, 0f);
        }

        public MovementSpeedModifier(List<ConditionData> conditions, float minScale,
                                     float walkSpeedModifier, float sprintSpeedModifier,
                                     String effectProto, StatusEffectDuration time,
                                     StatusEffectOperation subType, float delay) {
            this(new EffectCommonData(conditions, 1f, minScale, true), walkSpeedModifier,
                    sprintSpeedModifier, effectProto, time, subType, delay);
        }

        private MovementSpeedModifier(EffectCommonData common, MovementSpeedModifier value) {
            this(common, value.walkSpeedModifier(), value.sprintSpeedModifier(), value.effectProto(),
                    value.time(), value.subType(), value.delay());
        }

        @Override public String type() { return "MovementSpeedModifier"; }
    }

    record CleanBloodstream(EffectCommonData common, ResourceKey<ReagentData> excluded,
                            float cleanseRate) implements EffectData {
        public static final MapCodec<CleanBloodstream> CODEC = EffectData.common(
                RecordCodecBuilder.<CleanBloodstream>mapCodec(i -> i.group(
                        REAGENT_KEY.fieldOf("excluded").forGetter(CleanBloodstream::excluded),
                        Codec.FLOAT.fieldOf("cleanserate").forGetter(CleanBloodstream::cleanseRate)
                ).apply(i, (excluded, rate) -> new CleanBloodstream(
                        EffectCommonData.DEFAULT, excluded, rate))),
                CleanBloodstream::new);

        public CleanBloodstream(List<ConditionData> conditions,
                                ResourceKey<ReagentData> excluded, float cleanseRate) {
            this(new EffectCommonData(conditions, 1f, 0f, true), excluded, cleanseRate);
        }

        private CleanBloodstream(EffectCommonData common, CleanBloodstream value) {
            this(common, value.excluded(), value.cleanseRate());
        }

        @Override public String type() { return "CleanBloodstream"; }
    }

    record MakeSentient(EffectCommonData common) implements EffectData {
        public static final MapCodec<MakeSentient> CODEC = unit(MakeSentient::new);

        public MakeSentient(List<ConditionData> conditions) {
            this(new EffectCommonData(conditions, 1f, 0f, true));
        }

        @Override public String type() { return "MakeSentient"; }
    }

    record Polymorph(EffectCommonData common, String prototype) implements EffectData {
        public static final MapCodec<Polymorph> CODEC = EffectData.common(
                RecordCodecBuilder.<Polymorph>mapCodec(i -> i.group(
                        Codec.STRING.fieldOf("prototype").forGetter(Polymorph::prototype)
                ).apply(i, value -> new Polymorph(EffectCommonData.DEFAULT, value))),
                Polymorph::new);

        public Polymorph(List<ConditionData> conditions, String prototype) {
            this(new EffectCommonData(conditions, 1f, 0f, true), prototype);
        }

        private Polymorph(EffectCommonData common, Polymorph value) {
            this(common, value.prototype());
        }

        @Override public String type() { return "Polymorph"; }
    }

    record ResetNarcolepsy(EffectCommonData common) implements EffectData {
        public static final MapCodec<ResetNarcolepsy> CODEC = unit(ResetNarcolepsy::new);

        public ResetNarcolepsy(List<ConditionData> conditions) {
            this(new EffectCommonData(conditions, 1f, 0f, true));
        }

        @Override public String type() { return "ResetNarcolepsy"; }
    }

    record ModifyKnockdown(EffectCommonData common, StatusEffectDuration time,
                           StatusEffectOperation subType, float delay,
                           boolean crawling, boolean drop)
            implements EffectData {
        public static final MapCodec<ModifyKnockdown> CODEC = EffectData.common(
                RecordCodecBuilder.<ModifyKnockdown>mapCodec(i -> i.group(
                        StatusEffectDuration.fieldOf("time")
                                .forGetter(ModifyKnockdown::time),
                        StatusEffectOperation.CODEC.optionalFieldOf("subtype", StatusEffectOperation.UPDATE)
                                .forGetter(ModifyKnockdown::subType),
                        EffectCodecHelpers.NONNEGATIVE_FLOAT.optionalFieldOf("delay", 0f)
                                .forGetter(ModifyKnockdown::delay),
                        Codec.BOOL.optionalFieldOf("crawling", false)
                                .forGetter(ModifyKnockdown::crawling),
                        Codec.BOOL.optionalFieldOf("drop", false)
                                .forGetter(ModifyKnockdown::drop)
                ).apply(i, (time, subType, delay, crawling, drop) -> new ModifyKnockdown(
                        EffectCommonData.DEFAULT, time, subType, delay, crawling, drop))),
                ModifyKnockdown::new);

        public ModifyKnockdown {
            common = Objects.requireNonNull(common, "common");
            Objects.requireNonNull(time, "time");
            Objects.requireNonNull(subType, "subType");
            if (!Float.isFinite(delay) || delay < 0f) {
                throw new IllegalArgumentException("delay must be finite and nonnegative");
            }
        }

        public ModifyKnockdown(List<ConditionData> conditions, float time, String subType) {
            this(new EffectCommonData(conditions, 1f, 0f, true), StatusEffectDuration.finite(time),
                    operation(subType), 0f, false, false);
        }

        public ModifyKnockdown(List<ConditionData> conditions, StatusEffectDuration time,
                               StatusEffectOperation subType, float delay,
                               boolean crawling, boolean drop) {
            this(new EffectCommonData(conditions, 1f, 0f, true), time, subType, delay, crawling, drop);
        }

        private ModifyKnockdown(EffectCommonData common, ModifyKnockdown value) {
            this(common, value.time(), value.subType(), value.delay(), value.crawling(), value.drop());
        }

        @Override public String type() { return "ModifyKnockdown"; }
    }

    record Electrocute(EffectCommonData common, float electrocuteTime, int shockDamage,
                       boolean refresh, boolean bypassInsulation, float siemensCoefficient) implements EffectData {
        public static final MapCodec<Electrocute> CODEC = EffectData.common(
                RecordCodecBuilder.<Electrocute>mapCodec(i -> i.group(
                        EffectCodecHelpers.NONNEGATIVE_FLOAT.optionalFieldOf("electrocutetime", 2f)
                                .forGetter(Electrocute::electrocuteTime),
                        EffectCodecHelpers.NONNEGATIVE_INT.optionalFieldOf("shockdamage", 5)
                                .forGetter(Electrocute::shockDamage),
                        Codec.BOOL.optionalFieldOf("refresh", true).forGetter(Electrocute::refresh),
                        Codec.BOOL.optionalFieldOf("bypassinsulation", true).forGetter(Electrocute::bypassInsulation),
                        EffectCodecHelpers.NONNEGATIVE_FLOAT.optionalFieldOf("siemenscoefficient", 1f)
                                .forGetter(Electrocute::siemensCoefficient)
                ).apply(i, (time, damage, refresh, bypass, coefficient) -> new Electrocute(
                        EffectCommonData.DEFAULT, time, damage, refresh, bypass, coefficient))),
                Electrocute::new);

        public Electrocute(List<ConditionData> conditions, float electrocuteTime,
                           float siemensCoefficient, float probability) {
            this(new EffectCommonData(conditions, probability, 0f, true),
                    electrocuteTime, 5, true, true, siemensCoefficient);
        }
        public Electrocute(EffectCommonData common, float electrocuteTime,
                           float siemensCoefficient) {
            this(common, electrocuteTime, 5, true, true, siemensCoefficient);
        }

        private Electrocute(EffectCommonData common, Electrocute value) {
            this(common, value.electrocuteTime(), value.shockDamage(), value.refresh(),
                    value.bypassInsulation(), value.siemensCoefficient());
        }

        @Override public String type() { return "Electrocute"; }
    }

    record EyeDamage(EffectCommonData common, int amount) implements EffectData {
        public static final MapCodec<EyeDamage> CODEC = EffectData.common(
                RecordCodecBuilder.<EyeDamage>mapCodec(i -> i.group(
                        EffectCodecHelpers.EXACT_INT.optionalFieldOf("amount", -1).forGetter(EyeDamage::amount)
                ).apply(i, value -> new EyeDamage(EffectCommonData.DEFAULT, value))),
                EyeDamage::new);

        public EyeDamage() {
            this(EffectCommonData.DEFAULT, -1);
        }
        public EyeDamage(EffectCommonData common) {
            this(common, -1);
        }

        private EyeDamage(EffectCommonData common, EyeDamage value) {
            this(common, value.amount());
        }

        @Override public String type() { return "EyeDamage"; }
    }

    record ReduceRotting(EffectCommonData common, float seconds) implements EffectData {
        public static final MapCodec<ReduceRotting> CODEC = EffectData.common(
                RecordCodecBuilder.<ReduceRotting>mapCodec(i -> i.group(
                        Codec.FLOAT.fieldOf("seconds").forGetter(ReduceRotting::seconds)
                ).apply(i, value -> new ReduceRotting(EffectCommonData.DEFAULT, value))),
                ReduceRotting::new);

        public ReduceRotting(List<ConditionData> conditions, float seconds) {
            this(new EffectCommonData(conditions, 1f, 0f, true), seconds);
        }

        private ReduceRotting(EffectCommonData common, ReduceRotting value) {
            this(common, value.seconds());
        }

        @Override public String type() { return "ReduceRotting"; }
    }

    record CauseZombieInfection(EffectCommonData common) implements EffectData {
        public static final MapCodec<CauseZombieInfection> CODEC = unit(CauseZombieInfection::new);

        public CauseZombieInfection(List<ConditionData> conditions) {
            this(new EffectCommonData(conditions, 1f, 0f, true));
        }

        @Override public String type() { return "CauseZombieInfection"; }
    }

    Codec<ResourceKey<ReagentData>> REAGENT_KEY = LENIENT_ID_CODEC.xmap(rl -> ResourceKey.create(ModReagents.REAGENT_REGISTRY_KEY, rl), ResourceKey::location);

    private static String requireNonBlank(String value, String field) {
        Objects.requireNonNull(value, field);
        if (value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value;
    }

    private static StatusEffectOperation operation(String value) {
        Objects.requireNonNull(value, "subType");
        return switch (value) {
            case "update" -> StatusEffectOperation.UPDATE;
            case "add" -> StatusEffectOperation.ADD;
            case "remove" -> StatusEffectOperation.REMOVE;
            case "set" -> StatusEffectOperation.SET;
            default -> throw new IllegalArgumentException("Unknown status effect operation '" + value + "'");
        };
    }

    private static <T extends EffectData> MapCodec<T> common(
            MapCodec<T> specific,
            java.util.function.BiFunction<EffectCommonData, T, T> setter) {
        return EffectCommonData.withCommon(specific, EffectData::common,
                (value, common) -> setter.apply(common, value));
    }
    private static <T extends EffectData> MapCodec<T> unit(java.util.function.Function<EffectCommonData, T> factory) {
        T defaultValue = factory.apply(EffectCommonData.DEFAULT);
        return EffectCommonData.withCommon(
                MapCodec.unit(defaultValue),
                EffectData::common,
                (value, common) -> factory.apply(common));
    }
}
