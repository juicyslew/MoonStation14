package com.juicyslew.moonstation14.ms14.player_body_control.lifecycle.server;

import com.juicyslew.moonstation14.gametest.support.BoundedGameTestWait;
import com.juicyslew.moonstation14.gametest.support.GameTestProgressObserver;
import com.juicyslew.moonstation14.gametest.support.GameTestTags;
import com.juicyslew.moonstation14.ms14.hands.live.FixtureHeldTool;
import com.juicyslew.moonstation14.ms14.hands.network.LifecycleHandActorFixture;
import com.juicyslew.moonstation14.ms14.player_body_control.lifecycle.character.PlayerCharacterHarnessEntity;
import com.juicyslew.moonstation14.ms14.player_body_control.lifecycle.persistence.SavedLifecycleProfile;
import com.juicyslew.moonstation14.ms14.shuttle.sable.MovingCabinAuthorityFixture;
import com.juicyslew.moonstation14.ms14.shuttle.sable.SableCargoChunkPersistenceGameTests;
import com.juicyslew.moonstation14.ms14.shuttle.sable.SableFixturePlot;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

/** Ordinary logout/reconnect composed with the shared passive cargo chunk unload/reload proof. */
@GameTestTags("shuttle-offline-body-chunk-persistence")
@PrefixGameTestTemplate(false)
public final class LifecycleOfflineShuttlePersistenceGameTests {
    private LifecycleOfflineShuttlePersistenceGameTests() { }

    @GameTest(template = "empty", templateNamespace = "moonstation14", timeoutTicks = 2500)
    public static void offlineBodyAndCargoSurviveOrdinaryChunkReload(GameTestHelper helper) {
        GameTestProgressObserver.armShutdownStackCapture(helper);
        startOfflineFixture(helper,new BlockPos(2100,300,1700),"offline-body-chunk-persistence",false);
    }

    @GameTest(template = "empty", templateNamespace = "moonstation14", batch = "shuttle_flight_offline_chunk_persistence", timeoutTicks = 2500)
    @GameTestTags("shuttle-flight-offline-chunk-persistence")
    public static void flyingOfflineBodyAndCargoSurviveOrdinaryChunkReload(GameTestHelper helper) {
        GameTestProgressObserver.armShutdownStackCapture(helper);
        startOfflineFixture(helper,new BlockPos(2350,300,1700),"flight-offline-body-chunk-persistence",true);
    }

    private static void startOfflineFixture(GameTestHelper helper, BlockPos origin, String sceneName, boolean flight) {
        MovingCabinAuthorityFixture.start(helper, origin, sceneName, 2.5, 1, 2.5, (fixture, body) -> {
                    var owner = fixture.owner(); var authority = owner.authority();
                    var hands = body.getExistingDataOrNull(com.juicyslew.moonstation14.component.ModDataAttachments.LIVE_HANDS.get());
                    if (hands == null || !hands.compatible(body)) { helper.fail("initial lifecycle Ready lacks compatible hands"); return; }
                    var held = FixtureHeldTool.issue(owner.actor(), hands.activeHand(), new ItemStack(net.minecraft.world.item.Items.GLASS_BOTTLE));
                    var binding = body.playerCharacterBinding();
                    if (binding == null || !binding.accountId().equals(owner.actor().getUUID()) || !binding.mindId().equals(authority.mindId().value())) {
                        helper.fail("initial body lacks exact account/Mind character binding"); return;
                    }
                    var witness = new OfflineWitness(helper, fixture, body, owner, authority.mindId(), authority.handIds(), held,
                            binding.accountId(), binding.profileKey(), flight);
                    SableCargoChunkPersistenceGameTests.runWithOfflineBody(helper, fixture, witness);
                });
    }

    private static final class OfflineWitness implements SableCargoChunkPersistenceGameTests.OfflineBodyWitness {
        private final GameTestHelper helper;
        private final MovingCabinAuthorityFixture.Ready fixture;
        private final PlayerCharacterHarnessEntity body;
        private final LifecycleHandActorFixture.Ready owner;
        private final com.juicyslew.moonstation14.ms14.player_body_control.MindId mind;
        private final List<String> handIds;
        private final FixtureHeldTool.Issued held;
        private final UUID account;
        private final String profile;
        private LifecycleServerContext context;
        private SavedLifecycleProfile offline;
        private final boolean flight;
        private Vec3 savedFrameStoragePoint;

