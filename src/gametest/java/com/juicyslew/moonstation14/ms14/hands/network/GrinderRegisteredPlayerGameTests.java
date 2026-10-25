package com.juicyslew.moonstation14.ms14.hands.network;

import com.juicyslew.moonstation14.block.ModBlocks;
import com.juicyslew.moonstation14.ms14.botany.ReagentGrinderBlockEntity;
import com.juicyslew.moonstation14.gametest.support.GameTestTags;
import com.juicyslew.moonstation14.gametest.support.RegisteredServerPlayerFixture;
import com.juicyslew.moonstation14.ms14.botany.ui.GrinderControl;
import com.juicyslew.moonstation14.ms14.botany.ui.GrinderSessionService;
import com.juicyslew.moonstation14.ms14.botany.ui.GrinderSnapshotPayload;
import com.juicyslew.moonstation14.ms14.botany.GrinderProcessingSoundPayload;
import com.juicyslew.moonstation14.component.ModDataComponents;
import com.juicyslew.moonstation14.item.ModItems;
import com.juicyslew.moonstation14.ms14.hands.ItemToken;
import com.juicyslew.moonstation14.ms14.hands.transfer.ItemLocation;
import com.juicyslew.moonstation14.ms14.hands.transfer.LevelItemLocationLedgers;
import com.juicyslew.moonstation14.ms14.reagent.ReagentComponent;
import com.juicyslew.moonstation14.ms14.botany.ProduceFactory;
import com.juicyslew.moonstation14.ms14.botany.processing.GrinderProgram;
import net.minecraft.world.item.ItemStack;
import java.util.Map;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import com.juicyslew.moonstation14.ms14.hands.network.BodyBlockUseRequest;
import com.juicyslew.moonstation14.ms14.hands.network.BodyHandActionNetworking;
import com.juicyslew.moonstation14.ms14.hands.network.BodyHandStateQuery;
import com.juicyslew.moonstation14.ms14.hands.network.BodyHandStateService;
import com.juicyslew.moonstation14.ms14.player_body_control.lifecycle.server.RegisteredPlayerReadyDriver;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;


@PrefixGameTestTemplate(false)
@GameTestTags({"botany", "botany-grinder-registered-player", "ui", "interaction", "prediction"})
public final class GrinderRegisteredPlayerGameTests {
    private GrinderRegisteredPlayerGameTests() { }

    @GameTest(template = "empty", templateNamespace = "moonstation14", timeoutTicks = 100)
    public static void registeredPlayerActivatesGrinderAndControlsSession(GameTestHelper helper) {
        runRegisteredGrinderProgram(helper, GrinderProgram.Operation.GRIND);
    }

    @GameTestTags({"botany", "botany-grinder-registered-player", "ui", "interaction", "prediction", "botany-grinder-juicing"})
    @GameTest(template = "empty", templateNamespace = "moonstation14", timeoutTicks = 100)
    public static void registeredPlayerJuicesTomatoThroughAuthenticatedSession(GameTestHelper helper) {
        runRegisteredGrinderProgram(helper, GrinderProgram.Operation.JUICE);
    }

    @GameTestTags({"botany", "botany-grinder-registered-player", "interaction", "prediction"})
    @GameTest(template = "empty", templateNamespace = "moonstation14", timeoutTicks = 100)
    public static void registeredPlayerGrindsFreshRegisteredTomatoThroughAuthenticatedSession(GameTestHelper helper) {
        runRegisteredGrinderProgram(helper, GrinderProgram.Operation.GRIND, SourceCase.FRESH_TOMATO);
    }

    @GameTestTags({"botany", "botany-grinder-registered-player", "interaction", "prediction"})
    @GameTest(template = "empty", templateNamespace = "moonstation14", timeoutTicks = 100)
    public static void registeredPlayerGrindsZeroDataTomatoWithoutRefillingThroughAuthenticatedSession(GameTestHelper helper) {
        runRegisteredGrinderProgram(helper, GrinderProgram.Operation.GRIND, SourceCase.EMPTY_TOMATO);
    }

    @GameTestTags({"botany", "botany-grinder-registered-player", "interaction", "prediction", "botany-material-juice"})
    @GameTest(template = "empty", templateNamespace = "moonstation14", timeoutTicks = 100)
    public static void registeredPlayerJuicesCottonMaterialSource(GameTestHelper helper) {
        runRegisteredGrinderProgram(helper, GrinderProgram.Operation.JUICE, SourceCase.COTTON_MATERIAL);
    }

