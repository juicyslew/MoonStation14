package com.juicyslew.moonstation14.ms14.doors;

import com.juicyslew.moonstation14.block.ModBlocks;
import com.juicyslew.moonstation14.block.ModBlockEntities;
import com.juicyslew.moonstation14.block.custom.PowerDeviceBlock;
import com.juicyslew.moonstation14.gametest.support.GameTestTags;
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
    private static final java.util.Set<Long> CORE_FORCED_CHUNKS=new java.util.HashSet<>();
    private static final java.util.Set<Long> OVERRIDE_FORCED_CHUNKS=new java.util.HashSet<>();
    private AirlockCoreGameTests() { }
    @AfterBatch(batch="airlock_core") public static void releaseCoreChunks(ServerLevel level){unforceFixtureChunks(level,CORE_FORCED_CHUNKS);}
    @AfterBatch(batch="airlock_prototype_power_override")
    public static void restorePrototypeCatalogAfterOverrideBatch(ServerLevel level) {
        try { restorePrototypeCatalog(); }
        finally { unforceFixtureChunks(level,OVERRIDE_FORCED_CHUNKS); }
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
     @GameTest(templateNamespace="moonstation14",template="power_live_chain_clear",batch="airlock_core",timeoutTicks=300)
     @GameTestTags({"airlock_power_cycle", "suite-stabilization"})
     public static void liveApcMeterDrivesScheduledTwoPhaseDoorAndAutoclose(GameTestHelper helper) {
        Fixture f=poweredAirlock(helper,CORE_FORCED_CHUNKS);ServerLevel level=f.level();AirlockBlockEntity door=f.door();
        waitForFullPower(helper,f,120,()->{
            var sample=PowerRuntime.airlockPower(level,door);
            require(sample.known()&&sample.deliveredWatts()==door.prototype().apcPowerReceiver().requestedWatts(),"fresh solver meter supplies resolved receiver demand");
            require(door.activate(),"powered root activation accepted from actual APC meter");
            require(door.state().phase()==AirlockState.DoorPhase.OPENING,"open transition starts at request");
            helper.runAfterDelay(8,()->{
                require(door.state().phase()==AirlockState.DoorPhase.OPENING_PASSABLE&&door.state().doorPassable(),"0.4s opening phase makes both cells passable");
                require(level.getBlockState(f.root()).getValue(AirlockBlock.OPEN)&&level.getBlockState(f.root().above()).getValue(AirlockBlock.OPEN),"both block cells follow authoritative passability");
                helper.runAfterDelay(4,()->{
                    require(door.state().phase()==AirlockState.DoorPhase.OPEN,"0.2s second phase completes opening");
                     long openedAt = level.getGameTime();
                     long deadline = door.state().autoCloseAt();
                     require(deadline > openedAt && deadline - openedAt <= 100,
                             "resolved default five-second auto-close deadline is armed from actual OPEN; openedAt="
                                     + openedAt + " autoCloseAt=" + deadline);
                     awaitAutoCloseDeadline(helper, f, deadline, 0);
                 });
             });
         });
     }

     private static void awaitAutoCloseDeadline(GameTestHelper helper, Fixture f, long deadline, int waited) {
         long now = f.level().getGameTime();
         var state = f.door().state();
         if (state.phase() == AirlockState.DoorPhase.CLOSING) {
             require(now >= deadline, "automatic close begins at or after its resolved deadline; now=" + now
                      + " deadline=" + deadline + " " + doorDiagnostic(f.level(), f.door()));
             helper.runAfterDelay(12,()->{
                             require(f.door().state().phase()==AirlockState.DoorPhase.CLOSED,"0.4+0.2s close transition completes");
                             require(!f.level().getBlockState(f.root()).getValue(AirlockBlock.OPEN)&&!f.level().getBlockState(f.root().above()).getValue(AirlockBlock.OPEN),"both cells reseal");
                             helper.succeed();
                         });
             return;
         }
         require(state.phase() == AirlockState.DoorPhase.OPEN,
                  "door remains OPEN before its auto-close deadline; phase=" + state.phase() + " now=" + now
                          + " deadline=" + state.autoCloseAt());
         long currentDeadline = state.autoCloseAt();
         if (currentDeadline == 0) {
             require(waited < 140, "auto-close deadline remained disarmed after power change; now=" + now
                     + " " + doorDiagnostic(f.level(), f.door()));
             helper.runAfterDelay(1, () -> awaitAutoCloseDeadline(helper, f, 0, waited + 1));
             return;
         }
         if (currentDeadline != deadline) {
             require(currentDeadline > now && currentDeadline - now <= 100,
                     "power-resolved deadline reset remains within the default five-second bound; previous="
                             + deadline + " current=" + currentDeadline + " now=" + now);
             deadline = currentDeadline;
         }
         require(waited < 140, "door did not close at its resolved deadline; now=" + now + " deadline=" + deadline
                 + " " + doorDiagnostic(f.level(), f.door()));
         long expectedDeadline = deadline;
         helper.runAfterDelay(1, () -> awaitAutoCloseDeadline(helper, f, expectedDeadline, waited + 1));
     }
    @GameTest(templateNamespace="moonstation14",template="power_live_chain_clear",batch="airlock_core",timeoutTicks=180)
    @GameTestTags({"suite-stabilization"})
    public static void obstructionUsesExactlyTwoDoorwayCellsAndSafetyDeniesCloseActor(GameTestHelper helper) {
        Fixture f=poweredAirlock(helper,CORE_FORCED_CHUNKS);ServerLevel level=f.level();AirlockBlockEntity door=f.door();
        waitForFullPower(helper,f,120,()->{
            var occupant=new net.minecraft.world.entity.player.Player(level,f.root(),0,new com.mojang.authlib.GameProfile(java.util.UUID.randomUUID(),"airlock-occupant")){
                @Override public boolean isCreative(){return true;} @Override public boolean isSpectator(){return false;}
            };
            occupant.setPos(f.root().getX()+.5,f.root().getY()+.25,f.root().getZ()+.5);level.addFreshEntity(occupant);
            require(!door.activate()&&door.state().phase()==AirlockState.DoorPhase.DENYING,"living entity in either cell safety-denies opening");
            occupant.discard();
            helper.runAfterDelay(10,()->{
                var above=new net.minecraft.world.entity.player.Player(level,f.root(),0,new com.mojang.authlib.GameProfile(java.util.UUID.randomUUID(),"above-airlock")){
                    @Override public boolean isCreative(){return true;} @Override public boolean isSpectator(){return false;}
                };
                above.setPos(f.root().getX()+.5,f.root().getY()+2.1,f.root().getZ()+.5);
                above.setNoGravity(true);
                level.addFreshEntity(above);
                var doorway = new net.minecraft.world.phys.AABB(f.root().getX(), f.root().getY(), f.root().getZ(),
                        f.root().getX() + 1, f.root().getY() + 2, f.root().getZ() + 1);
                String aboveDetails = entitySpatialDiagnostic(above);
                require(!above.getBoundingBox().intersects(doorway), "above-y+2 probe is physically outside exact two-cell bounds; "
                        + "doorway=" + doorway + " actor=" + aboveDetails);
                var blockers = level.getEntitiesOfClass(net.minecraft.world.entity.LivingEntity.class, doorway,
                        e -> e.isAlive() && !e.isSpectator()).stream()
                        .filter(e -> e.getBoundingBox().intersects(doorway))
                        .map(AirlockCoreGameTests::entitySpatialDiagnostic).toList();
                require(blockers.isEmpty(), "exact two-cell doorway has no living obstruction before above-y+2 activation; "
                        + "doorway=" + doorway + " above=" + aboveDetails + " blockers=" + blockers);
                require(door.activate(),"actor above y+2 does not obstruct the two-cell doorway; doorway="
                        + doorway + " actor=" + aboveDetails + " blockers=" + blockers + " phase=" + door.state().phase());
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
        Fixture f=poweredAirlock(helper,CORE_FORCED_CHUNKS);ServerLevel level=f.level();AirlockBlockEntity door=f.door();
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
        Fixture f=poweredAirlock(helper,CORE_FORCED_CHUNKS);AirlockBlockEntity door=f.door();
        waitForFullPower(helper,f,120,()->{
            require(door.activate()&&door.state().powered(),"freshly paid receiver admits root activation");
            require(door.applyWire(door.identity(),door.state().revision(),AirlockState.Wire.LOG,AirlockState.WireAction.PULSE),"log pulse accepted");
            long deadline=door.state().logPulseUntil();
            require(deadline-helper.getLevel().getGameTime()==600,"resolved inherited 30-second duration schedules 600 ticks");
            require(com.juicyslew.moonstation14.ms14.doors.ui.AirlockWireIndicators.resolve(door.state(),helper.getLevel().getGameTime()).log()==com.juicyslew.moonstation14.ms14.doors.ui.AirlockWireIndicators.Light.OFF,"pulse immediately extinguishes log circuit light");
              helper.runAfterDelay(40,()->{
                  if (door.state().logCircuitDisabled() && door.state().logPulseUntil() == deadline
                          && f.level().getGameTime() < deadline && door.state().powered()) {
                      verifyLogPulseRepeatAndExpiry(helper, f, deadline, deadline);
                      return;
                  }
                  // Power loss cancels the recovery deadline upstream. Re-admit only after a fresh
                  // fully-paid sample, then verify non-extension against that active resolved deadline.
                  waitForFullPower(helper, f, 120, () -> {
                      require(door.state().phase() == AirlockState.DoorPhase.OPEN, "door remains open during paid pulse recovery");
                      require(door.applyWire(door.identity(), door.state().revision(), AirlockState.Wire.LOG,
                              AirlockState.WireAction.PULSE), "re-arm log pulse after observed power interruption");
                      long activeDeadline = door.state().logPulseUntil();
                      require(activeDeadline - f.level().getGameTime() == 600,
                              "re-armed pulse uses the resolved 30-second duration; activeDeadline=" + activeDeadline);
                      verifyLogPulseRepeatAndExpiry(helper, f, activeDeadline, activeDeadline);
                  });
              });
         });
     }

     private static void verifyLogPulseRepeatAndExpiry(GameTestHelper helper, Fixture f,
                                                        long deadline, long firstDeadline) {
                  AirlockBlockEntity door = f.door();
                  long beforeRepeat=door.state().logPulseUntil();
                  boolean poweredBefore=door.state().powered();boolean disabledBefore=door.state().logCircuitDisabled();
                  boolean repeated=door.applyWire(door.identity(),door.state().revision(),AirlockState.Wire.LOG,AirlockState.WireAction.PULSE);
                  String diagnostic="beforeDeadline="+beforeRepeat+" afterDeadline="+door.state().logPulseUntil()+" firstDeadline="+firstDeadline+" now="+f.level().getGameTime()
                          +" poweredBefore="+poweredBefore+" poweredAfter="+door.state().powered()+" logDisabledBefore="+disabledBefore+" logDisabledAfter="+door.state().logCircuitDisabled()
                          +" "+doorDiagnostic(f.level(),door);
                  require(repeated,"repeat pulse accepted; "+diagnostic);
                  require(door.state().logPulseUntil()==deadline,"repeat does not extend active pulse deadline; "+diagnostic);
                 var saved=door.saveWithoutMetadata(f.level().registryAccess());
                 var restored=new AirlockBlockEntity(f.root(),f.level().getBlockState(f.root()));restored.loadWithComponents(saved,f.level().registryAccess());
                 require(restored.state().logCircuitDisabled()&&restored.state().logPulseUntil()==deadline,"root NBT roundtrip preserves active log pulse deadline and disabled state");
                 helper.runAfterDelay((int)(deadline-helper.getLevel().getGameTime()+2),()->{
                   require(f.door().state().logCircuitDisabled()==false&&f.door().state().logPulseUntil()==0,"intact log circuit reenables at expiry");
                   require(com.juicyslew.moonstation14.ms14.doors.ui.AirlockWireIndicators.resolve(f.door().state(),helper.getLevel().getGameTime()).log()==com.juicyslew.moonstation14.ms14.doors.ui.AirlockWireIndicators.Light.ON,"expiry restores circuit light");
                     helper.succeed();
                 });
     }
    @GameTest(templateNamespace="moonstation14",template="power_live_chain_clear",batch="airlock_core",timeoutTicks=180)
    @GameTestTags({"movement","airlock_bump"})
    public static void enrolledHumanHostBumpsPoweredAirlockThroughEntityMoveWithoutPda(GameTestHelper helper) {
        Fixture f=poweredAirlock(helper,CORE_FORCED_CHUNKS);ServerLevel level=f.level();AirlockBlockEntity door=f.door();
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
    @GameTestTags({"airlock_electrified_open"})
    public static void electrifiedBumpAppliesShockAndStillStartsDoorOpening(GameTestHelper helper) {
        Fixture f=poweredAirlock(helper,CORE_FORCED_CHUNKS);ServerLevel level=f.level();AirlockBlockEntity door=f.door();
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
            require(door.state().phase()==AirlockState.DoorPhase.OPENING,
                    "the same shock contact still begins the normal door opening transition");
            actor.discard();helper.succeed();
        });
    }
    @GameTest(templateNamespace="moonstation14",template="power_live_chain_clear",batch="airlock_core",timeoutTicks=180)
    @GameTestTags({"movement","airlock_bump","airlock_seam"})
    public static void oneEntityMoveOpensBothSeamAdjacentPaidAirlocks(GameTestHelper helper) {
        Fixture f=poweredAirlock(helper,CORE_FORCED_CHUNKS);ServerLevel level=f.level();
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
        helper.runAfterDelay(1,()->awaitBothDoorPower(helper,f,second,remaining-1,ready));
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
        Fixture f=poweredAirlock(helper,OVERRIDE_FORCED_CHUNKS);ServerLevel level=f.level();AirlockBlockEntity door=f.door();
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
    private record Fixture(ServerLevel level,BlockPos root,AirlockBlockEntity door) { }
    private static Fixture poweredAirlock(GameTestHelper helper,java.util.Set<Long> forcedChunks){
        ServerLevel level=helper.getLevel();
        BlockPos root=helper.absolutePos(new BlockPos(3,1,9));
        for(int z=2;z<=9;z++)level.setBlock(helper.absolutePos(new BlockPos(4,1,z)),Blocks.STONE.defaultBlockState(),3);
        for(int z=4;z<=6;z++)level.setBlock(helper.absolutePos(new BlockPos(2,1,z)),Blocks.STONE.defaultBlockState(),3);
        placePowerDevice(level,helper.absolutePos(new BlockPos(3,1,2)),PowerDeviceKind.HV_SOURCE,Direction.EAST);
        placePowerDevice(level,helper.absolutePos(new BlockPos(3,1,4)),PowerDeviceKind.HV_MV_SUBSTATION,Direction.EAST);
        placePowerDevice(level,helper.absolutePos(new BlockPos(3,1,6)),PowerDeviceKind.APC,Direction.WEST);
        for(int z=2;z<=4;z++)require(CableStorage.place(level,helper.absolutePos(new BlockPos(4,1,z)),Direction.WEST,CableTier.HV),"HV cable");
        for(int z=4;z<=6;z++)require(CableStorage.place(level,helper.absolutePos(new BlockPos(2,1,z)),Direction.EAST,CableTier.MV),"MV cable");
        for(int z=6;z<=9;z++)require(CableStorage.place(level,helper.absolutePos(new BlockPos(4,1,z)),Direction.WEST,CableTier.APC),"APC cable");
        var lower=ModBlocks.AIRLOCK.get().defaultBlockState();level.setBlock(root,lower,3);level.setBlock(root.above(),lower.setValue(AirlockBlock.HALF,DoubleBlockHalf.UPPER),3);
        AirlockBlockEntity door=(AirlockBlockEntity)level.getBlockEntity(root);require(door!=null,"airlock BE exists");
        forcePowerFixtureChunks(level,root,helper.absolutePos(new BlockPos(2,1,2)),helper.absolutePos(new BlockPos(4,1,9)),
                door.prototype().extensionCableReceiver().localReachBlocks(),forcedChunks);
        return new Fixture(level,root,door);
    }
    private static void forcePowerFixtureChunks(ServerLevel level,BlockPos root,BlockPos networkMin,BlockPos networkMax,int reach,java.util.Set<Long> newlyForced){
        int minX=Math.min(root.getX()-reach,networkMin.getX())-1,maxX=Math.max(root.getX()+reach,networkMax.getX())+1;
        int minZ=Math.min(root.getZ()-reach,networkMin.getZ())-1,maxZ=Math.max(root.getZ()+reach,networkMax.getZ())+1;
        for(int cx=(minX>>4);cx<=(maxX>>4);cx++)for(int cz=(minZ>>4);cz<=(maxZ>>4);cz++){
            long key=net.minecraft.world.level.ChunkPos.asLong(cx,cz);
            if(!level.getForcedChunks().contains(key)){level.setChunkForced(cx,cz,true);newlyForced.add(key);}
            level.getChunk(cx,cz);
        }
    }
    private static void unforceFixtureChunks(ServerLevel level,java.util.Set<Long> chunks){
        for(long key:chunks){var pos=new net.minecraft.world.level.ChunkPos(key);level.setChunkForced(pos.x,pos.z,false);}chunks.clear();
    }
    private static void placePowerDevice(ServerLevel level,BlockPos pos,PowerDeviceKind kind,Direction facing){
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
    private static void waitForFullPower(GameTestHelper helper,Fixture f,int remaining,Runnable ready){
        var sample=PowerRuntime.airlockPower(f.level(),f.door());
        double demand=f.door().prototype().apcPowerReceiver().requestedWatts();
        if(sample.known()&&sample.deliveredWatts()==demand){ready.run();return;}
        if(remaining<=0)throw new GameTestAssertException("airlock fixture never reached fresh full-power readiness: known="+sample.known()
                +" delivered="+sample.deliveredWatts()+" resolvedDemand="+demand+" topologyReady="+com.juicyslew.moonstation14.ms14.power.graph.PowerGraphService.topologyReady(f.level())
                 +" rootCurrent="+(f.level().getBlockEntity(f.root())==f.door())+" phase="+f.door().state().phase()+" gameTime="+f.level().getGameTime());
        helper.runAfterDelay(1,()->waitForFullPower(helper,f,remaining-1,ready));
    }
    private static synchronized void restorePrototypeCatalog() {
        var original=originalAirlockCatalog;
        if(original==null)return;
        originalAirlockCatalog=null;
        com.juicyslew.moonstation14.ms14.prototype.PrototypeRuntime.serverManager()
                .publishDecoded(com.juicyslew.moonstation14.ms14.doors.prototype.ModAirlocks.TYPE,original);
    }
}
