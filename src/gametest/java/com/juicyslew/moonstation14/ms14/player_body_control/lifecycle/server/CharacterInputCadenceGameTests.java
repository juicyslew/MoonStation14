package com.juicyslew.moonstation14.ms14.player_body_control.lifecycle.server;

import com.juicyslew.moonstation14.gametest.support.BoundedGameTestWait;
import com.juicyslew.moonstation14.gametest.support.FixtureChunkTickets;
import com.juicyslew.moonstation14.gametest.support.GameTestTags;
import com.juicyslew.moonstation14.ms14.hands.HandActorAuthority;
import com.juicyslew.moonstation14.ms14.hands.network.LifecycleHandActorFixture;
import com.juicyslew.moonstation14.ms14.player_body_control.lifecycle.character.PlayerCharacterHarnessEntity;
import com.juicyslew.moonstation14.ms14.player_body_control.network.GhostControlPayloads;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Exercises real lifecycle intents against per-tick character movement and natural release friction. */
@GameTestTags("character-input-cadence")
@PrefixGameTestTemplate(false)
public final class CharacterInputCadenceGameTests {
    private CharacterInputCadenceGameTests() { }
    private static final int WORLD_ISOLATION_WAIT_TICKS = 80;
    private static final int WARMUP = 8, BASELINE = 12, BURST = 12, HELD_QUIET = 4;

    @GameTest(template = "empty", templateNamespace = "moonstation14", timeoutTicks = 400,
            batch = "character_input_cadence_world_only")
    public static void inputBurstIsHeldByWorldCadence(GameTestHelper helper) {
        var container = dev.ryanhcode.sable.api.sublevel.SubLevelContainer.getContainer(helper.getLevel());
        BoundedGameTestWait.await(helper, WORLD_ISOLATION_WAIT_TICKS,
                () -> "cadence fixture global WORLD isolation timed out after " + WORLD_ISOLATION_WAIT_TICKS
                        + " ticks; remaining frame count=" + container.getAllSubLevels().size()
                        + " ids=" + container.getAllSubLevels().stream().limit(8).map(Object::toString).toList(),
                () -> container.getAllSubLevels().isEmpty(), () -> startCadenceFixture(helper));
    }

    private static void startCadenceFixture(GameTestHelper helper) {
        LifecycleHandActorFixture.start(helper, "character-cadence", owner -> {
            var body = (PlayerCharacterHarnessEntity) owner.authority().body();
            var start = body.blockPosition();
            // Forward is yaw=0, i.e. +Z. Keep the full corridor and a margin loaded/ticking.
            var min = start.offset(-4, -2, -4);
            var max = start.offset(4, 3, 36);
            var lease = FixtureChunkTickets.around(helper, min, max, 1);
            for (int x = -4; x <= 4; x++) for (int z = -4; z <= 36; z++)
                helper.getLevel().setBlock(start.offset(x, -1, z), Blocks.STONE.defaultBlockState(), 3);
            FixtureChunkTickets.awaitEntityTicking(helper, lease, 12, () -> {
                BoundedGameTestWait.await(helper, 60, () -> "character did not settle grounded before cadence sample",
                        () -> body.onGround() && body.getDeltaMovement().horizontalDistanceSqr() < 1e-8,
                        () -> runCadence(helper, owner, body));
            }, start);
        });
    }

