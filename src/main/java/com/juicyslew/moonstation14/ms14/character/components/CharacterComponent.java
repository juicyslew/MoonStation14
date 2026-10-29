package com.juicyslew.moonstation14.ms14.character.components;

/** Immutable prototype configuration, not an entity attachment. */
public sealed interface CharacterComponent permits BarotraumaComponent, BlindablePrototypeComponent,
        BloodstreamComponent, BodyComponent, ComplexInteractionComponent, EquipmentSlotsPrototypeComponent,
        FlammablePrototypeComponent, HandsPrototypeComponent, HungerPrototypeComponent, InitialBodyComponent,
        MetabolizerPrototypeComponent, MovementSpeedModifierComponent, NoSlipComponent, ReactiveComponent,
        RespiratorComponent, SpeechComponent, StandingStateComponent, StomachPrototypeComponent, StunnableComponent,
        TemperatureComponent, TemperatureDamageComponent, ThermalRegulatorComponent, ThirstPrototypeComponent,
        MouthComponent, MobStateComponent, MobThresholdsComponent, CrawlerComponent, PullableComponent,
        ObserverMovementComponent, IncorporealComponent, GhostOnMoveComponent, PullerComponent, DoorBumpOpenerComponent,
        ExaminerComponent, DamageableComponent, HumanoidProfileComponent, StrippingPrototypeComponent,
        StrippablePrototypeComponent, GhostRoleComponent, MouseAppearanceComponent, CollisionProfileComponent,
        NpcBehaviorComponent, AmbientVocalizationComponent, NpcIdleAttentionComponent, PortableBodyComponent, UdderComponent,
        CanEscapeInventoryComponent, SpeciesComponent, AnimalAppearanceComponent, FlightComponent, NpcRetaliationComponent,
        MeleeAttackComponent, ParrotListenerComponent, ParrotMemoryComponent, EggLayerComponent,
        AbsorbentCleaningComponent, CombatModeComponent, VocalComponent, LightningTargetComponent, ProtectedFromStepTriggersComponent,
        DamageOnInteractProtectionComponent, StaminaPrototypeComponent, StatusEffectsPrototypeComponent,
        MeleeWeaponComponent, DisarmProneComponent {
    String type();
}
