package com.juicyslew.moonstation14.ms14.atmos.piping.fixture;

import com.juicyslew.moonstation14.gametest.support.BoundedGameTestWait;
import com.juicyslew.moonstation14.gametest.support.GameTestTags;
import com.juicyslew.moonstation14.ms14.atmos.piping.alarm.AirAlarmBlockEntity;
import com.juicyslew.moonstation14.ms14.atmos.monitor.AtmosAlarmSeverity;
import com.juicyslew.moonstation14.ms14.atmos.world.AtmosphereTopology;
import com.juicyslew.moonstation14.ms14.devicenet.DeviceNetworkRuntime;
import com.juicyslew.moonstation14.ms14.doors.FirelockBlock;
import com.juicyslew.moonstation14.ms14.doors.FirelockBlockEntity;
import com.juicyslew.moonstation14.ms14.doors.AirlockState;
import com.juicyslew.moonstation14.ms14.power.runtime.PowerRuntime;
import com.juicyslew.moonstation14.block.block_entity.PowerDeviceBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.UUID;

@GameTestTags({"firelock_fixture", "automatic_atmos_firelocks"})
@PrefixGameTestTemplate(false)
public final class PlayableFirelockFixtureGameTests {
    private static final BlockPos ORIGIN = new BlockPos(1, 1, 1);
    private PlayableFirelockFixtureGameTests() { }

    @GameTest(templateNamespace = "moonstation14", template = "atmos_large_empty",
            // Bounded phase maxima: endpoints 25 + initial paid 120 + off 80 + on 120
            // + warning/dwell 120 + danger 100 + reset 100 = 665 ticks; 750 leaves 85 ticks of setup slack.
            batch = "automatic_atmos_firelocks", timeoutTicks = 750)
    public static void finitePlayableFixtureWarningDangerAndReset(GameTestHelper helper) {
        var level = helper.getLevel();
        // The plan requires a fully loaded, entity-free 20x12x5 administrative footprint.
        for (int x = 0; x < VentilationFixturePlan.WIDTH; x++)
            for (int y = 0; y < VentilationFixturePlan.HEIGHT; y++)
                for (int z = 0; z < VentilationFixturePlan.DEPTH; z++)
                    helper.setBlock(ORIGIN.offset(x, y, z), Blocks.AIR);
        BlockPos absoluteOrigin = helper.absolutePos(ORIGIN);
        AABB footprint = new AABB(absoluteOrigin.getX(), absoluteOrigin.getY(), absoluteOrigin.getZ(),
                absoluteOrigin.getX() + VentilationFixturePlan.WIDTH,
                absoluteOrigin.getY() + VentilationFixturePlan.HEIGHT,
                absoluteOrigin.getZ() + VentilationFixturePlan.DEPTH);
        require(level.getEntities((net.minecraft.world.entity.Entity) null, footprint,
                net.minecraft.world.entity.Entity::isAlive).isEmpty(), "fixture footprint is entity-free before production build");
        String built = VentilationFixtureService.buildFirelock(level, helper.absolutePos(ORIGIN));
        require(built.startsWith("Built finite ventilation fixture"), "production build: " + built);
        BlockPos origin = helper.absolutePos(ORIGIN);
        var plan = VentilationFixturePlan.firelockPlan();
        BlockPos doorPos = origin.offset(plan.firelock());
        UUID identity = ((FirelockBlockEntity) level.getBlockEntity(doorPos)).identity();
        awaitEndpoints(helper, origin, doorPos, identity, 0, () -> {
            String initialized = VentilationFixtureService.initializeFirelock(level, origin);
            require(initialized.startsWith("Initialized connected firelock"),
                    "initialize owned fixture after exact endpoints register; actualReturn=" + initialized
                            + "; identity=" + identity + "; doorIdentity=" + ((FirelockBlockEntity)level.getBlockEntity(doorPos)).identity()
                            + "; alarmEndpoints=" + DeviceNetworkRuntime.forLevel(level).transport().lookupAt(origin.offset(VentilationFixturePlan.firelockPlan().alarm())).size()
                            + "; ventEndpoints=" + DeviceNetworkRuntime.forLevel(level).transport().lookupAt(origin.offset(VentilationFixturePlan.firelockPlan().vent())).size()
                            + "; doorEndpoints=" + DeviceNetworkRuntime.forLevel(level).transport().lookupAt(doorPos).size()
                            + "; initializedMarker=" + VentilationFixtureSavedData.existing(level).flatMap(d -> d.entry(origin)).map(VentilationFixtureSavedData.Entry::initialized).orElse(false));
            awaitPaid(helper, origin, doorPos, identity, 0);
        });
    }

