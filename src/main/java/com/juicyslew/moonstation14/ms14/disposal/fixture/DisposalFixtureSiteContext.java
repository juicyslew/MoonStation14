package com.juicyslew.moonstation14.ms14.disposal.fixture;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Runtime-only last successful fixture site, isolated by operator and dimension. */
final class DisposalFixtureSiteContext {
    private final Map<Key, BlockPos> sites = new HashMap<>();
    void remember(UUID operator, ResourceKey<Level> dimension, BlockPos entry) { sites.put(new Key(operator, dimension), entry.immutable()); }
    void buildResult(UUID operator, ResourceKey<Level> dimension, BlockPos entry, boolean successful) { if(successful) remember(operator, dimension, entry); }
    BlockPos find(UUID operator, ResourceKey<Level> dimension) { return sites.get(new Key(operator, dimension)); }
    void clear() { sites.clear(); }
    private record Key(UUID operator, ResourceKey<Level> dimension) { }
}
