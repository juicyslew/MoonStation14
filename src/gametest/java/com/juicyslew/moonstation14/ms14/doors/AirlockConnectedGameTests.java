package com.juicyslew.moonstation14.ms14.doors;

import com.juicyslew.moonstation14.block.ModBlocks;
import com.juicyslew.moonstation14.block.custom.PowerDeviceBlock;
import com.juicyslew.moonstation14.component.ModDataAttachments;
import com.juicyslew.moonstation14.gametest.support.GameTestTags;
import com.juicyslew.moonstation14.ms14.atmos.core.GasType;
import com.juicyslew.moonstation14.ms14.atmos.world.AtmosphereService;
import com.juicyslew.moonstation14.ms14.character.CharacterControlSystem;
import com.juicyslew.moonstation14.ms14.character.CharacterIdentitySystem;
import com.juicyslew.moonstation14.ms14.character.ModCharacters;
import com.juicyslew.moonstation14.ms14.organ.BodySystem;
import com.juicyslew.moonstation14.ms14.mobs.MobStateSystem;
import com.juicyslew.moonstation14.ms14.damage.DamageKeys;
import com.juicyslew.moonstation14.ms14.damage.DamageSystem;
import com.juicyslew.moonstation14.ms14.power.cable.CableStorage;
import com.juicyslew.moonstation14.ms14.power.cable.CableTier;
import com.juicyslew.moonstation14.ms14.power.device.PowerDeviceKind;
import com.juicyslew.moonstation14.ms14.power.runtime.PowerRuntime;
import com.juicyslew.moonstation14.gametest.support.FixtureChunkTickets;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.BeforeBatch;
import net.minecraft.gametest.framework.AfterBatch;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.chunk.LevelChunk;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.List;
import java.util.Set;

/** Connected end-to-end airlock acceptance coverage; no fake player authority is used. */
@GameTestTags({"airlock", "atmosphere", "airlock_connected"})
@PrefixGameTestTemplate(false)
public final class AirlockConnectedGameTests {
    private static final java.util.Set<Long> FORCED_POWER_FIXTURE_CHUNKS=new java.util.HashSet<>();
    private static java.util.Set<net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level>> previousVacuumDimensions;
    private AirlockConnectedGameTests() { }

    @net.minecraft.gametest.framework.AfterBatch(batch="airlock_connected")
    public static void releasePowerFixtureChunks(ServerLevel level){
        for(long key:FORCED_POWER_FIXTURE_CHUNKS){var pos=new net.minecraft.world.level.ChunkPos(key);level.setChunkForced(pos.x,pos.z,false);}
        FORCED_POWER_FIXTURE_CHUNKS.clear();
    }

    @BeforeBatch(batch="airlock_gas_flow")
    public static void setupGasFlowBatch(ServerLevel level) {
        AtmosphereService service=AtmosphereService.INSTANCE;
        previousVacuumDimensions=service.vacuumDimensionsForTest();
        service.configureAtServerStart(Set.of(level.dimension()),service.spaceDimensionsForTest());
    }

    @AfterBatch(batch="airlock_gas_flow")
    public static void restoreGasFlowBatch(ServerLevel level) {
        AtmosphereService.INSTANCE.configureAtServerStart(previousVacuumDimensions,AtmosphereService.INSTANCE.spaceDimensionsForTest());
    }

    @GameTestTags({"airlock_admission_probe"})
    @GameTest(templateNamespace = "moonstation14", template = "power_live_chain_clear", batch = "airlock_connected", timeoutTicks = 280)
    public static void unsafeCloseCrushesOnlyLivingBodyInsideExactDoorway(GameTestHelper helper) {
        Fixture f = poweredAirlock(helper);
        waitForPoweredAdmission(helper, f, 0, () -> {
            require(f.door.activate(), "powered door opens through its controller");
            com.juicyslew.moonstation14.gametest.support.BoundedGameTestWait.safeDelay(helper, 14, () -> {
                require(f.door.state().phase() == AirlockState.DoorPhase.OPEN, "scheduled transition completed; state="+f.door.state());
                waitForPoweredPhase(helper, f, AirlockState.DoorPhase.OPEN, 0, () -> {
                Villager victim = enrolledBody(f.level, f.root.getX() + .5, f.root.getY(), f.root.getZ() + .5);
                Villager above = enrolledBody(f.level, f.root.getX() + .5, f.root.getY() + 2.15, f.root.getZ() + .5);
                float victimBefore = DamageSystem.existingOrVanillaBaseline(victim).getOrDefault(DamageKeys.BLUNT, 0f);
                float aboveBefore = DamageSystem.existingOrVanillaBaseline(above).getOrDefault(DamageKeys.BLUNT, 0f);
                require(f.door.applyWire(f.door.identity(), f.door.state().revision(), AirlockState.Wire.SAFETY,
                        AirlockState.WireAction.CUT), "trusted fixture cuts safety wire through stable BE API");
                require(f.door.activate() && f.door.state().phase() == AirlockState.DoorPhase.CLOSING,
                        "actual controller begins unsafe close");
                require(DamageSystem.existingOrVanillaBaseline(victim).getOrDefault(DamageKeys.BLUNT, 0f)==victimBefore,
                        "closing transition does not crush before the partial-close collision check");
                com.juicyslew.moonstation14.gametest.support.BoundedGameTestWait.safeDelay(helper, 9, () -> {
                    float victimDelta = DamageSystem.existingOrVanillaBaseline(victim).getOrDefault(DamageKeys.BLUNT, 0f) - victimBefore;
                    float aboveDelta = DamageSystem.existingOrVanillaBaseline(above).getOrDefault(DamageKeys.BLUNT, 0f) - aboveBefore;
                    require(Math.abs(victimDelta - 15f) < .001f, "partial close applies typed blunt crush damage of 15");
                    require(Math.abs(aboveDelta) < .001f, "body whose bounds begin above y+2 is outside the doorway");
                     require(CharacterControlSystem.isStunned(victim), "partial-close crush stun is active");
                     com.juicyslew.moonstation14.gametest.support.BoundedGameTestWait.safeDelay(helper, 5, () -> {
                         require(f.door.state().phase()==AirlockState.DoorPhase.CLOSED,
                                 "scheduled close reaches CLOSED at the second transition before delayed reopen");
                         require(f.level.getGameTime()<f.door.crushReopenRetryAt(),
                                 "the two-second reopen deadline is later than the ordinary second close transition");
                        victim.discard(); above.discard(); helper.succeed();
                    });
                });
                });
            });
        });
    }

    @GameTest(templateNamespace = "moonstation14", template = "power_live_chain_clear", batch = "airlock_connected", timeoutTicks = 280)
    public static void lateActorDuringSafeCloseAbortsWithoutCrush(GameTestHelper helper) {
        Fixture f=poweredAirlock(helper);
        helper.runAfterDelay(100,()->waitForPoweredAdmission(helper,f,0,()->{
            require(f.door.activate(),"powered airlock starts opening");
            com.juicyslew.moonstation14.gametest.support.BoundedGameTestWait.safeDelay(helper, 14,()->{
                require(f.door.state().phase()==AirlockState.DoorPhase.OPEN,"door is open before close attempt; state="+f.door.state());
                waitForPoweredPhase(helper,f,AirlockState.DoorPhase.OPEN,0,30,()->{
                Villager entrant=enrolledBody(f.level,f.root.getX()+1.5,f.root.getY(),f.root.getZ()+.5);
                float before=DamageSystem.existingOrVanillaBaseline(entrant).getOrDefault(DamageKeys.BLUNT,0f);
                require(f.door.activate()&&f.door.state().phase()==AirlockState.DoorPhase.CLOSING,"empty safe close begins");
                com.juicyslew.moonstation14.gametest.support.BoundedGameTestWait.safeDelay(helper, 4,()->{
                    entrant.setPos(f.root.getX()+.5,f.root.getY(),f.root.getZ()+.5);
                    com.juicyslew.moonstation14.gametest.support.BoundedGameTestWait.safeDelay(helper, 5,()->{
                        require(f.door.state().phase()==AirlockState.DoorPhase.OPEN,"late obstruction returns partially closing door to open");
                        float damage=DamageSystem.existingOrVanillaBaseline(entrant).getOrDefault(DamageKeys.BLUNT,0f)-before;
                        require(Math.abs(damage)<.001f&&!CharacterControlSystem.isStunned(entrant),"safe partial-close abort inflicts no crush damage or stun");
                        entrant.discard();helper.succeed();
                    });
                });
                });
            });
        }));
    }

