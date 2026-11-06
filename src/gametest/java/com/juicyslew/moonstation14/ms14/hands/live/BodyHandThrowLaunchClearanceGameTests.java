package com.juicyslew.moonstation14.ms14.hands.live;

import com.juicyslew.moonstation14.component.ModDataAttachments;
import com.juicyslew.moonstation14.gametest.support.GameTestTags;
import com.juicyslew.moonstation14.ms14.hands.ItemToken;
import com.juicyslew.moonstation14.ms14.shuttle.sable.MovingCabinAuthorityFixture;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.BooleanOp;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.UUID;
import java.util.stream.Collectors;

@PrefixGameTestTemplate(false)
public final class BodyHandThrowLaunchClearanceGameTests {
    private BodyHandThrowLaunchClearanceGameTests() { }

    @GameTest(templateNamespace = "moonstation14", template = "empty", timeoutTicks = 260)
    @GameTestTags({"hands-stationary-cabin-regression", "shuttle"})
    public static void nativeWallLaunchRejectionPreservesHeldItem(GameTestHelper helper) {
        MovingCabinAuthorityFixture.start(helper, new BlockPos(7200, 300, 6000), "native-wall-throw-clearance",
                2.7, 1, 2.5, (fixture, body) -> {
                    var owner = fixture.owner();
                    var actor = owner.actor();
                    var wall = fixture.cabin().origin().offset(3, 2, 2);
                    if (!fixture.plot().setStorageBlock(wall, Blocks.STONE.defaultBlockState())
                            || !fixture.level().getBlockState(wall).is(Blocks.STONE))
                        throw new GameTestAssertException("native cabin wall was not authored/loaded at " + wall);

                    var pose = fixture.plot().subLevel().logicalPose();
                    Vec3 bodyWorld = pose.transformPosition(new Vec3(fixture.cabin().origin().getX() + 2.7,
                            fixture.cabin().origin().getY() + 1, fixture.cabin().origin().getZ() + 2.5));
                    body.setPos(bodyWorld);
                    body.setYRot(-90);
                    body.setXRot(0);
                    body.setYHeadRot(-90);
                    var source = LoadedFrameRayQuery.currentActorPoint(fixture.level(), body);
                    if (source == null || !fixture.plot().engineId().equals(source.frameId()))
                        throw new GameTestAssertException("fixture body lacks actual native cabin frame: " + source);

                    LiveHands before = body.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get());
                    if (before == null) throw new GameTestAssertException("fixture body missing resolved live hands");
                    ItemStack jug = new ItemStack(net.minecraft.world.item.Items.STICK);
                    String token = "native-wall-jug-" + UUID.randomUUID();
                    var stocked = before.putWhole(before.revision(), "left", new ItemToken(token), jug);
                    if (!stocked.succeeded() || !LiveHandsCommitGateway.commit(actor, owner.authority(), stocked.state()))
                        throw new GameTestAssertException("could not authenticate stocked jug");
                    before = body.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get());

                    var profile = com.juicyslew.moonstation14.ms14.loose_objects.LooseObjectProfile.resolveForStack(jug, false);
                    if (profile == null || profile.physicalWidth() <= .6)
                        throw new GameTestAssertException("test item did not resolve the expected generic physical profile: " + profile);
                    Vec3 eye = body.getEyePosition();
                    Vec3 rayEnd = eye.add(body.getLookAngle().scale(Math.min(BodyItemTargetProbe.MAX_THROW_REACH,
                            profile.throwRange())));
                    var ray = LoadedFrameRayQuery.clip(fixture.level(), eye, rayEnd, BodyItemTargetProbe.MAX_CHUNKS, 0, 1,
                            (getter, from, to) -> new net.minecraft.world.level.ClipContext(from, to,
                                    net.minecraft.world.level.ClipContext.Block.COLLIDER,
                                    net.minecraft.world.level.ClipContext.Fluid.NONE, body));
                    var capturedTarget = LoadedFrameRayQuery.capture(ray, rayEnd);
                    if (capturedTarget == null || !fixture.plot().engineId().equals(capturedTarget.frameId())
                            || !wall.equals(capturedTarget.frameCell()))
                        throw new GameTestAssertException("native ray did not capture authored wall: " + capturedTarget);

                    double handY = body.getY() + net.minecraft.util.Mth.clamp(1.125, 0,
                            Math.max(0, body.getBbHeight() - profile.physicalHeight() - .075));
                    Vec3 handOrigin = new Vec3(body.getX(), handY, body.getZ());
                    Vec3 selectedAim = fixture.plot().subLevel().logicalPose().transformPosition(capturedTarget.point());
                    Vec3 launch = handOrigin.add(selectedAim.subtract(handOrigin).normalize().scale(.1));
                    var candidate = com.juicyslew.moonstation14.entities.LooseItemEntity.create(fixture.level(),
                            launch, jug.copy());
                    var candidateBox = candidate.getBoundingBox();
                    var worldView = LoadedBlockGetter.collisionView(fixture.level(), launch, launch,
                            Math.max(candidateBox.getXsize(), candidateBox.getZsize()) / 2, 1, 0);
                    for (var worldShape : worldView.getBlockCollisions(candidate, candidateBox)) {
                        if (Shapes.joinIsNotEmpty(worldShape, Shapes.create(candidateBox), BooleanOp.AND))
                            throw new GameTestAssertException("ordinary world geometry already obstructs candidate launch: " + candidateBox);
                    }
                    var frameQuery = LoadedFrameRayQuery.frameBoxEndpointQuery(fixture.level(), candidateBox);
                    var expectedFrame = frameQuery.contexts().stream()
                            .filter(context -> fixture.plot().engineId().equals(context.frameId())).findFirst().orElse(null);
                    if (!frameQuery.complete() || expectedFrame == null
                            || !com.juicyslew.moonstation14.ms14.shuttle.sable.FrameBoxIntersection.supportsPose(expectedFrame.pose()))
                        throw new GameTestAssertException("candidate frame-box query incomplete or missing expected native frame");
                    var wallRead = expectedFrame.read(wall);
                    if (!wallRead.known() || wallRead.state() == null || !wallRead.state().is(Blocks.STONE))
                        throw new GameTestAssertException("expected native wall cell is unknown/not stone: " + wallRead);
                    var nativePlot = expectedFrame.frame().getPlot();
                    var storageBounds = new dev.ryanhcode.sable.companion.math.BoundingBox3d(
                            nativePlot.getBoundingBox()).toMojang().inflate(1.0);
                    var frameGetter = LoadedBlockGetter.forFullFrame(fixture.level(), nativePlot, storageBounds,
                            BodyItemTargetProbe.MAX_CHUNKS);
                    var shapeQuery = new com.juicyslew.moonstation14.ms14.shuttle.sable.NativeFrameShapeQuery(
                            fixture.level(), candidate, expectedFrame.pose(), frameGetter);
                    boolean nativeWallOverlap = shapeQuery.shape(wall, frameGetter.getBlockState(wall)).toAabbs().stream()
                            .map(shape -> shape.move(wall.getX(), wall.getY(), wall.getZ()))
                            .anyMatch(shape -> com.juicyslew.moonstation14.ms14.shuttle.sable.FrameBoxIntersection.intersects(
                                    candidateBox, new org.joml.Quaterniond(), shape, expectedFrame.pose()));
                    if (!nativeWallOverlap)
                        throw new GameTestAssertException("canonical native-frame wall shape does not overlap launch AABB: " + candidateBox);
                    if (BodyItemTargetProbe.loadedItemBoxClear(fixture.level(), candidate))
                        throw new GameTestAssertException("candidate launch box unexpectedly clear at native wall: " + launch);

                    var nearby = fixture.level().getEntitiesOfClass(com.juicyslew.moonstation14.entities.LooseItemEntity.class,
                            body.getBoundingBox().inflate(2)).stream().map(net.minecraft.world.entity.Entity::getUUID)
                            .collect(Collectors.toSet());
                    var request = com.juicyslew.moonstation14.ms14.hands.network.BodyHandActionRequest.throwItem(
                            "left", owner.epoch(), 1, before.revision(), token, capturedTarget);
                    var response = com.juicyslew.moonstation14.ms14.hands.network.BodyHandRequestService.apply(actor, request);
                    LiveHands after = body.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get());
                    if (response.accepted() || after.revision() != before.revision()
                            || !token.equals(after.token("left").orElse(null))
                            || after.stackCopy("left").filter(stack -> ItemStack.isSameItemSameComponents(jug, stack)
                                    && stack.getCount() == jug.getCount()).isEmpty())
                        throw new GameTestAssertException("blocked native launch changed held item: " + response);
                    var created = fixture.level().getEntitiesOfClass(com.juicyslew.moonstation14.entities.LooseItemEntity.class,
                            body.getBoundingBox().inflate(2)).stream().filter(item -> !nearby.contains(item.getUUID())).toList();
                    if (!created.isEmpty()) throw new GameTestAssertException("blocked launch created loose item(s): " + created);
                    helper.succeed();
                });
    }
}
