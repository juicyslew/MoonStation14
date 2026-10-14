package com.juicyslew.moonstation14.ms14.singularity.containment;

import com.juicyslew.moonstation14.block.ModBlocks;
import com.juicyslew.moonstation14.block.block_entity.FieldGeneratorBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import java.util.ArrayList;
import java.util.List;

/** Shared registered eight-corner enclosure fixture for containment and startup GameTests. */
public final class ContainmentFixture {
    private static final int[][] CORNERS={{1,1,1},{5,1,1},{1,5,1},{5,5,1},{1,1,5},{5,1,5},{1,5,5},{5,5,5}};
    private ContainmentFixture() { }
    public static List<FieldGeneratorBlockEntity> placeBox(GameTestHelper helper) {
        return placeBox(helper, new BlockPos(0, 0, 0));
    }
    public static List<FieldGeneratorBlockEntity> placeBox(GameTestHelper helper, BlockPos origin) {
        List<FieldGeneratorBlockEntity> result=new ArrayList<>();
        for(int[] c:CORNERS) {
            BlockPos local=origin.offset(c[0],c[1],c[2]); helper.setBlock(local,ModBlocks.FIELD_GENERATOR.get());
            BlockPos absolute=helper.absolutePos(local);
            if(!(helper.getLevel().getBlockEntity(absolute) instanceof FieldGeneratorBlockEntity be))
                throw new GameTestAssertException("registered field block creates adapter entity");
            be.receiveEmitterBolt(); be.receiveEmitterBolt(); be.toggleEnabled(); result.add(be);
        }
        FieldContainmentRuntime.refresh(helper.getLevel());
        return List.copyOf(result);
    }
}
