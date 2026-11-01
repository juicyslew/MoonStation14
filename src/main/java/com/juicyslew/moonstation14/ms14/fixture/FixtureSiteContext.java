package com.juicyslew.moonstation14.ms14.fixture;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Runtime-only last successful fixture site, isolated by operator and dimension. */
public final class FixtureSiteContext {
    private final Map<Key, BlockPos> sites = new HashMap<>();
    public void remember(UUID operator, ResourceKey<Level> dimension, BlockPos site) { sites.put(new Key(operator, dimension), site.immutable()); }
    public void buildResult(UUID operator, ResourceKey<Level> dimension, BlockPos site, boolean successful) { if (successful) remember(operator, dimension, site); }
    public BlockPos find(UUID operator, ResourceKey<Level> dimension) { return sites.get(new Key(operator, dimension)); }
    public void clear() { sites.clear(); }
    private record Key(UUID operator, ResourceKey<Level> dimension) { }
}
