package com.juicyslew.moonstation14.gametest;

import com.juicyslew.moonstation14.MoonStation14;
import com.juicyslew.moonstation14.block.ModBlocks;
import com.juicyslew.moonstation14.block.block_entity.PuddleBlockEntity;
import com.juicyslew.moonstation14.block.custom.PuddleBlock;
import com.juicyslew.moonstation14.ms14.MS14Bridges;
import com.juicyslew.moonstation14.ms14.MS14Provider;
import com.juicyslew.moonstation14.ms14.prototype.PrototypeRuntime;
import com.juicyslew.moonstation14.ms14.reagent.ModReagents;
import com.juicyslew.moonstation14.ms14.reagent.ReagentAttachment;
import com.juicyslew.moonstation14.ms14.reagent.ReagentComponent;
import net.minecraft.core.BlockPos;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;

import java.util.Map;

@GameTestHolder(MoonStation14.MOD_ID)
@PrefixGameTestTemplate(false)
public final class PuddleRenderStateGameTests {
    private PuddleRenderStateGameTests() { }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void providerUpdatesRenderStateForTinyAndEmptyPuddleVolume(GameTestHelper helper) {
        BlockPos relativePos = new BlockPos(2, 1, 2);
        helper.setBlock(relativePos.below(), net.minecraft.world.level.block.Blocks.STONE);
        helper.setBlock(relativePos, ModBlocks.PUDDLE.get().defaultBlockState());
        BlockPos pos = helper.absolutePos(relativePos);
        PuddleBlockEntity puddle = requirePuddle(helper.getLevel().getBlockEntity(pos));
        var reagentKey = ModReagents.createKey("polytrinicacid");
        var catalog = PrototypeRuntime.serverReagents();
        var prototype = catalog.get(reagentKey.location());
        require(prototype != null, "server reagent prototype not loaded: polytrinicacid");

        MS14Provider.update(puddle, MS14Bridges.REAGENT,
                new ReagentAttachment(Map.of(reagentKey, 20f)));
        ReagentAttachment tiny = new ReagentAttachment(Map.of(reagentKey, .25f));
        var beforeTiny = MS14Provider.snapshot(MS14Provider.get(puddle, MS14Bridges.REAGENT));
        require(MS14Provider.updateIfChanged(puddle, MS14Bridges.REAGENT, beforeTiny, tiny),
                "provider must persist the changed tiny-volume attachment");
        int tinyFillLevel = helper.getLevel().getBlockState(pos).getValue(PuddleBlock.FILL_LEVEL);
        require(tinyFillLevel == 1,
                "positive tiny volume must select visible fill level one; got " + tinyFillLevel);
        require(MS14Provider.get(puddle, MS14Bridges.REAGENT).getMap().equals(Map.of(reagentKey, .25f)),
                "tiny-volume attachment must retain exactly .25 units of polytrinicacid");
        int expectedColor = 0xFF000000 | (prototype.color() & 0x00FFFFFF);
        require(ReagentComponent.getBlendedColorFromCatalog(
                        MS14Provider.get(puddle, MS14Bridges.REAGENT).getMap(), catalog) == expectedColor,
                "pure tiny-volume puddle tint must resolve to its prototype color");

        ReagentAttachment empty = new ReagentAttachment();
        var beforeEmpty = MS14Provider.snapshot(MS14Provider.get(puddle, MS14Bridges.REAGENT));
        require(MS14Provider.updateIfChanged(puddle, MS14Bridges.REAGENT, beforeEmpty, empty),
                "provider must persist the transition to an empty attachment");
        require(helper.getLevel().getBlockState(pos).isAir()
                        && helper.getLevel().getBlockEntity(pos) == null,
                "committing an empty attachment must remove the puddle block and entity");
        require(MS14Provider.get(puddle, MS14Bridges.REAGENT).getMap().isEmpty(),
                "empty transition must clear the puddle attachment");
        require(ReagentComponent.getBlendedColorFromCatalog(
                        MS14Provider.get(puddle, MS14Bridges.REAGENT).getMap(), catalog) == -1,
                "empty puddle must use the pure catalog no-tint value");
        helper.succeed();
    }

    private static PuddleBlockEntity requirePuddle(net.minecraft.world.level.block.entity.BlockEntity blockEntity) {
        require(blockEntity instanceof PuddleBlockEntity, "puddle block entity was not created");
        return (PuddleBlockEntity) blockEntity;
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new GameTestAssertException(message);
    }
}
