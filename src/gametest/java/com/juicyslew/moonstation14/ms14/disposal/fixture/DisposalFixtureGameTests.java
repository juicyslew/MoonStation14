package com.juicyslew.moonstation14.ms14.disposal.fixture;

import com.juicyslew.moonstation14.MoonStation14;
import com.juicyslew.moonstation14.block.ModBlocks;
import com.juicyslew.moonstation14.block.custom.PowerDeviceBlock;
import com.juicyslew.moonstation14.ms14.atmos.world.AtmosphereService;
import com.juicyslew.moonstation14.ms14.atmos.world.AtmosphereReading;
import com.juicyslew.moonstation14.ms14.disposal.transport.DisposalTransitHolder;
import com.juicyslew.moonstation14.ms14.disposal.transport.LoadedWorldDisposalRouting;
import com.juicyslew.moonstation14.ms14.disposal.unit.DisposalUnitBlock;
import com.juicyslew.moonstation14.ms14.disposal.unit.DisposalUnitBlockEntity;
import com.juicyslew.moonstation14.ms14.disposal.world.DisposalTubeWorldData;
import com.juicyslew.moonstation14.ms14.power.runtime.PowerRuntime;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.Set;

/** Exercises the same explicit build/initialize/status path used by operators, then transports a real item stack. */
@com.juicyslew.moonstation14.gametest.support.GameTestTags({"disposal", "atmosphere", "power", "character"})
@PrefixGameTestTemplate(false)
public final class DisposalFixtureGameTests {
    // Keep the complete fixture (including the 30-block outdoor tour) inside the 32x32 template.
    private static final BlockPos ORIGIN=new BlockPos(7,0,9);
    private static final BlockPos UNIT=new BlockPos(3,2,8);
    private DisposalFixtureGameTests() { }

    @GameTest(batch="disposals_camera_tour",template="disposal_power_clear",templateNamespace="moonstation14_camera_tour",timeoutTicks=900)
    public static void fixtureBuildsInitializesPowersAndFlushesWholeStack(GameTestHelper helper) {
        ServerLevel level=(ServerLevel)helper.getLevel();
        AtmosphereService atmos=AtmosphereService.INSTANCE;
        RestoreState restore=new RestoreState();
        restore.guard(()->{
        atmos.configureAtServerStart(Set.of(level.dimension()), atmos.spaceDimensionsForTest());
        BlockPos entry=helper.absolutePos(ORIGIN);
        BlockPos origin=entry.offset(-6,0,-8);
         String built=DisposalFixtureCommands.build(level,entry);
          restore.require(built.startsWith("Built from floor reference")&&built.contains("finite owned gas-passable enclosure cells")&&built.contains("breathable pressure="),"fresh fixture build initializes finite breathable air: "+built);
          String reseeded=DisposalFixtureCommands.initialize(level,entry);
          restore.require(reseeded.startsWith("Initialized "),"explicit finite-air initialize safely reseeds already-claimed cells: "+reseeded);
        restore.require(origin.getY()==entry.getY()&&level.getBlockState(entry).is(ModBlocks.STEEL_WALL_BLOCK.get())&&level.getBlockState(entry.above()).isAir(),"floor reference is built at supplied Y with standing air above");
        for(int x=0;x<12;x++)for(int y=0;y<7;y++)for(int z=0;z<12;z++)restore.require(origin.getY()+y>=entry.getY(),"fixture target never writes below floor reference");
        for(int z=2;z<=8;z++)restore.require(level.getBlockState(origin.offset(4,1,z)).is(net.minecraft.world.level.block.Blocks.STONE),"solid cable host post on APC/HV row "+z);
        for(int z=4;z<=6;z++)restore.require(level.getBlockState(origin.offset(2,1,z)).is(net.minecraft.world.level.block.Blocks.STONE),"solid cable host post on MV row "+z);
        BlockPos unitPos=origin.offset(UNIT);
         restore.require(level.getBlockState(unitPos.above()).isAir(),"unit's direct-above finite-room gas source remains AIR");
         restore.require(AtmosphereService.INSTANCE.finiteRoomEndpointForTrustedSimulation(level,unitPos.above()).isPresent(),"unit headspace belongs to the fully claimed finite gas component");
        restore.require(level.getBlockState(origin.offset(4,3,7)).is(net.minecraft.world.level.block.Blocks.STONE),"APC lamp cable has a solid existing-style host post");
        restore.require(level.getBlockState(origin.offset(DisposalFixtureCommands.LAMP)).is(ModBlocks.POWER_LAMP.get()),"real APC-powered lamp installed in room without covering unit's source cell");
        restore.require(level.getBlockState(origin.offset(DisposalFixtureCommands.LAMP)).getValue(PowerDeviceBlock.FACING)==Direction.EAST,"lamp's real APC port faces its WEST-face cable host");
        restore.require(level.getBlockEntity(unitPos) instanceof DisposalUnitBlockEntity,"real disposal unit entity installed");
        var tubes=DisposalTubeWorldData.forMutation(level);
        var trunk=tubes.lookup(level,unitPos.below());
        restore.require(trunk.kind()==DisposalTubeWorldData.LookupKind.PRESENT,"real owned trunk directly under unit");
        var route=new LoadedWorldDisposalRouting(level);
        var resolvedPlan=DisposalFixtureTourPlan.resolve(com.juicyslew.moonstation14.ms14.prototype.PrototypeRuntime.serverDisposals());
        var positions=resolvedPlan.nodes().stream().map(DisposalFixtureTourPlan.Node::position).toList();
        restore.require(positions.size()==259,"resolved complete tour node count");
        for(int i=0;i<positions.size()-1;i++) {
            BlockPos aPos=origin.offset(positions.get(i)),bPos=origin.offset(positions.get(i+1));
            var a=route.record(aPos);var b=route.record(bPos);
            restore.require(a!=null&&b!=null&&route.reciprocal(a,b,Direction.fromDelta(bPos.getX()-aPos.getX(),bPos.getY()-aPos.getY(),bPos.getZ()-aPos.getZ())),
                    "reciprocal resolved ports at tour segment "+i+": "+aPos+" -> "+bPos);
        }
        BlockPos outlet=origin.offset(resolvedPlan.outlet());
        restore.require(level.getBlockState(outlet).getCollisionShape(level,outlet).isEmpty(),"clear safe outlet");
         awaitFiniteRoom(helper,level,entry,origin,unitPos,outlet,0,restore);
        });
    }

