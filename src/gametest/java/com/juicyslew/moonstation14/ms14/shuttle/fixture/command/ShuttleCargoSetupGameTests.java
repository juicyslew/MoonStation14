package com.juicyslew.moonstation14.ms14.shuttle.fixture.command;

import com.juicyslew.moonstation14.entities.LooseItemEntity;
import com.juicyslew.moonstation14.gametest.support.GameTestTags;
import com.juicyslew.moonstation14.item.ModItems;
import com.juicyslew.moonstation14.ms14.hands.live.LoadedFrameRayQuery;
import com.juicyslew.moonstation14.ms14.shuttle.sable.MovingCabinAuthorityFixture;
import com.juicyslew.moonstation14.component.ModDataComponents;
import com.juicyslew.moonstation14.ms14.reagent.ModReagents;
import com.juicyslew.moonstation14.ms14.reagent.ReagentComponent;
import dev.ryanhcode.sable.api.physics.handle.RigidBodyHandle;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.level.entity.EntityTypeTest;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import org.joml.Vector3d;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

@GameTestTags({"shuttle-cargo-setup", "shuttle-cargo-grant-context", "shuttle", "command"})
@PrefixGameTestTemplate(false)
public final class ShuttleCargoSetupGameTests {
    private ShuttleCargoSetupGameTests() { }

    @GameTest(templateNamespace = "moonstation14", template = "empty", timeoutTicks = 1200)
    public static void yawCommandAppliesOneNativeWorldYAngularDelta(GameTestHelper helper) {
        MovingCabinAuthorityFixture.start(helper, new BlockPos(2200, -47, 1500), "cargo-yaw-setup", 2.5, 1, 2.5, (fixture, body) -> {
            var handle = RigidBodyHandle.of(fixture.plot().subLevel());
            var beforeLinear = handle.getLinearVelocity(new Vector3d());
            var beforeAngular = handle.getAngularVelocity(new Vector3d());
            var pose = fixture.plot().subLevel().logicalPose();
            var beforePosition = new Vector3d(pose.position());
            var beforeOrientation = new org.joml.Quaterniond(pose.orientation());
            var dispatcher = fixture.level().getServer().getCommands().getDispatcher();
            var source = fixture.owner().actor().createCommandSourceStack().withPermission(2);
            String uuid = fixture.plot().engineId().toString();
            require(execute(dispatcher, "ms14 shuttle_fixture yaw " + uuid + " 0.4", source) == 1, "yaw command executes");
            var afterLinear = handle.getLinearVelocity(new Vector3d());
            var afterAngular = handle.getAngularVelocity(new Vector3d());
            require(Math.abs(afterAngular.y - beforeAngular.y - .4) < .01, "one command adds exactly world-Y angular velocity");
            require(Math.abs(afterAngular.x - beforeAngular.x) < .01 && Math.abs(afterAngular.z - beforeAngular.z) < .01,
                    "yaw command does not add X/Z angular velocity");
            require(afterLinear.distance(beforeLinear) < .01, "yaw command adds no linear velocity");
            var afterPose = fixture.plot().subLevel().logicalPose();
            require(afterPose.position().distance(beforePosition) < 1.0e-9
                            && afterPose.orientation().equals(beforeOrientation, 1.0e-9),
                    "command does not assign native position or orientation");
            try { dispatcher.execute("ms14 shuttle_fixture yaw " + uuid + " NaN", source); throw new GameTestAssertException("NaN accepted"); }
            catch (com.mojang.brigadier.exceptions.CommandSyntaxException expected) { }
            requireVelocityUnchanged(handle, afterLinear, afterAngular, "invalid yaw input");
            require(execute(dispatcher, "ms14 shuttle_fixture yaw ship-deadbeef 0.4", source) == 0,
                    "unknown ship is refused");
            requireVelocityUnchanged(handle, afterLinear, afterAngular, "unknown ship");
            try { dispatcher.execute("ms14 shuttle_fixture yaw " + uuid + " 0.4",
                    source.withPermission(0)); throw new GameTestAssertException("permission-0 accepted"); }
            catch (com.mojang.brigadier.exceptions.CommandSyntaxException expected) { }
            requireVelocityUnchanged(handle, afterLinear, afterAngular, "permission denial");
            helper.succeed();
        });
    }

