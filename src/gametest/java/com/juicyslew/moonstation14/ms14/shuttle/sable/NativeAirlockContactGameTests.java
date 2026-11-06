package com.juicyslew.moonstation14.ms14.shuttle.sable;

import com.juicyslew.moonstation14.gametest.support.GameTestTags;
import com.juicyslew.moonstation14.gametest.support.BoundedGameTestWait;
import com.juicyslew.moonstation14.gametest.support.PowerFixtureReadiness;
import com.juicyslew.moonstation14.ms14.doors.AirlockBlock;
import com.juicyslew.moonstation14.ms14.doors.AirlockBlockEntity;
import com.juicyslew.moonstation14.ms14.doors.AirlockBumpSystem;
import com.juicyslew.moonstation14.ms14.doors.AirlockState;
import com.juicyslew.moonstation14.ms14.doors.NativeAirlockContactAccess;
import com.juicyslew.moonstation14.ms14.doors.NativeAirlockContact;
import com.juicyslew.moonstation14.ms14.character.CharacterIdentitySystem;
import com.juicyslew.moonstation14.ms14.character.components.DoorBumpOpenerComponent;
import com.juicyslew.moonstation14.ms14.hands.live.LoadedFrameRayQuery;
import com.juicyslew.moonstation14.ms14.power.runtime.PowerRuntime;
import dev.ryanhcode.sable.mixinterface.entity.entity_sublevel_collision.EntityMovementExtension;
import dev.ryanhcode.sable.sublevel.entity_collision.SubLevelEntityCollision;
import dev.ryanhcode.sable.api.physics.handle.RigidBodyHandle;
import dev.ryanhcode.sable.api.math.LevelReusedVectors;
import dev.ryanhcode.sable.api.math.OrientedBoundingBox3d;
import dev.ryanhcode.sable.mixinterface.entity.entity_sublevel_collision.LevelExtension;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.GameTestInfo;
import net.minecraft.gametest.framework.GameTestListener;
import net.minecraft.gametest.framework.GameTestRunner;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import org.joml.Vector3d;
import org.joml.Quaterniond;

/** Verifies actual Sable-selected airlock contacts are attached to that native move result. */
@GameTestTags({"shuttle-airlock-bump", "airlock_bump"})
@PrefixGameTestTemplate(false)
public final class NativeAirlockContactGameTests {
    private NativeAirlockContactGameTests() { }

    @GameTest(template = "atmos_large_empty", templateNamespace = "moonstation14", timeoutTicks = 800,
            batch = "shuttle_native_airlock_contact")
    public static void nativeMoveCapturesSelectedAirlockStorageContact(GameTestHelper helper) {
        MovingCabinAuthorityFixture.start(helper, new BlockPos(760, 300, 680), "native-airlock-contact",
                 3.5, 1, 3.5, (fixture, human) -> {
                    var level = fixture.level();
                    var movement = (EntityMovementExtension) (Object) human;
                    var doorStorage = fixture.cabin().at(com.juicyslew.moonstation14.ms14.shuttle.fixture.ShuttleCabinFixture.AIRLOCK);
                    if (!(fixture.level().getBlockState(doorStorage).getBlock() instanceof AirlockBlock))
                        throw new GameTestAssertException("authored lower airlock missing in storage");
                    if (!(level.getBlockEntity(doorStorage) instanceof AirlockBlockEntity door))
                        throw new GameTestAssertException("authored lower airlock controller missing");
                    PowerFixtureReadiness.await(helper, level, 140, () -> fullyPaid20(level, door),
                            () -> "native contact airlock lacks paid 20 W: "
                                    + PowerRuntime.airlockPowerDiagnostic(level, door), () -> {

                    human.move(MoverType.SELF, new Vec3(0.2, 0, 0));
                    var emptyInfo = movement.sable$getCollisionInfo();
                    if (emptyInfo == null)
                        throw new GameTestAssertException("door-free native move did not produce collision information");
                    NativeAirlockContactAccess emptyCapture = (NativeAirlockContactAccess) (Object) emptyInfo;
                    if (emptyCapture == null)
                        throw new GameTestAssertException("Sable collision result lacks attached contact metadata access");
                    if (!emptyCapture.moonstation14$airlockContacts().isEmpty())
                        throw new GameTestAssertException("door-free native move unexpectedly recorded airlock contact");

                    var preMoveBounds = human.getBoundingBox();
                    var previousInfo = movement.sable$getCollisionInfo();
                    var request = new Vec3(0, -0.1, -4);
                    human.move(MoverType.SELF, request);
                    var info = movement.sable$getCollisionInfo();
                    if (info == null || info == previousInfo)
                        throw new GameTestAssertException("native Entity.move did not produce fresh collision-result metadata");
                    NativeAirlockContactAccess captured = (NativeAirlockContactAccess) (Object) info;
                    var matches = captured.moonstation14$airlockContacts().stream()
                            .filter(contact -> contact.frame().getUniqueId().equals(fixture.plot().engineId())
                                    && (contact.storagePos().equals(doorStorage)
                                    || contact.storagePos().equals(doorStorage.above()))
                                    && contact.state().getBlock() instanceof AirlockBlock)
                            .toList();
                    var firstInfo = info.firstCollisions == null ? null : info.firstCollisions.get(fixture.plot().subLevel());
                    if (firstInfo == null || firstInfo.horizontal())
                        throw new GameTestAssertException("regression fixture must retain its floor-first SDK contact");
                    if (matches.isEmpty())
                        throw new GameTestAssertException("native contact did not retain either exact airlock half and frame; contacts="
                                + captured.moonstation14$airlockContacts().stream().map(contact ->
                                contact.storagePos() + "@" + contact.frame().getUniqueId()).toList());
                    var contact = matches.stream().filter(candidate -> candidate.selectedPos().equals(candidate.storagePos()))
                            .findFirst().orElseThrow(() -> new GameTestAssertException(
                                    "no direct selected door contact among matching native contacts: " + matches));
                    double normalLength = Math.sqrt(contact.normalX() * contact.normalX()
                            + contact.normalY() * contact.normalY() + contact.normalZ() * contact.normalZ());
                    if (Math.abs(normalLength - 1.0) > 1.0E-6 || !contact.horizontal() || contact.vertical())
                        throw new GameTestAssertException("captured normal/axis flags differ from selected native side contact: " + contact);
                    if (!AirlockBumpSystem.afterNativeMove(human, preMoveBounds, request, info))
                        throw new GameTestAssertException("fresh native selected door contact did not activate through normal gates; door="
                                + door.state() + " power=" + PowerRuntime.airlockPowerDiagnostic(level, door));
                    if (door.state().phase() != AirlockState.DoorPhase.OPENING)
                        throw new GameTestAssertException("resolved paid selected airlock did not enter opening phase: " + door.state());
                    helper.succeed();
                    });
        });
    }

    @GameTestTags({"shuttle-airlock-bump", "airlock_bump", "movement"})
    @GameTest(template = "atmos_large_empty", templateNamespace = "moonstation14", timeoutTicks = 800,
            batch = "shuttle_native_airlock_bump")
    public static void poweredNativeFrameAirlockOpensFromRealLivingTravelContact(GameTestHelper helper) {
        MovingCabinAuthorityFixture.start(helper, new BlockPos(800, 300, 720), "native-airlock-open",
                3.5, 1, 3.5, (fixture, unusedOwnedHuman) -> {
                    var level = fixture.level();
                    BlockPos root = fixture.cabin().at(com.juicyslew.moonstation14.ms14.shuttle.fixture.ShuttleCabinFixture.AIRLOCK);
                    if (!(level.getBlockEntity(root) instanceof AirlockBlockEntity door))
                        throw new GameTestAssertException("authored frame airlock controller missing");
                    ServerSubLevel frame = fixture.plot().subLevel();
                    if (frame.isRemoved() || !frame.getUniqueId().equals(fixture.plot().engineId())
                            || LoadedFrameRayQuery.knownServerBlock(level, frame, root) == null
                            || LoadedFrameRayQuery.knownServerBlock(level, frame, root.above()) == null)
                        throw new GameTestAssertException("both airlock halves must be current known FULL storage");
                    requireClosed(door);
                    PowerFixtureReadiness.await(helper, level, 140,
                            () -> {
                                var power = PowerRuntime.airlockPower(level, door);
                                return power.known() && power.deliveredWatts()
                                        == door.prototype().apcPowerReceiver().requestedWatts()
                                        && power.deliveredWatts() == 20;
                            }, () -> "native-frame airlock never received its resolved paid 20 W grant: "
                                    + PowerRuntime.airlockPowerDiagnostic(level, door),
                            () -> startNativeTravelContact(helper, fixture, door, root));
                });
    }

    @GameTestTags({"shuttle-cabin-inside-bump", "shuttle-airlock-bump", "airlock_bump", "movement", "shuttle-owner-regression"})
    @GameTest(template = "atmos_large_empty", templateNamespace = "moonstation14", timeoutTicks = 900,
            batch = "shuttle_native_airlock_bump")
    public static void canonicalCabinInsideHatchBumpsThroughOwnedGroundedStep(GameTestHelper helper) {
        MovingCabinAuthorityFixture.start(helper, new BlockPos(1160, 300, 1080), "cabin-inside-bump",
                3.5, 1, 1.4, 1, (fixture, body) -> {
                    var level = fixture.level();
                    BlockPos root = fixture.cabin().at(com.juicyslew.moonstation14.ms14.shuttle.fixture.ShuttleCabinFixture.AIRLOCK);
                    if (!(level.getBlockEntity(root) instanceof AirlockBlockEntity door))
                        throw new GameTestAssertException("canonical inside fixture missing hatch root controller");
                    ServerSubLevel frame = fixture.plot().subLevel();
                    if (door.state().phase() != AirlockState.DoorPhase.CLOSED || door.state().boltsDown())
                        throw new GameTestAssertException("inside hatch requires a closed, unbolted airlock: " + door.state());
                    if (CharacterIdentitySystem.resolveForActor(body)
                            .filter(data -> data.component(DoorBumpOpenerComponent.class).isPresent()).isEmpty())
                        throw new GameTestAssertException("owned registered Human lacks resolved DoorBumpOpener");
                     PowerFixtureReadiness.await(helper, level, 160, () -> {
                                 var power = PowerRuntime.airlockPower(level, door);
                                 return power.known() && power.deliveredWatts() == 20 && door.prototype() != null
                                         && power.deliveredWatts() == door.prototype().apcPowerReceiver().requestedWatts();
                             }, () -> "inside hatch lacks fresh fully-paid 20 W receiver delivery: "
                                     + PowerRuntime.airlockPowerDiagnostic(level, door), () -> {
                                BlockPos insideCell = root.offset(0, 0, 1);
                                var insideState = LoadedFrameRayQuery.knownServerBlock(level, frame, insideCell);
                                if (insideState == null || !insideState.isAir())
                                    throw new GameTestAssertException("inside start must use the authored clear room cell behind the canonical hatch: "
                                            + insideCell + " state=" + insideState);
                                Vector3d expectedFeet = new Vector3d(root.getX() + .5, root.getY(), root.getZ() + 1.4);
                                frame.logicalPose().transformPosition(expectedFeet);
                                if (body.position().distanceToSqr(new Vec3(expectedFeet.x, expectedFeet.y, expectedFeet.z)) > .5)
                                    throw new GameTestAssertException("owned Human did not settle at the one-time clear interior start: "
                                            + body.position() + " expected=" + expectedFeet);
                                requireClosed(door);
                                  // trackViewToward makes local +Z (forward) point toward the door;
                                  // local -Z is backward and would walk away from this interior hatch.
                                  fixture.owner().setWish(0, 1000);
                                 Vector3d doorPoint = frame.logicalPose().transformPosition(new Vector3d(
                                         root.getX() + .5, root.getY() + .5, root.getZ()));
                                 fixture.owner().trackViewToward(body, () -> new Vec3(doorPoint.x, doorPoint.y, doorPoint.z));
                                 var previousNativeInfo = ((EntityMovementExtension) (Object) body).sable$getCollisionInfo();
                                  observeOwnedInsideInput(helper, fixture, body, door, root, previousNativeInfo);
                            });
                });
    }

    private static void observeOwnedInsideInput(GameTestHelper helper, MovingCabinAuthorityFixture.Ready fixture,
            net.minecraft.world.entity.Mob body, AirlockBlockEntity door, BlockPos root, Object previousNativeInfo) {
        var frame = fixture.plot().subLevel();
        final boolean[] exactFreshContact = {false};
        BoundedGameTestWait.observeServerTicks(helper, 30, event -> {
            Object current = ((EntityMovementExtension) (Object) body).sable$getCollisionInfo();
            boolean freshInfo = current != null && current != previousNativeInfo;
            if (freshInfo && currentContacts(body).stream().anyMatch(contact ->
                    contact.frame().getUniqueId().equals(fixture.plot().engineId()) && contact.rootPos().equals(root)))
                exactFreshContact[0] = true;
            if (door.state().phase() == AirlockState.DoorPhase.OPENING || door.state().phase() == AirlockState.DoorPhase.OPEN) {
                fixture.owner().setWish(0, 0);
                fixture.owner().neutralIntent(body);
                if (!freshInfo || !exactFreshContact[0])
                    throw new GameTestAssertException("inside owned bump opened without latched exact fresh frame/root nomination: "
                            + exteriorDiagnostic(body, frame, root) + " inputSequence=" + fixture.owner().intentSequence().get()
                            + " wish=(0,1000) yaw=" + body.getYRot() + " nativeInfo=" + current + " priorNative=" + previousNativeInfo);
                BoundedGameTestWait.succeedAfterObservation(helper);
            }
        }, () -> {
            if (door.state().phase() == AirlockState.DoorPhase.OPENING || door.state().phase() == AirlockState.DoorPhase.OPEN) return;
            Object current = ((EntityMovementExtension) (Object) body).sable$getCollisionInfo();
            throw new GameTestAssertException("canonical inside Human registered input did not open paid hatch; "
                    + exteriorDiagnostic(body, frame, root) + " controller=" + door.state() + " inputSequence="
                    + fixture.owner().intentSequence().get() + " wish=(0,1000) yaw=" + body.getYRot()
                    + " nativeInfo=" + current + " priorNative=" + previousNativeInfo);
        });
    }

