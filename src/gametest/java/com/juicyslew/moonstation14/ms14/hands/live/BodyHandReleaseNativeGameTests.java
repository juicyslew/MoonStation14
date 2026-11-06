package com.juicyslew.moonstation14.ms14.hands.live;

import com.juicyslew.moonstation14.component.ModDataAttachments;
import com.juicyslew.moonstation14.gametest.support.GameTestTags;
import com.juicyslew.moonstation14.ms14.hands.ItemToken;
import com.juicyslew.moonstation14.ms14.shuttle.sable.MovingCabinAuthorityFixture;
import com.juicyslew.moonstation14.ms14.shuttle.sable.SableFrameVelocity;
import dev.ryanhcode.sable.api.physics.handle.RigidBodyHandle;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import org.joml.Vector3d;

import java.util.UUID;
import java.util.stream.Collectors;
import java.util.concurrent.atomic.AtomicReference;
import com.juicyslew.moonstation14.ms14.shuttle.fixture.ShuttleFixtureMotionService;
import com.juicyslew.moonstation14.ms14.shuttle.fixture.ShuttleMotionRequest;

/** Real controlled body release on a translating and rotating native cabin. */
@PrefixGameTestTemplate(false)
@GameTestTags({"hands-release-native-carrier"})
public final class BodyHandReleaseNativeGameTests {
    private BodyHandReleaseNativeGameTests() { }

