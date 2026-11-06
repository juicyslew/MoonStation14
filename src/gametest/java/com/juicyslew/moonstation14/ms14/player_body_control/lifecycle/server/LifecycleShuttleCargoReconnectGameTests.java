package com.juicyslew.moonstation14.ms14.player_body_control.lifecycle.server;

import com.juicyslew.moonstation14.gametest.support.BoundedGameTestWait;
import com.juicyslew.moonstation14.gametest.support.GameTestTags;
import com.juicyslew.moonstation14.ms14.hands.network.LifecycleHandActorFixture;
import com.juicyslew.moonstation14.ms14.hands.live.FixtureHeldTool;
import com.juicyslew.moonstation14.ms14.shuttle.sable.MovingCabinAuthorityFixture;
import com.juicyslew.moonstation14.ms14.shuttle.sable.SableOwnedBodyCarryGameTests;
import dev.ryanhcode.sable.api.physics.handle.RigidBodyHandle;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.minecraft.sounds.SoundEvents;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.EntityLeaveLevelEvent;
import net.neoforged.neoforge.event.PlayLevelSoundEvent;
import net.neoforged.bus.api.EventPriority;
import net.minecraft.gametest.framework.GameTestListener;
import net.minecraft.gametest.framework.GameTestInfo;
import net.minecraft.gametest.framework.GameTestRunner;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import org.joml.Vector3d;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

/** Server-side reproduction of loose jug survival across an ordinary shuttle logout/rejoin. */
@GameTestTags("shuttle-cargo-reconnect-reproduction")
@PrefixGameTestTemplate(false)
public final class LifecycleShuttleCargoReconnectGameTests {
    private LifecycleShuttleCargoReconnectGameTests() { }

    @GameTest(template = "empty", templateNamespace = "moonstation14", timeoutTicks = 1000)
    public static void threeNormalJugsSurviveLogoutFlightAndRejoin(GameTestHelper helper) {
        start(helper, 2, false);
    }

    @GameTestTags("shuttle-resident-landing-reconnect")
    @GameTest(template = "empty", templateNamespace = "moonstation14", timeoutTicks = 1000)
    public static void threeNormalJugsSurviveResidentLandingAndRejoin(GameTestHelper helper) {
        start(helper, 6, true);
    }

