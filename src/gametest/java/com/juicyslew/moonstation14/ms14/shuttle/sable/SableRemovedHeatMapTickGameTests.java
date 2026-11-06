package com.juicyslew.moonstation14.ms14.shuttle.sable;

import com.juicyslew.moonstation14.gametest.support.GameTestTags;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import org.joml.Vector3d;

/** Verifies the Sable heat-map owner lifecycle guard against its real manager tick. */
@GameTestTags({"shuttle-block-updates", "shuttle-removed-heatmap"})
@PrefixGameTestTemplate(false)
public final class SableRemovedHeatMapTickGameTests {
    private SableRemovedHeatMapTickGameTests() { }

    @GameTest(templateNamespace = "moonstation14", template = "empty", timeoutTicks = 80)
    public static void activeHeatMapRunsAndRemovedOwnerTickIsCancelled(GameTestHelper helper) {
        var level = helper.getLevel();
        var authored = SableFixturePlot.allocateAndAuthor(level, new Vector3d(0.5, 80, 0.5), plot -> {
            BlockPos origin = plot.storageOrigin();
            plot.ensureStorageFootprint(origin, 4, 3, 4);
            BlockPos root = origin.offset(1, 1, 1);
            plot.setStorageBlock(root, Blocks.IRON_BLOCK.defaultBlockState());
            plot.setStorageBlock(root.east(2), Blocks.IRON_BLOCK.defaultBlockState()); // tail before its bridge
            return origin;
        });
        var owner = authored.plot().subLevel();
        var manager = owner.getHeatMapManager();
        BlockPos root = authored.result().offset(1, 1, 1);
        BlockPos tail = root.east(2);
        BlockPos bridge = root.east();
        helper.assertTrue(heat(owner, tail) == 0,
                "isolated tail has no heat before a connecting block is placed");
        authored.plot().setStorageBlock(bridge, Blocks.IRON_BLOCK.defaultBlockState());
        manager.tick();
        helper.assertTrue(!owner.isRemoved() && heat(owner, tail) > 0,
                "active-owner SDK propagation assigns heat through the newly connected tail");

        authored.plot().close();
        helper.assertTrue(owner.isRemoved(), "fixture cleanup marks this owner removed");
        manager.tick(); // Exact engine callback entry point; cancellation must precede stale split work.
        helper.runAtTickTime(1, () -> {
            helper.assertTrue(level.getServer().isSameThread(), "server remains healthy on the next scheduled tick");
            helper.succeed();
        });
    }

    private static short heat(dev.ryanhcode.sable.sublevel.ServerSubLevel owner, BlockPos pos) {
        SectionPos section = SectionPos.of(pos);
        var plot = owner.getPlot();
        var holder = plot.getChunkHolder(plot.toLocal(section.chunk()));
        if (holder == null) return 0;
        var heatSection = holder.getHeatSection(section.y());
        return heatSection == null ? 0 : heatSection.get(pos.getX() & 15, pos.getY() & 15, pos.getZ() & 15);
    }
}
