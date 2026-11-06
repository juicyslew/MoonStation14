package com.juicyslew.moonstation14.ms14.examine;

import com.juicyslew.moonstation14.entities.LooseItemEntity;
import com.juicyslew.moonstation14.gametest.support.GameTestTags;
import com.juicyslew.moonstation14.ms14.shuttle.sable.MovingCabinAuthorityFixture;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Live server examination consumers against resolved jug dimensions and a committed native frame yaw. */
@PrefixGameTestTemplate(false)
@GameTestTags({"shuttle-cargo-query", "examine_jug"})
public final class CargoExamineGeometryGameTests {
    private CargoExamineGeometryGameTests() { }

    @GameTest(templateNamespace = "moonstation14", template = "atmos_large_empty", timeoutTicks = 500)
    public static void rotatedCargoUsesOneSharedShapeForSelectionResolutionAndVisibility(GameTestHelper helper) {
        MovingCabinAuthorityFixture.start(helper, new BlockPos(1600, -47, 1500), "CargoQueryExaminer",
                2.5, 1, 2.5, 0, (fixture, body) -> MovingCabinAuthorityFixture.awaitNativeYaw(helper, fixture, body,
                        () -> queryAfterCommittedYaw(helper, fixture, body)));
    }

    private static void queryAfterCommittedYaw(GameTestHelper helper, MovingCabinAuthorityFixture.Ready fixture,
                                               net.minecraft.world.entity.LivingEntity body) {
        var level = fixture.level();
        Vec3 itemPosition = body.position().add(0, .35, 1.2);
        LooseItemEntity item = LooseItemEntity.createForActor(body, itemPosition,
                new ItemStack(com.juicyslew.moonstation14.item.ModItems.JUG.get()));
        item.setRotationAngle((float) (Math.PI / 4));
        if (!level.addFreshEntity(item)) throw new GameTestAssertException("actor-context jug spawn rejected");
        try {
            var shape = com.juicyslew.moonstation14.ms14.loose_objects.LooseItemQueryGeometry.forEntity(item, 1);
            require(shape != null, "resolved jug query shape exists");
            require(!shape.orientation().equals(new org.joml.Quaterniond(), 1e-3),
                    "actual committed actor-frame basis and local spin produce nonidentity shape");
            var physical = item.getBoundingBox();

            // A ray entering through local +X reaches the rotated profile while staying outside the native AABB.
            var orientation = shape.orientation();
            var localOutside = new org.joml.Vector3d(shape.localBounds().maxX + .18,
                    shape.localBounds().getCenter().y, shape.localBounds().maxZ - .02);
            var localInside = new org.joml.Vector3d(shape.localBounds().maxX - .02,
                    shape.localBounds().getCenter().y, shape.localBounds().maxZ - .02);
            Vec3 start = transform(shape.pivot(), orientation, localOutside);
            Vec3 finish = transform(shape.pivot(), orientation, localInside);
            require(physical.clip(start, finish).isEmpty(), "true oriented-profile hit lies outside unchanged physical AABB");
            require(shape.clip(start, finish) != null, "shared shape clips the exterior-AABB ray");
            var selected = ExamineWorldSelector.select(level, start, finish.subtract(start), start.distanceTo(finish),
                    java.util.Set.of(body.getId()));
            require(selected.stream().anyMatch(candidate -> candidate.target().kind() == ExamineTarget.Kind.ENTITY
                            && candidate.target().entityUuid().equals(item.getUUID())),
                    "production selector admits true oriented-shape hit outside physical AABB");

            var envelope = shape.worldEnvelope();
            Vec3 envelopeOnly = new Vec3(envelope.maxX - .02, envelope.getCenter().y, envelope.maxZ - .02);
            Vec3 envelopeEnd = envelopeOnly.add(.005, 0, 0);
            require(envelope.contains(envelopeOnly) && envelope.contains(envelopeEnd)
                            && shape.clip(envelopeOnly, envelopeEnd) == null,
                    "envelope-only segment is outside true oriented shape");
            var falsePositive = ExamineWorldSelector.select(level, envelopeOnly, envelopeEnd.subtract(envelopeOnly),
                    envelopeOnly.distanceTo(envelopeEnd), java.util.Set.of(body.getId()));
            require(falsePositive.stream().noneMatch(candidate -> candidate.target().kind() == ExamineTarget.Kind.ENTITY
                            && candidate.target().entityUuid().equals(item.getUUID())),
                    "production selector rejects envelope false-positive");

            var target = ExamineTarget.entity(item);
            var resolved = ExamineTargetResolver.resolve(level, body, target).orElseThrow(
                    () -> new GameTestAssertException("server resolver rejected live jug identity"));
            Vec3 expectedNearest = shape.nearestPoint(body.getEyePosition());
            require(resolved.point().distanceTo(expectedNearest) < 1e-8,
                    "server nearest point uses shared oriented profile");
            require(resolved.distance() <= ExaminationPolicy.NORMAL_RANGE + 2,
                    "oriented nearest point remains within resolver range gate");
            require(ExamineTargetResolver.visible(level, body, resolved),
                    "server visibility/LOS accepts the same in-range candidate");
            require(item.getBoundingBox().equals(physical), "query consumers leave physical entity AABB unchanged");
        } finally {
            item.remove(net.minecraft.world.entity.Entity.RemovalReason.DISCARDED);
        }
        helper.succeed();
    }

    private static Vec3 transform(Vec3 pivot, org.joml.Quaterniond rotation, org.joml.Vector3d local) {
        var world = rotation.transform(new org.joml.Vector3d(local)).add(pivot.x, pivot.y, pivot.z);
        return new Vec3(world.x, world.y, world.z);
    }
    private static void require(boolean value, String message) {
        if (!value) throw new GameTestAssertException(message);
    }
}
