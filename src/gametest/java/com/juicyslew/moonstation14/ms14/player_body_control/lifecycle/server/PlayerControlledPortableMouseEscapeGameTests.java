package com.juicyslew.moonstation14.ms14.player_body_control.lifecycle.server;

import com.juicyslew.moonstation14.entities.MouseMob;
import com.juicyslew.moonstation14.gametest.support.CommittedMouseRoleSetup;
import com.juicyslew.moonstation14.gametest.support.GameTestTags;
import com.juicyslew.moonstation14.gametest.support.RegisteredServerPlayerFixture;
import com.juicyslew.moonstation14.ms14.hands.live.PortableBodyEscapeService;
import com.juicyslew.moonstation14.ms14.player_body_control.network.GhostControlPayloads;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Real lifecycle-owned mouse receives directional control while held by an ordinary player character. */
@PrefixGameTestTemplate(false)
public final class PlayerControlledPortableMouseEscapeGameTests {
    private PlayerControlledPortableMouseEscapeGameTests() { }

    @GameTest(templateNamespace = "moonstation14", template = "empty", timeoutTicks = 220)
        @GameTestTags({"living-carry", "portable-escape", "player-lifecycle", "jump-escape"})
    public static void activeMouseDirectionalInputEscapesMovingPlayerCarrier(GameTestHelper helper) {
        ServerLevel level = (ServerLevel) helper.getLevel();
        RegisteredServerPlayerFixture mouseOwner = RegisteredServerPlayerFixture.register(level, "escapemouse");
        RegisteredServerPlayerFixture carrierFixture = RegisteredServerPlayerFixture.register(level, "escapecarrier");
        MouseMob mouse = (MouseMob) com.juicyslew.moonstation14.entities.ModEntities.MOUSE.get().create(level);
        require(mouse != null, "canonical mouse constructed");
        mouse.setPos(helper.absolutePos(new BlockPos(3, 1, 3)).getCenter());
        require(level.addFreshEntity(mouse), "mouse loaded");
        CommittedMouseRoleSetup.start(helper, mouseOwner, mouse, ignored -> {
            try {
                helper.runAfterDelay(1, () -> {
                    try {
                        var carrierReady = RegisteredPlayerReadyDriver.readyAndQuery(carrierFixture);
                        net.minecraft.world.entity.LivingEntity carrier = carrierReady.body();
                        var origin = helper.absolutePos(new BlockPos(2, 1, 2));
                        setEscapeFloor(helper, origin);
                        carrier.setPos(origin.getX() + .5, origin.getY(), origin.getZ() + .5);
                        carrier.setYRot(-90); carrier.setYHeadRot(-90); carrier.setXRot(0);
                        mouse.setPos(origin.getX() + 2.5, carrier.getEyeY() - .15, origin.getZ() + .5);
                        require(com.juicyslew.moonstation14.ms14.hands.live.BodyItemTargetProbe.probeSelected(
                                        level, carrier, mouse.getUUID(), com.juicyslew.moonstation14.ms14.hands.live.BodyItemTargetProbe.MAX_REACH) != null,
                                "ordinary server first-hit probe selects exact mouse target");
                        String hand = carrierReady.initialHands().activeHand();
                        var request = new com.juicyslew.moonstation14.ms14.hands.network.BodyHandActionRequest(
                                com.juicyslew.moonstation14.ms14.hands.network.BodyHandActionRequest.Action.PICKUP_SELECTED,
                                hand, carrierReady.epoch(), 2, carrierReady.initialHands().revision(), mouse.getUUID(), null);
                        var pickup = com.juicyslew.moonstation14.ms14.hands.network.BodyHandRequestService.apply(
                                carrierFixture.player(), request);
                        require(pickup.reason() == com.juicyslew.moonstation14.ms14.hands.network.BodyHandActionResult.Reason.OK,
                                "ordinary authoritative PICKUP_SELECTED request admits the canonical player mouse: " + pickup.reason());
                        var containment = com.juicyslew.moonstation14.ms14.containment.BodyContainmentService.forLevel(level);
                        var owner = containment.ownerOf(mouse).orElseThrow();
                        require(owner instanceof com.juicyslew.moonstation14.ms14.hands.live.PortableBodyCarrierOwner
                                        && ((com.juicyslew.moonstation14.ms14.hands.live.PortableBodyCarrierOwner) owner).carrier() == carrier,
                                "ordinary player body owns exact canonical mouse through published hand key");
                        var originalMouseId = mouse.getUUID();
                        long epoch = LifecycleCharacterSessionControl.activeCharacterBody(mouseOwner.player()).orElseThrow().epoch();
                        beginDirectionalInput(helper, mouseOwner, carrierFixture, mouse, carrier, owner, containment,
                                originalMouseId, epoch, 1, true);
                    } catch (RuntimeException | Error failure) {
                        helper.fail("player-controlled portable mouse escape setup: " + failure);
                        mouseOwner.close(); carrierFixture.close();
                    }
                });
            } catch (RuntimeException | Error failure) {
                mouseOwner.close(); carrierFixture.close(); mouse.discard(); throw failure;
            }
        });
    }

