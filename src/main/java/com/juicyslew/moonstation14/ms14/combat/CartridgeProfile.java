package com.juicyslew.moonstation14.ms14.combat;

import com.juicyslew.moonstation14.ms14.prototype.PrototypeMergeStrategy;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.ResourceLocation;
import java.util.Set;

/** Supported resolved CartridgeAmmo and Tag composition. */
public record CartridgeProfile(ResourceLocation projectile, boolean deleteOnSpawn, Set<ResourceLocation> tags, SpentCartridgePresentation spentPresentation) {
    public static final Codec<CartridgeProfile> CODEC = RecordCodecBuilder.create(i -> i.group(
            ResourceLocation.CODEC.fieldOf("projectile").forGetter(CartridgeProfile::projectile),
            Codec.BOOL.optionalFieldOf("delete_on_spawn", false).forGetter(CartridgeProfile::deleteOnSpawn),
            ResourceLocation.CODEC.listOf().optionalFieldOf("tags", java.util.List.of()).forGetter(v -> v.tags().stream().toList()),
            SpentCartridgePresentation.CODEC.optionalFieldOf("spent_presentation").forGetter(v -> java.util.Optional.ofNullable(v.spentPresentation()))
    ).apply(i, (p,d,t,s) -> new CartridgeProfile(p,d,Set.copyOf(t),s.orElse(null))));
    public CartridgeProfile(ResourceLocation projectile, boolean deleteOnSpawn, Set<ResourceLocation> tags) { this(projectile, deleteOnSpawn, tags, null); }
    public CartridgeProfile { if (projectile == null) throw new IllegalArgumentException("projectile is required"); tags = Set.copyOf(tags); }
}
