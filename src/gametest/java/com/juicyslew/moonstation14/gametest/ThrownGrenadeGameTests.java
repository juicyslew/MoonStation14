package com.juicyslew.moonstation14.gametest;

import com.juicyslew.moonstation14.MoonStation14;
import com.juicyslew.moonstation14.gametest.support.GameTestTags;
import com.juicyslew.moonstation14.component.ModDataComponents;
import com.juicyslew.moonstation14.entities.ModEntities;
import com.juicyslew.moonstation14.entities.ThrownGrenadeEntity;
import com.juicyslew.moonstation14.item.ModItems;
import com.juicyslew.moonstation14.ms14.explosion.grenade.core.GrenadeFuse;
import com.juicyslew.moonstation14.ms14.explosion.grenade.core.GrenadeFuseData;
import com.juicyslew.moonstation14.ms14.explosion.prototype.ExplosionData;
import com.juicyslew.moonstation14.ms14.explosion.prototype.ExplosiveSourceData;
import com.juicyslew.moonstation14.ms14.explosion.prototype.ModExplosions;
import com.juicyslew.moonstation14.ms14.explosion.prototype.ModExplosiveSources;
import com.juicyslew.moonstation14.ms14.prototype.PrototypeRuntime;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.core.Direction;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;

import java.util.Optional;
@PrefixGameTestTemplate(false)
@GameTestTags({"explosion"})
public final class ThrownGrenadeGameTests {
    private ThrownGrenadeGameTests() { }

    @GameTest(templateNamespace = "moonstation14", template = "empty", timeoutTicks = 10)
    public static void collisionLandsWithoutDetonatingAndRetainsFuse(GameTestHelper helper) {
        ServerLevel level = (ServerLevel) helper.getLevel();
        BlockPos wall = helper.absolutePos(new BlockPos(4, 2, 2));
        level.setBlock(wall, Blocks.STONE.defaultBlockState(), 3);
        ItemStack stack = new ItemStack(ModItems.GRENADE.get());
        stack.set(ModDataComponents.GRENADE_FUSE.get(), new GrenadeFuseData(GrenadeFuse.Phase.ARMED,
                level.getGameTime() + 60, false, false));
        Zombie shooter = helper.spawn(EntityType.ZOMBIE, new BlockPos(1, 1, 1));
        shooter.setNoAi(true);
        shooter.setNoGravity(true);
        ThrownGrenadeEntity grenade = new ThrownGrenadeEntity(level, shooter, stack);
        grenade.setPos(wall.getX() - .4, wall.getY() + .25, wall.getZ() + .5);
        grenade.setDeltaMovement(.6, 0, 0);
        level.addFreshEntity(grenade);
        // Drive the precise contact synchronously; do not rely on entity scheduling in the template.
        grenade.tick();
        helper.assertTrue(grenade.lastBlockHitFace() == Direction.WEST,
                "expected direct WEST-face wall collision; face=" + grenade.lastBlockHitFace()
                        + ", grenade=" + grenade.position() + ", wall=" + wall
                        + ", velocity=" + grenade.getDeltaMovement() + ", tickCount=" + grenade.tickCount);
        helper.runAfterDelay(3, () -> {
            if (grenade.isAlive()) grenade.tick();
            helper.assertTrue(grenade.isAlive(), "impact must not detonate or discard grenade");
            helper.assertTrue(level.getBlockState(wall).is(Blocks.STONE), "impact leaves block intact");
            helper.assertTrue(grenade.getItem().get(ModDataComponents.GRENADE_FUSE.get()).phase() == GrenadeFuse.Phase.ARMED,
                    "impact preserves armed fuse state");
            helper.assertTrue(level.getEntity(grenade.getUUID()) == grenade, "projectile remains registered after impact");
            helper.assertTrue(grenade.lastBlockHitFace() == Direction.WEST,
                    "fixture must hit the wall's WEST face; face=" + grenade.lastBlockHitFace()
                            + ", grenade=" + grenade.position() + ", velocity=" + grenade.getDeltaMovement()
                            + ", tickCount=" + grenade.tickCount);
            helper.assertTrue(grenade.getX() < wall.getX(), "grenade remains on the near side of the wall; grenadeX="
                    + grenade.getX() + ", wallX=" + wall.getX() + ", velocity=" + grenade.getDeltaMovement()
                    + ", position=" + grenade.position() + ", tickCount=" + grenade.tickCount);
            helper.assertTrue(grenade.getDeltaMovement().x < 0,
                    "wall impact reverses horizontal motion instead of freezing the grenade");
            helper.succeed();
        });
    }

