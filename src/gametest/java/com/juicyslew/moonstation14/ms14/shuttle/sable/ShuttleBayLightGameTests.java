package com.juicyslew.moonstation14.ms14.shuttle.sable;

import com.juicyslew.moonstation14.gametest.support.GameTestTags;
import com.juicyslew.moonstation14.ms14.shuttle.fixture.ShuttleManualFixture;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.LightLayer;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import org.joml.Vector3d;

/** Normal native light ownership through the exact already-loaded manual fixture plot. */
@GameTestTags("shuttle-bay-light")
@PrefixGameTestTemplate(false)
public final class ShuttleBayLightGameTests {
    private ShuttleBayLightGameTests() { }

    @GameTest(templateNamespace="moonstation14",template="empty",batch="shuttle_bay_light",timeoutTicks=240)
    @GameTestTags("shuttle-bay-light")
    public static void manualFixtureLightIsNormalEmissionAndIdempotent(GameTestHelper helper) {
        var level=helper.getLevel(); var origin=new BlockPos(4300,300,600);
        var region=SableFixtureScene.acquire(helper,new BlockPos(4276,300,576),48,ShuttleManualFixture.HEIGHT,48);
        SableFixtureScene.awaitEmpty(helper,region,40,()->{
            SableFixtureScene.authorStoneSupportPad(level,region);
            var authored=SableFixturePlot.allocateAndAuthor(level,new Vector3d(origin.getX()+.5,origin.getY(),origin.getZ()+.5),plot->{
                var storage=plot.storageOrigin();
                plot.ensureStorageFootprint(storage,ShuttleManualFixture.WIDTH,ShuttleManualFixture.HEIGHT,ShuttleManualFixture.DEPTH);
                return ShuttleManualFixture.build(level,storage,plot.engineId(),plot::setStorageBlock);
            });
            var plot=authored.plot(); SableFixtureScene.trackPlot(helper,plot);
            var bay=plot.storageOrigin().offset(14,6,6);
            var state=level.getBlockState(bay);
            if(!state.is(Blocks.SEA_LANTERN)||state.getLightEmission()!=15)
                throw new GameTestAssertException("manual shuttle bay lacks full-emission sea lantern: "+state);
            if(!plot.setStorageBlock(bay,com.juicyslew.moonstation14.block.ModBlocks.STEEL_WALL_BLOCK.get().defaultBlockState())
                    ||!level.getBlockState(bay).is(com.juicyslew.moonstation14.block.ModBlocks.STEEL_WALL_BLOCK.get())
                    ||level.getBlockState(bay).getLightEmission()!=0)
                throw new GameTestAssertException("could not prepare exact owned manual-ceiling steel retrofit cell");
            var source=level.getServer().createCommandSourceStack().withLevel(level).withPermission(2);
            var dispatcher=level.getServer().getCommands().getDispatcher();
            String command="ms14 shuttle_fixture bay_light "+plot.engineId();
            if(dispatch(dispatcher,command,source)!=1||!level.getBlockState(bay).is(Blocks.SEA_LANTERN)
                    ||level.getBlockState(bay).getLightEmission()!=15)
                throw new GameTestAssertException("actual bay_light command did not replace the owned steel with emission-15 lantern");
            if(dispatch(dispatcher,command,source)!=1||!level.getBlockState(bay).is(Blocks.SEA_LANTERN)
                    ||level.getBlockState(bay).getLightEmission()!=15)
                throw new GameTestAssertException("actual bay_light command was not idempotent");
            BlockPos interior=bay.below();
            com.juicyslew.moonstation14.gametest.support.BoundedGameTestWait.await(helper,30,
                    ()->"bay light did not propagate block light into bounded manual interior",
                    ()->level.getBrightness(LightLayer.BLOCK,interior)>0,
                    ()->helper.succeed());
        });
    }

    private static int dispatch(com.mojang.brigadier.CommandDispatcher<net.minecraft.commands.CommandSourceStack> dispatcher,
            String command,net.minecraft.commands.CommandSourceStack source) {
        try { return dispatcher.execute(command,source); }
        catch(com.mojang.brigadier.exceptions.CommandSyntaxException failure) {
            throw new GameTestAssertException("command dispatch failed: "+command+": "+failure.getMessage());
        }
    }
}