    @GameTest(templateNamespace = "moonstation14", template = "empty", timeoutTicks = 220)
    @GameTestTags({"living-carry", "portable-throw", "player-lifecycle"})
    public static void committedMouseThrowSnapshotKeepsLongFlightLease(GameTestHelper helper) {
        ServerLevel level = (ServerLevel) helper.getLevel();
        RegisteredServerPlayerFixture ownerFixture = RegisteredServerPlayerFixture.register(level, "throwmouse");
        RegisteredServerPlayerFixture carrierFixture = RegisteredServerPlayerFixture.register(level, "throwcarrier");
        MouseMob mouse = (MouseMob) com.juicyslew.moonstation14.entities.ModEntities.MOUSE.get().create(level);
        require(mouse != null, "canonical mouse constructed");
        mouse.setPos(helper.absolutePos(new BlockPos(3, 1, 3)).getCenter());
        require(level.addFreshEntity(mouse), "mouse loaded");
        CommittedMouseRoleSetup.start(helper, ownerFixture, mouse, committed -> {
            helper.runAfterDelay(1, () -> {
                try {
                    var ready = RegisteredPlayerReadyDriver.readyAndQuery(carrierFixture);
                    var carrier = ready.body();
                    var origin = helper.absolutePos(new BlockPos(2, 1, 2));
                    setEscapeFloor(helper, origin);
                    carrier.setPos(origin.getX() + .5, origin.getY(), origin.getZ() + .5);
                    carrier.setYRot(-90); carrier.setYHeadRot(-90); carrier.setXRot(0);
                    committed.setPos(origin.getX() + 2.5, carrier.getEyeY() - .15, origin.getZ() + .5);
                    require(com.juicyslew.moonstation14.ms14.hands.live.BodyItemTargetProbe.probeSelected(
                                    level, carrier, committed.getUUID(), com.juicyslew.moonstation14.ms14.hands.live.BodyItemTargetProbe.MAX_REACH) != null,
                            "ordinary player first-hit probe selects committed mouse");
                    String hand = ready.initialHands().activeHand();
                    var pickup = com.juicyslew.moonstation14.ms14.hands.network.BodyHandRequestService.apply(
                            carrierFixture.player(), new com.juicyslew.moonstation14.ms14.hands.network.BodyHandActionRequest(
                                    com.juicyslew.moonstation14.ms14.hands.network.BodyHandActionRequest.Action.PICKUP_SELECTED,
                                    hand, ready.epoch(), 1, ready.initialHands().revision(), committed.getUUID(), null));
                    require(pickup.reason() == com.juicyslew.moonstation14.ms14.hands.network.BodyHandActionResult.Reason.OK,
                            "ordinary player pickup request admits committed mouse: " + pickup.reason());
                    String token = pickup.itemToken();
                    var hands = carrier.getExistingDataOrNull(com.juicyslew.moonstation14.component.ModDataAttachments.LIVE_HANDS.get());
                    var request = new com.juicyslew.moonstation14.ms14.hands.network.BodyHandActionRequest(
                            com.juicyslew.moonstation14.ms14.hands.network.BodyHandActionRequest.Action.THROW_ITEM,
                            hand, ready.epoch(), 2, hands.revision(), null, token);
                    var result = com.juicyslew.moonstation14.ms14.hands.network.BodyHandRequestService.apply(
                            carrierFixture.player(), request);
                    require(result.reason() == com.juicyslew.moonstation14.ms14.hands.network.BodyHandActionResult.Reason.OK,
                            "normal authoritative throw request accepted: " + result.reason());
                    var sessionBefore = LifecycleCharacterSessionControl.activeCharacterBody(ownerFixture.player()).orElseThrow();
                    require(sessionBefore.body() == committed, "throw retains committed body session");
                    helper.runAfterDelay(1, () -> {
                        try {
                            var active = LifecycleCharacterSessionControl.activeCharacterBody(ownerFixture.player()).orElseThrow();
                            require(active.body() == committed, "session remains committed during flight");
                             long launchTick = level.getGameTime();
                             GhostControlPayloads.Snapshot snapshot = sendCharacterSnapshot(ownerFixture);
                            require(snapshot != null, "production post-tick snapshot sender emitted payload");
                            require(snapshot.externalFlightTicks() > 6, "real thrown body snapshot retains full flight lease");
                            var buf = new net.minecraft.network.RegistryFriendlyByteBuf(io.netty.buffer.Unpooled.buffer(),
                                    net.minecraft.core.RegistryAccess.EMPTY);
                            try {
                                GhostControlPayloads.Snapshot.STREAM_CODEC.encode(buf, snapshot);
                                require(GhostControlPayloads.Snapshot.STREAM_CODEC.decode(buf).equals(snapshot),
                                        "real snapshot stream codec round-trips the long lease");
                            } finally { buf.release(); }
                             int authoritativeTicks = com.juicyslew.moonstation14.ms14.player_body_control.movement.ExternalBodyFlight.remainingTicks(committed);
                            require(authoritativeTicks > 6, "authoritative flight lease exceeds former cap: ticks=" + authoritativeTicks
                                    + ", gameTime=" + launchTick);
                             require(snapshot.externalFlightTicks() == authoritativeTicks,
                                     "launch snapshot matches authoritative lease: snapshot=" + snapshot.externalFlightTicks()
                                             + ", authoritative=" + authoritativeTicks);
                             long absoluteLeaseEndTick = Math.addExact(launchTick, authoritativeTicks);
                             verifyLeaseExpiry(helper, ownerFixture, carrierFixture, committed, authoritativeTicks,
                                     launchTick, absoluteLeaseEndTick);
                        } catch (RuntimeException | Error failure) { ownerFixture.close(); carrierFixture.close(); helper.fail("throw snapshot: " + failure); }
                    });
                } catch (RuntimeException | Error failure) { ownerFixture.close(); carrierFixture.close(); helper.fail("throw setup: " + failure); }
            });
        });
    }

