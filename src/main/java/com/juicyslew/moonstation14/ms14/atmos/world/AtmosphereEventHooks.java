package com.juicyslew.moonstation14.ms14.atmos.world;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.LevelChunk;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.level.ChunkEvent;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/** NeoForge lifecycle bridge for the atmosphere runtime. */
public final class AtmosphereEventHooks {
    private AtmosphereEventHooks() { }

    public static void register(IEventBus bus) { bus.register(AtmosphereEventHooks.class); }

    @net.neoforged.bus.api.SubscribeEvent
    public static void onPostServerTick(ServerTickEvent.Post event) {
        if (!AtmosphereService.INSTANCE.isEnabled()) return;
        for (ServerLevel level : event.getServer().getAllLevels()) {
            AtmosphereService.INSTANCE.processDeferredChunkLoads(level);
            AtmosphereService.INSTANCE.tick(level, level.getGameTime());
        }
    }

    @net.neoforged.bus.api.SubscribeEvent
    public static void onChunkLoad(ChunkEvent.Load event) {
        if (!AtmosphereService.INSTANCE.isEnabled()) return;
        if (event.getLevel() instanceof ServerLevel level && event.getChunk() instanceof LevelChunk chunk)
            AtmosphereService.INSTANCE.deferChunkLoad(level, chunk.getPos());
    }

    @net.neoforged.bus.api.SubscribeEvent
    public static void onChunkUnload(ChunkEvent.Unload event) {
        if (!AtmosphereService.INSTANCE.isEnabled()) return;
        if (event.getLevel() instanceof ServerLevel level)
            AtmosphereService.INSTANCE.onChunkUnload(level, event.getChunk().getPos());
    }

    @net.neoforged.bus.api.SubscribeEvent
    public static void onLevelUnload(LevelEvent.Unload event) {
        if (event.getLevel() instanceof ServerLevel level) AtmosphereService.INSTANCE.clear(level);
    }

    @net.neoforged.bus.api.SubscribeEvent
    public static void onBlockPlaced(BlockEvent.EntityPlaceEvent event) {
        if (AtmosphereService.INSTANCE.isEnabled()) invalidate(event.getLevel(), event.getPos());
    }

    @net.neoforged.bus.api.SubscribeEvent
    public static void onBlockBroken(BlockEvent.BreakEvent event) {
        if (AtmosphereService.INSTANCE.isEnabled()) invalidate(event.getLevel(), event.getPos());
    }

    @net.neoforged.bus.api.SubscribeEvent
    public static void onNeighborNotified(BlockEvent.NeighborNotifyEvent event) {
        if (AtmosphereService.INSTANCE.isEnabled()) invalidate(event.getLevel(), event.getPos());
    }

    private static void invalidate(net.minecraft.world.level.LevelAccessor level, BlockPos pos) {
        if (level instanceof ServerLevel serverLevel) AtmosphereService.INSTANCE.invalidate(serverLevel, pos);
    }
}
