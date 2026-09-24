package com.juicyslew.moonstation14.gametest;

import com.juicyslew.moonstation14.MoonStation14;
import com.juicyslew.moonstation14.block.ModBlocks;
import com.juicyslew.moonstation14.block.block_entity.PuddleBlockEntity;
import com.juicyslew.moonstation14.component.ModDataAttachments;
import com.juicyslew.moonstation14.ms14.MS14Bridges;
import com.juicyslew.moonstation14.ms14.MS14Provider;
import com.juicyslew.moonstation14.ms14.reagent.ModReagents;
import com.juicyslew.moonstation14.ms14.reagent.ReagentAttachment;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Husk;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;

@GameTestHolder(MoonStation14.MOD_ID)
@PrefixGameTestTemplate(false)
public final class PuddleNoContactGameTest {
    private PuddleNoContactGameTest() { }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void entityInsideDoesNotConsumeOrApplyPuddleEffects(GameTestHelper helper) {
        BlockPos relativePos = new BlockPos(2, 1, 2);
        helper.setBlock(relativePos.below(), Blocks.STONE);
        helper.setBlock(relativePos, ModBlocks.PUDDLE.get().defaultBlockState());
        BlockPos pos = helper.absolutePos(relativePos);
        PuddleBlockEntity puddle = requirePuddle(helper.getLevel().getBlockEntity(pos));
        ReagentAttachment contents = new ReagentAttachment();
        // Keep the 20-unit total at the overflow threshold: larger fixtures can spread by normal puddle flow,
        // which changes the source independently of entity contact and is not what this test measures.
        contents.specificAdd(ModReagents.createKey("bicaridine"), 10f, puddle.getCapacity());
        contents.specificAdd(ModReagents.createKey("polytrinicacid"), 10f, puddle.getCapacity());
        MS14Provider.update(puddle, MS14Bridges.REAGENT, contents);

        Husk target = helper.spawn(EntityType.HUSK, new BlockPos(3, 1, 2));
        target.setNoAi(true);
        target.setNoGravity(true);
        float health = target.getHealth();
        target.setPos(pos.getX() + 0.5, pos.getY() + 0.1, pos.getZ() + 0.5);

        // Let the registered block receive normal entityInside callbacks while the living entity stands in it.
        helper.startSequence().thenExecuteAfter(2, () -> {
            requireNear(10f, quantity(puddle, "bicaridine"), "bicaridine must remain in puddle");
            requireNear(10f, quantity(puddle, "polytrinicacid"), "polytrinicacid must remain in puddle");
            requireNear(health, target.getHealth(), "puddle contact must not damage entity");
            require(target.getExistingDataOrNull(ModDataAttachments.DAMAGE.get()) == null,
                    "puddle contact must not create damage state");
            require(target.getExistingDataOrNull(ModDataAttachments.STOMACH.get()) == null,
                    "puddle contact must not create stomach state");
            require(target.getExistingDataOrNull(ModDataAttachments.REAGENT.get()) == null,
                    "puddle contact must not create body reagent state");
            helper.succeed();
        });
    }

    private static PuddleBlockEntity requirePuddle(net.minecraft.world.level.block.entity.BlockEntity blockEntity) {
        require(blockEntity instanceof PuddleBlockEntity, "puddle block entity was not created");
        return (PuddleBlockEntity) blockEntity;
    }

    private static float quantity(PuddleBlockEntity puddle, String id) {
        return MS14Provider.get(puddle, MS14Bridges.REAGENT).getMap()
                .getOrDefault(ModReagents.createKey(id), 0f);
    }

    private static void requireNear(float expected, float actual, String message) {
        if (Math.abs(expected - actual) > .0001f) {
            throw new GameTestAssertException(message + ": expected " + expected + ", got " + actual);
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new GameTestAssertException(message);
    }
}