    private static void awaitEndpoints(GameTestHelper h, BlockPos origin, BlockPos door, UUID id, int elapsed, Runnable ready) {
        var level = h.getLevel(); var plan = VentilationFixturePlan.firelockPlan();
        BlockPos alarm = origin.offset(plan.alarm()), vent = origin.offset(plan.vent());
        var transport = DeviceNetworkRuntime.forLevel(level).transport();
        var a = transport.lookupAt(alarm); var v = transport.lookupAt(vent); var d = transport.lookupAt(door);
        boolean ok = level.getBlockEntity(alarm) instanceof AirAlarmBlockEntity be && a.size() == 1
                && a.getFirst().identity().value().equals(be.controllerId()) && v.size() == 1
                && v.getFirst().identity().value().equals(((com.juicyslew.moonstation14.ms14.atmos.piping.VentilationDeviceBlockEntity)level.getBlockEntity(vent)).deviceId())
                && d.size() == 1 && d.getFirst().identity().value().equals(id);
        if (ok) { ready.run(); return; }
        if (elapsed >= 25) throw fail("exact placed endpoint lifecycle deadline: alarm="+a.size()+", vent="+v.size()+", door="+d.size());
        BoundedGameTestWait.safeDelay(h, 1, () -> awaitEndpoints(h, origin, door, id, elapsed + 1, ready));
    }

    private static void awaitPaid(GameTestHelper h, BlockPos origin, BlockPos door, UUID id, int elapsed) {
        var level=h.getLevel(); require(id.equals(((FirelockBlockEntity)level.getBlockEntity(door)).identity()), "firelock identity stable");
        var alarm=(AirAlarmBlockEntity)level.getBlockEntity(origin.offset(VentilationFixturePlan.firelockPlan().alarm()));
        var meter=PowerRuntime.airAlarmMeter(level,alarm);
        var firelock=(FirelockBlockEntity)level.getBlockEntity(door);
        var vent=(com.juicyslew.moonstation14.ms14.atmos.piping.VentilationDeviceBlockEntity)
                level.getBlockEntity(origin.offset(VentilationFixturePlan.firelockPlan().vent()));
        var ventMeter=PowerRuntime.ventilationMeter(level,vent);
        boolean paid=meter.known() && meter.deliveredWatts()==alarm.prototype().apcPowerReceiver().requestedWatts()
                && ventMeter.known() && ventMeter.deliveredWatts()==vent.prototype().apcPowerReceiver().requestedWatts()
                && firelock.powerObservation().known() && firelock.powerObservation().deliveredWatts()==firelock.prototype().apcPowerReceiver().requestedWatts();
        if(paid){
            require(VentilationFixtureService.setFirelockPower(level,origin,false).equals("Fixture APC turned off; normal allocation and paid-load telemetry remain authoritative."),"turn exact fixture APC off");
            awaitOff(h,origin,door,id,0);return;
        }
        if(elapsed>=120)throw fail("finite fixture power deadline: alarm="+meter+", vent="+ventMeter+", door="+firelock.powerObservation());
        BoundedGameTestWait.safeDelay(h,1,()->awaitPaid(h,origin,door,id,elapsed+1));
    }

