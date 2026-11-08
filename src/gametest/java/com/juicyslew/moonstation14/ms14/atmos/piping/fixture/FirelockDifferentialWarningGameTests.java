package com.juicyslew.moonstation14.ms14.atmos.piping.fixture;

import com.juicyslew.moonstation14.gametest.support.BoundedGameTestWait;
import com.juicyslew.moonstation14.gametest.support.GameTestTags;
import com.juicyslew.moonstation14.ms14.atmos.piping.alarm.AirAlarmBlockEntity;
import com.juicyslew.moonstation14.ms14.devicenet.DeviceNetworkRuntime;
import com.juicyslew.moonstation14.ms14.doors.FirelockBlock;
import com.juicyslew.moonstation14.ms14.doors.FirelockBlockEntity;
import com.juicyslew.moonstation14.ms14.power.runtime.PowerRuntime;
import com.juicyslew.moonstation14.ms14.doors.client.FirelockFramePlanner;
import com.juicyslew.moonstation14.ms14.doors.FirelockController;
import com.juicyslew.moonstation14.block.block_entity.PowerDeviceBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** End-to-end finite fixture coverage for independently resolved firelock differential warnings. */
@GameTestTags({"firelock_fixture", "automatic_atmos_firelocks"})
@PrefixGameTestTemplate(false)
public final class FirelockDifferentialWarningGameTests {
    private static final BlockPos LOCAL = new BlockPos(1, 1, 1);
    private FirelockDifferentialWarningGameTests() { }

    // Max bounded phases: endpoints 25 + paid 120 + danger close 100 + three warning samples 3x100
    // + APC off/on 2x100 + normal recovery 100 = 865; 900 outer ticks leave 35 setup slack.
    @GameTest(templateNamespace="moonstation14", template="atmos_large_empty", batch="automatic_atmos_firelocks", timeoutTicks=900)
    public static void differentialWarningsUseLiveAlarmAndFirelock(GameTestHelper h) {
        for(int x=0;x<VentilationFixturePlan.WIDTH;x++) for(int y=0;y<VentilationFixturePlan.HEIGHT;y++)
            for(int z=0;z<VentilationFixturePlan.DEPTH;z++) h.setBlock(LOCAL.offset(x,y,z), Blocks.AIR);
        BlockPos origin=h.absolutePos(LOCAL);
        String built=VentilationFixtureService.buildFirelock(h.getLevel(),origin);
        require(built.startsWith("Built finite ventilation fixture"),"buildFirelock actualReturn="+built);
        checkRooflights(h,origin);
        awaitEndpoints(h,origin,0);
    }