    @GameTestTags({"shuttle-cabin-exterior-bump", "shuttle-airlock-bump", "airlock_bump"})
    @GameTest(template = "atmos_large_empty", templateNamespace = "moonstation14", timeoutTicks = 900,
            batch = "shuttle_native_airlock_bump")
    public static void canonicalCabinExteriorHatchBumpsFromGroundHeightWithoutJump(GameTestHelper helper) {
        MovingCabinAuthorityFixture.start(helper, new BlockPos(1120, 300, 1040), "cabin-exterior-bump",
                1.5, 1, 5.5, 1, (fixture, unusedOwnedHuman) -> {
                    var level = fixture.level();
                    BlockPos root = fixture.cabin().at(com.juicyslew.moonstation14.ms14.shuttle.fixture.ShuttleCabinFixture.AIRLOCK);
                    if (!(level.getBlockEntity(root) instanceof AirlockBlockEntity door))
                        throw new GameTestAssertException("canonical exterior fixture missing hatch root controller");
                    ServerSubLevel frame = fixture.plot().subLevel();
                    if (door.state().phase() != AirlockState.DoorPhase.CLOSED || door.state().boltsDown())
                        throw new GameTestAssertException("exterior hatch prerequisites require closed phase and raised/retracted bolts: "
                                + door.state());
                     PowerFixtureReadiness.await(helper, level, 160, () -> {
                                 var power = PowerRuntime.airlockPower(level, door);
                                 return power.known() && power.deliveredWatts() == 20
                                         && door.prototype() != null
                                         && power.deliveredWatts() == door.prototype().apcPowerReceiver().requestedWatts();
                             }, () -> "canonical exterior hatch lacks fresh fully paid 20 W: "
                                     + PowerRuntime.airlockPowerDiagnostic(level, door), () -> {
                                 var sideActor = spawnExteriorHumanHost(level, frame, fixture.scene(),
                                         new Vector3d(root.getX() - .5, root.getY() - 1, root.getZ() - 1.4),
                                         "exterior-hull-only");
                                 driveHorizontalNativeTravel(helper, sideActor, frame, new Vector3d(0, 0, 1), 4, 0,
                                         sideActor.position(), ((EntityMovementExtension) (Object) sideActor).sable$getCollisionInfo(), () -> {
                                 boolean sideSelectedDoor = currentContacts(sideActor).stream().anyMatch(contact ->
                                        contact.frame().getUniqueId().equals(frame.getUniqueId())
                                                && contact.rootPos().equals(root));
                                if (sideSelectedDoor || door.state().phase() != AirlockState.DoorPhase.CLOSED) {
                                    throw new GameTestAssertException("adjacent exterior hull-only approach opened/nominated hatch; "
                                            + exteriorDiagnostic(sideActor, frame, root));
                                }
                                 sideActor.discard();

                                 var actor = spawnExteriorHumanHost(level, frame, fixture.scene(),
                                        new Vector3d(root.getX() + .5, root.getY() - 1, root.getZ() - 1.4),
                                        "exterior-hatch-contact");
                                var paid = PowerRuntime.airlockPower(level, door);
                                if (!paid.known() || paid.deliveredWatts() != 20
                                        || door.state().phase() != AirlockState.DoorPhase.CLOSED || door.state().boltsDown())
                                     throw new GameTestAssertException("real exterior contact prerequisites changed before travel: power="
                                             + paid + " diagnostic=" + PowerRuntime.airlockPowerDiagnostic(level, door)
                                             + " door=" + door.state());
                                Vector3d hatchBottom = frame.logicalPose().transformPosition(new Vector3d(
                                        root.getX(), root.getY(), root.getZ()));
                                if (!actor.onGround() || Math.abs(actor.getY() - (hatchBottom.y - 1)) > .1)
                                    throw new GameTestAssertException("exterior actor must begin grounded one world block below the hatch lower face: feet="
                                            + actor.position() + " transformedHatchBottom=" + hatchBottom + " storageRoot=" + root);
                                  driveExteriorTowardHatch(helper, actor, frame, root, door, 0, helper.getTick(), actor.position(), () -> {
                                             var contacts = currentContacts(actor);
                                            if (contacts.stream().noneMatch(contact ->
                                                    contact.frame().getUniqueId().equals(frame.getUniqueId())
                                                            && contact.rootPos().equals(root)))
                                                throw new GameTestAssertException("exterior hatch opened without exact selected live frame/root contact: "
                                                        + exteriorDiagnostic(actor, frame, root));
                                              actor.discard();
                                             helper.succeed();
                                         });
                                 });
                            });
                });
    }

    @GameTestTags({"shuttle-airlock-idle-contact", "shuttle-airlock-bump", "airlock_bump"})
    @GameTest(template = "atmos_large_empty", templateNamespace = "moonstation14", timeoutTicks = 900,
            batch = "shuttle_native_airlock_bump")
    public static void movingPaidDoorContactsIdleGroundedHuman(GameTestHelper helper) {
        MovingCabinAuthorityFixture.start(helper, new BlockPos(1240, 300, 1080), "idle-door-contact",
                3.5, 1, 3.5, (fixture, unusedOwnedHuman) -> {
                    var level = fixture.level();
                    BlockPos root = fixture.cabin().at(com.juicyslew.moonstation14.ms14.shuttle.fixture.ShuttleCabinFixture.AIRLOCK);
                    if (!(level.getBlockEntity(root) instanceof AirlockBlockEntity door))
                        throw new GameTestAssertException("idle-contact airlock root missing");
                    if (door.state().phase() != AirlockState.DoorPhase.CLOSED || door.state().boltsDown())
                        throw new GameTestAssertException("idle-contact requires closed, raised-bolt door: " + door.state());
                    ServerSubLevel frame = fixture.plot().subLevel();
                    PowerFixtureReadiness.await(helper, level, 160, () -> fullyPaid20(level, door),
                            () -> "idle-contact door lacks paid 20 W: " + PowerRuntime.airlockPowerDiagnostic(level, door), () -> {
                                 var actor = spawnExteriorHumanHost(level, frame, fixture.scene(),
                                         new Vector3d(root.getX() + .5, root.getY() - 1, root.getZ() - 1.0), "idle-door-contact");
                                var initialPose = new Vector3d(frame.logicalPose().position());
                                var initialCollision = ((EntityMovementExtension) (Object) actor).sable$getCollisionInfo();
                                dev.ryanhcode.sable.api.physics.handle.RigidBodyHandle.of(frame)
                                        .addLinearAndAngularVelocity(new Vector3d(0, 0, -4.0), new Vector3d());
                                helper.runAfterDelay(1, () -> idleDoorContactTick(helper, actor, frame, root, door,
                                        initialPose, initialCollision, helper.getTick(), 0));
                            });
                });
    }

    @GameTestTags({"shuttle-airlock-safety"})
    @GameTest(template = "atmos_large_empty", templateNamespace = "moonstation14", timeoutTicks = 900,
            batch = "shuttle_native_airlock_safety")
    public static void movingDefaultSafeDoorRefusesCloseOnAlreadyPresentHuman(GameTestHelper helper) {
        MovingCabinAuthorityFixture.start(helper, new BlockPos(1600, 300, 1600), "airlock-safety-present",
                3.5, 1, 3.5, (fixture, unusedOwnedHuman) -> {
                    var level = fixture.level();
                    BlockPos root = fixture.cabin().at(com.juicyslew.moonstation14.ms14.shuttle.fixture.ShuttleCabinFixture.AIRLOCK);
                    if (!(level.getBlockEntity(root) instanceof AirlockBlockEntity door))
                        throw new GameTestAssertException("safety fixture airlock root missing");
                    ServerSubLevel frame = fixture.plot().subLevel();
                    if (LoadedFrameRayQuery.knownServerBlock(level, frame, root) == null
                            || LoadedFrameRayQuery.knownServerBlock(level, frame, root.above()) == null)
                        throw new GameTestAssertException("safety fixture requires both known airlock parts");
                    PowerFixtureReadiness.await(helper, level, 160, () -> fullyPaid20(level, door),
                            () -> "safety fixture lacks paid 20 W: " + PowerRuntime.airlockPowerDiagnostic(level, door), () -> {
                        if (door.prototype() == null || !door.state().safetyActive(level.getGameTime()))
                            throw new GameTestAssertException("safety fixture must use resolved active prototype safety");
                        if (!door.activate()) throw new GameTestAssertException("normal activation failed on empty closed door");
                        helper.runAfterDelay(25, () -> {
                            if (door.state().phase() != AirlockState.DoorPhase.OPEN)
                                throw new GameTestAssertException("normally activated door did not reach OPEN: " + door.state());
                            var actor = spawnDeckHumanHost(level, frame, fixture.scene(),
                                    new Vector3d(root.getX() + .5, root.getY(), root.getZ() + .5), "safe-close-present-human");
                            var closedVolume = projectedDoorBounds(level, frame, root, true);
                            if (!actor.getBoundingBox().intersects(closedVolume))
                                throw new GameTestAssertException("host Human setup is not inside current doorway volume: " + actor.getBoundingBox());
                            var initialPose = new Vector3d(frame.logicalPose().position());
                            var initialHealth = actor.getHealth();
                            RigidBodyHandle.of(frame).addLinearAndAngularVelocity(new Vector3d(4, 0, 0), new Vector3d());
                            helper.runAfterDelay(1, () -> {
                                prepareFixtureTravel(actor, "safe-close native frame carry");
                                actor.travel(Vec3.ZERO);
                                if (frame.logicalPose().position().distanceSquared(initialPose) < 1.0E-8)
                                    throw new GameTestAssertException("native frame did not physically translate during safety probe");
                                var currentDoorway = projectedDoorBounds(level, frame, root, true);
                                PowerFixtureReadiness.await(helper, level, 80, () -> fullyPaid20(level, door),
                                        () -> "safety close lost fresh paid 20 W: " + PowerRuntime.airlockPowerDiagnostic(level, door), () -> {
                                    if (!actor.getBoundingBox().intersects(currentDoorway))
                                        throw new GameTestAssertException("carried Human left current doorway volume before close attempt");
                                    if (door.activate()) throw new GameTestAssertException("safe close unexpectedly accepted occupied doorway");
                                    if (door.state().phase() != AirlockState.DoorPhase.OPEN || actor.getHealth() != initialHealth)
                                        throw new GameTestAssertException("occupied safe close changed door/health: " + door.state()
                                                + " health=" + actor.getHealth() + " initial=" + initialHealth);
                                    actor.discard();
                                    helper.succeed();
                                });
                            });
                        });
                    });
                });
    }