    private static void awaitOff(GameTestHelper h,BlockPos origin,BlockPos door,UUID id,int elapsed){
        var level=h.getLevel();var plan=VentilationFixturePlan.firelockPlan();
        var alarm=(AirAlarmBlockEntity)level.getBlockEntity(origin.offset(plan.alarm()));
        var vent=(com.juicyslew.moonstation14.ms14.atmos.piping.VentilationDeviceBlockEntity)level.getBlockEntity(origin.offset(plan.vent()));
        var firelock=(FirelockBlockEntity)level.getBlockEntity(door);
        var apc=(PowerDeviceBlockEntity)level.getBlockEntity(origin.offset(plan.apc()));
        var am=PowerRuntime.airAlarmMeter(level,alarm);var vm=PowerRuntime.ventilationMeter(level,vent);var fm=firelock.powerObservation();
        // Breaker revision invalidates both meters; require that stale snapshots are
        // unknown, while the receiver explicitly reports known physical zero.
        if(!apc.breakerClosed()&&!am.known()&&!vm.known()&&fm.known()&&fm.deliveredWatts()==0){
            require(VentilationFixtureService.setFirelockPower(level,origin,true).equals("Fixture APC turned on; normal allocation and paid-load telemetry remain authoritative."),"turn exact fixture APC on");
            awaitOn(h,origin,door,id,0);return;
        }
        if(elapsed>=80)throw fail("APC off observation deadline: alarm="+am+", vent="+vm
                +" (unknown means invalidated/unusable, not known physical zero), door="+fm+", breaker="+apc.breakerClosed());
        BoundedGameTestWait.safeDelay(h,1,()->awaitOff(h,origin,door,id,elapsed+1));
    }

    private static void awaitOn(GameTestHelper h,BlockPos origin,BlockPos door,UUID id,int elapsed){
        var level=h.getLevel();var plan=VentilationFixturePlan.firelockPlan();
        var alarm=(AirAlarmBlockEntity)level.getBlockEntity(origin.offset(plan.alarm()));
        var vent=(com.juicyslew.moonstation14.ms14.atmos.piping.VentilationDeviceBlockEntity)level.getBlockEntity(origin.offset(plan.vent()));
        var firelock=(FirelockBlockEntity)level.getBlockEntity(door);
        var apc=(PowerDeviceBlockEntity)level.getBlockEntity(origin.offset(plan.apc()));
        var am=PowerRuntime.airAlarmMeter(level,alarm);var vm=PowerRuntime.ventilationMeter(level,vent);var fm=firelock.powerObservation();
        boolean paid=am.known()&&am.deliveredWatts()==alarm.prototype().apcPowerReceiver().requestedWatts()
                &&vm.known()&&vm.deliveredWatts()==vent.prototype().apcPowerReceiver().requestedWatts()
                &&fm.known()&&fm.deliveredWatts()==firelock.prototype().apcPowerReceiver().requestedWatts();
        if(apc.breakerClosed()&&paid){
            require(VentilationFixtureService.firelockScenario(level,origin,"pressure_warning").startsWith("Applied pressure_warning"),"apply finite warning gas");
            awaitWarning(h,origin,door,id,0,0);return;
        }
        if(elapsed>=120)throw fail("APC on full paid observation deadline: alarm="+am+", vent="+vm+", door="+fm+", breaker="+apc.breakerClosed());
        BoundedGameTestWait.safeDelay(h,1,()->awaitOn(h,origin,door,id,elapsed+1));
    }

    private static void awaitWarning(GameTestHelper h,BlockPos origin,BlockPos door,UUID id,int elapsed,int dwell){
        if(elapsed>=120)throw fail("warning and 20-tick dwell phase deadline");
        var level=h.getLevel(); var alarm=(AirAlarmBlockEntity)level.getBlockEntity(origin.offset(VentilationFixturePlan.firelockPlan().alarm()));
        var firelock=(FirelockBlockEntity)level.getBlockEntity(door);
        DeviceNetworkRuntime.forLevel(level).gameTick(); DeviceNetworkRuntime.forLevel(level).update();
        if(alarm.alarmSeverity()==AtmosAlarmSeverity.WARNING){
            require(id.equals(firelock.identity())&&level.getBlockState(door).getValue(FirelockBlock.OPEN),"warning leaves standalone door open");
            var shape=level.getBlockState(door).getCollisionShape(level,door,CollisionContext.empty());
            BlockPos upper=door.above();
            require(shape.isEmpty()&&level.getBlockState(upper).getCollisionShape(level,upper,CollisionContext.empty()).isEmpty()
                    &&AtmosphereTopology.isPassable(level,door)&&AtmosphereTopology.isPassable(level,upper),"open lower/upper door collision is clear");
            // Atmosphere sampling is every 2 ticks and paid APC observations resolve every 20;
            // hold the observed warning through a full 20-tick receiver/alarm polling cycle.
            if(dwell >= 20){
                require(VentilationFixtureService.firelockScenario(level,origin,"pressure_danger").startsWith("Applied pressure_danger"),"apply finite danger gas after warning dwell");
                awaitDanger(h,origin,door,id,0);return;
            }
            BoundedGameTestWait.safeDelay(h,1,()->awaitWarning(h,origin,door,id,elapsed+1,dwell+1));return;
        }
        if(elapsed>=100)throw fail("warning severity deadline: "+alarm.alarmSeverity());
        BoundedGameTestWait.safeDelay(h,1,()->awaitWarning(h,origin,door,id,elapsed+1,0));
    }