    private static void start(GameTestHelper helper, int expectedSubsteps, boolean requireLandingBeforeReconnect) {
        BlockPos origin = requireLandingBeforeReconnect ? new BlockPos(1800, -47, 1400) : new BlockPos(1600, -47, 1200);
        MovingCabinAuthorityFixture.start(helper, origin,
                "cargo-reconnect-reproduction", 2.5, 1, 2.5, (fixture, body) -> {
            var level = fixture.level();
            var owner = fixture.owner();
            var actor = owner.actor();
            var server = helper.getLevel().getServer();
            var frameId = fixture.plot().engineId();
            var mind = owner.authority().mindId();
            var hands = owner.authority().handIds();
            var liveHands = body.getExistingDataOrNull(com.juicyslew.moonstation14.component.ModDataAttachments.LIVE_HANDS.get());
            if (liveHands == null || !liveHands.compatible(body)) { helper.fail("initial Ready lacks compatible hands"); return; }
            var issued = FixtureHeldTool.issue(actor, liveHands.activeHand(), new ItemStack(net.minecraft.world.item.Items.GLASS_BOTTLE));
            var water = com.juicyslew.moonstation14.ms14.reagent.ModReagents.createKey("water");
            var cargo = new ArrayList<SableOwnedBodyCarryGameTests.RegisteredCargo>();
            double[][] offsets = {{1.3, 1.4}, {3.6, 1.4}, {1.3, 3.6}};
            for (int i = 0; i < offsets.length; i++) {
                ItemStack stack = new ItemStack(com.juicyslew.moonstation14.item.ModItems.JUG.get());
                stack.set(com.juicyslew.moonstation14.component.ModDataComponents.REAGENT.get(),
                        com.juicyslew.moonstation14.ms14.reagent.ReagentComponent.fromCents(Map.of(water,
                                com.juicyslew.moonstation14.ms14.reagent.ReagentUnits.fromFloat(5f + i))));
                var position = SableOwnedBodyCarryGameTests.deckFeetAt(fixture.plot(), fixture.cabin().origin(),
                        offsets[i][0], offsets[i][1]);
                cargo.add(SableOwnedBodyCarryGameTests.registerCargoRoot(level, position, stack,
                        "cargo-reconnect-" + i + "-"));
            }
            var expected = cargo.stream().map(root -> root.entity().getUUID()).toList();
            var fingerprints = cargo.stream().map(root -> fingerprint(root.entity().getItem())).toList();
            var itemSnapshots = cargo.stream().map(root -> root.entity().getItem().copy()).toList();
            var frameHandle = RigidBodyHandle.of(fixture.plot().subLevel());
            int readySubsteps = requireLandingBeforeReconnect
                    ? dev.ryanhcode.sable.sublevel.system.SubLevelPhysicsSystem.require(level).getConfig().substepsPerTick
                    : -1;
            if (requireLandingBeforeReconnect) {
                com.juicyslew.moonstation14.MoonStation14.LOGGER.info(
                        "resident landing reconnect fixture ready effectiveSubsteps={} frame={} level={}",
                        readySubsteps, fixture.plot().engineId(), level.dimension().location());
            }
            var journal = new CargoJournal(helper, fixture, body, cargo, expected, fingerprints, itemSnapshots,
                    requireLandingBeforeReconnect);
            if (requireLandingBeforeReconnect) journal.effectiveSubsteps = readySubsteps;
            journal.install();
            fixture.setIntentPumpEnabled(false);
            BoundedGameTestWait.await(helper, 100, () -> "three normal cargo roots did not settle on cabin frame",
                    () -> cargo.stream().allMatch(root -> root.entity().settled()
                            && level.getEntity(root.entity().getUUID()) == root.entity()
                            && com.juicyslew.moonstation14.ms14.hands.live.LoadedFrameRayQuery
                                    .currentActorPoint(level, root.entity()) != null
                            && frameId.equals(com.juicyslew.moonstation14.ms14.hands.live.LoadedFrameRayQuery
                                    .currentActorPoint(level, root.entity()).frameId())), () -> {
                int effective = requireLandingBeforeReconnect ? readySubsteps
                        : dev.ryanhcode.sable.sublevel.system.SubLevelPhysicsSystem.require(level).getConfig().substepsPerTick;
                if (effective != expectedSubsteps) { journal.fail("expected effective substeps=" + expectedSubsteps + ", got " + effective); return; }
                journal.effectiveSubsteps = effective;
                if (!journal.initialAboard(frameId)) {
                    journal.helperFailure("initial body or settled cargo outside authored cabin interior/support");
                    journal.fail("prelaunch cabin/support admission failed");
                    return;
                }
                journal.beginFlight();
                frameHandle.addLinearAndAngularVelocity(new Vector3d(0, 20, 0), new Vector3d());
                if (requireLandingBeforeReconnect) {
                    BoundedGameTestWait.await(helper, 8, () -> "launched frame did not begin rising before disconnect",
                            () -> frameHandle.getLinearVelocity(new Vector3d()).y > 0
                                    && fixture.plot().subLevel().logicalPose().position().y > journal.initialFrameY,
                            () -> {
                                fixture.closeSceneLease();
                                boolean ownerFarFromScene = owner.actor().blockPosition().distToCenterSqr(
                                        fixture.cabin().origin().getCenter()) > 128 * 128;
                                if (!fixture.sceneLeaseClosed() || !ownerFarFromScene || !journal.hasProductionResidencyTicket()) {
                                    journal.fail("could not prove scene lease released while production frame ticket remains: "
                                            + journal.residencyEvidence() + ", ownerFarFromScene=" + ownerFarFromScene
                                            + ", ownerPosition=" + owner.actor().position());
                                    return;
                                }
                                journal.markSceneLeaseReleased();
                                com.juicyslew.moonstation14.MoonStation14.LOGGER.info(
                                        "resident landing fixture released scene lease before disconnect: {}",
                                        journal.residencyEvidence());
                                disconnect(helper, fixture, body, owner, cargo, expected, fingerprints, frameId,
                                        mind, hands, issued, server, journal, itemSnapshots, requireLandingBeforeReconnect);
                            });
                } else {
                    BoundedGameTestWait.observeServerTicks(helper, 2, event -> { }, () -> disconnect(helper, fixture,
                            body, owner, cargo, expected, fingerprints, frameId, mind, hands, issued, server, journal,
                            itemSnapshots, false));
                }
                BoundedGameTestWait.safeDelay(helper, 500, () -> {
                    if (!journal.closed) journal.fail("explicit 500-tick post-launch deadline reached");
                });
            });
        });
    }

    private static void disconnect(GameTestHelper helper, MovingCabinAuthorityFixture.Ready fixture,
            com.juicyslew.moonstation14.ms14.player_body_control.lifecycle.character.PlayerCharacterHarnessEntity body,
            LifecycleHandActorFixture.Ready owner, List<SableOwnedBodyCarryGameTests.RegisteredCargo> cargo,
            List<UUID> expected, List<String> fingerprints, UUID frameId,
            com.juicyslew.moonstation14.ms14.player_body_control.MindId mind, List<String> hands,
            FixtureHeldTool.Issued issued, net.minecraft.server.MinecraftServer server, CargoJournal journal,
            List<ItemStack> itemSnapshots, boolean landingVariant) {
                    var actor = owner.actor();
                    var context = LifecycleStartupRuntime.contextFor(server).orElseThrow();
                    journal.phase("DETACHING");
                    actor.connection.disconnect(Component.literal("cargo reconnect reproduction"));
                    BoundedGameTestWait.await(helper, 60, () -> "disconnect did not persist OFFLINE lifecycle",
                            () -> server.getPlayerList().getPlayer(actor.getUUID()) != actor
                                    && context.lifecycle().profile(actor.getUUID()).map(p -> !p.active()
                                            && p.state() == com.juicyslew.moonstation14.ms14.player_body_control.lifecycle.PlayerLifecycleRegistry.LifecycleState.OFFLINE).orElse(false),
                            () -> {
                                journal.phase("OFFLINE");
                                observeOffline(helper, fixture, body, owner, cargo, expected, fingerprints,
                                        frameId, mind, hands, issued, context, journal, itemSnapshots, landingVariant ? 400 : 160);
                            });
    }

