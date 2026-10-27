package com.juicyslew.moonstation14.ms14.combat;

import com.juicyslew.moonstation14.ms14.prototype.PrototypeMergeStrategy;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.ResourceLocation;
import java.util.Set;

/** Supported resolved CartridgeAmmo and Tag composition. */
public record CartridgeProfile(ResourceLocation projectile, boolean deleteOnSpawn, Set<ResourceLocation> tags) {
    public static final Codec<CartridgeProfile> CODEC = RecordCodecBuilder.create(i -> i.group(
            ResourceLocation.CODEC.fieldOf("projectile").forGetter(CartridgeProfile::projectile),
            Codec.BOOL.optionalFieldOf("delete_on_spawn", false).forGetter(CartridgeProfile::deleteOnSpawn),
            ResourceLocation.CODEC.listOf().optionalFieldOf("tags", java.util.List.of()).forGetter(v -> v.tags().stream().toList())
    ).apply(i, (p,d,t) -> new CartridgeProfile(p,d,Set.copyOf(t))));
    public CartridgeProfile { if (projectile == null) throw new IllegalArgumentException("projectile is required"); tags = Set.copyOf(tags); }
}