    @GameTestTags({"airlock_crush", "status", "movement"})
    @GameTest(templateNamespace="moonstation14",template="power_live_chain_clear",batch="airlock_connected",timeoutTicks=500)
    public static void unsafeCrushRetainsVictimUntilPaidTwoSecondReopen(GameTestHelper helper) {
        Fixture f=poweredAirlock(helper);
        FixtureChunkTickets.around(helper,f.root.offset(-2,0,-2),f.root.offset(2,2,2),1);
        waitForPoweredAdmission(helper,f,0,()->{
            require(f.door.activate(),"freshly powered airlock opens");
            com.juicyslew.moonstation14.gametest.support.BoundedGameTestWait.safeDelay(helper, 14,()->{
                require(f.door.state().phase()==AirlockState.DoorPhase.OPEN,"door has completed normal two-phase opening");
                waitForPoweredPhase(helper,f,AirlockState.DoorPhase.OPEN,0,()->{
                Villager victim=enrolledBody(f.level,f.root.getX()+.5,f.root.getY()+.1,f.root.getZ()+.5);
                Villager outside=enrolledBody(f.level,f.root.getX()+.5,f.root.getY()+2.35,f.root.getZ()+.5);
                victim.setNoGravity(true);outside.setNoGravity(true);
                double initialY=victim.getY();
                float bluntBefore=DamageSystem.existingOrVanillaBaseline(victim).getOrDefault(DamageKeys.BLUNT,0f);
                require(f.door.applyWire(f.door.identity(),f.door.state().revision(),AirlockState.Wire.SAFETY,AirlockState.WireAction.CUT),"unsafe close enabled only via safety wire");
                require(f.door.activate()&&f.door.state().phase()==AirlockState.DoorPhase.CLOSING,"unsafe close starts through live controller");
                com.juicyslew.moonstation14.gametest.support.BoundedGameTestWait.safeDelay(helper, 9,()->{
                    float delta=DamageSystem.existingOrVanillaBaseline(victim).getOrDefault(DamageKeys.BLUNT,0f)-bluntBefore;
                    require(Math.abs(delta-15f)<.001f,"partial close applies exactly one resolved 15 Blunt damage delta; got="+delta);
                    require(f.door.state().phase()==AirlockState.DoorPhase.CLOSING_PASSABLE,"partial close solidifies and schedules the second close interval");
                    var replicated=f.door.getUpdateTag(f.level.registryAccess());
                    require(replicated.getList("CrushVictims",net.minecraft.nbt.Tag.TAG_COMPOUND).stream()
                                    .anyMatch(tag->tag instanceof net.minecraft.nbt.CompoundTag compound&&compound.hasUUID("Id")&&compound.getUUID("Id").equals(victim.getUUID())),
                            "transient crushed-victim UUID is in the existing BE update tag");
                    AirlockBlockEntity decoded=new AirlockBlockEntity(f.root,f.level.getBlockState(f.root));
                    decoded.handleUpdateTag(replicated,f.level.registryAccess());
                    require(decoded.isCrushExempt(net.minecraft.world.phys.shapes.CollisionContext.of(victim)),
                            "detached BE decodes transient UUID policy without mutating authoritative root; this is serialization coverage, not client-runtime proof");
                    for(BlockPos cell:new BlockPos[]{f.root,f.root.above()}) {
                        var closed=f.level.getBlockState(cell);
                        require(closed.getCollisionShape(f.level,cell,net.minecraft.world.phys.shapes.CollisionContext.of(victim)).isEmpty(),
                                "actual entity-context collision query exempts victim in both door cells");
                        require(!closed.getCollisionShape(f.level,cell,net.minecraft.world.phys.shapes.CollisionContext.of(outside)).isEmpty(),
                                "outside actor remains blocked by both closed door cells");
                        require(!closed.isSuffocating(f.level,cell),"airlock closed cell is specifically non-suffocating");
                    }
                    require(Blocks.STONE.defaultBlockState().isSuffocating(f.level,f.root),"ordinary stone retains vanilla suffocation behavior");
                    require(f.door.identity().equals(f.door.getUpdateTag(f.level.registryAccess()).getUUID("ViewIdentity")),
                            "replicated victim exemption is published by this two-cell root controller identity");
                    require(Math.abs(victim.getY()-initialY)<1e-6,"victim stayed at exact initial Y through solidification");
                    require(CharacterControlSystem.isStunned(victim)&&CharacterControlSystem.isKnockedDown(victim),
                            "crush projects action-blocking stun and knockdown");
                     float healthAfterCrush=victim.getHealth();
                     var crushedCenter=victim.getBoundingBox().getCenter();
                     long originalReopenDeadline=f.door.crushReopenRetryAt();
                     long crushedAt=originalReopenDeadline-40;
                     require(originalReopenDeadline>f.level.getGameTime()
                                     &&originalReopenDeadline-f.level.getGameTime()<=40,
                             "fresh fully-paid reopen is scheduled two seconds after the actual crush event");
                    f.level.setBlock(f.root.offset(0,0,-3),Blocks.AIR.defaultBlockState(),3);
                    float savedHealth=healthAfterCrush;
                      assertVictimStable(helper,f,victim,crushedCenter,savedHealth,bluntBefore,60,()->{
                        var powerless=PowerRuntime.airlockPower(f.level,f.door);
                        require(!powerless.known()||powerless.deliveredWatts()<f.door.prototype().apcPowerReceiver().requestedWatts(),
                                "removing APC prevents fresh full-power admission at the two-second deadline");
                         long now=f.level.getGameTime();
                         require(now>=originalReopenDeadline,"the original two-second crush reopen deadline has elapsed");
                         require(f.door.state().phase()==AirlockState.DoorPhase.CLOSED,"door remains sealed while its expired reopen deadline retries without power");
                         require(f.door.crushReopenRetryAt()>now&&f.door.crushReopenRetryAt()<=now+20,
                                 "failed fresh-power admission schedules a bounded future retry rather than retaining a past-due deadline");
                        require(!CharacterControlSystem.isStunned(victim)&&!CharacterControlSystem.isKnockedDown(victim),
                                "resolved 2.4-second paralyze/knockdown expires during the longer trapped hold");
                        require(!f.level.getBlockState(f.root).getValue(AirlockBlock.OPEN),"closed door remains physically closed while unpowered");
                        place(f.level,f.root.offset(0,0,-3),PowerDeviceKind.APC,Direction.WEST);
                         waitForPaidCrushReopen(helper,f,victim,outside,crushedCenter,savedHealth,bluntBefore,crushedAt,120);
                    });
                });
                });
            });
        });
    }

    @GameTestTags({"airlock_crush_cycle", "airlock_crush", "status"})
    @GameTest(templateNamespace="moonstation14",template="power_live_chain_clear",batch="airlock_connected",timeoutTicks=400)
    public static void crushReleaseCompletesNormalOpenBeforeFastAutoClose(GameTestHelper helper) {
        Fixture f=poweredAirlock(helper);
        waitForPoweredAdmission(helper,f,0,()->{
            require(f.door.activate(),"freshly paid controller begins normal opening");
            com.juicyslew.moonstation14.gametest.support.BoundedGameTestWait.safeDelay(helper, 9,()->{
                require(f.door.state().phase()==AirlockState.DoorPhase.OPENING_PASSABLE,"door is passable during the first opening animation");
                Villager victim=enrolledBody(f.level,f.root.getX()+.5,f.root.getY()+.1,f.root.getZ()+.5);
                float before=DamageSystem.existingOrVanillaBaseline(victim).getOrDefault(DamageKeys.BLUNT,0f);
                long revision=f.door.state().revision();
                require(f.door.applyWire(f.door.identity(),revision,AirlockState.Wire.SAFETY,AirlockState.WireAction.CUT),"unsafe crush admission uses safety wire");
                revision=f.door.state().revision();
                require(f.door.applyWire(f.door.identity(),revision,AirlockState.Wire.TIMING,AirlockState.WireAction.CUT),"fast auto-close uses timing wire");
                java.util.EnumSet<AirlockState.DoorPhase> postCrushPhases=java.util.EnumSet.noneOf(AirlockState.DoorPhase.class);
                boolean[] reopened={false};
                long[] openingStartedAt={0};
                traceCrushCycle(helper,f,victim,before,postCrushPhases,reopened,openingStartedAt,120);
            });
        });
    }

