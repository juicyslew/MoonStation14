package com.juicyslew.moonstation14.ms14.examine.client;

import com.juicyslew.moonstation14.ms14.examine.ExamineTarget;
import com.juicyslew.moonstation14.ms14.examine.ExamineWorldSelector;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import java.util.List;
import java.util.Set;

/** Client adapter retaining render-distance policy and legacy candidate type. */
public final class ExamineSelector {
    public static final int MAX_CANDIDATES = ExamineWorldSelector.MAX_CANDIDATES;
    public static final double MAX_VISIBLE_RANGE = ExamineWorldSelector.MAX_VISIBLE_RANGE;
    public record Candidate(ExamineTarget target, Vec3 hitPoint, double distance, String publicName, AABB box,
                            boolean transparent, boolean looseItem) {
        public Candidate(ExamineTarget target, Vec3 hitPoint, double distance, String publicName, AABB box, boolean transparent) {
            this(target, hitPoint, distance, publicName, box, transparent, false);
        }
        public Candidate(ExamineTarget target, Vec3 hitPoint, double distance, String publicName, AABB box) {
            this(target, hitPoint, distance, publicName, box, false, false);
        }
    }
    private ExamineSelector() { }
    public static List<Candidate> select(Level level, Vec3 rayOrigin, Vec3 rayDirection, double range,
                                         Set<Integer> ignoredEntityIds) {
        int renderChunks = net.minecraft.client.Minecraft.getInstance().options.renderDistance().get();
        double limit = Math.min(range, Math.max(2, renderChunks) * 16.0);
        return ExamineWorldSelector.select(level, rayOrigin, rayDirection, limit, ignoredEntityIds).stream()
                .map(c -> new Candidate(c.target(), c.hitPoint(), c.distance(), c.publicName(), c.box(), c.transparent(), c.looseItem())).toList();
    }
}