    private static void awaitFiniteRoom(GameTestHelper helper,ServerLevel level,BlockPos entry,BlockPos origin,BlockPos unitPos,BlockPos outlet,int elapsed,RestoreState restore) {
        BlockPos room=unitPos.above();
        AtmosphereService.INSTANCE.sample(level,room);
        if(AtmosphereService.INSTANCE.finiteRoomEndpointForTrustedSimulation(level,room).isEmpty()) {
            if(elapsed>=300)restore.fail("normal atmosphere ticks did not claim finite room cell "+room);
            helper.runAfterDelay(5,()->restore.guard(()->awaitFiniteRoom(helper,level,entry,origin,unitPos,outlet,elapsed+5,restore)));return;
        }
        var sample=AtmosphereService.INSTANCE.readAtmosphere(level,room).orElseThrow();
        restore.require(sample.mixture().pressureKpa(1.0)>20&&sample.mixture().gasMoles().getOrDefault(com.juicyslew.moonstation14.ms14.atmos.core.GasType.OXYGEN,0.0)>0,
                "fresh build room gas is breathable finite air without manual initialize: "+sample.mixture());
        awaitPaidUnit(helper,level,entry,origin,unitPos,outlet,0,0,restore);
    }

    private static void awaitPaidUnit(GameTestHelper helper,ServerLevel level,BlockPos entry,BlockPos origin,BlockPos unitPos,BlockPos outlet,int elapsed,int readyElapsed,RestoreState restore) {
        var unit=(DisposalUnitBlockEntity)level.getBlockEntity(unitPos);
        var meter=PowerRuntime.disposalUnitMeter(level,unit);
        BlockPos lampPos=origin.offset(DisposalFixtureCommands.LAMP);
        boolean lampLit=level.getBlockState(lampPos).getValue(PowerDeviceBlock.LIT);
        if(!meter.known()||meter.deliveredWatts()!=1000||!level.getBlockState(unitPos).getValue(DisposalUnitBlock.POWERED)||!lampLit) {
            if(elapsed>=480)restore.fail("real APC did not naturally power both 1000 W disposal unit and 100 W room lamp: unit="+meter+", lampLit="+lampLit+"; "+PowerRuntime.lastSolveDiagnostic(level)+"; unitState="+level.getBlockState(unitPos));
             helper.runAfterDelay(5,()->restore.guard(()->awaitPaidUnit(helper,level,entry,origin,unitPos,outlet,elapsed+5,0,restore)));return;
        }
        if(level.getBlockState(unitPos).getValue(DisposalUnitBlock.VISUAL_STATE)!=DisposalUnitBlock.VisualState.READY) {
            if(readyElapsed>=10)restore.fail("paid unit did not naturally reach READY phase: meter="+meter+", state="+level.getBlockState(unitPos));
             helper.runAfterDelay(1,()->restore.guard(()->awaitPaidUnit(helper,level,entry,origin,unitPos,outlet,elapsed,readyElapsed+1,restore)));return;
        }
        restore.require(unit.prototype()!=null&&unit.prototype().powerWatts()==1000,"resolved static-machine demand remains 1000 W");
        restore.require(level.getBlockState(lampPos).is(ModBlocks.POWER_LAMP.get())&&level.getBlockState(lampPos).getValue(PowerDeviceBlock.LIT),"ordinary APC power loop naturally lights the real 100 W lamp");
        String status=DisposalFixtureCommands.status(level,entry);
        var room=AtmosphereService.INSTANCE.readAtmosphere(level,unitPos.above());
        restore.require(room.isPresent()&&room.get().status()==AtmosphereReading.Status.FINITE&&room.get().mixture().totalMoles()>0,
                "known nonempty finite room air directly above unit is ready before flush");
        restore.require(level.getBlockState(unitPos.above()).isAir(),"powered lamp and its cable post leave the unit's gas-source cell AIR");
        restore.require(status.contains("route=true")&&status.contains("powered=true")&&status.contains("deliveredWatts=1000")&&status.contains("finiteRoom=FINITE"),"live status reports paid reciprocal fixture and finite room: "+status);
        // The local runtime fills the finite unit reservoir from this finite room at flush preparation,
        // not on a background tick. Do not inject reservoir gas or grant a fake powered state.
        var item=new ItemStack(Items.IRON_INGOT,3);item.set(DataComponents.CUSTOM_NAME,Component.literal("fixture stack"));
        var source=new ItemEntity(level,origin.getX()+6.5,origin.getY()+1.1,origin.getZ()+5.5,item);
        restore.require(level.addFreshEntity(source),"real world item entity admitted to level");
        helper.runAfterDelay(1,()->restore.guard(()->{
            restore.require(unit.acceptWorldItem(source),"actual world-item ownership transfer to unit");
            unit.engage();
         awaitEjected(helper,level,origin,unit,source.getUUID(),outlet,0,false,restore);
        }));
    }