    @GameTestTags({"shuttle-airlock-safety"})
    @GameTest(template = "atmos_large_empty", templateNamespace = "moonstation14", timeoutTicks = 900,
            batch = "shuttle_native_airlock_safety")
    public static void movingDefaultSafeDoorReopensForLateEntrantBeforeSolid(GameTestHelper helper) {
        MovingCabinAuthorityFixture.start(helper, new BlockPos(1800, 300, 1600), "airlock-safety-late-entrant",
                3.5, 1, 3.5, (fixture, unusedOwnedHuman) -> {
                    var level = fixture.level();
                    BlockPos root = fixture.cabin().at(com.juicyslew.moonstation14.ms14.shuttle.fixture.ShuttleCabinFixture.AIRLOCK);
                    if (!(level.getBlockEntity(root) instanceof AirlockBlockEntity door))
                        throw new GameTestAssertException("late-entrant safety fixture airlock root missing");
                    ServerSubLevel frame = fixture.plot().subLevel();
                    PowerFixtureReadiness.await(helper, level, 200, () -> fullyPaid20(level, door),
                            () -> "late-entrant door lacks paid 20 W: " + PowerRuntime.airlockPowerDiagnostic(level, door), () -> {
                        if (door.prototype() == null || !door.state().safetyActive(level.getGameTime()))
                            throw new GameTestAssertException("late-entrant fixture requires resolved active default safety");
                        if (!door.activate()) throw new GameTestAssertException("normal activation failed on empty closed door");
                        var actorHolder = new FixtureTravelVillager[1];
                        var initialHealth = new double[1];
                        var initialPose = new Vector3d[1];
                        var armed = new boolean[1];
                        var terminal = new boolean[1];
                        var lastTravelServerTick = new long[] {-1};
                        BoundedGameTestWait.observeServerTicks(helper, 40, event -> {
                            if (terminal[0]) return;
                            if (!armed[0]) {
                                if (door.state().phase() != AirlockState.DoorPhase.OPEN) return;
                                if (!door.activate() || door.state().phase() != AirlockState.DoorPhase.CLOSING)
                                    throw new GameTestAssertException("normal empty OPEN-door activation did not begin close: " + door.state());
                                var actor = spawnDeckHumanHost(level, frame, fixture.scene(),
                                        new Vector3d(root.getX() + .5, root.getY(), root.getZ() + .5), "late-safe-close-human");
                                var potentialClosed = projectedDoorBounds(level, frame, root, true);
                                if (!actor.getBoundingBox().intersects(potentialClosed))
                                    throw new GameTestAssertException("late entrant is outside current projected closed-leaf volume: " + actor.getBoundingBox());
                                actorHolder[0] = actor;
                                initialHealth[0] = actor.getHealth();
                                initialPose[0] = new Vector3d(frame.logicalPose().position());
                                RigidBodyHandle.of(frame).addLinearAndAngularVelocity(new Vector3d(4, 0, 0), new Vector3d());
                                armed[0] = true;
                                BoundedGameTestWait.safeOnEachTick(helper, () -> {
                                    if (!armed[0] || terminal[0] || actor.isRemoved() || helper.testInfo.isDone()) return;
                                    long serverTick = level.getServer().getTickCount();
                                    if (lastTravelServerTick[0] == serverTick) return;
                                    lastTravelServerTick[0] = serverTick;
                                    prepareFixtureTravel(actor, "late entrant native frame carry");
                                    actor.travel(Vec3.ZERO);
                                });
                                return;
                            }
                            var actor = actorHolder[0];
                            var bounds = projectedDoorBounds(level, frame, root, true);
                            boolean occupied = actor != null && actor.getBoundingBox().intersects(bounds);
                            boolean stunned = actor != null && com.juicyslew.moonstation14.ms14.character.CharacterControlSystem.isStunned(actor);
                            boolean knockedDown = actor != null && com.juicyslew.moonstation14.ms14.character.CharacterControlSystem.isKnockedDown(actor);
                            if ((door.state().phase() == AirlockState.DoorPhase.CLOSING
                                    || door.state().phase() == AirlockState.DoorPhase.CLOSING_PASSABLE) && !occupied)
                                throw new GameTestAssertException("late entrant left projected doorway before safety deadline: phase="
                                        + door.state() + " frame=" + frame.logicalPose() + " bounds=" + actor.getBoundingBox());
                            if (door.state().phase() == AirlockState.DoorPhase.CLOSED)
                                throw new GameTestAssertException("late entrant safety allowed a solid closed phase: " + door.state());
                            if (door.state().phase() == AirlockState.DoorPhase.OPEN) {
                                if (!occupied || actor.getHealth() != initialHealth[0] || stunned || knockedDown
                                        || frame.logicalPose().position().distanceSquared(initialPose[0]) < 1.0E-8)
                                    throw new GameTestAssertException("late entrant safety failed: phase=" + door.state()
                                            + " power=" + PowerRuntime.airlockPowerDiagnostic(level, door)
                                            + " frame=" + frame.logicalPose() + " bounds=" + actor.getBoundingBox()
                                            + " projectedClosed=" + bounds + " occupied=" + occupied + " health=" + actor.getHealth()
                                            + "/" + initialHealth[0] + " stunned=" + stunned + " knockedDown=" + knockedDown);
                                actor.discard();
                                terminal[0] = true;
                                BoundedGameTestWait.succeedAfterObservation(helper);
                            }
                        }, () -> {
                            if (terminal[0]) return;
                            var actor = actorHolder[0];
                            throw new GameTestAssertException("late entrant safety deadline timed out: phase=" + door.state()
                                    + " power=" + PowerRuntime.airlockPowerDiagnostic(level, door) + " frame=" + frame.logicalPose()
                                    + " actor=" + (actor == null ? "<not spawned>" : actor.getBoundingBox())
                                    + " health=" + (actor == null ? "<none>" : actor.getHealth())
                                    + " initialHealth=" + initialHealth[0]);
                        });
                    });
                });
    }

    private static void idleDoorContactTick(GameTestHelper helper, FixtureTravelVillager actor, ServerSubLevel frame,
            BlockPos root, AirlockBlockEntity door, Vector3d initialPose, Object previousCollision, long pulseStartTick, int tick) {
        if (actor.isRemoved() || helper.testInfo.isDone()) return;
        if (helper.getTick() - pulseStartTick >= 30) throw new GameTestAssertException("single frame velocity pulse did not contact door; framePose="
                + frame.logicalPose() + " actor=" + actor.position() + " door=" + door.state());
        Vec3 preTravelVelocity = actor.getDeltaMovement();
        if (preTravelVelocity.x != 0 || preTravelVelocity.z != 0)
            throw new GameTestAssertException("idle actor acquired own horizontal velocity before exact door witness at tick "
                    + tick + ": " + preTravelVelocity);
        PowerFixtureReadiness.await(helper, (net.minecraft.server.level.ServerLevel) actor.level(),
                Math.max(1, 30 - (int) (helper.getTick() - pulseStartTick)), () -> fullyPaid20((net.minecraft.server.level.ServerLevel) actor.level(), door),
                () -> "idle contact lost fully paid 20 W: " + PowerRuntime.airlockPowerDiagnostic(
                        (net.minecraft.server.level.ServerLevel) actor.level(), door), () -> {
        prepareFixtureTravel(actor, "idle-world-host gravity-only travel");
        actor.travel(Vec3.ZERO);
        var collision = ((EntityMovementExtension) (Object) actor).sable$getCollisionInfo();
        if (collision == null || collision == previousCollision)
            throw new GameTestAssertException("idle host travel did not produce fresh native collision result at tick " + tick);
        boolean exactContact = currentContacts(actor).stream().anyMatch(contact ->
                contact.frame().getUniqueId().equals(frame.getUniqueId()) && contact.rootPos().equals(root));
        if (exactContact) {
            com.juicyslew.moonstation14.MoonStation14.LOGGER.info(
                    "idle native door witness tick={} frameDelta={} preVelocity={} paidWatts={} contact={} currentInfo={} phase={}",
                    tick, new Vector3d(frame.logicalPose().position().x - initialPose.x,
                            frame.logicalPose().position().y - initialPose.y,
                            frame.logicalPose().position().z - initialPose.z), preTravelVelocity,
                    PowerRuntime.airlockPowerDiagnostic((net.minecraft.server.level.ServerLevel) actor.level(), door),
                    currentContacts(actor), collision, door.state().phase());
            if (frame.logicalPose().position().distanceSquared(initialPose) < 1.0E-8)
                throw new GameTestAssertException("door contact was captured without physical frame translation");
            if (door.state().phase() != AirlockState.DoorPhase.OPENING)
                throw new GameTestAssertException("fresh moving-door contact failed normal paid/closed gates: " + door.state());
            actor.discard();
            helper.succeed();
            return;
        }
        helper.runAfterDelay(1, () -> idleDoorContactTick(helper, actor, frame, root, door,
                initialPose, collision, pulseStartTick, tick + 1));
        });
    }

    private static FixtureTravelVillager spawnExteriorHumanHost(net.minecraft.server.level.ServerLevel level,
            ServerSubLevel frame, SableFixtureScene.Region scene, Vector3d localFeet, String label) {
        return spawnHumanHost(level, frame, scene, localFeet, label, false);
    }

    private static FixtureTravelVillager spawnDeckHumanHost(net.minecraft.server.level.ServerLevel level,
            ServerSubLevel frame, SableFixtureScene.Region scene, Vector3d localFeet, String label) {
        return spawnHumanHost(level, frame, scene, localFeet, label, true);
    }

    private static FixtureTravelVillager spawnHumanHost(net.minecraft.server.level.ServerLevel level,
            ServerSubLevel frame, SableFixtureScene.Region scene, Vector3d localFeet, String label, boolean deckSupported) {
        Vector3d worldFeet = frame.logicalPose().transformPosition(new Vector3d(localFeet));
        double terrainY = scene.origin.getY();
        if (deckSupported) {
            BlockPos storageSupport = BlockPos.containing(localFeet.x, localFeet.y - 1, localFeet.z);
            var supportState = LoadedFrameRayQuery.knownServerBlock(level, frame, storageSupport);
            if (frame.isRemoved() || supportState == null || !supportState.isCollisionShapeFullBlock(level, storageSupport))
                throw new GameTestAssertException(label + " requires a live owner frame and known authored deck cell");
        } else {
            BlockPos supportPos = BlockPos.containing(worldFeet.x, terrainY - 1, worldFeet.z);
            var supportState = level.getBlockState(supportPos);
            var supportShape = supportState.getCollisionShape(level, supportPos);
            if (supportShape.isEmpty() || Math.abs(supportPos.getY()
                    + supportShape.max(net.minecraft.core.Direction.Axis.Y) - terrainY) > 1.0E-6)
                throw new GameTestAssertException(label + " lacks loaded authored terrain support at its world X/Z: "
                        + supportPos + " shape=" + supportShape + " expectedSurfaceY=" + terrainY);
            worldFeet.y = terrainY;
        }
         var actor = new FixtureTravelVillager(level);
          actor.setNoAi(false);
          actor.goalSelector.removeAllGoals(goal -> true);
          actor.targetSelector.removeAllGoals(goal -> true);
          actor.getNavigation().stop();
          actor.setSpeed(.1f);
          actor.setPos(worldFeet.x, worldFeet.y, worldFeet.z);
          if (!level.addFreshEntity(actor)) throw new GameTestAssertException(label + " Human host did not spawn");
           if (!deckSupported) requireOrdinaryGround(actor, label, frame, terrainY);
        CharacterIdentitySystem.enrollSupportedActor(actor, level);
        if (CharacterIdentitySystem.resolveForActor(actor)
                .filter(data -> data.component(DoorBumpOpenerComponent.class).isPresent()).isEmpty()) {
            actor.discard();
            throw new GameTestAssertException(label + " lacks resolved Human DoorBumpOpener component");
        }
        return actor;
    }

    private static void prepareFixtureTravel(Mob actor, String label) {
        actor.getNavigation().stop();
        actor.setSpeed(.1f);
        if (actor.isNoAi() || !actor.isEffectiveAi())
            throw new GameTestAssertException(label + " requires effective vanilla travel AI: noAI="
                    + actor.isNoAi() + " effectiveAI=" + actor.isEffectiveAi());
        if (actor instanceof FixtureTravelVillager fixture) fixture.armTravel();
    }

    private static void driveHorizontalNativeTravel(GameTestHelper helper, net.minecraft.world.entity.LivingEntity actor,
            ServerSubLevel frame, Vector3d localDirection, int ticks, int tick, Vec3 initialPosition,
            Object priorCollisionInfo, Runnable completed) {
        if (actor.isRemoved() || helper.testInfo.isDone()) return;
        if (tick >= ticks) {
            var info = ((EntityMovementExtension) (Object) actor).sable$getCollisionInfo();
            if (info == null || info == priorCollisionInfo || actor.position().distanceToSqr(initialPosition) < .01)
                throw new GameTestAssertException("adjacent hull approach lacked real movement/collision evidence: "
                        + exteriorDiagnostic(actor, frame, BlockPos.containing(actor.position())));
            completed.run();
            return;
        }
        Vector3d start = frame.logicalPose().transformPosition(new Vector3d(0, 0, 0));
        Vector3d end = frame.logicalPose().transformPosition(new Vector3d(localDirection).mul(.2));
        Vec3 horizontal = new Vec3(end.x - start.x, 0, end.z - start.z);
        if (horizontal.lengthSqr() < 1.0E-8) throw new GameTestAssertException("frame pose has no horizontal exterior approach vector");
        float yaw = (float) Math.toDegrees(Math.atan2(horizontal.x, -horizontal.z));
        actor.setYRot(yaw);
        helper.runAfterDelay(1, () -> {
            if (actor.isRemoved() || helper.testInfo.isDone()) return;
            prepareFixtureTravel((Mob) actor, "adjacent hull travel tick " + tick);
            actor.travel(new Vec3(0, 0, -1));
            driveHorizontalNativeTravel(helper, actor, frame, localDirection, ticks, tick + 1, initialPosition,
                    priorCollisionInfo, completed);
        });
    }

    private static void driveExteriorTowardHatch(GameTestHelper helper, net.minecraft.world.entity.LivingEntity actor,
            ServerSubLevel frame, BlockPos root, AirlockBlockEntity door, int tick, long startTick,
            Vec3 initialPosition, Runnable completed) {
        if (actor.isRemoved() || helper.testInfo.isDone()) return;
        if (tick >= 12 || helper.getTick() - startTick >= 32)
            throw new GameTestAssertException("bounded exterior living travel missed hatch contact: "
                + exteriorDiagnostic(actor, frame, root) + " power="
                + PowerRuntime.airlockPowerDiagnostic((net.minecraft.server.level.ServerLevel) actor.level(), door)
                + " phase=" + door.state());
        helper.runAfterDelay(1, () -> {
            if (actor.isRemoved() || helper.testInfo.isDone()) return;
            long remaining = 32 - (helper.getTick() - startTick);
            if (remaining <= 0) throw new GameTestAssertException("exterior paid readiness expired: "
                    + PowerRuntime.airlockPowerDiagnostic((net.minecraft.server.level.ServerLevel) actor.level(), door));
            var level = (net.minecraft.server.level.ServerLevel) actor.level();
            PowerFixtureReadiness.await(helper, level, remaining,
                    () -> fullyPaid20(level, door) && door.state().phase() == AirlockState.DoorPhase.CLOSED
                            && !door.state().boltsDown(),
                    () -> "exterior approach requires current paid 20 W closed/unbolted hatch: "
                            + PowerRuntime.airlockPowerDiagnostic(level, door) + " phase=" + door.state(), () -> {
            if (actor.isRemoved() || helper.testInfo.isDone()) return;
            Vector3d origin = new Vector3d(root.getX() + .5, root.getY(), root.getZ() - 1.4);
            Vector3d target = new Vector3d(root.getX() + .5, root.getY(), root.getZ() - .4);
            frame.logicalPose().transformPosition(origin);
            frame.logicalPose().transformPosition(target);
            Vec3 horizontal = new Vec3(target.x - origin.x, 0, target.z - origin.z);
            if (horizontal.lengthSqr() < 1.0E-8)
                throw new GameTestAssertException("canonical frame pose has no horizontal front approach vector");
            actor.setYRot((float) Math.toDegrees(Math.atan2(horizontal.x, -horizontal.z)));
            prepareFixtureTravel((Mob) actor, "exterior hatch travel tick " + tick);
            Object previous = ((EntityMovementExtension) (Object) actor).sable$getCollisionInfo();
            actor.travel(new Vec3(0, 0, -1));
            Object current = ((EntityMovementExtension) (Object) actor).sable$getCollisionInfo();
            boolean exactDoor = currentContacts(actor).stream().anyMatch(contact ->
                    contact.frame().getUniqueId().equals(frame.getUniqueId()) && contact.rootPos().equals(root));
            if (actor.isRemoved() || helper.testInfo.isDone()) return;
            if (current == null || current == previous)
                throw new GameTestAssertException("effective exterior LivingEntity.travel produced no fresh native result: noAI="
                        + ((Mob) actor).isNoAi() + " effectiveAI=" + actor.isEffectiveAi() + " previous=" + previous
                        + " current=" + current + " " + exteriorDiagnostic(actor, frame, root));
            if (current != null && current != previous && exactDoor
                    && actor.position().distanceToSqr(initialPosition) > 1.0E-4
                    && door.state().phase() == AirlockState.DoorPhase.OPENING) completed.run();
            else driveExteriorTowardHatch(helper, actor, frame, root, door, tick + 1, startTick, initialPosition, completed);
            });
        });
    }

