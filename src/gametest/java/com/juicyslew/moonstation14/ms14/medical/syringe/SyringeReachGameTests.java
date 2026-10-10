package com.juicyslew.moonstation14.ms14.medical.syringe;

import com.juicyslew.moonstation14.gametest.support.GameTestTags;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestTags({"syringe", "medical-treatment", "interaction-reach"})
@PrefixGameTestTemplate(false)
public final class SyringeReachGameTests {
    private SyringeReachGameTests() { }

    @GameTest(template="empty", templateNamespace="moonstation14", timeoutTicks=20)
    public static void productionSyringeReachUsesThreeBlockRootAndPhysicalLos(GameTestHelper h) {
        Mob actor = body(h, new BlockPos(2, 1, 2));
        Mob target = body(h, new BlockPos(4, 1, 2));
        Vec3 origin = actor.position();
        place(target, origin.add(2.5, 0, 0));
        require(SyringeService.inReach(actor, target), "production syringe reach admits 2.5-block roots");
        place(target, origin.add(3, 0, 0));
        require(SyringeService.inReach(actor, target), "production syringe reach includes exact 3-block boundary");
        place(target, origin.add(3.05, 0, 0));
        require(!SyringeService.inReach(actor, target), "production syringe reach rejects roots beyond 3 blocks");
        place(target, origin.add(2.5, 0, 0));
        h.getLevel().setBlock(actor.blockPosition().offset(1, 1, 0), Blocks.STONE.defaultBlockState(), 3);
        require(!SyringeService.inReach(actor, target), "production syringe reach rejects wall-blocked physical eye ray");
        require(SyringeService.inReach(actor, actor), "self-injection remains in range");
        h.succeed();
    }

    private static Mob body(GameTestHelper h, BlockPos pos) {
        Mob body = EntityType.VILLAGER.create(h.getLevel());
        require(body != null, "villager created"); body.setNoAi(true); body.setNoGravity(true);
        BlockPos absolute = h.absolutePos(pos);
        body.setPos(absolute.getX() + .5, absolute.getY(), absolute.getZ() + .5);
        require(h.getLevel().addFreshEntity(body), "villager added to loaded test world"); return body;
    }
    private static void place(Mob body, Vec3 position) { body.setPos(position.x, position.y, position.z); }
    private static void require(boolean condition, String message) {
        if (!condition) throw new GameTestAssertException(message);
    }
}
