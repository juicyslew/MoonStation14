package com.juicyslew.moonstation14.ms14.doors;

import com.juicyslew.moonstation14.block.ModBlocks;
import com.juicyslew.moonstation14.block.ModBlockEntities;
import com.juicyslew.moonstation14.block.custom.PowerDeviceBlock;
import com.juicyslew.moonstation14.gametest.support.GameTestTags;
import com.juicyslew.moonstation14.gametest.support.FixtureChunkTickets;
import com.juicyslew.moonstation14.component.ModDataAttachments;
import com.juicyslew.moonstation14.ms14.atmos.core.GasType;
import com.juicyslew.moonstation14.ms14.atmos.world.AtmosphereService;
import com.juicyslew.moonstation14.ms14.power.cable.CableStorage;
import com.juicyslew.moonstation14.ms14.power.cable.CableTier;
import com.juicyslew.moonstation14.ms14.power.device.PowerDeviceKind;
import com.juicyslew.moonstation14.ms14.power.runtime.PowerRuntime;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.AfterBatch;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Focused proof that a placed composed prototype is consumed by the live root BE and both tiles. */
@GameTestTags({"airlock"})
@PrefixGameTestTemplate(false)
public final class AirlockCoreGameTests {
    private static volatile java.util.Map<net.minecraft.resources.ResourceLocation,com.juicyslew.moonstation14.ms14.doors.prototype.AirlockData> originalAirlockCatalog;
    private AirlockCoreGameTests() { }
    @GameTest(templateNamespace="moonstation14",template="atmos_large_empty",batch="airlock_core",timeoutTicks=100)
    @GameTestTags({"mouse-ai","airlock","physics"})
    public static void resolvedMousePassesClosedAirlockButVanillaRayAndWallRemainSolid(GameTestHelper helper) {
        ServerLevel level=helper.getLevel();BlockPos root=helper.absolutePos(new BlockPos(3,1,3));
        var lower=ModBlocks.AIRLOCK.get().defaultBlockState();level.setBlock(root,lower,3);
        level.setBlock(root.above(),lower.setValue(AirlockBlock.HALF,DoubleBlockHalf.UPPER),3);
        AirlockBlockEntity door=(AirlockBlockEntity)level.getBlockEntity(root);
        require(door.applyWire(door.identity(),door.state().revision(),AirlockState.Wire.BOLT,AirlockState.WireAction.PULSE),"fixture deploys bolts without requiring power");
        var mouse=com.juicyslew.moonstation14.entities.ModEntities.MOUSE.get().create(level);
        require(mouse!=null,"registered mouse created");mouse.setPos(root.getX()-.5,root.getY(),root.getZ()+.5);level.addFreshEntity(mouse);
        com.juicyslew.moonstation14.ms14.character.CharacterIdentitySystem.enrollSupportedActor(mouse,level);
        var character=com.juicyslew.moonstation14.ms14.character.CharacterIdentitySystem.resolveForActor(mouse).orElseThrow();
        var profile=character.component(com.juicyslew.moonstation14.ms14.character.components.CollisionProfileComponent.class).orElseThrow();
        require(!AirlockPassagePolicy.pairCollides(mouse,door),"resolved mouse profile and inherited fixture defaults have disjoint intersections");
        require(door.state().boltsDown()&&!door.state().powerAvailable(),"fixture is closed, bolted, and unpowered");
        for(int x=root.getX()-2;x<=root.getX()+2;x++) {
            level.setBlock(new BlockPos(x,root.getY(),root.getZ()-1),net.minecraft.world.level.block.Blocks.STONE.defaultBlockState(),3);
            level.setBlock(new BlockPos(x,root.getY(),root.getZ()+1),net.minecraft.world.level.block.Blocks.STONE.defaultBlockState(),3);
        }
        for(int i=0;i<20;i++)mouse.move(net.minecraft.world.entity.MoverType.SELF,new net.minecraft.world.phys.Vec3(.12,0,0));
        require(mouse.getX()>root.getX()+1,"native Entity.move crosses closed unbolted unpowered airlock");
        var ray=level.clip(new net.minecraft.world.level.ClipContext(new net.minecraft.world.phys.Vec3(root.getX()-.5,root.getY()+.5,root.getZ()+.5),
                new net.minecraft.world.phys.Vec3(root.getX()+.5,root.getY()+.5,root.getZ()+.5),net.minecraft.world.level.ClipContext.Block.COLLIDER,
                net.minecraft.world.level.ClipContext.Fluid.NONE,mouse));
        require(ray.getBlockPos().equals(root),"COLLIDER interaction ray still hits the closed door");
        var human=net.minecraft.world.entity.EntityType.VILLAGER.create(level);
        require(human!=null,"native human host created");human.setNoAi(true);human.setPos(root.getX()-1.4,root.getY(),root.getZ()+.5);
        level.addFreshEntity(human);com.juicyslew.moonstation14.ms14.character.CharacterIdentitySystem.enrollSupportedActor(human,level);
        double humanBefore=human.getX();for(int i=0;i<12;i++)human.move(net.minecraft.world.entity.MoverType.SELF,new net.minecraft.world.phys.Vec3(.12,0,0));
        require(human.getX()<root.getX()&&human.getX()>humanBefore,"unprofiled human remains blocked by original closed-door collision");human.discard();
        BlockPos wall=root.east().east();level.setBlock(wall,Blocks.STONE.defaultBlockState(),3);
        mouse.setPos(wall.getX()-.5,wall.getY(),wall.getZ()+.5);double before=mouse.getX();
        for(int i=0;i<8;i++)mouse.move(net.minecraft.world.entity.MoverType.SELF,new net.minecraft.world.phys.Vec3(.12,0,0));
        require(mouse.getX()<wall.getX(),"native wall collision remains solid for mouse");
        mouse.discard();helper.succeed();
    }
    @GameTest(templateNamespace="moonstation14",template="atmos_large_empty",timeoutTicks=60)
    @GameTestTags({"mouse-ai","npc-physics","airlock"})
    public static void mousePassesPlainPoweredClosedAirlockWithoutBumpOpening(GameTestHelper helper) {
        ServerLevel level=helper.getLevel();BlockPos root=helper.absolutePos(new BlockPos(3,1,3));
        var lower=ModBlocks.AIRLOCK.get().defaultBlockState();level.setBlock(root,lower,3);
        level.setBlock(root.above(),lower.setValue(AirlockBlock.HALF,DoubleBlockHalf.UPPER),3);
        AirlockBlockEntity door=(AirlockBlockEntity)level.getBlockEntity(root);
        require(door.setPowered(true)&&door.state().powerAvailable()&&!door.state().boltsDown(),
                "fixture is powered with ordinary bolts-up closed state");
        var mouse=com.juicyslew.moonstation14.entities.ModEntities.MOUSE.get().create(level);
        require(mouse!=null,"registered mouse created");mouse.setPos(root.getX()-.5,root.getY(),root.getZ()+.5);
        require(level.addFreshEntity(mouse),"mouse added to powered airlock lane");
        com.juicyslew.moonstation14.ms14.character.CharacterIdentitySystem.enrollSupportedActor(mouse,level);
        require(!AirlockPassagePolicy.collides(mouse,root),"resolved mouse fixture policy permits closed passage");
        for(int i=0;i<20;i++)mouse.move(net.minecraft.world.entity.MoverType.SELF,new net.minecraft.world.phys.Vec3(.12,0,0));
        require(mouse.getX()>root.getX()+1,"native movement passes powered closed airlock");
        require(door.state().phase()==AirlockState.DoorPhase.CLOSED
                        &&!level.getBlockState(root).getValue(AirlockBlock.OPEN),
                "physical passage does not dispatch bump opening or change closed state");
        mouse.discard();helper.succeed();
    }
    @GameTest(templateNamespace="moonstation14",template="atmos_large_empty",batch="airlock_core",timeoutTicks=80)
    @GameTestTags({"mouse-ai","npc-physics","airlock"})
    public static void npcSurfaceSearchDoesNotUsePassableAirlockAsSupport(GameTestHelper helper) {
        ServerLevel level=helper.getLevel();BlockPos root=helper.absolutePos(new BlockPos(3,1,3));
        for(int x=-2;x<=4;x++) for(int z=-2;z<=2;z++)
            level.setBlock(root.offset(x,0,z),Blocks.STONE.defaultBlockState(),3);
        level.setBlock(root,ModBlocks.AIRLOCK.get().defaultBlockState(),3);
        level.setBlock(root.above(),ModBlocks.AIRLOCK.get().defaultBlockState()
                .setValue(AirlockBlock.HALF,DoubleBlockHalf.UPPER),3);
        require(level.getBlockState(root).is(ModBlocks.AIRLOCK.get())
                        &&level.getBlockState(root.above()).is(ModBlocks.AIRLOCK.get()),
                "both expected airlock halves occupy the footprint");
        var mouse=com.juicyslew.moonstation14.entities.ModEntities.MOUSE.get().create(level);
        require(mouse!=null,"registered mouse created");mouse.setNoAi(true);mouse.setPos(root.getX()-.5,root.getY()+1,root.getZ()+.5);
        require(level.addFreshEntity(mouse),"mouse added beside passable airlock support fixture");
        com.juicyslew.moonstation14.ms14.character.CharacterIdentitySystem.enrollSupportedActor(mouse,level);
        var context=net.minecraft.world.phys.shapes.CollisionContext.of(mouse);
        var upper=level.getBlockState(root.above());
        require(com.juicyslew.moonstation14.ms14.doors.AirlockPassagePolicy
                        .physicalShape(mouse,upper,level,root.above(),context).isEmpty(),
                "resolved physical collision policy removes closed airlock shape for mouse");
        require(com.juicyslew.moonstation14.ms14.doors.AirlockPassagePolicy
                        .physicalShape(mouse,level.getBlockState(root),level,root,context).isEmpty(),
                "resolved physical collision policy removes lower airlock shape used by support search");
        var planner=new com.juicyslew.moonstation14.ms14.mobs.GroundNpcRoutePlanner();
        require(!planner.hasSupportedSurface(mouse,new net.minecraft.world.phys.Vec3(root.getX()+.5,root.getY()+1,root.getZ()+.5)),
                "shared surface query excludes upper airlock despite its static block collision shape");
        var route=planner.route(mouse,new net.minecraft.world.phys.Vec3(root.getX()+2.5,root.getY()+1,root.getZ()+.5));
        require(!route.isEmpty(),"planner finds supported route in adjacent platform fixture");
        require(route.stream().allMatch(point->planner.hasSupportedSurface(mouse,point.position())),
                "every emitted route point still resolves to actor-specific physical support: "+route);
        mouse.discard();helper.succeed();
    }
    @AfterBatch(batch="airlock_prototype_power_override")
    public static void restorePrototypeCatalogAfterOverrideBatch(ServerLevel level) {
        restorePrototypeCatalog();
    }
    @GameTest(templateNamespace="moonstation14",template="atmos_large_empty",batch="airlock_core",timeoutTicks=100)
    public static void twoTileRootResolvesPrototypeAndBothClosedCellsSeal(GameTestHelper helper) {
        ServerLevel level=helper.getLevel(); BlockPos relative=new BlockPos(3,1,3);BlockPos root=helper.absolutePos(relative);
        var lower=ModBlocks.AIRLOCK.get().defaultBlockState();
        helper.setBlock(relative,lower);
        helper.setBlock(relative.above(),lower.setValue(AirlockBlock.HALF,DoubleBlockHalf.UPPER));
        require(level.getBlockEntity(root) instanceof AirlockBlockEntity,"root controller BE exists");
        AirlockBlockEntity be=(AirlockBlockEntity)level.getBlockEntity(root);
        require(be.prototype()!=null,"resolved typed airlock prototype is live-consumed");
        require(be.prototype().apcPowerReceiver().requestedWatts()==20,"inherited 20 W receiver");
        require(!com.juicyslew.moonstation14.ms14.atmos.world.AtmosphereTopology.isPassable(level,root),"lower closed cell sealed");
        require(!com.juicyslew.moonstation14.ms14.atmos.world.AtmosphereTopology.isPassable(level,root.above()),"upper closed cell sealed");
        helper.succeed();
    }
    @GameTest(templateNamespace="moonstation14",template="atmos_large_empty",batch="airlock_core",timeoutTicks=100)
    public static void openStatePassesBothAtmosphereCellsAndCloseSealsWithoutReplacingRoot(GameTestHelper helper) {
        ServerLevel level=helper.getLevel();BlockPos relative=new BlockPos(3,1,3);BlockPos root=helper.absolutePos(relative);
        var lower=ModBlocks.AIRLOCK.get().defaultBlockState();helper.setBlock(relative,lower);
        helper.setBlock(relative.above(),lower.setValue(AirlockBlock.HALF,DoubleBlockHalf.UPPER));
        AirlockBlockEntity be=(AirlockBlockEntity)level.getBlockEntity(root);var identity=be.identity();
        for(BlockPos pos:new BlockPos[]{root,root.above()})level.setBlock(pos,level.getBlockState(pos).setValue(AirlockBlock.OPEN,true),3);
        require(com.juicyslew.moonstation14.ms14.atmos.world.AtmosphereTopology.isPassable(level,root),"lower opened cell passable");
        require(com.juicyslew.moonstation14.ms14.atmos.world.AtmosphereTopology.isPassable(level,root.above()),"upper opened cell passable");
        for(BlockPos pos:new BlockPos[]{root,root.above()})level.setBlock(pos,level.getBlockState(pos).setValue(AirlockBlock.OPEN,false),3);
        require(!com.juicyslew.moonstation14.ms14.atmos.world.AtmosphereTopology.isPassable(level,root),"closed lower is airtight again");
        require(!com.juicyslew.moonstation14.ms14.atmos.world.AtmosphereTopology.isPassable(level,root.above()),"closed upper is airtight again");
        require(level.getBlockEntity(root)==be&&be.identity().equals(identity),"door state changes preserve root identity");
        helper.succeed();
    }
    @GameTestTags("repair-airlock-unload")
    @GameTest(templateNamespace="moonstation14",template="empty",batch="airlock_unload_lifecycle",timeoutTicks=500)
    public static void unloadedPoweredAirlockDoesNotForceReloadAndRegistersAfterManualReload(GameTestHelper helper) {
        ServerLevel level=helper.getLevel();
        // Keep this explicit lifecycle fixture well outside every GameTest runner
        // hold region; the root structure's own chunk is intentionally not used.
        int x=helper.absolutePos(new BlockPos(256,1,256)).getX();
        int z0=helper.absolutePos(new BlockPos(256,1,256)).getZ();
        BlockPos source=new BlockPos(x,1,z0); BlockPos sub=new BlockPos(x,1,z0+2);
        BlockPos apc=new BlockPos(x,1,z0+4); BlockPos root=new BlockPos(x,1,z0+7);
        for(int z=z0;z<=z0+7;z++)level.setBlock(new BlockPos(x+1,1,z),Blocks.STONE.defaultBlockState(),3);
        for(int z=z0+2;z<=z0+4;z++)level.setBlock(new BlockPos(x-1,1,z),Blocks.STONE.defaultBlockState(),3);
        placePowerDevice(level,source,PowerDeviceKind.HV_SOURCE,Direction.EAST);
        placePowerDevice(level,sub,PowerDeviceKind.HV_MV_SUBSTATION,Direction.EAST);
        placePowerDevice(level,apc,PowerDeviceKind.APC,Direction.WEST);
        for(int z=z0;z<=z0+2;z++)require(CableStorage.place(level,new BlockPos(x+1,1,z),Direction.WEST,CableTier.HV),"remote unload fixture HV cable");
        for(int z=z0+2;z<=z0+4;z++)require(CableStorage.place(level,new BlockPos(x-1,1,z),Direction.EAST,CableTier.MV),"remote unload fixture MV cable");
        for(int z=z0+4;z<=z0+7;z++)require(CableStorage.place(level,new BlockPos(x+1,1,z),Direction.WEST,CableTier.APC),"remote unload fixture APC cable");
        var lower=ModBlocks.AIRLOCK.get().defaultBlockState();
        level.setBlock(root,lower,3);
        level.setBlock(root.above(),lower.setValue(AirlockBlock.HALF,DoubleBlockHalf.UPPER),3);
        AirlockBlockEntity door=(AirlockBlockEntity)level.getBlockEntity(root);
        require(door!=null,"remote controller loaded");
        var lease=com.juicyslew.moonstation14.gametest.support.FixtureChunkTickets.around(helper,
                root.offset(-4,0,-2),root.offset(2,2,9),1);
        final com.juicyslew.moonstation14.gametest.support.FixtureChunkTickets.Lease[] reloadLease={null};
        var identity=door.identity();
        com.juicyslew.moonstation14.gametest.support.BoundedGameTestWait.safeDelay(helper, 100,()->{
            var meter=PowerRuntime.airlockPower(level,door);
            require(meter.known()&&meter.deliveredWatts()==door.prototype().apcPowerReceiver().requestedWatts(),
                    "remote fixture has fresh fully paid controller power before unload: "+meter+", "+doorDiagnostic(level,door));
            lease.close();
            // This isolated fixture may share the server's native save queue with
            // unrelated chunks. Flush normal pending saves before measuring the
            // ticket-driven unload; this does not force eviction or tick the world.
            level.getChunkSource().save(true);
            com.juicyslew.moonstation14.gametest.support.BoundedGameTestWait.safeDelay(helper,1,
                    ()->waitForChunkUnload(helper,root,door,identity,lease,200,()->{
                var rootChunk=level.getChunkSource().getChunkNow(root.getX()>>4,root.getZ()>>4);
                require(!isFull(rootChunk),"airlock root chunk is below FULL after ticket release; no teardown force-load: "+chunkStatus(rootChunk));
                require(door.isRemoved(),"unloaded controller is detached");
                // The first lease has served its sole purpose: proving genuine
                // unload. Manual reload is a separate owner-held operation so
                // the reloaded network remains available for its fresh solve.
                observeNoReload(helper,root,door,20,()->{
                reloadLease[0]=com.juicyslew.moonstation14.gametest.support.FixtureChunkTickets.around(helper,
                        root.offset(-4,0,-2),root.offset(2,2,9),1);
                level.getChunk(root.getX()>>4,root.getZ()>>4);
                var reloadedChunk=level.getChunkSource().getChunkNow(root.getX()>>4,root.getZ()>>4);
                require(isFull(reloadedChunk),"explicit manual FULL chunk reload completed: "+chunkStatus(reloadedChunk));
                var reloaded=(AirlockBlockEntity)reloadedChunk.getBlockEntity(root);
                require(reloaded!=null&&reloaded!=door&&reloaded.identity().equals(identity),
                        "manual FULL reload reconstructs the same persisted controller identity");
                });
            }));
        });
        com.juicyslew.moonstation14.gametest.support.BoundedGameTestWait.safeDelay(helper, 400,()->{
            require(reloadLease[0]!=null&&!reloadLease[0].isClosed(),"manual reload owner ticket remains held through power recovery");
            var reloadedChunk=level.getChunkSource().getChunkNow(root.getX()>>4,root.getZ()>>4);
            require(isFull(reloadedChunk),"manually reloaded controller remains FULL-loaded for its solve: "+chunkStatus(reloadedChunk));
            var reloaded=(AirlockBlockEntity)reloadedChunk.getBlockEntity(root);
            var meter=PowerRuntime.airlockPower(level,reloaded);
            require(meter.known()&&meter.deliveredWatts()==reloaded.prototype().apcPowerReceiver().requestedWatts(),
                    "manual FULL reload re-registers controller and restores its paid power component: "+meter+", "+doorDiagnostic(level,reloaded));
            helper.succeed();
        });
    }
    private static void waitForChunkUnload(GameTestHelper helper,BlockPos root,AirlockBlockEntity door,
                                           java.util.UUID identity,com.juicyslew.moonstation14.gametest.support.FixtureChunkTickets.Lease lease,
                                           int remaining,Runnable done){
        var level=helper.getLevel();
        var chunk=level.getChunkSource().getChunkNow(root.getX()>>4,root.getZ()>>4);
        if(!isFull(chunk)&&door.isRemoved()){done.run();return;}
        if(remaining<=0)throw new GameTestAssertException("remote airlock did not unload below FULL and detach after its owner ticket was released (cached chunk status="+chunkStatus(chunk)+", removed="+door.isRemoved()+"): "+lease.diagnostic(root));
        com.juicyslew.moonstation14.gametest.support.BoundedGameTestWait.safeDelay(helper,1,
                ()->waitForChunkUnload(helper,root,door,identity,lease,remaining-1,done));
    }
    private static boolean isFull(LevelChunk chunk){return chunk!=null&&chunk.getFullStatus().isOrAfter(net.minecraft.server.level.FullChunkStatus.FULL);}
    private static String chunkStatus(LevelChunk chunk){return chunk==null?"null":chunk.getFullStatus().toString();}
    private static void observeNoReload(GameTestHelper helper,BlockPos root,AirlockBlockEntity door,int remaining,Runnable done){
        var chunk=helper.getLevel().getChunkSource().getChunkNow(root.getX()>>4,root.getZ()>>4);
        require(!isFull(chunk)&&door.isRemoved(),"unleased root remains below FULL with old controller removed during quiet interval: status="+chunkStatus(chunk)+", removed="+door.isRemoved());
        if(remaining<=0){done.run();return;}
        com.juicyslew.moonstation14.gametest.support.BoundedGameTestWait.safeDelay(helper,1,
                ()->observeNoReload(helper,root,door,remaining-1,done));
    }
    @GameTest(templateNamespace="moonstation14",template="power_live_chain_clear",batch="airlock_core",timeoutTicks=300)
     @GameTestTags({"airlock_power_cycle"})
     public static void liveApcMeterDrivesScheduledTwoPhaseDoorAndAutoclose(GameTestHelper helper) {
        Fixture f=poweredAirlock(helper);ServerLevel level=f.level();AirlockBlockEntity door=f.door();
        waitForFullPower(helper,f,120,()->{
            var sample=PowerRuntime.airlockPower(level,door);
            require(sample.known()&&sample.deliveredWatts()==door.prototype().apcPowerReceiver().requestedWatts(),"fresh solver meter supplies resolved receiver demand");
            require(door.activate(),"powered root activation accepted from actual APC meter");
            require(door.state().phase()==AirlockState.DoorPhase.OPENING,"open transition starts at request");
            com.juicyslew.moonstation14.gametest.support.BoundedGameTestWait.safeDelay(helper, 8,()->{
                require(door.state().phase()==AirlockState.DoorPhase.OPENING_PASSABLE&&door.state().doorPassable(),"0.4s opening phase makes both cells passable");
                require(level.getBlockState(f.root()).getValue(AirlockBlock.OPEN)&&level.getBlockState(f.root().above()).getValue(AirlockBlock.OPEN),"both block cells follow authoritative passability");
                com.juicyslew.moonstation14.gametest.support.BoundedGameTestWait.safeDelay(helper, 4,()->{
                    require(door.state().phase()==AirlockState.DoorPhase.OPEN,"0.2s second phase completes opening");
                    com.juicyslew.moonstation14.gametest.support.BoundedGameTestWait.safeDelay(helper, 100,()->{
                         require(door.state().phase()==AirlockState.DoorPhase.CLOSING,"resolved 5s auto-close begins closing; "+doorDiagnostic(f.level(),door)+" autoCloseAt="+door.state().autoCloseAt()+" now="+f.level().getGameTime());
                        com.juicyslew.moonstation14.gametest.support.BoundedGameTestWait.safeDelay(helper, 12,()->{
                            require(door.state().phase()==AirlockState.DoorPhase.CLOSED,"0.4+0.2s close transition completes");
                            require(!level.getBlockState(f.root()).getValue(AirlockBlock.OPEN)&&!level.getBlockState(f.root().above()).getValue(AirlockBlock.OPEN),"both cells reseal");
                            helper.succeed();
                        });
                    });
                });
            });
        });
    }
    @GameTest(templateNamespace="moonstation14",template="power_live_chain_clear",batch="airlock_core",timeoutTicks=180)
    @GameTestTags({"suite-stabilization"})
    public static void obstructionUsesExactlyTwoDoorwayCellsAndSafetyDeniesCloseActor(GameTestHelper helper) {
        Fixture f=poweredAirlock(helper);ServerLevel level=f.level();AirlockBlockEntity door=f.door();
        com.juicyslew.moonstation14.gametest.support.FixtureChunkTickets.around(helper,f.root().offset(-2,0,-2),f.root().offset(2,2,2),1);
        waitForFullPower(helper,f,120,()->{
            var occupant=new net.minecraft.world.entity.player.Player(level,f.root(),0,new com.mojang.authlib.GameProfile(java.util.UUID.randomUUID(),"airlock-occupant")){
                @Override public boolean isCreative(){return true;} @Override public boolean isSpectator(){return false;}
            };
            occupant.setPos(f.root().getX()+.5,f.root().getY()+.25,f.root().getZ()+.5);level.addFreshEntity(occupant);
            require(door.activate()&&door.state().phase()==AirlockState.DoorPhase.OPENING,"an occupant does not prevent opening a closed airlock");
            com.juicyslew.moonstation14.gametest.support.BoundedGameTestWait.safeDelay(helper, 12,()->{
                require(door.state().phase()==AirlockState.DoorPhase.OPEN,"opening completes with occupant in doorway");
                require(!door.activate()&&door.state().phase()==AirlockState.DoorPhase.OPEN,"safety denies closing onto a living entity in either cell");
                occupant.discard();
                var above=new net.minecraft.world.entity.player.Player(level,f.root(),0,new com.mojang.authlib.GameProfile(java.util.UUID.randomUUID(),"above-airlock")){
                    @Override public boolean isCreative(){return true;} @Override public boolean isSpectator(){return false;}
                };
                above.setPos(f.root().getX()+.5,f.root().getY()+2.1,f.root().getZ()+.5);level.addFreshEntity(above);
                require(door.activate(),"actor above y+2 does not obstruct closing the two-cell doorway");
                above.discard();
                helper.succeed();
            });
        });
    }