    private static void awaitEjected(GameTestHelper helper,ServerLevel level,BlockPos origin,DisposalUnitBlockEntity unit,
                                     java.util.UUID sourceId,BlockPos outlet,int elapsed,boolean holderAirObserved,RestoreState restore) {
        var box=new AABB(origin.getX(),origin.getY(),origin.getZ(),origin.getX()+12,origin.getY()+7,origin.getZ()+12);
        var holders=level.getEntitiesOfClass(DisposalTransitHolder.class,box,h->true);
        for(var holder:holders)if(holder.finiteAir().flatMap(a->a.sample()).map(a->a.mixture().totalMoles()>0).orElse(false))holderAirObserved=true;
        var outputs=level.getEntitiesOfClass(ItemEntity.class,box,e->e.isAlive()&&e.getItem().getItem()==Items.IRON_INGOT&&Component.literal("fixture stack").equals(e.getItem().get(DataComponents.CUSTOM_NAME)));
        if(outputs.size()==1&&outputs.getFirst().getItem().getCount()==3&&unit.contents().snapshot().isEmpty()) {
            restore.require(level.getEntity(sourceId)==null,"source world entity consumed exactly once");
            restore.require(holderAirObserved,"actual flush boundary captured positive finite room gas into the live holder");
             restore.require(outputs.getFirst().blockPosition().getX()>=outlet.getX(),"whole stack ejected at or beyond resolved clear outlet");
            helper.runAfterDelay(10,()->restore.guard(()->{
                var after=level.getEntitiesOfClass(ItemEntity.class,box,e->e.isAlive()&&e.getItem().getItem()==Items.IRON_INGOT&&Component.literal("fixture stack").equals(e.getItem().get(DataComponents.CUSTOM_NAME)));
                restore.require(after.size()==1&&after.getFirst().getItem().getCount()==3,"one whole output stack remains after duplicate-detection window");
                restore.restore();helper.succeed();
            }));return;
        }
         if(elapsed>=700)restore.fail("item did not complete powered real route; outputs="+outputs.size()+", unitContents="+unit.contents().snapshot().size()+", holderAirObserved="+holderAirObserved+", timing="+unit.timingSnapshot()+", status="+DisposalFixtureCommands.status(level,origin.offset(DisposalFixtureCommands.ENTRY_CELL.getX(),0,DisposalFixtureCommands.ENTRY_CELL.getZ())));
        boolean observed=holderAirObserved;
         helper.runAfterDelay(2,()->restore.guard(()->awaitEjected(helper,level,origin,unit,sourceId,outlet,elapsed+2,observed,restore)));
    }
    private record RestoreState() {
        void require(boolean ok,String message){if(!ok)fail(message);}
        void fail(String message){restore();throw new GameTestAssertException(message);}
        void guard(Runnable operation){try{operation.run();}catch(Throwable failure){restore();RestoreState.<RuntimeException>throwUnchecked(failure);}}
        @SuppressWarnings("unchecked") private static <T extends Throwable> void throwUnchecked(Throwable failure) throws T { throw (T)failure; }
        void restore(){ }
    }
}
