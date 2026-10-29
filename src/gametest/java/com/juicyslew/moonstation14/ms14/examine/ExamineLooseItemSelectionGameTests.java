package com.juicyslew.moonstation14.ms14.examine;

import com.juicyslew.moonstation14.entities.LooseItemEntity;
import com.juicyslew.moonstation14.gametest.support.GameTestTags;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.Set;

/** Native-world regressions for loose-item examine nomination and occlusion. */
@PrefixGameTestTemplate(false)
@GameTestTags("examine-loose-item-selection")
public final class ExamineLooseItemSelectionGameTests {
    private ExamineLooseItemSelectionGameTests() { }

    @GameTest(template="empty", templateNamespace="moonstation14", timeoutTicks=20)
    public static void loosePileUsesFrontGeometryAndHonorsOpaqueOcclusion(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos floor = helper.absolutePos(new BlockPos(2, 1, 2));
        double x = floor.getX() + .5, z = floor.getZ() + .5;
        Vec3 origin = helper.absoluteVec(new Vec3(2.5, 4.5, 2.5)), down = new Vec3(0, -1, 0);
        level.setBlock(floor, Blocks.STONE.defaultBlockState(), 3);
        ArrayList<net.minecraft.world.entity.Entity> spawned = new ArrayList<>();
        try {
            LooseItemEntity back = spawn(level, new Vec3(x, floor.getY() + 1.12, z), Items.WHEAT, spawned);
            LooseItemEntity front = spawn(level, new Vec3(x, floor.getY() + 1.3, z), Items.STONE, spawned);
            var hits = ExamineWorldSelector.select(level, origin, down, 8, Set.of());
            int frontIndex = index(hits, front), backIndex = index(hits, back), floorIndex = indexBlock(hits, floor);
            require(frontIndex >= 0 && backIndex >= 0, "pile candidates present: front=" + front.position() + " back=" + back.position());
            require(frontIndex < backIndex, "front overlapping pile member sorts first");
            require(floorIndex >= 0, "floor candidate present at " + floor);
            require(frontIndex < floorIndex, "front item is nominated before floor; front=" + front.position() + " floor=" + floor);

            BlockPos window = floor.above(2);
            level.setBlock(window, Blocks.GLASS.defaultBlockState(), 3);
            var throughGlass = ExamineWorldSelector.select(level, origin, down, 8, Set.of());
            require(index(throughGlass, front) >= 0 && throughGlass.stream().anyMatch(c -> c.target().kind() == ExamineTarget.Kind.BLOCK
                    && c.target().blockPos().equals(window)), "transparent window remains a cycle candidate with item");
            var clientCandidates = throughGlass.stream().map(c -> new com.juicyslew.moonstation14.ms14.examine.client.ExamineSelector.Candidate(
                    c.target(), c.hitPoint(), c.distance(), c.publicName(), c.box(), c.transparent(), c.looseItem())).toList();
            var cycle = new com.juicyslew.moonstation14.ms14.examine.client.ExamineCandidateCycle();
            require(front.getUUID().equals(cycle.update(clientCandidates, null, null, 0).entityUuid()),
                    "automatic choice through transparent window remains the front item");

            level.setBlock(window, Blocks.STONE.defaultBlockState(), 3);
            var blocked = ExamineWorldSelector.select(level, origin, down, 8, Set.of());
            require(index(blocked, front) < 0 && index(blocked, back) < 0, "opaque block before pile occludes loose items");
            level.setBlock(window, Blocks.AIR.defaultBlockState(), 3);

            var miss = ExamineWorldSelector.select(level, origin.add(2, 0, 0), down, 8, Set.of());
            require(index(miss, front) < 0 && index(miss, back) < 0, "ray miss nominates neither pile item");

            ItemEntity ordinary = new ItemEntity(level, x, floor.getY() + 1.5, z, new ItemStack(Items.APPLE));
            ordinary.setDeltaMovement(Vec3.ZERO);
            require(level.addFreshEntity(ordinary), "ordinary entity fixture spawns"); spawned.add(ordinary);
            var withOrdinary = ExamineWorldSelector.select(level, origin, down, 8, Set.of());
            var ordinaryCandidate = withOrdinary.stream().filter(c -> c.target().kind() == ExamineTarget.Kind.ENTITY
                    && c.target().entityId() == ordinary.getId()).findFirst().orElseThrow();
            require(ordinaryCandidate.box().equals(ordinary.getBoundingBox()), "non-loose item retains raw entity bounds");
        } finally {
            for (var entity : spawned) if (!entity.isRemoved()) entity.discard();
            level.setBlock(floor.above(2), Blocks.AIR.defaultBlockState(), 3);
            level.setBlock(floor, Blocks.AIR.defaultBlockState(), 3);
        }
        helper.succeed();
    }

    private static LooseItemEntity spawn(ServerLevel level, Vec3 position, net.minecraft.world.item.Item item,
                                         ArrayList<net.minecraft.world.entity.Entity> spawned) {
        LooseItemEntity entity = LooseItemEntity.create(level, position, new ItemStack(item));
        entity.setNoGravity(true);
        if (!level.addFreshEntity(entity)) throw new GameTestAssertException("loose item fixture spawns");
        spawned.add(entity);
        return entity;
    }

    private static int index(java.util.List<ExamineWorldSelector.Candidate> candidates, LooseItemEntity entity) {
        for (int i = 0; i < candidates.size(); i++) if (candidates.get(i).target().kind() == ExamineTarget.Kind.ENTITY
                && candidates.get(i).target().entityUuid().equals(entity.getUUID())) return i;
        return -1;
    }
    private static int indexBlock(java.util.List<ExamineWorldSelector.Candidate> candidates, BlockPos pos) {
        for (int i = 0; i < candidates.size(); i++) if (candidates.get(i).target().kind() == ExamineTarget.Kind.BLOCK
                && candidates.get(i).target().blockPos().equals(pos)) return i;
        return -1;
    }
    private static void require(boolean condition, String message) {
        if (!condition) throw new GameTestAssertException(message);
    }
}
