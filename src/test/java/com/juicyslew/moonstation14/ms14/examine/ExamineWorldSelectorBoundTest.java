package com.juicyslew.moonstation14.ms14.examine;

import com.juicyslew.moonstation14.ms14.examine.client.ExamineCandidateCycle;
import com.juicyslew.moonstation14.ms14.examine.client.ExamineSelector;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import com.juicyslew.moonstation14.ms14.hands.live.BodyItemTargetProbe;

import static org.junit.jupiter.api.Assertions.*;

class ExamineWorldSelectorBoundTest {
    private static ExamineWorldSelector.Candidate entity(int n, double distance) {
        return new ExamineWorldSelector.Candidate(ExamineTarget.entity(n + 1, UUID.nameUUIDFromBytes(("e" + n).getBytes())),
                Vec3.ZERO, distance, "item", new AABB(0, 0, 0, 1, 1, 1));
    }

    @Test void nearestAdmittedBlockSurvivesDenseEntitiesAndCanBeCycledTo() {
        var block = new ExamineWorldSelector.Candidate(ExamineTarget.block(new BlockPos(4, 0, 0)), Vec3.ZERO,
                40, "tray", new AABB(0, 0, 0, 1, 1, 1));
        ArrayList<ExamineWorldSelector.Candidate> pool = new ArrayList<>();
        for (int i = 0; i < 40; i++) pool.add(entity(i, i + 1));
        pool.add(block);
        pool.sort(Comparator.comparingDouble(ExamineWorldSelector.Candidate::distance));
        var bounded = ExamineWorldSelector.boundedCandidates(pool);
        assertEquals(32, bounded.size());
        assertTrue(bounded.contains(block));
        var cycle = new ExamineCandidateCycle();
        var converted = bounded.stream().map(c -> new ExamineSelector.Candidate(c.target(), c.hitPoint(), c.distance(), c.publicName(), c.box())).toList();
        cycle.update(converted, null, null, 0);
        var selected = cycle.update(converted, null, null, 1);
        assertEquals(block.target(), selected, "cycling over the bounded candidate set can reach the retained tray");
    }

    @Test void belowCapOrderIsUntouchedAndBlockDoesNotDisplaceEntitiesWhenPresent() {
        var a = entity(0, 1);
        var block = new ExamineWorldSelector.Candidate(ExamineTarget.block(new BlockPos(1, 0, 0)), Vec3.ZERO,
                2, "block", new AABB(0, 0, 0, 1, 1, 1));
        assertEquals(List.of(a, block), ExamineWorldSelector.boundedCandidates(List.of(a, block)));
        ArrayList<ExamineWorldSelector.Candidate> pool = new ArrayList<>();
        for (int i = 0; i < 31; i++) pool.add(entity(i, i + 1));
        pool.add(block);
        pool.sort(Comparator.comparingDouble(ExamineWorldSelector.Candidate::distance));
        var bounded = ExamineWorldSelector.boundedCandidates(pool);
        assertEquals(32, bounded.size());
        assertEquals(pool, bounded, "under-cap candidate order is unchanged");
    }

    @Test void expandedLooseItemRayHitWinsFloorBehindAndFrontOverlapHasStableOrder() {
        Vec3 origin = Vec3.ZERO, direction = new Vec3(0, 0, 1);
        AABB physicalItem = new AABB(-.1, 0, 2, .1, .2, 2.2);
        AABB itemRayBounds = physicalItem.inflate(BodyItemTargetProbe.TARGET_MARGIN);
        var itemHit = com.juicyslew.moonstation14.ms14.examine.client.ExamineRay.intersect(origin, direction, 8, itemRayBounds);
        assertNotNull(itemHit);
        assertTrue(itemHit.distance() < 2, "canonical targeting margin nominates item before the floor");
        var item = new ExamineSelector.Candidate(ExamineTarget.entity(1,
                UUID.fromString("00000000-0000-0000-0000-000000000001")), itemHit.point(), itemHit.distance(), "item", itemRayBounds, false, true);
        var floor = new ExamineSelector.Candidate(ExamineTarget.block(new BlockPos(0, 0, 2)),
                new Vec3(0, 0, 2), 2, "floor", new AABB(-1, 0, 2, 1, 1, 3));
        var frontBounds = itemRayBounds.move(0, 0, -.2);
        var frontHit = com.juicyslew.moonstation14.ms14.examine.client.ExamineRay.intersect(origin, direction, 8, frontBounds);
        assertNotNull(frontHit);
        var frontOverlap = new ExamineSelector.Candidate(ExamineTarget.entity(2,
                UUID.fromString("00000000-0000-0000-0000-000000000002")), frontHit.point(), frontHit.distance(),
                "front item", frontBounds, false, true);
        var ordered = com.juicyslew.moonstation14.ms14.examine.client.ExamineCandidateOrdering.nearest(
                List.of(floor, item, frontOverlap), 8);
        assertEquals(frontOverlap.target(), ordered.get(0).target());
        assertEquals(item.target(), ordered.get(1).target());
        assertEquals(floor.target(), ordered.get(2).target());
    }

}
