package com.juicyslew.moonstation14.ms14.singularity;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.juicyslew.moonstation14.ms14.prototype.PrototypeType;
import net.minecraft.resources.ResourceLocation;

/** Startup-only subset of root singulo.yml (no parent); inherited values extracted from SingularityGeneratorComponent defaults.
 * Verified at pinned SS14 Content.Server/Singularity/EntitySystems/SingularityGeneratorSystem.cs:112-159.
 */
public record SingularityGeneratorProfile(double startupThreshold, boolean startsInert,
                                          boolean failsafeEnabled, double failsafeDistance, double failsafeCooldownSeconds,
                                          ResourceLocation spawnProfile) {
    public static final double DEFAULT_THRESHOLD = 16;
    public static final double DEFAULT_FAILSAFE_DISTANCE = 16;
    public static final double DEFAULT_FAILSAFE_COOLDOWN_SECONDS = 10;
    public static final Codec<SingularityGeneratorProfile> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.DOUBLE.optionalFieldOf("startupThreshold", DEFAULT_THRESHOLD).forGetter(SingularityGeneratorProfile::startupThreshold),
            Codec.BOOL.optionalFieldOf("startsInert", false).forGetter(SingularityGeneratorProfile::startsInert),
            Codec.BOOL.optionalFieldOf("failsafeEnabled", true).forGetter(SingularityGeneratorProfile::failsafeEnabled),
            Codec.DOUBLE.optionalFieldOf("failsafeDistance", DEFAULT_FAILSAFE_DISTANCE).forGetter(SingularityGeneratorProfile::failsafeDistance),
            Codec.DOUBLE.optionalFieldOf("failsafeCooldownSeconds", DEFAULT_FAILSAFE_COOLDOWN_SECONDS).forGetter(SingularityGeneratorProfile::failsafeCooldownSeconds),
            ResourceLocation.CODEC.optionalFieldOf("spawnProfile", ResourceLocation.fromNamespaceAndPath("moonstation14", "singularity")).forGetter(SingularityGeneratorProfile::spawnProfile)
    ).apply(i, SingularityGeneratorProfile::new));
    public static final PrototypeType<SingularityGeneratorProfile> TYPE = new PrototypeType<>(
            ResourceLocation.fromNamespaceAndPath("moonstation14", "singularity_generator"),
            "moonstation14/singularity_generator", CODEC);

    public SingularityGeneratorProfile(double threshold, boolean inert, double distance, double cooldown) {
        this(threshold, inert, true, distance, cooldown, ResourceLocation.fromNamespaceAndPath("moonstation14", "singularity"));
    }

    public void validate() {
        if (!Double.isFinite(startupThreshold) || startupThreshold < 0 || !Double.isFinite(failsafeDistance) || failsafeDistance < 0
                || !Double.isFinite(failsafeCooldownSeconds) || failsafeCooldownSeconds < 0 || spawnProfile == null)
            throw new IllegalArgumentException("invalid singularity generator profile");
    }
}

