package com.juicyslew.moonstation14.ms14.hands.live;

import com.juicyslew.moonstation14.component.ModDataAttachments;
import com.juicyslew.moonstation14.ms14.hands.ItemToken;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

public final class BodyDropPlacementGameTests {
    private BodyDropPlacementGameTests() { }

    public static void bodyDropSpawnsForwardWithNaturalGravity(GameTestHelper helper) {
        Villager body = helper.spawn(EntityType.VILLAGER, new BlockPos(2, 2, 2));
        require(LiveHands.initialize(body).isPresent(), "body hands initialized");
        LiveHands initial = body.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get());
        var stored = initial.putWhole(initial.revision(), "left", new ItemToken("forward-drop"), new ItemStack(Items.STICK));
        require(stored.succeeded(), "fixture item stored");
        body.setData(ModDataAttachments.LIVE_HANDS.get(), stored.state());
        var expected = body.position().add(0, 1.125, 0).add(body.getLookAngle().scale(1.25));
        require(BodyHandItemTransfer.dropOwned(body, "left", "forward-drop", stored.state().revision(), () -> true)
                == BodyHandItemTransfer.Result.SUCCESS, "drop succeeds");
        var items = helper.getLevel().getEntitiesOfClass(ItemEntity.class, body.getBoundingBox().inflate(3),
                item -> item.getItem().is(Items.STICK));
        require(items.size() == 1, "single world item owns stack");
        ItemEntity item = items.getFirst();
        require(item.position().distanceToSqr(expected) < .07, "item starts at short hand-height aim projection");
        require(item.getDeltaMovement().dot(body.getLookAngle()) > .1,
                "air drop has deterministic forward motion and retains natural gravity");
        require(body.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get()).token("left").isEmpty(),
                "body hand no longer owns dropped stack");
        helper.succeed();
    }

    public static void upwardSurfaceDropHasNoIntentionalVelocity(GameTestHelper helper) {
        Villager body = helper.spawn(EntityType.VILLAGER, new BlockPos(2, 2, 2));
        require(LiveHands.initialize(body).isPresent(), "body hands initialized");
        body.setYRot(-90);
        body.setXRot(65);
        helper.getLevel().setBlock(body.blockPosition().offset(1, -1, 0), Blocks.STONE.defaultBlockState(), 3);
        LiveHands initial = body.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get());
        var stored = initial.putWhole(initial.revision(), "left", new ItemToken("ground-drop"), new ItemStack(Items.STICK));
        require(stored.succeeded(), "fixture item stored");
        body.setData(ModDataAttachments.LIVE_HANDS.get(), stored.state());
        require(BodyHandItemTransfer.dropOwned(body, "left", "ground-drop", stored.state().revision(), () -> true)
                == BodyHandItemTransfer.Result.SUCCESS, "ground drop succeeds");
        var items = helper.getLevel().getEntitiesOfClass(ItemEntity.class, body.getBoundingBox().inflate(5),
                item -> item.getItem().is(Items.STICK));
        require(items.size() == 1 && Math.abs(items.getFirst().getY() - (body.getY() + .001)) < .01,
                "item rests directly above hit ground surface: " + items.getFirst().position()
                        + " eye=" + body.getEyePosition() + " look=" + body.getLookAngle());
        require(items.getFirst().getDeltaMovement().lengthSqr() == 0, "surface placement has no intentional velocity");
        helper.succeed();
    }

    public static void dropFitsUnderTwoBlockRoof(GameTestHelper helper) {
        Villager body = helper.spawn(EntityType.VILLAGER, new BlockPos(2, 2, 2));
        require(LiveHands.initialize(body).isPresent(), "body hands initialized");
        body.setYRot(-90);
        body.setXRot(65);
        // Two blocks of actual body-relative headroom; the ceiling must intersect eye height,
        // while the conservative launch point remains beneath it.
        BlockPos feet = body.blockPosition();
        BlockPos roof = feet.above(2);
        BlockPos shelf = feet.offset(1, -1, 0);
        helper.getLevel().setBlock(roof, Blocks.STONE.defaultBlockState(), 3);
        helper.getLevel().setBlock(shelf, Blocks.STONE.defaultBlockState(), 3);
        require(!helper.getLevel().getBlockState(roof).getCollisionShape(helper.getLevel(), roof).isEmpty(),
                "body-relative roof has collision");
        require(roof.getY() > body.getEyeY() && roof.getY() > body.getY() + 1.125,
                "actual two-block room roof is above eye and hand-height launch");
        LiveHands initial = body.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get());
        var stored = initial.putWhole(initial.revision(), "left", new ItemToken("roof-drop"), new ItemStack(Items.STICK));
        require(stored.succeeded(), "fixture item stored");
        body.setData(ModDataAttachments.LIVE_HANDS.get(), stored.state());
        require(BodyHandItemTransfer.dropOwned(body, "left", "roof-drop", stored.state().revision(), () -> true)
                == BodyHandItemTransfer.Result.SUCCESS, "drop succeeds under roof");
        var items = helper.getLevel().getEntitiesOfClass(ItemEntity.class, body.getBoundingBox().inflate(5),
                item -> item.getItem().is(Items.STICK));
        require(items.size() == 1, "one dropped item remains in world");
        require(body.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get()).token("left").isEmpty(),
                "hand relinquishes only after placement succeeds");
        helper.succeed();
    }

    public static void groundAimIsClampedWithoutLosingDrop(GameTestHelper helper) {
        Villager body = helper.spawn(EntityType.VILLAGER, new BlockPos(2, 2, 2));
        body.setYRot(-90);
        body.setXRot(16);
        BlockPos feet = body.blockPosition();
        helper.getLevel().setBlock(feet.offset(4, -1, 0), Blocks.STONE.defaultBlockState(), 3);
        var placement = BodyItemTargetProbe.resolveDrop(helper.getLevel(), body);
        require(placement != null, "out-of-aim ground cannot deny conservation placement");
        Vec3 launch = new Vec3(body.getX(), body.getY() + 1.125, body.getZ());
        require(launch.distanceTo(placement.position()) <= 3.5,
                "ground beyond the 3.5-block aim ray is not selected");
        helper.succeed();
    }

    /** Exercises the trusted carrier-exclusion seam with a real spawned entity, not the authenticated player route. */
    public static void crowdingFallsBackWithoutDenyingPlacement(GameTestHelper helper) {
        var level = helper.getLevel();
        Villager body = helper.spawn(EntityType.VILLAGER, new BlockPos(2, 1, 2));
        Villager carrier = helper.spawn(EntityType.VILLAGER, new BlockPos(2, 1, 2));
        body.setYRot(-90);
        require(BodyItemTargetProbe.resolveDrop(level, body) != null,
                "overlapping entity cannot deny a loaded-body fallback");
        require(BodyItemTargetProbe.resolveDrop(level, body, carrier) != null,
                "designated carrier still permits an aimed placement");
        Villager unrelated = helper.spawn(EntityType.VILLAGER, new BlockPos(2, 1, 2));
        require(BodyItemTargetProbe.resolveDrop(level, body, carrier) != null,
                "unrelated crowding cannot deny a loaded-body fallback");
        unrelated.discard();
        helper.succeed();
    }

    /** Trusted fixture seam only; no authenticated ServerPlayer/controller is synthesized here. */
    public static void crowdedCarrierDropUsesFallback(GameTestHelper helper) {
        Villager body = helper.spawn(EntityType.VILLAGER, new BlockPos(2, 1, 2));
        Villager carrier = helper.spawn(EntityType.VILLAGER, new BlockPos(2, 1, 2));
        body.setYRot(-90);
        require(LiveHands.initialize(body).isPresent(), "body hands initialized");
        LiveHands initial = body.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get());
        var stored = initial.putWhole(initial.revision(), "left", new ItemToken("carrier-drop"),
                new ItemStack(Items.STICK));
        require(stored.succeeded(), "fixture item stored");
        body.setData(ModDataAttachments.LIVE_HANDS.get(), stored.state());
        require(BodyHandItemTransfer.dropOwned(body, "left", "carrier-drop", stored.state().revision(), () -> true)
                        == BodyHandItemTransfer.Result.SUCCESS,
                "crowding cannot deny an authorized drop");
        LiveHands after = body.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get());
        require(after.token("left").isEmpty(), "successful drop clears the hand");
        var items = helper.getLevel().getEntitiesOfClass(ItemEntity.class, body.getBoundingBox().inflate(3),
                item -> item.getItem().is(Items.STICK));
        require(items.size() == 1 && items.getFirst().getItem().getCount() == 1,
                "exactly one unchanged stack is spawned despite overlap");
        carrier.discard();
        helper.succeed();
    }

    public static void enclosedDropUsesLoadedBodyFallback(GameTestHelper helper) {
        Villager body = helper.spawn(EntityType.VILLAGER, new BlockPos(2, 1, 2));
        body.setYRot(-90);
        BlockPos feet = body.blockPosition();
        for (int x = -1; x <= 1; x++) for (int z = -1; z <= 1; z++) {
            helper.getLevel().setBlock(feet.offset(x, -1, z), Blocks.STONE.defaultBlockState(), 3);
            helper.getLevel().setBlock(feet.offset(x, 2, z), Blocks.STONE.defaultBlockState(), 3);
        }
        for (int y = 0; y <= 1; y++) {
            helper.getLevel().setBlock(feet.offset(-1, y, 0), Blocks.STONE.defaultBlockState(), 3);
            helper.getLevel().setBlock(feet.offset(1, y, 0), Blocks.STONE.defaultBlockState(), 3);
            helper.getLevel().setBlock(feet.offset(0, y, -1), Blocks.STONE.defaultBlockState(), 3);
            helper.getLevel().setBlock(feet.offset(0, y, 1), Blocks.STONE.defaultBlockState(), 3);
        }
        for (BlockPos wall : new BlockPos[]{feet.offset(-1, 0, 0), feet.offset(1, 0, 0),
                feet.offset(0, 0, -1), feet.offset(0, 0, 1)})
            require(!helper.getLevel().getBlockState(wall).getCollisionShape(helper.getLevel(), wall).isEmpty(),
                    "body-relative enclosing wall has collision: " + wall);
        BlockPos roof = feet.above(2);
        require(!helper.getLevel().getBlockState(roof).getCollisionShape(helper.getLevel(), roof).isEmpty(),
                "body-relative enclosure roof has collision");
        require(LiveHands.initialize(body).isPresent(), "body hands initialized");
        LiveHands initial = body.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get());
        var stored = initial.putWhole(initial.revision(), "left", new ItemToken("enclosed-drop"), new ItemStack(Items.STICK));
        require(stored.succeeded(), "fixture item stored");
        body.setData(ModDataAttachments.LIVE_HANDS.get(), stored.state());
        require(BodyHandItemTransfer.dropOwned(body, "left", "enclosed-drop", stored.state().revision(), () -> true)
                == BodyHandItemTransfer.Result.SUCCESS, "enclosure cannot deny drop");
        require(helper.getLevel().getEntitiesOfClass(ItemEntity.class, body.getBoundingBox().inflate(3),
                item -> item.getItem().is(Items.STICK)).size() == 1, "one item spawned in loaded enclosure");
        helper.succeed();
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