    @GameTest(templateNamespace = "moonstation14", template = "empty", timeoutTicks = 80)
    @GameTestTags({"living-carry", "portable-throw", "player-lifecycle"})
    public static void normalThrowRequestLaunchesRetainedDeadMouseBody(GameTestHelper helper) {
        ServerLevel level = (ServerLevel) helper.getLevel();
        RegisteredServerPlayerFixture carrierFixture = RegisteredServerPlayerFixture.register(level, "deadthrower");
        MouseMob mouse = (MouseMob) com.juicyslew.moonstation14.entities.ModEntities.MOUSE.get().create(level);
        require(mouse != null, "canonical dead mouse constructed");
        mouse.setNoAi(true);
        mouse.setPos(helper.absolutePos(new BlockPos(3, 1, 3)).getCenter());
        require(level.addFreshEntity(mouse), "dead mouse joins level");
        com.juicyslew.moonstation14.ms14.character.CharacterIdentitySystem.enrollSupportedActor(mouse, level);
        mouse.setData(com.juicyslew.moonstation14.component.ModDataAttachments.MOB_STATE.get(),
                new com.juicyslew.moonstation14.ms14.mobs.MobStateAttachment(
                        new com.juicyslew.moonstation14.ms14.mobs.MobStateData(
                                com.juicyslew.moonstation14.ms14.character.components.MobStateComponent.State.DEAD, 1)));
        mouse.setHealth(0);
        helper.runAtTickTime(2, () -> {
            try {
                require(!mouse.isAlive() && com.juicyslew.moonstation14.ms14.mobs.MobStateSystem.isDead(mouse),
                        "native dead target state established");
                var ready = RegisteredPlayerReadyDriver.readyAndQuery(carrierFixture);
                var carrier = ready.body();
                var origin = helper.absolutePos(new BlockPos(2, 1, 2));
                setEscapeFloor(helper, origin);
                carrier.setPos(origin.getX() + .5, origin.getY(), origin.getZ() + .5);
                carrier.setYRot(-90); carrier.setYHeadRot(-90); carrier.setXRot(0);
                mouse.setPos(origin.getX() + 2.5, carrier.getEyeY() - .15, origin.getZ() + .5);
                var pickup = com.juicyslew.moonstation14.ms14.hands.network.BodyHandRequestService.apply(
                        carrierFixture.player(), new com.juicyslew.moonstation14.ms14.hands.network.BodyHandActionRequest(
                                com.juicyslew.moonstation14.ms14.hands.network.BodyHandActionRequest.Action.PICKUP_SELECTED,
                                ready.initialHands().activeHand(), ready.epoch(), 1, ready.initialHands().revision(),
                                mouse.getUUID(), null));
                require(pickup.reason() == com.juicyslew.moonstation14.ms14.hands.network.BodyHandActionResult.Reason.OK,
                        "normal pickup request accepts dead mouse: " + pickup.reason());
                var hands = carrier.getExistingDataOrNull(com.juicyslew.moonstation14.component.ModDataAttachments.LIVE_HANDS.get());
                var key = hands.stackCopy(hands.activeHand()).orElseThrow();
                var reference = com.juicyslew.moonstation14.ms14.hands.PortableBodyKeys.reference(key);
                require(reference != null && reference.body().equals(mouse.getUUID())
                                && pickup.itemToken().equals(hands.token(hands.activeHand()).orElseThrow()),
                        "ordinary pickup publishes the canonical UUID-backed key and live hand token");
                require(com.juicyslew.moonstation14.ms14.player_body_control.movement.ExternalBodyFlight
                                .canLaunchAfterPortableRelease(mouse),
                        "resolved retained-corpse movement profile passes release flight preflight");
                var throwResult = com.juicyslew.moonstation14.ms14.hands.network.BodyHandRequestService.apply(
                        carrierFixture.player(), new com.juicyslew.moonstation14.ms14.hands.network.BodyHandActionRequest(
                                com.juicyslew.moonstation14.ms14.hands.network.BodyHandActionRequest.Action.THROW_ITEM,
                                hands.activeHand(), ready.epoch(), 2, hands.revision(), null,
                                hands.token(hands.activeHand()).orElseThrow()));
                require(throwResult.reason() == com.juicyslew.moonstation14.ms14.hands.network.BodyHandActionResult.Reason.OK,
                        "normal throw request accepts retained dead mouse: " + throwResult.reason()
                                + "; gate=" + com.juicyslew.moonstation14.ms14.hands.live.BodyLooseThrow
                                        .lastPortableThrowGateForDiagnostics());
                require(com.juicyslew.moonstation14.ms14.player_body_control.movement.ExternalBodyFlight.active(mouse),
                        "dead retained body receives the standard flight lease");
                var launchPosition = mouse.position();
                com.juicyslew.moonstation14.ms14.player_body_control.movement.ExternalBodyFlight.tick(level);
                require(level.getEntity(mouse.getUUID()) == mouse && !mouse.isRemoved()
                                && mouse.position().distanceToSqr(launchPosition) > 0,
                        "shared retained-corpse physics advances original dead mouse during flight");
                carrierFixture.close(); helper.succeed();
            } catch (RuntimeException | Error failure) {
                carrierFixture.close(); helper.fail("dead mouse normal throw: " + failure);
            }
        });
    }