    private static void observeOffline(GameTestHelper helper, MovingCabinAuthorityFixture.Ready fixture,
            com.juicyslew.moonstation14.ms14.player_body_control.lifecycle.character.PlayerCharacterHarnessEntity body,
            com.juicyslew.moonstation14.ms14.hands.network.LifecycleHandActorFixture.Ready owner,
            List<SableOwnedBodyCarryGameTests.RegisteredCargo> cargo, List<UUID> expected,
            List<String> fingerprints, UUID frame, com.juicyslew.moonstation14.ms14.player_body_control.MindId mind,
            List<String> hands, FixtureHeldTool.Issued issued, LifecycleServerContext context,
            CargoJournal journal, List<ItemStack> itemSnapshots, int ticks) {
        Runnable reconnect = () -> {
            journal.phase("RECONNECTING");
            LifecycleHandActorFixture.reconnect(helper, owner, joined -> {
                journal.phase("REATTACHED");
                BoundedGameTestWait.observeServerTicks(helper, 60, event -> { }, () -> {
                    boolean bodySame = joined.authority().body() == body
                            && joined.authority().mindId().equals(mind) && joined.authority().handIds().equals(hands)
                            && body.isAlive() && !body.isRemoved()
                            && body.getExistingDataOrNull(com.juicyslew.moonstation14.component.ModDataAttachments.LIVE_HANDS.get()) != null
                            && body.getExistingDataOrNull(com.juicyslew.moonstation14.component.ModDataAttachments.LIVE_HANDS.get())
                                    .token(issued.hand()).filter(issued.token()::equals).isPresent()
                            && body.getExistingDataOrNull(com.juicyslew.moonstation14.component.ModDataAttachments.LIVE_HANDS.get())
                                    .stackCopy(issued.hand()).filter(stack -> ItemStack.matches(stack, issued.stack())).isPresent();
                    var bodyPoint = com.juicyslew.moonstation14.ms14.hands.live.LoadedFrameRayQuery
                            .currentActorPoint(fixture.level(), body);
                    bodySame &= bodyPoint != null && frame.equals(bodyPoint.frameId())
                            && insideCabin(fixture, bodyPoint.storagePoint())
                            && joined.actor().connection.isAcceptingMessages();
                    try {
                        var active = context.currentAccountProfile(owner.actor().getUUID()).orElse(null);
                        bodySame &= active != null
                                && active.state() == com.juicyslew.moonstation14.ms14.player_body_control.lifecycle.persistence.SavedLifecycleProfile.State.ACTIVE
                                && active.bodyId().equals(body.getUUID()) && active.mindId().equals(mind.value());
                    } catch (java.io.IOException failure) {
                        bodySame = false;
                    }
                    boolean cargoSame = true;
                    for (int i = 0; i < cargo.size(); i++) {
                        var root = cargo.get(i);
                        var entity = resolved(fixture.level(), expected.get(i));
                        var ledger = com.juicyslew.moonstation14.ms14.hands.transfer.LevelItemLocationLedgers
                                .forLevel(fixture.level()).snapshot().locationOf(root.token());
                        boolean intact = entity != null && !entity.isRemoved()
                                && ItemStack.matches(itemSnapshots.get(i), entity.getItem())
                                && fingerprints.get(i).equals(fingerprint(entity.getItem()))
                                && ledger.filter(new com.juicyslew.moonstation14.ms14.hands.transfer.ItemLocation.WorldEntity(
                                        root.world(), expected.get(i))::equals).isPresent();
                        cargoSame &= intact;
                    }
                    var handle = RigidBodyHandle.of(fixture.plot().subLevel());
                    var velocity = handle.getLinearVelocity(new Vector3d());
                    var angular = handle.getAngularVelocity(new Vector3d());
                    double frameY = fixture.plot().subLevel().logicalPose().position().y;
                    boolean landed = journal.sawAscent && journal.sawDescent && journal.sawNaturalRest
                            && Math.abs(frameY - journal.initialFrameY) <= .10
                            && velocity.lengthSquared() <= .0025 && angular.lengthSquared() <= .0001;
                    for (int i = 0; i < cargo.size(); i++) {
                        var entity = resolved(fixture.level(), expected.get(i));
                        var point = entity == null ? null : com.juicyslew.moonstation14.ms14.hands.live.LoadedFrameRayQuery
                                .currentActorPoint(fixture.level(), entity);
                        var tracking = entity == null ? null : ((dev.ryanhcode.sable.mixinterface.entity.entity_sublevel_collision.EntityMovementExtension)
                                (Object) entity).sable$getTrackingSubLevel();
                        if (entity == null || point == null || !frame.equals(point.frameId())
                                || tracking != fixture.plot().subLevel() || !entity.settled()
                                || !insideCabin(fixture, point.storagePoint())) cargoSame = false;
                    }
                    if (!bodySame) journal.helperFailure("body/Mind/hands/issued item/current cabin authority failed after reconnect");
                    if (!cargoSame) journal.helperFailure("cargo UUID/content/ledger/current-frame aboard check failed at completion");
                    if (!landed) journal.helperFailure("native flight did not ascend, descend and naturally settle near initial pose");
                    journal.phase("COMPLETE");
                    if (journal.failureText() != null) journal.fail("latched failure: " + journal.failureText());
                    else journal.succeed();
                });
            });
        };
        if (journal.requireLandingBeforeReconnect) {
            BoundedGameTestWait.await(helper, ticks, () -> "offline shuttle did not witness stable authored-terrain landing: " + journal.flightEvidence(),
                    journal::stableLanding, reconnect::run);
        } else BoundedGameTestWait.observeServerTicks(helper, ticks, event -> { }, reconnect);
    }