    private static String exteriorDiagnostic(net.minecraft.world.entity.LivingEntity actor, ServerSubLevel frame, BlockPos root) {
        EntityMovementExtension movement = (EntityMovementExtension) (Object) actor;
        var info = movement.sable$getCollisionInfo();
        var contacts = currentContacts(actor);
        var doorState = LoadedFrameRayQuery.knownServerBlock((net.minecraft.server.level.ServerLevel) actor.level(), frame, root);
        var doorShape = doorState == null ? java.util.List.of()
                : doorState.getCollisionShape(actor.level(), root).toAabbs().stream().map(box -> box.move(root)).toList();
        boolean finalDoorShapeTouch = doorState != null && productionSatTouches(
                (net.minecraft.server.level.ServerLevel) actor.level(), frame, root, actor.getBoundingBox());
        return "feet=" + actor.position() + " bounds=" + actor.getBoundingBox()
                + " framePose=" + frame.logicalPose() + " nativeInfo=" + info + " nativeInfoFields=" + nativeInfoFields(info)
                + " nativeContacts=" + contacts + " finalDoorShape=" + doorShape
                 + " finalBodyTouchesDoorByProductionSat=" + finalDoorShapeTouch
                 + " postCollisionVelocity=" + actor.getDeltaMovement();
    }

    private static boolean productionSatTouches(net.minecraft.server.level.ServerLevel level, ServerSubLevel frame,
            BlockPos root, net.minecraft.world.phys.AABB bounds) {
        var state = LoadedFrameRayQuery.knownServerBlock(level, frame, root);
        if (state == null) return false;
        try {
            var proof = AirlockBumpSystem.class.getDeclaredMethod("actualShapeTouches",
                    net.minecraft.server.level.ServerLevel.class,
                    dev.ryanhcode.sable.companion.math.Pose3dc.class,
                    BlockPos.class, net.minecraft.world.level.block.state.BlockState.class,
                    net.minecraft.world.phys.AABB.class);
            proof.setAccessible(true);
            return (boolean) proof.invoke(null, level, frame.logicalPose(), root, state, bounds);
        } catch (ReflectiveOperationException failure) {
            throw new GameTestAssertException("production final-shape SAT diagnostic failed: " + failure);
        }
    }

    private static double reflectedTimeOfImpact(net.minecraft.world.phys.AABB moving, Vec3 delta,
            net.minecraft.world.phys.AABB target) {
        try {
            var method = AirlockBumpSystem.class.getDeclaredMethod("timeOfImpact",
                    net.minecraft.world.phys.AABB.class, Vec3.class, net.minecraft.world.phys.AABB.class);
            method.setAccessible(true);
            return (double) method.invoke(null, moving, delta, target);
        } catch (ReflectiveOperationException failure) {
            throw new GameTestAssertException("shared loaded-world sweep TOI diagnostic failed: " + failure);
        }
    }

    private static net.minecraft.world.phys.AABB projectedDoorBounds(net.minecraft.server.level.ServerLevel level,
            ServerSubLevel frame, BlockPos root) {
        return projectedDoorBounds(level, frame, root, false);
    }

    private static net.minecraft.world.phys.AABB projectedDoorBounds(net.minecraft.server.level.ServerLevel level,
            ServerSubLevel frame, BlockPos root, boolean potentialClosedLeaf) {
        double minX = Double.POSITIVE_INFINITY, minY = Double.POSITIVE_INFINITY, minZ = Double.POSITIVE_INFINITY;
        double maxX = Double.NEGATIVE_INFINITY, maxY = Double.NEGATIVE_INFINITY, maxZ = Double.NEGATIVE_INFINITY;
        for (BlockPos pos : java.util.List.of(root, root.above())) {
            var state = LoadedFrameRayQuery.knownServerBlock(level, frame, pos);
            if (state == null) throw new GameTestAssertException("native door leaf shape is not current known storage: " + pos);
            if (potentialClosedLeaf && state.getBlock() instanceof AirlockBlock)
                state = state.setValue(AirlockBlock.OPEN, false);
            for (var piece : state.getCollisionShape(level, pos).toAabbs())
                for (int x = 0; x < 2; x++) for (int y = 0; y < 2; y++) for (int z = 0; z < 2; z++) {
                    var corner = frame.logicalPose().transformPosition(new Vector3d(
                            pos.getX() + (x == 0 ? piece.minX : piece.maxX),
                            pos.getY() + (y == 0 ? piece.minY : piece.maxY),
                            pos.getZ() + (z == 0 ? piece.minZ : piece.maxZ)));
                    minX = Math.min(minX, corner.x); minY = Math.min(minY, corner.y); minZ = Math.min(minZ, corner.z);
                    maxX = Math.max(maxX, corner.x); maxY = Math.max(maxY, corner.y); maxZ = Math.max(maxZ, corner.z);
                }
        }
        if (!Double.isFinite(minX)) throw new GameTestAssertException("native airlock leaf has no collision geometry");
        return new net.minecraft.world.phys.AABB(minX, minY, minZ, maxX, maxY, maxZ);
    }

    private static java.util.List<com.juicyslew.moonstation14.ms14.doors.NativeAirlockContact> contactsFromMove(
            com.juicyslew.moonstation14.ms14.shuttle.sable.SableOwnedBodyMotion.MoveResult move) {
        if (move == null || move.collisionInfo() == null) return java.util.List.of();
        return ((NativeAirlockContactAccess) (Object) move.collisionInfo()).moonstation14$airlockContacts();
    }

    private static String nativeInfoFields(Object info) {
        if (info == null) return "<none>";
        var fields = new java.util.ArrayList<String>();
        for (var field : info.getClass().getDeclaredFields()) {
            try {
                field.setAccessible(true);
                fields.add(field.getName() + "=" + field.get(info));
            } catch (ReflectiveOperationException | RuntimeException failure) {
                fields.add(field.getName() + "=<" + failure.getClass().getSimpleName() + ">");
            }
        }
        return fields.toString();
    }

    private static String knownShapeOverlapDiagnostic(net.minecraft.server.level.ServerLevel level, ServerSubLevel frame,
            BlockPos frameRoot, BlockPos worldRoot, net.minecraft.world.phys.AABB bodyBounds) {
        var frameState = LoadedFrameRayQuery.knownServerBlock(level, frame, frameRoot);
        var frameBoxes = new java.util.ArrayList<net.minecraft.world.phys.AABB>();
        if (frameState != null) for (var local : frameState.getCollisionShape(level, frameRoot).toAabbs()) {
            var box = local.move(frameRoot);
            double minX = Double.POSITIVE_INFINITY, minY = Double.POSITIVE_INFINITY, minZ = Double.POSITIVE_INFINITY;
            double maxX = Double.NEGATIVE_INFINITY, maxY = Double.NEGATIVE_INFINITY, maxZ = Double.NEGATIVE_INFINITY;
            for (int x = 0; x < 2; x++) for (int y = 0; y < 2; y++) for (int z = 0; z < 2; z++) {
                var corner = frame.logicalPose().transformPosition(new Vector3d(
                        x == 0 ? box.minX : box.maxX, y == 0 ? box.minY : box.maxY, z == 0 ? box.minZ : box.maxZ));
                minX = Math.min(minX, corner.x); minY = Math.min(minY, corner.y); minZ = Math.min(minZ, corner.z);
                maxX = Math.max(maxX, corner.x); maxY = Math.max(maxY, corner.y); maxZ = Math.max(maxZ, corner.z);
            }
            frameBoxes.add(new net.minecraft.world.phys.AABB(minX, minY, minZ, maxX, maxY, maxZ));
        }
        boolean worldChunkKnown = level.getChunkSource().getChunk(worldRoot.getX() >> 4, worldRoot.getZ() >> 4,
                net.minecraft.world.level.chunk.status.ChunkStatus.FULL, false) != null;
        var worldState = worldChunkKnown ? level.getBlockState(worldRoot) : null;
        var worldBoxes = worldState == null ? java.util.List.<net.minecraft.world.phys.AABB>of()
                : worldState.getCollisionShape(level, worldRoot).toAabbs().stream().map(box -> box.move(worldRoot)).toList();
        return "frameKnown=" + (frameState != null) + " frameShape=" + frameBoxes
                + " frameAabbOverlapsBody=" + frameBoxes.stream().anyMatch(box -> box.intersects(bodyBounds))
                + " worldKnown=" + worldChunkKnown + " worldState=" + worldState + " worldShape=" + worldBoxes
                + " worldShapeOverlapsBody=" + worldBoxes.stream().anyMatch(box -> box.intersects(bodyBounds))
                + " body=" + bodyBounds;
    }

    /** Exact SDK OBB/SAT clearance proof for every authored solid world block against every cabin hull collision piece. */
    private static String proveWorldAssemblyOutsideNativeHull(net.minecraft.server.level.ServerLevel level,
            MovingCabinAuthorityFixture.Ready fixture,
            com.juicyslew.moonstation14.ms14.doors.AirlockCoreGameTests.PoweredAssembly assembly) {
        if (!(level instanceof LevelExtension extension))
            throw new GameTestAssertException("loaded ServerLevel lacks Sable OBB scratch-vector access");
        LevelReusedVectors sink = extension.sable$getJOMLSink();
        ServerSubLevel frame = fixture.plot().subLevel();
        BlockPos origin = fixture.cabin().origin();
        var solids = new java.util.ArrayList<net.minecraft.world.phys.AABB>();
        for (BlockPos pos : assembly.solidPositions()) {
            if (level.getChunkSource().getChunk(pos.getX() >> 4, pos.getZ() >> 4,
                    net.minecraft.world.level.chunk.status.ChunkStatus.FULL, false) == null)
                throw new GameTestAssertException("world-solid overlap proof refuses unloaded block: " + pos);
            var state = level.getBlockState(pos);
            var pieces = state.getCollisionShape(level, pos).toAabbs();
            if (pieces.isEmpty()) throw new GameTestAssertException("authored world solid has no collision shape: " + pos + " " + state);
            for (var piece : pieces) solids.add(piece.move(pos));
        }
        var hull = new java.util.ArrayList<String>();
        for (int x = 0; x < com.juicyslew.moonstation14.ms14.shuttle.fixture.ShuttleCabinFixture.WIDTH; x++)
            for (int y = 0; y < com.juicyslew.moonstation14.ms14.shuttle.fixture.ShuttleCabinFixture.HEIGHT; y++)
                for (int z = 0; z < com.juicyslew.moonstation14.ms14.shuttle.fixture.ShuttleCabinFixture.DEPTH; z++) {
                    BlockPos pos = origin.offset(x, y, z);
                    var state = LoadedFrameRayQuery.knownServerBlock(level, frame, pos);
                    if (state == null)
                        throw new GameTestAssertException("world-solid overlap proof refuses unknown native hull cell: " + pos);
                    for (var piece : state.getCollisionShape(level, pos).toAabbs()) hull.add(pos + " " + piece);
                }
        int tested = 0;
        for (BlockPos pos : assembly.solidPositions()) {
            var state = level.getBlockState(pos);
            for (var localWorld : state.getCollisionShape(level, pos).toAabbs()) {
                var worldBox = localWorld.move(pos);
                var worldObb = new OrientedBoundingBox3d(new Vector3d(
                        (worldBox.minX + worldBox.maxX) * .5, (worldBox.minY + worldBox.maxY) * .5,
                        (worldBox.minZ + worldBox.maxZ) * .5), new Vector3d(worldBox.getXsize(), worldBox.getYsize(),
                        worldBox.getZsize()), new Quaterniond(), sink);
                for (int x = 0; x < com.juicyslew.moonstation14.ms14.shuttle.fixture.ShuttleCabinFixture.WIDTH; x++)
                    for (int y = 0; y < com.juicyslew.moonstation14.ms14.shuttle.fixture.ShuttleCabinFixture.HEIGHT; y++)
                        for (int z = 0; z < com.juicyslew.moonstation14.ms14.shuttle.fixture.ShuttleCabinFixture.DEPTH; z++) {
                            BlockPos hullPos = origin.offset(x, y, z);
                            var hullState = LoadedFrameRayQuery.knownServerBlock(level, frame, hullPos);
                            for (var localHull : hullState.getCollisionShape(level, hullPos).toAabbs()) {
                                Vector3d center = new Vector3d(hullPos.getX() + (localHull.minX + localHull.maxX) * .5,
                                        hullPos.getY() + (localHull.minY + localHull.maxY) * .5,
                                        hullPos.getZ() + (localHull.minZ + localHull.maxZ) * .5);
                                frame.logicalPose().transformPosition(center);
                                var hullObb = new OrientedBoundingBox3d(center, new Vector3d(
                                        (localHull.maxX - localHull.minX) * frame.logicalPose().scale().x(),
                                        (localHull.maxY - localHull.minY) * frame.logicalPose().scale().y(),
                                        (localHull.maxZ - localHull.minZ) * frame.logicalPose().scale().z()),
                                        frame.logicalPose().orientation(), sink);
                                tested++;
                                Vector3d mtv = new Vector3d(OrientedBoundingBox3d.satToleranced(worldObb, hullObb, 0d));
                                if (mtv.lengthSquared() > 0)
                                    throw new GameTestAssertException("authored ordinary-world solid intersects native cabin hull by SDK OBB/SAT: "
                                            + "worldPos=" + pos + " worldPiece=" + worldBox + " hullPos=" + hullPos
                                            + " hullPiece=" + localHull + " mtv=" + mtv + " pose=" + frame.logicalPose());
                            }
                        }
            }
        }
        return "sdkObbSatTolerance=0 worldSolidPieces=" + solids.size() + " nativeHullPieces=" + hull.size()
                + " pairTests=" + tested + " overlap=false";
    }