    @GameTestTags({"botany", "botany-grinder-registered-player", "interaction", "prediction", "botany-material-juice"})
    @GameTest(template = "empty", templateNamespace = "moonstation14", timeoutTicks = 100)
    public static void registeredPlayerJuicesPyrottonMaterialSource(GameTestHelper helper) {
        runRegisteredGrinderProgram(helper, GrinderProgram.Operation.JUICE, SourceCase.PYROTTON_MATERIAL);
    }

    @GameTestTags({"botany", "botany-grinder-registered-player", "interaction", "prediction", "botany-material-juice"})
    @GameTest(template = "empty", templateNamespace = "moonstation14", timeoutTicks = 100)
    public static void registeredPlayerPartiallyJuicesStackedPyrottonMaterial(GameTestHelper helper) {
        runRegisteredGrinderProgram(helper, GrinderProgram.Operation.JUICE, SourceCase.PYROTTON_STACK);
    }

    private enum SourceCase { NORMAL, FRESH_TOMATO, EMPTY_TOMATO, COTTON_MATERIAL, PYROTTON_MATERIAL, PYROTTON_STACK }

    private static void runRegisteredGrinderProgram(GameTestHelper helper, GrinderProgram.Operation operation) {
        runRegisteredGrinderProgram(helper, operation, SourceCase.NORMAL);
    }

