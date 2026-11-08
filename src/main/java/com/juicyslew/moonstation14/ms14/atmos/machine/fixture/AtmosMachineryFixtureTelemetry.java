package com.juicyslew.moonstation14.ms14.atmos.machine.fixture;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.AABB;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/** One bounded, read-only telemetry pass for nearby, already-loaded fixture viewers. */
@EventBusSubscriber(modid="moonstation14")
public final class AtmosMachineryFixtureTelemetry {
    private static final int CADENCE_TICKS=20;
    private static final double VIEW_MARGIN=16.0;

    private AtmosMachineryFixtureTelemetry() { }

    @SubscribeEvent public static void tick(ServerTickEvent.Post event) {
        for(ServerLevel level:event.getServer().getAllLevels()) {
            if(level.getGameTime()%CADENCE_TICKS!=0)continue;
            var data=AtmosMachineryFixtureSavedData.existing(level).orElse(null);
            if(data==null)continue;
            for(var entry:data.entries()) {
                if(entry.reservedDemos().isEmpty()||!nearbyViewer(level,entry.origin()))continue;
                AtmosMachineryFixtureService.refreshTelemetryIfLoaded(level,entry);
            }
        }
    }

    private static boolean nearbyViewer(ServerLevel level,net.minecraft.core.BlockPos origin) {
        var footprint=new AABB(origin.getX(),origin.getY(),origin.getZ(),
                origin.getX()+AtmosMachineryFixturePlan.WIDTH,origin.getY()+AtmosMachineryFixturePlan.HEIGHT,
                origin.getZ()+AtmosMachineryFixturePlan.DEPTH).inflate(VIEW_MARGIN);
        return level.players().stream().anyMatch(player->footprint.contains(player.position()));
    }
}