    @GameTest(templateNamespace = "moonstation14", template = "empty", timeoutTicks = 20)
    public static void lowSpeedFloorContactSettlesAndDoesNotDetonate(GameTestHelper helper) {
        ServerLevel level = (ServerLevel) helper.getLevel();
        BlockPos floor = helper.absolutePos(new BlockPos(2, 1, 2));
        level.setBlock(floor, Blocks.STONE.defaultBlockState(), 3);
        ItemStack stack = new ItemStack(ModItems.GRENADE.get());
        stack.set(ModDataComponents.GRENADE_FUSE.get(), new GrenadeFuseData(GrenadeFuse.Phase.ARMED,
                level.getGameTime() + 100, false, false));
        Zombie shooter = helper.spawn(EntityType.ZOMBIE, new BlockPos(1, 1, 1));
        shooter.setNoAi(true);
        ThrownGrenadeEntity grenade = new ThrownGrenadeEntity(level, shooter, stack);
        grenade.setPos(floor.getX() + .5, floor.getY() + 2, floor.getZ() + .5);
        grenade.setDeltaMovement(.02, -.8, 0);
        level.addFreshEntity(grenade);
        helper.runAfterDelay(1, () -> {
            double floorTop = floor.getY() + 1;
            double firstBounceHeight = Double.NaN;
            double secondBounceHeight = Double.NaN;
            int bounceCount = 0;
            boolean trackingAscent = false;
            double ascentPeak = Double.NaN;
            for (int i = 0; i < 40 && grenade.isAlive(); i++) {
                double incomingY = grenade.getDeltaMovement().y;
                grenade.tick();
                helper.assertTrue(grenade.getY() >= floorTop - 1.0e-6,
                        "grenade must not penetrate floor on step " + i + "; y=" + grenade.getY());
                if (incomingY < 0 && grenade.lastBlockHitFace() == Direction.UP && grenade.getDeltaMovement().y > 0) {
                    if (trackingAscent) {
                        if (bounceCount == 1) firstBounceHeight = ascentPeak;
                        else if (bounceCount == 2) secondBounceHeight = ascentPeak;
                    }
                    bounceCount++;
                    trackingAscent = true;
                    ascentPeak = grenade.getY();
                } else if (trackingAscent) {
                    ascentPeak = Math.max(ascentPeak, grenade.getY());
                    if (grenade.getDeltaMovement().y <= 0) {
                        if (bounceCount == 1) firstBounceHeight = ascentPeak;
                        else if (bounceCount == 2) secondBounceHeight = ascentPeak;
                        trackingAscent = false;
                    }
                }
            }
            if (trackingAscent) {
                if (bounceCount == 1) firstBounceHeight = ascentPeak;
                else if (bounceCount == 2) secondBounceHeight = ascentPeak;
            }
            helper.assertTrue(grenade.isAlive(), "floor contact must not detonate grenade");
            helper.assertTrue(grenade.lastBlockHitFace() == Direction.UP, "must contact the floor face");
            helper.assertTrue(bounceCount >= 2, "0.8 downward impact should produce two upward bounces; count=" + bounceCount);
            helper.assertTrue(firstBounceHeight > floorTop,
                    "first impact must rebound above contact; y=" + firstBounceHeight + ", floor=" + floorTop);
            helper.assertTrue(secondBounceHeight > floorTop && secondBounceHeight < firstBounceHeight,
                    "second rebound must be above floor and lower than first; second=" + secondBounceHeight
                            + ", first=" + firstBounceHeight + ", floor=" + floorTop);
            helper.assertTrue(grenade.getDeltaMovement().y == 0,
                    "rebound should settle to rest after damping; velocity=" + grenade.getDeltaMovement());
            helper.assertTrue(grenade.getDeltaMovement().horizontalDistance() < .02,
                    "floor friction must reduce horizontal motion");
            helper.succeed();
        });
    }