    private static void verifyLeaseExpiry(GameTestHelper helper, RegisteredServerPlayerFixture fixture,
                                           RegisteredServerPlayerFixture carrierFixture,
                                           MouseMob body, int previous, long launchTick, long absoluteLeaseEndTick) {
        helper.runAfterDelay(1, () -> {
            try {
                var active = LifecycleCharacterSessionControl.activeCharacterBody(fixture.player()).orElseThrow();
                require(active.body() == body, "same committed body remains controlled through lease expiry");
                var snapshot = sendCharacterSnapshot(fixture);
                require(snapshot != null, "production snapshot emitted throughout flight countdown");
                    long snapshotTick = snapshot.serverGameTick();
                    long elapsed = snapshotTick - launchTick;
                    int authoritative = com.juicyslew.moonstation14.ms14.player_body_control.movement.ExternalBodyFlight.remainingTicks(body);
                    int expected = (int) Math.max(0L, absoluteLeaseEndTick - snapshotTick);
                    require(snapshot.externalFlightTicks() == expected,
                            "snapshot lease matches launch-clock countdown: snapshot=" + snapshot.externalFlightTicks()
                                    + ", expected=" + expected + ", elapsed=" + elapsed + ", launch=" + launchTick
                                    + ", snapshotTick=" + snapshotTick);
                    int authoritativeExpected = (int) Math.max(0L, absoluteLeaseEndTick - levelGameTime(body));
                    require(authoritative == authoritativeExpected,
                            "authoritative lease matches launch-clock countdown: authoritative=" + authoritative
                                    + ", expected=" + authoritativeExpected + ", elapsed=" + elapsed + ", launch=" + launchTick);
                    require(snapshot.externalFlightTicks() <= previous,
                            "authoritative flight lease counts down monotonically: previous=" + previous + ", current="
                                    + snapshot.externalFlightTicks() + ", elapsed=" + elapsed);
                if (snapshot.externalFlightTicks() > 0) {
                    verifyLeaseExpiry(helper, fixture, carrierFixture, body, snapshot.externalFlightTicks(),
                            launchTick, absoluteLeaseEndTick);
                    return;
                }
                require(LifecycleCharacterSessionControl.activeCharacterBody(fixture.player())
                                .filter(current -> current.body() == body && current.epoch() == active.epoch()).isPresent(),
                        "lease expiry does not recover, replace, or revoke the committed session");
                fixture.close(); carrierFixture.close(); helper.succeed();
            } catch (RuntimeException | Error failure) { fixture.close(); carrierFixture.close(); helper.fail("throw lease expiry: " + failure); }
        });
    }

    private static long levelGameTime(MouseMob body) { return body.level().getGameTime(); }

    private static GhostControlPayloads.Snapshot sendCharacterSnapshot(RegisteredServerPlayerFixture fixture) {
        LifecycleCharacterSessionControl.playerTick(new PlayerTickEvent.Post(fixture.player()));
        GhostControlPayloads.Snapshot snapshot = null;
        Object outbound;
        while ((outbound = fixture.pollOutbound()) != null) {
            if (outbound instanceof net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket packet
                    && packet.payload() instanceof GhostControlPayloads.Snapshot value) snapshot = value;
        }
        return snapshot;
    }

    @GameTest(templateNamespace = "moonstation14", template = "empty", timeoutTicks = 190)
    @GameTestTags({"living-carry", "portable-escape", "mouse-ai"})
    public static void autonomousMouseEscapesNormalPlayerCarrier(GameTestHelper helper) {
        autonomousMouseEscape(helper, false);
    }

    @GameTest(templateNamespace = "moonstation14", template = "empty", timeoutTicks = 190)
    @GameTestTags({"living-carry", "portable-escape", "mouse-ai"})
    public static void autonomousMouseEscapesCreativeParkedPlayerCarrier(GameTestHelper helper) {
        autonomousMouseEscape(helper, true);
    }