    private static String fingerprint(ItemStack stack) {
        var reagent = stack.get(com.juicyslew.moonstation14.component.ModDataComponents.REAGENT.get());
        return stack.getItem() + "/" + stack.getCount() + "/" + (reagent == null ? null : reagent.centContents());
    }

    private static com.juicyslew.moonstation14.entities.LooseItemEntity resolved(
            net.minecraft.server.level.ServerLevel level, UUID uuid) {
        var entity = level.getEntity(uuid);
        return entity instanceof com.juicyslew.moonstation14.entities.LooseItemEntity loose ? loose : null;
    }

    private static boolean insideCabin(MovingCabinAuthorityFixture.Ready fixture, Vec3 storage) {
        if (storage == null) return false;
        var cells = fixture.cabin().roomCells();
        if (cells.isEmpty()) return false;
        int minX = cells.stream().mapToInt(pos -> pos.getX()).min().orElse(Integer.MIN_VALUE);
        int maxX = cells.stream().mapToInt(pos -> pos.getX()).max().orElse(Integer.MIN_VALUE) + 1;
        int minY = cells.stream().mapToInt(pos -> pos.getY()).min().orElse(Integer.MIN_VALUE);
        int maxY = cells.stream().mapToInt(pos -> pos.getY()).max().orElse(Integer.MIN_VALUE) + 1;
        int minZ = cells.stream().mapToInt(pos -> pos.getZ()).min().orElse(Integer.MIN_VALUE);
        int maxZ = cells.stream().mapToInt(pos -> pos.getZ()).max().orElse(Integer.MIN_VALUE) + 1;
        return storage.x >= minX && storage.x <= maxX
                && storage.y >= minY - .10 && storage.y <= maxY
                && storage.z >= minZ && storage.z <= maxZ;
    }

    private static String snapshot(GameTestHelper helper, MovingCabinAuthorityFixture.Ready fixture,
            com.juicyslew.moonstation14.ms14.player_body_control.lifecycle.character.PlayerCharacterHarnessEntity body,
            List<SableOwnedBodyCarryGameTests.RegisteredCargo> cargo) {
        return "tick=" + helper.getLevel().getServer().getTickCount() + " frame="
                + fixture.plot().subLevel().logicalPose() + " frameVelocity="
                + RigidBodyHandle.of(fixture.plot().subLevel()).getLinearVelocity(new Vector3d())
                + " body=" + body.position() + "/alive=" + body.isAlive() + "/framePoint="
                + com.juicyslew.moonstation14.ms14.hands.live.LoadedFrameRayQuery.currentActorPoint(body.level(), body)
                + " cargo=" + cargo.stream().map(root -> {
                    var entity = resolved(fixture.level(), root.entity().getUUID());
                    if (entity == null) return root.entity().getUUID() + ":absent-or-wrong-type";
                    var point = com.juicyslew.moonstation14.ms14.hands.live.LoadedFrameRayQuery
                            .currentActorPoint(entity.level(), entity);
                    var tracking = ((dev.ryanhcode.sable.mixinterface.entity.entity_sublevel_collision.EntityMovementExtension)
                            (Object) entity).sable$getTrackingSubLevel();
                    return entity.getUUID() + ":" + entity.position() + ":" + entity.getDeltaMovement()
                            + ":removed=" + entity.isRemoved() + ":settled=" + entity.settled()
                            + ":ground=" + entity.onGround() + ":framePoint=" + point + ":tracking="
                            + (tracking == null ? null : tracking.getUniqueId()) + ":stack=" + fingerprint(entity.getItem());
                }).toList();
    }

    private static final class CargoJournal {
        private final GameTestHelper helper;
        private final MovingCabinAuthorityFixture.Ready fixture;
        private final com.juicyslew.moonstation14.ms14.player_body_control.lifecycle.character.PlayerCharacterHarnessEntity body;
        private final List<SableOwnedBodyCarryGameTests.RegisteredCargo> cargo;
        private final List<UUID> expected;
        private final List<String> fingerprints;
        private final List<ItemStack> itemSnapshots;
        private final CargoReconnectWitnessHistory history = new CargoReconnectWitnessHistory();
        private final Map<UUID, String> previousSamples = new java.util.HashMap<>();
        private final Map<UUID, Vec3> lastPositions = new java.util.HashMap<>();
        private final List<String> soundEvidence = new ArrayList<>();
        private Consumer<EntityLeaveLevelEvent> leave;
        private Consumer<PlayLevelSoundEvent.AtPosition> sound;
        private Consumer<net.neoforged.neoforge.event.tick.ServerTickEvent.Post> post;
        private String phase = "BEFORE_DETACH";
        private long launchTick = -1, detachTick = -1, offlineTick = -1, offlineEndTick = -1,
                reattachTick = -1, endTick = -1;
        private double initialFrameY, maxFrameY;
        private boolean sawAscent, sawDescent, sawNaturalRest, launched, closed, terminalScheduled;
        private final boolean requireLandingBeforeReconnect;
        private Map<String, Float> launchDamage;
        private Vec3 launchBodyPoint;
        private List<Vec3> launchCargoPoints = List.of();
        private int stableLandingSamples;
        private boolean residentBaselineReady;
        private boolean sceneLeaseReleased;
        private String initialBaseline;
        private int effectiveSubsteps = -1;

