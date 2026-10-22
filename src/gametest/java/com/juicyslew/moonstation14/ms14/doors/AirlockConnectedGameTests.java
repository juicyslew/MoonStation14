package com.juicyslew.moonstation14.ms14.doors;

import com.juicyslew.moonstation14.block.ModBlocks;
import com.juicyslew.moonstation14.block.custom.PowerDeviceBlock;
import com.juicyslew.moonstation14.component.ModDataAttachments;
import com.juicyslew.moonstation14.gametest.support.GameTestTags;
import com.juicyslew.moonstation14.gametest.support.AtmospherePrecisionBudget;
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
    private static java.util.Set<net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level>> previousVacuumDimensions;
    private AirlockConnectedGameTests() { }

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
                waitForPoweredPhase(helper,f,AirlockState.DoorPhase.OPEN,0,()->{
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
        });
    }

    @GameTest(templateNamespace = "moonstation14", template = "power_live_chain_clear", batch = "airlock_connected", timeoutTicks = 280)
    public static void lateActorDuringSafeCloseAbortsWithoutCrush(GameTestHelper helper) {
        Fixture f=poweredAirlock(helper);
        com.juicyslew.moonstation14.gametest.support.BoundedGameTestWait.safeDelay(helper, 100,()->waitForPoweredAdmission(helper,f,0,()->{
            require(f.door.activate(),"powered airlock starts opening");
            com.juicyslew.moonstation14.gametest.support.BoundedGameTestWait.safeDelay(helper, 14,()->{
                require(f.door.state().phase()==AirlockState.DoorPhase.OPEN,"door is open before close attempt; state="+f.door.state());
                waitForPoweredPhase(helper,f,AirlockState.DoorPhase.OPEN,0,30,()->{
                Villager entrant=enrolledBody(f.level,f.root.getX()+1.5,f.root.getY(),f.root.getZ()+.5);
                entrant.setNoGravity(true);
                float before=DamageSystem.existingOrVanillaBaseline(entrant).getOrDefault(DamageKeys.BLUNT,0f);
                require(f.door.activate()&&f.door.state().phase()==AirlockState.DoorPhase.CLOSING,"empty safe close begins");
                com.juicyslew.moonstation14.gametest.support.BoundedGameTestWait.safeDelay(helper, 4,()->{
                     entrant.setPos(f.root.getX()+.5,f.root.getY(),f.root.getZ()+.5);
                     require(entrant.getBoundingBox().intersects(doorwayBounds(f.root)),
                             "late actor has actual physical overlap with the closing doorway, not a tangent contact");
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

    @GameTestTags({"airlock_crush_cycle", "airlock_crush", "status", "suite-stabilization"})
    @GameTest(templateNamespace="moonstation14",template="power_live_chain_clear",batch="airlock_connected",timeoutTicks=400)
    public static void crushReleaseCompletesNormalOpenBeforeFastAutoClose(GameTestHelper helper) {
        Fixture f=poweredAirlock(helper);
        waitForPoweredAdmission(helper,f,0,()->{
            DoorPhaseTickObserver phaseObserver = new DoorPhaseTickObserver(f.level, f.door);
            long observerLifetime=Math.max(1,399-helper.getTick());
            phaseObserver.register(observerLifetime);
            com.juicyslew.moonstation14.gametest.support.BoundedGameTestWait.safeDelay(helper, observerLifetime, phaseObserver::close);
            try {
            require(f.door.activate(),"freshly paid controller begins normal opening");
              waitForDoorPhase(helper,f,AirlockState.DoorPhase.OPEN,30,()->{
              try {
                require(f.door.state().phase()==AirlockState.DoorPhase.OPENING_PASSABLE,"door is passable during the first opening animation");
                Villager victim=enrolledBody(f.level,f.root.getX()+.5,f.root.getY()+.1,f.root.getZ()+.5);
                float before=DamageSystem.existingOrVanillaBaseline(victim).getOrDefault(DamageKeys.BLUNT,0f);
                phaseObserver.setVictim(victim,before);
                long revision=f.door.state().revision();
                require(f.door.applyWire(f.door.identity(),revision,AirlockState.Wire.SAFETY,AirlockState.WireAction.CUT),"unsafe crush admission uses safety wire");
                revision=f.door.state().revision();
                require(f.door.applyWire(f.door.identity(),revision,AirlockState.Wire.TIMING,AirlockState.WireAction.CUT),"fast auto-close uses timing wire");
                java.util.EnumSet<AirlockState.DoorPhase> postCrushPhases=java.util.EnumSet.noneOf(AirlockState.DoorPhase.class);
                boolean[] reopened={false};
                long[] openingStartedAt={0};
                long[] reopenedOpenDue={0};
                long[] traceStartedAt={-1};
                int[] helperCallbacks={0};
                traceCrushCycle(helper,f,victim,before,postCrushPhases,reopened,openingStartedAt,
                        reopenedOpenDue,phaseObserver,traceStartedAt,helperCallbacks,helper.getTick(),victim.tickCount);
              } catch (RuntimeException | Error failure) {
                  phaseObserver.close();
                  throw failure;
              }
            });
            } catch (RuntimeException | Error failure) {
                phaseObserver.close();
                throw failure;
            }
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
                 // Exercise the documented positive-gap tolerance, not penetration.
                 // Derive the center from the actual native body width and verify the
                 // resulting AABB because entity dimensions/position may be rounded.
                 double expectedGap=5.0E-6;
                 boundary.setPos(f.root.getX()-boundary.getBbWidth()/2.0-expectedGap,boundary.getY(),boundary.getZ());
                 var boundaryPos=boundary.position();
                 var doorway=new net.minecraft.world.phys.AABB(f.root.getX(),f.root.getY(),f.root.getZ(),f.root.getX()+1,f.root.getY()+2,f.root.getZ()+1);
                 double initialGap=doorway.minX-boundary.getBoundingBox().maxX;
                 require(initialGap>0&&initialGap<=1.0E-5&&!boundary.getBoundingBox().intersects(doorway)
                                 &&AirlockGeometry.touchesDoorway(boundary.getBoundingBox(),doorway),
                         "fixture must be a real positive face gap within static-contact tolerance; gap="+initialGap
                                 +", expectedGap="+expectedGap+", body="+boundary.getBoundingBox()+", doorway="+doorway
                                 +", root="+f.root+", width="+boundary.getBbWidth());
                float boundaryBlunt=DamageSystem.existingOrVanillaBaseline(boundary).getOrDefault(DamageKeys.BLUNT,0f);
                float boundaryShock=DamageSystem.existingOrVanillaBaseline(boundary).getOrDefault(DamageKeys.SHOCK,0f);
                float nearbyShockBefore=DamageSystem.existingOrVanillaBaseline(nearby).getOrDefault(DamageKeys.SHOCK,0f);
                DamageSystem.applyHealthChange(victim,java.util.Map.of(DamageKeys.BLUNT,100f),1f,false);
                require(MobStateSystem.isCritical(victim)&&victim.isAlive(),"fixture is an actual living Critical body");
                float shockBefore=DamageSystem.existingOrVanillaBaseline(victim).getOrDefault(DamageKeys.SHOCK,0f);
                float bluntBefore=DamageSystem.existingOrVanillaBaseline(victim).getOrDefault(DamageKeys.BLUNT,0f);
                var pos=victim.position();
                 require(f.door.applyWire(f.door.identity(),f.door.state().revision(),AirlockState.Wire.SAFETY,AirlockState.WireAction.CUT),"safety wire enables crush");
                 waitForPoweredPhase(helper,f,AirlockState.DoorPhase.OPEN,0,()->{
                 require(f.door.activate(),"unsafe close begins");
                 long closeStartedAt=f.level.getGameTime();
                 long solidificationDue=f.door.state().transitionAt();
                 com.juicyslew.moonstation14.gametest.support.BoundedGameTestWait.safeDelay(helper, 9,()->{
                    require(DamageSystem.existingOrVanillaBaseline(nearby).getOrDefault(DamageKeys.SHOCK,0f)==nearbyShockBefore,
                            "powered closing contact does not shock an actor outside the exact doorway");
                      float boundaryShockNow=DamageSystem.existingOrVanillaBaseline(boundary).getOrDefault(DamageKeys.SHOCK,0f);
                      double contactGap=doorway.minX-boundary.getBoundingBox().maxX;
                      require(contactGap>0&&contactGap<=1.0E-5
                                      &&!boundary.getBoundingBox().intersects(doorway)
                                      &&AirlockGeometry.touchesDoorway(boundary.getBoundingBox(),doorway),
                              "face-tolerance actor must still have a positive non-overlapping gap at contact; gap="
                                      +contactGap+", bodyBounds="+boundary.getBoundingBox()+", bodyPos="+boundary.position()
                                      +", onGround="+boundary.onGround()+", critical="+MobStateSystem.isCritical(boundary)
                                      +", root="+f.root+", phase="+f.door.state().phase()+", transitionAt="
                                      +f.door.state().transitionAt());
                     require(Math.abs(boundaryShockNow-boundaryShock-7f)<.001f,
                              "face-tangent living body receives the solidification contact despite its center being outside; "
                                      +"actualShockDelta="+(boundaryShockNow-boundaryShock)+", shockBefore="+boundaryShock
                                      +", initialGap="+initialGap+", contactGap="+contactGap
                                     +", bodyPos="+boundary.position()+", bodyVelocity="+boundary.getDeltaMovement()
                                     +", bodyBounds="+boundary.getBoundingBox()+", startPos="+boundaryPos
                                     +", root="+f.root+", closeStartedAt="+closeStartedAt+", solidificationDue="
                                     +solidificationDue+", serverGameTime="+f.level.getGameTime()+", helperTick="+helper.getTick()
                                     +", serverTick="+f.level.getServer().getTickCount()+", doorState="+f.door.state()
                                     +", powerAvailable="+f.door.state().powerAvailable()+", phase="+f.door.state().phase()
                                     +", transitionAt="+f.door.state().transitionAt()+", autoCloseAt="+f.door.state().autoCloseAt());
                    require(DamageSystem.existingOrVanillaBaseline(boundary).getOrDefault(DamageKeys.BLUNT,0f)==boundaryBlunt,
                            "boundary contact does not enter crush damage; blunt="+DamageSystem.existingOrVanillaBaseline(boundary).getOrDefault(DamageKeys.BLUNT,0f)+" before="+boundaryBlunt+" bbox="+boundary.getBoundingBox()+" root="+f.root);
                    assertStationaryCrushContact(helper,f,victim,pos,shockBefore,bluntBefore,()->
                         com.juicyslew.moonstation14.gametest.support.BoundedGameTestWait.safeDelay(helper, 5,()->{
                            require(f.door.state().phase()==AirlockState.DoorPhase.CLOSED,"normal close reaches sealed phase");
                            waitForDoorPhase(helper,f,AirlockState.DoorPhase.OPEN,80,()->{
                                require(MobStateSystem.isCritical(victim)&&victim.isAlive(),"victim remains living and Critical for another cycle");
                                float secondShock=DamageSystem.existingOrVanillaBaseline(victim).getOrDefault(DamageKeys.SHOCK,0f);
                                require(f.door.activate(),"reopened door begins second close");
                                 // Wait for the native close phase, not a fixed tick count: fixture power may
                                 // pause a transition, and the second contact is valid only at solidification.
                                  waitForDoorPhase(helper,f,AirlockState.DoorPhase.CLOSED,80,()->{
                                     // The phase transition is visible to scheduled GameTest callbacks
                                     // before the entity-world contact scan for that tick. Observe the
                                     // next completed world tick so this assertion measures the
                                     // solidification contact, not merely the CLOSED state edge.
                                     com.juicyslew.moonstation14.gametest.support.BoundedGameTestWait.safeDelay(helper,1,()->{
                                     float total=DamageSystem.existingOrVanillaBaseline(victim).getOrDefault(DamageKeys.SHOCK,0f)-shockBefore;
                                     require(Math.abs(total-14f)<.001f,"new solidification cycle produces exactly one additional 7 shock; total="+total+" secondStart="+secondShock+" "+contactDiagnostic(f,victim,shockBefore,DamageSystem.existingOrVanillaBaseline(victim).getOrDefault(DamageKeys.SHOCK,0f)));
                                     require(Math.abs(DamageSystem.existingOrVanillaBaseline(victim).getOrDefault(DamageKeys.BLUNT,0f)-bluntBefore-30f)<.001f,"both solidification cycles apply one 15-point crush each");
                                     require(MobStateSystem.isCritical(victim),"second contact remains eligible while Critical");
                                     victim.discard();nearby.discard();boundary.discard();helper.succeed();
                                     });
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
        });
    }

    @GameTestTags({"airlock_stationary_contact", "airlock_bump", "status"})
    @GameTest(templateNamespace="moonstation14",template="power_live_chain_clear",batch="airlock_connected",timeoutTicks=160)
    public static void retainedDeadTangentBodyQuietlyReopensEachNormalClose(GameTestHelper helper){
        Fixture f=poweredAirlock(helper);
        TangentTrace trace=new TangentTrace(helper,f);
        FixtureChunkTickets.around(helper,f.root.offset(-2,0,-2),f.root.offset(2,2,2),1);
        trace.mark("initial");
        waitForPoweredAdmission(helper,f,0,()->{
            require(f.door.activate(),"ordinary paid opening starts");
            waitForDoorPhase(helper,f,AirlockState.DoorPhase.OPEN,30,()->{
                trace.mark("body-enrollment-before");
                Villager corpse=enrolledBody(f.level,f.root.getX()-.5,f.root.getY()+.1,f.root.getZ()+.5);
                trace.mark("body-enrollment-after");trace.assertOpen("body enrollment");
                corpse.setNoGravity(true);
                 require(DamageSystem.applyHealthChange(corpse,java.util.Map.of(DamageKeys.BLUNT,200f),1f,true)==DamageSystem.Result.APPLIED
                                 &&MobStateSystem.isDead(corpse)&&!corpse.isAlive(),"fixture is a loaded resolved medical DEAD body");
                trace.mark("dead-damage-after");trace.assertOpen("dead-body damage");
                 placeActualBoxAtDoorFace(corpse,f.root);
                trace.mark("place-face-after");trace.assertOpen("placeActualBoxAtDoorFace");
                require(AirlockGeometry.touchesDoorway(corpse.getBoundingBox(),new net.minecraft.world.phys.AABB(f.root.getX(),f.root.getY(),f.root.getZ(),f.root.getX()+1,f.root.getY()+2,f.root.getZ()+1)),"corpse bounding box touches root face: "+corpse.getBoundingBox()+" root="+f.root+" width="+corpse.getBbWidth());
                require(corpse.getX()<f.root.getX()&&corpse.getBoundingBox().maxX<=f.root.getX()+1.0E-5,"corpse center is outside left side and only its actual bbox face touches the door");
                  Villager gap=enrolledBody(f.level,f.root.getX()+1.31,f.root.getY()+.1,f.root.getZ()+.5);
                trace.mark("gap-enrollment-after");trace.assertOpen("gap enrollment");
                gap.setNoGravity(true);
                 require(!AirlockGeometry.touchesDoorway(gap.getBoundingBox(),new net.minecraft.world.phys.AABB(f.root.getX(),f.root.getY(),f.root.getZ(),f.root.getX()+1,f.root.getY()+2,f.root.getZ()+1)),"nearby body has a genuine horizontal gap");
                  trace.mark("before-first-close");
                  waitForPoweredPhase(helper,f,AirlockState.DoorPhase.OPEN,0,trace,"first-close",()->{
                  require(f.door.activate(),"normal powered closing begins with stationary bodies at its boundary; "+criticalCloseDiagnostic(f,corpse)+", gap="+gap.getBoundingBox()+", trace="+trace);
                   waitForCloseRecovery(helper,f,40,corpse,()->{
                    require(MobStateSystem.isDead(corpse)&&!corpse.isRemoved(),"dead corpse remains loaded and persistent after closed-edge reopen");
                    require(f.door.state().phase()==AirlockState.DoorPhase.OPENING,"quiet normal opening is selected at closed transition");
                       trace.mark("before-second-open-wait");
                       waitForDoorPhase(helper,f,AirlockState.DoorPhase.OPEN,20,()->{
                       trace.mark("second-open-complete");
                       waitForPoweredPhase(helper,f,AirlockState.DoorPhase.OPEN,0,trace,"second-close",()->{
                           require(f.door.activate(),"second normal close begins with corpse still at the static face; "+criticalCloseDiagnostic(f,corpse)+", trace="+trace);
                             waitForCloseRecovery(helper,f,40,corpse,()->{
                            require(MobStateSystem.isDead(corpse)&&!corpse.isRemoved(),"same static corpse reopens a later close episode");
                            require(f.door.state().phase()==AirlockState.DoorPhase.OPENING,"second close also starts ordinary non-denying opening");
                            corpse.discard();gap.discard();helper.succeed();
                  });
                       });
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
                    float shockAfter=DamageSystem.existingOrVanillaBaseline(body).getOrDefault(DamageKeys.SHOCK,0f);
                    require(Math.abs(shockAfter-shock-7f)<.001f,"passive partial-close contact shocks once; "+contactDiagnostic(f,body,shock,shockAfter));
                    require(DamageSystem.existingOrVanillaBaseline(body).getOrDefault(DamageKeys.BLUNT,0f)==blunt,"tangent actor is not a crush victim");
                    require(CharacterControlSystem.isStunned(body),"contact left Critical actor paralyzed before the normal close transition");
                     waitForCloseRecovery(helper,f,30,body,()->{
                        require(f.door.state().phase()==AirlockState.DoorPhase.OPENING||f.door.state().phase()==AirlockState.DoorPhase.OPEN,
                                "closed edge uses quiet normal opening despite Critical/stunned body");
                        require(Math.abs(DamageSystem.existingOrVanillaBaseline(body).getOrDefault(DamageKeys.SHOCK,0f)-shock-7f)<.001f,
                                "closed-edge bump recovery does not dispatch a duplicate electrical contact; "+contactDiagnostic(f,body,shock,DamageSystem.existingOrVanillaBaseline(body).getOrDefault(DamageKeys.SHOCK,0f)));
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
             // Death changes the native villager pose/collider; position the dead body
             // at the doorway and prove actual physical overlap before crush activation.
             corpse.setPos(f.root.getX()+.5,f.root.getY()+.1,f.root.getZ()+.5);
             require(!corpse.noPhysics&&corpse.getBoundingBox().intersects(doorwayBounds(f.root)),
                     "unsafe-close fixture starts with a loaded physical medical-dead collider overlapping doorway; uuid="
                             +corpse.getUUID()+" bounds="+corpse.getBoundingBox()+" doorway="+doorwayBounds(f.root)
                             +" noPhysics="+corpse.noPhysics+" damage="+DamageSystem.existingOrVanillaBaseline(corpse));
             require(f.door.applyWire(f.door.identity(),f.door.state().revision(),AirlockState.Wire.SAFETY,AirlockState.WireAction.CUT),"safety wire permits unsafe crush");
             waitForPoweredPhase(helper,f,AirlockState.DoorPhase.OPEN,0,()->{
             require(f.door.activate(),"unsafe close begins around the dead physical body; "+criticalCloseDiagnostic(f,corpse));
             com.juicyslew.moonstation14.gametest.support.BoundedGameTestWait.safeDelay(helper, 9,()->{
                require(f.door.state().phase()==AirlockState.DoorPhase.CLOSING_PASSABLE,"dead collider participates in unsafe partial-close capture");
                 require(f.door.getUpdateTag(f.level.registryAccess()).getList("CrushVictims",net.minecraft.nbt.Tag.TAG_COMPOUND).stream()
                                 .anyMatch(tag->tag instanceof net.minecraft.nbt.CompoundTag compound&&compound.hasUUID("Id")&&compound.getUUID("Id").equals(corpse.getUUID())),
                         "retained medical corpse is root-owned by the existing crush collision exemption");
                 long capturedReopenDeadline=f.door.crushReopenRetryAt();
                 require(capturedReopenDeadline>f.level.getGameTime(),"corpse capture retains the normal separate paid-reopen deadline");
                  com.juicyslew.moonstation14.gametest.support.BoundedGameTestWait.safeDelay(helper, 8,()->{
                     require(f.door.state().phase()==AirlockState.DoorPhase.CLOSED&&MobStateSystem.isDead(corpse),"dead body remains loaded through the ordinary sealed interval");
                     require(f.level.getGameTime()<capturedReopenDeadline
                                     &&f.door.crushReopenRetryAt()==capturedReopenDeadline,
                             "sealed interval neither starts paid reopen early nor changes captured retry deadline");
                     waitForCloseRecovery(helper,f,60,()->{
                         require(f.level.getGameTime()>=capturedReopenDeadline,
                                 "paid reopen cannot begin before the unchanged captured crush deadline");
                         require(f.door.state().phase()==AirlockState.DoorPhase.OPENING||f.door.state().phase()==AirlockState.DoorPhase.OPEN,
                                 "existing fresh-paid crush reopen releases the loaded medical corpse");
                        corpse.discard();helper.succeed();
             });
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
                    entrant.setNoGravity(true);
                    float blunt=DamageSystem.existingOrVanillaBaseline(entrant).getOrDefault(DamageKeys.BLUNT,0f);
                    float shock=DamageSystem.existingOrVanillaBaseline(entrant).getOrDefault(DamageKeys.SHOCK,0f);
                    AirlockCoreGameTests.waitForFullPower(helper,
                            new AirlockCoreGameTests.Fixture(f.level,f.root,f.door),120,()->{
                    require(f.door.state().phase()==AirlockState.DoorPhase.OPEN,
                            "fresh full-power barrier leaves safe-close fixture OPEN");
                    require(f.door.activate(),"empty safe close starts");com.juicyslew.moonstation14.gametest.support.BoundedGameTestWait.safeDelay(helper, 4,()->{
                        entrant.setPos(f.root.getX()+.5,f.root.getY()+.1,f.root.getZ()+.5);
                        require(entrant.getBoundingBox().intersects(doorwayBounds(f.root)),
                                "late actor physically overlaps the doorway during partial close");
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
             AirlockCoreGameTests.waitForFullPower(helper,
                     new AirlockCoreGameTests.Fixture(f.level,f.root,f.door),120,()->{
             require(f.door.state().phase()==AirlockState.DoorPhase.OPEN,
                     "fresh full-power barrier leaves unsafe-close fixture OPEN");
             require(f.door.activate(),"unsafe close starts");
            require(f.door.applyWire(f.door.identity(),f.door.state().revision(),AirlockState.Wire.POWER_ONE,AirlockState.WireAction.CUT),"first power wire is cut during closing");
            require(f.door.applyWire(f.door.identity(),f.door.state().revision(),AirlockState.Wire.POWER_TWO,AirlockState.WireAction.CUT),"second power wire removes power and disables electrification during closing");
            com.juicyslew.moonstation14.gametest.support.BoundedGameTestWait.safeDelay(helper, 9,()->{
                require(Math.abs(DamageSystem.existingOrVanillaBaseline(inside).getOrDefault(DamageKeys.BLUNT,0f)-blunt-15f)<.001f,"inactive-electrification door retains ordinary crush");
                require(DamageSystem.existingOrVanillaBaseline(inside).getOrDefault(DamageKeys.SHOCK,0f)==shock,"inactive electrification emits no shock");
                require(DamageSystem.existingOrVanillaBaseline(adjacent).getOrDefault(DamageKeys.SHOCK,0f)==adjacentShock,"actor outside exact doorway is not scanned as a contact");
                inside.discard();adjacent.discard();helper.succeed();
             });
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
        com.juicyslew.moonstation14.gametest.support.BoundedGameTestWait.safeDelay(helper, 2,()->{
            float repeated=DamageSystem.existingOrVanillaBaseline(victim).getOrDefault(DamageKeys.SHOCK,0f)-shockBefore;
            require(Math.abs(repeated-7f)<.001f,"scheduled callbacks do not repeat the same solidification contact");done.run();
        });
    }

    private static void waitForDoorPhase(GameTestHelper helper,Fixture f,AirlockState.DoorPhase phase,int remaining,Runnable done){
        if(f.door.state().phase()==phase){done.run();return;}
        if(remaining<=0){
            String diagnostic="expectedPhase="+phase+" actualPhase="+f.door.state().phase()
                +" gameTime="+f.level.getGameTime()+" serverTick="+f.level.getServer().getTickCount()
                +" transitionStartedAt="+f.door.clientView().transitionStartedAt()+" transitionAt="+f.door.state().transitionAt()
                +" crushReopenRetryAt="+f.door.crushReopenRetryAt()+" powerAvailable="+f.door.state().powerAvailable()
                +" doorState="+f.door.state()+" meter="+PowerRuntime.airlockPowerDiagnostic(f.level,f.door);
            com.juicyslew.moonstation14.MoonStation14.LOGGER.error("[airlock GameTest phase wait exhausted] {}",diagnostic);
            throw new GameTestAssertException("door did not reach expected phase="+phase+" actual="+f.door.state().phase()
                    +" transitionAt="+f.door.state().transitionAt()+" gameTime="+f.level.getGameTime());
        }
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
                                         long[] openingStartedAt,long[] reopenedOpenDue,
                                         DoorPhaseTickObserver phaseObserver,long[] traceStartedAt,
                                         int[] helperCallbacks,long traceStartHelperTick,int traceStartVictimTick) {
        try {
        helperCallbacks[0]++;
        long now=f.level.getGameTime();
        if(traceStartedAt[0]<0)traceStartedAt[0]=now;
        AirlockState state=f.door.state();
        if(!reopened[0]&&state.phase()==AirlockState.DoorPhase.OPENING
                &&postCrushPhases.contains(AirlockState.DoorPhase.CLOSING_PASSABLE)) {
            reopened[0]=true;
            openingStartedAt[0]=f.door.clientView().transitionStartedAt();
            require(openingStartedAt[0]==f.level.getGameTime(),"crush release resets the client animation start on entry to fresh opening");
            postCrushPhases.clear();
            phaseObserver.arm();
        }
        if(reopened[0]&&state.phase()==AirlockState.DoorPhase.OPENING) {
            require(f.door.clientView().transitionStartedAt()==openingStartedAt[0],"opening animation timestamp remains stable during the first reopening phase");
            require(f.level.getGameTime()-openingStartedAt[0]<8,"first reopening phase lasts the normal .4 seconds");
        }
        if(reopened[0]&&state.phase()==AirlockState.DoorPhase.OPENING_PASSABLE&&reopenedOpenDue[0]==0) {
            reopenedOpenDue[0]=state.transitionAt();
            long remainingToOpen=reopenedOpenDue[0]-f.level.getGameTime();
            require(remainingToOpen>=0&&remainingToOpen<=20,
                    "reopened OPENING_PASSABLE carries its resolved OPEN transition deadline; now="
                            +f.level.getGameTime()+" transitionAt="+state.transitionAt()+" state="+state);
            long openDue=reopenedOpenDue[0];
        }
        if(reopened[0])postCrushPhases.add(state.phase());
        else if(state.phase()==AirlockState.DoorPhase.CLOSING_PASSABLE)postCrushPhases.add(state.phase());
        if(state.phase()==AirlockState.DoorPhase.CLOSING_PASSABLE
                &&DamageSystem.existingOrVanillaBaseline(victim).getOrDefault(DamageKeys.BLUNT,0f)-bluntBefore>=30f) {
            require(reopened[0]&&postCrushPhases.contains(AirlockState.DoorPhase.OPENING)
                    &&postCrushPhases.contains(AirlockState.DoorPhase.OPENING_PASSABLE)
                    &&phaseObserver.observedResolvedOpen(reopenedOpenDue[0], f.door.prototype().airlock().autoCloseSeconds(),
                            f.door.prototype().wires().timingCutModifier()),
                     "crush release traversed normal OPENING and OPENING_PASSABLE, the actual full OPEN server-tick "
                             + "sample and its resolved fast auto-close before the next damaging close; phases="
                             +postCrushPhases+" openDue="+reopenedOpenDue[0]+" observations="+phaseObserver.diagnostic()
                             +" current="+state+", gameTime="+now+", helperTick="+helper.getTick()
                             +", serverTick="+f.level.getServer().getTickCount()+", victimTick="+victim.tickCount
                             +", victimPos="+victim.position()+", victimVelocity="+victim.getDeltaMovement()
                             +", victimBounds="+victim.getBoundingBox()+", bluntDelta="
                             +(DamageSystem.existingOrVanillaBaseline(victim).getOrDefault(DamageKeys.BLUNT,0f)-bluntBefore)
                             +", powerAvailable="+state.powerAvailable()+", transitionStartedAt="
                             +f.door.clientView().transitionStartedAt()+", transitionAt="+state.transitionAt()
                             +", autoCloseAt="+state.autoCloseAt()+", solve="
                             +com.juicyslew.moonstation14.ms14.power.runtime.PowerRuntime.lastSolveDiagnostic(f.level));
            require(openingStartedAt[0]>0,"client animation start timestamp is published for fresh crush opening");
            require(f.door.isCrushExempt(net.minecraft.world.phys.shapes.CollisionContext.of(victim)),"crushed victim remains collision-exempt through close cycle");
            require(CharacterControlSystem.isStunned(victim),"victim remains stunned at the next crush transition");
            try { victim.discard(); helper.succeed(); }
            finally { phaseObserver.close(); }
        }
        long elapsedGameTicks=now-traceStartedAt[0];
        if(elapsedGameTicks>=120)throw new GameTestAssertException("crush cycle did not reach next damaging partial close within 120 real level ticks; "
                +"elapsedGameTicks="+elapsedGameTicks+" traceStartGameTime="+traceStartedAt[0]+" now="+now
                +" helperTick="+helper.getTick()+" traceCallbacks="+helperCallbacks[0]+" helperElapsed="
                +(helper.getTick()-traceStartHelperTick)+" victimEntityTickElapsed="+(victim.tickCount-traceStartVictimTick)
                +" phase="+state.phase()+" transitionAt="+state.transitionAt()+" autoCloseAt="+state.autoCloseAt()
                +" revision="+state.revision()+" doorPowered="+state.powered()+" victimAlive="+victim.isAlive()
                +" victimPos="+victim.position()+" victimBounds="+victim.getBoundingBox()+" doorway="+doorwayBounds(f.root)
                +" victimIntersectsDoorway="+victim.getBoundingBox().intersects(doorwayBounds(f.root))
                +" victimBluntDelta="+(DamageSystem.existingOrVanillaBaseline(victim).getOrDefault(DamageKeys.BLUNT,0f)-bluntBefore)
                +" stun="+CharacterControlSystem.isStunned(victim)+" knockdown="+CharacterControlSystem.isKnockedDown(victim)
                +" phases="+postCrushPhases+" observedTicks="+phaseObserver.diagnostic());
        com.juicyslew.moonstation14.gametest.support.BoundedGameTestWait.safeDelay(helper, 1,()->traceCrushCycle(helper,f,victim,bluntBefore,postCrushPhases,reopened,
                openingStartedAt,reopenedOpenDue,phaseObserver,traceStartedAt,helperCallbacks,
                traceStartHelperTick,traceStartVictimTick));
        } catch (RuntimeException | Error failure) {
            phaseObserver.close();
            throw failure;
        }
    }

    /** Captures the controller's native phase and frame timestamps once per real level tick. */
    private static final class DoorPhaseTickObserver {
        private final ServerLevel level;
        private final AirlockBlockEntity door;
        private final java.util.function.Consumer<net.neoforged.neoforge.event.tick.LevelTickEvent.Post> listener;
        private boolean registered, armed;
        private long expiresAt;
        private Villager victim;
        private PhaseSample open, closing;
        private Object latest;
        DoorPhaseTickObserver(ServerLevel level, AirlockBlockEntity door) {
            this.level=level; this.door=door;
            listener=event->{
                if(!registered||event.getLevel()!=this.level||this.level.getGameTime()>=expiresAt)return;
                AirlockState state=this.door.state(); var view=this.door.clientView(); long now=this.level.getGameTime();
                latest="tick="+now+",phase="+state.phase()+",revision="+state.revision()+",transitionAt="+state.transitionAt()+",autoCloseAt="+state.autoCloseAt()+",frameStart="+view.transitionStartedAt()+",victim="+(victim==null?"none":victim.position());
                if(!armed)return;
                if(open==null&&state.phase()==AirlockState.DoorPhase.OPEN)open=new PhaseSample(now,state.phase(),state.autoCloseAt(),view.transitionStartedAt());
                else if(open!=null&&closing==null&&state.phase()==AirlockState.DoorPhase.CLOSING)closing=new PhaseSample(now,state.phase(),state.autoCloseAt(),view.transitionStartedAt());
            };
        }
        void register(long ticks){expiresAt=level.getGameTime()+ticks;registered=true;net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(listener);}
        void arm(){armed=true;}
        void setVictim(Villager victim,float ignored){this.victim=victim;}
        boolean observedResolvedOpen(long expected,double seconds,double modifier){long duration=Math.max(1,(long)Math.ceil(seconds*modifier*20));return open!=null&&closing!=null&&open.gameTime==expected&&open.frameStart==expected&&open.autoCloseAt==expected+duration&&closing.gameTime==open.autoCloseAt&&closing.frameStart==open.autoCloseAt&&closing.phase==AirlockState.DoorPhase.CLOSING;}
        String diagnostic(){return "latest="+latest+",open="+open+",closing="+closing;}
        void close(){if(registered){registered=false;net.neoforged.neoforge.common.NeoForge.EVENT_BUS.unregister(listener);}}
    }
    private record PhaseSample(long gameTime,AirlockState.DoorPhase phase,long autoCloseAt,long frameStart) { }

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
        boolean fixtureEntityTicking=java.util.stream.Stream.of(f.root,f.root.offset(0,0,-7),f.root.offset(0,0,-5),
                f.root.offset(0,0,-3),f.root.offset(1,0,-7),f.root.offset(-1,0,-5),f.root.offset(1,0,0))
                .allMatch(f.level::isPositionEntityTicking);
        if(observation.known()&&observation.deliveredWatts()==demand&&fixtureEntityTicking) {
            require(f.level.getBlockEntity(f.root)==f.door,"resolved root controller identity remains current before activation");
            require(f.door.state().phase()==AirlockState.DoorPhase.CLOSED,"airlock remains closed while awaiting full-power admission");
            admitted.run();
            return;
        }
        if(waited>=120){
            String diagnostic="known="+observation.known()+" delivered="+observation.deliveredWatts()+" resolvedDemand="+demand
                +" topologyReady="+com.juicyslew.moonstation14.ms14.power.graph.PowerGraphService.topologyReady(f.level)
                +" rootCurrent="+(f.level.getBlockEntity(f.root)==f.door)+" phase="+f.door.state().phase()+" gameTime="+f.level.getGameTime()
                +" fixtureEntityTicking="+fixtureEntityTicking
                +" meter="+PowerRuntime.airlockPowerDiagnostic(f.level,f.door);
            com.juicyslew.moonstation14.MoonStation14.LOGGER.error("[connected airlock GameTest power wait exhausted] {}",diagnostic);
            throw new GameTestAssertException("connected airlock never reached fresh full-power admission: known="+observation.known()
                    +" delivered="+observation.deliveredWatts()+" demand="+demand+" phase="+f.door.state().phase()
                    +" gameTime="+f.level.getGameTime()+" fixtureEntityTicking="+fixtureEntityTicking);
        }
        com.juicyslew.moonstation14.gametest.support.BoundedGameTestWait.safeDelay(helper,1,()->waitForPoweredAdmission(helper,f,waited+1,admitted));
    }

    private static void waitForPoweredPhase(GameTestHelper helper, Fixture f, AirlockState.DoorPhase expected,
                                             int waited, Runnable admitted) {
        waitForPoweredPhase(helper,f,expected,waited,80,null,null,admitted);
    }

    private static void waitForPoweredPhase(GameTestHelper helper, Fixture f, AirlockState.DoorPhase expected,
                                             int waited,int maxWait,Runnable admitted) {
        waitForPoweredPhase(helper,f,expected,waited,maxWait,null,null,admitted);
    }

    private static void waitForPoweredPhase(GameTestHelper helper, Fixture f, AirlockState.DoorPhase expected,
                                             int waited,TangentTrace trace,String closeLabel,Runnable admitted) {
        waitForPoweredPhase(helper,f,expected,waited,80,trace,closeLabel,admitted);
    }

    private static void waitForPoweredPhase(GameTestHelper helper, Fixture f, AirlockState.DoorPhase expected,
                                             int waited,int maxWait,TangentTrace trace,String closeLabel,Runnable admitted) {
        var phase=f.door.state().phase();
        var observation=PowerRuntime.airlockPower(f.level,f.door);
        if(trace!=null)trace.poll(closeLabel,observation);
        if(phase==expected) {
            AirlockCoreGameTests.waitForFullPower(helper,
                    new AirlockCoreGameTests.Fixture(f.level,f.root,f.door),maxWait,()->{
                require(f.level.getBlockEntity(f.root)==f.door,"root controller identity before later activation");
                require(f.door.state().phase()==expected,"fresh full-power readiness phase mismatch close="+closeLabel
                        +" expected="+expected+" actual="+f.door.state().phase()+" now="+f.level.getGameTime()
                        +" root="+f.root+(trace==null?"":" trace="+trace));
                admitted.run();
            });
            return;
        }
        if(waited>=maxWait || (f.door.state().autoCloseAt()>0&&f.level.getGameTime()>=f.door.state().autoCloseAt()))
            throw new GameTestAssertException("powered phase readiness timeout; expectedPhase="+expected+" actualPhase="+phase
                    +" bolts="+f.door.state().boltsDown()+" disabled="+f.door.state().receiverPowerDisabled()
                    +" power="+observation+" now="+f.level.getGameTime()+" deadline="+f.door.state().autoCloseAt()+" root="+f.root+" close="+closeLabel
                    +(trace==null?"":" trace="+trace));
        com.juicyslew.moonstation14.gametest.support.BoundedGameTestWait.safeDelay(helper,1,()->waitForPoweredPhase(helper,f,expected,waited+1,maxWait,trace,closeLabel,admitted));
    }

    private static final class TangentTrace {
        private final GameTestHelper helper;private final Fixture f;private final java.util.ArrayList<String> entries=new java.util.ArrayList<>();
        private String lastKey="",stage="init",latest="";private int omitted;
        private TangentTrace(GameTestHelper helper,Fixture f){this.helper=helper;this.f=f;}
        private void mark(String next){stage=next;record(null);}
        private void poll(String label,com.juicyslew.moonstation14.ms14.power.runtime.PowerRuntime.VentMeterObservation observation){
            stage=label+"-pending";record(observation);
        }
        private void assertOpen(String operation){
            require(f.door.state().phase()==AirlockState.DoorPhase.OPEN,"tangent fixture unexpectedly left OPEN after "+operation+"; trace="+this);
        }
        private void record(com.juicyslew.moonstation14.ms14.power.runtime.PowerRuntime.VentMeterObservation supplied){
            var o=supplied==null?PowerRuntime.airlockPower(f.level,f.door):supplied;
            String key=stage+"|"+f.level.getGameTime()+"|"+f.door.state().phase()+"|"+o.known()+"|"+o.deliveredWatts()+"|"+com.juicyslew.moonstation14.ms14.power.graph.PowerGraphService.topologyGeneration(f.level);
            if(key.equals(lastKey))return;lastKey=key;
            String line="AIRLOCK_TANGENT_TRACE stage="+stage+" helperTick="+helper.getTick()+" globalTime="+f.level.getGameTime()
                    +" phase="+f.door.state().phase()+" autoCloseAt="+f.door.state().autoCloseAt()+" powerKnown="+o.known()
                    +" watts="+o.deliveredWatts()+" topologyGeneration="+com.juicyslew.moonstation14.ms14.power.graph.PowerGraphService.topologyGeneration(f.level)
                    +" rootCurrent="+(f.level.getBlockEntity(f.root)==f.door)+" rootIdentity="+System.identityHashCode(f.door);
            if(entries.size()<79)entries.add(line);else omitted++;
            latest=line;
        }
        @Override public String toString(){
            int shown=Math.min(entries.size(),4);
            return "first="+entries.subList(0,shown)+", omitted="+(omitted+entries.size()-shown)+", latest="+latest;
        }
    }

    private static String criticalCloseDiagnostic(Fixture f,Villager actor) {
        return "phase="+f.door.state().phase()+", power="+PowerRuntime.airlockPower(f.level,f.door)
                +", canOpenOrClose="+f.door.state().canOpenOrClose()+", obstructingBBox="+actor.getBoundingBox()
                +", doorState="+f.door.state();
    }

    private static String contactDiagnostic(Fixture f,Villager actor,float shockBefore,float shockAfter) {
        return "shockLedger={before="+shockBefore+",after="+shockAfter+",delta="+(shockAfter-shockBefore)+"}"
                +", actor={uuid="+actor.getUUID()+",pos="+actor.position()+",health="+actor.getHealth()+",alive="+actor.isAlive()
                +",critical="+MobStateSystem.isCritical(actor)+",dead="+MobStateSystem.isDead(actor)+",bounds="+actor.getBoundingBox()+"}"
                +", doorway="+doorwayBounds(f.root)+", doorPhase="+f.door.state().phase()+", doorState="+f.door.state()
                +", doorView="+f.door.clientView()+", power="+PowerRuntime.airlockPower(f.level,f.door);
    }

    @GameTest(templateNamespace = "moonstation14", template = "atmos_large_empty", batch = "airlock_gas_flow", timeoutTicks = 500)
    public static void scheduledDoorOpeningTransportsAndConservesFiniteGas(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos root = helper.absolutePos(new BlockPos(3, 1, 9));
        clearFixtureVolume(level, root, 2, 8);
        // Two sealed one-cell chambers communicate only through the two-cell airlock.
        BlockPos left = root.north(), right = root.south();
        for (BlockPos cell : List.of(left, right, root, root.above()))
            level.setBlock(cell, Blocks.AIR.defaultBlockState(), 3);
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
        // The power fixture contributes the east wall of the lower portal. Build it before
        // authoring the component so the trusted setup proves the complete four-cell passage.
        connectPower(helper, root, null);
        List<BlockPos> finiteCells = List.of(left, root, root.above(), right);
        var atmosphere = AtmosphereService.INSTANCE;
        for (BlockPos cell : finiteCells)
            require(com.juicyslew.moonstation14.ms14.atmos.world.AtmosphereTopology.isPassable(level, cell),
                    "authored passage cell is passable before ownership setup: " + cell);
        require(atmosphere.initializeEnclosedFiniteMixtureForTrustedSetup(level, finiteCells,
                        new com.juicyslew.moonstation14.ms14.atmos.core.GasMixture(java.util.Map.of(), 293.15)),
                "trusted setup proves and initializes the complete sealed four-cell finite passage");
        require(atmosphere.addGas(level, left, GasType.NITROGEN, 40, 300), "seed high-pressure chamber");
        require(atmosphere.addGas(level, right, GasType.NITROGEN, 2, 300), "seed low-pressure chamber");
        var lower = ModBlocks.AIRLOCK.get().defaultBlockState();
        level.setBlock(root, lower, 3);
        level.setBlock(root.above(), lower.setValue(AirlockBlock.HALF, DoubleBlockHalf.UPPER), 3);
        AirlockBlockEntity door = (AirlockBlockEntity) level.getBlockEntity(root);
        require(door != null, "root controller exists");

        int[] atmosphereClock = {0};
        acquirePowerFixtureTickets(helper,root,door.prototype().extensionCableReceiver().localReachBlocks());
        Fixture fixture = new Fixture(level, root, door);
        waitForPoweredAdmission(helper, fixture, 0, () -> {
            List<BlockPos> inventoryCells = List.of(left, right, root, root.above());
            // Closed portal cells may be blocked rather than live-sampled. In a vacuum
            // dimension their pre-opening ambient inventory is zero; count any existing
            // persisted finite override too, without fabricating claims for blocked cells.
            Inventory before = inventory(level, inventoryCells, false);
            var countersBefore = atmosphere.diagnosticRuntime(level);
            long faceTransfersBefore = countersBefore.pressureFaceTransfers();
            double beforeTotal = before.nitrogen();
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
                double operations = AtmospherePrecisionBudget.operations(160, 3, 0);
                double nitrogenBudget = nitrogenUlpBudget(before, after, operations);
                double energyBudget = energyUlpBudget(before, after, operations);
                require(Math.abs(after.nitrogen() - before.nitrogen()) <= nitrogenBudget,
                        "finite chamber and portal nitrogen is conserved within operation-derived float rounding; "
                                + conservationDiagnostic(atmosphere, level, inventoryCells, before, after, nitrogenBudget, operations));
                require(Math.abs(after.energy() - before.energy()) <= energyBudget,
                        "finite chamber and portal energy conservation: beforeTotal="+before.energy()+" afterTotal="+after.energy()
                                +" energyDelta="+(after.energy()-before.energy())+" energyBudget="+energyBudget
                                +" beforeN2="+beforeTotal+" afterN2="+after.nitrogen()+" nitrogenBudget="+nitrogenBudget
                                +" portal="+portalDiagnostics(level,root,before,after,atmosphere));
                waitForPoweredPhase(helper,fixture,AirlockState.DoorPhase.OPEN,0,()->{
                require(door.activate(), "actual controller starts scheduled closing");
                com.juicyslew.moonstation14.gametest.support.BoundedGameTestWait.safeDelay(helper, 14, () -> {
                    require(door.state().phase() == AirlockState.DoorPhase.CLOSED, "scheduled close completes");
                    atmosphere.topologyChanged(level, root);
                    atmosphere.topologyChanged(level, root.above());
                    for (int i = 0; i < 160; i++) atmosphere.tick(level, ++atmosphereClock[0]);
                    Inventory closed = inventory(level, inventoryCells, true);
                    require(Math.abs(closed.nitrogen() - beforeTotal) <= nitrogenUlpBudget(before, closed, operations),
                            "closed doorway retains finite nitrogen ownership; "
                                    + conservationDiagnostic(atmosphere, level, inventoryCells, before, closed,
                                    nitrogenUlpBudget(before, closed, operations), operations));
                    require(Math.abs(closed.energy() - before.energy()) <= energyUlpBudget(before, closed, operations),
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
        var lower = ModBlocks.AIRLOCK.get().defaultBlockState();
        level.setBlock(root, lower, 3);
        level.setBlock(root.above(), lower.setValue(AirlockBlock.HALF, DoubleBlockHalf.UPPER), 3);
        AirlockBlockEntity door=(AirlockBlockEntity)level.getBlockEntity(root);
        acquirePowerFixtureTickets(helper,root,door.prototype().extensionCableReceiver().localReachBlocks());
        return connectPower(helper,root,door);
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

    private static void acquirePowerFixtureTickets(GameTestHelper helper,BlockPos root,int reach){
        int minX=root.getX()-Math.max(reach,1)-2,maxX=root.getX()+Math.max(reach,1)+2;
        int minZ=root.getZ()-9,maxZ=root.getZ()+Math.max(reach,1)+2;
        FixtureChunkTickets.acquire(helper,new net.minecraft.world.phys.AABB(minX,root.getY(),minZ,
                Math.nextDown(maxX+1.0),root.getY()+3,Math.nextDown(maxZ+1.0)));
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
        if (kind == PowerDeviceKind.APC) {
            require(com.juicyslew.moonstation14.ms14.power.device.PowerDeviceFixturePlacement.placeApc(level, pos, facing), "place supported APC");
            return;
        }
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

    private record Inventory(AtmospherePrecisionBudget.Inventory budget) {
        double nitrogen() { return budget.moles(GasType.NITROGEN); }
        double oxygen() { return budget.moles(GasType.OXYGEN); }
        double energy() { return budget.energy(); }
    }

    private static Inventory inventory(ServerLevel level, List<BlockPos> cells, boolean requireFiniteOwnership) {
        List<com.juicyslew.moonstation14.ms14.atmos.core.GasMixture> mixtures = new java.util.ArrayList<>();
        for (BlockPos pos : cells) {
            LevelChunk chunk = (LevelChunk) level.getChunk(pos);
            var data = chunk.getExistingDataOrNull(ModDataAttachments.ATMOSPHERE_CHUNK.get());
            boolean claimed = data != null && data.isFiniteClaimed(pos.getX() & 15, pos.getY(), pos.getZ() & 15);
            if (requireFiniteOwnership) require(claimed,
                    "each chamber and portal cell remains explicitly finite-owned: " + pos);
            var gas = data == null ? null : data.get(pos.getX() & 15, pos.getY(), pos.getZ() & 15);
            if (requireFiniteOwnership) require(gas != null, "owned chamber/portal gas mixture remains persisted: " + pos);
            mixtures.add(gas == null
                    ? new com.juicyslew.moonstation14.ms14.atmos.core.GasMixture(java.util.Map.of(), 293.15)
                    : gas);
        }
        return new Inventory(AtmospherePrecisionBudget.Inventory.capture(mixtures));
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
        return "beforeN2=" + before.nitrogen() + ",afterN2=" + after.nitrogen() + ",beforeO2=" + before.oxygen()
                + ",afterO2=" + after.oxygen() + ",deltaN2=" + (after.nitrogen()-before.nitrogen())
                + ",tolerance=" + tolerance + ",operations=" + operations + "," + topology + "]";
    }

    private static String portalDiagnostics(ServerLevel level, BlockPos root, Inventory before, Inventory after, AtmosphereService local) {
        return "beforeTotal="+before.nitrogen()+" afterTotal="+after.nitrogen()+" beforeEnergy="+before.energy()+" afterEnergy="+after.energy()
                +" lower="+cellDiagnostic(level,root)+" upper="+cellDiagnostic(level,root.above())
                +" INSTANCE.vacuumDimensions="+AtmosphereService.INSTANCE.vacuumDimensionsForTest()
                +" local.vacuumDimensions="+local.vacuumDimensionsForTest();
    }

    private static String cellDiagnostic(ServerLevel level, BlockPos pos) {
        LevelChunk chunk=(LevelChunk)level.getChunk(pos);
        var data=chunk.getExistingDataOrNull(ModDataAttachments.ATMOSPHERE_CHUNK.get());
        boolean claimed=data!=null&&data.isFiniteClaimed(pos.getX()&15,pos.getY(),pos.getZ()&15);
        var gas=data==null?null:data.get(pos.getX()&15,pos.getY(),pos.getZ()&15);
        return pos+"{block="+level.getBlockState(pos).getBlock()+",claimed="+claimed+",gas="
                +(gas==null?"null":"N2:"+gas.moles(GasType.NITROGEN)+" total:"+gas.totalMoles()+" E:"+gas.thermalEnergy())+"}";
    }

    private static double nitrogenUlpBudget(Inventory before, Inventory after, double operations) {
        return before.budget.moleBudget(GasType.NITROGEN, after.budget, operations);
    }

    private static double energyUlpBudget(Inventory before, Inventory after, double operations) {
        return before.budget.energyBudget(after.budget, operations);
    }

    private static net.minecraft.world.phys.AABB doorwayBounds(BlockPos root) {
        return new net.minecraft.world.phys.AABB(root.getX(), root.getY(), root.getZ(),
                root.getX() + 1, root.getY() + 2, root.getZ() + 1);
    }

    private static void require(boolean ok, String message) {
        if (!ok) throw new GameTestAssertException(message);
    }
}