    @GameTest(templateNamespace = "moonstation14", template = "empty", timeoutTicks = 1200)
    public static void cargoCommandPublishesOneWaterJugForExactAdmittedFrame(GameTestHelper helper) {
        MovingCabinAuthorityFixture.start(helper, new BlockPos(2400, -47, 1500), "cargo-grant-setup", 2.5, 1, 2.5, (fixture, body) -> {
            MovingCabinAuthorityFixture.awaitNativeYaw(helper, fixture, body, () -> {
                var level = fixture.level();
                UUID frameId = fixture.plot().engineId();
                var point = LoadedFrameRayQuery.currentActorPoint(level, body);
                require(point != null && frameId.equals(point.frameId()), "controlled actor has the exact fixture frame");
                var expectedBasis = LoadedFrameRayQuery.frameOrientation(level, point.frameId(), point.storagePoint(), false, 1f);
                require(expectedBasis != null && !expectedBasis.equals(new org.joml.Quaterniond(), 1.0e-4),
                        "ordinary native yaw provides a nonidentity world basis");
                var beforeHands = body.getExistingDataOrNull(com.juicyslew.moonstation14.component.ModDataAttachments.LIVE_HANDS.get());
                var owner = fixture.owner().actor();
                // Deliberately overlap the authorized spectator carrier with its controlled body;
                // trusted cargo must exclude this exact authority carrier during candidate checks.
                owner.setPos(body.position());
                require(com.juicyslew.moonstation14.ms14.hands.HandActorAuthority.resolve(owner)
                                .filter(snapshot -> snapshot.body() == body
                                        && com.juicyslew.moonstation14.ms14.hands.HandActorAuthority.revalidate(owner, snapshot)).isPresent(),
                        "co-located carrier remains the exact authorized body authority");
                var inventoryBefore = snapshotInventory(owner);
                Set<UUID> beforeItems = itemIds(level, body.position());
                var dispatcher = level.getServer().getCommands().getDispatcher();
                var source = owner.createCommandSourceStack().withPermission(2);
                int result = execute(dispatcher, "ms14 shuttle_fixture cargo " + frameId, source);
                var afterItems = items(level, body.position());
                var added = afterItems.stream().filter(item -> !beforeItems.contains(item.getUUID())).toList();
                try {
                    require(result == 1 && added.size() == 1, "cargo command publishes exactly one new loose item");
                    var jug = added.getFirst();
                    var stack = jug.getItem();
                    require(stack.is(ModItems.JUG.get()) && stack.getCount() == 1, "published item is one ordinary jug");
                    ReagentComponent contents = stack.get(ModDataComponents.REAGENT.get());
                    var water = ModReagents.createKey("water");
                    require(contents != null && contents.centContents().equals(java.util.Map.of(water, 2000L)),
                            "jug contains exactly 2000 cents (20 units) of water and no other reagents");
                    require(ModItems.JUG.get().getCapacity(level) >= 20f, "resolved jug capacity admits the grant");
                    require(jug instanceof LooseItemEntity && !jug.isRemoved(), "grant uses the live loose-item world path");
                    var loose = (LooseItemEntity) jug;
                    var snapshot = loose.orientationSnapshot();
                    var seededBasis = loose.authoritativeQueryOrientation(level);
                    var itemPoint = LoadedFrameRayQuery.currentActorPoint(level, loose);
                    require(snapshot != null && snapshot.frameId() == null && seededBasis != null
                                    && expectedBasis.equals(seededBasis, 1.0e-5)
                                    && itemPoint != null && itemPoint.frameId() == null,
                            "grant placement captures current world basis without assigning the item to the ship");
                    require(beforeHands == body.getExistingDataOrNull(com.juicyslew.moonstation14.component.ModDataAttachments.LIVE_HANDS.get()),
                            "grant leaves body hand ownership unchanged");
                    assertInventoryUnchanged(owner, inventoryBefore);
                    for (var item : added) item.discard();
                    Set<UUID> refusalBaseline = itemIds(level, body.position());
                    var refusalHands = body.getExistingDataOrNull(com.juicyslew.moonstation14.component.ModDataAttachments.LIVE_HANDS.get());
                    var refusalInventory = snapshotInventory(owner);
                    require(execute(dispatcher, "ms14 shuttle_fixture cargo ship-deadbeef", source) == 0,
                            "unknown ship grant is refused");
                    assertRefusalUnchanged(level, body, owner, refusalBaseline, refusalHands, refusalInventory);
                    require(execute(dispatcher, "ms14 shuttle_fixture cargo " + frameId,
                            level.getServer().createCommandSourceStack().withLevel(level).withPermission(2)) == 0,
                            "console without admitted body is refused");
                    assertRefusalUnchanged(level, body, owner, refusalBaseline, refusalHands, refusalInventory);
                    helper.succeed();
                } catch (RuntimeException | AssertionError failure) {
                    added.forEach(item -> { if (!item.isRemoved()) item.discard(); });
                    throw failure;
                }
            });
        });
    }