        CargoJournal(GameTestHelper helper, MovingCabinAuthorityFixture.Ready fixture,
                com.juicyslew.moonstation14.ms14.player_body_control.lifecycle.character.PlayerCharacterHarnessEntity body,
                List<SableOwnedBodyCarryGameTests.RegisteredCargo> cargo, List<UUID> expected,
                List<String> fingerprints, List<ItemStack> itemSnapshots, boolean requireLandingBeforeReconnect) {
            this.helper = helper; this.fixture = fixture; this.body = body; this.cargo = cargo;
            this.expected = expected; this.fingerprints = fingerprints;
            this.itemSnapshots = itemSnapshots;
            this.requireLandingBeforeReconnect = requireLandingBeforeReconnect;
        }
        void install() {
            leave = event -> {
                try {
                    if (!active() || event.getLevel() != fixture.level()
                            || !(event.getEntity() instanceof com.juicyslew.moonstation14.entities.LooseItemEntity loose)
                            || cargo.stream().noneMatch(root -> root.entity().getUUID().equals(loose.getUUID()))) return;
                    var root = cargo.stream().filter(value -> value.entity().getUUID().equals(loose.getUUID())).findFirst().orElseThrow();
                    var removalReason = loose.getRemovalReason();
                    history.departure(witness(root, loose, resolved(fixture.level(), loose.getUUID()),
                            "entity-left-level removalReason="
                                    + (removalReason == null ? "unknown(null)" : removalReason.toString())));
                } catch (RuntimeException | AssertionError failure) { deferFailure("EntityLeaveLevelEvent observer", failure); }
            };
            sound = event -> {
                try {
                    if (!active() || event.getLevel() != fixture.level()
                            || event.getSound().value() != SoundEvents.GLASS_BREAK) return;
                    for (var root : cargo) {
                        var entity = resolved(fixture.level(), root.entity().getUUID());
                        Vec3 near = entity == null ? lastPositions.get(root.entity().getUUID()) : entity.position();
                        if (near != null && event.getPosition().distanceToSqr(near) < 16 && soundEvidence.size() < 32)
                            soundEvidence.add("tick=" + helper.getLevel().getServer().getTickCount() + " phase=" + phase
                                    + " windows=" + detachTick + "/" + offlineTick + "/" + reattachTick
                                    + " uuid=" + root.entity().getUUID() + " pos=" + event.getPosition());
                    }
                } catch (RuntimeException | AssertionError failure) { deferFailure("PlayLevelSoundEvent observer", failure); }
            };
            post = event -> {
                try {
                    if (!active() || event.getServer() != fixture.level().getServer()) return;
                    tick();
                }
                catch (RuntimeException | AssertionError failure) { deferFailure("continuous cargo tick observer", failure); }
            };
            NeoForge.EVENT_BUS.addListener(leave);
            NeoForge.EVENT_BUS.addListener(sound);
            NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, false, post);
            helper.testInfo.addListener(new GameTestListener() {
                private void clean() { close(); }
                @Override public void testStructureLoaded(GameTestInfo ignored) { }
                @Override public void testPassed(GameTestInfo ignored, GameTestRunner runner) { clean(); }
                @Override public void testFailed(GameTestInfo ignored, GameTestRunner runner) { clean(); }
                @Override public void testAddedForRerun(GameTestInfo oldTest, GameTestInfo newTest, GameTestRunner runner) { clean(); }
            });
        }
        boolean initialAboard(UUID frame) {
            var actorPoint = com.juicyslew.moonstation14.ms14.hands.live.LoadedFrameRayQuery
                    .currentActorPoint(fixture.level(), body);
            if (actorPoint == null || !frame.equals(actorPoint.frameId()) || !insideCabin(fixture, actorPoint.storagePoint()))
                return false;
            for (int i = 0; i < expected.size(); i++) {
                var entity = resolved(fixture.level(), expected.get(i));
                if (entity == null || !entity.settled()) return false;
                var point = com.juicyslew.moonstation14.ms14.hands.live.LoadedFrameRayQuery
                        .currentActorPoint(fixture.level(), entity);
                var tracking = ((dev.ryanhcode.sable.mixinterface.entity.entity_sublevel_collision.EntityMovementExtension)
                        (Object) entity).sable$getTrackingSubLevel();
                if (point == null || !frame.equals(point.frameId()) || tracking != fixture.plot().subLevel()
                        || !insideCabin(fixture, point.storagePoint())) return false;
            }
            return true;
        }
        void beginFlight() {
            initialFrameY = fixture.plot().subLevel().logicalPose().position().y;
            maxFrameY = initialFrameY;
            initialBaseline = snapshot(helper, fixture, body, cargo);
            if (requireLandingBeforeReconnect) {
                var damage = body.getExistingDataOrNull(com.juicyslew.moonstation14.component.ModDataAttachments.DAMAGE.get());
                launchDamage = damage == null ? Map.of() : Map.copyOf(damage.getMap());
                launchBodyPoint = com.juicyslew.moonstation14.ms14.hands.live.LoadedFrameRayQuery
                        .currentActorPoint(fixture.level(), body).storagePoint();
                launchCargoPoints = expected.stream().map(id -> {
                    var entity = resolved(fixture.level(), id);
                    return com.juicyslew.moonstation14.ms14.hands.live.LoadedFrameRayQuery
                            .currentActorPoint(fixture.level(), entity).storagePoint();
                }).toList();
                residentBaselineReady = launchDamage != null && launchBodyPoint != null
                        && launchCargoPoints.size() == expected.size()
                        && launchCargoPoints.stream().allMatch(java.util.Objects::nonNull)
                        && initialBaseline != null;
                if (!residentBaselineReady) throw new IllegalStateException("resident launch baseline incomplete: damage="
                        + launchDamage + ", bodyPoint=" + launchBodyPoint + ", cargoPoints=" + launchCargoPoints
                        + ", payloadBaseline=" + (initialBaseline != null));
            }
            launchTick = helper.getLevel().getServer().getTickCount();
            launched = true;
            phase = "FLIGHT_BEFORE_DETACH";
        }
        void phase(String next) {
            phase = next;
            long tick = helper.getLevel().getServer().getTickCount();
            if (next.equals("DETACHING")) detachTick = tick;
            if (next.equals("OFFLINE")) offlineTick = tick;
            if (next.equals("RECONNECTING")) offlineEndTick = tick;
            if (next.equals("REATTACHED")) reattachTick = tick;
        }
        private void tick() {
            var pose = fixture.plot().subLevel().logicalPose();
            var handle = RigidBodyHandle.of(fixture.plot().subLevel());
            var velocity = handle.getLinearVelocity(new Vector3d());
            var angular = handle.getAngularVelocity(new Vector3d());
            maxFrameY = Math.max(maxFrameY, pose.position().y);
            if (launched) {
                sawAscent |= maxFrameY - initialFrameY >= 3;
                sawDescent |= sawAscent && velocity.y < -.1;
                sawNaturalRest |= sawDescent && Math.abs(pose.position().y - initialFrameY) <= .10
                        && velocity.lengthSquared() <= .0025 && angular.lengthSquared() <= .0001;
            }
            if (requireLandingBeforeReconnect && phase.equals("OFFLINE")) {
                boolean stable = sawAscent && sawDescent && sawNaturalRest && confirmedTerrainContact()
                        && velocity.lengthSquared() <= .0025 && angular.lengthSquared() <= .0001;
                stableLandingSamples = stable ? stableLandingSamples + 1 : 0;
            }
            String row = phase + " " + snapshot(helper, fixture, body, cargo) + " poseY=" + pose.position().y
                    + " velocity=" + velocity + " angular=" + angular + " orientation=" + pose.orientation()
                    + " residency=" + (requireLandingBeforeReconnect ? residencyEvidence() : "not-required")
                    + " windows=" + detachTick + "/" + offlineTick + "/" + reattachTick;
            history.sample(row);
            checkCurrentCargo();
            if (requireLandingBeforeReconnect && residentBaselineReady) checkResidentSnapshot();
            if (!body.isAlive() || body.isRemoved() || helper.getLevel().getEntity(body.getUUID()) != body)
                helperFailure("body ceased to exist/alive during " + phase + " tick="
                        + helper.getLevel().getServer().getTickCount() + " pos=" + body.position());
        }