        OfflineWitness(GameTestHelper helper, MovingCabinAuthorityFixture.Ready fixture,
                        PlayerCharacterHarnessEntity body, LifecycleHandActorFixture.Ready owner,
                        com.juicyslew.moonstation14.ms14.player_body_control.MindId mind,
                        List<String> handIds, FixtureHeldTool.Issued held, UUID account, String profile, boolean flight) {
            this.helper = helper; this.fixture = fixture; this.body = body; this.owner = owner;
            this.mind = mind; this.handIds = List.copyOf(handIds); this.held = held;
            this.account = account; this.profile = profile;
            this.flight = flight;
        }

        @Override public boolean flightMode() { return flight; }
        @Override public double launchSpeed() { return 20; }
        @Override public void onUnloadedFrame(SableFixturePlot plot, int elapsedTicks) {
            if (!flight) return;
            var saved = com.juicyslew.moonstation14.ms14.shuttle.sable.OfflineFlightPayload.read(helper.getLevel(), plot.engineId());
            com.juicyslew.moonstation14.MoonStation14.LOGGER.info("[OFFLINE_SHUTTLE_FLIGHT] SAVED tick={} elapsed={} {}",
                    helper.getTick(), elapsedTicks, saved);
            if (!saved.valid()) throw new GameTestAssertException("flight-at-unload premise FAILED: " + saved);
        }
        @Override public void onReacquiredFrame(SableFixturePlot plot) {
            if (!flight) return;
            var handle=dev.ryanhcode.sable.api.physics.handle.RigidBodyHandle.of(plot.subLevel());
            if (handle==null || !handle.isValid()) {
                com.juicyslew.moonstation14.MoonStation14.LOGGER.info(
                        "[OFFLINE_SHUTTLE_FLIGHT] FIRST_OBSERVED_RELOAD tick={} nativeHandleValid=false nativePose={} linear=unknown angular=unknown (post-load observation)",
                        helper.getTick(),plot.subLevel().logicalPose());
                return;
            }
            com.juicyslew.moonstation14.MoonStation14.LOGGER.info(
                    "[OFFLINE_SHUTTLE_FLIGHT] FIRST_OBSERVED_RELOAD tick={} nativeHandleValid=true nativePose={} linear={} angular={} (post-load observation, not saved-state equality)",
                    helper.getTick(), plot.subLevel().logicalPose(), handle.getLinearVelocity(new org.joml.Vector3d()),
                    handle.getAngularVelocity(new org.joml.Vector3d()));
        }