    @GameTest(templateNamespace = "moonstation14", template = "empty", timeoutTicks = 10)
    public static void losingFullFloorSupportResumesGravity(GameTestHelper helper) {
        ServerLevel level = (ServerLevel) helper.getLevel();
        BlockPos floor = helper.absolutePos(new BlockPos(2, 1, 2));
        level.setBlock(floor, Blocks.STONE.defaultBlockState(), 3);
        ItemStack stack = new ItemStack(ModItems.GRENADE.get());
        stack.set(ModDataComponents.GRENADE_FUSE.get(), new GrenadeFuseData(GrenadeFuse.Phase.ARMED,
                level.getGameTime() + 100, false, false));
        Zombie shooter = helper.spawn(EntityType.ZOMBIE, new BlockPos(1, 1, 1));
        shooter.setNoAi(true);
        ThrownGrenadeEntity grenade = new ThrownGrenadeEntity(level, shooter, stack);
        grenade.setPos(floor.getX() + .5, floor.getY() + 1.15, floor.getZ() + .5);
        grenade.setDeltaMovement(0, -.1, 0);
        level.addFreshEntity(grenade);
        for (int i = 0; i < 8 && grenade.lastBlockHitFace() != Direction.UP; i++) {
            grenade.tick();
        }
        helper.assertTrue(grenade.lastBlockHitFace() == Direction.UP,
                "low-speed downward collision must establish floor rest; face=" + grenade.lastBlockHitFace()
                        + ", y=" + grenade.getY() + ", velocity=" + grenade.getDeltaMovement());
        double restingY = grenade.getY();
        grenade.tick();
        helper.assertTrue(Math.abs(grenade.getY() - restingY) < 1.0e-6,
                "full block supports grenade after actual rest; before=" + restingY + ", after=" + grenade.getY());
        level.setBlock(floor, Blocks.SMOOTH_STONE_SLAB.defaultBlockState(), 3);
        grenade.tick();
        helper.assertTrue(grenade.getDeltaMovement().y < 0,
                "gravity must resume after losing full-block support; y=" + grenade.getY()
                        + ", velocity=" + grenade.getDeltaMovement());
        boolean descended = false;
        for (int i = 0; i < 4 && grenade.isAlive(); i++) {
            grenade.tick();
            helper.assertTrue(grenade.getY() <= floor.getY() + 1,
                    "lower slab top must not support grenade at former full-block height; y=" + grenade.getY()
                            + ", velocity=" + grenade.getDeltaMovement());
            descended |= grenade.getY() < restingY;
        }
        helper.assertTrue(descended,
                "grenade must descend after losing full-block support; restingY=" + restingY
                        + ", y=" + grenade.getY() + ", velocity=" + grenade.getDeltaMovement());
        helper.succeed();
    }

