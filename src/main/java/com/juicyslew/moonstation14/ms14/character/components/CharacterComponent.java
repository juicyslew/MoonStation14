package com.juicyslew.moonstation14.ms14.character.components;

/** Immutable prototype configuration, not an entity attachment. */
public sealed interface CharacterComponent permits BarotraumaComponent, BlindablePrototypeComponent,
        BloodstreamComponent, BodyComponent, ComplexInteractionComponent, EquipmentSlotsPrototypeComponent,
        FlammablePrototypeComponent, HandsPrototypeComponent, HungerPrototypeComponent, InitialBodyComponent,
        MetabolizerPrototypeComponent, MovementSpeedModifierComponent, NoSlipComponent, ReactiveComponent,
        RespiratorComponent, SpeechComponent, StandingStateComponent, StomachPrototypeComponent, StunnableComponent,
        TemperatureComponent, TemperatureDamageComponent, ThermalRegulatorComponent, ThirstPrototypeComponent {
    String type();
}
