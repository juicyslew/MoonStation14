package com.juicyslew.moonstation14.ms14.singularity.pa;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.world.level.block.Rotation;

/** Resolved, typed footprint entry; coordinates are profile-local X/Y (Minecraft X/Z). */
public record ParticleAcceleratorPartProfile(String role, int offsetX, int offsetY,
                                               Rotation relativeRotation, String completedPrototype) {
    public static final Codec<ParticleAcceleratorPartProfile> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.STRING.fieldOf("role").forGetter(ParticleAcceleratorPartProfile::role),
            Codec.INT.fieldOf("offset_x").forGetter(ParticleAcceleratorPartProfile::offsetX),
            Codec.INT.fieldOf("offset_y").forGetter(ParticleAcceleratorPartProfile::offsetY),
            Codec.STRING.xmap(ParticleAcceleratorPartProfile::parseRotation, ParticleAcceleratorPartProfile::rotationName).fieldOf("relative_rotation").forGetter(ParticleAcceleratorPartProfile::relativeRotation),
            Codec.STRING.fieldOf("completed_prototype").forGetter(ParticleAcceleratorPartProfile::completedPrototype)
    ).apply(i, ParticleAcceleratorPartProfile::new));
    public ParticleAcceleratorPartProfile {
        if (role == null || role.isBlank() || relativeRotation == null || completedPrototype == null || completedPrototype.isBlank())
            throw new IllegalArgumentException("Invalid PA part profile");
    }
    private static Rotation parseRotation(String value) {
        return switch (value) {
            case "0" -> Rotation.NONE;
            case "-90", "270" -> Rotation.CLOCKWISE_90;
            case "180", "-180" -> Rotation.CLOCKWISE_180;
            case "90", "-270" -> Rotation.COUNTERCLOCKWISE_90;
            default -> throw new IllegalArgumentException("Unsupported PA rotation: " + value);
        };
    }
    private static String rotationName(Rotation value) { return Integer.toString(switch(value) {
        case NONE -> 0; case CLOCKWISE_90 -> -90; case CLOCKWISE_180 -> 180; case COUNTERCLOCKWISE_90 -> 90;
    }); }
}