    private static void autonomousMouseEscape(GameTestHelper helper, boolean creativePark) {
        ServerLevel level = (ServerLevel) helper.getLevel();
        RegisteredServerPlayerFixture holder = RegisteredServerPlayerFixture.register(level, creativePark ? "creativehold" : "npcmousehold");
        var mouse = com.juicyslew.moonstation14.entities.ModEntities.MOUSE.get().create(level);
        require(mouse != null, "autonomous mouse constructed");
        try {
            helper.runAfterDelay(2, () -> {
                try {
                    var ready = RegisteredPlayerReadyDriver.readyAndQuery(holder);
                    var carrier = ready.body();
                    var origin = helper.absolutePos(new BlockPos(2, 1, 2));
                    setEscapeFloor(helper, origin);
                    carrier.setPos(origin.getX() + .5, origin.getY(), origin.getZ() + .5);
                    carrier.setYRot(-90); carrier.setYHeadRot(-90); carrier.setXRot(0);
                    mouse.setPos(origin.getX() + 2.5, carrier.getEyeY() - .15, origin.getZ() + .5);
                    require(level.addFreshEntity(mouse), "unclaimed autonomous mouse enters level");
                    com.juicyslew.moonstation14.ms14.character.CharacterIdentitySystem.enrollSupportedActor(mouse, level);
                    require(com.juicyslew.moonstation14.ms14.player_body_control.lifecycle.character.PlayerCharacterHarnessBinder
                                    .binding(mouse) == null,
                            "autonomous mouse has no adopted role or exact OFFLINE lifecycle ownership");
                    require(com.juicyslew.moonstation14.ms14.hands.live.BodyItemTargetProbe.probeSelected(level, carrier,
                                    mouse.getUUID(), com.juicyslew.moonstation14.ms14.hands.live.BodyItemTargetProbe.MAX_REACH) != null,
                            "ordinary target probe sees unclaimed autonomous mouse");
                    String hand = ready.initialHands().activeHand();
                    var pickup = com.juicyslew.moonstation14.ms14.hands.network.BodyHandRequestService.apply(holder.player(),
                            new com.juicyslew.moonstation14.ms14.hands.network.BodyHandActionRequest(
                                    com.juicyslew.moonstation14.ms14.hands.network.BodyHandActionRequest.Action.PICKUP_SELECTED,
                                    hand, ready.epoch(), 2, ready.initialHands().revision(), mouse.getUUID(), null));
                    require(pickup.reason() == com.juicyslew.moonstation14.ms14.hands.network.BodyHandActionResult.Reason.OK,
                            "normal player pickup request binds autonomous mouse: " + pickup.reason());
                    if (creativePark) {
                        holder.player().getServer().getPlayerList().getOps().add(new net.minecraft.server.players.ServerOpListEntry(
                                holder.player().getGameProfile(), 4, true));
                        try {
                            var dispatcher = level.getServer().getCommands().getDispatcher();
                            dispatcher.execute(dispatcher.parse("ms14dev creative", holder.player().createCommandSourceStack()));
                        } catch (com.mojang.brigadier.exceptions.CommandSyntaxException failure) {
                            throw new GameTestAssertException("production Creative park failed: " + failure);
                        }
                        require(holder.player().gameMode.getGameModeForPlayer() == net.minecraft.world.level.GameType.CREATIVE,
                                "holder parked through production Creative lifecycle command");
                    }
                    var containment = com.juicyslew.moonstation14.ms14.containment.BodyContainmentService.forLevel(level);
                    var owner = containment.ownerOf(mouse).orElseThrow();
                    require(owner instanceof com.juicyslew.moonstation14.ms14.hands.live.PortableBodyCarrierOwner carried
                                    && carried.carrier() == carrier && carried.isValid(),
                            "same autonomous mouse remains under exact player carrier key custody");
                    var startCarrier = carrier.position();
                    var escapeDiagnostics = java.util.Collections.synchronizedList(new java.util.ArrayList<String>());
                    AutoCloseable diagnosticSubscription = com.juicyslew.moonstation14.ms14.hands.live.PortableBodyGameTestAccess
                            .observeEscapeDiagnostics(diagnostic -> {
                                if (diagnostic.bodyId().equals(mouse.getUUID())
                                        && java.util.Set.of("completion-outcome", "completion-denied-gate", "placement-denied",
                                                "release-result", "start-denied").contains(diagnostic.stage())
                                        && escapeDiagnostics.size() < 8)
                                    escapeDiagnostics.add(diagnostic.stage() + ":" + diagnostic.detail());
                            });
                    helper.runAfterDelay(110, () -> {
                        try {
                            var state = mouse.getExistingDataOrNull(com.juicyslew.moonstation14.component.ModDataAttachments.TIMED_OPERATIONS.get());
                            var completed = state == null ? null : state.operations().stream()
                                    .filter(operation -> operation.kind().equals(PortableBodyEscapeService.OPERATION_KIND)
                                            && operation.phase() == com.juicyslew.moonstation14.ms14.timed_interaction.TimedOperation.Phase.COMPLETED)
                                    .findFirst().orElse(null);
                            logEscapeDiagnostics("autonomous", mouse, escapeDiagnostics);
                            require(completed != null && level.getEntity(mouse.getUUID()) == mouse
                                            && containment.ownerOf(mouse).isEmpty(),
                                    "autonomous escape release failed: phase=" + (completed == null ? "none" : completed.phase())
                                            + ", owner=" + containment.ownerOf(mouse).isPresent()
                                            + ", terminal=" + lastDiagnostic(escapeDiagnostics));
                            require(carrier.position().distanceTo(startCarrier) < .3,
                                    "carrier remained within physical stationary threshold during autonomous escape");
                            if (creativePark) holder.player().getServer().getPlayerList().getOps().remove(holder.player().getGameProfile());
                            try { diagnosticSubscription.close(); } catch (Exception ignored) { }
                            holder.close(); helper.succeed();
                        } catch (RuntimeException | Error failure) {
                            if (creativePark) holder.player().getServer().getPlayerList().getOps().remove(holder.player().getGameProfile());
                            try { diagnosticSubscription.close(); } catch (Exception ignored) { }
                            holder.close(); helper.fail("autonomous portable mouse escape: " + failure);
                        }
                    });
                } catch (RuntimeException | Error failure) {
                    holder.close(); helper.fail("autonomous portable mouse escape setup: " + failure);
                }
            });
        } catch (RuntimeException | Error failure) {
            holder.close(); mouse.discard(); throw failure;
        }
    }

