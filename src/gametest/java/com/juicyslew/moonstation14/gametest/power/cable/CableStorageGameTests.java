package com.juicyslew.moonstation14.gametest.power.cable;

import com.juicyslew.moonstation14.MoonStation14;
import com.juicyslew.moonstation14.block.ModBlocks;
import com.juicyslew.moonstation14.component.ModDataAttachments;
import com.juicyslew.moonstation14.item.ModItems;
import com.juicyslew.moonstation14.ms14.power.cable.CableStorage;
import com.juicyslew.moonstation14.ms14.power.cable.CableTier;
import com.juicyslew.moonstation14.ms14.power.floor.StationFloorBlock;
import com.mojang.authlib.GameProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.GameTestAssertException;

import java.util.UUID;

@GameTestHolder(MoonStation14.MOD_ID)
@PrefixGameTestTemplate(false)
public final class CableStorageGameTests {
    private CableStorageGameTests() { }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void servicePersistsAllFacesAndFloorFinishDoesNotMutateRecords(GameTestHelper helper) {
        ServerLevel level = (ServerLevel) helper.getLevel();
        BlockPos host = helper.absolutePos(new BlockPos(2, 1, 2));
        level.setBlock(host, ModBlocks.STATION_FLOOR.get().defaultBlockState(), 3);
        var chunk = level.getChunkAt(host);
        for (Direction face : Direction.values())
            require(CableStorage.place(level, host, face, CableTier.HV), "place face " + face);
        require(!CableStorage.place(level, host, Direction.UP, CableTier.HV), "same tier cannot duplicate on a face");
        require(CableStorage.place(level, host, Direction.UP, CableTier.MV), "different tier coexists on a face");
        require(CableStorage.place(level, host, Direction.UP, CableTier.APC), "LV tier coexists on a face");
        BlockPos sibling = host.above();
        require((sibling.getX() >> 4) == (host.getX() >> 4)
                && (sibling.getZ() >> 4) == (host.getZ() >> 4), "sibling shares owning chunk");
        level.setBlock(sibling, ModBlocks.STATION_FLOOR.get().defaultBlockState(), 3);
        require(CableStorage.place(level, sibling, Direction.SOUTH, CableTier.MV), "place sibling host cable");
        var before = chunk.getExistingDataOrNull(ModDataAttachments.POWER_CABLE_CHUNK.get()).snapshot();
        require(before.stream().filter(record -> record.x() == (host.getX() & 15)
                && record.y() == host.getY() && record.z() == (host.getZ() & 15)).count() == 8,
                "all six HV faces and both additional UP tiers are present in the raw attachment");
        require(before.stream().anyMatch(record -> record.x() == (sibling.getX() & 15)
                && record.y() == sibling.getY() && record.z() == (sibling.getZ() & 15)
                && record.face() == Direction.SOUTH && record.tier() == CableTier.MV),
                "sibling cable is present in the raw attachment before replacement");
        level.setBlock(host, level.getBlockState(host).setValue(StationFloorBlock.TILE_FINISH,
                StationFloorBlock.TileFinish.STEEL), 3);
        require(before.equals(chunk.getExistingDataOrNull(ModDataAttachments.POWER_CABLE_CHUNK.get()).snapshot()),
                "floor finish preserves cable records");
        require(chunk.isUnsaved(), "cable mutation dirties owning chunk for save");
        level.setBlock(host, Blocks.STONE.defaultBlockState(), 3);
        var expected = before.stream().filter(record -> record.x() != (host.getX() & 15)
                || record.y() != host.getY() || record.z() != (host.getZ() & 15)).toList();
        require(chunk.getExistingDataOrNull(ModDataAttachments.POWER_CABLE_CHUNK.get()).snapshot().equals(expected),
                "owned floor replacement prunes exactly its host, preserving sibling and other chunk records");
        require(chunk.getExistingDataOrNull(ModDataAttachments.POWER_CABLE_CHUNK.get()).snapshot().stream()
                .anyMatch(record -> record.x() == (sibling.getX() & 15)
                && record.y() == sibling.getY() && record.z() == (sibling.getZ() & 15)
                && record.face() == Direction.SOUTH && record.tier() == CableTier.MV),
                "neighboring host cable remains in the raw attachment");
        level.setBlock(sibling, Blocks.STONE.defaultBlockState(), 3);
        var afterCleanup = expected.stream().filter(record -> record.x() != (sibling.getX() & 15)
                || record.y() != sibling.getY() || record.z() != (sibling.getZ() & 15)).toList();
        require(chunk.getExistingDataOrNull(ModDataAttachments.POWER_CABLE_CHUNK.get()).snapshot().equals(afterCleanup),
                "sibling cleanup removes only its record");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void spoolAndCutterConserveExactTierOnExactFace(GameTestHelper helper) {
        ServerLevel level = (ServerLevel) helper.getLevel();
        BlockPos host = helper.absolutePos(new BlockPos(2, 1, 2));
        level.setBlock(host, Blocks.STONE.defaultBlockState(), 3);
        Player player = player(level, helper.absolutePos(new BlockPos(2, 2, 2)));
        ItemStack spool = new ItemStack(ModItems.HV_CABLE_SPOOL.get(), 2);
        player.setItemInHand(InteractionHand.MAIN_HAND, spool);
        require(use(ModItems.HV_CABLE_SPOOL.get(), level, player, spool, host, Direction.NORTH).consumesAction(), "place cable");
        require(spool.getCount() == 1 && CableStorage.get(level, host, Direction.NORTH) == CableTier.HV,
                "one spool is consumed and tier stored");
        require(player.getInventory().add(spool), "store the remaining spool before switching tools");
        ItemStack cutter = new ItemStack(ModItems.CABLE_CUTTER.get());
        player.setItemInHand(InteractionHand.MAIN_HAND, cutter);
        require(use(ModItems.CABLE_CUTTER.get(), level, player, cutter, host, Direction.SOUTH) == InteractionResult.FAIL,
                "cutting a different face fails");
        require(use(ModItems.CABLE_CUTTER.get(), level, player, cutter, host, Direction.NORTH).consumesAction(), "cut exact face");
        require(CableStorage.get(level, host, Direction.NORTH) == null, "record removed");
        int spoolsAfterCut = matchingSpools(player, level, host);
        require(spoolsAfterCut == 1,
                "cut returns exactly one matching spool to inventory or world (found=" + spoolsAfterCut + ")");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void ambiguousCutRequiresMatchingOffhandCoil(GameTestHelper helper) {
        ServerLevel level = (ServerLevel) helper.getLevel();
        BlockPos host = helper.absolutePos(new BlockPos(2, 1, 2));
        level.setBlock(host, Blocks.STONE.defaultBlockState(), 3);
        require(CableStorage.place(level, host, Direction.NORTH, CableTier.HV), "place HV");
        require(CableStorage.place(level, host, Direction.NORTH, CableTier.MV), "place MV alongside HV");
        Player player = player(level, helper.absolutePos(new BlockPos(2, 2, 2)));
        ItemStack cutter = new ItemStack(ModItems.CABLE_CUTTER.get());
        player.setItemInHand(InteractionHand.MAIN_HAND, cutter);
        require(use(ModItems.CABLE_CUTTER.get(), level, player, cutter, host, Direction.NORTH) == InteractionResult.FAIL,
                "ambiguous cut fails closed");
        require(CableStorage.has(level, host, Direction.NORTH, CableTier.HV)
                && CableStorage.has(level, host, Direction.NORTH, CableTier.MV), "ambiguous cut preserves both cables");
        player.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(ModItems.APC_CABLE_SPOOL.get()));
        require(use(ModItems.CABLE_CUTTER.get(), level, player, cutter, host, Direction.NORTH) == InteractionResult.FAIL,
                "wrong offhand tier cannot cut a sibling");
        require(CableStorage.has(level, host, Direction.NORTH, CableTier.HV)
                && CableStorage.has(level, host, Direction.NORTH, CableTier.MV), "wrong selector preserves both cables");
        player.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(ModItems.MV_CABLE_SPOOL.get()));
        require(use(ModItems.CABLE_CUTTER.get(), level, player, cutter, host, Direction.NORTH).consumesAction(),
                "matching offhand spool selects tier");
        require(CableStorage.has(level, host, Direction.NORTH, CableTier.HV)
                && !CableStorage.has(level, host, Direction.NORTH, CableTier.MV), "only selected cable removed");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void onlyFullSturdyHostsAreEligible(GameTestHelper helper) {
        ServerLevel level = (ServerLevel) helper.getLevel();
        BlockPos stone = helper.absolutePos(new BlockPos(2, 1, 2));
        BlockPos slab = helper.absolutePos(new BlockPos(4, 1, 2));
        level.setBlock(stone, Blocks.STONE.defaultBlockState(), 3);
        level.setBlock(slab, Blocks.OAK_SLAB.defaultBlockState(), 3);
        for (Direction face : Direction.values())
            require(CableStorage.eligible(level, stone, face), "full stone accepts " + face);
        require(!CableStorage.eligible(level, slab, Direction.UP), "partial slab rejected");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void changedHostCannotBeCutOrRefunded(GameTestHelper helper) {
        ServerLevel level = (ServerLevel) helper.getLevel();
        BlockPos host = helper.absolutePos(new BlockPos(2, 1, 2));
        level.setBlock(host, Blocks.STONE.defaultBlockState(), 3);
        Player player = player(level, helper.absolutePos(new BlockPos(2, 2, 2)));
        ItemStack spool = new ItemStack(ModItems.HV_CABLE_SPOOL.get());
        player.setItemInHand(InteractionHand.MAIN_HAND, spool);
        require(use(ModItems.HV_CABLE_SPOOL.get(), level, player, spool, host, Direction.NORTH).consumesAction(), "place cable");
        level.setBlock(host, Blocks.DIRT.defaultBlockState(), 3);
        ItemStack cutter = new ItemStack(ModItems.CABLE_CUTTER.get());
        player.setItemInHand(InteractionHand.MAIN_HAND, cutter);
        require(use(ModItems.CABLE_CUTTER.get(), level, player, cutter, host, Direction.NORTH) == InteractionResult.FAIL,
                "changed host record cannot be removed or refunded");
        require(CableStorage.get(level, host, Direction.NORTH) == null, "stale record is pruned on access");
        require(level.getEntitiesOfClass(ItemEntity.class, new AABB(host).inflate(2), entity ->
                entity.getItem().is(ModItems.HV_CABLE_SPOOL.get())).isEmpty(), "stale cable yields no refund");
        helper.succeed();
    }

    private static InteractionResult use(Item item, ServerLevel level, Player player, ItemStack stack, BlockPos pos, Direction face) {
        return item.useOn(new UseOnContext(level, player, InteractionHand.MAIN_HAND, stack,
                new BlockHitResult(Vec3.atCenterOf(pos), face, pos, false)));
    }
    private static Player player(ServerLevel level, BlockPos pos) {
        Player player = new Player(level, pos, 0f, new GameProfile(UUID.randomUUID(), "cable-test")) {
            @Override public boolean isCreative() { return false; }
            @Override public boolean isSpectator() { return false; }
        };
        player.setPos(pos.getX(), pos.getY(), pos.getZ());
        return player;
    }
    private static int matchingSpools(Player player, ServerLevel level, BlockPos host) {
        int inventory = player.getInventory().items.stream()
                .filter(stack -> stack.is(ModItems.HV_CABLE_SPOOL.get()))
                .mapToInt(ItemStack::getCount).sum();
        int dropped = level.getEntitiesOfClass(ItemEntity.class, new AABB(host).inflate(2), entity ->
                entity.getItem().is(ModItems.HV_CABLE_SPOOL.get())).stream()
                .mapToInt(entity -> entity.getItem().getCount()).sum();
        return inventory + dropped;
    }
    private static void require(boolean value, String message) {
        if (!value) throw new GameTestAssertException(message);
    }
}