    private static void registerWorldAssemblyCleanup(GameTestHelper helper,
            com.juicyslew.moonstation14.ms14.doors.AirlockCoreGameTests.PoweredAssembly assembly) {
        helper.testInfo.addListener(new GameTestListener() {
            private void clear() {
                com.juicyslew.moonstation14.ms14.doors.AirlockCoreGameTests.removePoweredAirlockAt(assembly);
            }
            @Override public void testStructureLoaded(GameTestInfo ignored) { }
            @Override public void testPassed(GameTestInfo ignored, GameTestRunner runner) { clear(); }
            @Override public void testFailed(GameTestInfo ignored, GameTestRunner runner) { clear(); }
            @Override public void testAddedForRerun(GameTestInfo oldTest, GameTestInfo newTest, GameTestRunner runner) { clear(); }
        });
    }

    @GameTestTags({"shuttle-airlock-bump", "airlock_bump", "movement"})
    @GameTest(template = "atmos_large_empty", templateNamespace = "moonstation14", timeoutTicks = 800,
            batch = "shuttle_native_airlock_bump")
    public static void nearerOrdinaryWallMasksNativeFrameDoorNominatedBeforeWorldClip(GameTestHelper helper) {
        MovingCabinAuthorityFixture.start(helper, new BlockPos(840, 300, 760), "native-airlock-world-mask",
                3.5, 1, 3.5, (fixture, unusedOwnedHuman) -> {
                    var level = fixture.level();
                    BlockPos root = fixture.cabin().at(com.juicyslew.moonstation14.ms14.shuttle.fixture.ShuttleCabinFixture.AIRLOCK);
                    if (!(level.getBlockEntity(root) instanceof AirlockBlockEntity door))
                        throw new GameTestAssertException("authored frame airlock controller missing");
                    ServerSubLevel frame = fixture.plot().subLevel();
                     PowerFixtureReadiness.await(helper, level, 140, () -> fullyPaid20(level, door),
                             () -> "frame door lacks paid power before occlusion test: "
                                     + PowerRuntime.airlockPowerDiagnostic(level, door), () -> {
                                 Vector3d doorWorld = new Vector3d(root.getX() + .5, root.getY() - 1, root.getZ() - .55);
                                 frame.logicalPose().transformPosition(doorWorld);
                                  var mob = spawnExteriorHumanHost(level, frame, fixture.scene(),
                                          new Vector3d(root.getX() + .5, root.getY() - 1, root.getZ() - 2.5), "mask-test");
                                  BlockPos wall = BlockPos.containing(doorWorld.x,
                                          mob.getBoundingBox().minY + .1, doorWorld.z);
                                  var wallState = net.minecraft.world.level.block.Blocks.STONE.defaultBlockState();
                                  level.setBlock(wall, wallState, 3);
                                 Vector3d target = new Vector3d(root.getX() + .5, root.getY() - 1, root.getZ() - .4);
                                 frame.logicalPose().transformPosition(target);
                                   Vec3 approach = new Vec3(target.x - mob.getX(), 0, target.z - mob.getZ()).normalize().scale(3.5);
                                   var wallShape = wallState.getCollisionShape(level, wall);
                                   if (wallShape.isEmpty() || mob.getBoundingBox().intersects(wallShape.bounds().move(wall)))
                                       throw new GameTestAssertException("ordinary wall must be solid at actor height without initial body penetration: wall="
                                               + wall + " body=" + mob.getBoundingBox() + " shape=" + wallShape);
                                   double wallToi = reflectedTimeOfImpact(mob.getBoundingBox(), approach,
                                           wallShape.bounds().move(wall));
                                   var leafBounds = projectedDoorBounds(level, frame, root);
                                   double leafToi = reflectedTimeOfImpact(mob.getBoundingBox(), approach, leafBounds);
                                   if (!(wallToi >= 0 && leafToi >= 0 && wallToi < leafToi))
                                       throw new GameTestAssertException("loaded ordinary wall must be the first swept contact before actual native leaf: wall="
                                               + wall + " wallTOI=" + wallToi + " leaf=" + leafBounds + " leafTOI=" + leafToi
                                               + " start=" + mob.getBoundingBox() + " delta=" + approach);
                                  var previousInfo = ((EntityMovementExtension) (Object) mob).sable$getCollisionInfo();
                                  Vec3 beforeMove = mob.position();
                                  var beforeBounds = mob.getBoundingBox();
                                  mob.setDeltaMovement(approach);
                                  prepareFixtureTravel(mob, "nearer ordinary wall native move");
                                  mob.travel(Vec3.ZERO);
                                  var afterInfo = ((EntityMovementExtension) (Object) mob).sable$getCollisionInfo();
                                  if (afterInfo == null || afterInfo == previousInfo
                                          || mob.position().distanceToSqr(beforeMove) < 1.0E-4)
                                      throw new GameTestAssertException("nearer ordinary wall move had stale/no physical travel evidence: noAI="
                                              + mob.isNoAi() + " effectiveAI=" + mob.isEffectiveAi() + " prior=" + previousInfo
                                              + " fresh=" + afterInfo + " before=" + beforeMove + " after=" + mob.position()
                                              + " request=" + approach);
                                  var contacts = currentContacts(mob);
                                boolean nominatedFrameDoor = contacts.stream().anyMatch(contact ->
                                        contact.frame().getUniqueId().equals(frame.getUniqueId())
                                                && (contact.storagePos().equals(root) || contact.storagePos().equals(root.above())));
                                 if (!nominatedFrameDoor)
                                      throw new GameTestAssertException("exterior ordinary-wall blocker did not retain a fresh same-plane door nomination; wall="
                                              + wall + " beforeBounds=" + beforeBounds + " body=" + mob.getBoundingBox()
                                              + " noAI=" + mob.isNoAi() + " effectiveAI=" + mob.isEffectiveAi()
                                              + " oldResult=" + previousInfo + " native=" + afterInfo + " contacts=" + contacts
                                              + " shapes=" + knownShapeOverlapDiagnostic(level, frame, root, wall, mob.getBoundingBox()));
                                if (door.state().phase() != AirlockState.DoorPhase.CLOSED)
                                    throw new GameTestAssertException("nearer ordinary wall must mask the nominated farther frame door: "
                                            + door.state() + " body=" + mob.getBoundingBox() + " wall=" + wall);
                                mob.discard();
                                helper.succeed();
                            });
                });
    }

    @GameTestTags({"shuttle-airlock-bump", "airlock_bump", "movement"})
    @GameTest(template = "atmos_large_empty", templateNamespace = "moonstation14", timeoutTicks = 900,
            batch = "shuttle_native_airlock_bump")
    public static void nearerFrameDoorMasksFartherPaidOrdinaryWorldDoor(GameTestHelper helper) {
        MovingCabinAuthorityFixture.start(helper, new BlockPos(1080, 300, 1000), "frame-masks-world-door",
                3.5, 1, 3.5, (fixture, unusedOwnedHuman) -> {
                    var level = fixture.level();
                    BlockPos frameRoot = fixture.cabin().at(com.juicyslew.moonstation14.ms14.shuttle.fixture.ShuttleCabinFixture.AIRLOCK);
                    if (!(level.getBlockEntity(frameRoot) instanceof AirlockBlockEntity frameDoor))
                        throw new GameTestAssertException("near frame-root controller missing");
                    var target = LoadedFrameRayQuery.currentTargetPoint(level, frameRoot);
                    if (target == null || target.frameId() == null)
                        throw new GameTestAssertException("frame airlock target lacks exact current frame point");
                     BlockPos worldRoot = new BlockPos((int) Math.floor(target.worldPoint().x),
                             302, (int) Math.floor(target.worldPoint().z) - 2);
                     ServerSubLevel frame = fixture.plot().subLevel();
                     var worldAssembly = com.juicyslew.moonstation14.ms14.doors.AirlockCoreGameTests
                            .poweredAirlockAt(level, worldRoot, -2);
                    registerWorldAssemblyCleanup(helper, worldAssembly);
                    AirlockBlockEntity worldDoor = worldAssembly.fixture().door();
                    String hullClearance = proveWorldAssemblyOutsideNativeHull(level, fixture, worldAssembly);
                    SableFixtureScene.awaitStationaryFrame(helper, fixture.plot(), 120, () ->
                            PowerFixtureReadiness.await(helper, level, 160,
                                    () -> fullyPaid20(level, frameDoor) && fullyPaid20(level, worldDoor),
                                    () -> "both near-frame and in-room ordinary doors need paid 20 W: frame="
                                            + PowerRuntime.airlockPowerDiagnostic(level, frameDoor) + " world="
                                            + PowerRuntime.airlockPowerDiagnostic(level, worldDoor) + " " + hullClearance, () -> {
                                         var mob = spawnDeckHumanHost(level, frame, fixture.scene(),
                                                  new Vector3d(frameRoot.getX() + .5, frameRoot.getY(),
                                                          frameRoot.getZ() + 1.4), "world-mask");
                                        var initialBounds = mob.getBoundingBox();
                                         if (!fullyPaid20(level, frameDoor) || !fullyPaid20(level, worldDoor)
                                                 || frameDoor.state().phase() != AirlockState.DoorPhase.CLOSED
                                                 || frameDoor.state().boltsDown())
                                             throw new GameTestAssertException("doors not paid and closed before ordering sweep");
                                         Vec3 request = new Vec3(worldRoot.getX() + .5 - mob.getX(), 0,
                                                 worldRoot.getZ() + .5 - mob.getZ()).normalize().scale(3.5);
                                         var nominated = AirlockBumpSystem.beforeMoveContacts(mob, request);
                                         boolean nominatedWorld = nominated.stream().anyMatch(contact -> contact.getBlockPos().equals(worldRoot));
                                         if (!nominatedWorld) throw new GameTestAssertException("real requested movement failed to nominate exact ordinary root: request=" + request + " candidates=" + nominated);
                                         var nativeBounds = projectedDoorBounds(level, frame, frameRoot);
                                         var worldBounds = new net.minecraft.world.phys.AABB(worldRoot.getX(), worldRoot.getY(), worldRoot.getZ(),
                                                 worldRoot.getX() + 1, worldRoot.getY() + 2, worldRoot.getZ() + 1);
                                         double nativeToi = reflectedTimeOfImpact(mob.getBoundingBox(), request, nativeBounds);
                                         double worldToi = reflectedTimeOfImpact(mob.getBoundingBox(), request, worldBounds);
                                         if (!(nativeToi > 0 && worldToi > nativeToi))
                                             throw new GameTestAssertException("native leaf must precede nominated ordinary leaf: nativeTOI=" + nativeToi + " worldTOI=" + worldToi);
                                         Object previousInfo = ((EntityMovementExtension) (Object) mob).sable$getCollisionInfo();
                                         var before = mob.getBoundingBox();
                                         mob.setDeltaMovement(request);
                                         prepareFixtureTravel(mob, "native-before-ordinary real strong travel");
                                         mob.travel(Vec3.ZERO);
                                         var info = ((EntityMovementExtension) (Object) mob).sable$getCollisionInfo();
                                         if (info == null || info == previousInfo)
                                             throw new GameTestAssertException("ordering sweep lacked fresh physical collision result");
                                         {
                                                     var contacts = currentContacts(mob);
                                                    boolean nativeFrameContact = contacts.stream().anyMatch(contact ->
                                                            contact.frame().getUniqueId().equals(frame.getUniqueId())
                                                                    && (contact.storagePos().equals(frameRoot)
                                                                    || contact.storagePos().equals(frameRoot.above())));
                                                     if (!nativeFrameContact)
                                                         throw new GameTestAssertException("near frame contact was not selected before ordinary-room world door: "
                                                                 + hullClearance + " initialBounds=" + initialBounds + " fresh=" + info
                                                                 + " before=" + before + " request=" + request + " nativeTOI=" + nativeToi + " worldTOI=" + worldToi);
                                                    if (frameDoor.state().phase() != AirlockState.DoorPhase.OPENING)
                                                        throw new GameTestAssertException("near frame door did not consume its actual native contact: "
                                                                + hullClearance
                                                                + " initialBounds=" + initialBounds + " fresh=" + info);
                                                    if (worldDoor.state().phase() != AirlockState.DoorPhase.CLOSED)
                                                        throw new GameTestAssertException("farther paid ordinary-room door opened beyond the nearer frame leaf: "
                                                                + hullClearance
                                                                + " initialBounds=" + initialBounds + " fresh=" + info);
                                                    mob.discard();
                                                    helper.succeed();
                                         }
                                    }));
                });
    }

