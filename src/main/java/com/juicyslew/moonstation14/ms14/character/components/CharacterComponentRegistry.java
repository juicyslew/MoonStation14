package com.juicyslew.moonstation14.ms14.character.components;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.juicyslew.moonstation14.component.codec.json.CharacterSchemaAudit;
import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.Decoder;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.Encoder;
import com.mojang.serialization.JsonOps;
import java.util.Map;

/** Explicit bounded discriminator registry; new component types register one codec and one schema audit. */
public final class CharacterComponentRegistry {
    private CharacterComponentRegistry() { }
    private record Entry<T extends CharacterComponent>(Class<T> javaType, Codec<T> codec) {
        <O> DataResult<O> encode(CharacterComponent component, DynamicOps<O> ops) {
            if (!javaType.isInstance(component)) return DataResult.error(() -> "component type/class mismatch");
            return codec.encodeStart(ops, javaType.cast(component));
        }

        DataResult<CharacterComponent> parse(JsonElement json) {
            return codec.parse(JsonOps.INSTANCE, json).map(component -> component);
        }
    }

    private static final Map<String, Entry<?>> TYPES = Map.ofEntries(
            Map.entry(CombatModeComponent.TYPE, new Entry<>(CombatModeComponent.class, CombatModeComponent.CODEC)),
            Map.entry(DisarmProneComponent.TYPE, new Entry<>(DisarmProneComponent.class, DisarmProneComponent.CODEC)),
            Map.entry(MeleeWeaponComponent.TYPE, new Entry<>(MeleeWeaponComponent.class, MeleeWeaponComponent.CODEC)),
            Map.entry(DamageableComponent.TYPE, new Entry<>(DamageableComponent.class, DamageableComponent.CODEC)),
            Map.entry(HumanoidProfileComponent.TYPE, new Entry<>(HumanoidProfileComponent.class, HumanoidProfileComponent.CODEC)),
            Map.entry(GhostRoleComponent.TYPE, new Entry<>(GhostRoleComponent.class, GhostRoleComponent.CODEC)),
            Map.entry(BarotraumaComponent.TYPE, new Entry<>(BarotraumaComponent.class, BarotraumaComponent.CODEC)),
            Map.entry(ExaminerComponent.TYPE, new Entry<>(ExaminerComponent.class, ExaminerComponent.CODEC)),
            Map.entry(ComplexInteractionComponent.TYPE, new Entry<>(ComplexInteractionComponent.class, ComplexInteractionComponent.CODEC)),
            Map.entry(DoorBumpOpenerComponent.TYPE, new Entry<>(DoorBumpOpenerComponent.class, DoorBumpOpenerComponent.CODEC)),
            Map.entry(BlindablePrototypeComponent.TYPE, new Entry<>(BlindablePrototypeComponent.class, BlindablePrototypeComponent.CODEC)),
            Map.entry(BloodstreamComponent.TYPE, new Entry<>(BloodstreamComponent.class, BloodstreamComponent.CODEC)),
            Map.entry(BodyComponent.TYPE, new Entry<>(BodyComponent.class, BodyComponent.CODEC)),
            Map.entry(EquipmentSlotsPrototypeComponent.TYPE, new Entry<>(EquipmentSlotsPrototypeComponent.class, EquipmentSlotsPrototypeComponent.CODEC)),
            Map.entry(FlammablePrototypeComponent.TYPE, new Entry<>(FlammablePrototypeComponent.class, FlammablePrototypeComponent.CODEC)),
            Map.entry(HandsPrototypeComponent.TYPE, new Entry<>(HandsPrototypeComponent.class, HandsPrototypeComponent.CODEC)),
            Map.entry(StrippingPrototypeComponent.TYPE, new Entry<>(StrippingPrototypeComponent.class, StrippingPrototypeComponent.CODEC)),
            Map.entry(StrippablePrototypeComponent.TYPE, new Entry<>(StrippablePrototypeComponent.class, StrippablePrototypeComponent.CODEC)),
            Map.entry(PullableComponent.TYPE, new Entry<>(PullableComponent.class, PullableComponent.CODEC)),
            Map.entry(PullerComponent.TYPE, new Entry<>(PullerComponent.class, PullerComponent.CODEC)),
            Map.entry(HungerPrototypeComponent.TYPE, new Entry<>(HungerPrototypeComponent.class, HungerPrototypeComponent.CODEC)),
            Map.entry(InitialBodyComponent.TYPE, new Entry<>(InitialBodyComponent.class, InitialBodyComponent.CODEC)),
            Map.entry(ObserverMovementComponent.TYPE, new Entry<>(ObserverMovementComponent.class, ObserverMovementComponent.CODEC)),
            Map.entry(IncorporealComponent.TYPE, new Entry<>(IncorporealComponent.class, IncorporealComponent.CODEC)),
            Map.entry(MetabolizerPrototypeComponent.TYPE, new Entry<>(MetabolizerPrototypeComponent.class, MetabolizerPrototypeComponent.CODEC)),
            Map.entry(MovementSpeedModifierComponent.TYPE, new Entry<>(MovementSpeedModifierComponent.class, MovementSpeedModifierComponent.CODEC)),
            Map.entry(MobStateComponent.TYPE, new Entry<>(MobStateComponent.class, MobStateComponent.CODEC)),
            Map.entry(MobThresholdsComponent.TYPE, new Entry<>(MobThresholdsComponent.class, MobThresholdsComponent.CODEC)),
            Map.entry(MouseAppearanceComponent.TYPE, new Entry<>(MouseAppearanceComponent.class, MouseAppearanceComponent.CODEC)),
            Map.entry(CrawlerComponent.TYPE, new Entry<>(CrawlerComponent.class, CrawlerComponent.CODEC)),
            Map.entry(GhostOnMoveComponent.TYPE, new Entry<>(GhostOnMoveComponent.class, GhostOnMoveComponent.CODEC)),
            Map.entry(MouthComponent.TYPE, new Entry<>(MouthComponent.class, MouthComponent.CODEC)),
            Map.entry(NoSlipComponent.TYPE, new Entry<>(NoSlipComponent.class, NoSlipComponent.CODEC)),
            Map.entry(ProtectedFromStepTriggersComponent.TYPE, new Entry<>(ProtectedFromStepTriggersComponent.class, ProtectedFromStepTriggersComponent.CODEC)),
            Map.entry(DamageOnInteractProtectionComponent.TYPE, new Entry<>(DamageOnInteractProtectionComponent.class, DamageOnInteractProtectionComponent.CODEC)),
            Map.entry(ReactiveComponent.TYPE, new Entry<>(ReactiveComponent.class, ReactiveComponent.CODEC)),
            Map.entry(RespiratorComponent.TYPE, new Entry<>(RespiratorComponent.class, RespiratorComponent.CODEC)),
            Map.entry(SpeechComponent.TYPE, new Entry<>(SpeechComponent.class, SpeechComponent.CODEC)),
            Map.entry(StandingStateComponent.TYPE, new Entry<>(StandingStateComponent.class, StandingStateComponent.CODEC)),
            Map.entry(StomachPrototypeComponent.TYPE, new Entry<>(StomachPrototypeComponent.class, StomachPrototypeComponent.CODEC)),
            Map.entry(StunnableComponent.TYPE, new Entry<>(StunnableComponent.class, StunnableComponent.CODEC)),
            Map.entry(ThirstPrototypeComponent.TYPE, new Entry<>(ThirstPrototypeComponent.class, ThirstPrototypeComponent.CODEC)),
            Map.entry(StaminaPrototypeComponent.TYPE, new Entry<>(StaminaPrototypeComponent.class, StaminaPrototypeComponent.CODEC)),
            Map.entry(StatusEffectsPrototypeComponent.TYPE, new Entry<>(StatusEffectsPrototypeComponent.class, StatusEffectsPrototypeComponent.CODEC)),
            Map.entry(TemperatureComponent.TYPE, new Entry<>(TemperatureComponent.class, TemperatureComponent.CODEC)),
            Map.entry(TemperatureDamageComponent.TYPE, new Entry<>(TemperatureDamageComponent.class, TemperatureDamageComponent.CODEC)),
            Map.entry(ThermalRegulatorComponent.TYPE, new Entry<>(ThermalRegulatorComponent.class, ThermalRegulatorComponent.CODEC)),
            Map.entry(AnimalAppearanceComponent.TYPE, new Entry<>(AnimalAppearanceComponent.class, AnimalAppearanceComponent.CODEC)),
            Map.entry(FlightComponent.TYPE, new Entry<>(FlightComponent.class, FlightComponent.CODEC)),
            Map.entry(NpcRetaliationComponent.TYPE, new Entry<>(NpcRetaliationComponent.class, NpcRetaliationComponent.CODEC)),
            Map.entry(MeleeAttackComponent.TYPE, new Entry<>(MeleeAttackComponent.class, MeleeAttackComponent.CODEC)),
            Map.entry(ParrotListenerComponent.TYPE, new Entry<>(ParrotListenerComponent.class, ParrotListenerComponent.CODEC)),
            Map.entry(ParrotMemoryComponent.TYPE, new Entry<>(ParrotMemoryComponent.class, ParrotMemoryComponent.CODEC)),
            Map.entry(AmbientVocalizationComponent.TYPE, new Entry<>(AmbientVocalizationComponent.class, AmbientVocalizationComponent.CODEC)),
            Map.entry(SpeciesComponent.TYPE, new Entry<>(SpeciesComponent.class, SpeciesComponent.CODEC)),
            Map.entry(CollisionProfileComponent.TYPE, new Entry<>(CollisionProfileComponent.class, CollisionProfileComponent.CODEC)),
            Map.entry(NpcBehaviorComponent.TYPE, new Entry<>(NpcBehaviorComponent.class, NpcBehaviorComponent.CODEC)),
            Map.entry(NpcIdleAttentionComponent.TYPE, new Entry<>(NpcIdleAttentionComponent.class, NpcIdleAttentionComponent.CODEC)),
            Map.entry(PortableBodyComponent.TYPE, new Entry<>(PortableBodyComponent.class, PortableBodyComponent.CODEC)),
            Map.entry(CanEscapeInventoryComponent.TYPE, new Entry<>(CanEscapeInventoryComponent.class, CanEscapeInventoryComponent.CODEC)),
            Map.entry(LightningTargetComponent.TYPE, new Entry<>(LightningTargetComponent.class, LightningTargetComponent.CODEC)),
            Map.entry(UdderComponent.TYPE, new Entry<>(UdderComponent.class, UdderComponent.CODEC)),
            Map.entry(EggLayerComponent.TYPE, new Entry<>(EggLayerComponent.class, EggLayerComponent.CODEC)),
            Map.entry(AbsorbentCleaningComponent.TYPE,
                    new Entry<>(AbsorbentCleaningComponent.class, AbsorbentCleaningComponent.CODEC)),
            Map.entry(VocalComponent.TYPE, new Entry<>(VocalComponent.class, VocalComponent.CODEC)));

    public static boolean registered(String type) { return TYPES.containsKey(type); }

    public static final Codec<CharacterComponent> CODEC = Codec.of(new Encoder<>() {
        @Override public <T> DataResult<T> encode(CharacterComponent component, DynamicOps<T> ops, T prefix) {
            if (component == null || !registered(component.type()))
                return DataResult.error(() -> "unknown character component");
            return TYPES.get(component.type()).encode(component, ops);
        }
    }, new Decoder<>() {
        @Override public <T> DataResult<Pair<CharacterComponent, T>> decode(DynamicOps<T> ops, T input) {
            try {
                JsonElement json = ops.convertTo(JsonOps.INSTANCE, input);
                if (!json.isJsonObject()) return DataResult.error(() -> "component must be an object");
                JsonObject object = json.getAsJsonObject();
                CharacterSchemaAudit.auditComponent(object, "$.components[0]");
                String type = object.get("type").getAsString();
                return TYPES.get(type).parse(json).map(component -> Pair.of(component, input));
            } catch (RuntimeException exception) {
                return DataResult.error(() -> exception.getMessage() == null ? "invalid component" : exception.getMessage());
            }
        }
    });
}
