package com.juicyslew.moonstation14.gametest.power.cable;

import com.juicyslew.moonstation14.MoonStation14;
import com.juicyslew.moonstation14.block.ModBlocks;
import com.juicyslew.moonstation14.component.ModDataAttachments;
import com.juicyslew.moonstation14.item.ModItems;
import com.juicyslew.moonstation14.ms14.power.cable.CableStorage;
import com.juicyslew.moonstation14.ms14.power.cable.CableTier;
import com.juicyslew.moonstation14.ms14.power.cable.CableCutSelection;
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
import com.juicyslew.moonstation14.gametest.support.GameTestTags;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.GameTestAssertException;

import java.util.UUID;

@GameTestTags({"power", "storage", "persistence", "cable-cut-visible"})
@PrefixGameTestTemplate(false)
public final class CableStorageGameTests {
    private CableStorageGameTests() { }

    @GameTest(template = "empty", timeoutTicks = 20, templateNamespace = "moonstation14")
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

    @GameTest(template = "empty", timeoutTicks = 20, templateNamespace = "moonstation14")
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
        ItemStack cutter = new ItemStack(ModItems.WIRECUTTER.get());
        player.setItemInHand(InteractionHand.MAIN_HAND, cutter);
        require(use(ModItems.WIRECUTTER.get(), level, player, cutter, host, Direction.SOUTH) == InteractionResult.FAIL,
                "cutting a different face fails");
        require(useAt(ModItems.WIRECUTTER.get(), level, player, cutter, host, Direction.NORTH, CableTier.HV).consumesAction(), "cut exact visible HV cap");
        require(CableStorage.get(level, host, Direction.NORTH) == null, "record removed");
        int spoolsAfterCut = matchingSpools(player, level, host);
        require(spoolsAfterCut == 1,
                "cut returns exactly one matching spool to inventory or world (found=" + spoolsAfterCut + ")");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 20, templateNamespace = "moonstation14")
    public static void visibleLaneSelectsTierWithoutOffhandCoil(GameTestHelper helper) {
        ServerLevel level = (ServerLevel) helper.getLevel();
        BlockPos host = helper.absolutePos(new BlockPos(2, 1, 2));
        level.setBlock(host, Blocks.STONE.defaultBlockState(), 3);
        require(CableStorage.place(level, host, Direction.NORTH, CableTier.HV), "place HV");
        require(CableStorage.place(level, host, Direction.NORTH, CableTier.MV), "place MV alongside HV");
        Player player = player(level, helper.absolutePos(new BlockPos(2, 2, 2)));
        ItemStack cutter = new ItemStack(ModItems.WIRECUTTER.get());
        player.setItemInHand(InteractionHand.MAIN_HAND, cutter);
        require(useAt(ModItems.WIRECUTTER.get(), level, player, cutter, host, Direction.NORTH, CableTier.HV).consumesAction(),
                "HV lane selects tier on ambiguous face");
        require(!CableStorage.has(level, host, Direction.NORTH, CableTier.HV)
                && CableStorage.has(level, host, Direction.NORTH, CableTier.MV), "only HV lane cable removed");
        player.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(ModItems.APC_CABLE_SPOOL.get()));
        require(useAt(ModItems.WIRECUTTER.get(), level, player, cutter, host, Direction.NORTH, CableTier.MV).consumesAction(),
                "visible MV lane selects tier despite mismatched offhand");
        require(!CableStorage.has(level, host, Direction.NORTH, CableTier.MV), "only selected cable removed");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 20, templateNamespace = "moonstation14")
    public static void allVisibleLanesRefundExactTierAndPreserveOtherFace(GameTestHelper helper) {
        ServerLevel level = (ServerLevel) helper.getLevel();
        BlockPos host = helper.absolutePos(new BlockPos(2, 1, 2));
        level.setBlock(host, Blocks.STONE.defaultBlockState(), 3);
        for (CableTier tier : CableTier.values())
            require(CableStorage.place(level, host, Direction.NORTH, tier), "place NORTH " + tier);
        require(CableStorage.place(level, host, Direction.SOUTH, CableTier.APC), "place separate SOUTH cable");
        Player player = player(level, helper.absolutePos(new BlockPos(2, 2, 2)));
        ItemStack cutter = new ItemStack(ModItems.WIRECUTTER.get());
        player.setItemInHand(InteractionHand.MAIN_HAND, cutter);

        int[] baselineRefunds = new int[CableTier.values().length];
        for (CableTier tier : CableTier.values())
            baselineRefunds[tier.ordinal()] = matchingSpools(player, level, host, tier);
        for (Direction face : Direction.values()) {
            Vec3 physicalHit = laneHit(host, face, CableTier.APC);
            double expectedPlane = switch (face.getAxis()) {
                case X -> host.getX() + (face.getStepX() > 0 ? 1 : 0);
                case Y -> host.getY() + (face.getStepY() > 0 ? 1 : 0);
                case Z -> host.getZ() + (face.getStepZ() > 0 ? 1 : 0);
            };
            double actualPlane = switch (face.getAxis()) {
                case X -> physicalHit.x; case Y -> physicalHit.y; case Z -> physicalHit.z;
            };
            require(Math.abs(actualPlane - expectedPlane) < 1.0e-9,
                    "lane hit lies on outward physical plane for " + face);
        }
        for (CableTier tier : CableTier.values()) {
            int[] stepBeforeRefunds = new int[CableTier.values().length];
            for (CableTier countTier : CableTier.values())
                stepBeforeRefunds[countTier.ordinal()] = matchingSpools(player, level, host, countTier);
            require(useAt(ModItems.WIRECUTTER.get(), level, player, cutter, host, Direction.NORTH, tier).consumesAction(),
                    "cut visible " + tier + " lane");
            require(!CableStorage.has(level, host, Direction.NORTH, tier), "selected NORTH " + tier + " removed");
            for (CableTier remaining : CableTier.values())
                require(CableStorage.has(level, host, Direction.NORTH, remaining) == (remaining.ordinal() > tier.ordinal()),
                        "exact remaining NORTH records after " + tier + ": " + remaining);
            require(CableStorage.get(level, host, Direction.SOUTH) == CableTier.APC, "other face unchanged");
            require(matchingSpools(player, level, host, tier) == stepBeforeRefunds[tier.ordinal()] + 1,
                    "exactly one " + tier + " refund added");
            for (CableTier other : CableTier.values()) if (other != tier)
                require(matchingSpools(player, level, host, other) == stepBeforeRefunds[other.ordinal()],
                        "no wrong-tier refund added: " + other);
            require(useAt(ModItems.WIRECUTTER.get(), level, player, cutter, host, Direction.NORTH, tier) == InteractionResult.FAIL,
                    "repeat cut of removed " + tier + " lane fails");
            require(matchingSpools(player, level, host, tier) == stepBeforeRefunds[tier.ordinal()] + 1,
                    "repeat cut adds no refund");
        }
        for (CableTier tier : CableTier.values())
            require(matchingSpools(player, level, host, tier) == baselineRefunds[tier.ordinal()] + 1,
                    "one total refund for each tier after all cuts: " + tier);
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 20, templateNamespace = "moonstation14")
    public static void cableCutRejectsMissNullHitAndCoveredFloorFace(GameTestHelper helper) {
        ServerLevel level = (ServerLevel) helper.getLevel();
        BlockPos host = helper.absolutePos(new BlockPos(2, 1, 2));
        level.setBlock(host, Blocks.STONE.defaultBlockState(), 3);
        require(CableStorage.place(level, host, Direction.NORTH, CableTier.APC), "place APC");
        Player player = player(level, helper.absolutePos(new BlockPos(2, 2, 2)));
        ItemStack cutter = new ItemStack(ModItems.WIRECUTTER.get());
        player.setItemInHand(InteractionHand.MAIN_HAND, cutter);
        require(useHit(ModItems.WIRECUTTER.get(), level, player, cutter, host, Direction.NORTH,
                Vec3.atCenterOf(host)) == InteractionResult.FAIL, "off-lane center miss fails");
        require(CableStorage.has(level, host, Direction.NORTH, CableTier.APC), "miss does not mutate record");
        require(CableCutSelection.selectAt(level, host, Direction.NORTH, null).refusal()
                == CableCutSelection.Refusal.MISSING, "null-hit helper rejects without fallback");
        require(matchingSpools(player, level, host, CableTier.APC) == 0, "miss/null produce no refund");

        BlockPos floor = host.east();
        level.setBlock(floor, ModBlocks.STATION_FLOOR.get().defaultBlockState()
                .setValue(StationFloorBlock.TILE_FINISH, StationFloorBlock.TileFinish.STEEL), 3);
        require(CableStorage.place(level, floor, Direction.UP, CableTier.APC), "place under finished floor");
        require(useAt(ModItems.WIRECUTTER.get(), level, player, cutter, floor, Direction.UP, CableTier.APC)
                == InteractionResult.FAIL, "covered station-floor UP face denied");
        require(CableStorage.has(level, floor, Direction.UP, CableTier.APC), "covered cable remains unchanged");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 20, templateNamespace = "moonstation14")
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

    @GameTest(template = "empty", timeoutTicks = 20, templateNamespace = "moonstation14")
    public static void changedHostCannotBeCutOrRefunded(GameTestHelper helper) {
        ServerLevel level = (ServerLevel) helper.getLevel();
        BlockPos host = helper.absolutePos(new BlockPos(2, 1, 2));
        level.setBlock(host, Blocks.STONE.defaultBlockState(), 3);
        Player player = player(level, helper.absolutePos(new BlockPos(2, 2, 2)));
        ItemStack spool = new ItemStack(ModItems.HV_CABLE_SPOOL.get());
        player.setItemInHand(InteractionHand.MAIN_HAND, spool);
        require(use(ModItems.HV_CABLE_SPOOL.get(), level, player, spool, host, Direction.NORTH).consumesAction(), "place cable");
        level.setBlock(host, Blocks.DIRT.defaultBlockState(), 3);
        ItemStack cutter = new ItemStack(ModItems.WIRECUTTER.get());
        player.setItemInHand(InteractionHand.MAIN_HAND, cutter);
        require(use(ModItems.WIRECUTTER.get(), level, player, cutter, host, Direction.NORTH) == InteractionResult.FAIL,
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
    private static InteractionResult useAt(Item item, ServerLevel level, Player player, ItemStack stack,
                                           BlockPos pos, Direction face, CableTier tier) {
        return useHit(item, level, player, stack, pos, face, laneHit(pos, face, tier));
    }
    private static Vec3 laneHit(BlockPos pos, Direction face, CableTier tier) {
        var corners = com.juicyslew.moonstation14.ms14.power.cable.CableVisualGeometry.centerCorners(
                new com.juicyslew.moonstation14.ms14.power.topology.CableFaceNode(pos, face, tier), .01F);
        float x = 0, y = 0, z = 0;
        for (var corner : corners) { x += corner.x(); y += corner.y(); z += corner.z(); }
        var point = new com.juicyslew.moonstation14.ms14.power.cable.CableVisualGeometry.Vector3(
                x / corners.size(), y / corners.size(), z / corners.size());
        double normal = .5;
        return Vec3.atCenterOf(pos).add(point.x() + face.getStepX() * normal,
                point.y() + face.getStepY() * normal, point.z() + face.getStepZ() * normal);
    }
    private static InteractionResult useHit(Item item, ServerLevel level, Player player, ItemStack stack,
                                            BlockPos pos, Direction face, Vec3 hit) {
        return item.useOn(new UseOnContext(level, player, InteractionHand.MAIN_HAND, stack,
                new BlockHitResult(hit, face, pos, false)));
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
    private static int matchingSpools(Player player, ServerLevel level, BlockPos host, CableTier tier) {
        Item item = switch (tier) {
            case HV -> ModItems.HV_CABLE_SPOOL.get();
            case MV -> ModItems.MV_CABLE_SPOOL.get();
            case APC -> ModItems.APC_CABLE_SPOOL.get();
        };
        int inventory = player.getInventory().items.stream().filter(stack -> stack.is(item)
                ).mapToInt(ItemStack::getCount).sum();
        return inventory + level.getEntitiesOfClass(ItemEntity.class, new AABB(host).inflate(2), entity ->
                entity.getItem().is(item)).stream().mapToInt(entity -> entity.getItem().getCount()).sum();
    }
    private static void require(boolean value, String message) {
        if (!value) throw new GameTestAssertException(message);
    }
}