    private static void runRegisteredGrinderProgram(GameTestHelper helper, GrinderProgram.Operation operation, SourceCase sourceCase) {
        var fixture = RegisteredServerPlayerFixture.register(helper.getLevel(), "botany-grinder-registered-v1");
        helper.runAtTickTime(2, () -> {
            try {
                var owner = RegisteredPlayerReadyDriver.readyAndQuery(fixture);
                ServerLevel level = (ServerLevel) helper.getLevel();
                BlockPos pos = helper.absolutePos(new BlockPos(3, 1, 3));
                level.setBlock(pos, ModBlocks.REAGENT_GRINDER.get().defaultBlockState(), 3);
                helper.runAfterDelay(2, () -> {
                    try {
                        var grinder = (ReagentGrinderBlockEntity) level.getBlockEntity(pos);
                        var ledger = LevelItemLocationLedgers.forLevel(level);
                          var beaker = new ItemStack(ModItems.BEAKER.get());
                          beaker.set(ModDataComponents.REAGENT.get(), new ReagentComponent(Map.of()));
                          float initialBeakerCapacity = ((com.juicyslew.moonstation14.ms14.reagent.IReagentTrait) beaker.getItem())
                                  .getCapacity(beaker, level);
                          var produce = sourceCase == SourceCase.COTTON_MATERIAL
                                  ? new ItemStack(ModItems.COTTON_MATERIAL.get())
                                  : sourceCase == SourceCase.PYROTTON_MATERIAL || sourceCase == SourceCase.PYROTTON_STACK
                                          ? new ItemStack(ModItems.PYROTTON_MATERIAL.get(), sourceCase == SourceCase.PYROTTON_STACK ? 30 : 1)
                                          : sourceCase == SourceCase.NORMAL && operation == GrinderProgram.Operation.JUICE
                                  ? ProduceFactory.createHarvest(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("moonstation14", "tomato"), 10)
                                  : sourceCase == SourceCase.NORMAL
                                          ? ProduceFactory.createHarvest(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("moonstation14", "wheat"), 5)
                                          : new ItemStack(ModItems.TOMATO.get());
                          var tomatoSource = sourceCase == SourceCase.NORMAL ? null
                                  : com.juicyslew.moonstation14.ms14.botany.ProduceSourceComposition.resolve(
                                          produce.getItem(), level);
                           if (sourceCase == SourceCase.FRESH_TOMATO || sourceCase == SourceCase.EMPTY_TOMATO) {
                              require(tomatoSource != null && tomatoSource.capacityCents() == 1400
                                              && tomatoSource.contentsCents().equals(Map.of(
                                                      net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("moonstation14", "nutriment"), 700L,
                                                      net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("moonstation14", "vitamin"), 300L,
                                                      net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("moonstation14", "water"), 400L)),
                                      "resolved registered tomato source is its exact initial 1400-cent food composition");
                              require(produce.get(ModDataComponents.PRODUCE_INSTANCE.get()) == null,
                                      "fresh registered tomato has no harvest instance metadata");
                          }
                          if (sourceCase == SourceCase.FRESH_TOMATO) {
                              require(produce.get(ModDataComponents.REAGENT.get()) == null,
                                      "fresh registered tomato has no materialized reagent component");
                              require(produce.get(ModDataComponents.GENETIC_VALUE.get()) == null,
                                      "fresh registered tomato has no harvest genetics");
                          }
                          if (sourceCase == SourceCase.EMPTY_TOMATO)
                              produce.set(ModDataComponents.REAGENT.get(), new ReagentComponent(Map.of()));
                           if (sourceCase == SourceCase.FRESH_TOMATO || sourceCase == SourceCase.EMPTY_TOMATO) {
                              require(sourceCase == SourceCase.FRESH_TOMATO
                                              ? produce.get(ModDataComponents.REAGENT.get()) == null
                                              : produce.get(ModDataComponents.REAGENT.get()) != null
                                                      && produce.get(ModDataComponents.REAGENT.get()).centContents().isEmpty(),
                                      "tomato begins as fresh lazy chemistry or explicit zero-data setup");
                              require(grinder.canStart(GrinderProgram.Operation.GRIND) == false,
                                      "tomato is not admitted before its real chamber slot transfer");
                          }
                           if (operation == GrinderProgram.Operation.JUICE && (sourceCase == SourceCase.NORMAL || sourceCase == SourceCase.FRESH_TOMATO || sourceCase == SourceCase.EMPTY_TOMATO))
                              require(!produce.get(ModDataComponents.REAGENT.get()).centContents().isEmpty(),
                                      "real tomato harvest begins with food chemistry, not juice output");
                           var materialProfile = sourceCase == SourceCase.COTTON_MATERIAL || sourceCase == SourceCase.PYROTTON_MATERIAL || sourceCase == SourceCase.PYROTTON_STACK
                                  ? com.juicyslew.moonstation14.ms14.item_processing.ModExtractableProfiles.serverCatalog().get(
                                          net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(produce.getItem())) : null;
                          if (materialProfile != null) {
                              require(materialProfile.grindableSolutionName() == null && materialProfile.juiceSolution() != null,
                                      "registered material source is juice-only and does not inherit food grindability");
                              require(produce.get(ModDataComponents.REAGENT.get()) == null,
                                      "material juice extraction starts without materializing separate edible food chemistry");
                              require(com.juicyslew.moonstation14.ms14.botany.ModProduceProfiles.serverCatalog().get(
                                              net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(produce.getItem())) == null,
                                      "material identity is not misclassified as produce");
                              require(!grinder.canStart(GrinderProgram.Operation.JUICE), "material is not admitted before real slot transfer");
                          }
                         var genetics = produce.get(ModDataComponents.GENETIC_VALUE.get());
                        var beakerToken = new ItemToken("registered-grinder-beaker-" + UUID.randomUUID());
                         var wheatToken = new ItemToken("registered-grinder-source-" + UUID.randomUUID());
                        var body = owner.body();
                        var hands = body.getExistingDataOrNull(com.juicyslew.moonstation14.component.ModDataAttachments.LIVE_HANDS.get());
                        String hand = hands.activeHand();
                        var origin = new ItemLocation.BodyHand(body.getUUID(), hand);
                        require(ledger.register(beakerToken, origin).accepted()
                                 && ledger.register(wheatToken, new ItemLocation.BodyHand(UUID.randomUUID(), "left")).accepted(), "fixture item ledger registration");
                        require(grinder.beakerSlot().insert(grinder.beakerSlot().snapshot().revision(), ledger.snapshot().revision(), beakerToken, origin, beaker), "insert fixture beaker");
                        var wheatOrigin = ledger.snapshot().locationOf(wheatToken).orElseThrow();
                         require(grinder.chamberSlots().get(0).insert(grinder.chamberSlots().get(0).snapshot().revision(), ledger.snapshot().revision(), wheatToken, wheatOrigin, produce), "insert fixture grinder input");
                            if (sourceCase == SourceCase.FRESH_TOMATO || sourceCase == SourceCase.EMPTY_TOMATO)
                                require(grinder.canStart(GrinderProgram.Operation.GRIND), "registered tomato is grind-admissible from its resolved produce profile");
                            if (materialProfile != null)
                                require(grinder.canStart(GrinderProgram.Operation.JUICE), "registered material becomes juice-admissible through its actual machine item slot");
                         require(level.getBlockState(pos).getValue(com.juicyslew.moonstation14.ms14.botany.ReagentGrinderBlock.BEAKER_ATTACHED), "chamber-only insert leaves beaker-attached appearance unchanged");
                         body.setPos(pos.getX() + .5, pos.getY() + 1, pos.getZ() - 1.5);
                         var nativeSoundPackets = new ArrayList<net.minecraft.network.protocol.game.ClientboundSoundPacket>();
                           grinder.slotTransferCommitted(true, true); // Fixture-only committed insert path after the slot was populated.
                           require(level.getBlockState(pos).getValue(com.juicyslew.moonstation14.ms14.botany.ReagentGrinderBlock.BEAKER_ATTACHED), "committed beaker insertion projects attached appearance");
                          takeOptionalSnapshot(fixture, null, nativeSoundPackets);
                         var insertSounds = soundPackets(nativeSoundPackets, com.juicyslew.moonstation14.sounds.ModSounds.CHEMMASTER_INSERT.get());
                         require(insertSounds.size() == 1, "registered body-view recipient receives native beaker-insert sound packet");
                          requireNativeSound(insertSounds.get(0), com.juicyslew.moonstation14.sounds.ModSounds.CHEMMASTER_INSERT.get(), pos, 1f);
                        var query = BodyHandStateService.query(fixture.player(), new BodyHandStateQuery(owner.epoch(), 2));
                        var held = query.hands().stream().filter(h -> h.id().equals(hand)).findFirst().orElseThrow();
                        var hit = RegisteredPlayerReadyDriver.aimAt(body, level, pos);
                        var request = new BodyBlockUseRequest(owner.epoch(), 1, query.revision(), hand, held.token(), pos,
                                hit.getDirection(), false, BodyBlockUseRequest.Intent.ACTIVATE_WORLD);
                        require(BodyHandActionNetworking.handleBlockUse(fixture.player(), request)
                                == com.juicyslew.moonstation14.ms14.hands.network.BodyBlockUseResult.Outcome.NO_CHANGE,
                                "authenticated ordinary world activation opens grinder");
                        var snapshot = takeSnapshot(fixture);
                         require(snapshot.open() && !snapshot.active() && snapshot.sessionId() != null
                                 && snapshot.placement().equals(grinder.placementIdentity())
                                 && snapshot.stateRevision() == grinder.stateRevision()
                                  && snapshot.beakerRevision() > 0 && !snapshot.chamber().get(0).item().isEmpty()
                                  && (operation == GrinderProgram.Operation.JUICE ? snapshot.canJuice() : snapshot.canGrind()),
                                 "ordinary authenticated ACTIVATE emits actual opened session snapshot on connection");
                         GrinderSessionService.control(fixture.player(), new GrinderControl(snapshot.sessionId(),
                                 snapshot.placement(), snapshot.stateRevision() - 1,
                                 operation == GrinderProgram.Operation.JUICE ? GrinderControl.Operation.JUICE : GrinderControl.Operation.GRIND, -1));
                        var rejected = takeSnapshot(fixture);
                        require(rejected.sessionId().equals(snapshot.sessionId()) && rejected.rejection() != null
                                && !rejected.active(), "stale correlated control is rejected on same session");
                         GrinderSessionService.control(fixture.player(), new GrinderControl(snapshot.sessionId(),
                                 snapshot.placement(), grinder.stateRevision(), operation == GrinderProgram.Operation.JUICE
                                         ? GrinderControl.Operation.JUICE : GrinderControl.Operation.GRIND, 0));
                          require(grinder.program().active() && grinder.program().operation() == operation,
                                  "authenticated session starts actual selected " + operation + " machine program");
                         require(level.getBlockState(pos).getValue(com.juicyslew.moonstation14.ms14.botany.ReagentGrinderBlock.WORKING), "successful authenticated start projects working appearance");
                         require(level.getBlockState(pos).getValue(com.juicyslew.moonstation14.ms14.botany.ReagentGrinderBlock.BEAKER_ATTACHED), "working projection preserves attached beaker appearance");
                        long programStartTick = grinder.program().startTick();
                        var processingCues = new ArrayList<GrinderProcessingSoundPayload>();
                        var started = takeSnapshot(fixture, processingCues);
                         require(started.sessionId().equals(snapshot.sessionId()) && started.active()
                                         && started.activeOperation() == operation && !started.canGrind() && !started.canJuice(),
                                 "actual mode-started snapshot is consumed before next control");
                        var busyRevision = grinder.stateRevision();
                        GrinderSessionService.control(fixture.player(), new GrinderControl(snapshot.sessionId(),
                                snapshot.placement(), busyRevision, GrinderControl.Operation.EJECT_BEAKER, -1));
                          var busyRejected = takeSnapshot(fixture, null, nativeSoundPackets);
                         require(busyRejected.sessionId().equals(snapshot.sessionId()) && busyRejected.rejection() != null && busyRejected.active(), "busy grinder rejects beaker ejection on correlated session");
                            require(level.getBlockState(pos).getValue(com.juicyslew.moonstation14.ms14.botany.ReagentGrinderBlock.WORKING), "denied busy ejection preserves actual working projection");
                           require(level.getBlockState(pos).getValue(com.juicyslew.moonstation14.ms14.botany.ReagentGrinderBlock.BEAKER_ATTACHED), "denied busy ejection leaves appearance unchanged");
                          require(soundPackets(nativeSoundPackets, com.juicyslew.moonstation14.sounds.ModSounds.CHEMMASTER_EJECT.get()).isEmpty(), "busy denied ejection emits no native beaker-eject packet");
                        helper.runAfterDelay(72, () -> {
                            try {
                                  var completedContents = grinder.beakerSlot().snapshot().stack().get(ModDataComponents.REAGENT.get());
                                  var completedBeaker = grinder.beakerSlot().snapshot().stack();
                                   if (sourceCase == SourceCase.FRESH_TOMATO || sourceCase == SourceCase.EMPTY_TOMATO)
                                      require(completedBeaker.is(ModItems.BEAKER.get()) && completedBeaker.getCount() == 1
                                                      && Float.compare(((com.juicyslew.moonstation14.ms14.reagent.IReagentTrait) completedBeaker.getItem())
                                                              .getCapacity(completedBeaker, level), initialBeakerCapacity) == 0,
                                              "tomato grinding preserves beaker identity, count, and capacity");
                                  int materialConsumedCount = 1;
                                   if (sourceCase == SourceCase.PYROTTON_STACK) {
                                       long capacityCents = com.juicyslew.moonstation14.ms14.reagent.ReagentUnits.fromFloat(initialBeakerCapacity);
                                       long ratio = Math.min(100, capacityCents * 100 / 18000 + 1);
                                       materialConsumedCount = (int) (30L * ratio / 100);
                                      require(materialConsumedCount > 0 && materialConsumedCount < 30,
                                              "30-unit material stack is only partially accepted by the actual registered beaker capacity");
                                      var remaining = grinder.chamberSlots().get(0).snapshot().stack();
                                      require(remaining.is(ModItems.PYROTTON_MATERIAL.get()) && remaining.getCount() == 30 - materialConsumedCount,
                                              "capacity-limited JUICE keeps the unconsumed registered material stack remainder");
                                      require(remaining.get(ModDataComponents.REAGENT.get()) == null,
                                               "partial source remainder retains its unmaterialized independent edible chemistry");
                                   }
                                   final int expectedMaterialConsumedCount = materialConsumedCount;
                                   if (materialProfile != null) {
                                       var expectedJuice = materialProfile.juiceSolution().centContents().entrySet().stream().collect(
                                               java.util.stream.Collectors.toMap(Map.Entry::getKey,
                                                       entry -> entry.getValue() * expectedMaterialConsumedCount));
                                      require(completedContents.centContents().equals(expectedJuice),
                                              "authenticated JUICE publishes exact registered material juice composition, independent of food chemistry");
                                      require(sourceCase == SourceCase.PYROTTON_STACK
                                                      ? ledger.snapshot().locationOf(wheatToken).isPresent()
                                                      : ledger.snapshot().locationOf(wheatToken).isEmpty(),
                                              "source token retires only when the complete source stack is consumed");
                                  } else if (operation == GrinderProgram.Operation.JUICE) {
                                     require(completedContents.centContents().size() == 1
                                                     && completedContents.centContents().getOrDefault(com.juicyslew.moonstation14.ms14.reagent.ModReagents.createKey("juicetomato"), 0L) == 1000,
                                             "authenticated timed JUICE replaces food chemistry with exactly 1000 cents tomato juice");
                                     require(genetics != null, "real tomato harvest carries its registered genetics into the authenticated session");
                                     require(ledger.snapshot().locationOf(wheatToken).isEmpty(), "consumed juicing source token retires from ledger");
                                  } else {
                                      if (sourceCase == SourceCase.FRESH_TOMATO) {
                                          var expected = tomatoSource.contentsCents().entrySet().stream().collect(
                                                  java.util.stream.Collectors.toMap(entry -> com.juicyslew.moonstation14.ms14.reagent.ModReagents.createKey(entry.getKey().getPath()), Map.Entry::getValue));
                                          require(completedContents.centContents().equals(expected),
                                                  "normal timed grind transfers exactly resolved initial tomato food chemistry");
                                      } else if (sourceCase == SourceCase.EMPTY_TOMATO) {
                                          require(completedContents.centContents().isEmpty(),
                                                  "zero-data registered tomato does not refill beaker with prototype chemistry");
                                      } else require(completedContents.centContents().values().stream().mapToLong(Long::longValue).sum() == 650,
                                              "timed grinding transfers 650 cents");
                                  }
                                  if (sourceCase != SourceCase.PYROTTON_STACK) {
                                      require(grinder.chamberSlots().get(0).snapshot().stack().isEmpty(), "timed mode consumes source");
                                      require(ledger.snapshot().locationOf(wheatToken).isEmpty(), "consumed grinder source token has no remaining owner");
                                  }
                                 var completed = takeSnapshot(fixture, processingCues, nativeSoundPackets);
                                 require(!level.getBlockState(pos).getValue(com.juicyslew.moonstation14.ms14.botany.ReagentGrinderBlock.WORKING), "normal 70-tick completion clears working projection");
                                var processingStart = processingCues.stream().filter(cue -> cue.operation() == GrinderProcessingSoundPayload.Operation.START).toList();
                                var processingStop = processingCues.stream().filter(cue -> cue.operation() == GrinderProcessingSoundPayload.Operation.STOP).toList();
                                var profile = grinder.resolvedProfile();
                         require(processingStart.size() == 1 && processingStop.size() == 1,
                                 "one actual grinder START and STOP processing sound payload are delivered");
                                var startCue = processingStart.get(0);
                                var stopCue = processingStop.get(0);
                                require(startCue.placement().equals(grinder.placementIdentity()) && startCue.position().equals(pos)
                                                && startCue.dimension().equals(level.dimension().location()) && startCue.startTick() == programStartTick
                                                && startCue.endTick() - startCue.startTick() == 70
                                                 && startCue.sound().equals((operation == GrinderProgram.Operation.JUICE
                                                         ? profile.juiceSound() : profile.processingSound()).sound())
                                                && startCue.volume() == 1f && startCue.pitch() == 1f,
                                         "START sound payload identifies actual placement, mode timing, and resolved profile cue");
                                require(stopCue.placement().equals(startCue.placement()) && stopCue.position().equals(startCue.position())
                                                && stopCue.dimension().equals(startCue.dimension()) && stopCue.startTick() == startCue.startTick()
                                                && stopCue.endTick() == startCue.endTick() && stopCue.sound().equals(startCue.sound())
                                                && stopCue.volume() == startCue.volume() && stopCue.pitch() == startCue.pitch(),
                                        "STOP sound payload matches the same actual grinder program and cue");
                                require(completed.sessionId().equals(snapshot.sessionId()) && !completed.active(), "same registered session observes completed inactive snapshot");
                                long completedRevision = grinder.stateRevision();
                                GrinderSessionService.control(fixture.player(), new GrinderControl(snapshot.sessionId(),
                                        snapshot.placement(), completedRevision, GrinderControl.Operation.EJECT_BEAKER, -1));
                                  var ejected = takeSnapshot(fixture, null, nativeSoundPackets);
                                 var ejectSounds = soundPackets(nativeSoundPackets, com.juicyslew.moonstation14.sounds.ModSounds.CHEMMASTER_EJECT.get());
                                 require(ejectSounds.size() == 1, "registered body-view recipient receives native beaker-eject sound packet");
                                  requireNativeSound(ejectSounds.get(0), com.juicyslew.moonstation14.sounds.ModSounds.CHEMMASTER_EJECT.get(), pos, 1f);
                                 require(ejected.sessionId().equals(snapshot.sessionId()) && ejected.stateRevision() == completedRevision + 1
                                                && ejected.chamber().stream().filter(slot -> slot.index() == -1).findFirst().orElseThrow().item().isEmpty()
                                                && grinder.beakerSlot().snapshot().stack().isEmpty(),
                                         "authenticated beaker ejection publishes empty slot at exactly next machine revision");
                                 require(!level.getBlockState(pos).getValue(com.juicyslew.moonstation14.ms14.botany.ReagentGrinderBlock.BEAKER_ATTACHED), "successful beaker ejection projects empty appearance");
                                var ledgerAfterEject = ledger.snapshot();
                                var liveHands = body.getExistingDataOrNull(com.juicyslew.moonstation14.component.ModDataAttachments.LIVE_HANDS.get());
                                boolean tokenInHand = liveHands != null && liveHands.handIds().stream()
                                        .anyMatch(id -> beakerToken.value().equals(liveHands.token(id).orElse(null)));
                                var landed = level.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class,
                                        body.getBoundingBox().inflate(5), entity -> entity.getItem().is(ModItems.BEAKER.get()));
                                require(tokenInHand || landed.size() == 1, "ejected beaker has one actual hand or world owner");
                                require(landed.size() <= 1 && (tokenInHand || !ledgerAfterEject.locations().containsKey(beakerToken)),
                                        "ejected beaker token is not duplicated between hands and world");
                                require(tokenInHand || landed.size() == 1, "preserved original beaker item token across ejection");
                                GrinderSessionService.control(fixture.player(), new GrinderControl(snapshot.sessionId(),
                                        snapshot.placement(), completedRevision, GrinderControl.Operation.EJECT_BEAKER, -1));
                                  var staleEject = takeSnapshot(fixture, null, nativeSoundPackets);
                                  require(soundPackets(nativeSoundPackets, com.juicyslew.moonstation14.sounds.ModSounds.CHEMMASTER_EJECT.get()).size() == 1, "stale duplicate ejection emits no second native sound packet");
                                require(staleEject.sessionId().equals(snapshot.sessionId()) && staleEject.rejection() != null
                                                && grinder.beakerSlot().snapshot().stack().isEmpty(),
                                        "duplicate old-revision ejection rejects without losing landed beaker");
                                 var freshWheatToken = new ItemToken("registered-grinder-eject-all-" + UUID.randomUUID());
                                 require(ledger.register(freshWheatToken, new ItemLocation.BodyHand(UUID.randomUUID(), "left")).accepted(), "register fresh EJECT_ALL fixture token");
                                 for (int i = 0; i < 2; i++) {
                                     var slot = grinder.chamberSlots().get(i);
                                      var existing = slot.snapshot();
                                      if (i == 0 && !existing.stack().isEmpty()) continue;
                                      var ejectAllWheat = ProduceFactory.createHarvest(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("moonstation14", "wheat"), 2);
                                     var token = i == 0 ? freshWheatToken : new ItemToken("registered-grinder-eject-all-second-" + UUID.randomUUID());
                                     if (i == 1) require(ledger.register(token, new ItemLocation.BodyHand(UUID.randomUUID(), "left")).accepted(), "register second fresh EJECT_ALL fixture token");
                                     require(slot.insert(slot.snapshot().revision(), ledger.snapshot().revision(), token,
                                             ledger.snapshot().locationOf(token).orElseThrow(), ejectAllWheat), "insert EJECT_ALL fixture item");
                                 }
                                 GrinderSessionService.control(fixture.player(), new GrinderControl(snapshot.sessionId(),
                                         snapshot.placement(), grinder.stateRevision(), GrinderControl.Operation.EJECT_ALL, -1));
                                 takeSnapshot(fixture, null, nativeSoundPackets);
                                 var chamberCue = grinder.resolvedProfile().chamberEjectSound();
                                 var chamberSounds = soundPackets(nativeSoundPackets, com.juicyslew.moonstation14.sounds.ModSounds.APC_SWITCH.get());
                                 require(chamberSounds.size() == 1, "nonempty two-item EJECT_ALL batch delivers exactly one native chamber-eject packet");
                                 requireNativeSound(chamberSounds.get(0), com.juicyslew.moonstation14.sounds.ModSounds.APC_SWITCH.get(), pos, chamberCue.volume());
                                 require(grinder.chamberSlots().stream().allMatch(slot -> slot.snapshot().stack().isEmpty()), "EJECT_ALL empties each populated chamber slot");
                                 GrinderSessionService.control(fixture.player(), new GrinderControl(snapshot.sessionId(),
                                         snapshot.placement(), grinder.stateRevision(), GrinderControl.Operation.EJECT_ALL, -1));
                                 var emptyAll = takeSnapshot(fixture, null, nativeSoundPackets);
                                 require(emptyAll.rejection() != null && soundPackets(nativeSoundPackets, com.juicyslew.moonstation14.sounds.ModSounds.APC_SWITCH.get()).size() == 1,
                                         "empty EJECT_ALL is rejected without another native chamber-eject packet");
                                 GrinderSessionService.control(fixture.player(), new GrinderControl(snapshot.sessionId(),
                                         snapshot.placement(), grinder.stateRevision(), GrinderControl.Operation.CLOSE, -1));
                                helper.succeed();
                            } catch (Throwable failure) { helper.fail("registered grinder timed completion: " + failure); }
                            finally { fixture.close(); }
                        });
                    } catch (Throwable failure) { fixture.close(); helper.fail("registered grinder activation/session: " + failure); }
                });
            } catch (Throwable failure) { fixture.close(); helper.fail("grinder player readiness: " + failure); }
        });
    }
    private static com.juicyslew.moonstation14.ms14.botany.ui.GrinderSnapshot takeSnapshot(RegisteredServerPlayerFixture fixture) {
        var result = takeOptionalSnapshot(fixture, null);
        if (result == null) throw new AssertionError("no grinder snapshot payload on registered player connection");
        return result;
    }
    private static com.juicyslew.moonstation14.ms14.botany.ui.GrinderSnapshot takeSnapshot(RegisteredServerPlayerFixture fixture, List<GrinderProcessingSoundPayload> cues) {
        return takeSnapshot(fixture, cues, null);
    }
    private static com.juicyslew.moonstation14.ms14.botany.ui.GrinderSnapshot takeSnapshot(RegisteredServerPlayerFixture fixture,
            List<GrinderProcessingSoundPayload> cues, List<net.minecraft.network.protocol.game.ClientboundSoundPacket> sounds) {
        var result = takeOptionalSnapshot(fixture, cues, sounds);
        if (result == null) throw new AssertionError("no grinder snapshot payload on registered player connection");
        return result;
    }
    private static com.juicyslew.moonstation14.ms14.botany.ui.GrinderSnapshot takeOptionalSnapshot(RegisteredServerPlayerFixture fixture, List<GrinderProcessingSoundPayload> cues) {
        return takeOptionalSnapshot(fixture, cues, null);
    }
    private static com.juicyslew.moonstation14.ms14.botany.ui.GrinderSnapshot takeOptionalSnapshot(RegisteredServerPlayerFixture fixture,
            List<GrinderProcessingSoundPayload> cues, List<net.minecraft.network.protocol.game.ClientboundSoundPacket> sounds) {
        Object packet;
        com.juicyslew.moonstation14.ms14.botany.ui.GrinderSnapshot latest = null;
        while ((packet = fixture.pollOutbound()) != null) {
            if (packet instanceof net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket custom) {
                if (custom.payload() instanceof GrinderSnapshotPayload payload) latest = payload.snapshot();
                else if (custom.payload() instanceof GrinderProcessingSoundPayload payload && cues != null) cues.add(payload);
            } else if (packet instanceof net.minecraft.network.protocol.game.ClientboundSoundPacket sound && sounds != null) sounds.add(sound);
        }
        return latest;
    }
    private static List<net.minecraft.network.protocol.game.ClientboundSoundPacket> soundPackets(
            List<net.minecraft.network.protocol.game.ClientboundSoundPacket> packets, net.minecraft.sounds.SoundEvent expected) {
        return packets.stream().filter(packet -> packet.getSound().value() == expected).toList();
    }
    private static void requireNativeSound(net.minecraft.network.protocol.game.ClientboundSoundPacket packet,
            net.minecraft.sounds.SoundEvent event, BlockPos pos, float volume) {
        require(packet.getSound().value().getLocation().equals(event.getLocation()),
                "native sound event expected=" + event.getLocation() + " actual=" + packet.getSound().value().getLocation());
        require(packet.getSource() == net.minecraft.sounds.SoundSource.BLOCKS,
                "native sound source expected=BLOCKS actual=" + packet.getSource());
        require(Float.compare(packet.getVolume(), volume) == 0,
                "native sound gain expected=" + volume + " actual=" + packet.getVolume());
        require(packet.getPitch() == 1f, "native sound pitch expected=1.0 actual=" + packet.getPitch());
        // Compare against the native packet's own constructor/decode path. The wire coordinate is
        // eighth-block fixed point, but the Minecraft getter converts through float, losing
        // sub-block precision at large GameTest world coordinates.
        double expectedX = pos.getX() + .5, expectedY = pos.getY() + .5, expectedZ = pos.getZ() + .5;
        var expected = new net.minecraft.network.protocol.game.ClientboundSoundPacket(
                net.minecraft.core.Holder.direct(event), net.minecraft.sounds.SoundSource.BLOCKS,
                expectedX, expectedY, expectedZ, volume, 1f, 0L);
        require(packet.getX() == expected.getX() && packet.getY() == expected.getY() && packet.getZ() == expected.getZ(),
                "native sound position expected=(" + expectedX + "," + expectedY + "," + expectedZ + ") actual=("
                        + packet.getX() + "," + packet.getY() + "," + packet.getZ() + ")");
    }
    private static void require(boolean value, String message) { if (!value) throw new AssertionError(message); }
}