    @GameTestTags({"airlock_stationary_contact", "airlock_crush", "status"})
    @GameTest(templateNamespace="moonstation14",template="power_live_chain_clear",batch="airlock_connected",timeoutTicks=260)
    public static void stationaryCriticalCrushContactShocksOncePerSolidification(GameTestHelper helper) {
        Fixture f=poweredAirlock(helper);
        waitForPoweredAdmission(helper,f,0,()->{
            require(f.door.applyWire(f.door.identity(),f.door.state().revision(),AirlockState.Wire.POWER_ONE,AirlockState.WireAction.CUT),"one power-wire cut enables electrification while retaining door power");
            waitForPoweredAdmission(helper,f,0,()->{
            require(f.door.activate(),"paid airlock opens");
            com.juicyslew.moonstation14.gametest.support.BoundedGameTestWait.safeDelay(helper, 14,()->{
                Villager victim=enrolledBody(f.level,f.root.getX()+.5,f.root.getY()+.1,f.root.getZ()+.5);
                Villager nearby=enrolledBody(f.level,f.root.getX()+1.5,f.root.getY()+.1,f.root.getZ()+.5);
                Villager boundary=enrolledBody(f.level,f.root.getX()-.5,f.root.getY()+.1,f.root.getZ()+.5);
                victim.setNoGravity(true);
                boundary.setNoGravity(true);
                boundary.setPos(f.root.getX()-.30001,boundary.getY(),boundary.getZ());
                var boundaryPos=boundary.position();
                require(AirlockGeometry.touchesDoorway(boundary.getBoundingBox(),new net.minecraft.world.phys.AABB(f.root.getX(),f.root.getY(),f.root.getZ(),f.root.getX()+1,f.root.getY()+2,f.root.getZ()+1)),"boundary fixture begins within static-contact tolerance body="+boundary.getBoundingBox()+" root="+f.root+" width="+boundary.getBbWidth());
                float boundaryBlunt=DamageSystem.existingOrVanillaBaseline(boundary).getOrDefault(DamageKeys.BLUNT,0f);
                float boundaryShock=DamageSystem.existingOrVanillaBaseline(boundary).getOrDefault(DamageKeys.SHOCK,0f);
                float nearbyShockBefore=DamageSystem.existingOrVanillaBaseline(nearby).getOrDefault(DamageKeys.SHOCK,0f);
                DamageSystem.applyHealthChange(victim,java.util.Map.of(DamageKeys.BLUNT,100f),1f,false);
                require(MobStateSystem.isCritical(victim)&&victim.isAlive(),"fixture is an actual living Critical body");
                float shockBefore=DamageSystem.existingOrVanillaBaseline(victim).getOrDefault(DamageKeys.SHOCK,0f);
                float bluntBefore=DamageSystem.existingOrVanillaBaseline(victim).getOrDefault(DamageKeys.BLUNT,0f);
                var pos=victim.position();
                require(f.door.applyWire(f.door.identity(),f.door.state().revision(),AirlockState.Wire.SAFETY,AirlockState.WireAction.CUT),"safety wire enables crush");
                require(f.door.activate(),"unsafe close begins");
                com.juicyslew.moonstation14.gametest.support.BoundedGameTestWait.safeDelay(helper, 9,()->{
                    require(DamageSystem.existingOrVanillaBaseline(nearby).getOrDefault(DamageKeys.SHOCK,0f)==nearbyShockBefore,
                            "powered closing contact does not shock an actor outside the exact doorway");
                    require(Math.abs(DamageSystem.existingOrVanillaBaseline(boundary).getOrDefault(DamageKeys.SHOCK,0f)-boundaryShock-7f)<.001f,
                            "face-tangent living body receives the solidification contact despite its center being outside");
                    require(DamageSystem.existingOrVanillaBaseline(boundary).getOrDefault(DamageKeys.BLUNT,0f)==boundaryBlunt,
                            "boundary contact does not enter crush damage; blunt="+DamageSystem.existingOrVanillaBaseline(boundary).getOrDefault(DamageKeys.BLUNT,0f)+" before="+boundaryBlunt+" bbox="+boundary.getBoundingBox()+" root="+f.root);
                    assertStationaryCrushContact(helper,f,victim,pos,shockBefore,bluntBefore,()->
                         com.juicyslew.moonstation14.gametest.support.BoundedGameTestWait.safeDelay(helper, 5,()->{
                            require(f.door.state().phase()==AirlockState.DoorPhase.CLOSED,"normal close reaches sealed phase");
                            waitForDoorPhase(helper,f,AirlockState.DoorPhase.OPEN,80,()->{
                                require(MobStateSystem.isCritical(victim)&&victim.isAlive(),"victim remains living and Critical for another cycle");
                                float secondShock=DamageSystem.existingOrVanillaBaseline(victim).getOrDefault(DamageKeys.SHOCK,0f);
                                require(f.door.activate(),"reopened door begins second close");
                                 com.juicyslew.moonstation14.gametest.support.BoundedGameTestWait.safeDelay(helper, 9,()->{
                                    float total=DamageSystem.existingOrVanillaBaseline(victim).getOrDefault(DamageKeys.SHOCK,0f)-shockBefore;
                                    require(Math.abs(total-14f)<.001f,"new solidification cycle produces exactly one additional 7 shock; total="+total+" secondStart="+secondShock);
                                    require(Math.abs(DamageSystem.existingOrVanillaBaseline(victim).getOrDefault(DamageKeys.BLUNT,0f)-bluntBefore-30f)<.001f,"both solidification cycles apply one 15-point crush each");
                                    require(MobStateSystem.isCritical(victim),"second contact remains eligible while Critical");
                                    victim.discard();nearby.discard();boundary.discard();helper.succeed();
                                });
                            });
                        }));
                });
                com.juicyslew.moonstation14.gametest.support.BoundedGameTestWait.safeDelay(helper, 55,()->require(CharacterControlSystem.isStunned(victim)
                                &&CharacterControlSystem.isKnockedDown(victim),
                        "shock's five-second paralysis replaces the shorter 2.4-second crush projection"));
            });
        });
        });
    }

    @GameTestTags({"airlock_stationary_contact", "airlock_bump", "status"})
    @GameTest(templateNamespace="moonstation14",template="power_live_chain_clear",batch="airlock_connected",timeoutTicks=160)
    public static void retainedDeadTangentBodyQuietlyReopensEachNormalClose(GameTestHelper helper){
        Fixture f=poweredAirlock(helper);
        FixtureChunkTickets.around(helper,f.root.offset(-2,0,-2),f.root.offset(2,2,2),1);
        waitForPoweredAdmission(helper,f,0,()->{
            require(f.door.activate(),"ordinary paid opening starts");
            waitForDoorPhase(helper,f,AirlockState.DoorPhase.OPEN,30,()->{
                Villager corpse=enrolledBody(f.level,f.root.getX()-.5,f.root.getY()+.1,f.root.getZ()+.5);
                corpse.setNoGravity(true);
                 require(DamageSystem.applyHealthChange(corpse,java.util.Map.of(DamageKeys.BLUNT,200f),1f,true)==DamageSystem.Result.APPLIED
                                &&MobStateSystem.isDead(corpse)&&!corpse.isAlive(),"fixture is a loaded resolved medical DEAD body");
                 placeActualBoxAtDoorFace(corpse,f.root);
                require(AirlockGeometry.touchesDoorway(corpse.getBoundingBox(),new net.minecraft.world.phys.AABB(f.root.getX(),f.root.getY(),f.root.getZ(),f.root.getX()+1,f.root.getY()+2,f.root.getZ()+1)),"corpse bounding box touches root face: "+corpse.getBoundingBox()+" root="+f.root+" width="+corpse.getBbWidth());
                require(corpse.getX()<f.root.getX()&&corpse.getBoundingBox().maxX<=f.root.getX()+1.0E-5,"corpse center is outside left side and only its actual bbox face touches the door");
                 Villager gap=enrolledBody(f.level,f.root.getX()+1.31,f.root.getY()+.1,f.root.getZ()+.5);
                gap.setNoGravity(true);
                require(!AirlockGeometry.touchesDoorway(gap.getBoundingBox(),new net.minecraft.world.phys.AABB(f.root.getX(),f.root.getY(),f.root.getZ(),f.root.getX()+1,f.root.getY()+2,f.root.getZ()+1)),"nearby body has a genuine horizontal gap");
                require(f.door.activate(),"normal powered closing begins with stationary bodies at its boundary; corpse="+corpse.getBoundingBox()+" root="+f.root+" gap="+gap.getBoundingBox());
                  waitForCloseRecovery(helper,f,40,corpse,()->{
                    require(MobStateSystem.isDead(corpse)&&!corpse.isRemoved(),"dead corpse remains loaded and persistent after closed-edge reopen");
                    require(f.door.state().phase()==AirlockState.DoorPhase.OPENING,"quiet normal opening is selected at closed transition");
                    waitForDoorPhase(helper,f,AirlockState.DoorPhase.OPEN,20,()->{
                        require(f.door.activate(),"second normal close begins with corpse still at the static face");
                             waitForCloseRecovery(helper,f,40,corpse,()->{
                            require(MobStateSystem.isDead(corpse)&&!corpse.isRemoved(),"same static corpse reopens a later close episode");
                            require(f.door.state().phase()==AirlockState.DoorPhase.OPENING,"second close also starts ordinary non-denying opening");
                            corpse.discard();gap.discard();helper.succeed();
                        });
                    });
                });
            });
        });
    }