    private static Set<UUID> itemIds(net.minecraft.server.level.ServerLevel level, net.minecraft.world.phys.Vec3 center) {
        Set<UUID> ids = new HashSet<>();
        items(level, center).forEach(item -> ids.add(item.getUUID()));
        return ids;
    }

    private static java.util.List<net.minecraft.world.entity.item.ItemEntity> items(
            net.minecraft.server.level.ServerLevel level, net.minecraft.world.phys.Vec3 center) {
        var found = new java.util.ArrayList<net.minecraft.world.entity.item.ItemEntity>();
        level.getEntities(EntityTypeTest.forClass(net.minecraft.world.entity.item.ItemEntity.class),
                new AABB(center, center).inflate(8), entity -> !entity.isRemoved(), found, 33);
        if (found.size() > 32) throw new GameTestAssertException("bounded nearby-item query exceeded fixture cap (32)");
        return found;
    }

    private static void requireVelocityUnchanged(RigidBodyHandle handle, Vector3d linear, Vector3d angular, String reason) {
        require(handle.getLinearVelocity(new Vector3d()).distance(linear) < .01
                        && handle.getAngularVelocity(new Vector3d()).distance(angular) < .01,
                reason + " must not change native velocity");
    }

    private static java.util.List<net.minecraft.world.item.ItemStack> snapshotInventory(net.minecraft.server.level.ServerPlayer player) {
        var inventory = player.getInventory();
        var result = new java.util.ArrayList<net.minecraft.world.item.ItemStack>(inventory.getContainerSize());
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) result.add(inventory.getItem(slot).copy());
        return result;
    }

    private static void assertInventoryUnchanged(net.minecraft.server.level.ServerPlayer player,
                                                  java.util.List<net.minecraft.world.item.ItemStack> before) {
        var inventory = player.getInventory();
        require(inventory.getContainerSize() == before.size(), "carrier inventory slot count unchanged");
        for (int slot = 0; slot < before.size(); slot++)
            require(net.minecraft.world.item.ItemStack.matches(before.get(slot), inventory.getItem(slot)),
                    "carrier inventory slot " + slot + " unchanged");
    }

    private static void assertRefusalUnchanged(net.minecraft.server.level.ServerLevel level,
            net.minecraft.world.entity.LivingEntity body, net.minecraft.server.level.ServerPlayer owner,
            Set<UUID> itemsBefore, Object handsBefore, java.util.List<net.minecraft.world.item.ItemStack> inventoryBefore) {
        require(itemIds(level, body.position()).equals(itemsBefore), "refused grant publishes no nearby world item");
        require(body.getExistingDataOrNull(com.juicyslew.moonstation14.component.ModDataAttachments.LIVE_HANDS.get()) == handsBefore,
                "refused grant does not mutate body hands");
        assertInventoryUnchanged(owner, inventoryBefore);
    }

    private static int execute(com.mojang.brigadier.CommandDispatcher<net.minecraft.commands.CommandSourceStack> dispatcher,
                               String command, net.minecraft.commands.CommandSourceStack source) {
        try { return dispatcher.execute(command, source); }
        catch (com.mojang.brigadier.exceptions.CommandSyntaxException failure) {
            throw new GameTestAssertException("setup command parse/execute failed: " + failure.getMessage());
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new GameTestAssertException(message);
    }
}
