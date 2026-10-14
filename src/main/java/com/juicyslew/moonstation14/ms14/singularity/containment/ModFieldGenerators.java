package com.juicyslew.moonstation14.ms14.singularity.containment;

import com.juicyslew.moonstation14.ms14.prototype.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;

public final class ModFieldGenerators {
    public static final ResourceLocation BASE_ID = ResourceLocation.fromNamespaceAndPath("moonstation14", "field_generator_base");
    public static final ResourceLocation CONTAINMENT_ID = ResourceLocation.fromNamespaceAndPath("moonstation14", "containment_field_generator");
    public static final PrototypeType<FieldGeneratorProfile> TYPE = new PrototypeType<>(
            ResourceLocation.fromNamespaceAndPath("moonstation14", "field_generator"),
            "moonstation14/field_generator", FieldGeneratorProfile.CODEC);
    private ModFieldGenerators() {}
    public static PrototypeCatalog<FieldGeneratorProfile> catalog(Level level) {
        return (level.isClientSide() ? PrototypeRuntime.clientManager() : PrototypeRuntime.serverManager()).snapshot(TYPE);
    }
}