    private static void beginDirectionalInput(GameTestHelper helper, RegisteredServerPlayerFixture mouseOwner,
            RegisteredServerPlayerFixture carrierFixture,
            MouseMob mouse, net.minecraft.world.entity.LivingEntity carrier,
            com.juicyslew.moonstation14.ms14.containment.ContainedBodyOwner owner,
            com.juicyslew.moonstation14.ms14.containment.BodyContainmentService containment,
            java.util.UUID originalMouseId, long mouseEpoch, long sequence, boolean moveAndSelectCarrier) {
        // setPos in the fixture does not establish a grounded physics state. Consume a
        // real, neutral accepted intent and let collision physics settle before escape.
        long carrierEpoch = LifecycleCharacterSessionControl.activeCharacterBody(carrierFixture.player())
                .orElseThrow().epoch();
        warmCarrierOnFloor(helper, carrierFixture, carrier, carrierEpoch, 0, () ->
                beginDirectionalInputAfterWarmup(helper, mouseOwner, carrierFixture,
                        mouse, carrier, owner, containment, originalMouseId, mouseEpoch, sequence, moveAndSelectCarrier));
    }

    private static void warmCarrierOnFloor(GameTestHelper helper, RegisteredServerPlayerFixture fixture,
            net.minecraft.world.entity.LivingEntity carrier, long epoch, int ticks, Runnable ready) {
        RegisteredPlayerReadyDriver.submitAcceptedIntent(fixture, new GhostControlPayloads.Intent(epoch, 100 + ticks,
                (short) 0, (short) 0, (byte) 0, 0, carrier.getYRot(), carrier.getXRot()));
        helper.runAfterDelay(1, () -> {
            try {
                if (carrier.onGround()) {
                    ready.run();
                    return;
                }
                require(ticks < 11, "carrier floor warm-up failed after 12 ticks: onGround=" + carrier.onGround()
                        + ", pos=" + carrier.position());
                warmCarrierOnFloor(helper, fixture, carrier, epoch, ticks + 1, ready);
            } catch (RuntimeException | Error failure) {
                fixture.close(); helper.fail("carrier grounded warm-up: " + failure);
            }
        });
    }

