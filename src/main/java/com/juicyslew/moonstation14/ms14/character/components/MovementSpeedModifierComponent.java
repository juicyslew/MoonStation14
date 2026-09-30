package com.juicyslew.moonstation14.ms14.character.components;

import com.google.gson.JsonObject;
import com.juicyslew.moonstation14.component.codec.json.CharacterSchemaAudit;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/** Grounded movement policy owned by a decoded character prototype. */
public record MovementSpeedModifierComponent(String mode, double acceleration, double walkSpeed,
                                             double sprintSpeed, double groundFrictionWithInput,
                                             double groundFrictionWithoutInput, double minimumFrictionSpeed)
        implements CharacterComponent {
    public static final String TYPE = "MovementSpeedModifier";

    @Override public String type() { return TYPE; }

    public static final Codec<MovementSpeedModifierComponent> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.STRING.fieldOf("type").forGetter(MovementSpeedModifierComponent::type),
            Codec.STRING.fieldOf("mode").forGetter(MovementSpeedModifierComponent::mode),
            Codec.DOUBLE.fieldOf("acceleration").forGetter(MovementSpeedModifierComponent::acceleration),
            Codec.DOUBLE.fieldOf("walk_speed").forGetter(MovementSpeedModifierComponent::walkSpeed),
            Codec.DOUBLE.fieldOf("sprint_speed").forGetter(MovementSpeedModifierComponent::sprintSpeed),
            Codec.DOUBLE.fieldOf("ground_friction_with_input").forGetter(MovementSpeedModifierComponent::groundFrictionWithInput),
            Codec.DOUBLE.fieldOf("ground_friction_without_input").forGetter(MovementSpeedModifierComponent::groundFrictionWithoutInput),
            Codec.DOUBLE.fieldOf("minimum_friction_speed").forGetter(MovementSpeedModifierComponent::minimumFrictionSpeed)
    ).apply(instance, (type, mode, acceleration, walkSpeed, sprintSpeed, withInput, withoutInput, minimum) -> {
        if (!TYPE.equals(type)) throw new IllegalArgumentException("expected MovementSpeedModifier component");
        return new MovementSpeedModifierComponent(mode, acceleration, walkSpeed, sprintSpeed, withInput, withoutInput, minimum);
    }));

    public MovementSpeedModifierComponent {
        JsonObject json = new JsonObject();
        json.addProperty("type", TYPE);
        json.addProperty("mode", mode);
        json.addProperty("acceleration", acceleration);
        json.addProperty("walk_speed", walkSpeed);
        json.addProperty("sprint_speed", sprintSpeed);
        json.addProperty("ground_friction_with_input", groundFrictionWithInput);
        json.addProperty("ground_friction_without_input", groundFrictionWithoutInput);
        json.addProperty("minimum_friction_speed", minimumFrictionSpeed);
        CharacterSchemaAudit.auditMovement(json, "$.components[MovementSpeedModifier]", true);
    }
}
