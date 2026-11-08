package com.juicyslew.moonstation14.ms14.radiation;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.chunk.LevelChunk;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.ChunkEvent;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/** Automatic loaded-level lifecycle bridge; no global source bus or forced chunks. */
@EventBusSubscriber(modid = com.juicyslew.moonstation14.MoonStation14.MOD_ID, bus = EventBusSubscriber.Bus.GAME)
public final class RadiationEventHooks {
    private RadiationEventHooks() { }
    @SubscribeEvent public static void postTick(ServerTickEvent.Post event) {
        for (ServerLevel level : event.getServer().getAllLevels()) RadiationService.INSTANCE.tick(level, level.getGameTime());
    }
    @SubscribeEvent public static void chunkUnload(ChunkEvent.Unload event) {
        if (event.getLevel() instanceof ServerLevel level && event.getChunk() instanceof LevelChunk chunk)
            RadiationService.INSTANCE.onChunkUnload(level, chunk.getPos());
    }
    @SubscribeEvent public static void levelUnload(LevelEvent.Unload event) {
        if (event.getLevel() instanceof ServerLevel level) RadiationService.INSTANCE.clear(level);
    }
}