    @GameTestTags({"airlock_stationary_contact", "airlock_bump", "critical", "status"})
    @GameTest(templateNamespace="moonstation14",template="power_live_chain_clear",batch="airlock_connected",timeoutTicks=160)
    public static void criticalTangentContactReopensWithoutAnotherShock(GameTestHelper helper){
        Fixture f=poweredAirlock(helper);
        FixtureChunkTickets.around(helper,f.root.offset(-2,0,-2),f.root.offset(2,2,2),1);
        waitForPoweredAdmission(helper,f,0,()->{
            require(f.door.applyWire(f.door.identity(),f.door.state().revision(),AirlockState.Wire.POWER_ONE,AirlockState.WireAction.CUT),"one power cut enables electrification");
            waitForPoweredAdmission(helper,f,0,()->{require(f.door.activate(),"paid opening starts");waitForDoorPhase(helper,f,AirlockState.DoorPhase.OPEN,30,()->{
                Villager body=enrolledBody(f.level,f.root.getX()-.3,f.root.getY()+.1,f.root.getZ()+.5);
                body.setNoGravity(true);
                body.setPos(f.root.getX()-.30001,body.getY(),body.getZ());
                require(DamageSystem.applyHealthChange(body,java.util.Map.of(DamageKeys.BLUNT,100f),1f,false)==DamageSystem.Result.APPLIED
                 &&MobStateSystem.isCritical(body)&&body.isAlive(),"boundary actor is an actual living Critical body");
                placeActualBoxAtDoorFace(body,f.root);
                var doorway=new net.minecraft.world.phys.AABB(f.root.getX(),f.root.getY(),f.root.getZ(),f.root.getX()+1,f.root.getY()+2,f.root.getZ()+1);
                require(body.getX()<f.root.getX()&&AirlockGeometry.touchesDoorway(body.getBoundingBox(),doorway),"body center is outside but its bbox is face-tangent");
                float blunt=DamageSystem.existingOrVanillaBaseline(body).getOrDefault(DamageKeys.BLUNT,0f);
                float shock=DamageSystem.existingOrVanillaBaseline(body).getOrDefault(DamageKeys.SHOCK,0f);
                waitForPoweredPhase(helper,f,AirlockState.DoorPhase.OPEN,0,()->{
                    require(f.door.activate(),"normal closing begins with a safe tangent boundary contact; "+criticalCloseDiagnostic(f,body));
                    require(f.door.state().phase()==AirlockState.DoorPhase.CLOSING,
                            "admitted normal close transitions directly to CLOSING; "+criticalCloseDiagnostic(f,body));
                com.juicyslew.moonstation14.gametest.support.BoundedGameTestWait.safeDelay(helper, 9,()->{
                    require(Math.abs(DamageSystem.existingOrVanillaBaseline(body).getOrDefault(DamageKeys.SHOCK,0f)-shock-7f)<.001f,"passive partial-close contact shocks once");
                    require(DamageSystem.existingOrVanillaBaseline(body).getOrDefault(DamageKeys.BLUNT,0f)==blunt,"tangent actor is not a crush victim");
                    require(CharacterControlSystem.isStunned(body),"contact left Critical actor paralyzed before the normal close transition");
                     waitForCloseRecovery(helper,f,30,body,()->{
                        require(f.door.state().phase()==AirlockState.DoorPhase.OPENING||f.door.state().phase()==AirlockState.DoorPhase.OPEN,
                                "closed edge uses quiet normal opening despite Critical/stunned body");
                        require(Math.abs(DamageSystem.existingOrVanillaBaseline(body).getOrDefault(DamageKeys.SHOCK,0f)-shock-7f)<.001f,
                                "closed-edge bump recovery does not dispatch a duplicate electrical contact");
                        body.discard();helper.succeed();
                    });
                });
                });
            });});
        });
    }

    @GameTestTags({"airlock_stationary_contact", "airlock_crush", "death"})
    @GameTest(templateNamespace="moonstation14",template="power_live_chain_clear",batch="airlock_connected",timeoutTicks=100)
    public static void safeCloseTreatsLoadedMedicalDeadBodyAsObstruction(GameTestHelper helper){
        Fixture f=poweredAirlock(helper);
        waitForPoweredAdmission(helper,f,0,()->{require(f.door.activate(),"paid opening starts");waitForDoorPhase(helper,f,AirlockState.DoorPhase.OPEN,30,()->{
            Villager corpse=enrolledBody(f.level,f.root.getX()+.5,f.root.getY()+.1,f.root.getZ()+.5);
            corpse.setNoGravity(true);
            require(DamageSystem.applyHealthChange(corpse,java.util.Map.of(DamageKeys.BLUNT,200f),1f,true)==DamageSystem.Result.APPLIED
                            &&MobStateSystem.isDead(corpse)&&!corpse.isAlive(),"loaded body reaches resolved medical DEAD state");
            float blunt=DamageSystem.existingOrVanillaBaseline(corpse).getOrDefault(DamageKeys.BLUNT,0f);
            require(!f.door.activate()&&f.door.state().phase()==AirlockState.DoorPhase.OPEN,
                    "default safety sees the physical dead body and keeps the opening door open");
            require(DamageSystem.existingOrVanillaBaseline(corpse).getOrDefault(DamageKeys.BLUNT,0f)==blunt,
                    "safe obstruction does not apply unsafe crush damage");
            corpse.discard();helper.succeed();
        });});
    }

    @GameTestTags({"airlock_stationary_contact", "airlock_crush", "death"})
    @GameTest(templateNamespace="moonstation14",template="power_live_chain_clear",batch="airlock_connected",timeoutTicks=150)
    public static void unsafeCrushRetainsLoadedMedicalDeadBodyUntilPaidReopen(GameTestHelper helper){
        Fixture f=poweredAirlock(helper);
        waitForPoweredAdmission(helper,f,0,()->{require(f.door.activate(),"paid opening starts");waitForDoorPhase(helper,f,AirlockState.DoorPhase.OPEN,30,()->{
            Villager corpse=enrolledBody(f.level,f.root.getX()+.5,f.root.getY()+.1,f.root.getZ()+.5);
            corpse.setNoGravity(true);
            require(DamageSystem.applyHealthChange(corpse,java.util.Map.of(DamageKeys.BLUNT,200f),1f,true)==DamageSystem.Result.APPLIED
                            &&MobStateSystem.isDead(corpse)&&!corpse.isAlive(),"loaded body is typed medical DEAD before unsafe closing");
            require(f.door.applyWire(f.door.identity(),f.door.state().revision(),AirlockState.Wire.SAFETY,AirlockState.WireAction.CUT),"safety wire permits unsafe crush");
            require(f.door.activate(),"unsafe close begins around the dead physical body");
             com.juicyslew.moonstation14.gametest.support.BoundedGameTestWait.safeDelay(helper, 9,()->{
                require(f.door.state().phase()==AirlockState.DoorPhase.CLOSING_PASSABLE,"dead collider participates in unsafe partial-close capture");
                require(f.door.getUpdateTag(f.level.registryAccess()).getList("CrushVictims",net.minecraft.nbt.Tag.TAG_COMPOUND).stream()
                                .anyMatch(tag->tag instanceof net.minecraft.nbt.CompoundTag compound&&compound.hasUUID("Id")&&compound.getUUID("Id").equals(corpse.getUUID())),
                        "retained medical corpse is root-owned by the existing crush collision exemption");
                require(f.door.crushReopenRetryAt()>f.level.getGameTime(),"corpse capture retains the normal separate paid-reopen deadline");
                 com.juicyslew.moonstation14.gametest.support.BoundedGameTestWait.safeDelay(helper, 8,()->{
                    require(f.door.state().phase()==AirlockState.DoorPhase.CLOSED&&MobStateSystem.isDead(corpse),"dead body remains loaded through the ordinary sealed interval");
                    waitForCloseRecovery(helper,f,60,()->{
                        require(f.door.state().phase()==AirlockState.DoorPhase.OPENING||f.door.state().phase()==AirlockState.DoorPhase.OPEN,
                                "existing fresh-paid crush reopen releases the loaded medical corpse");
                        corpse.discard();helper.succeed();
                    });
                });
            });
        });});
    }