    @GameTestTags({"shuttle-airlock-bump", "airlock_bump", "movement"})
    @GameTest(template = "atmos_large_empty", templateNamespace = "moonstation14", timeoutTicks = 800,
            batch = "shuttle_native_airlock_bump")
    public static void rotatedFaceContactOpensWhileNearbyJambMissDoesNot(GameTestHelper helper) {
        MovingCabinAuthorityFixture.start(helper, new BlockPos(880, 300, 800), "rotated-native-airlock",
                3.5, 1, 3.5, (fixture, unusedOwnedHuman) -> {
                    var level = fixture.level();
                    BlockPos root = fixture.cabin().at(com.juicyslew.moonstation14.ms14.shuttle.fixture.ShuttleCabinFixture.AIRLOCK);
                    if (!(level.getBlockEntity(root) instanceof AirlockBlockEntity door))
                        throw new GameTestAssertException("authored rotated frame airlock controller missing");
                    ServerSubLevel frame = fixture.plot().subLevel();
                    Quaterniond rotation = new Quaterniond().rotationY(Math.PI / 4);
                    frame.logicalPose().orientation().set(rotation);
                    RigidBodyHandle.of(frame).teleport(frame.logicalPose().position(), rotation);
                    frame.updateLastPose();
                    frame.updateBoundingBox();
                    SableFixtureScene.awaitStationaryFrame(helper, fixture.plot(), 120, () ->
                     PowerFixtureReadiness.await(helper, level, 140, () -> fullyPaid20(level, door),
                             () -> "rotated frame airlock lacks 20 W paid APC grant: "
                                     + PowerRuntime.airlockPowerDiagnostic(level, door), () -> {
                                exerciseRotatedJambMiss(helper, fixture, door, root);
                                startNativeTravelContact(helper, fixture, door, root);
                             }));
                });
    }

    @GameTestTags({"shuttle-airlock-bump", "airlock_bump", "airlock_seam", "movement"})
    @GameTest(template = "atmos_large_empty", templateNamespace = "moonstation14", timeoutTicks = 850,
            batch = "shuttle_native_airlock_bump")
    public static void nativeContactPlaneOpensBothPaidSeamRootsOnce(GameTestHelper helper) {
        MovingCabinAuthorityFixture.start(helper, new BlockPos(920, 300, 840), "native-airlock-seam",
                3.5, 1, 3.5, (fixture, unusedOwnedHuman) -> {
                    var level = fixture.level();
                    BlockPos firstRoot = fixture.cabin().at(com.juicyslew.moonstation14.ms14.shuttle.fixture.ShuttleCabinFixture.AIRLOCK);
                    BlockPos secondRoot = firstRoot.east();
                    if (!(level.getBlockEntity(firstRoot) instanceof AirlockBlockEntity first))
                        throw new GameTestAssertException("authored seam root controller missing");
                     AirlockBlockEntity second = authorPaidSeamNeighbor(level, fixture, secondRoot);
                     PowerFixtureReadiness.await(helper, level, 150, () -> {
                                 var firstPower = PowerRuntime.airlockPower(level, first);
                                 var secondPower = PowerRuntime.airlockPower(level, second);
                                 return firstPower.known() && firstPower.deliveredWatts() == 20
                                         && secondPower.known() && secondPower.deliveredWatts() == 20;
                             }, () -> "both frame seam doors lack full paid 20 W grants: first="
                                     + PowerRuntime.airlockPowerDiagnostic(level, first) + " second="
                                     + PowerRuntime.airlockPowerDiagnostic(level, second),
                             () -> {
                     ServerSubLevel frame = fixture.plot().subLevel();
                                 var mob = spawnExteriorHumanHost(level, frame, fixture.scene(),
                                         new Vector3d(firstRoot.getX() + 1.0, firstRoot.getY() - 1, firstRoot.getZ() - 1.4),
                                         "seam");
                                     driveScheduledNativeTravel(helper, mob, frame, firstRoot, firstRoot.getX() + 1.0,
                                            0, helper.getTick(), mob.position(),
                                           () -> fullyPaid20(level, first) && fullyPaid20(level, second),
                                          () -> "first=" + PowerRuntime.airlockPowerDiagnostic(level, first)
                                                  + " second=" + PowerRuntime.airlockPowerDiagnostic(level, second), () -> {
                                     var contacts = currentContacts(mob);
                                     boolean nominatedFirst = contacts.stream().anyMatch(contact ->
                                             contact.frame() == frame && contact.rootPos().equals(firstRoot));
                                     boolean nominatedSecond = contacts.stream().anyMatch(contact ->
                                             contact.frame() == frame && contact.rootPos().equals(secondRoot));
                                     if (first.state().phase() != AirlockState.DoorPhase.OPENING
                                             || second.state().phase() != AirlockState.DoorPhase.OPENING
                                             || !nominatedFirst || !nominatedSecond)
                                         throw new GameTestAssertException("fresh seam contact failed exact dual-root nomination/opening: "
                                                 + contacts + " / " + first.state() + " / " + second.state());
                                     mob.discard();
                                     helper.succeed();
                                          });
                            });
                });
    }