        @Override public void awaitOffline(Runnable ready) {
            fixture.setIntentPumpEnabled(false);
            context = LifecycleStartupRuntime.contextFor(helper.getLevel().getServer()).orElseThrow();
            owner.actor().connection.disconnect(Component.literal("offline body chunk persistence fixture"));
            BoundedGameTestWait.await(helper, 80, () -> "ordinary disconnect did not persist exact OFFLINE body",
                    () -> helper.getLevel().getServer().getPlayerList().getPlayer(account) != owner.actor()
                            && context.lifecycle().profile(account).map(p -> !p.active()
                            && p.state() == com.juicyslew.moonstation14.ms14.player_body_control.lifecycle.PlayerLifecycleRegistry.LifecycleState.OFFLINE).orElse(false),
                    () -> {
                        try { offline = context.currentAccountProfile(account).orElseThrow(); }
                        catch (IOException e) { throw new GameTestAssertException("cannot read OFFLINE saved profile: " + e); }
                        if (offline.state() != SavedLifecycleProfile.State.OFFLINE
                                || !offline.bodyId().equals(body.getUUID()) || !offline.mindId().equals(mind.value()))
                            throw new GameTestAssertException("OFFLINE profile changed body/Mind identity: " + offline);
                        var binding = body.playerCharacterBinding();
                         var tracking = ((dev.ryanhcode.sable.mixinterface.entity.entity_sublevel_collision.EntityMovementExtension)
                                 (Object) body).sable$getTrackingSubLevel();
                         if (body.isRemoved() || !body.isAlive() || !body.onGround()
                                 || helper.getLevel().getEntity(body.getUUID()) != body
                                 || binding == null || !account.equals(binding.accountId())
                                 || !profile.equals(binding.profileKey()) || !mind.value().equals(binding.mindId())
                                 || tracking != fixture.plot().subLevel())
                             throw new GameTestAssertException("body not alive/current/on-ground at OFFLINE boundary: " + diagnostic());
                         if (flight) body.sable$frameAnchorState().armLifecycleDiagnostics(fixture.level());
                         BoundedGameTestWait.observeServerTicks(helper, 1, event -> {
                             long sampleTick = event.getServer().getTickCount();
                             boolean physicalStepConsumed = ((com.juicyslew.moonstation14.ms14.player_body_control.character.MindControlledMob) (Object) body)
                                     .moonstation14$physicalStepConsumed(fixture.level(), sampleTick);
                             var bodyPoint = com.juicyslew.moonstation14.ms14.hands.live.LoadedFrameRayQuery
                                     .currentActorPoint(fixture.level(), body);
                             var postTracking = ((dev.ryanhcode.sable.mixinterface.entity.entity_sublevel_collision.EntityMovementExtension)
                                     (Object) body).sable$getTrackingSubLevel();
                             var postBinding = body.playerCharacterBinding();
                             if (!physicalStepConsumed || bodyPoint == null || !fixture.plot().engineId().equals(bodyPoint.frameId())
                                     || body.isRemoved() || !body.isAlive() || !body.onGround()
                                     || helper.getLevel().getEntity(body.getUUID()) != body
                                     || postBinding == null || !account.equals(postBinding.accountId())
                                     || !profile.equals(postBinding.profileKey()) || !mind.value().equals(postBinding.mindId())
                                     || postTracking != fixture.plot().subLevel())
                                 throw new GameTestAssertException("OFFLINE post-physics sample lacks consumed physical step or exact fixture/current body authority: " + bodyPoint
                                         + " physicalStepConsumed=" + physicalStepConsumed + " sampleTick=" + sampleTick
                                         + " trackingExact=" + (postTracking == fixture.plot().subLevel()) + " ground=" + body.onGround());
                             savedFrameStoragePoint = bodyPoint.storagePoint();
                             com.juicyslew.moonstation14.MoonStation14.LOGGER.info(
                                     "[OFFLINE_SHUTTLE_BODY_POINT] tick={} levelGameTime={} phase=ServerTick.Post/LOWEST body={} frame={} currentBodyWorld={} bodyPoint={} savedFrameStoragePoint={} nativeOwner={} nativeOwnerExactFixture={} velocity={} ground={}",
                                      sampleTick, fixture.level().getGameTime(), body.getUUID(), bodyPoint.frameId(), body.position(),
                                     bodyPoint, savedFrameStoragePoint,
                                     postTracking == null ? "null" : postTracking.getUniqueId(), postTracking == fixture.plot().subLevel(),
                                     body.getDeltaMovement(), body.onGround());
                             com.juicyslew.moonstation14.MoonStation14.LOGGER.info(
                                     "[OFFLINE_SHUTTLE_PERSISTENCE] OFFLINE tick={} body={} mind={} hands={} profile={}/{}",
                                     helper.getTick(), body.getUUID(), mind, handIds, account, profile);
                         }, ready);
                    });
        }

        @Override public Entity oldBody() { return body; }

        @Override public String diagnostic() {
            return "body=" + body.getUUID() + " removed=" + body.isRemoved() + " reason=" + body.getRemovalReason()
                    + " lookup=" + helper.getLevel().getEntity(body.getUUID()) + " offline=" + offline
                    + " mind=" + mind + " hands=" + handIds;
        }