    @GameTestTags({"airlock_stationary_contact", "airlock_crush"})
    @GameTest(templateNamespace="moonstation14",template="power_live_chain_clear",batch="airlock_connected",timeoutTicks=180)
    public static void safeLateObstructionAbortsWithoutCrushOrContactShock(GameTestHelper helper){
        Fixture f=poweredAirlock(helper);
        waitForPoweredAdmission(helper,f,0,()->{
            require(f.door.applyWire(f.door.identity(),f.door.state().revision(),AirlockState.Wire.POWER_ONE,AirlockState.WireAction.CUT),"electrification enabled with one cut wire");
            waitForPoweredAdmission(helper,f,0,()->{
                require(f.door.activate(),"door begins open");com.juicyslew.moonstation14.gametest.support.BoundedGameTestWait.safeDelay(helper, 14,()->{
                    Villager entrant=enrolledBody(f.level,f.root.getX()+1.5,f.root.getY(),f.root.getZ()+.5);
                    float blunt=DamageSystem.existingOrVanillaBaseline(entrant).getOrDefault(DamageKeys.BLUNT,0f);
                    float shock=DamageSystem.existingOrVanillaBaseline(entrant).getOrDefault(DamageKeys.SHOCK,0f);
                    require(f.door.activate(),"empty safe close starts");com.juicyslew.moonstation14.gametest.support.BoundedGameTestWait.safeDelay(helper, 4,()->{
                        entrant.setPos(f.root.getX()+.5,f.root.getY()+.1,f.root.getZ()+.5);
                        com.juicyslew.moonstation14.gametest.support.BoundedGameTestWait.safeDelay(helper, 5,()->{
                            require(f.door.state().phase()==AirlockState.DoorPhase.OPEN,"late entrant aborts safe close before solidification");
                            require(DamageSystem.existingOrVanillaBaseline(entrant).getOrDefault(DamageKeys.BLUNT,0f)==blunt
                                            &&DamageSystem.existingOrVanillaBaseline(entrant).getOrDefault(DamageKeys.SHOCK,0f)==shock,
                                    "safe abort neither crushes nor dispatches stationary closing contact");
                            entrant.discard();helper.succeed();
                        });
                    });
                });
            });
        });
    }

    @GameTestTags({"airlock_stationary_contact", "airlock_crush"})
    @GameTest(templateNamespace="moonstation14",template="power_live_chain_clear",batch="airlock_connected",timeoutTicks=180)
    public static void powerLossDuringUnsafeCloseAndAdjacentActorDoNotShock(GameTestHelper helper){
        Fixture f=poweredAirlock(helper);
        waitForPoweredAdmission(helper,f,0,()->{require(f.door.activate(),"airlock begins opening");com.juicyslew.moonstation14.gametest.support.BoundedGameTestWait.safeDelay(helper, 14,()->{
            Villager inside=enrolledBody(f.level,f.root.getX()+.5,f.root.getY()+.1,f.root.getZ()+.5);
            Villager adjacent=enrolledBody(f.level,f.root.getX()+1.2,f.root.getY()+.1,f.root.getZ()+.5);
            float shock=DamageSystem.existingOrVanillaBaseline(inside).getOrDefault(DamageKeys.SHOCK,0f);
            float adjacentShock=DamageSystem.existingOrVanillaBaseline(adjacent).getOrDefault(DamageKeys.SHOCK,0f);
            float blunt=DamageSystem.existingOrVanillaBaseline(inside).getOrDefault(DamageKeys.BLUNT,0f);
            require(f.door.applyWire(f.door.identity(),f.door.state().revision(),AirlockState.Wire.SAFETY,AirlockState.WireAction.CUT),"unsafe crush enabled");
            require(f.door.activate(),"unsafe close starts");
            require(f.door.applyWire(f.door.identity(),f.door.state().revision(),AirlockState.Wire.POWER_ONE,AirlockState.WireAction.CUT),"first power wire is cut during closing");
            require(f.door.applyWire(f.door.identity(),f.door.state().revision(),AirlockState.Wire.POWER_TWO,AirlockState.WireAction.CUT),"second power wire removes power and disables electrification during closing");
            com.juicyslew.moonstation14.gametest.support.BoundedGameTestWait.safeDelay(helper, 9,()->{
                require(Math.abs(DamageSystem.existingOrVanillaBaseline(inside).getOrDefault(DamageKeys.BLUNT,0f)-blunt-15f)<.001f,"inactive-electrification door retains ordinary crush");
                require(DamageSystem.existingOrVanillaBaseline(inside).getOrDefault(DamageKeys.SHOCK,0f)==shock,"inactive electrification emits no shock");
                require(DamageSystem.existingOrVanillaBaseline(adjacent).getOrDefault(DamageKeys.SHOCK,0f)==adjacentShock,"actor outside exact doorway is not scanned as a contact");
                inside.discard();adjacent.discard();helper.succeed();
            });
        });});
    }

    private static void assertStationaryCrushContact(GameTestHelper helper,Fixture f,Villager victim,
                                                       net.minecraft.world.phys.Vec3 pos,float shockBefore,
                                                       float bluntBefore,Runnable done) {
        float shock=DamageSystem.existingOrVanillaBaseline(victim).getOrDefault(DamageKeys.SHOCK,0f)-shockBefore;
        float blunt=DamageSystem.existingOrVanillaBaseline(victim).getOrDefault(DamageKeys.BLUNT,0f)-bluntBefore;
        require(Math.abs(shock-7f)<.001f,"captured stationary Critical actor receives exactly one root BUMP shock; delta="+shock);
        require(Math.abs(blunt-15f)<.001f,"existing crush damage remains exactly 15");
        require(victim.position().distanceToSqr(pos)<1e-8,"stationary overlap is not moved during solidification");
        require(f.door.state().phase()==AirlockState.DoorPhase.CLOSING_PASSABLE,"contact runs on the actual partial-close edge");
        helper.runAfterDelay(2,()->{
            float repeated=DamageSystem.existingOrVanillaBaseline(victim).getOrDefault(DamageKeys.SHOCK,0f)-shockBefore;
            require(Math.abs(repeated-7f)<.001f,"scheduled callbacks do not repeat the same solidification contact");done.run();
        });
    }

    private static void waitForDoorPhase(GameTestHelper helper,Fixture f,AirlockState.DoorPhase phase,int remaining,Runnable done){
        if(f.door.state().phase()==phase){done.run();return;}
        if(remaining<=0)throw new GameTestAssertException("door did not reach expected phase="+phase+" actual="+f.door.state().phase());
        com.juicyslew.moonstation14.gametest.support.BoundedGameTestWait.safeDelay(helper,1,()->waitForDoorPhase(helper,f,phase,remaining-1,done));
    }
    private static void waitForCloseRecovery(GameTestHelper helper,Fixture f,int remaining,Runnable done){
        waitForCloseRecovery(helper,f,remaining,null,done);
    }
    private static void waitForCloseRecovery(GameTestHelper helper,Fixture f,int remaining,Villager actor,Runnable done){
        var phase=f.door.state().phase();
        if(phase==AirlockState.DoorPhase.OPENING||phase==AirlockState.DoorPhase.OPEN){done.run();return;}
        if(remaining<=0)throw new GameTestAssertException("close did not recover to OPENING/OPEN; phase="+phase+" actor="+actorDiagnostic(f,actor));
        com.juicyslew.moonstation14.gametest.support.BoundedGameTestWait.safeDelay(helper,1,()->waitForCloseRecovery(helper,f,remaining-1,actor,done));
    }

    private static void traceCrushCycle(GameTestHelper helper,Fixture f,Villager victim,float bluntBefore,
                                         java.util.EnumSet<AirlockState.DoorPhase> postCrushPhases,boolean[] reopened,
                                         long[] openingStartedAt,int remaining) {
        AirlockState state=f.door.state();
        if(!reopened[0]&&state.phase()==AirlockState.DoorPhase.OPENING
                &&postCrushPhases.contains(AirlockState.DoorPhase.CLOSING_PASSABLE)) {
            reopened[0]=true;
            openingStartedAt[0]=f.door.clientView().transitionStartedAt();
            require(openingStartedAt[0]==f.level.getGameTime(),"crush release resets the client animation start on entry to fresh opening");
            postCrushPhases.clear();
        }
        if(reopened[0]&&state.phase()==AirlockState.DoorPhase.OPENING) {
            require(f.door.clientView().transitionStartedAt()==openingStartedAt[0],"opening animation timestamp remains stable during the first reopening phase");
            require(f.level.getGameTime()-openingStartedAt[0]<8,"first reopening phase lasts the normal .4 seconds");
        }
        if(reopened[0])postCrushPhases.add(state.phase());
        else if(state.phase()==AirlockState.DoorPhase.CLOSING_PASSABLE)postCrushPhases.add(state.phase());
        if(state.phase()==AirlockState.DoorPhase.CLOSING_PASSABLE
                &&DamageSystem.existingOrVanillaBaseline(victim).getOrDefault(DamageKeys.BLUNT,0f)-bluntBefore>=30f) {
            require(reopened[0]&&postCrushPhases.contains(AirlockState.DoorPhase.OPENING)
                            &&postCrushPhases.contains(AirlockState.DoorPhase.OPENING_PASSABLE)
                            &&postCrushPhases.contains(AirlockState.DoorPhase.OPEN),"crush release traversed both opening phases and fully OPEN before next close");
            require(openingStartedAt[0]>0,"client animation start timestamp is published for fresh crush opening");
            require(f.door.isCrushExempt(net.minecraft.world.phys.shapes.CollisionContext.of(victim)),"crushed victim remains collision-exempt through close cycle");
            require(CharacterControlSystem.isStunned(victim),"victim remains stunned at the next crush transition");
            victim.discard();helper.succeed();return;
        }
        if(remaining<=0)throw new GameTestAssertException("crush cycle did not reach next damaging partial close; phases="+postCrushPhases+" current="+state);
        com.juicyslew.moonstation14.gametest.support.BoundedGameTestWait.safeDelay(helper,1,()->traceCrushCycle(helper,f,victim,bluntBefore,postCrushPhases,reopened,openingStartedAt,remaining-1));
    }