        private void checkResidentSnapshot() {
            if (!residentBaselineReady) return;
            var damage = body.getExistingDataOrNull(com.juicyslew.moonstation14.component.ModDataAttachments.DAMAGE.get());
            Map<String, Float> currentDamage = damage == null ? Map.of() : Map.copyOf(damage.getMap());
            var bodyPoint = com.juicyslew.moonstation14.ms14.hands.live.LoadedFrameRayQuery
                    .currentActorPoint(fixture.level(), body);
            if (!launchDamage.equals(currentDamage) || bodyPoint == null
                    || !fixture.plot().engineId().equals(bodyPoint.frameId())
                    || bodyPoint.storagePoint().distanceTo(launchBodyPoint) > .12
                    || !leaseStateValid(sceneLeaseReleased, fixture.sceneLeaseClosed())
                    || !hasProductionResidencyTicket() || !residentWorldChunksTicking()) {
                helperFailure("resident body/frame ticket/world-chunk/local-point or typed damage changed during "
                        + phase + ": " + residencyEvidence());
                return;
            }
            for (int i = 0; i < expected.size(); i++) {
                var entity = resolved(fixture.level(), expected.get(i));
                var point = entity == null ? null : com.juicyslew.moonstation14.ms14.hands.live.LoadedFrameRayQuery
                        .currentActorPoint(fixture.level(), entity);
                if (point == null || !fixture.plot().engineId().equals(point.frameId())
                        || point.storagePoint().distanceTo(launchCargoPoints.get(i)) > .12
                        || ((dev.ryanhcode.sable.mixinterface.entity.entity_sublevel_collision.EntityMovementExtension)
                                (Object) entity).sable$getTrackingSubLevel() != fixture.plot().subLevel()) {
                    helperFailure("resident cargo frame/local-point changed during " + phase + " uuid=" + expected.get(i));
                    return;
                }
            }
        }

        void markSceneLeaseReleased() {
            if (!fixture.sceneLeaseClosed())
                throw new IllegalStateException("cannot mark scene lease released before close succeeds");
            sceneLeaseReleased = true;
        }

        private boolean confirmedTerrainContact() {
            var origin = fixture.cabin().origin();
            var pose = fixture.plot().subLevel().logicalPose();
            var bottom = pose.transformPosition(new Vector3d(origin.getX() + 2.5, origin.getY(), origin.getZ() + 2.5));
            var terrain = BlockPos.containing(bottom.x, bottom.y - .01, bottom.z);
            return fixture.level().getBlockState(terrain).is(net.minecraft.world.level.block.Blocks.STONE)
                    && Math.abs(bottom.y - (terrain.getY() + 1)) <= .15;
        }