    @GameTest(templateNamespace = "moonstation14", template = "empty", timeoutTicks = 220)
    @GameTestTags({"cargo-drop-wall-clearance", "hands-release-native-carrier"})
    public static void wideCargoCannotCommitIntoWorldWallAtFallback(GameTestHelper helper) {
        MovingCabinAuthorityFixture.start(helper, new BlockPos(2540, 300, 640), "cargo-world-wall",
                2.1875, 1, 2.5, (fixture, body) -> {
                    var actor = fixture.owner().actor();
                    var owner = fixture.owner();
                    long viewSequence = owner.viewTo(body, body.getEyePosition().add(0, 0, 3));
                    Vec3 expectedLook = new Vec3(0, 0, 1);
                    com.juicyslew.moonstation14.gametest.support.BoundedGameTestWait.await(helper, 40,
                            () -> "world clearance view not acknowledged: seq=" + viewSequence + " applied="
                                    + owner.drainAppliedSequence() + " look=" + body.getLookAngle()
                                    + " frame=" + LoadedFrameRayQuery.currentActorPoint(fixture.level(), body),
                            () -> owner.drainAppliedSequence() >= viewSequence
                                    && body.getLookAngle().dot(expectedLook) >= .999,
                            () -> {
                    LiveHands hands = body.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get());
                    ItemStack cargo = wideReagentCargo();
                    var profile = com.juicyslew.moonstation14.ms14.loose_objects.LooseObjectProfile
                            .resolveForStack(cargo, false);
                    if (profile.physicalWidth() <= body.getBbWidth())
                        throw new GameTestAssertException("fixture cargo must be wider than the body: " + profile);
                    int wallX = net.minecraft.util.Mth.ceil(body.getBoundingBox().maxX);
                    int minY = net.minecraft.core.BlockPos.containing(body.position()).getY();
                    var fallbackBox = new net.minecraft.world.phys.AABB(body.getX() - profile.physicalWidth() / 2,
                            body.getY(), body.getZ() - profile.physicalWidth() / 2,
                            body.getX() + profile.physicalWidth() / 2, body.getY() + profile.physicalHeight(),
                            body.getZ() + profile.physicalWidth() / 2);
                    var launchBox = new net.minecraft.world.phys.AABB(body.getX() - profile.physicalWidth() / 2,
                            body.getY() + 1.125, body.getZ() - profile.physicalWidth() / 2,
                            body.getX() + profile.physicalWidth() / 2, body.getY() + 1.125 + profile.physicalHeight(),
                            body.getZ() + profile.physicalWidth() / 2);
                    if (body.getBoundingBox().maxX > wallX || fallbackBox.maxX <= wallX || launchBox.maxX <= wallX)
                        throw new GameTestAssertException("world-wall geometry misses required body/cargo bounds: body="
                                + body.getBoundingBox() + " fallback=" + fallbackBox + " launch=" + launchBox + " wallX=" + wallX);
                    // Cover the whole 3.5 m aim ray plus cargo half-width, so a returned
                    // surface/air candidate cannot escape around the end of this wall.
                    int minZ = net.minecraft.util.Mth.floor(body.getZ() - 4.0);
                    int maxZ = net.minecraft.util.Mth.floor(body.getZ() + 4.0);
                    int maxY = net.minecraft.util.Mth.ceil(launchBox.maxY);
                    for (int y = minY; y < maxY; y++) for (int z = minZ; z <= maxZ; z++)
                        fixture.level().setBlock(new BlockPos(wallX, y, z),
                                net.minecraft.world.level.block.Blocks.STONE.defaultBlockState(), 3);
                    var wallBox = new net.minecraft.world.phys.AABB(wallX, minY, minZ, wallX + 1, maxY, maxZ + 1);
                    if (!fallbackBox.intersects(wallBox) || !launchBox.intersects(wallBox)
                            || body.getBoundingBox().intersects(wallBox))
                        throw new GameTestAssertException("world wall must hit cargo fallback/launch boxes but not body: " + wallBox);
                    String token = "wide-world-wall-" + UUID.randomUUID();
                    var stocked = hands.putWhole(hands.revision(), "left", new ItemToken(token), cargo);
                    if (!stocked.succeeded() || !LiveHandsCommitGateway.commit(actor, fixture.owner().authority(), stocked.state()))
                        throw new GameTestAssertException("could not stock wall-clearance cargo");
                    var before = body.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get());
                    var existing = fixture.level().getEntitiesOfClass(com.juicyslew.moonstation14.entities.LooseItemEntity.class,
                            body.getBoundingBox().inflate(4)).stream().map(net.minecraft.world.entity.Entity::getUUID).collect(Collectors.toSet());
                    var fallback = com.juicyslew.moonstation14.entities.LooseItemEntity.createForActor(body, body.position(), cargo.copy());
                    var candidate = com.juicyslew.moonstation14.entities.LooseItemEntity.createForActor(body,
                            body.position().add(0, 1.125, 0), cargo.copy());
                    var plan = BodyItemTargetProbe.resolveDrop(fixture.level(), body, actor,
                            profile.physicalWidth(), profile.physicalHeight());
                    boolean unsafePlan = false;
                    if (plan != null) {
                        var planned = com.juicyslew.moonstation14.entities.LooseItemEntity.createForActor(body,
                                plan.position(), cargo.copy());
                        unsafePlan = planned.getBoundingBox().intersects(wallBox);
                        if (!unsafePlan)
                            throw new GameTestAssertException("world wall did not cover returned candidate: " + plan
                                    + " plannedBox=" + planned.getBoundingBox() + " wall=" + wallBox);
                    }
                    if (BodyItemTargetProbe.loadedItemBoxClear(fixture.level(), fallback)
                            || BodyItemTargetProbe.loadedItemBoxClear(fixture.level(), candidate))
                        throw new GameTestAssertException("authored world wall must intersect fallback and launch physical bounds");
                    java.util.concurrent.atomic.AtomicReference<UUID> committed = new java.util.concurrent.atomic.AtomicReference<>();
                    var result = BodyHandItemTransfer.drop(actor, "left", token, before.revision(), committed::set);
                    var after = body.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get());
                    var created = fixture.level().getEntitiesOfClass(com.juicyslew.moonstation14.entities.LooseItemEntity.class,
                            body.getBoundingBox().inflate(4)).stream().filter(item -> !existing.contains(item.getUUID())).toList();
                    if (plan == null || unsafePlan) {
                        if (result != BodyHandItemTransfer.Result.DENIED || committed.get() != null
                                || after.revision() != before.revision() || after.token("left").filter(token::equals).isEmpty()
                                || after.stackCopy("left").filter(stack -> ItemStack.isSameItemSameComponents(cargo, stack)
                                        && stack.getCount() == cargo.getCount()).isEmpty() || !created.isEmpty())
                            throw new GameTestAssertException("world-wall fallback/intersecting plan must deny without changing hand/spawning: plan="
                                    + plan + " result=" + result + " revision=" + after.revision()
                                    + " hand=" + after.stackCopy("left") + " created=" + created.size());
                    } else if (result != BodyHandItemTransfer.Result.SUCCESS || created.size() != 1
                            || !created.get(0).getUUID().equals(committed.get())
                            || !ItemStack.isSameItemSameComponents(cargo, created.get(0).getItem())
                            || !BodyItemTargetProbe.loadedItemBoxClear(fixture.level(), created.get(0))
                            || after.revision() != before.revision() + 1 || after.token("left").isPresent()
                            || after.stackCopy("left").isPresent())
                        throw new GameTestAssertException("safe world-wall alternative must transfer exact cargo once: plan="
                                + plan + " result=" + result + " created=" + created.size());
                    helper.succeed();
                            });
                });
    }

    @GameTest(templateNamespace = "moonstation14", template = "empty", timeoutTicks = 220)
    @GameTestTags({"cargo-drop-wall-clearance", "hands-release-native-carrier"})
    public static void wideCargoCannotCommitIntoNativeFrameWallAtFallback(GameTestHelper helper) {
        MovingCabinAuthorityFixture.start(helper, new BlockPos(2640, 300, 640), "cargo-frame-wall",
                2.6875, 1, 2.5, (fixture, body) -> {
                    var actor = fixture.owner().actor();
                    var owner = fixture.owner();
                    var aimPoint = LoadedFrameRayQuery.currentActorPoint(fixture.level(), body);
                    if (aimPoint == null || !fixture.plot().engineId().equals(aimPoint.frameId()))
                        throw new GameTestAssertException("wall fixture body lacks exact native frame point");
                    var aimBasis = LoadedFrameRayQuery.frameOrientation(fixture.level(), aimPoint.frameId(),
                            aimPoint.storagePoint(), false, 1f);
                    if (aimBasis == null) throw new GameTestAssertException("native local +Z view basis unavailable");
                    var localForward = aimBasis.transform(new org.joml.Vector3d(0, 0, 1));
                    Vec3 aimedPoint = body.getEyePosition().add(localForward.x * 3, localForward.y * 3, localForward.z * 3);
                    long viewSequence = owner.viewTo(body, aimedPoint);
                    Vec3 expectedLook = new Vec3(localForward.x, localForward.y, localForward.z).normalize();
                    com.juicyslew.moonstation14.gametest.support.BoundedGameTestWait.await(helper, 40,
                            () -> "native clearance view not acknowledged: seq=" + viewSequence + " applied="
                                    + owner.drainAppliedSequence() + " look=" + body.getLookAngle()
                                    + " frame=" + LoadedFrameRayQuery.currentActorPoint(fixture.level(), body),
                            () -> owner.drainAppliedSequence() >= viewSequence
                                    && body.getLookAngle().dot(expectedLook) >= .999,
                            () -> {
                    var actorPoint = LoadedFrameRayQuery.currentActorPoint(fixture.level(), body);
                    if (actorPoint == null || !fixture.plot().engineId().equals(actorPoint.frameId()))
                        throw new GameTestAssertException("native clearance actor lost exact frame after acknowledged view: " + actorPoint);
                    var currentBasis = LoadedFrameRayQuery.frameOrientation(fixture.level(), actorPoint.frameId(),
                            actorPoint.storagePoint(), false, 1f);
                    var currentForward = currentBasis == null ? null : currentBasis.transform(new org.joml.Vector3d(0, 0, 1));
                    Vec3 actualExpectedLook = currentForward == null ? null
                            : new Vec3(currentForward.x, currentForward.y, currentForward.z).normalize();
                    if (actualExpectedLook == null || body.getLookAngle().dot(actualExpectedLook) < .999)
                        throw new GameTestAssertException("native local +Z aim/frame changed before geometry: look="
                                + body.getLookAngle() + " expected=" + actualExpectedLook + " point=" + actorPoint);
                    LiveHands hands = body.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get());
                    ItemStack cargo = wideReagentCargo();
                    var profile = com.juicyslew.moonstation14.ms14.loose_objects.LooseObjectProfile
                            .resolveForStack(cargo, false);
                    // Put the wall at the first storage cell beyond the body edge. The block-item
                    // profile's .65 m footprint crosses this boundary while the .6 m body clears it.
                    BlockPos wall = new BlockPos(net.minecraft.util.Mth.ceil(actorPoint.storagePoint().x + body.getBbWidth() / 2),
                            net.minecraft.util.Mth.floor(actorPoint.storagePoint().y),
                            net.minecraft.util.Mth.floor(actorPoint.storagePoint().z - 4));
                    int wallMaxZ = net.minecraft.util.Mth.floor(actorPoint.storagePoint().z + 4);
                    for (int y = wall.getY(); y < wall.getY() + 3; y++)
                        for (int z = wall.getZ(); z <= wallMaxZ; z++)
                            fixture.plot().setStorageBlock(new BlockPos(wall.getX(), y, z),
                                    net.minecraft.world.level.block.Blocks.STONE.defaultBlockState());
                    String token = "wide-frame-wall-" + UUID.randomUUID();
                    var stocked = hands.putWhole(hands.revision(), "left", new ItemToken(token), cargo);
                    if (!stocked.succeeded() || !LiveHandsCommitGateway.commit(actor, fixture.owner().authority(), stocked.state()))
                        throw new GameTestAssertException("could not stock frame-wall cargo");
                    var before = body.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get());
                    var existing = fixture.level().getEntitiesOfClass(com.juicyslew.moonstation14.entities.LooseItemEntity.class,
                            body.getBoundingBox().inflate(4)).stream().map(net.minecraft.world.entity.Entity::getUUID).collect(Collectors.toSet());
                    var fallback = com.juicyslew.moonstation14.entities.LooseItemEntity.createForActor(body, body.position(), cargo.copy());
                    var launch = com.juicyslew.moonstation14.entities.LooseItemEntity.createForActor(body,
                            body.position().add(0, 1.125, 0), cargo.copy());
                    if (BodyItemTargetProbe.loadedItemBoxClear(fixture.level(), fallback)
                            || BodyItemTargetProbe.loadedItemBoxClear(fixture.level(), launch))
                        throw new GameTestAssertException("native wall must obstruct fallback and launch cargo boxes");
                    int wallMaxY = wall.getY() + 3;
                    if (nativeWallIntersects(fixture, body, wall.getX(), wall.getZ(), wallMaxZ, wall.getY(), wallMaxY))
                        throw new GameTestAssertException("native wall must remain SAT-clear of body bounds; wall="
                                + wall + "..z" + wallMaxZ + " body=" + body.getBoundingBox());
                    if (!nativeWallIntersects(fixture, fallback, wall.getX(), wall.getZ(), wallMaxZ, wall.getY(), wallMaxY)
                            || !nativeWallIntersects(fixture, launch, wall.getX(), wall.getZ(), wallMaxZ, wall.getY(), wallMaxY))
                        throw new GameTestAssertException("native wall must SAT-intersect both actual fallback and launch cargo bounds; wall="
                                + wall + "..z" + wallMaxZ + " fallback=" + fallback.getBoundingBox()
                                + " launch=" + launch.getBoundingBox() + " point=" + actorPoint);
                    var plan = BodyItemTargetProbe.resolveDrop(fixture.level(), body, actor,
                            profile.physicalWidth(), profile.physicalHeight());
                    boolean unsafePlan = false;
                    if (plan != null) {
                        var planned = stagedCandidateForPlan(fixture, body, cargo, plan);
                        unsafePlan = nativeWallIntersects(fixture, planned, wall.getX(), wall.getZ(), wallMaxZ, wall.getY(), wallMaxY)
                                || !BodyItemTargetProbe.loadedItemBoxClear(fixture.level(), planned);
                    }
                    java.util.concurrent.atomic.AtomicReference<UUID> committed = new java.util.concurrent.atomic.AtomicReference<>();
                    var result = BodyHandItemTransfer.drop(actor, "left", token, before.revision(), committed::set);
                    var after = body.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get());
                    var created = fixture.level().getEntitiesOfClass(com.juicyslew.moonstation14.entities.LooseItemEntity.class,
                            body.getBoundingBox().inflate(4)).stream().filter(item -> !existing.contains(item.getUUID())).toList();
                    if (unsafePlan || plan == null) {
                        if (result != BodyHandItemTransfer.Result.DENIED || committed.get() != null
                                || after.revision() != before.revision()
                                || after.token("left").filter(token::equals).isEmpty()
                                || after.stackCopy("left").filter(stack -> ItemStack.isSameItemSameComponents(cargo, stack)
                                        && stack.getCount() == cargo.getCount()).isEmpty() || !created.isEmpty())
                            throw new GameTestAssertException("intersecting native-wall cargo must be denied with exact hand/no orphan: plan="
                                    + plan + " result=" + result + " revision=" + after.revision()
                                    + " hand=" + after.stackCopy("left") + " width=" + profile.physicalWidth()
                                    + " created=" + created.size());
                    } else {
                        if (result != BodyHandItemTransfer.Result.SUCCESS || after.revision() != before.revision() + 1
                                || after.token("left").isPresent() || after.stackCopy("left").isPresent() || created.size() != 1
                                || !created.get(0).getUUID().equals(committed.get())
                                || !ItemStack.isSameItemSameComponents(cargo, created.get(0).getItem())
                                || nativeWallIntersects(fixture, created.get(0), wall.getX(), wall.getZ(), wallMaxZ, wall.getY(), wallMaxY)
                                || !BodyItemTargetProbe.loadedItemBoxClear(fixture.level(), created.get(0)))
                            throw new GameTestAssertException("safe native-wall alternative must commit exactly once and remain clear: plan="
                                    + plan + " result=" + result + " hand=" + after.stackCopy("left") + " created=" + created.size());
                    }
                    helper.succeed();
                            });
                });
    }

    private static boolean nativeWallIntersects(MovingCabinAuthorityFixture.Ready fixture,
            net.minecraft.world.entity.Entity entity, int wallX, int minWallZ, int maxWallZ, int minWallY, int maxWallY) {
        var endpoint = LoadedFrameRayQuery.frameBoxEndpointQuery(fixture.level(), entity.getBoundingBox());
        var context = endpoint.contexts().stream().filter(frame -> fixture.plot().engineId().equals(frame.frameId()))
                .findFirst().orElse(null);
        if (!endpoint.complete() || context == null)
            throw new GameTestAssertException("native wall collision oracle lacks a complete current-pose context: " + entity.getBoundingBox());
        var plot = context.frame().getPlot();
        var storageBounds = new dev.ryanhcode.sable.companion.math.BoundingBox3d(plot.getBoundingBox()).toMojang().inflate(1.0);
        var getter = LoadedBlockGetter.forFullFrame(fixture.level(), plot, storageBounds, BodyItemTargetProbe.MAX_CHUNKS);
        var shapes = new com.juicyslew.moonstation14.ms14.shuttle.sable.NativeFrameShapeQuery(
                fixture.level(), entity, context.pose(), getter);
        for (int y = minWallY; y < maxWallY; y++) for (int z = minWallZ; z <= maxWallZ; z++) {
            BlockPos wall = new BlockPos(wallX, y, z);
            var read = context.read(wall);
            if (!read.known() || read.state() == null || !read.state().is(net.minecraft.world.level.block.Blocks.STONE))
                throw new GameTestAssertException("native wall oracle found unknown/nonwall authored storage: " + wall + " " + read);
            for (var shape : shapes.shape(wall, read.state()).toAabbs())
                if (com.juicyslew.moonstation14.ms14.shuttle.sable.FrameBoxIntersection.intersects(
                        entity.getBoundingBox(), new org.joml.Quaterniond(),
                        shape.move(wall.getX(), wall.getY(), wall.getZ()), context.pose())) return true;
        }
        return false;
    }

    private static com.juicyslew.moonstation14.entities.LooseItemEntity stagedCandidateForPlan(
            MovingCabinAuthorityFixture.Ready fixture, net.minecraft.world.entity.LivingEntity body,
            ItemStack stack, BodyItemTargetProbe.DropPlacement plan) {
        var item = com.juicyslew.moonstation14.entities.LooseItemEntity.createForActor(body, plan.position(), stack.copy());
        if (plan.surfacePlacement() && plan.supportFrameId() != null
                && !com.juicyslew.moonstation14.ms14.shuttle.sable.FrameAnchor.hydrate(fixture.level(), item,
                new com.juicyslew.moonstation14.ms14.shuttle.sable.FrameAnchor(plan.supportFrameId(), plan.supportStoragePoint())))
            throw new GameTestAssertException("could not reproduce production surface hydration for plan=" + plan);
        if (!item.prepareInitialFrameState(fixture.level()))
            throw new GameTestAssertException("could not reproduce production initial frame state for plan=" + plan);
        return item;
    }

    private static ItemStack wideReagentCargo() {
        ItemStack stack = new ItemStack(Items.STONE);
        var water = net.minecraft.resources.ResourceKey.create(
                com.juicyslew.moonstation14.ms14.reagent.ModReagents.REAGENT_REGISTRY_KEY,
                net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("moonstation14", "water"));
        stack.set(com.juicyslew.moonstation14.component.ModDataComponents.REAGENT.get(),
                new com.juicyslew.moonstation14.ms14.reagent.ReagentComponent(java.util.Map.of(water, 10f)));
        return stack;
    }

    @GameTest(templateNamespace = "moonstation14", template = "empty", timeoutTicks = 220)
    public static void translatingRotatingCabinDropCarriesSourcePointVelocityOnce(GameTestHelper helper) {
        MovingCabinAuthorityFixture.start(helper, new BlockPos(1640, 300, 640), "release-native-carrier",
                 2.5, 1, 2.5, (fixture, body) -> {
                     com.juicyslew.moonstation14.ms14.shuttle.sable.HullImpactSubstepsExpectation
                             .requireNormalSix(fixture.level(), "hands-stationary-canonical");
                    var actor = fixture.owner().actor();
                    var authority = fixture.owner().authority();
                    var owner = fixture.owner();
                    LiveHands hands = body.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get());
                    if (hands == null) throw new GameTestAssertException("fixture body missing resolved live hands");
                    ItemStack stack = new ItemStack(Items.STICK);
                    String token = "native-release-" + UUID.randomUUID();
                    LiveHands.WholeResult stocked = hands.putWhole(hands.revision(), "left", new ItemToken(token), stack);
                    if (!stocked.succeeded() || !LiveHandsCommitGateway.commit(actor, authority, stocked.state()))
                        throw new GameTestAssertException("could not stock authenticated fixture hand");

                    var handle = RigidBodyHandle.of(fixture.plot().subLevel());
                    var orientationBefore = new org.joml.Quaterniond(
                            fixture.plot().subLevel().logicalPose().orientation());
                    handle.addLinearAndAngularVelocity(new Vector3d(2.0, 0, .7), new Vector3d(0, .6, 0));
                    helper.runAfterDelay(10, () -> {
                        var source = LoadedFrameRayQuery.currentActorPoint(fixture.level(), body);
                        if (source == null || !fixture.plot().engineId().equals(source.frameId()))
                            throw new GameTestAssertException("real controlled body lost native tracking before release: " + source);
                        var profile = com.juicyslew.moonstation14.ms14.loose_objects.LooseObjectProfile
                                .resolveForStack(stack, false);
                         owner.viewTo(body, body.getEyePosition().add(0, 3, 0));
                         // Match the authenticated public drop route: its real actor carrier is excluded
                        // while probing placement, rather than comparing against a null-exclusion plan.
                        var placement = BodyItemTargetProbe.resolveDrop(fixture.level(), body, actor,
                                profile.physicalWidth(), profile.physicalHeight());
                         if (placement == null || placement.surfacePlacement())
                             throw new GameTestAssertException("AIR release control must resolve an air/fallback plan, not floor placement: " + placement);
                        var sourceVelocityPoint = LoadedFrameRayQuery.pointAtWorldPosition(fixture.level(),
                                source.frameId(), placement.position());
                        Vec3 carrier = SableFrameVelocity.pointVelocityAt(fixture.level(), sourceVelocityPoint);
                        if (carrier == null || carrier.length() < .15)
                            throw new GameTestAssertException("native cabin has no validated release-point carrier velocity: " + carrier);
                        Vec3 frameLinear = new Vec3(handle.getLinearVelocity(new Vector3d()).x,
                                handle.getLinearVelocity(new Vector3d()).y, handle.getLinearVelocity(new Vector3d()).z);
                        Vector3d angular = handle.getAngularVelocity(new Vector3d());
                        double rotationalPointContribution = carrier.distanceTo(frameLinear);
                        var orientationAfter = fixture.plot().subLevel().logicalPose().orientation();
                        double orientationChange = 1 - Math.abs(orientationBefore.dot(orientationAfter));
                        System.out.println("HAND_RELEASE_NATIVE_PREMISE frame=" + source.frameId()
                                + " tick=" + fixture.level().getServer().getTickCount() + " linear=" + frameLinear
                                + " point=" + carrier + " angular=" + angular + " rotationalPoint="
                                + rotationalPointContribution + " orientationChange=" + orientationChange);
                        if (angular.length() < 1.0e-8 || rotationalPointContribution < .05
                                || orientationChange < 1.0e-8)
                            throw new GameTestAssertException("fixture must prove angular point velocity, not translation alone: linear="
                                    + frameLinear + " point=" + carrier + " angular=" + angular
                                    + " orientationChange=" + orientationChange);
                        Vec3 own = body.getDeltaMovement();
                        Vec3 expectedInitial = HandReleaseKinematics.compose(own, carrier, placement.velocity());
                        if (expectedInitial == null) throw new GameTestAssertException("release velocity composition rejected finite points");
                        AtomicReference<UUID> committed = new AtomicReference<>();
                        var result = BodyHandItemTransfer.drop(actor, "left", token, stocked.state().revision(), committed::set);
                        if (result != BodyHandItemTransfer.Result.SUCCESS || committed.get() == null)
                            throw new GameTestAssertException("authenticated native-carrier hand drop failed: " + result);
                        var entity = fixture.level().getEntity(committed.get());
                        if (!(entity instanceof com.juicyslew.moonstation14.entities.LooseItemEntity loose))
                            throw new GameTestAssertException("release entity missing/wrong type: " + entity);
                        var airSnapshot = loose.orientationSnapshot();
                        var airWorldBasis = loose.authoritativeQueryOrientation(fixture.level());
                        if (airSnapshot == null || airSnapshot.frameId() != null || airWorldBasis == null
                                || 1 - Math.abs(airSnapshot.localBasis().dot(airWorldBasis)) > 1.0e-8)
                            throw new GameTestAssertException("AIR release must preserve coherent WORLD-basis orientation before first tick: snapshot="
                                    + airSnapshot + " world=" + airWorldBasis);
                        if (loose.position().distanceTo(placement.position()) > 1.0e-6)
                            throw new GameTestAssertException("authenticated release did not use the matching actor-excluded plan: planned="
                                    + placement.position() + " actual=" + loose.position());
                        var actualCarrierPoint = LoadedFrameRayQuery.pointAtWorldPosition(fixture.level(),
                                source.frameId(), loose.position());
                        Vec3 actualCarrier = SableFrameVelocity.pointVelocityAt(fixture.level(), actualCarrierPoint);
                        System.out.println("HAND_RELEASE_NATIVE_RELEASE frame=" + source.frameId()
                                + " tick=" + fixture.level().getServer().getTickCount() + " plannedPos=" + placement.position()
                                + " actualPos=" + loose.position() + " own=" + own + " carrierPlan=" + carrier
                                + " carrierActual=" + actualCarrier + " intent=" + placement.velocity()
                                + " expected=" + expectedInitial + " actual=" + loose.getDeltaMovement());
                        if (actualCarrier == null || loose.getDeltaMovement().distanceTo(expectedInitial) > 1.0e-5)
                            throw new GameTestAssertException("release must include body-own + SDK point carrier*.05 + placement intent exactly once; expected="
                                    + expectedInitial + " actual=" + loose.getDeltaMovement() + " carrierActual=" + actualCarrier);
                        awaitFirstFrameEntry(helper, fixture, loose, 0);
                    });
                });
    }

    @GameTest(templateNamespace = "moonstation14", template = "empty", timeoutTicks = 220)
    @GameTestTags({"hands-release-native-carrier", "hands-release-native-floor-placement"})
    public static void walkingBodyPlacesJugAtRestOnFastSpinningNativeFloor(GameTestHelper helper) {
        runFloorPlacement(helper, new BlockPos(1960, 300, 640), "release-native-floor", Mode.ORIGINAL);
    }

    @GameTest(templateNamespace = "moonstation14", template = "empty", timeoutTicks = 600)
    @GameTestTags({"cargo-placement-motion-cause-diagnostic"})
    public static void stationarySupportedCargoPlacementMotionCauseDiagnostic(GameTestHelper helper) {
        runFloorPlacement(helper, new BlockPos(2240, 300, 640), "cargo-still", Mode.STATIONARY);
    }

    @GameTest(templateNamespace = "moonstation14", template = "empty", timeoutTicks = 600)
    @GameTestTags({"cargo-placement-motion-cause-diagnostic"})
    public static void translatingSpinningSupportedCargoPlacementMotionCauseDiagnostic(GameTestHelper helper) {
        runFloorPlacement(helper, new BlockPos(2340, 300, 640), "cargo-move", Mode.MOVING);
    }

    enum Mode { ORIGINAL, STATIONARY, MOVING }

    private static void runFloorPlacement(GameTestHelper helper, BlockPos origin, String name, Mode mode) {
        CargoMotionRecorder recorder = mode == Mode.ORIGINAL ? null : new CargoMotionRecorder(mode);
        MovingCabinAuthorityFixture.start(helper, origin, name,
                2.5, 1, 2.5, (fixture, body) -> {
                    if (recorder != null) helper.testInfo.addListener(new net.minecraft.gametest.framework.GameTestListener() {
                        @Override public void testStructureLoaded(net.minecraft.gametest.framework.GameTestInfo i) { }
                        @Override public void testPassed(net.minecraft.gametest.framework.GameTestInfo i, net.minecraft.gametest.framework.GameTestRunner r) { recorder.dump("terminal result"); }
                        @Override public void testFailed(net.minecraft.gametest.framework.GameTestInfo i, net.minecraft.gametest.framework.GameTestRunner r) { recorder.dump("terminal failure"); }
                        @Override public void testAddedForRerun(net.minecraft.gametest.framework.GameTestInfo a, net.minecraft.gametest.framework.GameTestInfo b, net.minecraft.gametest.framework.GameTestRunner r) { recorder.dump("rerun"); }
                    });
                    var owner = fixture.owner();
                    var actor = owner.actor();
                    var authority = owner.authority();
                    var handle = RigidBodyHandle.of(fixture.plot().subLevel());
                    owner.setWish(0, 1000);
                    long walkingSequence = owner.neutralIntent(body);
                    com.juicyslew.moonstation14.gametest.support.BoundedGameTestWait.await(helper, 20,
                            () -> "fixed walking intent was not acknowledged / body did not acquire horizontal own motion: "
                                    + body.getDeltaMovement() + " applied=" + owner.drainAppliedSequence()
                                    + " walkingSequence=" + walkingSequence + " requested=" + owner.intentSequence().get(),
                            () -> {
                                var v = body.getDeltaMovement();
                                return owner.drainAppliedSequence() >= walkingSequence
                                        && new Vec3(v.x, 0, v.z).length() > .01;
                            }, () -> {
                        var before = LoadedFrameRayQuery.currentActorPoint(fixture.level(), body);
                        if (before == null || !fixture.plot().engineId().equals(before.frameId()))
                            throw new GameTestAssertException("walking actor lost its native support: " + before);
                        Vec3 ownAtRelease = body.getDeltaMovement();
                        if (new Vec3(ownAtRelease.x, 0, ownAtRelease.z).length() <= .01)
                            throw new GameTestAssertException("actor is not actually walking at release: " + ownAtRelease);
                        if (!com.juicyslew.moonstation14.ms14.shuttle.sable.MovingCabinAuthorityFixture
                                .removeAuthoredStonePad(fixture)) return;
                        var sub = fixture.plot().subLevel();
                        var initialOrientation = new org.joml.Quaterniond(sub.logicalPose().orientation());
                        var initialPoint = LoadedFrameRayQuery.currentActorPoint(fixture.level(), body);
                        if (mode != Mode.STATIONARY) handle.addLinearAndAngularVelocity(
                                mode == Mode.MOVING ? new Vector3d(1, 0, 0) : new Vector3d(), new Vector3d(0, 3.2, 0));
                        int followCount = mode == Mode.ORIGINAL ? 8 : 160;
                        if (mode != Mode.ORIGINAL) {
                            var controlled = sub;
                            ShuttleFixtureMotionService.registerFixture(controlled);
                            helper.testInfo.addListener(new net.minecraft.gametest.framework.GameTestListener() {
                                private boolean retired;
                                private void cleanup() { if (!retired) { retired = true;
                                    ShuttleFixtureMotionService.unregisterFixture(fixture.level(), controlled); } }
                                @Override public void testStructureLoaded(net.minecraft.gametest.framework.GameTestInfo i) { }
                                @Override public void testPassed(net.minecraft.gametest.framework.GameTestInfo i, net.minecraft.gametest.framework.GameTestRunner r) { cleanup(); }
                                @Override public void testFailed(net.minecraft.gametest.framework.GameTestInfo i, net.minecraft.gametest.framework.GameTestRunner r) { cleanup(); }
                                @Override public void testAddedForRerun(net.minecraft.gametest.framework.GameTestInfo a, net.minecraft.gametest.framework.GameTestInfo b, net.minecraft.gametest.framework.GameTestRunner r) { cleanup(); }
                            });
                            var request = mode == Mode.MOVING ? new ShuttleMotionRequest(1, 0, 0, 3.2, 1200)
                                    : new ShuttleMotionRequest(0, 0, 0, 0, 1200);
                            if (!ShuttleFixtureMotionService.start(fixture.level(), controlled, request))
                                throw new GameTestAssertException("diagnostic native motion controller failed to start");
                        }
                        com.juicyslew.moonstation14.gametest.support.BoundedGameTestWait.observeServerTicks(
                                helper, 4, ignored -> { }, () -> {
                        Vector3d nativeAngular = handle.getAngularVelocity(new Vector3d());
                        var currentOrientation = new org.joml.Quaterniond(sub.logicalPose().orientation());
                        var finalPoint = LoadedFrameRayQuery.currentActorPoint(fixture.level(), body);
                        if (mode != Mode.STATIONARY && Math.abs(nativeAngular.y) < 2.8)
                            throw new GameTestAssertException("fast-spin premise not present at release: " + nativeAngular);
                        Vector3d nativeLinear = handle.getLinearVelocity(new Vector3d());
                        if (mode == Mode.MOVING && Math.abs(nativeLinear.x-1) > .2)
                            throw new GameTestAssertException("moving diagnostic linear target not established: " + nativeLinear);
                        if (mode == Mode.STATIONARY && (Math.abs(nativeAngular.y) > .05
                                || Math.abs(nativeLinear.x) > .05 || Math.abs(nativeLinear.z) > .05))
                            throw new GameTestAssertException("stationary control motion is not near zero: v="+nativeLinear+" omega="+nativeAngular);
                        if ((mode != Mode.STATIONARY && 1.0 - Math.abs(initialOrientation.dot(currentOrientation)) <= 1e-5)
                                || initialPoint == null || finalPoint == null
                                || !fixture.plot().engineId().equals(finalPoint.frameId()))
                            throw new GameTestAssertException("native rotating pose did not advance before release: initial="
                                    + initialOrientation + " current=" + currentOrientation + " points=" + initialPoint + "/" + finalPoint);
                        if (!body.onGround() || new Vec3(body.getDeltaMovement().x, 0, body.getDeltaMovement().z).length() <= .01)
                            throw new GameTestAssertException("walking actor lost floor/walking premise before release: ground="
                                    + body.onGround() + " velocity=" + body.getDeltaMovement());
                        // Authenticated lifecycle view input; pitch down toward the known clear interior deck.
                        long viewSequence = owner.viewTo(body, body.position().add(0, -3, 1));
                        // The acknowledged lifecycle view is the causal barrier for the later native probe, not a gameplay delay.
                        com.juicyslew.moonstation14.gametest.support.BoundedGameTestWait.await(helper, 20,
                                () -> "view input was not acknowledged before floor probe: applied="
                                        + owner.drainAppliedSequence() + " viewSequence=" + viewSequence
                                        + " pitch=" + body.getXRot() + " look=" + body.getLookAngle()
                                        + " plan=" + BodyItemTargetProbe.resolveDrop(fixture.level(), body, actor,
                                        com.juicyslew.moonstation14.ms14.loose_objects.LooseObjectProfile.resolveForStack(
                                                new ItemStack(com.juicyslew.moonstation14.item.ModItems.JUG.get()), false).physicalWidth(),
                                        com.juicyslew.moonstation14.ms14.loose_objects.LooseObjectProfile.resolveForStack(
                                                new ItemStack(com.juicyslew.moonstation14.item.ModItems.JUG.get()), false).physicalHeight())
                                        + " frame=" + LoadedFrameRayQuery.currentActorPoint(fixture.level(), body)
                                        + " grounded=" + body.onGround() + " omega=" + handle.getAngularVelocity(new Vector3d()),
                                () -> owner.drainAppliedSequence() >= viewSequence, () -> {
                        Vector3d appliedAngular = handle.getAngularVelocity(new Vector3d());
                        if ((mode != Mode.STATIONARY && Math.abs(appliedAngular.y) < 2.8)
                                || (mode == Mode.STATIONARY && Math.abs(appliedAngular.y) > .05) || !body.onGround()
                                || new Vec3(body.getDeltaMovement().x, 0, body.getDeltaMovement().z).length() <= .01
                                || (mode != Mode.STATIONARY && 1.0 - Math.abs(initialOrientation.dot(new org.joml.Quaterniond(sub.logicalPose().orientation()))) <= 1e-5))
                            throw new GameTestAssertException("native drop premises changed at acknowledged view probe: applied="
                                    + owner.drainAppliedSequence() + " viewSequence=" + viewSequence + " pitch="
                                    + body.getXRot() + " look=" + body.getLookAngle() + " omega=" + appliedAngular
                                    + " grounded=" + body.onGround() + " velocity=" + body.getDeltaMovement());
                        var jug = new ItemStack(com.juicyslew.moonstation14.item.ModItems.JUG.get());
                        var water = net.minecraft.resources.ResourceKey.create(
                                com.juicyslew.moonstation14.ms14.reagent.ModReagents.REAGENT_REGISTRY_KEY,
                                net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("moonstation14", "water"));
                        jug.set(com.juicyslew.moonstation14.component.ModDataComponents.REAGENT.get(),
                                new com.juicyslew.moonstation14.ms14.reagent.ReagentComponent(java.util.Map.of(water, 10f)));
                        LiveHands hands = body.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get());
                        if (hands == null) throw new GameTestAssertException("fixture body missing resolved live hands");
                        String token = "native-floor-jug-" + UUID.randomUUID();
                        var stocked = hands.putWhole(hands.revision(), "left", new ItemToken(token), jug);
                        if (!stocked.succeeded() || !LiveHandsCommitGateway.commit(actor, authority, stocked.state()))
                            throw new GameTestAssertException("could not stock authenticated floor-placement jug");
                        var profile = com.juicyslew.moonstation14.ms14.loose_objects.LooseObjectProfile.resolveForStack(jug, false);
                        var placement = BodyItemTargetProbe.resolveDrop(fixture.level(), body, actor,
                                profile.physicalWidth(), profile.physicalHeight());
                        if (placement == null || !placement.surfacePlacement()
                                || !fixture.plot().engineId().equals(placement.supportFrameId()))
                            throw new GameTestAssertException("release did not resolve a verified native-floor placement: " + placement);
                        var storage = placement.supportStoragePoint();
                        if (storage.x < fixture.cabin().origin().getX() + 1 || storage.x > fixture.cabin().origin().getX() + 6
                                || storage.z < fixture.cabin().origin().getZ() + 1 || storage.z > fixture.cabin().origin().getZ() + 6
                                || Math.abs(storage.y - (fixture.cabin().origin().getY() + 1)) > .02)
                            throw new GameTestAssertException("placement support point not on known clear interior floor: " + storage);
                        UUID[] committed = new UUID[1];
                        var result = BodyHandItemTransfer.drop(actor, "left", token, stocked.state().revision(), id -> committed[0] = id);
                        if (result != BodyHandItemTransfer.Result.SUCCESS || committed[0] == null)
                            throw new GameTestAssertException("authenticated native-floor drop failed: " + result);
                        var entity = fixture.level().getEntity(committed[0]);
                        if (!(entity instanceof com.juicyslew.moonstation14.entities.LooseItemEntity loose))
                            throw new GameTestAssertException("placed jug entity missing: " + entity);
                        assertInitialOrientationComposition(fixture, loose, placement.supportFrameId());
                        LiveHands after = body.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get());
                        if (after.revision() != stocked.state().revision() + 1 || after.token("left").isPresent()
                                || after.stackCopy("left").isPresent()
                                || !ItemStack.isSameItemSameComponents(jug, loose.getItem()) || loose.getItem().getCount() != jug.getCount())
                            throw new GameTestAssertException("placement did not conserve committed hand/token/reagents");
                        Vec3 acceptedOwn = loose.getDeltaMovement();
                        if (loose.position().distanceTo(placement.position()) > 1e-6 || !finite(acceptedOwn)
                                || acceptedOwn.lengthSqr() != 0)
                            throw new GameTestAssertException("floor placement must preserve the clearance-offset point and start at rest: plan="
                                    + placement.position() + " actual=" + loose.position() + " velocity=" + loose.getDeltaMovement());
                        var anchor = LoadedFrameRayQuery.currentActorPoint(fixture.level(), loose);
                        if (anchor == null || !anchor.frameId().equals(placement.supportFrameId()))
                            throw new GameTestAssertException("placed jug not anchored in exact target frame: " + anchor);
                        // Stop issuing walking intent for the observation window; retain ordinary body physics.
                        owner.setWish(0, 0);
                        if (recorder != null) recorder.record(0, fixture.level().getServer().getTickCount(), loose, anchor.storagePoint(), sub);
                        followPlacedJug(helper, fixture, loose, jug, anchor.storagePoint(), anchor.storagePoint(), 0,
                                followCount, mode, recorder);
                        });
                        });
                    });
                });
    }

    private static void followPlacedJug(GameTestHelper helper, MovingCabinAuthorityFixture.Ready fixture,
                                        com.juicyslew.moonstation14.entities.LooseItemEntity loose,
                                         ItemStack expectedStack, Vec3 initialLocal, Vec3 previousLocal, int samples,
                                         int limit, Mode mode, CargoMotionRecorder recorder) {
        helper.runAfterDelay(1, () -> {
            if (loose.isRemoved() || !ItemStack.isSameItemSameComponents(expectedStack, loose.getItem())
                    || loose.getItem().getCount() != expectedStack.getCount()) {
            if (recorder != null) recorder.record(samples + 1, helper.getLevel().getServer().getTickCount(), null, null, fixture.plot().subLevel());
                throw new GameTestAssertException("native floor jug broke, spilled, or changed during first intervals");
            }
            var point = LoadedFrameRayQuery.currentActorPoint(helper.getLevel(), loose);
            if (recorder != null) recorder.record(samples + 1, helper.getLevel().getServer().getTickCount(), loose,
                    point == null ? null : point.storagePoint(), fixture.plot().subLevel());
            if (point == null || !fixture.plot().engineId().equals(point.frameId())) {
                throw new GameTestAssertException("placed jug lost exact target frame during follow-up: " + point);
            }
            // Compare post-step local points from one current frame pose, not pre/post carry samples.
            if (!finite(point.storagePoint()) || !finite(previousLocal) || !finite(initialLocal)) {
                if (recorder != null) recorder.dump("non-finite local support position");
                throw new GameTestAssertException("placed jug has invalid local support measurement");
            }
            double planarDrift = Math.hypot(point.storagePoint().x - previousLocal.x,
                    point.storagePoint().z - previousLocal.z);
            double totalPlanarDrift = Math.hypot(point.storagePoint().x - initialLocal.x,
                    point.storagePoint().z - initialLocal.z);
            if (!Double.isFinite(planarDrift) || !Double.isFinite(totalPlanarDrift)
                    || planarDrift > .06 || totalPlanarDrift > .03) {
                if (recorder != null) recorder.dump("physical XZ drift assertion");
                throw new GameTestAssertException("placed jug drifted across native floor: " + planarDrift
                        + " total=" + totalPlanarDrift + " local=" + initialLocal + " -> " + point.storagePoint());
            }
            if (samples == 0) {
                var capture = loose.nativeMoveCapture();
                Vec3 acceptedOwn = loose.getDeltaMovement();
                if (capture == null || !capture.positionCaptured() || !capture.collisionInfoFresh()
                        || capture.before() == null || capture.atReturn() == null
                        || !finite(capture.before()) || !finite(capture.atReturn())
                        || capture.acceptedOwnDelta() == null
                        || !finite(capture.acceptedOwnDelta())
                        || !finite(capture.requested())
                        || capture.acceptedOwnDelta().distanceTo(capture.atReturn().subtract(capture.before())) > 1e-5
                        || capture.beforeActorPoint() == null
                        || capture.afterActorPoint() == null
                        || !fixture.plot().engineId().equals(capture.beforeActorPoint().frameId())
                        || !fixture.plot().engineId().equals(capture.afterActorPoint().frameId())
                        || !finite(capture.beforeActorPoint().storagePoint())
                        || !finite(capture.afterActorPoint().storagePoint())
                        || capture.frameVelocityDeltaPerSecond() != null)
                    throw new GameTestAssertException("first native interval lacks same-frame/no-rebind evidence: " + capture);
                // finishNativeMoveCapture leaves this null when the fresh before/after frame IDs match;
                // a zero vector would incorrectly manufacture evidence that a frame transition was sampled.
            }
            if (samples + 1 < limit) followPlacedJug(helper, fixture, loose, expectedStack, initialLocal, point.storagePoint(), samples + 1, limit, mode, recorder);
            else {
                if (recorder != null && !recorder.complete()) {
                    recorder.dump("sample coverage incomplete");
                    throw new GameTestAssertException("diagnostic sample coverage incomplete");
                }
                if (recorder != null) recorder.dump("terminal"); helper.succeed();
            }
        });
    }

    private static boolean finite(Vec3 value) {
        return value != null && Double.isFinite(value.x) && Double.isFinite(value.y) && Double.isFinite(value.z);
    }

    private static void assertInitialOrientationComposition(MovingCabinAuthorityFixture.Ready fixture,
            com.juicyslew.moonstation14.entities.LooseItemEntity item, UUID expectedFrame) {
        var point = LoadedFrameRayQuery.currentActorPoint(fixture.level(), item);
        var snapshot = item.orientationSnapshot();
        if (point == null || snapshot == null || !expectedFrame.equals(point.frameId())
                || !expectedFrame.equals(snapshot.frameId()))
            throw new GameTestAssertException("initial native cargo orientation/position parent mismatch before next tick: point="
                    + point + " snapshot=" + snapshot + " expected=" + expectedFrame);
        var parent = LoadedFrameRayQuery.frameOrientation(fixture.level(), point.frameId(), point.storagePoint(), false, 1f);
        var authoritative = item.authoritativeQueryOrientation(fixture.level());
        var composed = parent == null ? null
                : com.juicyslew.moonstation14.entities.LooseFrameOrientation.worldBasis(parent, snapshot.localBasis());
        if (parent == null || authoritative == null || composed == null
                || 1 - Math.abs(composed.dot(authoritative)) > 1.0e-8)
            throw new GameTestAssertException("initial local cargo orientation does not compose with exact current position parent: parent="
                    + parent + " snapshot=" + snapshot + " composed=" + composed + " authoritative=" + authoritative);
    }

    @GameTest(templateNamespace = "moonstation14", template = "empty", timeoutTicks = 220)
    @GameTestTags({"hands-stationary-cabin-regression"})
    public static void stationaryCabinCanonicalThrowAndPickup(GameTestHelper helper) {
        MovingCabinAuthorityFixture.start(helper, new BlockPos(1780, 300, 780), "hands-stationary-cabin",
                2.5, 1, 2.5, (fixture, body) -> {
                    var owner = fixture.owner();
                    var actor = owner.actor();
                    var authority = owner.authority();
                    var sourceFrame = LoadedFrameRayQuery.currentActorPoint(fixture.level(), body);
                    if (sourceFrame == null || !fixture.plot().engineId().equals(sourceFrame.frameId()))
                        throw new GameTestAssertException("stationary-cabin owner lacks exact native frame: frame="
                                + sourceFrame + " eye=" + body.getEyePosition() + " look=" + body.getLookAngle());
                    if (!com.juicyslew.moonstation14.ms14.character.CharacterControlSystem.canAct(body))
                        throw new GameTestAssertException("stationary-cabin body cannot act; eye="
                                + body.getEyePosition() + " look=" + body.getLookAngle() + " frame=" + sourceFrame);
                    LiveHands hands = body.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get());
                    if (hands == null) throw new GameTestAssertException("stationary-cabin body missing resolved live hands");
                    ItemStack jug = new ItemStack(com.juicyslew.moonstation14.item.ModItems.JUG.get());
                    var water = net.minecraft.resources.ResourceKey.create(
                            com.juicyslew.moonstation14.ms14.reagent.ModReagents.REAGENT_REGISTRY_KEY,
                            net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("moonstation14", "water"));
                    jug.set(com.juicyslew.moonstation14.component.ModDataComponents.REAGENT.get(),
                            new com.juicyslew.moonstation14.ms14.reagent.ReagentComponent(java.util.Map.of(water, 10f)));
                    String token = "stationary-cabin-jug-" + UUID.randomUUID();
                    long initialRevision = hands.revision();
                    var stocked = hands.putWhole(initialRevision, "left", new ItemToken(token), jug);
                    if (!stocked.succeeded() || !LiveHandsCommitGateway.commit(actor, authority, stocked.state()))
                        throw new GameTestAssertException("could not stock stationary-cabin jug");
                     // Embedded lifecycle actor is authenticated, but not a connected client; client cadence is not exercised.
                     body.setYRot(0); body.setXRot(0); body.setYHeadRot(0);
                     var eye = body.getEyePosition(); var look = body.getLookAngle();
                     var ownMotion = body.getDeltaMovement();
                     var profile = com.juicyslew.moonstation14.ms14.loose_objects.LooseObjectProfile
                             .resolveForStack(jug, false);
                     double throwRange = Math.min(BodyItemTargetProbe.MAX_THROW_REACH, profile.throwRange());
                      var rayEnd = eye.add(look.scale(throwRange));
                      var ray = LoadedFrameRayQuery.clip(fixture.level(), eye, rayEnd,
                              BodyItemTargetProbe.MAX_CHUNKS, 0, 1,
                              (getter, from, to) -> new net.minecraft.world.level.ClipContext(from, to,
                                      net.minecraft.world.level.ClipContext.Block.COLLIDER,
                                      net.minecraft.world.level.ClipContext.Fluid.NONE, body));
                      var capturedTarget = LoadedFrameRayQuery.capture(ray, rayEnd);
                      var selectedAim = capturedTarget == null ? null : capturedTarget.frameId() == null
                              ? capturedTarget.point()
                              : fixture.plot().subLevel().logicalPose().transformPosition(capturedTarget.point());
                      var resolvedAim = LoadedFrameRayQuery.resolveCapturedTarget(fixture.level(), capturedTarget);
                      if (selectedAim == null || resolvedAim == null || selectedAim.distanceTo(resolvedAim) > 1.0e-6)
                          throw new GameTestAssertException("canonical throw has no shared loaded aim point; eye=" + eye + " look=" + look);
                     double handY = body.getY() + net.minecraft.util.Mth.clamp(1.125, 0.0,
                             Math.max(0.0, body.getBbHeight() - profile.physicalHeight() - .075));
                     var handOrigin = new Vec3(body.getX(), handY, body.getZ());
                     var expectedDirection = selectedAim.subtract(handOrigin).normalize();
                     // Match BodyLooseThrow's conservative 0.1-block hand-origin launch offset.
                     var expectedLaunch = handOrigin.add(expectedDirection.scale(.1));
                     var expectedVelocityPoint = LoadedFrameRayQuery.pointAtWorldPosition(fixture.level(),
                             sourceFrame.frameId(), expectedLaunch);
                     var expectedCarrier = expectedVelocityPoint == null ? null
                             : SableFrameVelocity.pointVelocityAt(fixture.level(), expectedVelocityPoint);
                      var expectedVelocity = expectedCarrier == null ? null
                              : ownMotion.add(expectedCarrier.scale(
                                      com.juicyslew.moonstation14.ms14.player_body_control.movement.GroundedHarnessPhysics.TICK_SECONDS))
                              .add(expectedDirection.scale(profile.throwSpeed()));
                      var beforeThrow = body.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get());
                     var nearbyBeforeThrow = fixture.level().getEntitiesOfClass(
                             com.juicyslew.moonstation14.entities.LooseItemEntity.class, body.getBoundingBox().inflate(2))
                             .stream().map(net.minecraft.world.entity.Entity::getUUID).collect(Collectors.toSet());
                     var request = com.juicyslew.moonstation14.ms14.hands.network.BodyHandActionRequest.throwItem(
                             "left", owner.epoch(), 1, beforeThrow.revision(), token, capturedTarget);
                     body.setYRot(180); body.setXRot(0); body.setYHeadRot(180);
                    var response = com.juicyslew.moonstation14.ms14.hands.network.BodyHandRequestService.apply(actor, request);
                      if (response.reason() != com.juicyslew.moonstation14.ms14.hands.network.BodyHandActionResult.Reason.OK
                             || !response.accepted() || response.revision() != beforeThrow.revision() + 1)
                         throw new GameTestAssertException("stationary-cabin THROW_ITEM failed: result=" + response
                                 + " actualEye=" + eye + " actualLook=" + look + " frame=" + sourceFrame);
                     var newJugs = fixture.level().getEntitiesOfClass(
                                     com.juicyslew.moonstation14.entities.LooseItemEntity.class,
                                     body.getBoundingBox().inflate(2)).stream()
                             .filter(item -> !nearbyBeforeThrow.contains(item.getUUID()))
                             .filter(item -> ItemStack.isSameItemSameComponents(jug, item.getItem())
                                     && item.getItem().getCount() == jug.getCount()).toList();
                     if (newJugs.size() != 1)
                         throw new GameTestAssertException("THROW_ITEM must create exactly one new canonical jug; found=" + newJugs.size());
                      UUID id = newJugs.get(0).getUUID();
                      var hostTransition = response.hostTransition();
                      if (hostTransition == null
                              || hostTransition.direction() != com.juicyslew.moonstation14.ms14.hands.network.BodyHandActionResult.HostTransition.Direction.DROP
                              || !id.equals(hostTransition.worldEntity())
                              || !token.equals(hostTransition.hostToken()))
                          throw new GameTestAssertException("THROW_ITEM response lost committed host transition: result="
                                  + response + " discoveredWorldEntity=" + id + " expectedHostToken=" + token);
                     var loose = fixture.level().getEntity(id);
                     if (!(loose instanceof com.juicyslew.moonstation14.entities.LooseItemEntity item))
                         throw new GameTestAssertException("THROW_ITEM did not produce canonical loose entity: uuid=" + id
                                 + " eye=" + eye + " look=" + look + " frame=" + sourceFrame);
                     var actualVelocityPoint = LoadedFrameRayQuery.pointAtWorldPosition(fixture.level(),
                             sourceFrame.frameId(), item.position());
                     var actualCarrier = actualVelocityPoint == null ? null
                             : SableFrameVelocity.pointVelocityAt(fixture.level(), actualVelocityPoint);
                     var actualIntent = actualCarrier == null ? null
                             : item.getDeltaMovement().subtract(ownMotion).subtract(actualCarrier.scale(.05));
                    var afterThrow = body.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get());
                    if (afterThrow.revision() != beforeThrow.revision() + 1
                            || afterThrow.stackCopy("left").isPresent() || afterThrow.token("left").isPresent())
                        throw new GameTestAssertException("THROW_ITEM hand commit mismatch: revision=" + afterThrow.revision()
                                + " expected=" + (beforeThrow.revision() + 1) + " eye=" + eye + " look=" + look + " frame=" + sourceFrame);
                    if (!ItemStack.isSameItemSameComponents(jug, item.getItem()) || item.getItem().getCount() != jug.getCount())
                        throw new GameTestAssertException("THROW_ITEM changed canonical jug components/reagents: uuid=" + id
                                + " original=" + jug + " world=" + item.getItem());
                     if (expectedVelocityPoint == null || expectedVelocity == null || actualVelocityPoint == null
                             || !sourceFrame.frameId().equals(actualVelocityPoint.frameId())
                             || actualCarrier == null || actualIntent == null
                             || item.position().distanceTo(expectedLaunch) > 1.0e-6
                             || item.getDeltaMovement().distanceTo(expectedVelocity) > 1.0e-5
                             || actualIntent.dot(selectedAim.subtract(handOrigin)) <= 0)
                         throw new GameTestAssertException("THROW_ITEM initial pose/motion implausible: uuid=" + id
                                 + " expectedLaunch=" + expectedLaunch + " position=" + item.position()
                                 + " aim=" + selectedAim + " handOrigin=" + handOrigin + " eye=" + eye + " look=" + look
                                 + " expectedVelocity=" + expectedVelocity + " velocity=" + item.getDeltaMovement()
                                 + " carrierPoint=" + actualVelocityPoint + " carrier=" + actualCarrier
                                 + " intent=" + actualIntent + " frame=" + sourceFrame);

                     // Allow native item physics to advance before choosing the authoritative pickup ray.
                     helper.runAfterDelay(3, () -> {
                         var live = fixture.level().getEntity(id);
                         if (!(live instanceof net.minecraft.world.entity.item.ItemEntity worldItem) || worldItem.isRemoved())
                            throw new GameTestAssertException("thrown jug unavailable for pickup; uuid=" + id + " eye="
                                     + body.getEyePosition() + " look=" + body.getLookAngle() + " frame="
                                     + LoadedFrameRayQuery.currentActorPoint(fixture.level(), body));
                          // Nominated pickup is geometry-authoritative, independent of body gaze.
                          Vec3 target = worldItem.position().add(0, worldItem.getBbHeight() * .5, 0);
                          body.setYRot(180); body.setXRot(0); body.setYHeadRot(180);
                        var current = body.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get());
                        var pickup = new com.juicyslew.moonstation14.ms14.hands.network.BodyHandActionRequest(
                                com.juicyslew.moonstation14.ms14.hands.network.BodyHandActionRequest.Action.PICKUP,
                                "left", owner.epoch(), 2, current.revision(), id, null);
                        var picked = com.juicyslew.moonstation14.ms14.hands.network.BodyHandRequestService.apply(actor, pickup);
                        var restored = body.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get());
                        if (picked.reason() != com.juicyslew.moonstation14.ms14.hands.network.BodyHandActionResult.Reason.OK
                                || picked.revision() != current.revision() + 1 || !worldItem.isRemoved()
                                || restored.revision() != current.revision() + 1
                                || restored.stackCopy("left").filter(stack -> ItemStack.isSameItemSameComponents(jug, stack)
                                        && stack.getCount() == jug.getCount()).isEmpty())
                            throw new GameTestAssertException("canonical PICKUP failed: result=" + picked + " uuid=" + id
                                    + " entityRemoved=" + worldItem.isRemoved() + " hand=" + restored.stackCopy("left")
                                    + " eye=" + body.getEyePosition() + " look=" + body.getLookAngle()
                                    + " frame=" + LoadedFrameRayQuery.currentActorPoint(fixture.level(), body));
                        helper.succeed();
                    });
                });
    }

    private static void awaitFirstFrameEntry(GameTestHelper helper, MovingCabinAuthorityFixture.Ready fixture,
                                              com.juicyslew.moonstation14.entities.LooseItemEntity loose, int waited) {
        if (waited >= 50) throw new GameTestAssertException("released item did not acquire native frame tracking");
        var capture = loose.nativeMoveCapture();
        if (capture == null || !capture.collisionInfoFresh() || capture.beforeActorPoint() == null
                || capture.afterActorPoint() == null || capture.beforeActorPoint().frameId() != null
                || !fixture.plot().engineId().equals(capture.afterActorPoint().frameId())
                || capture.frameVelocityDeltaPerSecond() == null) {
            helper.runAfterDelay(1, () -> awaitFirstFrameEntry(helper, fixture, loose, waited + 1));
            return;
        }
        Vec3 destination = SableFrameVelocity.pointVelocityAt(fixture.level(), capture.afterActorPoint());
        Vec3 expectedDelta = destination == null ? null : destination.scale(-1);
        if (expectedDelta == null || capture.frameVelocityDeltaPerSecond().distanceTo(expectedDelta) > 1.0e-5)
            throw new GameTestAssertException("initial world-to-frame transition must remove destination point velocity exactly once; expected="
                    + expectedDelta + " observed=" + capture.frameVelocityDeltaPerSecond());
        helper.succeed();
    }
}