    private static void assertVictimStable(GameTestHelper helper,Fixture f,Villager victim,net.minecraft.world.phys.Vec3 expectedCenter,float health,float bluntBefore,int remaining,Runnable done) {
        if(remaining<=0){done.run();return;}
        com.juicyslew.moonstation14.gametest.support.BoundedGameTestWait.safeDelay(helper,1,()->{
            var center=victim.getBoundingBox().getCenter();
            require(Math.abs(center.x-expectedCenter.x)<1e-6&&Math.abs(center.y-expectedCenter.y)<1e-6&&Math.abs(center.z-expectedCenter.z)<1e-6,"crushed victim bounding-box center remains stable during blocked close/reopen wait; "+actorDiagnostic(f,victim));
            require(Math.abs(victim.getHealth()-health)<1e-6,"no recurring vanilla in-wall damage while victim remains trapped; expectedHealth="+health+" "+actorDiagnostic(f,victim));
            assertVictimStable(helper,f,victim,expectedCenter,health,bluntBefore,remaining-1,done);
        });
    }

    private static String actorDiagnostic(Fixture f,Villager actor){
        if(actor==null)return "actor=<not supplied>";
        var box=actor.getBoundingBox();
        String contact="unknown",safety="unknown",power="unknown";
        if(f!=null){
            contact=Boolean.toString(AirlockGeometry.touchesDoorway(box,new net.minecraft.world.phys.AABB(f.root.getX(),f.root.getY(),f.root.getZ(),f.root.getX()+1,f.root.getY()+2,f.root.getZ()+1)));
            safety="enabled="+f.door.state().safety()+",cut="+f.door.state().safetyWireCut();
            var sample=PowerRuntime.airlockPower(f.level,f.door);power="known="+sample.known()+",watts="+sample.deliveredWatts();
        }
        boolean present=actor.level().getEntitiesOfClass(Villager.class,box.inflate(.01)).stream().anyMatch(entity->entity.getUUID().equals(actor.getUUID()));
        return "uuid="+actor.getUUID()+",presentViaQuery="+present+",removed="+actor.isRemoved()+",alive="+actor.isAlive()+",medicalCritical="+MobStateSystem.isCritical(actor)+",medicalDead="+MobStateSystem.isDead(actor)+",stunned="+CharacterControlSystem.isStunned(actor)+",knockedDown="+CharacterControlSystem.isKnockedDown(actor)+",pose="+actor.getPose()+",pos="+actor.position()+",bbox="+box+",touchesDoorway="+contact+",health="+actor.getHealth()+",doorSafety="+safety+",power="+power;
    }

    private static void waitForPaidCrushReopen(GameTestHelper helper,Fixture f,Villager victim,Villager outside,
                                                net.minecraft.world.phys.Vec3 expectedCenter,float health,float bluntBefore,long crushedAt,int remaining) {
        var sample=PowerRuntime.airlockPower(f.level,f.door);
        if(sample.known()&&sample.deliveredWatts()==f.door.prototype().apcPowerReceiver().requestedWatts()
                &&f.door.state().phase()==AirlockState.DoorPhase.OPEN) {
            require(f.level.getGameTime()-crushedAt>=40,"scheduled crush release does not reopen before the resolved 2s deadline");
            require(f.level.getBlockState(f.root).getValue(AirlockBlock.OPEN),"paid scheduled retry opens lower cell");
            var center=victim.getBoundingBox().getCenter();
            require(Math.abs(center.x-expectedCenter.x)<1e-6&&Math.abs(center.y-expectedCenter.y)<1e-6&&Math.abs(center.z-expectedCenter.z)<1e-6&&Math.abs(victim.getHealth()-health)<1e-6,"victim remains undamaged and unejected until reopen; expectedCenter="+expectedCenter+",expectedHealth="+health+", "+actorDiagnostic(f,victim));
            float finalBlunt=DamageSystem.existingOrVanillaBaseline(victim).getOrDefault(DamageKeys.BLUNT,0f);
            require(Math.abs(finalBlunt-bluntBefore-15f)<.001f,"crush blunt damage remains exactly one 15-point application across extended hold");
            require(f.door.getUpdateTag(f.level.registryAccess()).getList("CrushVictims",net.minecraft.nbt.Tag.TAG_COMPOUND).isEmpty(),
                    "opening clears transient victim collision exemptions");
            victim.discard();outside.discard();helper.succeed();return;
        }
        if(remaining<=0)throw new GameTestAssertException("scheduled crush reopen never regained fresh full power: known="+sample.known()+" watts="+sample.deliveredWatts()+" state="+f.door.state());
        com.juicyslew.moonstation14.gametest.support.BoundedGameTestWait.safeDelay(helper,1,()->waitForPaidCrushReopen(helper,f,victim,outside,expectedCenter,health,bluntBefore,crushedAt,remaining-1));
    }

    private static void waitForPoweredAdmission(GameTestHelper helper, Fixture f, int waited, Runnable admitted) {
        var observation=PowerRuntime.airlockPower(f.level,f.door);
        double demand=f.door.prototype().apcPowerReceiver().requestedWatts();
        if(observation.known()&&observation.deliveredWatts()==demand) {
            require(f.level.getBlockEntity(f.root)==f.door,"resolved root controller identity remains current before activation");
            require(f.door.state().phase()==AirlockState.DoorPhase.CLOSED,"airlock remains closed while awaiting full-power admission");
            admitted.run();
            return;
        }
        if(waited>=120) throw new GameTestAssertException("connected airlock never reached fresh full-power admission: known="+observation.known()
                +" delivered="+observation.deliveredWatts()+" resolvedDemand="+demand+" topologyReady="+com.juicyslew.moonstation14.ms14.power.graph.PowerGraphService.topologyReady(f.level)
                +" rootCurrent="+(f.level.getBlockEntity(f.root)==f.door)+" phase="+f.door.state().phase()+" gameTime="+f.level.getGameTime());
        com.juicyslew.moonstation14.gametest.support.BoundedGameTestWait.safeDelay(helper,1,()->waitForPoweredAdmission(helper,f,waited+1,admitted));
    }

    private static void waitForPoweredPhase(GameTestHelper helper, Fixture f, AirlockState.DoorPhase expected,
                                             int waited, Runnable admitted) {
        waitForPoweredPhase(helper,f,expected,waited,80,admitted);
    }

    private static void waitForPoweredPhase(GameTestHelper helper, Fixture f, AirlockState.DoorPhase expected,
                                             int waited,int maxWait,Runnable admitted) {
        var observation=PowerRuntime.airlockPower(f.level,f.door);
        double demand=f.door.prototype().apcPowerReceiver().requestedWatts();
        var phase=f.door.state().phase();
        if(observation.known()&&observation.deliveredWatts()==demand) {
            require(f.level.getBlockEntity(f.root)==f.door,"root controller identity before later activation");
            require(phase==expected,"fresh full-power readiness phase mismatch; expected="+expected+" actual="+phase
                    +" known="+observation.known()+" watts="+observation.deliveredWatts()+" demand="+demand
                    +" bolts="+f.door.state().boltsDown()+" disabled="+f.door.state().receiverPowerDisabled()
                    +" now="+f.level.getGameTime()+" deadline="+f.door.state().autoCloseAt()+" root="+f.root);
            admitted.run();return;
        }
        if(waited>=maxWait || (f.door.state().autoCloseAt()>0&&f.level.getGameTime()>=f.door.state().autoCloseAt()))
            throw new GameTestAssertException("fresh full-power readiness timeout; expectedPhase="+expected+" actualPhase="+phase
                    +" known="+observation.known()+" watts="+observation.deliveredWatts()+" demand="+demand
                    +" bolts="+f.door.state().boltsDown()+" disabled="+f.door.state().receiverPowerDisabled()
                    +" now="+f.level.getGameTime()+" deadline="+f.door.state().autoCloseAt()+" root="+f.root);
        com.juicyslew.moonstation14.gametest.support.BoundedGameTestWait.safeDelay(helper,1,()->waitForPoweredPhase(helper,f,expected,waited+1,maxWait,admitted));
    }

    private static String criticalCloseDiagnostic(Fixture f,Villager actor) {
        return "power="+PowerRuntime.airlockPower(f.level,f.door)+", actorBBox="+actor.getBoundingBox()
                +", doorState="+f.door.state();
    }

