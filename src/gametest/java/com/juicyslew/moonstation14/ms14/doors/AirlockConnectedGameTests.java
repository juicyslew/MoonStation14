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
import com.juicyslew.moonstation14.ms14.damage.DamageKeys;
import com.juicyslew.moonstation14.ms14.damage.DamageSystem;
import com.juicyslew.moonstation14.ms14.power.cable.CableStorage;
import com.juicyslew.moonstation14.ms14.power.cable.CableTier;
import com.juicyslew.moonstation14.ms14.power.device.PowerDeviceKind;
import com.juicyslew.moonstation14.ms14.power.runtime.PowerRuntime;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
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
    private AirlockConnectedGameTests() { }

    @net.minecraft.gametest.framework.AfterBatch(batch="airlock_connected")
    public static void releasePowerFixtureChunks(ServerLevel level){
        for(long key:FORCED_POWER_FIXTURE_CHUNKS){var pos=new net.minecraft.world.level.ChunkPos(key);level.setChunkForced(pos.x,pos.z,false);}
        FORCED_POWER_FIXTURE_CHUNKS.clear();
    }

    @GameTestTags({"airlock_admission_probe"})
    @GameTest(templateNamespace = "moonstation14", template = "power_live_chain_clear", batch = "airlock_connected", timeoutTicks = 280)
    public static void unsafeCloseCrushesOnlyLivingBodyInsideExactDoorway(GameTestHelper helper) {
        Fixture f = poweredAirlock(helper);
        waitForPoweredAdmission(helper, f, 0, () -> {
            require(f.door.activate(), "powered door opens through its controller");
            helper.runAfterDelay(14, () -> {
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
                helper.runAfterDelay(9, () -> {
                    float victimDelta = DamageSystem.existingOrVanillaBaseline(victim).getOrDefault(DamageKeys.BLUNT, 0f) - victimBefore;
                    float aboveDelta = DamageSystem.existingOrVanillaBaseline(above).getOrDefault(DamageKeys.BLUNT, 0f) - aboveBefore;
                    require(Math.abs(victimDelta - 15f) < .001f, "partial close applies typed blunt crush damage of 15");
                    require(Math.abs(aboveDelta) < .001f, "body whose bounds begin above y+2 is outside the doorway");
                     require(CharacterControlSystem.isStunned(victim), "partial-close crush stun is active");
                     helper.runAfterDelay(5, () -> {
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
            helper.runAfterDelay(14,()->{
                require(f.door.state().phase()==AirlockState.DoorPhase.OPEN,"door is open before close attempt; state="+f.door.state());
                waitForPoweredPhase(helper,f,AirlockState.DoorPhase.OPEN,0,()->{
                Villager entrant=enrolledBody(f.level,f.root.getX()+1.5,f.root.getY(),f.root.getZ()+.5);
                float before=DamageSystem.existingOrVanillaBaseline(entrant).getOrDefault(DamageKeys.BLUNT,0f);
                require(f.door.activate()&&f.door.state().phase()==AirlockState.DoorPhase.CLOSING,"empty safe close begins");
                helper.runAfterDelay(4,()->{
                    entrant.setPos(f.root.getX()+.5,f.root.getY(),f.root.getZ()+.5);
                    helper.runAfterDelay(5,()->{
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
        waitForPoweredAdmission(helper,f,0,()->{
            require(f.door.activate(),"freshly powered airlock opens");
            helper.runAfterDelay(14,()->{
                require(f.door.state().phase()==AirlockState.DoorPhase.OPEN,"door has completed normal two-phase opening");
                waitForPoweredPhase(helper,f,AirlockState.DoorPhase.OPEN,0,()->{
                Villager victim=enrolledBody(f.level,f.root.getX()+.5,f.root.getY()+.1,f.root.getZ()+.5);
                Villager outside=enrolledBody(f.level,f.root.getX()+.5,f.root.getY()+2.35,f.root.getZ()+.5);
                victim.setNoGravity(true);outside.setNoGravity(true);
                double initialY=victim.getY();
                float bluntBefore=DamageSystem.existingOrVanillaBaseline(victim).getOrDefault(DamageKeys.BLUNT,0f);
                require(f.door.applyWire(f.door.identity(),f.door.state().revision(),AirlockState.Wire.SAFETY,AirlockState.WireAction.CUT),"unsafe close enabled only via safety wire");
                require(f.door.activate()&&f.door.state().phase()==AirlockState.DoorPhase.CLOSING,"unsafe close starts through live controller");
                helper.runAfterDelay(9,()->{
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
                     long originalReopenDeadline=f.door.crushReopenRetryAt();
                     long crushedAt=originalReopenDeadline-40;
                     require(originalReopenDeadline>f.level.getGameTime()
                                     &&originalReopenDeadline-f.level.getGameTime()<=40,
                             "fresh fully-paid reopen is scheduled two seconds after the actual crush event");
                    f.level.setBlock(f.root.offset(0,0,-3),Blocks.AIR.defaultBlockState(),3);
                    float savedHealth=healthAfterCrush;
                    assertVictimStable(helper,victim,initialY,savedHealth,bluntBefore,60,()->{
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
                        waitForPaidCrushReopen(helper,f,victim,outside,initialY,savedHealth,bluntBefore,crushedAt,120);
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
            helper.runAfterDelay(9,()->{
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
        helper.runAfterDelay(1,()->traceCrushCycle(helper,f,victim,bluntBefore,postCrushPhases,reopened,openingStartedAt,remaining-1));
    }

    private static void assertVictimStable(GameTestHelper helper,Villager victim,double y,float health,float bluntBefore,int remaining,Runnable done) {
        if(remaining<=0){done.run();return;}
        helper.runAfterDelay(1,()->{
            require(Math.abs(victim.getY()-y)<1e-6,"crushed victim Y remains stable during blocked close/reopen wait");
            require(Math.abs(victim.getHealth()-health)<1e-6,"no recurring vanilla in-wall damage while victim remains trapped");
            assertVictimStable(helper,victim,y,health,bluntBefore,remaining-1,done);
        });
    }

    private static void waitForPaidCrushReopen(GameTestHelper helper,Fixture f,Villager victim,Villager outside,
                                                double y,float health,float bluntBefore,long crushedAt,int remaining) {
        var sample=PowerRuntime.airlockPower(f.level,f.door);
        if(sample.known()&&sample.deliveredWatts()==f.door.prototype().apcPowerReceiver().requestedWatts()
                &&f.door.state().phase()==AirlockState.DoorPhase.OPEN) {
            require(f.level.getGameTime()-crushedAt>=40,"scheduled crush release does not reopen before the resolved 2s deadline");
            require(f.level.getBlockState(f.root).getValue(AirlockBlock.OPEN),"paid scheduled retry opens lower cell");
            require(Math.abs(victim.getY()-y)<1e-6&&Math.abs(victim.getHealth()-health)<1e-6,"victim remains undamaged and unejected until reopen");
            float finalBlunt=DamageSystem.existingOrVanillaBaseline(victim).getOrDefault(DamageKeys.BLUNT,0f);
            require(Math.abs(finalBlunt-bluntBefore-15f)<.001f,"crush blunt damage remains exactly one 15-point application across extended hold");
            require(f.door.getUpdateTag(f.level.registryAccess()).getList("CrushVictims",net.minecraft.nbt.Tag.TAG_COMPOUND).isEmpty(),
                    "opening clears transient victim collision exemptions");
            victim.discard();outside.discard();helper.succeed();return;
        }
        if(remaining<=0)throw new GameTestAssertException("scheduled crush reopen never regained fresh full power: known="+sample.known()+" watts="+sample.deliveredWatts()+" state="+f.door.state());
        helper.runAfterDelay(1,()->waitForPaidCrushReopen(helper,f,victim,outside,y,health,bluntBefore,crushedAt,remaining-1));
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
        helper.runAfterDelay(1,()->waitForPoweredAdmission(helper,f,waited+1,admitted));
    }

    private static void waitForPoweredPhase(GameTestHelper helper, Fixture f, AirlockState.DoorPhase expected,
                                            int waited, Runnable admitted) {
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
        if(waited>=80 || (f.door.state().autoCloseAt()>0&&f.level.getGameTime()>=f.door.state().autoCloseAt()))
            throw new GameTestAssertException("fresh full-power readiness timeout; expectedPhase="+expected+" actualPhase="+phase
                    +" known="+observation.known()+" watts="+observation.deliveredWatts()+" demand="+demand
                    +" bolts="+f.door.state().boltsDown()+" disabled="+f.door.state().receiverPowerDisabled()
                    +" now="+f.level.getGameTime()+" deadline="+f.door.state().autoCloseAt()+" root="+f.root);
        helper.runAfterDelay(1,()->waitForPoweredPhase(helper,f,expected,waited+1,admitted));
    }

    @GameTest(templateNamespace = "moonstation14", template = "atmos_large_empty", batch = "airlock_connected", timeoutTicks = 500)
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

        AtmosphereService atmosphere = AtmosphereService.withVacuumDimensions(Set.of(level.dimension()));
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
            helper.runAfterDelay(14, () -> {
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
                        "finite chamber and portal nitrogen is conserved within operation-derived float rounding");
                require(Math.abs(after.energy - before.energy) <= energyBudget,
                        "finite chamber and portal thermal energy is conserved within operation-derived float rounding");
                waitForPoweredPhase(helper,fixture,AirlockState.DoorPhase.OPEN,0,()->{
                require(door.activate(), "actual controller starts scheduled closing");
                helper.runAfterDelay(14, () -> {
                    require(door.state().phase() == AirlockState.DoorPhase.CLOSED, "scheduled close completes");
                    atmosphere.topologyChanged(level, root);
                    atmosphere.topologyChanged(level, root.above());
                    for (int i = 0; i < 160; i++) atmosphere.tick(level, ++atmosphereClock[0]);
                    Inventory closed = inventory(level, inventoryCells, true);
                    require(Math.abs(closed.nitrogen - beforeTotal) <= nitrogenUlpBudget(before, closed, operations),
                            "closed doorway retains finite nitrogen ownership");
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

    private record Inventory(double nitrogen, double energy, double nitrogenUlp, double energyUlp) { }

    private static Inventory inventory(ServerLevel level, List<BlockPos> cells, boolean requireFiniteOwnership) {
        double nitrogen = 0, energy = 0, nitrogenUlp = 0, energyUlp = 0;
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
            nitrogen += cellNitrogen; energy += cellEnergy;
            nitrogenUlp += Math.ulp((float) cellNitrogen); energyUlp += Math.ulp((float) cellEnergy);
        }
        return new Inventory(nitrogen, energy, nitrogenUlp, energyUlp);
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