    private static void runCadence(GameTestHelper helper, LifecycleHandActorFixture.Ready owner,
                                  PlayerCharacterHarnessEntity body) {
        var original = HandActorAuthority.resolve(owner.actor()).orElseThrow();
        var binding = body.playerCharacterBinding();
        var liveHands = body.getExistingDataOrNull(com.juicyslew.moonstation14.component.ModDataAttachments.LIVE_HANDS.get());
        var legacyHands = body.getExistingDataOrNull(com.juicyslew.moonstation14.component.ModDataAttachments.HANDS.get());
        var handState = legacyHands == null ? null : legacyHands.state();
        var liveHandIds = liveHands == null ? java.util.List.<String>of() : liveHands.handIds();
        var liveActive = liveHands == null ? null : liveHands.activeHand();
        long liveRevision = liveHands == null ? -1 : liveHands.revision();
        var liveTokens = new java.util.LinkedHashMap<String, String>();
        var liveStacks = new java.util.LinkedHashMap<String, net.minecraft.world.item.ItemStack>();
        if (liveHands != null) for (String id : liveHandIds) {
            liveTokens.put(id, liveHands.token(id).orElse(null));
            liveStacks.put(id, liveHands.stackCopy(id).orElse(net.minecraft.world.item.ItemStack.EMPTY).copy());
        }
        var characterIdentity = body.getExistingDataOrNull(com.juicyslew.moonstation14.component.ModDataAttachments.CHARACTER_IDENTITY.get());
        var trackingMovement = ((Object) body) instanceof dev.ryanhcode.sable.mixinterface.entity.entity_sublevel_collision.EntityMovementExtension extension
                ? extension : null;
        if (binding == null || original.handIds().isEmpty() || liveHands == null || !liveHands.compatible(body)
                || !liveHandIds.equals(original.handIds()) || characterIdentity == null
                || !com.juicyslew.moonstation14.ms14.character.ModCharacters.HUMAN_ID.equals(characterIdentity.characterId())
                || trackingMovement == null || trackingMovement.sable$getTrackingSubLevel() != null
                || !dev.ryanhcode.sable.api.sublevel.SubLevelContainer.getContainer(helper.getLevel()).getAllSubLevels().isEmpty()) {
            helper.fail("fixture lacks exact live lifecycle binding/hands before cadence"); return;
        }
        owner.drainSnapshots();
        var startingSnapshot = owner.latestSnapshot().get();
        final long[] sequence = {owner.intentSequence().get()}, newestAck = {startingSnapshot == null ? -1 : startingSnapshot.acknowledgedAppliedSequence()},
                releaseAck = {-1}, serverTick = {-1}, snapshotTick = {-1}, rebaseRevision = {-1};
        final java.util.ArrayDeque<Long> accepted = new java.util.ArrayDeque<>();
        final int[] phaseTick = {0}, releaseQuiet = {0};
        final double[] baselineStep = {0}, lastSpeed = {Double.NaN};
        final net.minecraft.world.phys.Vec3[] previous = {body.position()};
        final boolean[] removed = {false}, done = {false};
        @SuppressWarnings("unchecked") final java.util.function.Consumer<ServerTickEvent.Pre>[] preRef = new java.util.function.Consumer[1];
        Runnable remove = () -> { if (!removed[0]) { removed[0] = true; NeoForge.EVENT_BUS.unregister(preRef[0]); } };
        helper.testInfo.addListener(new net.minecraft.gametest.framework.GameTestListener() {
            private void cleanup() { remove.run(); }
            @Override public void testStructureLoaded(net.minecraft.gametest.framework.GameTestInfo test) { }
            @Override public void testPassed(net.minecraft.gametest.framework.GameTestInfo test, net.minecraft.gametest.framework.GameTestRunner runner) { cleanup(); }
            @Override public void testFailed(net.minecraft.gametest.framework.GameTestInfo test, net.minecraft.gametest.framework.GameTestRunner runner) { cleanup(); }
            @Override public void testAddedForRerun(net.minecraft.gametest.framework.GameTestInfo oldTest, net.minecraft.gametest.framework.GameTestInfo newTest, net.minecraft.gametest.framework.GameTestRunner runner) { cleanup(); }
        });
        var pre = new java.util.function.Consumer<ServerTickEvent.Pre>() {
            @Override public void accept(ServerTickEvent.Pre event) {
                if (event.getServer() != helper.getLevel().getServer() || removed[0]) return;
                try {
                    int n = phaseTick[0]++;
                    ((com.juicyslew.moonstation14.ms14.player_body_control.character.MindControlledMob) (Object) body)
                            .moonstation14$physicalStepConsumed(helper.getLevel(), event.getServer().getTickCount());
                    if (n < WARMUP + BASELINE) {
                        enqueueSend(owner, body, accepted, sequence, 1000);
                    } else if (n >= WARMUP + BASELINE && n < WARMUP + BASELINE + BURST
                            && ((n - WARMUP - BASELINE) % 2 == 0)) {
                        enqueueSend(owner, body, accepted, sequence, 1000);
                        enqueueSend(owner, body, accepted, sequence, 1000);
                    }
                    int releaseBoundary = WARMUP + BASELINE + BURST + HELD_QUIET;
                    if (n == releaseBoundary) {
                        enqueueSend(owner, body, accepted, sequence, 1000);
                        enqueueSend(owner, body, accepted, sequence, 0);
                        releaseAck[0] = sequence[0];
                    }
                    if (n == 0) com.juicyslew.moonstation14.MoonStation14.LOGGER.info("cadence phase warmup packets=8");
                } catch (RuntimeException | AssertionError failure) {
                    remove.run(); helper.testInfo.fail(new AssertionError("cadence PRE dispatch failed", failure));
                }
            }
        };
        preRef[0] = pre;
        NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, pre);
        BoundedGameTestWait.observeServerTicksUntil(helper, WARMUP + BASELINE + BURST + HELD_QUIET + 40, EventPriority.LOWEST, event -> {
            if (phaseTick[0] == 0) return; // The observer can see this tick's POST before its first PRE callback.
            owner.drainSnapshots();
            var snapshot = owner.latestSnapshot().get();
            long tick = event.getServer().getTickCount();
            if (tick <= serverTick[0]) throw new AssertionError("nonmonotonic physical server tick");
            serverTick[0] = tick;
            if (!((com.juicyslew.moonstation14.ms14.player_body_control.character.MindControlledMob) (Object) body)
                    .moonstation14$physicalStepConsumed(helper.getLevel(), tick))
                throw new AssertionError("character physical-step marker absent for sampled server tick " + tick);
            var authority = HandActorAuthority.resolve(owner.actor()).orElse(null);
            var point = com.juicyslew.moonstation14.ms14.hands.live.LoadedFrameRayQuery.currentActorPoint(helper.getLevel(), body);
            if (authority == null || authority.actor() != original.actor() || authority.body() != body
                    || authority.body() != original.body() || !authority.mindId().equals(original.mindId())
                    || !authority.handIds().equals(original.handIds()) || !binding.equals(body.playerCharacterBinding())
                    || body.getExistingDataOrNull(com.juicyslew.moonstation14.component.ModDataAttachments.LIVE_HANDS.get()) != liveHands
                    || !liveHands.compatible(body) || !liveHands.handIds().equals(liveHandIds)
                    || liveHands.revision() != liveRevision || !liveHands.activeHand().equals(liveActive)
                    || liveHandIds.stream().anyMatch(id -> !java.util.Objects.equals(liveHands.token(id).orElse(null), liveTokens.get(id))
                            || !net.minecraft.world.item.ItemStack.matches(liveHands.stackCopy(id).orElse(net.minecraft.world.item.ItemStack.EMPTY), liveStacks.get(id)))
                    || body.getExistingDataOrNull(com.juicyslew.moonstation14.component.ModDataAttachments.HANDS.get()) != legacyHands
                    || legacyHands != null && !legacyHands.state().equals(handState)
                    || body.getExistingDataOrNull(com.juicyslew.moonstation14.component.ModDataAttachments.CHARACTER_IDENTITY.get()) != characterIdentity
                    || trackingMovement.sable$getTrackingSubLevel() != null
                    || point == null || point.frameId() != null
                    || !body.onGround() || snapshot == null || snapshot.epoch() != original.epoch()
                    || snapshot.harnessEntityId() != body.getId() || snapshot.harnessKind() != com.juicyslew.moonstation14.ms14.player_body_control.MobHarnessKind.CHARACTER
                    || snapshot.frameState().isPresent() || snapshot.movementMode() != GhostControlPayloads.Snapshot.MovementMode.GROUNDED
                    || snapshot.movementBlocked())
                throw new AssertionError("binding/authority/hands/WORLD/ground invariant changed");
            if (snapshot.serverGameTick() <= snapshotTick[0]) throw new AssertionError("authoritative snapshot tick did not advance");
            snapshotTick[0] = snapshot.serverGameTick();
            // The lifecycle reduces every admitted ordered state before this one physical tick.
            // ACK the highest processed prefix, not one command per physical step.
            while (!accepted.isEmpty()) newestAck[0] = accepted.removeFirst();
            if (snapshot.acknowledgedAppliedSequence() != newestAck[0])
                throw new AssertionError("ordered processed-prefix acknowledgement mismatch; expected=" + newestAck[0]
                        + " got=" + snapshot.acknowledgedAppliedSequence());
            if (rebaseRevision[0] == -1) rebaseRevision[0] = snapshot.movementRebaseRevision();
            else if (snapshot.movementRebaseRevision() != rebaseRevision[0]) throw new AssertionError("movement rebase revision changed");
            int n = phaseTick[0] - 1;
            if (n >= WARMUP && n < WARMUP + BASELINE) {
                double step = body.getZ() - previous[0].z;
                if (step <= 0) throw new AssertionError("yaw-zero forward baseline did not move +Z");
                baselineStep[0] += step;
                if (n == WARMUP + BASELINE - 1) {
                    baselineStep[0] /= BASELINE;
                    com.juicyslew.moonstation14.MoonStation14.LOGGER.info("cadence phase baseline ticks=12 meanZStep={}", baselineStep[0]);
                }
            } else if (n >= WARMUP + BASELINE && n < WARMUP + BASELINE + BURST) {
                double step = body.getZ() - previous[0].z;
                if (Math.abs(step - baselineStep[0]) > 1e-6)
                    throw new AssertionError("burst cadence step differs from steady baseline: " + step + " vs " + baselineStep[0]);
                if (n == WARMUP + BASELINE + BURST - 1)
                    com.juicyslew.moonstation14.MoonStation14.LOGGER.info("cadence phase burst ticks=12 packets=12");
            } else if (n >= WARMUP + BASELINE + BURST && n < WARMUP + BASELINE + BURST + HELD_QUIET) {
                if (snapshot.acknowledgedAppliedSequence() != newestAck[0])
                    throw new AssertionError("held-forward ACK changed on four packet-free ticks");
                double step = body.getZ() - previous[0].z;
                if (Math.abs(step - baselineStep[0]) > 1e-6)
                    throw new AssertionError("queue-empty held-forward step differs from baseline: " + step);
                if (n == WARMUP + BASELINE + BURST + HELD_QUIET - 1)
                    com.juicyslew.moonstation14.MoonStation14.LOGGER.info("cadence phase held-quiet ticks=4 ack={}", newestAck[0]);
            } else if (n >= WARMUP + BASELINE + BURST + HELD_QUIET) {
                boolean releaseApplied = snapshot.acknowledgedAppliedSequence() == releaseAck[0];
                if (snapshot.acknowledgedAppliedSequence() != newestAck[0])
                    throw new AssertionError("FIFO ACK differs from last consumed command");
                double speed = body.getDeltaMovement().horizontalDistance();
                double displacement = body.position().subtract(previous[0]).horizontalDistance();
                if (Double.isNaN(lastSpeed[0])) lastSpeed[0] = speed;
                if (speed > lastSpeed[0] + 1e-6 || displacement > baselineStep[0] + 1e-6)
                    throw new AssertionError("released body accelerated or took an extra/double step");
                lastSpeed[0] = speed;
                if (releaseApplied && speed <= 1e-4 && displacement <= 1e-6) releaseQuiet[0]++; else releaseQuiet[0] = 0;
                if (releaseQuiet[0] >= 4 && !done[0]) {
                    done[0] = true; remove.run();
                    com.juicyslew.moonstation14.MoonStation14.LOGGER.info("cadence phase release complete stoppedQuietTicks=4 packets={} simulatedTicks={}", sequence[0], phaseTick[0]);
                    BoundedGameTestWait.succeedAfterObservation(helper);
                }
                if (n >= WARMUP + BASELINE + BURST + HELD_QUIET + 32 && !done[0])
                    throw new AssertionError("release failed to naturally stop within 32 quiet ticks");
            }
            previous[0] = body.position();
        }, () -> false, () -> { }, () -> { remove.run(); helper.fail("cadence phase timed out before four consecutive stopped samples"); });
    }

    private static void enqueueSend(LifecycleHandActorFixture.Ready owner, PlayerCharacterHarnessEntity body,
                                    java.util.ArrayDeque<Long> accepted, long[] sequence, int forward) {
        long seq = ++sequence[0];
        owner.dispatch(new GhostControlPayloads.Intent(owner.epoch(), seq, (short) 0, (short) forward,
                (byte) 0, 0, 0f, 0f));
        accepted.addLast(seq);
    }
}
