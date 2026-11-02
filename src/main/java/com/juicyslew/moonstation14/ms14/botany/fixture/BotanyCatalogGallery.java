package com.juicyslew.moonstation14.ms14.botany.fixture;

import com.juicyslew.moonstation14.block.ModBlocks;
import com.juicyslew.moonstation14.ms14.atmos.world.AtmosphereTopology;
import com.juicyslew.moonstation14.ms14.botany.HydroponicsTrayBlockEntity;
import com.juicyslew.moonstation14.ms14.botany.SmallBotanyFixtureLayout;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;

import java.util.ArrayList;
import java.util.List;

/** Runtime construction and readiness checks for the pure catalog-gallery plan. */
final class BotanyCatalogGallery {
    private BotanyCatalogGallery() { }

    static boolean compatible(BotanyCatalogGalleryPlan plan) {
        var air=BotanyFixtureCommands.freshFixtureAir();
        double pressure=air.pressureKpa(1.0);
        return plan.entries().stream().allMatch(e -> pressure>=e.profile().biology().lowPressure()
                && pressure<=e.profile().biology().highPressure()
                && air.temperatureKelvin()>=e.profile().biology().idealHeat()-e.profile().biology().heatTolerance()
                && air.temperatureKelvin()<=e.profile().biology().idealHeat()+e.profile().biology().heatTolerance());
    }

    static String build(ServerLevel level, BotanyCatalogGalleryPlan plan) {
        BlockPos blocked=BotanyFixtureCommands.firstBlockedFootprintPosition(level,plan.plannedFootprint());
        if(blocked!=null) return "Refused without changes: catalog gallery footprint occupied, out of bounds, or unloaded at "+blocked+".";
        BotanyFixtureCommands.writeRoomShell(level,plan.origin(),plan.width(),plan.depth(),plan.height());
        BotanyFixtureCommands.placeCoreMachines(level,plan.origin());
        BlockPos apc=SmallBotanyFixtureLayout.apc(plan.origin());
        String power=BotanyFixtureCommands.placeCorePower(level,plan.origin(),apc);
        if(power!=null) return power;
        String lamp=BotanyFixtureCommands.placeCoreLamp(level,plan.origin());
        if(lamp!=null) return lamp;
        for(var entry:plan.entries()) {
            level.setBlock(entry.trayPosition(),ModBlocks.HYDROPONICS_TRAY.get().defaultBlockState(),3);
        }
        BotanyFixtureSigns.placeGuides(level,BotanyFixtureSigns.catalogLabels(plan));
        for(BlockPos supply:BotanyFixtureCommands.galleryKitPositions(plan))
            level.setBlock(supply.below(),Blocks.STONE.defaultBlockState(),3);
        for(BlockPos light:plan.lightingPositions()) level.setBlock(light,Blocks.SEA_LANTERN.defaultBlockState(),3);
        BotanyFixtureCommands.chargeCoreApc(level,apc);
        return "Built catalog gallery ["+plan.origin().getX()+","+plan.origin().getY()+","+plan.origin().getZ()+"]";
    }

    static String initializeAir(ServerLevel level,BotanyCatalogGalleryPlan plan) {
        var cells=new java.util.LinkedHashSet<BlockPos>();
        BlockPos o=plan.origin();
        for(int x=1;x<plan.width()-1;x++) for(int y=1;y<plan.height()-1;y++) for(int z=1;z<plan.depth()-1;z++) {
            BlockPos pos=o.offset(x,y,z); if(AtmosphereTopology.isPassable(level,pos)) cells.add(pos);
        }
        for(var entry:plan.entries()) { BlockPos head=entry.trayPosition().above(); if(AtmosphereTopology.isPassable(level,head)) cells.add(head); }
        return BotanyFixtureCommands.initializeFiniteAir(level,List.copyOf(cells));
    }

    static boolean traysInitialized(ServerLevel level,BotanyCatalogGalleryPlan plan) {
        for(var entry:plan.entries()) if(!(level.getBlockEntity(entry.trayPosition()) instanceof HydroponicsTrayBlockEntity tray)
                || !tray.isLoadedValid() || tray.plantState()==null) return false;
        return level.getBlockEntity(SmallBotanyFixtureLayout.apc(plan.origin())) instanceof com.juicyslew.moonstation14.block.block_entity.PowerDeviceBlockEntity;
    }

    static List<BlockPos> trayPositions(BotanyCatalogGalleryPlan plan) {
        return plan.entries().stream().map(BotanyCatalogGalleryPlan.Entry::trayPosition).toList();
    }
}