    private static void awaitEndpoints(GameTestHelper h,BlockPos o,int n){
        var plan=VentilationFixturePlan.firelockPlan(); var level=h.getLevel();
        BlockPos door=o.offset(plan.firelock()), alarm=o.offset(plan.alarm()), vent=o.offset(plan.vent());
        boolean ready=level.getBlockEntity(door) instanceof FirelockBlockEntity f
                &&DeviceNetworkRuntime.forLevel(level).transport().lookupAt(door).size()==1
                &&level.getBlockEntity(alarm) instanceof AirAlarmBlockEntity a
                &&DeviceNetworkRuntime.forLevel(level).transport().lookupAt(alarm).stream().anyMatch(e->e.identity().value().equals(a.controllerId()))
                &&level.getBlockEntity(vent) instanceof com.juicyslew.moonstation14.ms14.atmos.piping.VentilationDeviceBlockEntity v
                &&DeviceNetworkRuntime.forLevel(level).transport().lookupAt(vent).stream().anyMatch(e->e.identity().value().equals(v.deviceId()))
                &&DeviceNetworkRuntime.forLevel(level).transport().lookupAt(door).stream().anyMatch(e->e.identity().value().equals(f.identity()));
        if(ready){String initialized=VentilationFixtureService.initializeFirelock(level,o);
            require(initialized.startsWith("Initialized connected firelock"),"initialize actualReturn="+initialized);
            checkInitializedCells(h,o);
            awaitPaid(h,o,0);return;}
        if(n>=25)throw fail("endpoint deadline alarm="+DeviceNetworkRuntime.forLevel(level).transport().lookupAt(alarm).size()+" vent="+DeviceNetworkRuntime.forLevel(level).transport().lookupAt(vent).size()+" door="+DeviceNetworkRuntime.forLevel(level).transport().lookupAt(door).size());
        BoundedGameTestWait.safeDelay(h,1,()->awaitEndpoints(h,o,n+1));
    }
    private static void awaitPaid(GameTestHelper h,BlockPos o,int n){
        var level=h.getLevel();var plan=VentilationFixturePlan.firelockPlan();
        var a=(AirAlarmBlockEntity)level.getBlockEntity(o.offset(plan.alarm()));
        var d=(FirelockBlockEntity)level.getBlockEntity(o.offset(plan.firelock()));
        var v=(com.juicyslew.moonstation14.ms14.atmos.piping.VentilationDeviceBlockEntity)level.getBlockEntity(o.offset(plan.vent()));
        var am=PowerRuntime.airAlarmMeter(level,a);var vm=PowerRuntime.ventilationMeter(level,v);var fm=d.powerObservation();
        if(am.known()&&am.deliveredWatts()==a.prototype().apcPowerReceiver().requestedWatts()&&vm.known()&&vm.deliveredWatts()==v.prototype().apcPowerReceiver().requestedWatts()&&fm.known()&&fm.deliveredWatts()==d.prototype().apcPowerReceiver().requestedWatts()){
            String result=VentilationFixtureService.firelockScenario(level,o,"pressure_danger");require(result.startsWith("Applied pressure_danger"),"pressure danger setup actualReturn="+result);awaitClosed(h,o,0);return;}
        if(n>=120)throw fail("paid-load deadline alarm="+am+" vent="+vm+" door="+fm);
        BoundedGameTestWait.safeDelay(h,1,()->awaitPaid(h,o,n+1));
    }
    private static void awaitClosed(GameTestHelper h,BlockPos o,int n){
        var level=h.getLevel();var plan=VentilationFixturePlan.firelockPlan();var door=o.offset(plan.firelock());var d=(FirelockBlockEntity)level.getBlockEntity(door);
        if(d.state()!=null&&d.state().phase()==com.juicyslew.moonstation14.ms14.doors.AirlockState.DoorPhase.CLOSED&&!level.getBlockState(door).getValue(FirelockBlock.OPEN)){
            String result=VentilationFixtureService.firelockScenario(level,o,"pressure_differential");
            require(result.startsWith("Applied pressure_differential"),"pressure differential actualReturn="+result);
            assertDeltas(level,o,d,"pressure_differential");
            awaitDifferential(h,o,true,false,0);return;
        }
        if(n>=100)throw fail("normal-loop closure deadline serviceReturn=uniform_pressure_danger phase="+(d.state()==null?null:d.state().phase()));
        BoundedGameTestWait.safeDelay(h,1,()->awaitClosed(h,o,n+1));
    }
    private static void awaitDifferential(GameTestHelper h,BlockPos o,boolean pressure,boolean temperature,int n){
        var level=h.getLevel();var plan=VentilationFixturePlan.firelockPlan();var door=o.offset(plan.firelock());var d=(FirelockBlockEntity)level.getBlockEntity(door);
        var s=d.state();
        if(s!=null&&s.pressureWarning()==pressure&&s.temperatureWarning()==temperature){
            require(s.phase()==com.juicyslew.moonstation14.ms14.doors.AirlockState.DoorPhase.CLOSED,"differential retains exact closed latch");
            require(!d.activate(false),"ordinary open denied while differential lock is active");
            if(pressure&&!temperature){String r=VentilationFixtureService.firelockScenario(level,o,"temperature_differential");require(r.startsWith("Applied temperature_differential"),"temperature differential actualReturn="+r);assertDeltas(level,o,d,"temperature_differential");awaitDifferential(h,o,false,true,0);return;}
            if(!pressure&&temperature){String r=VentilationFixtureService.firelockScenario(level,o,"combined_differential");require(r.startsWith("Applied combined_differential"),"combined differential actualReturn="+r);assertDeltas(level,o,d,"combined_differential");awaitDifferential(h,o,true,true,0);return;}
            require(s.pressureWarning()&&s.temperatureWarning(),"combined live flags before power-off");
            String off=VentilationFixtureService.setFirelockPower(level,o,false);require(off.equals("Fixture APC turned off; normal allocation and paid-load telemetry remain authoritative."),"APC off actualReturn="+off);awaitOff(h,o,0);return;
        }
        if(n>=100)throw fail("warning sample deadline expected pressure="+pressure+" temperature="+temperature+" actual="+s);
        BoundedGameTestWait.safeDelay(h,1,()->awaitDifferential(h,o,pressure,temperature,n+1));
    }
    private static void assertDeltas(net.minecraft.server.level.ServerLevel l,BlockPos o,FirelockBlockEntity d,String scenario){
        var plan=VentilationFixturePlan.firelockPlan();var f=d.prototype();
        var facing=l.getBlockState(o.offset(plan.firelock())).getValue(FirelockBlock.FACING);
        var left=FirelockController.sampleEnvironment(l,o.offset(plan.firelock()).relative(facing.getOpposite()));
        var right=FirelockController.sampleEnvironment(l,o.offset(plan.firelock()).relative(facing));
        require(left.observation()==com.juicyslew.moonstation14.ms14.doors.FirelockState.Observation.AVAILABLE&&right.observation()==com.juicyslew.moonstation14.ms14.doors.FirelockState.Observation.AVAILABLE,"known sampled side atmospheres left="+left+" right="+right);
        double dp=Math.abs(right.pressureKpa()-left.pressureKpa()),dt=Math.abs(right.temperatureK()-left.temperatureK());
        boolean ep=!scenario.equals("temperature_differential"),et=!scenario.equals("pressure_differential");
        require((dp>f.pressureDifferenceKpa())==ep&& (dt>f.temperatureDifferenceK())==et,"actual adjacent sampled deltas dp="+dp+" resolvedP="+f.pressureDifferenceKpa()+" dt="+dt+" resolvedT="+f.temperatureDifferenceK()+" scenario="+scenario);
    }
    private static void checkRooflights(GameTestHelper h,BlockPos o){
        var p=VentilationFixturePlan.firelockPlan();int count=0;
        for(var cell:p.blocks())if(cell.role()==VentilationFixturePlan.BlockRole.LIGHT){var pos=o.offset(cell.x(),cell.y(),cell.z());var state=h.getLevel().getBlockState(pos);require(state.is(Blocks.SEA_LANTERN),"planned fixture light is SEA_LANTERN at "+pos);require(state.getLightEmission(h.getLevel(),pos)>0,"planned rooflight emits at "+pos);count++;}
        require(count==3,"exact three planned rooflights, actual="+count);
    }
    private static void checkInitializedCells(GameTestHelper h,BlockPos o){
        var p=VentilationFixturePlan.firelockPlan();int count=0;
        for(var pos:p.firelockConnectedCells()){var e=com.juicyslew.moonstation14.ms14.atmos.world.AtmosphereService.INSTANCE.finiteRoomEndpointForTrustedSimulation(h.getLevel(),o.offset(pos));require(e.isPresent()&&e.get().snapshot()!=null,"initialized finite enclosure cell="+pos);count++;}
        require(count==46,"complete 46-cell finite enclosure actual="+count);
    }
    private static void awaitOff(GameTestHelper h,BlockPos o,int n){
        var l=h.getLevel();var p=VentilationFixturePlan.firelockPlan();var d=(FirelockBlockEntity)l.getBlockEntity(o.offset(p.firelock()));var a=(AirAlarmBlockEntity)l.getBlockEntity(o.offset(p.alarm()));var v=(com.juicyslew.moonstation14.ms14.atmos.piping.VentilationDeviceBlockEntity)l.getBlockEntity(o.offset(p.vent()));var apc=(PowerDeviceBlockEntity)l.getBlockEntity(o.offset(p.apc()));var am=PowerRuntime.airAlarmMeter(l,a);var vm=PowerRuntime.ventilationMeter(l,v);var fm=d.powerObservation();
        if(!apc.breakerClosed()&&!am.known()&&!vm.known()&&fm.known()&&fm.deliveredWatts()==0){var view=d.firelockPresentation();var plan=FirelockFramePlanner.plan(view,d.prototype(),l.getGameTime());require(view!=null&&view.state().pressureWarning()&&view.state().temperatureWarning(),"unpowered consumed snapshot retains both warnings");require(plan!=null&&plan.indicator()!=FirelockFramePlanner.Indicator.DARK,"closed warning planner remains lit while APC off: "+plan);String on=VentilationFixtureService.setFirelockPower(l,o,true);require(on.equals("Fixture APC turned on; normal allocation and paid-load telemetry remain authoritative."),"APC restore actualReturn="+on);awaitOn(h,o,0);return;}
        if(n>=100)throw fail("APC-off deadline alarm="+am+" vent="+vm+" door="+fm+" breaker="+apc.breakerClosed());BoundedGameTestWait.safeDelay(h,1,()->awaitOff(h,o,n+1));
    }
    private static void awaitOn(GameTestHelper h,BlockPos o,int n){var l=h.getLevel();var p=VentilationFixturePlan.firelockPlan();var a=(AirAlarmBlockEntity)l.getBlockEntity(o.offset(p.alarm()));var v=(com.juicyslew.moonstation14.ms14.atmos.piping.VentilationDeviceBlockEntity)l.getBlockEntity(o.offset(p.vent()));var d=(FirelockBlockEntity)l.getBlockEntity(o.offset(p.firelock()));var am=PowerRuntime.airAlarmMeter(l,a);var vm=PowerRuntime.ventilationMeter(l,v);var fm=d.powerObservation();if(am.known()&&am.deliveredWatts()==a.prototype().apcPowerReceiver().requestedWatts()&&vm.known()&&vm.deliveredWatts()==v.prototype().apcPowerReceiver().requestedWatts()&&fm.known()&&fm.deliveredWatts()==d.prototype().apcPowerReceiver().requestedWatts()){String r=VentilationFixtureService.resetFirelock(l,o);require(r.startsWith("Reset using trusted operator"),"firelock recovery actualReturn="+r);awaitNormal(h,o,0);return;}if(n>=120)throw fail("fresh paid allocation deadline alarm="+am+" vent="+vm+" door="+fm);BoundedGameTestWait.safeDelay(h,1,()->awaitOn(h,o,n+1));}
    private static void awaitNormal(GameTestHelper h,BlockPos o,int n){
        var level=h.getLevel();var p=VentilationFixturePlan.firelockPlan();var d=(FirelockBlockEntity)level.getBlockEntity(o.offset(p.firelock()));var s=d.state();
        if(s!=null&&!s.pressureWarning()&&!s.temperatureWarning()&&s.phase()==com.juicyslew.moonstation14.ms14.doors.AirlockState.DoorPhase.OPEN){h.succeed();return;}
        if(n>=100)throw fail("normal recovery deadline state="+s);
        BoundedGameTestWait.safeDelay(h,1,()->awaitNormal(h,o,n+1));
    }
    private static void require(boolean b,String m){if(!b)throw fail(m);} private static GameTestAssertException fail(String m){return new GameTestAssertException(m);}
}
