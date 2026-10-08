package com.juicyslew.moonstation14.ms14.doors;

import com.juicyslew.moonstation14.block.ModBlocks;
import com.juicyslew.moonstation14.gametest.support.GameTestTags;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Geometry-only tool reach proof; it deliberately does not synthesize player authority. */
@GameTestTags({"airlock"})
@PrefixGameTestTemplate(false)
public final class AirlockToolAccessGameTests {
    private AirlockToolAccessGameTests() { }

    @GameTest(templateNamespace="moonstation14",template="atmos_large_empty",batch="airlock_tool_access")
    public static void upperHalfAdmissionUsesSameRootAndRejectsReplacementOrOcclusion(GameTestHelper helper) {
        var level=helper.getLevel();BlockPos root=helper.absolutePos(new BlockPos(5,1,5));
        var lower=ModBlocks.AIRLOCK.get().defaultBlockState();
        level.setBlock(root,lower,3);level.setBlock(root.above(),lower.setValue(AirlockBlock.HALF,DoubleBlockHalf.UPPER),3);
        var old=(AirlockBlockEntity)level.getBlockEntity(root);require(old!=null,"root controller placed");
        Villager body=EntityType.VILLAGER.create(level);require(body!=null,"geometry body created");
        body.setNoAi(true);body.setNoGravity(true);body.setPos(root.getX()+1.5,root.getY(),root.getZ()+.5);level.addFreshEntity(body);

        BlockPos hit=AirlockToolLineOfSight.tracedControllerHalf(body,level,root,old,old.identity());
        require(root.above().equals(hit),"standing one block away, eye ray admits the upper half of this exact root");
        require(!AirlockToolLineOfSight.canReachRoot(body,level,root,old,java.util.UUID.randomUUID()),"stale controller identity is rejected");
        var openLower=lower.setValue(AirlockBlock.OPEN,true);
        level.setBlock(root,openLower,3);level.setBlock(root.above(),openLower.setValue(AirlockBlock.HALF,DoubleBlockHalf.UPPER),3);
        require(root.above().equals(AirlockToolLineOfSight.tracedControllerHalf(body,level,root,old,old.identity())),
                "outline ray remains targetable when open door collision is empty");
        level.setBlock(root,lower,3);level.setBlock(root.above(),lower.setValue(AirlockBlock.HALF,DoubleBlockHalf.UPPER),3);

        body.setPos(root.getX()+2.5,root.getY(),root.getZ()+.5);
        level.setBlock(root.east(),Blocks.STONE.defaultBlockState(),3);
        require(!AirlockToolLineOfSight.canReachRoot(body,level,root,old,old.identity()),"intervening opaque block rejects the ray");
        level.setBlock(root.east(),Blocks.AIR.defaultBlockState(),3);

        level.setBlock(root,Blocks.AIR.defaultBlockState(),3);
        level.setBlock(root.above(),Blocks.AIR.defaultBlockState(),3);
        level.setBlock(root,lower,3);level.setBlock(root.above(),lower.setValue(AirlockBlock.HALF,DoubleBlockHalf.UPPER),3);
        var replacement=(AirlockBlockEntity)level.getBlockEntity(root);require(replacement!=null&&replacement!=old,"root replacement creates a new controller");
        require(!AirlockToolLineOfSight.canReachRoot(body,level,root,old,old.identity()),"removed/replaced root fails exact-controller validation");
        require(AirlockToolLineOfSight.canReachRoot(body,level,root,replacement,replacement.identity()),"new controller is reachable through its own footprint");
        body.discard();helper.succeed();
    }

    private static void require(boolean condition,String message){if(!condition)throw new GameTestAssertException(message);}
}
