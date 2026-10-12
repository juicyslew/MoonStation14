package com.juicyslew.moonstation14.ms14.doors;

import com.juicyslew.moonstation14.gametest.support.GameTestTags;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Native server integration for the real two-tile vertical airlock motion/collision contract. */
@GameTestTags({"airlock-vertical-model", "airlock"})
@PrefixGameTestTemplate(false)
public final class AirlockVerticalModelGameTests {
    private AirlockVerticalModelGameTests() { }

    @GameTest(templateNamespace="moonstation14",template="power_live_chain_clear",batch="airlock_core",timeoutTicks=300)
    public static void poweredVerticalDoorMovesAndSynchronizesBothTiles(GameTestHelper helper) {
        AirlockCoreGameTests.Fixture fixture=AirlockCoreGameTests.poweredAirlock(helper);
        ServerLevel level=fixture.level();
        AirlockBlockEntity door=fixture.door();
        BlockPos root=door.getBlockPos();
        var data=door.prototype();
        require(data!=null,"resolved vertical door prototype");
        require(data.door().openTimeOneSeconds()>0&&data.door().openTimeTwoSeconds()>0
                &&data.door().closeTimeOneSeconds()>0&&data.door().closeTimeTwoSeconds()>0,"resolved motion durations");
        AirlockCoreGameTests.waitForFullPower(helper,fixture,120,()->{
            require(upperResolvesToRoot(level,door,root.above()),"upper tile resolves to root controller identity");
            require(door.activate(),"powered controller starts opening");
            com.juicyslew.moonstation14.gametest.support.BoundedGameTestWait.safeDelay(helper, 2,()->{
                var view=door.clientView();
                require(view.phase()==AirlockState.DoorPhase.OPENING,"opening first phase is live");
                double q=AirlockGeometry.openness(view.phase(),(level.getGameTime()-view.transitionStartedAt())/20.0,
                        data.door().openTimeOneSeconds(),data.door().openTimeTwoSeconds(),
                        data.door().closeTimeOneSeconds(),data.door().closeTimeTwoSeconds());
                require(q>0&&q<1,"canonical leaf geometry is partway through travel");
                require(!view.passable(),"first opening phase remains blocking");
                require(!com.juicyslew.moonstation14.ms14.atmos.world.AtmosphereTopology.isPassable(level,root)
                                &&!com.juicyslew.moonstation14.ms14.atmos.world.AtmosphereTopology.isPassable(level,root.above()),
                        "both cells stay sealed before passability boundary");
            });
            com.juicyslew.moonstation14.gametest.support.BoundedGameTestWait.safeDelay(helper, (long)Math.ceil(data.door().openTimeOneSeconds()*20)+1,()->{
                var view=door.clientView();
                require(view.passable(),"opening boundary becomes passable");
                require(level.getBlockState(root).getValue(AirlockBlock.OPEN)
                                &&level.getBlockState(root.above()).getValue(AirlockBlock.OPEN),"both tile states open together");
                require(com.juicyslew.moonstation14.ms14.atmos.world.AtmosphereTopology.isPassable(level,root)
                                &&com.juicyslew.moonstation14.ms14.atmos.world.AtmosphereTopology.isPassable(level,root.above()),
                        "both cells pass atmosphere at opening boundary");
                var frame=AirlockGeometry.frame();
                require(!frame.isEmpty(),"authored fixed frame geometry remains present");
                double q=AirlockGeometry.openness(view.phase(),(level.getGameTime()-view.transitionStartedAt())/20.0,
                        data.door().openTimeOneSeconds(),data.door().openTimeTwoSeconds(),
                        data.door().closeTimeOneSeconds(),data.door().closeTimeTwoSeconds());
                var aperture=new AirlockGeometry.Box(1.0/16,1.0/16,7.0/16,15.0/16,31.0/16,9.0/16);
                require(AirlockGeometry.leaves(q).stream().noneMatch(box->intersects(box,aperture)),
                        "live fully raised leaves clear the authored doorway aperture");
            });
            com.juicyslew.moonstation14.gametest.support.BoundedGameTestWait.safeDelay(helper, 24,()->{
                require(door.clientView().phase()==AirlockState.DoorPhase.OPEN,"door reaches fully open phase");
                require(upperResolvesToRoot(level,door,root.above()),"root controller identity survives motion");
                require(door.activate(),"powered controller starts closing");
                com.juicyslew.moonstation14.gametest.support.BoundedGameTestWait.safeDelay(helper,10,()->{
                    require(door.clientView().phase()==AirlockState.DoorPhase.CLOSING_PASSABLE
                                    ||door.clientView().phase()==AirlockState.DoorPhase.CLOSED,
                            "closing reaches its passability/closed boundary");
                    if(door.clientView().phase()==AirlockState.DoorPhase.CLOSED) checkClosed(level,root,door,helper);
                    else com.juicyslew.moonstation14.gametest.support.BoundedGameTestWait.safeDelay(helper,8,()->checkClosed(level,root,door,helper));
                });
            });
        });
    }

    private static void checkClosed(ServerLevel level,BlockPos root,AirlockBlockEntity door,GameTestHelper helper) {
        require(door.clientView().phase()==AirlockState.DoorPhase.CLOSED,"door completes close cycle");
        require(!door.clientView().passable(),"closed door blocks passage");
        require(!level.getBlockState(root).getValue(AirlockBlock.OPEN)
                        &&!level.getBlockState(root.above()).getValue(AirlockBlock.OPEN),"both tiles close together");
        require(!com.juicyslew.moonstation14.ms14.atmos.world.AtmosphereTopology.isPassable(level,root)
                        &&!com.juicyslew.moonstation14.ms14.atmos.world.AtmosphereTopology.isPassable(level,root.above()),
                "both cells seal after close");
        require(upperResolvesToRoot(level,door,root.above()),"same root controller remains after cycle");
        helper.succeed();
    }

    private static boolean upperResolvesToRoot(ServerLevel level,AirlockBlockEntity door,BlockPos upper) {
        var endpoint=com.juicyslew.moonstation14.ms14.devicenet.DeviceNetworkRuntime.forLevel(level)
                .transport().lookup(new com.juicyslew.moonstation14.ms14.devicenet.DeviceIdentity(door.identity()));
        return endpoint!=null&&endpoint.acceptsBlockTarget(upper)
                &&level.getBlockEntity(door.getBlockPos())==door&&level.getBlockEntity(upper)==null;
    }

    private static boolean intersects(AirlockGeometry.Box a,AirlockGeometry.Box b) {
        return a.x0()<b.x1()&&a.x1()>b.x0()&&a.y0()<b.y1()&&a.y1()>b.y0()
                &&a.z0()<b.z1()&&a.z1()>b.z0();
    }

    private static void require(boolean condition,String message) {
        if(!condition)throw new net.minecraft.gametest.framework.GameTestAssertException(message);
    }
}
