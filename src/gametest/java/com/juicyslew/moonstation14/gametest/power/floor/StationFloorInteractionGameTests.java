package com.juicyslew.moonstation14.gametest.power.floor;

import com.juicyslew.moonstation14.MoonStation14;
import com.juicyslew.moonstation14.block.ModBlocks;
import com.juicyslew.moonstation14.item.ModItems;
import com.juicyslew.moonstation14.ms14.power.floor.StationFloorBlock;
import com.mojang.authlib.GameProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;

import java.util.List;
import java.util.UUID;

@GameTestHolder(MoonStation14.MOD_ID)
@PrefixGameTestTemplate(false)
public final class StationFloorInteractionGameTests {
    private StationFloorInteractionGameTests() { }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void topFaceTileAndPryMutateSameRegisteredBlockAndConsumeOrReturnOneTile(GameTestHelper helper) {
        ServerLevel level = (ServerLevel) helper.getLevel();
        BlockPos floor = floor(helper, new BlockPos(2, 1, 2));
        Player player = player(level, helper.absolutePos(new BlockPos(2, 2, 2)), false);
        ItemStack tiles = new ItemStack(ModItems.STATION_FLOOR_TILE.get(), 2);
        player.setItemInHand(InteractionHand.MAIN_HAND, tiles);

        InteractionResult installed = use(level, player, tiles, floor, Direction.UP,
                ModItems.STATION_FLOOR_TILE.get());
        require(installed.consumesAction(), "top-face tile installation consumes the interaction");
        require(level.getBlockState(floor).is(ModBlocks.STATION_FLOOR.get()), "installation preserves host block identity");
        require(level.getBlockState(floor).getValue(StationFloorBlock.TILE_FINISH)
                == StationFloorBlock.TileFinish.STEEL, "installation updates the actual world blockstate");
        require(tiles.getCount() == 1, "survival installation consumes exactly one tile");

        ItemStack pry = new ItemStack(ModItems.STATION_FLOOR_TILE_PRY.get());
        player.setItemInHand(InteractionHand.MAIN_HAND, pry);
        int durabilityBefore = pry.getDamageValue();
        InteractionResult removed = use(level, player, pry, floor, Direction.UP,
                ModItems.STATION_FLOOR_TILE_PRY.get());
        require(removed.consumesAction(), "top-face pry consumes the interaction");
        require(level.getBlockState(floor).is(ModBlocks.STATION_FLOOR.get()), "prying preserves host block identity");
        require(level.getBlockState(floor).getValue(StationFloorBlock.TILE_FINISH)
                == StationFloorBlock.TileFinish.NONE, "prying resets only the finish blockstate");
        List<ItemEntity> drops = tileDrops(level, floor);
        require(drops.size() == 1 && drops.get(0).getItem().getCount() == 1
                        && drops.get(0).getItem().is(ModItems.STATION_FLOOR_TILE.get()),
                "successful pry drops exactly one registered tile item");
        require(pry.getDamageValue() == durabilityBefore + 1,
                "successful pry damages the registered floor-tile pry tool exactly once");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void invalidHostsAndNonTopFacesDoNotChangeFloorOrItems(GameTestHelper helper) {
        ServerLevel level = (ServerLevel) helper.getLevel();
        BlockPos floor = floor(helper, new BlockPos(2, 1, 2));
        BlockPos wrongHost = helper.absolutePos(new BlockPos(5, 1, 2));
        level.setBlock(wrongHost, Blocks.STONE.defaultBlockState(), 3);
        Player player = player(level, helper.absolutePos(new BlockPos(2, 2, 2)), false);
        ItemStack tile = new ItemStack(ModItems.STATION_FLOOR_TILE.get(), 2);
        ItemStack pry = new ItemStack(ModItems.STATION_FLOOR_TILE_PRY.get());

        for (Direction side : List.of(Direction.NORTH, Direction.DOWN)) {
            require(use(level, player, tile, floor, side, ModItems.STATION_FLOOR_TILE.get()) == InteractionResult.FAIL,
                    "tile placement rejects " + side + " face");
        }
        level.setBlock(floor, level.getBlockState(floor).setValue(StationFloorBlock.TILE_FINISH,
                StationFloorBlock.TileFinish.STEEL), 3);
        for (Direction side : List.of(Direction.NORTH, Direction.DOWN)) {
            require(use(level, player, pry, floor, side, ModItems.STATION_FLOOR_TILE_PRY.get()) == InteractionResult.FAIL,
                    "prying rejects " + side + " face");
        }
        require(use(level, player, tile, wrongHost, Direction.UP, ModItems.STATION_FLOOR_TILE.get())
                        == InteractionResult.FAIL,
                "tile placement rejects a non-station-floor host");
        require(use(level, player, pry, wrongHost, Direction.UP, ModItems.STATION_FLOOR_TILE_PRY.get())
                        == InteractionResult.FAIL,
                "prying rejects a non-station-floor host");
        require(tile.getCount() == 2 && level.getBlockState(floor).getValue(StationFloorBlock.TILE_FINISH)
                        == StationFloorBlock.TileFinish.STEEL,
                "rejected interactions preserve tile count and existing floor finish");
        require(pry.getDamageValue() == 0, "rejected face and host pries do not damage the pry tool");
        require(tileDrops(level, floor).isEmpty(), "rejected interactions do not drop tiles");
        require(use(level, null, tile, floor, Direction.UP, ModItems.STATION_FLOOR_TILE.get())
                        == InteractionResult.FAIL,
                "server-side placement safely rejects a missing player");
        require(use(level, null, pry, floor, Direction.UP, ModItems.STATION_FLOOR_TILE_PRY.get()) == InteractionResult.FAIL,
                "server-side floor-tile pry safely rejects a missing player");
        Player unauthorized = player(level, helper.absolutePos(new BlockPos(2, 2, 2)), false, false);
        int durabilityBefore = pry.getDamageValue();
        require(use(level, unauthorized, pry, floor, Direction.UP, ModItems.STATION_FLOOR_TILE_PRY.get()) == InteractionResult.FAIL,
                "floor-tile pry rejects an unauthorized player");
        require(pry.getDamageValue() == durabilityBefore && tileDrops(level, floor).isEmpty(),
                "unauthorized pry consumes no durability and drops no tile");
        level.setBlock(floor, level.getBlockState(floor).setValue(StationFloorBlock.TILE_FINISH,
                StationFloorBlock.TileFinish.NONE), 3);
        require(use(level, player, pry, floor, Direction.UP, ModItems.STATION_FLOOR_TILE_PRY.get()) == InteractionResult.FAIL,
                "floor-tile pry rejects an untiled station floor");
        require(pry.getDamageValue() == durabilityBefore, "failed untiled pry does not damage the pry tool");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void creativePlacementDoesNotConsumeAndCreativePryStillReturnsTile(GameTestHelper helper) {
        ServerLevel level = (ServerLevel) helper.getLevel();
        BlockPos floor = floor(helper, new BlockPos(2, 1, 2));
        Player player = player(level, helper.absolutePos(new BlockPos(2, 2, 2)), true);
        ItemStack tile = new ItemStack(ModItems.STATION_FLOOR_TILE.get());
        player.setItemInHand(InteractionHand.MAIN_HAND, tile);
        require(use(level, player, tile, floor, Direction.UP, ModItems.STATION_FLOOR_TILE.get()).consumesAction(),
                "creative top-face installation succeeds");
        require(tile.getCount() == 1, "creative placement leaves its tile stack untouched");

        ItemStack pry = new ItemStack(ModItems.STATION_FLOOR_TILE_PRY.get());
        player.setItemInHand(InteractionHand.MAIN_HAND, pry);
        require(use(level, player, pry, floor, Direction.UP, ModItems.STATION_FLOOR_TILE_PRY.get()).consumesAction(),
                "creative top-face pry succeeds");
        require(tileDrops(level, floor).size() == 1,
                "creative pry follows the same returned-tile drop behavior");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void whiteTileIsIndependentAndPryReturnsExactVariant(GameTestHelper helper) {
        ServerLevel level = (ServerLevel) helper.getLevel();
        BlockPos floor = floor(helper, new BlockPos(2, 1, 2));
        Player player = player(level, helper.absolutePos(new BlockPos(2, 2, 2)), false);
        ItemStack white = new ItemStack(ModItems.STATION_FLOOR_TILE_WHITE.get(), 2);
        ItemStack steel = new ItemStack(ModItems.STATION_FLOOR_TILE.get());

        require(use(level, player, white, floor, Direction.UP, ModItems.STATION_FLOOR_TILE_WHITE.get()).consumesAction(),
                "white tile installs");
        require(level.getBlockState(floor).getValue(StationFloorBlock.TILE_FINISH) == StationFloorBlock.TileFinish.WHITE,
                "white tile selects its own blockstate variant");
        require(use(level, player, steel, floor, Direction.UP, ModItems.STATION_FLOOR_TILE.get()) == InteractionResult.FAIL,
                "an occupied floor rejects replacement until it is pried");
        require(white.getCount() == 1 && steel.getCount() == 1, "occupied replacement consumes no item");

        ItemStack pry = new ItemStack(ModItems.STATION_FLOOR_TILE_PRY.get());
        require(use(level, player, pry, floor, Direction.UP, ModItems.STATION_FLOOR_TILE_PRY.get()).consumesAction(),
                "white tile can be pried");
        List<ItemEntity> drops = level.getEntitiesOfClass(ItemEntity.class, new AABB(floor).inflate(1.5),
                entity -> entity.getItem().is(ModItems.STATION_FLOOR_TILE_WHITE.get()));
        require(drops.size() == 1 && drops.get(0).getItem().getCount() == 1,
                "prying white finish returns exactly one white tile item");
        require(level.getBlockState(floor).getValue(StationFloorBlock.TILE_FINISH) == StationFloorBlock.TileFinish.NONE,
                "prying white finish resets only tile state");
        helper.succeed();
    }

    private static BlockPos floor(GameTestHelper helper, BlockPos relative) {
        BlockPos pos = helper.absolutePos(relative);
        helper.getLevel().setBlock(pos, ModBlocks.STATION_FLOOR.get().defaultBlockState(), 3);
        require(helper.getLevel().getBlockState(pos).is(ModBlocks.STATION_FLOOR.get()), "fixture must place registered floor");
        return pos;
    }

    private static InteractionResult use(ServerLevel level, Player player, ItemStack stack, BlockPos pos,
                                         Direction face, net.minecraft.world.item.Item item) {
        BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(pos), face, pos, false);
        return item.useOn(new UseOnContext(level, player, InteractionHand.MAIN_HAND, stack, hit));
    }

    private static Player player(ServerLevel level, BlockPos pos, boolean creative) {
        return player(level, pos, creative, true);
    }

    private static Player player(ServerLevel level, BlockPos pos, boolean creative, boolean canUseItem) {
        Player player = new Player(level, pos, 0f, new GameProfile(UUID.randomUUID(), "station-floor-test")) {
            @Override public boolean isCreative() { return creative; }
            @Override public boolean isSpectator() { return false; }
            @Override public boolean mayUseItemAt(BlockPos blockPos, Direction face, ItemStack stack) {
                return canUseItem;
            }
        };
        player.getAbilities().instabuild = creative;
        player.setPos(pos.getX(), pos.getY(), pos.getZ());
        return player;
    }

    private static List<ItemEntity> tileDrops(ServerLevel level, BlockPos pos) {
        return level.getEntitiesOfClass(ItemEntity.class, new AABB(pos).inflate(1.5),
                entity -> entity.getItem().is(ModItems.STATION_FLOOR_TILE.get()));
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new GameTestAssertException(message);
    }
}