    @GameTest(templateNamespace = "moonstation14", template = "atmos_large_empty", batch = "airlock_gas_flow", timeoutTicks = 500)
    public static void scheduledDoorOpeningTransportsAndConservesFiniteGas(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos root = helper.absolutePos(new BlockPos(3, 1, 9));
        clearFixtureVolume(level, root, 2, 8);
        // Two sealed one-cell chambers communicate only through the two-cell airlock.
        BlockPos left = root.north(), right = root.south();
        require(level.getBlockState(left).isAir() && level.getBlockState(right).isAir(),
                "intended chamber cells start as air: root=" + root + " left=" + left + " "
                        + level.getBlockState(left) + " right=" + right + " " + level.getBlockState(right));
        for (BlockPos chamber : List.of(left, right)) {
            level.setBlock(chamber.below(), Blocks.STONE.defaultBlockState(), 3);
            level.setBlock(chamber.above(2), Blocks.STONE.defaultBlockState(), 3);
            for (Direction d : Direction.values()) {
                BlockPos wall = chamber.relative(d);
                if (wall.equals(root) || wall.equals(root.above())) continue;
                if (wall.equals(left) || wall.equals(right)) continue;
                level.setBlock(wall, Blocks.STONE.defaultBlockState(), 3);
            }
        }
        // Seal the other faces of each chamber and the exterior sides of the portal.
        level.setBlock(left.north(), Blocks.STONE.defaultBlockState(), 3);
        level.setBlock(right.south(), Blocks.STONE.defaultBlockState(), 3);
        // The lower portal has only north/south chamber faces exposed; close its floor
        // and east/west sides. The upper portal has a sealed roof and sealed lateral faces.
        level.setBlock(root.below(), Blocks.STONE.defaultBlockState(), 3);
        level.setBlock(root.west(), Blocks.STONE.defaultBlockState(), 3);
        level.setBlock(root.above().west(), Blocks.STONE.defaultBlockState(), 3);
        level.setBlock(root.above().east(), Blocks.STONE.defaultBlockState(), 3);
        level.setBlock(root.above(2), Blocks.STONE.defaultBlockState(), 3);
        require(level.getBlockState(left).isAir() && level.getBlockState(right).isAir(),
                "chamber construction leaves target cells open: root=" + root + " left=" + left + " "
                        + level.getBlockState(left) + " right=" + right + " " + level.getBlockState(right));
        var lower = ModBlocks.AIRLOCK.get().defaultBlockState();
        level.setBlock(root, lower, 3);
        level.setBlock(root.above(), lower.setValue(AirlockBlock.HALF, DoubleBlockHalf.UPPER), 3);
        AirlockBlockEntity door = (AirlockBlockEntity) level.getBlockEntity(root);
        require(door != null, "root controller exists");

        AtmosphereService atmosphere = AtmosphereService.INSTANCE;
        int[] atmosphereClock = {0};
        classify(atmosphere, level, left, atmosphereClock);
        classify(atmosphere, level, right, atmosphereClock);
        require(atmosphere.addGas(level, left, GasType.NITROGEN, 40, 300), "seed high-pressure chamber");
        require(atmosphere.addGas(level, right, GasType.NITROGEN, 2, 300), "seed low-pressure chamber");

        connectPower(helper, root, door);
        forcePowerFixtureChunks(level,root,door.prototype().extensionCableReceiver().localReachBlocks());
        Fixture fixture = new Fixture(level, root, door);
        waitForPoweredAdmission(helper, fixture, 0, () -> {
            List<BlockPos> inventoryCells = List.of(left, right, root, root.above());
            // Closed portal cells may be blocked rather than live-sampled. In a vacuum
            // dimension their pre-opening ambient inventory is zero; count any existing
            // persisted finite override too, without fabricating claims for blocked cells.
            Inventory before = inventory(level, inventoryCells, false);
            var countersBefore = atmosphere.diagnosticRuntime(level);
            long faceTransfersBefore = countersBefore.pressureFaceTransfers();
            long lindaPairsBefore = countersBefore.lindaSharedPairs();
            double beforeTotal = before.nitrogen;
            double beforeLeft = sample(atmosphere, level, left).moles(GasType.NITROGEN);
            double beforeRight = sample(atmosphere, level, right).moles(GasType.NITROGEN);
            require(door.activate(), "airlock activation starts real scheduled opening");
            com.juicyslew.moonstation14.gametest.support.BoundedGameTestWait.safeDelay(helper, 14, () -> {
                require(door.state().phase() == AirlockState.DoorPhase.OPEN, "both transition stages have elapsed; state="+door.state());
                atmosphere.topologyChanged(level, root);
                atmosphere.topologyChanged(level, root.above());
                // Drive full ownership, pressure-solver, and LINDA service stages. These
                // callbacks don't advance server time, so the scheduled door remains open.
                for (int i = 0; i < 160; i++) atmosphere.tick(level, ++atmosphereClock[0]);
                double afterLeft = sample(atmosphere, level, left).moles(GasType.NITROGEN);
                double afterRight = sample(atmosphere, level, right).moles(GasType.NITROGEN);
                require(afterLeft < beforeLeft && afterRight > beforeRight,
                        "actual atmosphere solver transports gas from high to low pressure through open door");
                Inventory after = inventory(level, inventoryCells, true);
                var countersAfter = atmosphere.diagnosticRuntime(level);
                long faceTransfers = countersAfter.pressureFaceTransfers() - faceTransfersBefore;
                require(faceTransfers > 0, "pressure-face solver committed real transfer operations");
                double operations = 1 + faceTransfers + countersAfter.lindaSharedPairs() - lindaPairsBefore;
                double nitrogenBudget = nitrogenUlpBudget(before, after, operations);
                double energyBudget = energyUlpBudget(before, after, operations);
                require(Math.abs(after.nitrogen - before.nitrogen) <= nitrogenBudget,
                        "finite chamber and portal nitrogen is conserved within operation-derived float rounding; "
                                + conservationDiagnostic(atmosphere, level, inventoryCells, before, after, nitrogenBudget, operations));
                require(Math.abs(after.energy - before.energy) <= energyBudget,
                        "finite chamber and portal thermal energy is conserved within operation-derived float rounding");
                waitForPoweredPhase(helper,fixture,AirlockState.DoorPhase.OPEN,0,()->{
                require(door.activate(), "actual controller starts scheduled closing");
                com.juicyslew.moonstation14.gametest.support.BoundedGameTestWait.safeDelay(helper, 14, () -> {
                    require(door.state().phase() == AirlockState.DoorPhase.CLOSED, "scheduled close completes");
                    atmosphere.topologyChanged(level, root);
                    atmosphere.topologyChanged(level, root.above());
                    for (int i = 0; i < 160; i++) atmosphere.tick(level, ++atmosphereClock[0]);
                    Inventory closed = inventory(level, inventoryCells, true);
                    require(Math.abs(closed.nitrogen - beforeTotal) <= nitrogenUlpBudget(before, closed, operations),
                            "closed doorway retains finite nitrogen ownership; "
                                    + conservationDiagnostic(atmosphere, level, inventoryCells, before, closed,
                                    nitrogenUlpBudget(before, closed, operations), operations));
                    require(Math.abs(closed.energy - before.energy) <= energyUlpBudget(before, closed, operations),
                            "closed doorway retains finite thermal energy");
                    require(Math.abs(sample(atmosphere, level, left).moles(GasType.NITROGEN) - afterLeft) <= nitrogenBudget
                                    && Math.abs(sample(atmosphere, level, right).moles(GasType.NITROGEN) - afterRight) <= nitrogenBudget,
                            "closed door prevents subsequent cross-chamber exchange");
                    helper.succeed();
                });
                });
            });
        });
    }

    private record Fixture(ServerLevel level, BlockPos root, AirlockBlockEntity door) { }

    private static Fixture poweredAirlock(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos root = helper.absolutePos(new BlockPos(3, 1, 9));
        clearFixtureVolume(level, root, 2, 8);
        Fixture f = connectPower(helper, root, null);
        var lower = ModBlocks.AIRLOCK.get().defaultBlockState();
        level.setBlock(root, lower, 3);
        level.setBlock(root.above(), lower.setValue(AirlockBlock.HALF, DoubleBlockHalf.UPPER), 3);
        AirlockBlockEntity door=(AirlockBlockEntity)level.getBlockEntity(root);
        forcePowerFixtureChunks(level,root,door.prototype().extensionCableReceiver().localReachBlocks());
        return new Fixture(level, root, door);
    }

    private static Fixture connectPower(GameTestHelper helper, BlockPos root, AirlockBlockEntity existing) {
        ServerLevel level = helper.getLevel();
        for (int dz = -7; dz <= 0; dz++) level.setBlock(root.offset(1, 0, dz), Blocks.STONE.defaultBlockState(), 3);
        for (int dz = -5; dz <= -3; dz++) level.setBlock(root.offset(-1, 0, dz), Blocks.STONE.defaultBlockState(), 3);
        place(level, root.offset(0, 0, -7), PowerDeviceKind.HV_SOURCE, Direction.EAST);
        place(level, root.offset(0, 0, -5), PowerDeviceKind.HV_MV_SUBSTATION, Direction.EAST);
        place(level, root.offset(0, 0, -3), PowerDeviceKind.APC, Direction.WEST);
        for (int dz = -7; dz <= -5; dz++) require(CableStorage.place(level, root.offset(1, 0, dz), Direction.WEST, CableTier.HV), "HV cable placement succeeds at " + root.offset(1, 0, dz));
        for (int dz = -5; dz <= -3; dz++) require(CableStorage.place(level, root.offset(-1, 0, dz), Direction.EAST, CableTier.MV), "MV cable placement succeeds at " + root.offset(-1, 0, dz));
        for (int dz = -3; dz <= 0; dz++) require(CableStorage.place(level, root.offset(1, 0, dz), Direction.WEST, CableTier.APC), "APC cable placement succeeds at " + root.offset(1, 0, dz));
        return new Fixture(level, root, existing);
    }

