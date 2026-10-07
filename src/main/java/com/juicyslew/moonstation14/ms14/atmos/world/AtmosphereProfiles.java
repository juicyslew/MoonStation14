package com.juicyslew.moonstation14.ms14.atmos.world;

import com.juicyslew.moonstation14.ms14.atmos.core.GasMixture;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

import java.util.Objects;
import java.util.Set;

/** Immutable dimension ambient profiles. Configured gas content is the ambient reservoir endpoint,
 * kept separate from the dimension's space classification.
 */
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

    /** Space classification is independent of the dimension's ambient gas composition. */
    public static boolean spaceDimension(ResourceKey<Level> dimension, Set<ResourceKey<Level>> spaceDimensions) {
        return Objects.requireNonNull(spaceDimensions, "spaceDimensions").contains(Objects.requireNonNull(dimension, "dimension"));
    }

    /** Ambient composition remains controlled solely by vacuum policy; space is a separate profile axis. */
    public static GasMixture ambient(ResourceKey<Level> dimension, Set<ResourceKey<Level>> vacuumDimensions,
                                     Set<ResourceKey<Level>> spaceDimensions) {
        Objects.requireNonNull(spaceDimensions, "spaceDimensions");
        return ambient(dimension, vacuumDimensions);
    }
}