    private static String entitySpatialDiagnostic(net.minecraft.world.entity.LivingEntity entity) {
        var posture = entity.getExistingDataOrNull(com.juicyslew.moonstation14.component.ModDataAttachments.POSTURE.get());
        return "uuid=" + entity.getUUID() + ",type=" + net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType())
                + ",pos=" + entity.position() + ",bbox=" + entity.getBoundingBox() + ",alive=" + entity.isAlive()
                + ",removed=" + entity.isRemoved() + ",noGravity=" + entity.isNoGravity() + ",posture="
                + (posture == null ? "none" : posture.data());
    }
    @GameTest(templateNamespace="moonstation14",template="power_live_chain_clear",batch="airlock_core",timeoutTicks=180)
    public static void receiverRequiresFullResolvedWattsAndBoltsGateNormalActivation(GameTestHelper helper) {
        Fixture f=poweredAirlock(helper);ServerLevel level=f.level();AirlockBlockEntity door=f.door();
        waitForFullPower(helper,f,120,()->{
            require(PowerRuntime.airlockPower(level,door).deliveredWatts()==door.prototype().apcPowerReceiver().requestedWatts(),"meter is full before control test");
            var revision=door.state().revision();
            require(door.applyWire(door.identity(),revision,AirlockState.Wire.BOLT,AirlockState.WireAction.CUT),"bolt wire cut accepted");
            require(door.state().boltsDown()&&!door.activate(),"bolts deny normal activation");
            require(door.applyWire(door.identity(),door.state().revision(),AirlockState.Wire.BOLT,AirlockState.WireAction.MEND),"bolt wire mend accepted");
            require(door.state().boltsDown(),"mending cut bolt wire does not retract already deployed bolts");
            require(door.applyWire(door.identity(),door.state().revision(),AirlockState.Wire.BOLT,AirlockState.WireAction.PULSE),"bolt pulse accepted");
            require(!door.state().boltsDown()&&door.activate(),"unbolted powered door activates");
            helper.succeed();
        });
    }
    @GameTest(templateNamespace="moonstation14",template="atmos_large_empty",batch="airlock_core",timeoutTicks=100)
    @GameTestTags({"airlock_wire_layout"})
    public static void inactiveLogAndAiWiresPersistPhysicalCutsWithoutGameplayEffects(GameTestHelper helper) {
        ServerLevel level=helper.getLevel();BlockPos root=helper.absolutePos(new BlockPos(3,1,3));
        var lower=ModBlocks.AIRLOCK.get().defaultBlockState();level.setBlock(root,lower,3);
        level.setBlock(root.above(),lower.setValue(AirlockBlock.HALF,DoubleBlockHalf.UPPER),3);
        AirlockBlockEntity door=(AirlockBlockEntity)level.getBlockEntity(root);
        require(door!=null&&door.prototype()!=null&&door.prototype().wires().supportedActions().size()==8,"live root resolves the eight-entry inherited wire layout");
        require(door.applyWire(door.identity(),door.state().revision(),AirlockState.Wire.AI_INTERACT,AirlockState.WireAction.CUT),"AI-interact placeholder cut accepted");
        require(door.state().aiInteractWireCut()&&!door.state().powered()&&!door.state().boltsDown()&&door.state().safety(),"AI wire stores only physical cut state");
        require(door.applyWire(door.identity(),door.state().revision(),AirlockState.Wire.LOG,AirlockState.WireAction.PULSE),"log placeholder pulse is accepted");
        require(door.state().aiInteractWireCut()&&!door.state().logWireCut()&&door.state().logCircuitDisabled()&&door.state().logPulseUntil()==0&&!door.state().powered()&&!door.state().boltsDown()&&door.state().safety(),"unpowered log pulse latches disablement without a recovery timer; AIA pulse remains a no-op");
        require(door.applyWire(door.identity(),door.state().revision(),AirlockState.Wire.AI_INTERACT,AirlockState.WireAction.PULSE)&&door.state().aiInteractWireCut()&&door.state().logCircuitDisabled(),"AIA pulse is accepted but has no wire-state effect");
        require(door.applyWire(door.identity(),door.state().revision(),AirlockState.Wire.LOG,AirlockState.WireAction.CUT),"log placeholder cut accepted");
        var saved=door.saveWithoutMetadata(level.registryAccess());
        var restored=new AirlockBlockEntity(root,level.getBlockState(root));restored.loadWithComponents(saved,level.registryAccess());
        require(restored.identity().equals(door.identity())&&restored.state().aiInteractWireCut()&&restored.state().logWireCut()&&restored.state().logCircuitDisabled()&&restored.state().logPulseUntil()==0,"root NBT roundtrip preserves cuts, log disable latch and identity");
        require(door.applyWire(door.identity(),door.state().revision(),AirlockState.Wire.AI_INTERACT,AirlockState.WireAction.MEND),"AI-interact placeholder mend accepted");
        require(!door.state().aiInteractWireCut()&&door.state().logWireCut(),"mend clears only the selected physical placeholder cut state");
        helper.succeed();
    }
    @GameTest(templateNamespace="moonstation14",template="power_live_chain_clear",batch="airlock_core",timeoutTicks=850)
     @GameTestTags({"airlock_log_pulse", "suite-stabilization"})
    public static void logPulseDisablesLampAndRestoresAtResolvedDeadline(GameTestHelper helper) {
        Fixture f=poweredAirlock(helper);AirlockBlockEntity door=f.door();
        waitForFullPower(helper,f,120,()->{
            require(door.activate()&&door.state().powered(),"freshly paid receiver admits root activation");
            require(door.applyWire(door.identity(),door.state().revision(),AirlockState.Wire.LOG,AirlockState.WireAction.PULSE),"log pulse accepted");
            long deadline=door.state().logPulseUntil();
            require(deadline-helper.getLevel().getGameTime()==600,"resolved inherited 30-second duration schedules 600 ticks");
            require(com.juicyslew.moonstation14.ms14.doors.ui.AirlockWireIndicators.resolve(door.state(),helper.getLevel().getGameTime()).log()==com.juicyslew.moonstation14.ms14.doors.ui.AirlockWireIndicators.Light.OFF,"pulse immediately extinguishes log circuit light");
             com.juicyslew.moonstation14.gametest.support.BoundedGameTestWait.safeDelay(helper, 40,()->{
                 long beforeRepeat=door.state().logPulseUntil();
                 boolean poweredBefore=door.state().powered();boolean disabledBefore=door.state().logCircuitDisabled();
                 boolean repeated=door.applyWire(door.identity(),door.state().revision(),AirlockState.Wire.LOG,AirlockState.WireAction.PULSE);
                 String diagnostic="beforeDeadline="+beforeRepeat+" afterDeadline="+door.state().logPulseUntil()+" firstDeadline="+deadline+" now="+f.level().getGameTime()
                         +" poweredBefore="+poweredBefore+" poweredAfter="+door.state().powered()+" logDisabledBefore="+disabledBefore+" logDisabledAfter="+door.state().logCircuitDisabled()
                         +" "+doorDiagnostic(f.level(),door);
                 require(repeated,"repeat pulse accepted; "+diagnostic);
                 require(door.state().logPulseUntil()==deadline,"repeat does not extend pulse deadline; "+diagnostic);
                var saved=door.saveWithoutMetadata(f.level().registryAccess());
                var restored=new AirlockBlockEntity(f.root(),f.level().getBlockState(f.root()));restored.loadWithComponents(saved,f.level().registryAccess());
                require(restored.state().logCircuitDisabled()&&restored.state().logPulseUntil()==deadline,"root NBT roundtrip preserves active log pulse deadline and disabled state");
                  com.juicyslew.moonstation14.gametest.support.BoundedGameTestWait.safeDelay(helper, Math.max(1,deadline-f.level().getGameTime()),()->
                      com.juicyslew.moonstation14.gametest.support.BoundedGameTestWait.await(helper,5,
                              ()->"native log-pulse expiry did not process within 5 ticks: now="+f.level().getGameTime()
                                      +" firstDeadline="+deadline+" currentDeadline="+door.state().logPulseUntil()
                                      +" phase="+door.state().phase()+" state="+door.state()+" "+doorDiagnostic(f.level(),door),
                              ()->f.level().getGameTime()>=deadline&&!door.state().logCircuitDisabled()&&door.state().logPulseUntil()==0,
                              ()->{
                                  String expiryDiagnostic="now="+f.level().getGameTime()+" firstDeadline="+deadline
                                          +" currentDeadline="+door.state().logPulseUntil()+" phase="+door.state().phase()
                                          +" state="+door.state()+" "+doorDiagnostic(f.level(),door);
                                  require(!door.state().logCircuitDisabled()&&door.state().logPulseUntil()==0,"intact log circuit reenables at expiry; "+expiryDiagnostic);
                                  require(com.juicyslew.moonstation14.ms14.doors.ui.AirlockWireIndicators.resolve(door.state(),f.level().getGameTime()).log()==com.juicyslew.moonstation14.ms14.doors.ui.AirlockWireIndicators.Light.ON,"expiry restores circuit light; "+expiryDiagnostic);
                                  helper.succeed();
                              }));
            });
        });
    }
    @GameTest(templateNamespace="moonstation14",template="power_live_chain_clear",batch="airlock_core",timeoutTicks=180)
    @GameTestTags({"movement","airlock_bump"})
    public static void enrolledHumanHostBumpsPoweredAirlockThroughEntityMoveWithoutPda(GameTestHelper helper) {
        Fixture f=poweredAirlock(helper);ServerLevel level=f.level();AirlockBlockEntity door=f.door();
        waitForFullPower(helper,f,120,()->{
            var actor=net.minecraft.world.entity.EntityType.VILLAGER.create(level);
            require(actor!=null,"registered character host created");
            actor.setNoAi(true);
            actor.setPos(f.root().getX()-.4,f.root().getY(),f.root().getZ()+.5);
            level.addFreshEntity(actor);
            com.juicyslew.moonstation14.ms14.character.CharacterIdentitySystem.enrollSupportedActor(actor,level);
            require(com.juicyslew.moonstation14.ms14.character.CharacterIdentitySystem.resolveForActor(actor)
                    .filter(data->data.component(com.juicyslew.moonstation14.ms14.character.components.DoorBumpOpenerComponent.class).isPresent()).isPresent(),
                    "resolved human-host profile grants the typed DoorBumpOpener marker");
            require(door.applyWire(door.identity(),door.state().revision(),AirlockState.Wire.POWER_ONE,AirlockState.WireAction.CUT),"cut first power receiver wire");
            require(door.applyWire(door.identity(),door.state().revision(),AirlockState.Wire.POWER_TWO,AirlockState.WireAction.CUT),"cut second power receiver wire");
            actor.move(net.minecraft.world.entity.MoverType.SELF,new net.minecraft.world.phys.Vec3(.2,-.08,0));
            require(door.state().phase()==AirlockState.DoorPhase.CLOSED,"bump cannot open a receiver-disabled door");
            require(door.applyWire(door.identity(),door.state().revision(),AirlockState.Wire.POWER_ONE,AirlockState.WireAction.MEND),"restore first power receiver wire");
            require(door.applyWire(door.identity(),door.state().revision(),AirlockState.Wire.POWER_TWO,AirlockState.WireAction.MEND),"restore second power receiver wire");
            require(door.applyWire(door.identity(),door.state().revision(),AirlockState.Wire.BOLT,AirlockState.WireAction.CUT),"raise bolts");
            actor.move(net.minecraft.world.entity.MoverType.SELF,new net.minecraft.world.phys.Vec3(.2,-.08,0));
            require(door.state().phase()==AirlockState.DoorPhase.CLOSED,"bump cannot open a bolted door");
            require(door.applyWire(door.identity(),door.state().revision(),AirlockState.Wire.BOLT,AirlockState.WireAction.PULSE),"retract bolts");
            actor.move(net.minecraft.world.entity.MoverType.SELF,new net.minecraft.world.phys.Vec3(.2,-.08,0));
            require(door.state().phase()==AirlockState.DoorPhase.OPENING,
                    "grounded Entity.move contact with the powered airlock opens without a PDA/ID");
            actor.discard();helper.succeed();
        });
    }
    @GameTest(templateNamespace="moonstation14",template="power_live_chain_clear",batch="airlock_core",timeoutTicks=180)
    @GameTestTags({"airlock_electrified_open", "airlock_shock_posture"})
    public static void electrifiedBumpAppliesShockAndStillStartsDoorOpening(GameTestHelper helper) {
        Fixture f=poweredAirlock(helper);ServerLevel level=f.level();AirlockBlockEntity door=f.door();
        require(door.applyWire(door.identity(),door.state().revision(),AirlockState.Wire.POWER_ONE,AirlockState.WireAction.CUT),
                "actorless fixture setup cuts one power wire and enables electrification without shocking the actor");
        waitForFullPower(helper,f,120,()->{
            require(door.electrificationState().enabled(),"one cut wire leaves the airlock electrified");
            var actor=net.minecraft.world.entity.EntityType.VILLAGER.create(level);
            require(actor!=null,"registered character host created");actor.setNoAi(true);
            actor.setPos(f.root().getX()-.4,f.root().getY(),f.root().getZ()+.5);level.addFreshEntity(actor);
            com.juicyslew.moonstation14.ms14.character.CharacterIdentitySystem.enrollSupportedActor(actor,level);
            require(com.juicyslew.moonstation14.ms14.character.CharacterIdentitySystem.resolveForActor(actor)
                    .filter(data->data.component(com.juicyslew.moonstation14.ms14.character.components.DoorBumpOpenerComponent.class).isPresent()).isPresent(),
                    "resolved human-host profile grants bump opening");
            require(com.juicyslew.moonstation14.ms14.character.CharacterControlSystem.canAct(actor),"actor is eligible before contact");
            float before=actor.getHealth();
            actor.move(net.minecraft.world.entity.MoverType.SELF,new net.minecraft.world.phys.Vec3(.2,-.08,0));
            require(Math.abs((before-actor.getHealth())-1.4f)<.01f,"fresh contact applies the typed seven-point shock (1.4 entity health)");
            require(com.juicyslew.moonstation14.ms14.character.CharacterControlSystem.isStunned(actor)
                    &&com.juicyslew.moonstation14.ms14.character.CharacterControlSystem.isKnockedDown(actor),
                    "fresh contact applies the configured five-second paralyze and knockdown");
            require(com.juicyslew.moonstation14.ms14.standing.PostureSystem.isDown(actor)
                            && actor.getPose()==net.minecraft.world.entity.Pose.SWIMMING
                            && Math.abs(actor.getBoundingBox().getYsize()-.6)<.001,
                    "actual electrified contact commits shared prone posture and physical dimensions");
            require(door.state().phase()==AirlockState.DoorPhase.OPENING,
                    "the same shock contact still begins the normal door opening transition");
            actor.discard();helper.succeed();
        });
    }
    @GameTest(templateNamespace="moonstation14",template="power_live_chain_clear",batch="airlock_core",timeoutTicks=180)
    @GameTestTags({"movement","airlock_bump","airlock_seam"})
    public static void oneEntityMoveOpensBothSeamAdjacentPaidAirlocks(GameTestHelper helper) {
        Fixture f=poweredAirlock(helper);ServerLevel level=f.level();
        waitForFullPower(helper,f,120,()->{
            BlockPos adjacent=f.root().east();
            // Free the neighboring airlock root; the existing paid west-facing APC
            // spine at z=6..8 is already within the inherited 3-block receiver reach
            // of both roots. Do not add another cable path here.
            BlockPos endpoint=helper.absolutePos(new BlockPos(4,1,9));
            require(CableStorage.remove(level,endpoint,Direction.WEST,CableTier.APC)==CableTier.APC,"free neighbor root cell from former cable endpoint");
            clearAndRequireSeamApproach(level,f.root());
            var lower=ModBlocks.AIRLOCK.get().defaultBlockState();
            level.setBlock(adjacent,lower,3);
            level.setBlock(adjacent.above(),lower.setValue(AirlockBlock.HALF,DoubleBlockHalf.UPPER),3);
            AirlockBlockEntity second=(AirlockBlockEntity)level.getBlockEntity(adjacent);
            require(second!=null,"neighbor root controller exists");
            awaitBothDoorPower(helper,f,second,120,()->{
                var actor=net.minecraft.world.entity.EntityType.VILLAGER.create(level);
                require(actor!=null,"registered character host created");
                actor.setNoAi(true);
                actor.setPos(f.root().getX()+1.0,f.root().getY(),f.root().getZ()+1.35);
                level.addFreshEntity(actor);
                com.juicyslew.moonstation14.ms14.character.CharacterIdentitySystem.enrollSupportedActor(actor,level);
                require(com.juicyslew.moonstation14.ms14.character.CharacterIdentitySystem.resolveForActor(actor)
                        .filter(data->data.component(com.juicyslew.moonstation14.ms14.character.components.DoorBumpOpenerComponent.class).isPresent()).isPresent(),
                        "resolved human-host profile opts into bump opening");
                requireFreshFullPower(f.door());
                requireFreshFullPower(second);
                var before=actor.getBoundingBox();
                actor.move(net.minecraft.world.entity.MoverType.SELF,new net.minecraft.world.phys.Vec3(0,-.08,-.2));
                String movementDiagnostic="actorBefore="+before+" actorAfter="+actor.getBoundingBox()+" "+seamDoorDiagnostic(f.door())+" "+seamDoorDiagnostic(second);
                require(f.door().state().phase()==AirlockState.DoorPhase.OPENING,"left seam root begins opening in this move; "+movementDiagnostic);
                require(second.state().phase()==AirlockState.DoorPhase.OPENING,"right seam root begins opening in the same move; "+movementDiagnostic);
                actor.discard();helper.succeed();
            });
        });
    }

    private static void awaitBothDoorPower(GameTestHelper helper,Fixture f,AirlockBlockEntity second,int remaining,Runnable ready){
        var first=PowerRuntime.airlockPower(f.level(),f.door());
        var neighbor=PowerRuntime.airlockPower(f.level(),second);
        boolean firstFull=first.known()&&first.deliveredWatts()==f.door().prototype().apcPowerReceiver().requestedWatts();
        boolean secondFull=neighbor.known()&&neighbor.deliveredWatts()==second.prototype().apcPowerReceiver().requestedWatts();
        if(firstFull&&secondFull){ready.run();return;}
        if(remaining<=0)throw new GameTestAssertException("seam airlocks never both received fresh full paid APC power: first="+first+" demand="+f.door().prototype().apcPowerReceiver().requestedWatts()+" second="+neighbor+" demand="+second.prototype().apcPowerReceiver().requestedWatts());
        com.juicyslew.moonstation14.gametest.support.BoundedGameTestWait.safeDelay(helper,1,()->awaitBothDoorPower(helper,f,second,remaining-1,ready));
    }
    private static void requireFreshFullPower(AirlockBlockEntity door){
        var sample=PowerRuntime.airlockPower((ServerLevel)door.getLevel(),door);
        double demand=door.prototype().apcPowerReceiver().requestedWatts();
        require(sample.known()&&sample.deliveredWatts()==demand,"airlock has fresh full paid APC meter at seam move: "+sample+" demand="+demand);
    }
    private static void clearAndRequireSeamApproach(ServerLevel level,BlockPos leftRoot){
        // The GameTest structure ends immediately behind this two-wide approach.
        // Generated boundary barriers at these four bbox cells must not mask contact.
        for(int x=0;x<=1;x++)for(int y=0;y<=1;y++){
            BlockPos pos=leftRoot.offset(x,y,1);
            level.setBlock(pos,Blocks.AIR.defaultBlockState(),3);
            require(level.getBlockState(pos).getCollisionShape(level,pos).isEmpty(),"seam actor approach cell has no collision: "+pos);
        }
    }
    private static String seamDoorDiagnostic(AirlockBlockEntity door){
        var sample=PowerRuntime.airlockPower((ServerLevel)door.getLevel(),door);
        return "door="+door.getBlockPos()+" phase="+door.state().phase()+" powered="+door.state().powered()
                +" bolts="+door.state().boltsDown()+" meterKnown="+sample.known()+" meterWatts="+sample.deliveredWatts()
                +" demand="+door.prototype().apcPowerReceiver().requestedWatts();
    }
    private static String doorDiagnostic(ServerLevel level,AirlockBlockEntity door){
        return "door="+door.getBlockPos()+" phase="+door.state().phase()+" powered="+door.state().powered()
                +" circuitDisabled="+door.state().receiverPowerDisabled()+" meter="+PowerRuntime.airlockPower(level,door)
                +" demand="+door.prototype().apcPowerReceiver().requestedWatts()+" rootCurrent="+(level.getBlockEntity(door.getBlockPos())==door)
                +" identity="+door.identity()+" revision="+door.state().revision();
    }
    @GameTest(templateNamespace="moonstation14",template="atmos_large_empty",batch="airlock_core",timeoutTicks=100)
    public static void finiteGasOwnershipMergesAndSeparatesAcrossCompleteDoorCycle(GameTestHelper helper){
        ServerLevel level=helper.getLevel();BlockPos root=helper.absolutePos(new BlockPos(3,1,3));
        BlockPos left=root.west(),right=root.east();
        for(BlockPos cell:new BlockPos[]{left,right})for(Direction direction:Direction.values()){
            BlockPos neighbor=cell.relative(direction);
            if(neighbor.equals(root)||neighbor.equals(left)||neighbor.equals(right))continue;
            level.setBlock(neighbor,Blocks.STONE.defaultBlockState(),3);
        }
        var atmosphere=AtmosphereService.withVacuumDimensions(java.util.Set.of(level.dimension()));
        claimFinite(level,left);claimFinite(level,right);
        require(atmosphere.addGas(level,left,GasType.OXYGEN,2,293),"seed left owned finite cell");
        require(atmosphere.addGas(level,right,GasType.NITROGEN,3,293),"seed right owned finite cell");
        var lower=ModBlocks.AIRLOCK.get().defaultBlockState();level.setBlock(root,lower,3);level.setBlock(root.above(),lower.setValue(AirlockBlock.HALF,DoubleBlockHalf.UPPER),3);
        var leftClosed=atmosphere.sample(level,left).orElseThrow();
        var rightClosed=atmosphere.sample(level,right).orElseThrow();
        require(leftClosed.totalMoles()==2&&rightClosed.totalMoles()==3,"closed door cells retain separate finite inventories");
        for(BlockPos pos:new BlockPos[]{root,root.above()})level.setBlock(pos,level.getBlockState(pos).setValue(AirlockBlock.OPEN,true),3);
        require(com.juicyslew.moonstation14.ms14.atmos.world.AtmosphereTopology.isPassable(level,root)
                &&com.juicyslew.moonstation14.ms14.atmos.world.AtmosphereTopology.isPassable(level,root.above()),"open door connects both atmosphere cells");
        require(atmosphere.sample(level,left).orElseThrow().totalMoles()+atmosphere.sample(level,right).orElseThrow().totalMoles()==5,"open cycle retains both finite gas inventories");
        for(BlockPos pos:new BlockPos[]{root,root.above()})level.setBlock(pos,level.getBlockState(pos).setValue(AirlockBlock.OPEN,false),3);
        leftClosed=atmosphere.sample(level,left).orElseThrow();rightClosed=atmosphere.sample(level,right).orElseThrow();
        require(leftClosed.totalMoles()==2&&rightClosed.totalMoles()==3,"closed cycle retains finite-cell ownership and conserves all gas");
        helper.succeed();
    }
    @GameTest(templateNamespace="moonstation14",template="power_live_chain_clear",batch="airlock_prototype_power_override",timeoutTicks=280)
    @GameTestTags({"airlock_override"})
    public static void inheritedThirtyFiveWattOverrideInvalidatesAndRefreshesLiveMeter(GameTestHelper helper){
        Fixture f=poweredAirlock(helper);ServerLevel level=f.level();AirlockBlockEntity door=f.door();
        waitForFullPower(helper,f,120,()->{
            require(PowerRuntime.airlockPower(level,door).known()&&PowerRuntime.airlockPower(level,door).deliveredWatts()==20,"original inherited 20 W demand has a fresh sample");
            var manager=com.juicyslew.moonstation14.ms14.prototype.PrototypeRuntime.serverManager();
            var type=com.juicyslew.moonstation14.ms14.doors.prototype.ModAirlocks.TYPE;
            var original=new java.util.LinkedHashMap<>(manager.snapshot(type).asMap());
            originalAirlockCatalog=java.util.Map.copyOf(original);
            try {
                manager.reload(type,airlockRaw(true));
                require(!PowerRuntime.airlockPower(level,door).known(),"published prototype change invalidates prior meter");
            } catch (Throwable failure) {
                restorePrototypeCatalog();
                throw failure;
            }
            waitForFullPower(helper,f,120,()->{
                try{
                    require(door.prototype().apcPowerReceiver().requestedWatts()==35,"live root resolves inherited 35 W override");
                    var sample=PowerRuntime.airlockPower(level,door);
                    require(sample.known()&&sample.deliveredWatts()==35,"live solver bills and meters overridden 35 W demand");
                    require(door.activate()&&door.state().powered(),"activation accepts exactly the resolved 35 W delivery");
                    helper.succeed();
                }finally{restorePrototypeCatalog();}
            });
        });
    }
    public record Fixture(ServerLevel level,BlockPos root,AirlockBlockEntity door) { }
    /** Shared powered two-tile airlock fixture for native cross-system GameTests. */
    public static Fixture poweredAirlock(GameTestHelper helper){
        ServerLevel level=helper.getLevel();
        BlockPos root=helper.absolutePos(new BlockPos(3,1,9));
        var lower=ModBlocks.AIRLOCK.get().defaultBlockState();level.setBlock(root,lower,3);level.setBlock(root.above(),lower.setValue(AirlockBlock.HALF,DoubleBlockHalf.UPPER),3);
        AirlockBlockEntity door=(AirlockBlockEntity)level.getBlockEntity(root);require(door!=null,"airlock BE exists");
        acquirePowerFixtureTickets(helper,root,helper.absolutePos(new BlockPos(2,1,2)),helper.absolutePos(new BlockPos(4,1,9)),
                door.prototype().extensionCableReceiver().localReachBlocks());
        for(int z=2;z<=9;z++)level.setBlock(helper.absolutePos(new BlockPos(4,1,z)),Blocks.STONE.defaultBlockState(),3);
        for(int z=4;z<=6;z++)level.setBlock(helper.absolutePos(new BlockPos(2,1,z)),Blocks.STONE.defaultBlockState(),3);
        placePowerDevice(level,helper.absolutePos(new BlockPos(3,1,2)),PowerDeviceKind.HV_SOURCE,Direction.EAST);
        placePowerDevice(level,helper.absolutePos(new BlockPos(3,1,4)),PowerDeviceKind.HV_MV_SUBSTATION,Direction.EAST);
        placePowerDevice(level,helper.absolutePos(new BlockPos(3,1,6)),PowerDeviceKind.APC,Direction.WEST);
        for(int z=2;z<=4;z++)require(CableStorage.place(level,helper.absolutePos(new BlockPos(4,1,z)),Direction.WEST,CableTier.HV),"HV cable");
        for(int z=4;z<=6;z++)require(CableStorage.place(level,helper.absolutePos(new BlockPos(2,1,z)),Direction.EAST,CableTier.MV),"MV cable");
        for(int z=6;z<=9;z++)require(CableStorage.place(level,helper.absolutePos(new BlockPos(4,1,z)),Direction.WEST,CableTier.APC),"APC cable");
        return new Fixture(level,root,door);
    }
    private static void acquirePowerFixtureTickets(GameTestHelper helper,BlockPos root,BlockPos networkMin,BlockPos networkMax,int reach){
        int minX=Math.min(root.getX()-reach,networkMin.getX())-1,maxX=Math.max(root.getX()+reach,networkMax.getX())+1;
        int minZ=Math.min(root.getZ()-reach,networkMin.getZ())-1,maxZ=Math.max(root.getZ()+reach,networkMax.getZ())+1;
        FixtureChunkTickets.acquire(helper,new net.minecraft.world.phys.AABB(minX,root.getY(),minZ,
                Math.nextDown(maxX+1.0),root.getY()+3,Math.nextDown(maxZ+1.0)));
    }
    private static void placePowerDevice(ServerLevel level,BlockPos pos,PowerDeviceKind kind,Direction facing){
        if(kind==PowerDeviceKind.APC){require(com.juicyslew.moonstation14.ms14.power.device.PowerDeviceFixturePlacement.placeApc(level,pos,facing),"place supported APC");return;}
        var block=switch(kind){case HV_SOURCE->ModBlocks.HV_SOURCE.get();case HV_MV_SUBSTATION->ModBlocks.HV_MV_SUBSTATION.get();case APC->ModBlocks.APC.get();default->throw new IllegalArgumentException("unexpected device");};
        level.setBlock(pos,block.defaultBlockState().setValue(PowerDeviceBlock.FACING,facing),3);
    }
    private static void claimFinite(ServerLevel level,BlockPos pos){
        LevelChunk chunk=(LevelChunk)level.getChunk(pos);
        chunk.getData(ModDataAttachments.ATMOSPHERE_CHUNK.get()).claimFinite(pos.getX()&15,pos.getY(),pos.getZ()&15);
        chunk.setUnsaved(true);
    }
    private static java.util.Map<net.minecraft.resources.ResourceLocation,com.google.gson.JsonObject> airlockRaw(boolean thirtyFiveWatts){
        try{
            var base=readAirlockJson("base_airlock");var child=readAirlockJson("standard_airlock");
            if(thirtyFiveWatts)base.getAsJsonObject("apcPowerReceiver").addProperty("requestedWatts",35.0);
            var raw=new java.util.LinkedHashMap<net.minecraft.resources.ResourceLocation,com.google.gson.JsonObject>();
            raw.put(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("moonstation14","base_airlock"),base);
            raw.put(com.juicyslew.moonstation14.ms14.doors.prototype.ModAirlocks.STANDARD,child);return raw;
        }catch(Exception e){throw new GameTestAssertException("load airlock source prototypes: "+e);}
    }
    private static com.google.gson.JsonObject readAirlockJson(String name)throws Exception{
        try(var in=new java.io.InputStreamReader(AirlockCoreGameTests.class.getResourceAsStream("/data/moonstation14/moonstation14/airlock/"+name+".json"),java.nio.charset.StandardCharsets.UTF_8)){
            return com.google.gson.JsonParser.parseReader(in).getAsJsonObject();
        }
    }
    private static void require(boolean condition,String message){if(!condition)throw new GameTestAssertException(message);}
    public static void waitForFullPower(GameTestHelper helper,Fixture f,int remaining,Runnable ready){
        var sample=PowerRuntime.airlockPower(f.level(),f.door());
        double demand=f.door().prototype().apcPowerReceiver().requestedWatts();
        boolean fixtureEntityTicking=java.util.stream.Stream.of(f.root(),f.root().offset(0,0,-7),f.root().offset(0,0,-5),
                f.root().offset(0,0,-3),f.root().offset(1,0,-7),f.root().offset(-1,0,-5),f.root().offset(1,0,0))
                .allMatch(f.level()::isPositionEntityTicking);
        if(sample.known()&&sample.deliveredWatts()==demand&&fixtureEntityTicking){ready.run();return;}
        if(remaining<=0){
            String diagnostic="known="+sample.known()
                +" delivered="+sample.deliveredWatts()+" resolvedDemand="+demand+" topologyReady="+com.juicyslew.moonstation14.ms14.power.graph.PowerGraphService.topologyReady(f.level())
                 +" rootCurrent="+(f.level().getBlockEntity(f.root())==f.door())+" phase="+f.door().state().phase()+" gameTime="+f.level().getGameTime()
                  +" "+com.juicyslew.moonstation14.ms14.power.graph.PowerGraphService.diagnosticSnapshot(f.level())
                  +" solve="+PowerRuntime.lastSolveSummary(f.level())
                  +" meterGate="+PowerRuntime.airlockPowerDiagnostic(f.level(),f.door())
                  +" receiverTopology="+airlockReceiverDiagnostic(f)+" fixtureEntityTicking="+fixtureEntityTicking;
            com.juicyslew.moonstation14.MoonStation14.LOGGER.error("[airlock core GameTest power wait exhausted] {}",diagnostic);
            throw new GameTestAssertException("airlock fixture never reached fresh full-power readiness: known="+sample.known()
                    +" delivered="+sample.deliveredWatts()+" demand="+demand+" phase="+f.door().state().phase()
                    +" gameTime="+f.level().getGameTime());
        }
        com.juicyslew.moonstation14.gametest.support.BoundedGameTestWait.safeDelay(helper,1,()->waitForFullPower(helper,f,remaining-1,ready));
    }
    private static String airlockReceiverDiagnostic(Fixture f){
        ServerLevel level=f.level();
        int reach=f.door().prototype().extensionCableReceiver().localReachBlocks();
        var nodes=com.juicyslew.moonstation14.ms14.power.graph.PowerGraphService.nodesNear(level,f.root(),reach,CableTier.APC);
        String candidates=nodes==null?"unknown-footprint":nodes.stream()
                .sorted(java.util.Comparator.comparingDouble(node->node.host().distSqr(f.root())))
                .limit(4).map(node->node.host().toShortString()+"/"+node.face()+"="
                        +com.juicyslew.moonstation14.ms14.power.graph.PowerGraphService.state(level,node)).toList().toString();
        BlockPos apc=f.root().relative(Direction.NORTH,3);
        BlockPos inputCable=apc.relative(Direction.WEST);
        BlockPos outputCable=apc.relative(Direction.EAST);
        BlockPos receiverCable=f.root().relative(Direction.EAST);
        return "apc="+apc.toShortString()+" apcBE="+(level.getBlockEntity(apc)!=null)
                +" apcChunkLoaded="+(level.getChunkSource().getChunkNow(apc.getX()>>4,apc.getZ()>>4)!=null)
                +" inputCable="+rawCableRecord(level,inputCable,Direction.EAST,CableTier.MV)
                +" outputCable="+rawCableRecord(level,outputCable,Direction.WEST,CableTier.APC)
                +" receiverCable="+rawCableRecord(level,receiverCable,Direction.WEST,CableTier.APC)
                +" receiverCandidates="+candidates;
    }
    private static String rawCableRecord(ServerLevel level,BlockPos pos,Direction face,CableTier tier){
        var chunk=level.getChunkSource().getChunkNow(pos.getX()>>4,pos.getZ()>>4);
        if(!(chunk instanceof LevelChunk levelChunk))return pos.toShortString()+"=not-full";
        var data=levelChunk.getExistingDataOrNull(ModDataAttachments.POWER_CABLE_CHUNK.get());
        return pos.toShortString()+"="+(data!=null&&data.contains(pos.getX()&15,pos.getY(),pos.getZ()&15,face,tier));
    }
    private static synchronized void restorePrototypeCatalog() {
        var original=originalAirlockCatalog;
        if(original==null)return;
        originalAirlockCatalog=null;
        com.juicyslew.moonstation14.ms14.prototype.PrototypeRuntime.serverManager()
                .publishDecoded(com.juicyslew.moonstation14.ms14.doors.prototype.ModAirlocks.TYPE,original);
    }
}