    private static void forcePowerFixtureChunks(ServerLevel level,BlockPos root,int reach){
        int minX=root.getX()-Math.max(reach,1)-2,maxX=root.getX()+Math.max(reach,1)+2;
        int minZ=root.getZ()-9,maxZ=root.getZ()+Math.max(reach,1)+2;
        for(int cx=(minX>>4);cx<=(maxX>>4);cx++)for(int cz=(minZ>>4);cz<=(maxZ>>4);cz++){
            long key=net.minecraft.world.level.ChunkPos.asLong(cx,cz);
            if(!level.getForcedChunks().contains(key)){level.setChunkForced(cx,cz,true);FORCED_POWER_FIXTURE_CHUNKS.add(key);}
            level.getChunk(cx,cz);
        }
    }

    private static void clearFixtureVolume(ServerLevel level, BlockPos root, int horizontalRadius, int depth) {
        // Structure templates may retain their BARRIER fill; prepare only this fixture's
        // bounded working volume and leave the template boundary untouched.
        for (int x = -horizontalRadius; x <= horizontalRadius; x++)
            for (int y = 0; y <= 3; y++)
                for (int z = -depth; z <= 2; z++)
                    level.setBlock(root.offset(x, y, z), Blocks.AIR.defaultBlockState(), 3);
    }

    private static void place(ServerLevel level, BlockPos pos, PowerDeviceKind kind, Direction facing) {
        var block = switch (kind) {
            case HV_SOURCE -> ModBlocks.HV_SOURCE.get();
            case HV_MV_SUBSTATION -> ModBlocks.HV_MV_SUBSTATION.get();
            case APC -> ModBlocks.APC.get();
            default -> throw new IllegalArgumentException("unexpected power device");
        };
        level.setBlock(pos, block.defaultBlockState().setValue(PowerDeviceBlock.FACING, facing), 3);
    }

    private static Villager enrolledBody(ServerLevel level, double x, double y, double z) {
        Villager body = EntityType.VILLAGER.create(level);
        require(body != null, "villager fixture created");
        body.setNoAi(true); body.setNoGravity(true); body.setPos(x, y, z);
        require(level.addFreshEntity(body) && CharacterIdentitySystem.enroll(body, level, ModCharacters.HUMAN_ID),
                "crush victim enrolled as supported human character");
        require(BodySystem.reconcile(body), "enrolled body physiology reconciles");
        return body;
    }

    /** Position using the post-transition native AABB; posture/death can refresh dimensions. */
    private static void placeActualBoxAtDoorFace(Villager body,BlockPos root) {
        var box=body.getBoundingBox();
        body.setPos(body.getX()+(root.getX()-box.maxX),body.getY()+root.getY()-box.minY,body.getZ()+root.getZ()+.5-(box.minZ+box.maxZ)*.5);
        for(int i=0;i<4&&body.getBoundingBox().maxX>root.getX();i++)body.setPos(Math.nextDown(body.getX()),body.getY(),body.getZ());
        var placed=body.getBoundingBox();
        var doorway=new net.minecraft.world.phys.AABB(root.getX(),root.getY(),root.getZ(),root.getX()+1,root.getY()+2,root.getZ()+1);
        require(placed.maxX<=root.getX(),"actual body box is strictly non-overlapping at door face: box="+placed+" root="+root);
        require(AirlockGeometry.touchesDoorway(placed,doorway),"actual body box remains within doorway contact tolerance: box="+placed+" root="+root);
    }


    private static void classify(AtmosphereService service, ServerLevel level, BlockPos pos, int[] clock) {
        service.activateCellAndNeighbors(level, pos);
        for (int i = 1; i <= 300; i++) {
            service.tick(level, ++clock[0]);
            if (service.diagnosticCell(level, pos).status().equals("finite")) return;
        }
        StringBuilder neighbors=new StringBuilder();
        for(Direction direction:Direction.values())neighbors.append(' ').append(direction).append('=').append(service.diagnosticCell(level,pos.relative(direction)));
        throw new GameTestAssertException("finite atmosphere ownership did not classify: pos="+pos+" cell="+service.diagnosticCell(level,pos)+" neighbors="+neighbors+" runtime="+service.diagnosticRuntime(level)+" fixtureTicks=300 simulatedTime="+level.getGameTime());
    }

    private static com.juicyslew.moonstation14.ms14.atmos.core.GasMixture sample(AtmosphereService service, ServerLevel level, BlockPos pos) {
        return service.sample(level, pos).orElseThrow(() -> new GameTestAssertException("unclassified gas cell " + pos));
    }

    private record Inventory(double nitrogen, double oxygen, double energy, double nitrogenUlp, double energyUlp) { }

    private static Inventory inventory(ServerLevel level, List<BlockPos> cells, boolean requireFiniteOwnership) {
        double nitrogen = 0, oxygen = 0, energy = 0, nitrogenUlp = 0, energyUlp = 0;
        for (BlockPos pos : cells) {
            LevelChunk chunk = (LevelChunk) level.getChunk(pos);
            var data = chunk.getExistingDataOrNull(ModDataAttachments.ATMOSPHERE_CHUNK.get());
            boolean claimed = data != null && data.isFiniteClaimed(pos.getX() & 15, pos.getY(), pos.getZ() & 15);
            if (requireFiniteOwnership) require(claimed,
                    "each chamber and portal cell remains explicitly finite-owned: " + pos);
            var gas = data == null ? null : data.get(pos.getX() & 15, pos.getY(), pos.getZ() & 15);
            if (requireFiniteOwnership) require(gas != null, "owned chamber/portal gas mixture remains persisted: " + pos);
            if (gas == null) continue; // Vacuum ambient contributes zero moles and energy.
            double cellNitrogen = gas.moles(GasType.NITROGEN), cellEnergy = gas.thermalEnergy();
            nitrogen += cellNitrogen; oxygen += gas.moles(GasType.OXYGEN); energy += cellEnergy;
            nitrogenUlp += Math.ulp((float) cellNitrogen); energyUlp += Math.ulp((float) cellEnergy);
        }
        return new Inventory(nitrogen, oxygen, energy, nitrogenUlp, energyUlp);
    }

    private static String conservationDiagnostic(AtmosphereService atmosphere, ServerLevel level, List<BlockPos> cells,
                                                 Inventory before, Inventory after, double tolerance, double operations) {
        StringBuilder topology = new StringBuilder("cells=[");
        for (BlockPos pos : cells) {
            var diagnostic = atmosphere.diagnosticCell(level, pos);
            LevelChunk chunk = (LevelChunk) level.getChunk(pos);
            var data = chunk.getExistingDataOrNull(ModDataAttachments.ATMOSPHERE_CHUNK.get());
            boolean owned = data != null && data.isFiniteClaimed(pos.getX() & 15, pos.getY(), pos.getZ() & 15);
            topology.append(pos).append("{topology=").append(diagnostic.status()).append("/partition=unavailable")
                    .append(",owned=").append(owned).append(",N2=")
                    .append(diagnostic.gas() == null ? "unobserved" : diagnostic.gas().moles(GasType.NITROGEN))
                    .append(",O2=").append(diagnostic.gas() == null ? "unobserved" : diagnostic.gas().moles(GasType.OXYGEN))
                    .append("},");
        }
        return "beforeN2=" + before.nitrogen + ",afterN2=" + after.nitrogen + ",beforeO2=" + before.oxygen
                + ",afterO2=" + after.oxygen + ",deltaN2=" + (after.nitrogen-before.nitrogen)
                + ",tolerance=" + tolerance + ",operations=" + operations + "," + topology + "]";
    }

    private static double nitrogenUlpBudget(Inventory before, Inventory after, double operations) {
        return operations * (before.nitrogenUlp + after.nitrogenUlp + Math.ulp((float) before.nitrogen)
                + Math.ulp((float) after.nitrogen));
    }

    private static double energyUlpBudget(Inventory before, Inventory after, double operations) {
        return operations * (before.energyUlp + after.energyUlp + Math.ulp((float) before.energy)
                + Math.ulp((float) after.energy));
    }

    private static void require(boolean ok, String message) {
        if (!ok) throw new GameTestAssertException(message);
    }
}