    private static void beginDirectionalInputAfterWarmup(GameTestHelper helper, RegisteredServerPlayerFixture mouseOwner,
            RegisteredServerPlayerFixture carrierFixture,
            MouseMob mouse, net.minecraft.world.entity.LivingEntity carrier,
            com.juicyslew.moonstation14.ms14.containment.ContainedBodyOwner owner,
            com.juicyslew.moonstation14.ms14.containment.BodyContainmentService containment,
            java.util.UUID originalMouseId, long epoch, long sequence, boolean moveAndSelectCarrier) {
        var escapeDiagnostics = java.util.Collections.synchronizedList(new java.util.ArrayList<String>());
        AutoCloseable diagnosticSubscription = com.juicyslew.moonstation14.ms14.hands.live.PortableBodyGameTestAccess
                .observeEscapeDiagnostics(diagnostic -> {
                    if (diagnostic.bodyId().equals(mouse.getUUID())
                            && java.util.Set.of("completion-outcome", "completion-denied-gate", "placement-denied",
                                    "release-result", "start-denied").contains(diagnostic.stage())
                            && escapeDiagnostics.size() < 8)
                        escapeDiagnostics.add(diagnostic.stage() + ":" + diagnostic.detail());
                });
        try {
            submitDirectionalInput(mouseOwner, mouse, epoch, sequence);
            helper.runAfterDelay(1, () -> {
                try {
                    var state = mouse.getExistingDataOrNull(com.juicyslew.moonstation14.component.ModDataAttachments.TIMED_OPERATIONS.get());
                    var active = state == null ? null : state.operations().stream()
                            .filter(op -> op.kind().equals(PortableBodyEscapeService.OPERATION_KIND))
                            .filter(op -> op.phase() == com.juicyslew.moonstation14.ms14.timed_interaction.TimedOperation.Phase.ACTIVE)
                            .findFirst().orElse(null);
                    require(active != null, "accepted ordinary directional lifecycle input starts mouse escape operation");
                    long startTick = active.startTick();
                    submitDirectionalInput(mouseOwner, mouse, epoch, sequence + 1);
                    helper.runAfterDelay(1, () -> {
                        try {
                            var repeatedState = mouse.getExistingDataOrNull(com.juicyslew.moonstation14.component.ModDataAttachments.TIMED_OPERATIONS.get());
                            var repeated = repeatedState == null ? null : repeatedState.operations().stream()
                                    .filter(op -> op.id() == active.id()).findFirst().orElse(null);
                     require(repeated != null && repeated.startTick() == startTick,
                                     "repeated accepted directional input does not reset timed escape progress");
                            if (moveAndSelectCarrier) {
                                selectCarrierOtherHand(carrierFixture);
                                var carrierEpoch = LifecycleCharacterSessionControl.activeCharacterBody(
                                        carrierFixture.player()).orElseThrow().epoch();
                                var startPosition = carrier.position();
                                var carriedOwner = containment.ownerOf(mouse).orElseThrow();
                                moveCarrierDuringEscape(helper, carrierFixture, carrier, carrierEpoch, 75, 0,
                                        startPosition, 0, active.id(), active.startTick(), mouse, containment, carriedOwner);
                            }
                        } catch (RuntimeException | Error failure) {
                            helper.fail("repeated player-controlled escape input: " + failure);
                            closeDiagnostic(diagnosticSubscription); mouseOwner.close(); carrierFixture.close();
                        }
                    });
                    require(snapshotFor(mouseOwner, mouse, active.id(), sequence) != null,
                            "timed progress presenter publishes mouse UUID and accepted input origin sequence");
                    require(mouseOwner.player().getCamera() == mouse
                                    && LifecycleCharacterSessionControl.activeCharacterBody(mouseOwner.player()).orElseThrow().body() == mouse,
                            "same player Mind/camera remains bound to exact mouse during escape");
                    helper.runAfterDelay(100, () -> {
                        try {
                            var terminalState = mouse.getExistingDataOrNull(com.juicyslew.moonstation14.component.ModDataAttachments.TIMED_OPERATIONS.get());
                            var terminal = terminalState == null ? null : terminalState.operations().stream()
                                    .filter(op -> op.id() == active.id()).findFirst().orElse(null);
                            logEscapeDiagnostics("player-controlled", mouse, escapeDiagnostics);
                            require(terminal != null && terminal.phase() == com.juicyslew.moonstation14.ms14.timed_interaction.TimedOperation.Phase.COMPLETED,
                                    "timed escape incomplete: " + (terminal == null ? "none" : terminal.phase()));
                            require(levelEntity(mouse) == mouse && containment.ownerOf(mouse).isEmpty(),
                                    "completed escape retained custody: " + lastDiagnostic(escapeDiagnostics));
                            require(mouse.getUUID().equals(originalMouseId),
                                    "original mouse UUID remains the released body");
                            require(mouseOwner.player().getCamera() == mouse
                                            && LifecycleCharacterSessionControl.activeCharacterBody(mouseOwner.player()).orElseThrow().body() == mouse,
                                    "release does not replace the mouse lifecycle body or camera");
                            closeDiagnostic(diagnosticSubscription); mouseOwner.close(); carrierFixture.close();
                            helper.succeed();
                        } catch (RuntimeException | Error failure) {
                            helper.fail("player-controlled portable mouse escape: " + failure);
                            closeDiagnostic(diagnosticSubscription);
                            mouseOwner.close(); carrierFixture.close();
                        }
                    });
                } catch (RuntimeException | Error failure) {
                    helper.fail("player-controlled portable mouse escape progress: " + failure);
                    closeDiagnostic(diagnosticSubscription);
                    mouseOwner.close(); carrierFixture.close();
                }
            });
        } catch (RuntimeException | Error failure) {
            helper.fail("player-controlled portable mouse escape input: " + failure);
            closeDiagnostic(diagnosticSubscription);
            mouseOwner.close(); carrierFixture.close();
        }
    }

    private static void submitDirectionalInput(RegisteredServerPlayerFixture fixture, MouseMob mouse, long epoch, long sequence) {
        LifecycleCharacterSessionControl.onPayload(new GhostControlPayloads.Intent(epoch, sequence,
                (short) 0, (short) 1000, (byte) 0, 0, mouse.getYRot(), mouse.getXRot()), fixture.player());
        // Registered synthetic players are in PlayerList but do not have a live network listener tick.
        // Dispatch the same public post-tick owner that consumes accepted production intents; this
        // covers payload admission plus the production motor without claiming socket/client coverage.
        LifecycleCharacterSessionControl.playerTick(new PlayerTickEvent.Post(fixture.player()));
    }

    private static void selectCarrierOtherHand(RegisteredServerPlayerFixture fixture) {
        var carrier = LifecycleCharacterSessionControl.activeCharacterBody(fixture.player()).orElseThrow();
        var hands = carrier.body().getExistingDataOrNull(com.juicyslew.moonstation14.component.ModDataAttachments.LIVE_HANDS.get());
        String active = hands.activeHand();
        String other = hands.handIds().stream().filter(id -> !id.equals(active)).findFirst().orElseThrow();
        var result = com.juicyslew.moonstation14.ms14.hands.network.BodyHandRequestService.apply(fixture.player(),
                new com.juicyslew.moonstation14.ms14.hands.network.BodyHandActionRequest(
                        com.juicyslew.moonstation14.ms14.hands.network.BodyHandActionRequest.Action.SELECT_HAND,
                        other, carrier.epoch(), 200, hands.revision(), null, null));
        require(result.reason() == com.juicyslew.moonstation14.ms14.hands.network.BodyHandActionResult.Reason.OK,
                "captor selects the other hand through normal authenticated hand-action service: " + result.reason());
    }