        boolean hasProductionResidencyTicket() {
            var container = dev.ryanhcode.sable.api.sublevel.SubLevelContainer.getContainer(fixture.level());
            return container instanceof dev.ryanhcode.sable.api.sublevel.ServerSubLevelContainer serverContainer
                    && serverContainer.collectForceLoadedSubLevels().stream()
                            .anyMatch(sub -> sub == fixture.plot().subLevel());
        }

        private boolean residentWorldChunksTicking() {
            if (!fixture.level().isPositionEntityTicking(body.blockPosition())) return false;
            for (UUID id : expected) {
                var entity = resolved(fixture.level(), id);
                if (entity == null || !fixture.level().isPositionEntityTicking(entity.blockPosition())) return false;
            }
            return true;
        }

        String residencyEvidence() {
            var container = dev.ryanhcode.sable.api.sublevel.SubLevelContainer.getContainer(fixture.level());
            var forceLoaded = container instanceof dev.ryanhcode.sable.api.sublevel.ServerSubLevelContainer serverContainer
                    ? serverContainer.collectForceLoadedSubLevels().stream().map(sub -> sub.getUniqueId().toString()).toList()
                    : List.of("no-server-container");
            return "releaseBoundaryObserved=" + sceneLeaseReleased + ", sceneLeaseClosed=" + fixture.sceneLeaseClosed()
                    + ", expectedFrame=" + fixture.plot().engineId()
                    + ", actualFrame=" + (container == null ? null : container.getSubLevel(fixture.plot().engineId()))
                    + ", forceLoaded=" + forceLoaded + ", worldChunksTicking=" + residentWorldChunksTicking();
        }

        boolean stableLanding() { return stableLandingSamples >= 10; }
        String flightEvidence() { return "stableSamples=" + stableLandingSamples + ", ascent=" + sawAscent
                + ", descent=" + sawDescent + ", rest=" + sawNaturalRest + ", terrain=" + confirmedTerrainContact(); }