    private static void awaitDanger(GameTestHelper h,BlockPos origin,BlockPos door,UUID id,int elapsed){
        var level=h.getLevel(); var alarm=(AirAlarmBlockEntity)level.getBlockEntity(origin.offset(VentilationFixturePlan.firelockPlan().alarm()));
        var firelock=(FirelockBlockEntity)level.getBlockEntity(door);
        DeviceNetworkRuntime.forLevel(level).gameTick(); DeviceNetworkRuntime.forLevel(level).update();
        if(alarm.alarmSeverity()==AtmosAlarmSeverity.DANGER&&!level.getBlockState(door).getValue(FirelockBlock.OPEN)
                &&firelock.state()!=null&&firelock.state().phase()==AirlockState.DoorPhase.CLOSED){
            require(id.equals(firelock.identity()),"danger closure retains exact firelock identity");
            require(!level.getBlockState(door).getCollisionShape(level,door,CollisionContext.empty()).isEmpty()
                    &&!level.getBlockState(door.above()).getCollisionShape(level,door.above(),CollisionContext.empty()).isEmpty()
                    &&!AtmosphereTopology.isPassable(level,door)&&!AtmosphereTopology.isPassable(level,door.above()),"closed lower/upper door blocks collision and atmosphere");
            require(VentilationFixtureService.resetFirelock(level,origin).startsWith("Reset using trusted operator"),"reset owned finite fixture");
            awaitReset(h,origin,door,id,0);return;
        }
        if(elapsed>=100)throw fail("danger did not close through alarm loop: severity="+alarm.alarmSeverity()+", open="+level.getBlockState(door).getValue(FirelockBlock.OPEN));
        BoundedGameTestWait.safeDelay(h,1,()->awaitDanger(h,origin,door,id,elapsed+1));
    }

    private static void awaitReset(GameTestHelper h,BlockPos origin,BlockPos door,UUID id,int elapsed){
        var level=h.getLevel();var alarm=(AirAlarmBlockEntity)level.getBlockEntity(origin.offset(VentilationFixturePlan.firelockPlan().alarm()));
        DeviceNetworkRuntime.forLevel(level).gameTick();DeviceNetworkRuntime.forLevel(level).update();
        var firelock=(FirelockBlockEntity)level.getBlockEntity(door);
        if(alarm.alarmSeverity()==AtmosAlarmSeverity.NORMAL&&level.getBlockState(door).getValue(FirelockBlock.OPEN)
                &&firelock.state()!=null&&firelock.state().phase()==AirlockState.DoorPhase.OPEN){
            require(id.equals(firelock.identity()),"reset preserves exact door");
            require(level.getBlockState(door).getCollisionShape(level,door,CollisionContext.empty()).isEmpty()
                    &&level.getBlockState(door.above()).getCollisionShape(level,door.above(),CollisionContext.empty()).isEmpty()
                    &&AtmosphereTopology.isPassable(level,door)&&AtmosphereTopology.isPassable(level,door.above()),"reset open lower/upper collision/passability");h.succeed();return;
        }
        if(elapsed>=100)throw fail("normal reset did not reopen firelock: severity="+alarm.alarmSeverity());
        BoundedGameTestWait.safeDelay(h,1,()->awaitReset(h,origin,door,id,elapsed+1));
    }
    private static GameTestAssertException fail(String message){return new GameTestAssertException(message);}
    private static void require(boolean value,String message){if(!value)throw fail(message);}
}