    private static void moveCarrierDuringEscape(GameTestHelper helper, RegisteredServerPlayerFixture fixture,
            net.minecraft.world.entity.LivingEntity carrier, long epoch, int remaining, int step,
            net.minecraft.world.phys.Vec3 startPosition, double greatestDisplacement, long operationId,
            long operationStartTick, MouseMob mouse,
            com.juicyslew.moonstation14.ms14.containment.BodyContainmentService containment,
            com.juicyslew.moonstation14.ms14.containment.ContainedBodyOwner carriedOwner) {
        var preJumpWorldRelative = step == 0 ? mouse.position().subtract(carrier.position()) : null;
        var preJumpCanonicalRelative = step == 0
                ? containment.relativeMovementPosition(mouse, carriedOwner).orElseThrow() : null;
        short forward = (short) ((step / 8) % 2 == 0 ? 1000 : -1000);
        LifecycleCharacterSessionControl.onPayload(new GhostControlPayloads.Intent(epoch, 300 + step,
                (short) 0, forward, (byte) 0, step == 0 ? GhostControlPayloads.BUTTON_JUMP : 0, -90, 0), fixture.player());
        LifecycleCharacterSessionControl.playerTick(new PlayerTickEvent.Post(fixture.player()));
        double displacement = carrier.position().distanceTo(startPosition);
        if (step == 0)
            require(carrier.position().y > startPosition.y,
                    "accepted production jump input moved the registered human carrier upward");
        if (step == 0) {
            var postJumpWorldRelative = mouse.position().subtract(carrier.position());
            var postJumpCanonicalRelative = containment.relativeMovementPosition(mouse, carriedOwner).orElseThrow();
            require(postJumpWorldRelative.distanceTo(preJumpWorldRelative) > .3,
                    "jump produced a >0.3 world-relative child delta that fooled the former escape sampler");
            require(postJumpCanonicalRelative.distanceTo(preJumpCanonicalRelative) <= 1.0e-8,
                    "canonical pin-frame child-relative sample remained stable across carrier jump");
        }
        double maxDisplacement = Math.max(greatestDisplacement, displacement);
        // Moving back and forth keeps the root inside the fixture while exercising real
        // accepted player input and the production character movement motor each tick.
        if (remaining > 1) {
            helper.runAfterDelay(1, () -> moveCarrierDuringEscape(helper, fixture, carrier, epoch,
                    remaining - 1, step + 1, startPosition, maxDisplacement, operationId, operationStartTick, mouse,
                    containment, carriedOwner));
            return;
        }
        var state = mouse.getExistingDataOrNull(com.juicyslew.moonstation14.component.ModDataAttachments.TIMED_OPERATIONS.get());
        var operation = state == null ? null : state.operations().stream().filter(op -> op.id() == operationId).findFirst().orElse(null);
        require(operation != null && operation.phase() == com.juicyslew.moonstation14.ms14.timed_interaction.TimedOperation.Phase.ACTIVE
                        && operation.startTick() == operationStartTick,
                "same operation/progress remains active after prolonged player movement and hand selection");
        require(maxDisplacement > .3,
                "normal accepted carrier movement exceeded the world-space movement threshold");
    }

    private static net.minecraft.world.entity.Entity levelEntity(MouseMob mouse) {
        return ((ServerLevel) mouse.level()).getEntity(mouse.getUUID());
    }
    private static void setEscapeFloor(GameTestHelper helper, BlockPos origin) {
        for (int x = -2; x <= 5; x++) for (int z = -2; z <= 2; z++)
            helper.getLevel().setBlock(origin.offset(x, -1, z), Blocks.STONE.defaultBlockState(), 3);
    }
    private static com.juicyslew.moonstation14.ms14.timed_interaction.TimedInteractionSnapshot snapshotFor(
            RegisteredServerPlayerFixture fixture, MouseMob mouse, long operationId, long originSequence) {
        Object packet;
        while ((packet = fixture.pollOutbound()) != null)
            if (packet instanceof net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket payload
                    && payload.payload() instanceof com.juicyslew.moonstation14.ms14.timed_interaction.TimedInteractionSnapshot snapshot
                    && snapshot.bodyUuid().equals(mouse.getUUID())
                    && snapshot.views().stream().anyMatch(view -> view.operationID() == operationId
                            && view.originatingActionSequence() == originSequence)) return snapshot;
        return null;
    }
    private static String lastDiagnostic(java.util.List<String> diagnostics) {
        if (diagnostics.isEmpty()) return "none";
        String value = diagnostics.getLast();
        return value.substring(0, Math.min(180, value.length()));
    }
    private static void logEscapeDiagnostics(String scenario, MouseMob mouse, java.util.List<String> diagnostics) {
        String value = String.join(" | ", diagnostics);
        value = value.substring(0, Math.min(1400, value.length()));
        com.juicyslew.moonstation14.MoonStation14.LOGGER.info(
                "[portable-escape-test] scenario={} body={} diagnostics={}", scenario, mouse.getUUID(), value);
    }
    private static void closeDiagnostic(AutoCloseable subscription) {
        try { subscription.close(); } catch (Exception ignored) { }
    }
    private static void require(boolean condition, String message) {
        if (!condition) throw new GameTestAssertException(message);
    }
}