        private void checkCurrentCargo() {
            for (int i = 0; i < cargo.size(); i++) {
                var root = cargo.get(i);
                var entity = resolved(fixture.level(), expected.get(i));
                var ledger = com.juicyslew.moonstation14.ms14.hands.transfer.LevelItemLocationLedgers
                        .forLevel(fixture.level()).snapshot().locationOf(root.token());
                boolean valid = entity != null && !entity.isRemoved()
                        && ItemStack.matches(itemSnapshots.get(i), entity.getItem())
                        && fingerprints.get(i).equals(fingerprint(entity.getItem()))
                        && ledger.filter(new com.juicyslew.moonstation14.ms14.hands.transfer.ItemLocation.WorldEntity(
                                root.world(), expected.get(i))::equals).isPresent();
                String current = entity == null ? "missing" : sample(root, entity);
                if (!valid) history.cargoFailure(witness(root, root.entity(), entity,
                        "identity/content/ledger loss; " + current));
                previousSamples.put(expected.get(i), current);
                if (entity != null) lastPositions.put(expected.get(i), entity.position());
            }
        }
        private CargoReconnectWitnessHistory.Witness witness(
                SableOwnedBodyCarryGameTests.RegisteredCargo root,
                com.juicyslew.moonstation14.entities.LooseItemEntity departed,
                com.juicyslew.moonstation14.entities.LooseItemEntity current, String reason) {
            UUID id = root.entity().getUUID();
            String prior = previousSamples.get(id);
            ItemStack observedStack = current != null ? current.getItem()
                    : departed != null ? departed.getItem() : ItemStack.EMPTY;
            String observed = observedStack.isEmpty() && prior != null
                    ? "entity-cleared; prior=" + prior : fingerprint(observedStack);
            String location = com.juicyslew.moonstation14.ms14.hands.transfer.LevelItemLocationLedgers
                    .forLevel(fixture.level()).snapshot().locationOf(root.token()).map(Object::toString).orElse("missing");
            String support = current == null ? "unavailable; prior=" + prior : support(current);
            String position = current != null ? current.position().toString()
                    : departed != null ? departed.position().toString() : String.valueOf(lastPositions.get(id));
            int index = expected.indexOf(id);
            return new CargoReconnectWitnessHistory.Witness(helper.getLevel().getServer().getTickCount(), phase,
                    id, root.entity().getUUID(), current == null ? null : current.getUUID(),
                    reference(root.entity()), reference(current), fingerprints.get(index), reason + "; " + observed,
                    location, position, support, prior);
        }
        private String sample(SableOwnedBodyCarryGameTests.RegisteredCargo root,
                com.juicyslew.moonstation14.entities.LooseItemEntity entity) {
            return entity.getUUID() + ":" + fingerprint(entity.getItem()) + ":" + entity.position() + ":" + support(entity);
        }
        private String support(com.juicyslew.moonstation14.entities.LooseItemEntity entity) {
            var point = com.juicyslew.moonstation14.ms14.hands.live.LoadedFrameRayQuery
                    .currentActorPoint(fixture.level(), entity);
            var tracking = ((dev.ryanhcode.sable.mixinterface.entity.entity_sublevel_collision.EntityMovementExtension)
                    (Object) entity).sable$getTrackingSubLevel();
            return "point=" + point + ",tracking=" + (tracking == null ? null : tracking.getUniqueId())
                    + ",supported=" + (tracking == fixture.plot().subLevel() && entity.settled());
        }
        private static String reference(Object entity) {
            return entity == null ? null : entity.getClass().getSimpleName() + "@" + System.identityHashCode(entity);
        }
        private boolean active() {
            if (closed) return false;
            if (helper.testInfo.isDone()) { close(); return false; }
            return true;
        }
        private void deferFailure(String source, Throwable failure) {
            close();
            try {
                BoundedGameTestWait.safeDelay(helper, 1, () -> {
                    if (!helper.testInfo.isDone()) fail(source + ": " + failure.getClass().getSimpleName()
                            + ": " + failure.getMessage());
                });
            } catch (RuntimeException | AssertionError ignored) { /* already-terminal fixture cleanup */ }
        }
        void helperFailure(String reason) {
            if (cargo.isEmpty()) return;
            var root = cargo.get(0);
            history.cargoFailure(witness(root, root.entity(), resolved(fixture.level(), root.entity().getUUID()), reason));
        }
        String failureText() {
            var first = history.firstFailure();
            return first == null ? null : first.toString();
        }
        private String terminalSummary() {
            endTick = helper.getLevel().getServer().getTickCount();
            var handle = RigidBodyHandle.of(fixture.plot().subLevel());
            var velocity = handle.getLinearVelocity(new Vector3d());
            var angular = handle.getAngularVelocity(new Vector3d());
            var baselineCargo = cargo.stream().map(root -> root.entity().getUUID() + ":" + fingerprints.get(
                    expected.indexOf(root.entity().getUUID())) + ":" + new com.juicyslew.moonstation14.ms14.hands.transfer.ItemLocation.WorldEntity(
                    root.world(), root.entity().getUUID())).toList();
            var finalCargo = expected.stream().map(id -> {
                var current = resolved(fixture.level(), id);
                if (current == null) return id + ":missing";
                var ledger = com.juicyslew.moonstation14.ms14.hands.transfer.LevelItemLocationLedgers
                        .forLevel(fixture.level()).snapshot().locationOf(
                                cargo.get(expected.indexOf(id)).token()).map(Object::toString).orElse("missing");
                return id + ":" + fingerprint(current.getItem()) + ":" + ledger;
            }).toList();
            return "substeps=" + effectiveSubsteps + " ticks={launch=" + launchTick + ",detach=" + detachTick
                    + ",offline=" + offlineTick + ",offlineEnd=" + offlineEndTick
                    + ",reattach=" + reattachTick + ",end=" + endTick
                    + ",offlineObserved=" + (offlineTick < 0 || offlineEndTick < 0 ? 0 : offlineEndTick - offlineTick)
                    + ",postObserved=" + (endTick < 0 || reattachTick < 0 ? 0 : endTick - reattachTick) + "}"
                    + " ids=" + expected + " baseline=" + baselineCargo + " final=" + finalCargo
                    + " flight={ascent=" + sawAscent + ",descent=" + sawDescent + ",rest=" + sawNaturalRest
                    + ",maxY=" + maxFrameY + ",initialY=" + initialFrameY + ",finalY="
                    + fixture.plot().subLevel().logicalPose().position().y + ",linear=" + velocity
                    + ",angular=" + angular + "} witnesses={departures=" + history.departures().size()
                    + ",cargoFailures=" + history.cargoFailures().size() + ",sounds=" + soundEvidence.size()
                    + ",recent=" + history.recent().size() + "}";
        }
        void fail(String reason) {
            if (terminalScheduled) return;
            terminalScheduled = true;
            close();
            com.juicyslew.moonstation14.MoonStation14.LOGGER.error(
                    "cargo reconnect diagnostic failure={} summary={} initialBaseline={} first={} departures={} cargoFailures={} sounds={} rows={}",
                    reason, terminalSummary(), initialBaseline, history.firstFailure(), history.departures(),
                    history.cargoFailures(), soundEvidence, history.recent());
            String brief = "cargo reconnect failed: " + reason + " [first=" + history.firstFailure()
                    + "; windows=" + detachTick + "/" + offlineTick + "/" + reattachTick + "]";
            var capturedFailure = new net.minecraft.gametest.framework.GameTestAssertException(
                    brief.substring(0, Math.min(390, brief.length())));
            BoundedGameTestWait.safeDelay(helper, 1, () -> {
                if (!helper.testInfo.isDone()) helper.testInfo.fail(capturedFailure);
            });
        }
        void succeed() {
            if (terminalScheduled) return;
            terminalScheduled = true;
            close();
            com.juicyslew.moonstation14.MoonStation14.LOGGER.info(
                    "cargo reconnect diagnostic success summary={} initialBaseline={} first={} departures={} cargoFailures={} sounds={} rows={}",
                    terminalSummary(), initialBaseline, history.firstFailure(), history.departures(),
                    history.cargoFailures(), soundEvidence, history.recent());
            BoundedGameTestWait.succeedAfterObservation(helper);
        }
        void close() {
            if (closed) return;
            closed = true;
            NeoForge.EVENT_BUS.unregister(leave);
            NeoForge.EVENT_BUS.unregister(sound);
            NeoForge.EVENT_BUS.unregister(post);
        }
    }

    static boolean leaseStateValid(boolean releaseBoundaryObserved, boolean leaseClosed) {
        return !releaseBoundaryObserved || leaseClosed;
    }
}