    @GameTest(templateNamespace = "moonstation14", template = "empty", timeoutTicks = 120)
    public static void productionTickOrAutomaticSchedulingAfterFuseDeadlineDetonatesOnceAndBreachesStationWall(GameTestHelper helper) {
        ExplosiveSourceData sourceData = PrototypeRuntime.serverExplosiveSources().get(ModExplosiveSources.EX_GRENADE_ID);
        helper.assertTrue(sourceData != null, "live server catalog must contain bundled grenade explosive source");
        helper.assertTrue(sourceData.totalIntensity() == 380 && sourceData.intensitySlope() == 3
                        && sourceData.maxIntensity() == 10,
                "bundled grenade source must have 380/3/10 intensity; source=" + sourceData);
        ExplosionData explosionData = PrototypeRuntime.serverExplosions().get(sourceData.explosionType());
        helper.assertTrue(explosionData != null, "grenade source explosion behavior must resolve from live server catalog: "
                + sourceData.explosionType());
        helper.assertTrue(explosionData.temperatureKelvin().equals(Optional.of(800f)),
                "Default explosion must specify 800K; behavior=" + explosionData);
        helper.assertTrue(explosionData.fireStacks().equals(Optional.empty()),
                "Default explosion must omit fire stacks; behavior=" + explosionData);

        ServerLevel level = (ServerLevel) helper.getLevel();
        BlockPos source = helper.absolutePos(new BlockPos(2, 2, 2));
        BlockPos wall = source.east();
        level.setBlock(wall, com.juicyslew.moonstation14.block.ModBlocks.STEEL_WALL_BLOCK.get().defaultBlockState(), 3);
        long deadline = level.getGameTime() + 60;
        ItemStack stack = new ItemStack(ModItems.GRENADE.get());
        stack.set(ModDataComponents.GRENADE_FUSE.get(), new GrenadeFuseData(GrenadeFuse.Phase.ARMED,
                deadline, false, false));
        Zombie shooter = helper.spawn(EntityType.ZOMBIE, new BlockPos(1, 1, 1));
        shooter.setNoAi(true);
        shooter.setNoGravity(true);
        ThrownGrenadeEntity grenade = new ThrownGrenadeEntity(level, shooter, stack);
        grenade.setPos(source.getX() + .5, source.getY(), source.getZ() + .5);
        grenade.setNoGravity(true);
        grenade.setDeltaMovement(0, 0, 0);
        helper.assertTrue(level.hasChunkAt(source), "grenade source must be in loaded template chunk");
        helper.assertTrue(level.addFreshEntity(grenade), "grenade must be accepted into loaded source chunk");
        // Verify the early state independently of whether this template chunk is entity-ticking.
        helper.runAfterDelay(3, () -> {
            helper.assertTrue(grenade.isAlive(), "early callback: grenade must remain armed; worldTime="
                    + level.getGameTime() + ", deadline=" + deadline + ", tickCount=" + grenade.tickCount);
            helper.assertTrue(level.getBlockState(wall).is(com.juicyslew.moonstation14.block.ModBlocks.STEEL_WALL_BLOCK.get()),
                    "early callback: wall must remain intact; worldTime=" + level.getGameTime()
                            + ", deadline=" + deadline + ", tickCount=" + grenade.tickCount);
            helper.assertTrue(grenade.getItem().get(ModDataComponents.GRENADE_FUSE.get()).phase() == GrenadeFuse.Phase.ARMED,
                    "early callback: grenade fuse must remain armed; worldTime=" + level.getGameTime()
                            + ", deadline=" + deadline + ", tickCount=" + grenade.tickCount);
        });
        // Verifies the production server tick path when driven by the harness OR automatic
        // scheduling; this does not prove every chunk automatically ticks entities.
        helper.runAfterDelay(65, () -> {
            helper.assertTrue(level.getGameTime() > deadline, "world must pass fuse deadline; worldTime="
                    + level.getGameTime() + ", deadline=" + deadline + ", tickCount=" + grenade.tickCount);
            boolean alive = grenade.isAlive();
            boolean registered = level.getEntity(grenade.getUUID()) == grenade;
            if (alive && registered) {
                grenade.tick();
                helper.assertTrue(!grenade.isAlive(), "production tick must remove expired grenade; worldTime="
                        + level.getGameTime() + ", deadline=" + deadline + ", tickCount=" + grenade.tickCount);
            } else {
                helper.assertTrue(!alive && !registered, "automatic-scheduling branch requires grenade already removed"
                        + "; alive=" + alive + ", registered=" + registered + ", worldTime=" + level.getGameTime()
                        + ", deadline=" + deadline + ", tickCount=" + grenade.tickCount);
            }
            helper.assertTrue(!grenade.isAlive(), "expired grenade must be removed; worldTime=" + level.getGameTime()
                    + ", deadline=" + deadline + ", tickCount=" + grenade.tickCount);
            helper.assertTrue(level.getEntity(grenade.getUUID()) == null, "expired grenade is no longer registered; worldTime="
                    + level.getGameTime() + ", deadline=" + deadline + ", tickCount=" + grenade.tickCount);
            helper.assertTrue(level.getBlockState(wall).isAir(), "fuse tick breaches adjacent steel wall; worldTime="
                    + level.getGameTime() + ", deadline=" + deadline + ", tickCount=" + grenade.tickCount);
            helper.succeed();
        });
    }
}
