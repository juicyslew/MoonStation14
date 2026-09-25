package com.juicyslew.moonstation14.ms14.atmos.world;

import com.juicyslew.moonstation14.ms14.atmos.core.GasMixture;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

import java.util.Objects;
import java.util.Set;

/** Dimension ambient defaults. Ambient is a boundary condition, not a gas reservoir. */
public final class AtmosphereProfiles {
    private AtmosphereProfiles() { }

    /** All dimensions, vanilla or custom, are breathable unless explicitly opted in to vacuum. */
    public static GasMixture ambient(ResourceKey<Level> dimension) {
        Objects.requireNonNull(dimension, "dimension");
        return GasMixture.breathableAir();
    }

    /** Ambient selected by an explicit caller-owned policy; no global mutable dimension list is kept. */
    public static GasMixture ambient(ResourceKey<Level> dimension, Set<ResourceKey<Level>> vacuumDimensions) {
        return vacuumDimension(dimension, vacuumDimensions) ? GasMixture.vacuum() : ambient(dimension);
    }

    public static boolean vacuumDimension(ResourceKey<Level> dimension, Set<ResourceKey<Level>> vacuumDimensions) {
        return Objects.requireNonNull(vacuumDimensions, "vacuumDimensions").contains(Objects.requireNonNull(dimension, "dimension"));
    }
}