        @Override public void reconnectAndVerify(SableFixturePlot reloaded, Runnable passed) {
            Entity loadedBeforeReconnect = helper.getLevel().getEntity(body.getUUID());
            if (!body.isRemoved() || body.getRemovalReason() != Entity.RemovalReason.UNLOADED_TO_CHUNK)
                throw new GameTestAssertException("old body did not normally unload before reconnect: " + diagnostic());
            if (loadedBeforeReconnect != null && (!(loadedBeforeReconnect instanceof PlayerCharacterHarnessEntity)
                    || loadedBeforeReconnect == body || !loadedBeforeReconnect.getUUID().equals(body.getUUID())))
                throw new GameTestAssertException("unexpected body UUID lookup before reconnect: " + loadedBeforeReconnect);
            try {
                offline = context.currentAccountProfile(account).orElseThrow();
            } catch (IOException e) { throw new GameTestAssertException("cannot reread saved OFFLINE profile: " + e); }
            if (offline.state() != SavedLifecycleProfile.State.OFFLINE || !offline.bodyId().equals(body.getUUID())
                    || !offline.mindId().equals(mind.value()))
                throw new GameTestAssertException("saved OFFLINE identity changed during chunk unload: " + offline);
            com.juicyslew.moonstation14.MoonStation14.LOGGER.info(
                    "[OFFLINE_SHUTTLE_PERSISTENCE] OLD_BODY_UNLOADED tick={} body={} reason={} loadedBeforeReconnect={} savedState={} mind={} hands={}",
                    helper.getTick(), body.getUUID(), body.getRemovalReason(), loadedBeforeReconnect, offline.state(), mind, handIds);
            final long reconnectStartTick = helper.getLevel().getServer().getTickCount();
            LifecycleHandActorFixture.reconnect(helper, owner, joined -> {
                final boolean[] ready = {false};
                final int[] firstEntityTick = {-1};
                BoundedGameTestWait.observeServerTicksUntil(helper, 40, net.neoforged.bus.api.EventPriority.LOWEST,
                    event -> {
                        var fresh = joined.authority().body();
                        if (!(fresh instanceof PlayerCharacterHarnessEntity character)) return;
                        if (firstEntityTick[0] < 0) firstEntityTick[0] = character.tickCount;
                        long sampleTick = event.getServer().getTickCount();
                        boolean physicalStepConsumed = ((com.juicyslew.moonstation14.ms14.player_body_control.character.MindControlledMob) (Object) character)
                                .moonstation14$physicalStepConsumed(fixture.level(), sampleTick);
                        var liveHands = character.getExistingDataOrNull(
                                com.juicyslew.moonstation14.component.ModDataAttachments.LIVE_HANDS.get());
                        var point = com.juicyslew.moonstation14.ms14.hands.live.LoadedFrameRayQuery
                                .currentActorPoint(fixture.level(), character);
                        try {
                            var active = context.currentAccountProfile(account).orElse(null);
                            var profileMatches = active != null && active.state() == SavedLifecycleProfile.State.ACTIVE
                                    && active.bodyId().equals(character.getUUID()) && active.mindId().equals(mind.value());
                            var ext = (dev.ryanhcode.sable.mixinterface.entity.entity_sublevel_collision.EntityMovementExtension) (Object) character;
                            boolean bindingMatches = character.playerCharacterBinding() != null
                                    && account.equals(character.playerCharacterBinding().accountId())
                                    && profile.equals(character.playerCharacterBinding().profileKey())
                                    && mind.value().equals(character.playerCharacterBinding().mindId());
                            ready[0] = physicalStepConsumed && character != body && character.getUUID().equals(body.getUUID())
                                    && character.isAlive() && !character.isRemoved() && !character.isNoGravity() && !character.noPhysics
                                    && joined.authority().mindId().equals(mind) && joined.authority().handIds().equals(handIds)
                                    && liveHands != null && liveHands.token(held.hand()).filter(held.token()::equals).isPresent()
                                    && liveHands.stackCopy(held.hand()).filter(s -> ItemStack.matches(s, held.stack())).isPresent()
                                    && bindingMatches && profileMatches && joined.actor().connection.isAcceptingMessages()
                                    && point != null && reloaded.engineId().equals(point.frameId())
                                    && sampleTick > reconnectStartTick
                                    && firstEntityTick[0] >= 0 && character.tickCount > firstEntityTick[0]
                                    && !character.sable$frameAnchorState().pending()
                                    && savedFrameStoragePoint != null && point.storagePoint().distanceTo(savedFrameStoragePoint) <= .12
                                    && ext.sable$getTrackingSubLevel() == reloaded.subLevel() && character.onGround()
                                    && character.sable$frameAnchorState().restoredFromFrame();
                            if (ready[0]) {
                                joined.neutralIntent(character);
                                com.juicyslew.moonstation14.MoonStation14.LOGGER.info(
                                        "[OFFLINE_SHUTTLE_PERSISTENCE] RECONNECTED tick={} samplePhase=ServerTick.Post/LOWEST uuid={} oldRefChanged=true mind={} hands={} heldToken={} frame={} point={} activeProfile={} entityTickAdvanced=true",
                                        sampleTick, character.getUUID(), mind, handIds, held.token(), point.frameId(), point, active);
                            }
                        } catch (IOException e) { throw new GameTestAssertException("cannot read ACTIVE profile after reconnect: " + e); }
                    }, () -> ready[0], passed,
                    () -> {
                         var candidate = joined.authority().body();
                        String witness = "body authority timeout candidate=" + (candidate == null ? "null" : candidate.getUUID());
                        if (candidate instanceof PlayerCharacterHarnessEntity character) {
                            long sampleTick = helper.getLevel().getServer().getTickCount();
                            boolean physicalStepConsumed = ((com.juicyslew.moonstation14.ms14.player_body_control.character.MindControlledMob) (Object) character)
                                    .moonstation14$physicalStepConsumed(fixture.level(), sampleTick);
                            var point = com.juicyslew.moonstation14.ms14.hands.live.LoadedFrameRayQuery.currentActorPoint(fixture.level(), character);
                            var nativeOwner = ((dev.ryanhcode.sable.mixinterface.entity.entity_sublevel_collision.EntityMovementExtension) (Object) character).sable$getTrackingSubLevel();
                            var binding = character.playerCharacterBinding();
                            var hands = character.getExistingDataOrNull(com.juicyslew.moonstation14.component.ModDataAttachments.LIVE_HANDS.get());
                            boolean profileMatches = false;
                            try {
                                var active = context.currentAccountProfile(account).orElse(null);
                                profileMatches = active != null && active.state() == SavedLifecycleProfile.State.ACTIVE
                                        && active.bodyId().equals(character.getUUID()) && active.mindId().equals(mind.value());
                            } catch (IOException ignored) { }
                             witness += " ground=" + character.onGround() + " removed=" + character.isRemoved() + " pos=" + character.position()
                                     + " sampleTick=" + sampleTick + " physicalStepConsumed=" + physicalStepConsumed
                                    + " velocity=" + character.getDeltaMovement() + " pending=" + character.sable$frameAnchorState().pending()
                                    + " restored=" + character.sable$frameAnchorState().restoredFromFrame() + " point=" + point
                                    + " nativeOwner=" + (nativeOwner == null ? "null" : nativeOwner.getUniqueId())
                                    + " expectedFrame=" + fixture.plot().engineId() + " trackingExact=" + (nativeOwner == reloaded.subLevel())
                                    + " alive=" + character.isAlive() + " noGravity=" + character.isNoGravity() + " noPhysics=" + character.noPhysics
                                    + " binding=" + (binding != null && account.equals(binding.accountId()) && profile.equals(binding.profileKey())
                                    && mind.value().equals(binding.mindId())) + " mind=" + joined.authority().mindId().equals(mind)
                                    + " profile=" + profileMatches + " hands=" + joined.authority().handIds().equals(handIds) + " liveHands=" + (hands != null)
                                    + " heldToken=" + (hands != null && hands.token(held.hand()).filter(held.token()::equals).isPresent())
                                    + " heldStack=" + (hands != null && hands.stackCopy(held.hand()).filter(s -> ItemStack.matches(s, held.stack())).isPresent())
                                     + " localDistance=" + (point == null || savedFrameStoragePoint == null ? "unknown"
                                     : point.storagePoint().distanceTo(savedFrameStoragePoint));
                            witness += " expectedStoragePoint=" + savedFrameStoragePoint
                                    + " levelGameTime=" + fixture.level().getGameTime() + " fixtureTick=" + helper.getTick();
                        }
                         com.juicyslew.moonstation14.MoonStation14.LOGGER.error("[OFFLINE_SHUTTLE_BODY_FAILURE] {}", witness);
                         helper.fail("body authority timeout [OFFLINE_SHUTTLE_BODY_FAILURE]");
                    });
            });
        }
    }
}