    @GameTestTags({"shuttle-airlock-bump", "airlock_bump"})
    @GameTest(template = "atmos_large_empty", templateNamespace = "moonstation14", timeoutTicks = 800,
            batch = "shuttle_native_airlock_bump")
    public static void replacedRootAndRemovedFrameRefuseCapturedRealNativeContact(GameTestHelper helper) {
        MovingCabinAuthorityFixture.start(helper, new BlockPos(960, 300, 880), "stale-native-airlock-contact",
                3.5, 1, 3.5, (fixture, unusedOwnedHuman) -> {
                    var level = fixture.level();
                    BlockPos root = fixture.cabin().at(com.juicyslew.moonstation14.ms14.shuttle.fixture.ShuttleCabinFixture.AIRLOCK);
                    if (!(level.getBlockEntity(root) instanceof AirlockBlockEntity original))
                        throw new GameTestAssertException("stale-contact root controller missing");
                    ServerSubLevel frame = fixture.plot().subLevel();
                     PowerFixtureReadiness.await(helper, level, 140, () -> fullyPaid20(level, original),
                             () -> "stale-contact frame door lacks 20 W readiness: "
                                     + PowerRuntime.airlockPowerDiagnostic(level, original), () -> {
                                 var mob = spawnExteriorHumanHost(level, frame, fixture.scene(),
                                         new Vector3d(root.getX() + .5, root.getY() - 1, root.getZ() - 1.4), "stale-contact");
                                var movement = (EntityMovementExtension) (Object) mob;
                                var previous = movement.sable$getCollisionInfo();
                                var preMove = mob.getBoundingBox();
                                 Vector3d target = new Vector3d(root.getX() + .5, root.getY() - 1, root.getZ() - .4);
                                 frame.logicalPose().transformPosition(target);
                                  Vec3 requested = new Vec3(target.x - mob.getX(), 0, target.z - mob.getZ()).normalize().scale(3.0);
                                mob.move(MoverType.SELF, requested);
                                var captured = movement.sable$getCollisionInfo();
                                if (captured == null || captured == previous)
                                    throw new GameTestAssertException("fixture failed to produce a fresh native result");
                                var access = (NativeAirlockContactAccess) (Object) captured;
                                 var matching = access.moonstation14$airlockContacts().stream()
                                         .filter(contact -> contact.frame().getUniqueId().equals(frame.getUniqueId())
                                                 && (contact.storagePos().equals(root) || contact.storagePos().equals(root.above())))
                                         .findFirst().orElseThrow(() -> new GameTestAssertException(
                                                 "native capture omitted actual lower/upper door contact"));
                                if (!original.identity().equals(matching.rootControllerIdentity())
                                        || !matching.rootPos().equals(root))
                                    throw new GameTestAssertException("native metadata failed to capture exact root-controller identity");
                                if (original.state().phase() != AirlockState.DoorPhase.CLOSED)
                                    throw new GameTestAssertException("direct native capture must not invoke a door opener");

                                var oldLower = level.getBlockState(root);
                                var oldUpper = level.getBlockState(root.above());
                                level.setBlock(root, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), 3);
                                level.setBlock(root.above(), net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), 3);
                                level.setBlock(root, oldLower, 3);
                                level.setBlock(root.above(), oldUpper, 3);
                                if (!(level.getBlockEntity(root) instanceof AirlockBlockEntity replacement)
                                        || replacement.identity().equals(original.identity()))
                                    throw new GameTestAssertException("same-state root replacement did not create new controller identity");
                                if (AirlockBumpSystem.afterNativeMove(mob, preMove, requested, captured)
                                        || replacement.state().phase() != AirlockState.DoorPhase.CLOSED)
                                    throw new GameTestAssertException("captured contact must not retarget a same-state replacement controller");

                                var beforeRemoved = mob.getBoundingBox();
                                var priorSecond = movement.sable$getCollisionInfo();
                                mob.move(MoverType.SELF, requested);
                                var secondContact = movement.sable$getCollisionInfo();
                                if (secondContact == null || secondContact == priorSecond
                                        || currentContacts(mob).isEmpty())
                                    throw new GameTestAssertException("replacement root did not produce a fresh second native contact");
                                fixture.plot().close();
                                if (AirlockBumpSystem.afterNativeMove(mob, beforeRemoved, requested, secondContact))
                                    throw new GameTestAssertException("removed frame must refuse its captured native contact");
                                mob.discard();
                                helper.succeed();
                            });
                });
    }

    @GameTestTags({"shuttle-airlock-bump", "airlock_bump", "airlock_seam"})
    @GameTest(template = "atmos_large_empty", templateNamespace = "moonstation14", timeoutTicks = 900,
            batch = "shuttle_native_airlock_bump")
    public static void capturedSeamNomineesCannotRetargetReplacedNeighborRoot(GameTestHelper helper) {
        MovingCabinAuthorityFixture.start(helper, new BlockPos(1200, 300, 1120), "stale-native-seam-root",
                3.5, 1, 3.5, (fixture, unusedOwnedHuman) -> {
                    var level = fixture.level();
                    BlockPos root = fixture.cabin().at(com.juicyslew.moonstation14.ms14.shuttle.fixture.ShuttleCabinFixture.AIRLOCK);
                     BlockPos neighborRoot = root.east();
                     if (!(level.getBlockEntity(root) instanceof AirlockBlockEntity original))
                         throw new GameTestAssertException("seam replacement original controller missing: " + root + " "
                                 + level.getBlockState(root) + "/" + level.getBlockEntity(root));
                     AirlockBlockEntity neighbor = authorPaidSeamNeighbor(level, fixture, neighborRoot);
                     ServerSubLevel frame = fixture.plot().subLevel();
                     PowerFixtureReadiness.await(helper, level, 160, () -> {
                                   return fullyPaid20(level, original) && fullyPaid20(level, neighbor);
                             }, () -> "both live seam roots need fresh paid 20 W grants: original="
                                     + PowerRuntime.airlockPowerDiagnostic(level, original) + " neighbor="
                                     + PowerRuntime.airlockPowerDiagnostic(level, neighbor), () -> {
                                 var actor = spawnExteriorHumanHost(level, frame, fixture.scene(),
                                         new Vector3d(root.getX() + 1.0, root.getY() - 1, root.getZ() - 1.4),
                                         "seam replacement");
                                 var movement = (EntityMovementExtension) (Object) actor;
                                 class Capture {
                                     void step(int tick, long started) {
                                         if (tick >= 8 || helper.getTick() - started >= 16)
                                             throw new GameTestAssertException("bounded seam travel missed simultaneous native nominees: " + currentContacts(actor));
                                         helper.runAfterDelay(1, () -> {
                                             Vector3d target = new Vector3d(root.getX() + 1.0, root.getY() - 1, root.getZ() + .5);
                                             frame.logicalPose().transformPosition(target);
                                             Vec3 requested = new Vec3(target.x - actor.getX(), 0, target.z - actor.getZ()).normalize().scale(.2);
                                             Object prior = movement.sable$getCollisionInfo();
                                             var beforeMove = actor.getBoundingBox();
                                             actor.move(MoverType.SELF, requested);
                                             Object current = movement.sable$getCollisionInfo();
                                             var nominees = current == null ? java.util.List.<NativeAirlockContact>of()
                                                     : ((NativeAirlockContactAccess) (Object) current).moonstation14$airlockContacts();
                                             boolean both = nominees.stream().anyMatch(contact -> contact.frame() == frame && contact.rootPos().equals(root)
                                                     && contact.rootControllerIdentity().equals(original.identity()))
                                                     && nominees.stream().anyMatch(contact -> contact.frame() == frame && contact.rootPos().equals(neighborRoot)
                                                     && contact.rootControllerIdentity().equals(neighbor.identity()));
                                             if (current == null || current == prior)
                                                 throw new GameTestAssertException("direct seam movement produced no fresh actual native result");
                                              if (both) finish((SubLevelEntityCollision.CollisionInfo) current, beforeMove, requested, nominees);
                                             else step(tick + 1, started);
                                         });
                                     }
                                      void finish(SubLevelEntityCollision.CollisionInfo captured, net.minecraft.world.phys.AABB before, Vec3 requested,
                                                 java.util.List<NativeAirlockContact> contacts) {
                                var firstContact = contacts.stream().filter(contact -> contact.frame() == frame
                                                && contact.rootPos().equals(root)
                                                && contact.rootControllerIdentity().equals(original.identity()))
                                        .findFirst().orElseThrow(() -> new GameTestAssertException(
                                                "real seam native result omitted the first root/controller nominee: " + contacts));
                                var neighborContact = contacts.stream().filter(contact -> contact.frame() == frame
                                                && contact.rootPos().equals(neighborRoot)
                                                && contact.rootControllerIdentity().equals(neighbor.identity()))
                                        .findFirst().orElseThrow(() -> new GameTestAssertException(
                                                "real seam native result omitted neighbor root/controller nominee: " + contacts));
                                if (firstContact.rootControllerIdentity().equals(neighborContact.rootControllerIdentity()))
                                    throw new GameTestAssertException("seam nominees must retain distinct exact root identities");

                                 if (original.state().phase() != AirlockState.DoorPhase.CLOSED
                                         || neighbor.state().phase() != AirlockState.DoorPhase.CLOSED)
                                     throw new GameTestAssertException("both seam roots must remain closed until captured nominees are consumed");
                                 var oldLower = level.getBlockState(neighborRoot);
                                var oldUpper = level.getBlockState(neighborRoot.above());
                                level.setBlock(neighborRoot, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), 3);
                                level.setBlock(neighborRoot.above(), net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), 3);
                                level.setBlock(neighborRoot, oldLower, 3);
                                level.setBlock(neighborRoot.above(), oldUpper, 3);
                                 if (!(level.getBlockEntity(neighborRoot) instanceof AirlockBlockEntity replacement)
                                         || replacement.identity().equals(neighbor.identity()))
                                     throw new GameTestAssertException("same-state paid neighbor replacement did not change root identity");
                                 PowerFixtureReadiness.await(helper, level, 160,
                                         () -> {
                                             var meter = PowerRuntime.airlockPower(level, replacement);
                                              return meter.known() && meter.deliveredWatts() == 20
                                                      && replacement.prototype() != null
                                                      && meter.deliveredWatts() == replacement.prototype().apcPowerReceiver().requestedWatts()
                                                      && fullyPaid20(level, original);
                                          }, () -> "same-state replacement lacks its own fresh paid 20 W grant: "
                                                  + PowerRuntime.airlockPowerDiagnostic(level, replacement) + " original="
                                                  + PowerRuntime.airlockPowerDiagnostic(level, original), () -> {
                                          boolean activated = AirlockBumpSystem.afterNativeMove(actor, before, requested, captured);
                                             if (!activated || original.state().phase() != AirlockState.DoorPhase.OPENING
                                                     || replacement.state().phase() != AirlockState.DoorPhase.CLOSED)
                                                 throw new GameTestAssertException("captured seam may open only the still-owned original; replacement must remain closed: "
                                                         + original.state() + " / " + replacement.state() + " contacts=" + contacts);
                                              actor.discard();
                                              helper.succeed();
                                          });
                                      }
                                  }
                                 var capture = new Capture();
                                 capture.step(0, helper.getTick());
                            });
                });
    }

    @GameTestTags({"shuttle-airlock-bump", "airlock_bump", "movement"})
    @GameTest(template = "atmos_large_empty", templateNamespace = "moonstation14", timeoutTicks = 850,
            batch = "shuttle_native_airlock_bump")
    public static void emptyWorldRouteDoesNotResetContinuousFrameContactMemo(GameTestHelper helper) {
        MovingCabinAuthorityFixture.start(helper, new BlockPos(1000, 300, 920), "frame-bump-memo",
                3.5, 1, 3.5, (fixture, unusedOwnedHuman) -> {
                    var level = fixture.level();
                    BlockPos root = fixture.cabin().at(com.juicyslew.moonstation14.ms14.shuttle.fixture.ShuttleCabinFixture.AIRLOCK);
                    if (!(level.getBlockEntity(root) instanceof AirlockBlockEntity door))
                        throw new GameTestAssertException("memo fixture airlock root missing");
                    if (!door.applyWire(door.identity(), door.state().revision(), AirlockState.Wire.POWER_ONE,
                            AirlockState.WireAction.CUT)
                            || !door.applyWire(door.identity(), door.state().revision(), AirlockState.Wire.BOLT,
                            AirlockState.WireAction.CUT))
                        throw new GameTestAssertException("memo fixture failed to enable electrification and raise bolts");
                     PowerFixtureReadiness.await(helper, level, 140, () -> {
                                 var meter = PowerRuntime.airlockPower(level, door);
                                 return door.electrificationState().enabled() && door.state().boltsDown()
                                         && meter.known() && meter.deliveredWatts() == 20;
                             }, () -> "electrified bolted door lacks its paid 20 W grant: "
                                     + PowerRuntime.airlockPowerDiagnostic(level, door), () -> {
                                ServerSubLevel frame = fixture.plot().subLevel();
                                 var mob = spawnDeckHumanHost(level, frame, fixture.scene(),
                                          new Vector3d(root.getX() + .5, root.getY(), root.getZ() + 1.4), "memo");
                                float initialHealth = mob.getHealth();
                                   driveScheduledNativeTravel(helper, mob, frame, root, 0, helper.getTick(), mob.position(),
                                          () -> fullyPaid20(level, door) && door.state().phase() == AirlockState.DoorPhase.CLOSED
                                                  && door.state().boltsDown(),
                                          () -> PowerRuntime.airlockPowerDiagnostic(level, door), () -> {
                                    float afterFirst = mob.getHealth();
                                    if (Math.abs((initialHealth - afterFirst) - 1.4f) > .01f
                                            || door.state().phase() != AirlockState.DoorPhase.CLOSED
                                            || !door.state().boltsDown())
                                        throw new GameTestAssertException("first real contact must shock once while bolts retain CLOSED: health="
                                                + initialHealth + "→" + afterFirst + " state=" + door.state());
                                    long sameRootRevision = door.state().revision();
                                    Object frameMemoBeforeEmptyWorldRoute = frameContactMemo(mob, door.identity());
                                    if (frameMemoBeforeEmptyWorldRoute == null)
                                        throw new GameTestAssertException("native bump contact did not install its frame-root memo");
                                      Vector3d doorCenter = frame.logicalPose().transformPosition(new Vector3d(root.getX() + .5,
                                              root.getY() + .5, root.getZ() + .5));
                                      Vec3 towardDoor = new Vec3(doorCenter.x - mob.getX(), 0, doorCenter.z - mob.getZ()).normalize();
                                      Vec3 tangent = new Vec3(1, 0, 0);
                                      Vec3 localStep = towardDoor.scale(.02).add(tangent.scale(.01));
                                      Vector3d ordinaryStep = frame.logicalPose().transformPosition(new Vector3d(localStep.x, 0, localStep.z));
                                     Vector3d ordinaryOrigin = frame.logicalPose().transformPosition(new Vector3d(0, 0, 0));
                                     Vec3 ordinaryRequest = new Vec3(ordinaryStep.x - ordinaryOrigin.x, 0,
                                             ordinaryStep.z - ordinaryOrigin.z);
                                     if (!AirlockBumpSystem.beforeMoveContacts(mob, ordinaryRequest).isEmpty())
                                         throw new GameTestAssertException("ordinary-world approach unexpectedly nominated native door");
                                     mob.move(MoverType.SELF, ordinaryRequest);
                                    if (door.state().revision() != sameRootRevision || mob.getHealth() != afterFirst)
                                        throw new GameTestAssertException("empty ordinary-world route changed frame memo/effects");
                                    Object frameMemoAfterEmptyWorldRoute = frameContactMemo(mob, door.identity());
                                    if (!java.util.Objects.equals(frameMemoBeforeEmptyWorldRoute, frameMemoAfterEmptyWorldRoute))
                                        throw new GameTestAssertException("empty world route cleared or changed the still-active frame-root memo");
                                     boolean stillTouchingNativeDoor = currentContacts(mob).stream().anyMatch(contact ->
                                             contact.frame().getUniqueId().equals(frame.getUniqueId())
                                                     && (contact.storagePos().equals(root)
                                                     || contact.storagePos().equals(root.above())));
                                    if (!stillTouchingNativeDoor || mob.getHealth() != afterFirst
                                            || door.state().revision() != sameRootRevision)
                                        throw new GameTestAssertException("continuous contact must remain deduplicated at the same root/revision");
                                    mob.discard();
                                    helper.succeed();
                                });
                            });
                });
    }

    @GameTestTags({"shuttle-airlock-bump", "airlock_bump", "movement"})
    @GameTest(template = "atmos_large_empty", templateNamespace = "moonstation14", timeoutTicks = 900,
            batch = "shuttle_native_airlock_bump")
    public static void failedUnpoweredContactLeavesAndPaidRecontactRetriesSameFrameRoot(GameTestHelper helper) {
        MovingCabinAuthorityFixture.start(helper, new BlockPos(1040, 300, 960), "native-airlock-power-rearm",
                3.5, 1, 3.5, (fixture, unusedOwnedHuman) -> {
                    var level = fixture.level();
                    BlockPos root = fixture.cabin().at(com.juicyslew.moonstation14.ms14.shuttle.fixture.ShuttleCabinFixture.AIRLOCK);
                    if (!(level.getBlockEntity(root) instanceof AirlockBlockEntity door))
                        throw new GameTestAssertException("power-rearm airlock root missing");
                    if (!door.applyWire(door.identity(), door.state().revision(), AirlockState.Wire.POWER_ONE,
                            AirlockState.WireAction.CUT)
                            || !door.applyWire(door.identity(), door.state().revision(), AirlockState.Wire.POWER_TWO,
                            AirlockState.WireAction.CUT))
                        throw new GameTestAssertException("power-rearm fixture failed to disable its receiver");
                    ServerSubLevel frame = fixture.plot().subLevel();
                     var mob = spawnExteriorHumanHost(level, frame, fixture.scene(),
                             new Vector3d(root.getX() + .5, root.getY() - 1, root.getZ() - 1.4), "power-rearm");
                       driveScheduledNativeTravel(helper, mob, frame, root, 0, helper.getTick(), mob.position(),
                              () -> powerKnownAt(level, door, 0) && door.state().phase() == AirlockState.DoorPhase.CLOSED,
                              () -> PowerRuntime.airlockPowerDiagnostic(level, door), () -> {
                        if (door.state().phase() != AirlockState.DoorPhase.CLOSED)
                            throw new GameTestAssertException("unpaid native contact must not open the door");
                        if (frameContactMemo(mob, door.identity()) == null)
                            throw new GameTestAssertException("unpaid contact was not retained while actor stayed at root");
                         leaveUnpaidContact(helper, mob, frame, root, door, 0, helper.getTick(),
                                 ((EntityMovementExtension) (Object) mob).sable$getCollisionInfo());
                     });
                 });
     }

     private static void leaveUnpaidContact(GameTestHelper helper, Mob mob, ServerSubLevel frame, BlockPos root,
                                             AirlockBlockEntity door, int tick, long started, Object prior) {
         if (tick >= 8 || helper.getTick() - started >= 12)
             throw new GameTestAssertException("normal backward travel did not leave unpaid contact: " + travelDiagnostic(mob, frame, root));
          helper.runAfterDelay(1, () -> {
              Vector3d doorCenter = frame.logicalPose().transformPosition(new Vector3d(root.getX()+.5, root.getY()-1, root.getZ()+.5));
              Vec3 outward = new Vec3(mob.getX()-doorCenter.x, 0, mob.getZ()-doorCenter.z).normalize();
              if (outward.lengthSqr() == 0) throw new GameTestAssertException("actor is not on an outward side of door");
              Vec3 before = mob.position();
              prepareFixtureTravel(mob, "unpaid-contact normal backward departure");
              mob.travel(new Vec3(0, 0, 1));
              if (new Vec3(mob.getX()-before.x, 0, mob.getZ()-before.z).dot(outward) <= 0)
                  throw new GameTestAssertException("normal backward travel did not move outward: " + travelDiagnostic(mob, frame, root));
             Object fresh = ((EntityMovementExtension) (Object) mob).sable$getCollisionInfo();
             if (fresh == null || fresh == prior) throw new GameTestAssertException("backward travel produced no fresh native result");
             if (currentContacts(mob).isEmpty() && frameContactMemo(mob, door.identity()) == null) {
                 if (door.state().phase() != AirlockState.DoorPhase.CLOSED) throw new GameTestAssertException("unpowered departure changed closed phase");
                  if (!door.applyWire(door.identity(), door.state().revision(), AirlockState.Wire.POWER_ONE,
                          AirlockState.WireAction.MEND)
                          || !door.applyWire(door.identity(), door.state().revision(), AirlockState.Wire.POWER_TWO,
                          AirlockState.WireAction.MEND))
                      throw new GameTestAssertException("power-rearm fixture failed to restore both receiver wires");
                  var level = (net.minecraft.server.level.ServerLevel) mob.level();
                  PowerFixtureReadiness.await(helper, level, 140, () -> fullyPaid20(level, door),
                          () -> "restored receiver did not regain a fresh paid 20 W grant: "
                                  + PowerRuntime.airlockPowerDiagnostic(level, door),
                          () -> driveScheduledNativeTravel(helper, mob, frame, root, 0, helper.getTick(), mob.position(),
                                  () -> fullyPaid20(level, door) && door.state().phase() == AirlockState.DoorPhase.CLOSED
                                          && !door.state().boltsDown(),
                                  () -> PowerRuntime.airlockPowerDiagnostic(level, door), () -> {
                                      if (door.state().phase() != AirlockState.DoorPhase.OPENING)
                                          throw new GameTestAssertException("same-root paid recontact did not retry the quiet opening gate");
                                      if (currentContacts(mob).stream().noneMatch(contact ->
                                              contact.frame().getUniqueId().equals(frame.getUniqueId())
                                                      && contact.rootPos().equals(root)))
                                          throw new GameTestAssertException("paid recontact lost exact native frame/root identity");
                                      mob.discard();
                                      helper.succeed();
                                  }));
              } else leaveUnpaidContact(helper, mob, frame, root, door, tick+1, started, fresh);
          });
      }

    private static void driveScheduledNativeTravel(GameTestHelper helper, net.minecraft.world.entity.LivingEntity actor,
            ServerSubLevel frame, BlockPos root, int tick, long startTick, Vec3 initialPosition,
            java.util.function.BooleanSupplier paidReady,
            java.util.function.Supplier<String> readinessDiagnostic, Runnable contacted) {
        driveScheduledNativeTravel(helper, actor, frame, root, root.getX() + .5, tick, startTick, initialPosition,
                paidReady, readinessDiagnostic, contacted);
    }

    private static void driveScheduledNativeTravel(GameTestHelper helper, net.minecraft.world.entity.LivingEntity actor,
            ServerSubLevel frame, BlockPos root, double targetLocalX, int tick, long startTick, Vec3 initialPosition,
            java.util.function.BooleanSupplier paidReady,
            java.util.function.Supplier<String> readinessDiagnostic, Runnable contacted) {
        if (actor.isRemoved() || helper.testInfo.isDone()) return;
        if (tick >= 16 || helper.getTick() - startTick >= 32)
            throw new GameTestAssertException("bounded real travel missed native airlock contact: "
                + travelDiagnostic(actor, frame, root) + " readiness=" + readinessDiagnostic.get());
        helper.runAfterDelay(1, () -> {
            if (actor.isRemoved() || helper.testInfo.isDone()) return;
            long remaining = 32 - (helper.getTick() - startTick);
            if (remaining <= 0) throw new GameTestAssertException("bounded paid-contact readiness expired: " + readinessDiagnostic.get());
            PowerFixtureReadiness.await(helper, (net.minecraft.server.level.ServerLevel) actor.level(), remaining,
                    paidReady, readinessDiagnostic, () -> {
                        if (actor.isRemoved() || helper.testInfo.isDone()) return;
                        Vector3d target = new Vector3d(targetLocalX, root.getY() - 1, root.getZ() + .5);
                        frame.logicalPose().transformPosition(target);
                        Vec3 toward = new Vec3(target.x - actor.getX(), 0, target.z - actor.getZ());
                        if (toward.lengthSqr() < 1.0E-8)
                            throw new GameTestAssertException("native frame approach direction is degenerate");
                        actor.setYRot((float) Math.toDegrees(Math.atan2(toward.x, -toward.z)));
                        prepareFixtureTravel((Mob) actor, "native contact tick " + tick);
                        Object previous = ((EntityMovementExtension) (Object) actor).sable$getCollisionInfo();
                        actor.travel(new Vec3(0, 0, -1));
                        boolean contact = currentContacts(actor).stream().anyMatch(candidate ->
                                candidate.frame().getUniqueId().equals(frame.getUniqueId())
                                        && (candidate.storagePos().equals(root) || candidate.storagePos().equals(root.above())));
                        Object current = ((EntityMovementExtension) (Object) actor).sable$getCollisionInfo();
                        if (actor.isRemoved() || helper.testInfo.isDone()) return;
                        if (current == null || current == previous)
                            throw new GameTestAssertException("effective LivingEntity.travel produced no fresh native result: noAI="
                                    + ((Mob) actor).isNoAi() + " effectiveAI=" + actor.isEffectiveAi() + " prior=" + previous
                                    + " current=" + current + " requested=(0,0,-1) " + travelDiagnostic(actor, frame, root));
                        if (contact && current != null && current != previous
                                && actor.position().distanceToSqr(initialPosition) > 1.0E-4) contacted.run();
                        else driveScheduledNativeTravel(helper, actor, frame, root, targetLocalX, tick + 1, startTick, initialPosition,
                                paidReady, readinessDiagnostic, contacted);
                    });
        });
    }

    private static String travelDiagnostic(net.minecraft.world.entity.LivingEntity actor, ServerSubLevel frame, BlockPos root) {
        Vector3d local = frame.logicalPose().transformPositionInverse(new Vector3d(actor.getX(), actor.getY(), actor.getZ()));
        var level = actor.level() instanceof net.minecraft.server.level.ServerLevel server ? server : null;
        var door = level != null && level.getBlockEntity(root) instanceof AirlockBlockEntity airlock ? airlock : null;
        return "world=" + actor.position() + " localFeet=" + local + " requested=(0,0,-1) contacts="
                + currentContacts(actor) + " meter=" + (door == null ? "<unknown>" : PowerRuntime.airlockPower(level, door))
                + " powerDiagnostic=" + (door == null ? "<unknown>" : PowerRuntime.airlockPowerDiagnostic(level, door));
    }

    private static Object frameContactMemo(net.minecraft.world.entity.Entity actor, java.util.UUID root) {
        try {
            var field = AirlockBumpSystem.class.getDeclaredField("LAST_FRAME_CONTACT");
            field.setAccessible(true);
            Object actorMemos = ((java.util.Map<?, ?>) field.get(null)).get(actor);
            return actorMemos instanceof java.util.Map<?, ?> memos ? memos.get(root) : null;
        } catch (ReflectiveOperationException failure) {
            throw new GameTestAssertException("frame contact memo inspection failed: " + failure);
        }
    }

    private static void startNativeTravelContact(GameTestHelper helper, MovingCabinAuthorityFixture.Ready fixture,
                                                  AirlockBlockEntity door, BlockPos root) {
        var level = fixture.level();
        ServerSubLevel frame = fixture.plot().subLevel();
        var mob = spawnExteriorHumanHost(level, frame, fixture.scene(),
                new Vector3d(root.getX() + .5, root.getY() - 1, root.getZ() - 1.4), "native-frame bump");
         requireClosed(door);
        var initialMeter = PowerRuntime.airlockPower(level, door);
        if (!initialMeter.known() || initialMeter.deliveredWatts() != 20 || door.prototype() == null
                || initialMeter.deliveredWatts() != door.prototype().apcPowerReceiver().requestedWatts())
            throw new GameTestAssertException("real travel approach requires fresh fully-paid 20 W hatch: " + initialMeter
                    + " diagnostic=" + PowerRuntime.airlockPowerDiagnostic(level, door));
          driveScheduledNativeTravel(helper, mob, frame, root, 0, helper.getTick(), mob.position(),
                 () -> fullyPaid20(level, door) && door.state().phase() == AirlockState.DoorPhase.CLOSED
                         && !door.state().boltsDown(),
                 () -> PowerRuntime.airlockPowerDiagnostic(level, door), () -> {
             var contacts = currentContacts(mob);
             if (door.state().phase() != AirlockState.DoorPhase.OPENING
                     || contacts.isEmpty() || contacts.stream().noneMatch(contact ->
                     contact.frame().getUniqueId().equals(frame.getUniqueId())
                             && (contact.storagePos().equals(root) || contact.storagePos().equals(root.above()))))
                 throw new GameTestAssertException("paid travel did not open from a fresh exact native nominee: " + travelDiagnostic(mob, frame, root));
            mob.discard();
            helper.succeed();
        });
    }

    private static void exerciseRotatedJambMiss(GameTestHelper helper, MovingCabinAuthorityFixture.Ready fixture,
                                                 AirlockBlockEntity door, BlockPos root) {
        var level = fixture.level();
        ServerSubLevel frame = fixture.plot().subLevel();
        Vector3d feet = new Vector3d(root.getX() - .35, root.getY() - 1, root.getZ() - 1.4);
        Vector3d target = new Vector3d(root.getX() - .35, root.getY() - 1, root.getZ() - .4);
        frame.logicalPose().transformPosition(target);
        var mob = spawnExteriorHumanHost(level, frame, fixture.scene(), feet, "rotated-jamb");
        CharacterIdentitySystem.enrollSupportedActor(mob, level);
         Vec3 worldPosition = mob.position();
         Vector3d travel = new Vector3d(target).sub(worldPosition.x, worldPosition.y, worldPosition.z).mul(2.0);
        var previous = ((EntityMovementExtension) (Object) mob).sable$getCollisionInfo();
        Vec3 before = mob.position();
        mob.setDeltaMovement(new Vec3(travel.x, 0, travel.z));
        prepareFixtureTravel(mob, "rotated jamb negative approach");
        mob.travel(Vec3.ZERO);
        var fresh = ((EntityMovementExtension) (Object) mob).sable$getCollisionInfo();
        if (fresh == null || fresh == previous || mob.position().distanceToSqr(before) < 1.0E-4)
            throw new GameTestAssertException("effective rotated-jamb travel lacked fresh movement evidence: noAI="
                    + mob.isNoAi() + " effectiveAI=" + mob.isEffectiveAi() + " prior=" + previous
                    + " fresh=" + fresh + " before=" + before + " after=" + mob.position());
        boolean selectedDoor = currentContacts(mob).stream().anyMatch(contact ->
                contact.frame().getUniqueId().equals(frame.getUniqueId())
                        && (contact.storagePos().equals(root) || contact.storagePos().equals(root.above())));
        if (selectedDoor || door.state().phase() != AirlockState.DoorPhase.CLOSED)
            throw new GameTestAssertException("rotated broadphase/jamb proximity must not become a leaf contact");
        mob.discard();
    }

    private static java.util.List<com.juicyslew.moonstation14.ms14.doors.NativeAirlockContact> currentContacts(
            net.minecraft.world.entity.Entity actor) {
        EntityMovementExtension movement = (EntityMovementExtension) (Object) actor;
        var info = movement.sable$getCollisionInfo();
        return info == null ? java.util.List.of()
                : ((NativeAirlockContactAccess) (Object) info).moonstation14$airlockContacts();
    }

    private static void requireOrdinaryGround(net.minecraft.world.entity.LivingEntity actor, String label,
            ServerSubLevel frame) {
        requireOrdinaryGround(actor, label, frame, actor.getY());
    }

    private static boolean fullyPaid20(net.minecraft.server.level.ServerLevel level, AirlockBlockEntity door) {
        var meter = PowerRuntime.airlockPower(level, door);
        return meter.known() && meter.deliveredWatts() == 20 && door.prototype() != null
                && meter.deliveredWatts() == door.prototype().apcPowerReceiver().requestedWatts();
    }

    private static boolean powerKnownAt(net.minecraft.server.level.ServerLevel level, AirlockBlockEntity door, int watts) {
        var meter = PowerRuntime.airlockPower(level, door);
        return meter.known() && meter.deliveredWatts() == watts;
    }

    private static void requireOrdinaryGround(net.minecraft.world.entity.LivingEntity actor, String label,
            ServerSubLevel frame, double terrainY) {
        if (actor.getBoundingBox().minY < terrainY - 1.0E-6) {
            actor.discard();
            throw new GameTestAssertException(label + " initially penetrated authored terrain: feet="
                    + actor.position() + " terrainY=" + terrainY + " bounds=" + actor.getBoundingBox());
        }
        double beforeY = actor.getY();
        actor.move(MoverType.SELF, new Vec3(0, -.2, 0));
        if (!actor.onGround() || Math.abs(actor.getY() - beforeY) > 1.0E-6) {
            actor.discard();
            throw new GameTestAssertException(label + " downward collision probe found no ordinary support: world="
                    + actor.position() + " expectedTerrainY=" + terrainY + " frame=" + frame.logicalPose());
        }
    }

    /** Author the actual adjacent seam controller with the same lower/upper cell rules used by the cabin fixture. */
    private static AirlockBlockEntity authorPaidSeamNeighbor(net.minecraft.server.level.ServerLevel level,
            MovingCabinAuthorityFixture.Ready fixture, BlockPos root) {
        BlockPos origin = fixture.cabin().origin();
        if (root.getX() < origin.getX() || root.getX() >= origin.getX()
                + com.juicyslew.moonstation14.ms14.shuttle.fixture.ShuttleCabinFixture.WIDTH
                || root.getY() < origin.getY() || root.getY() >= origin.getY()
                + com.juicyslew.moonstation14.ms14.shuttle.fixture.ShuttleCabinFixture.HEIGHT - 1
                || LoadedFrameRayQuery.knownServerBlock(level, fixture.plot().subLevel(), root) == null
                || LoadedFrameRayQuery.knownServerBlock(level, fixture.plot().subLevel(), root.above()) == null
                || level.getBlockEntity(root) != null || level.getBlockEntity(root.above()) != null)
            throw new GameTestAssertException("seam cells unavailable or already controller-owned: root=" + root
                    + " lower=" + level.getBlockState(root) + "/" + level.getBlockEntity(root)
                    + " upper=" + level.getBlockState(root.above()) + "/" + level.getBlockEntity(root.above()));
        var lower = com.juicyslew.moonstation14.block.ModBlocks.AIRLOCK.get().defaultBlockState();
        if (!level.setBlock(root, lower, 3)
                || !level.setBlock(root.above(), lower.setValue(AirlockBlock.HALF,
                net.minecraft.world.level.block.state.properties.DoubleBlockHalf.UPPER), 3))
            throw new GameTestAssertException("canonical seam lower/upper airlock writes failed: root=" + root);
        if (!(level.getBlockEntity(root) instanceof AirlockBlockEntity door) || door.prototype() == null)
            throw new GameTestAssertException("authored paid seam lower half lacks resolved controller: root=" + root
                    + " lower=" + level.getBlockState(root) + " upper=" + level.getBlockState(root.above()));
        return door;
    }

    private static void requireClosed(AirlockBlockEntity door) {
        if (door.state().phase() != AirlockState.DoorPhase.CLOSED || door.state().boltsDown())
            throw new GameTestAssertException("native bump fixture door must start closed/unbolted: " + door.state());
    }

    /** Test-only controlled travel scheduling; this is not a gameplay movement rule. */
    private static final class FixtureTravelVillager extends Villager {
        private boolean travelArmed;
        private FixtureTravelVillager(net.minecraft.world.level.Level level) {
            super(EntityType.VILLAGER, level);
        }

        private void armTravel() { travelArmed = true; }

        @Override public void travel(Vec3 input) {
            if (!travelArmed) return;
            travelArmed = false;
            super.travel(input);
        }

        @Override protected void customServerAiStep() {
            // Suppress Brain goals. Vanilla automatic travel is independently suppressed above;
            // scheduled test calls still execute the real Living/Villager travel implementation.
        }
    }
}
