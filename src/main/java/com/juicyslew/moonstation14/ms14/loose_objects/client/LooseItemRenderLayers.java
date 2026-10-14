package com.juicyslew.moonstation14.ms14.loose_objects.client;

import com.juicyslew.moonstation14.entities.LooseItemEntity;
import com.juicyslew.moonstation14.ms14.loose_objects.LooseObjectProfile;
import net.minecraft.world.phys.AABB;

import java.util.*;

/** Client-only render ordering for settled, visually overlapping loose objects. */
public final class LooseItemRenderLayers {
    public static final double MAX_RISE = .03;
    private static final double SAME_GROUND = .02;

    private LooseItemRenderLayers() { }

    /** Geometry snapshot deliberately contains presentation footprint, never native entity bounds. */
    public record Footprint(UUID id, double x, double z, double baseY, double halfX, double halfZ) { }

    public static boolean overlaps(Footprint a, Footprint b) {
        return Math.abs(a.baseY() - b.baseY()) <= SAME_GROUND
                && Math.abs(a.x() - b.x()) < a.halfX() + b.halfX()
                && Math.abs(a.z() - b.z()) < a.halfZ() + b.halfZ();
    }

    /** Ground-height then UUID ordering assigns each connected component strictly ordered render heights. */
    public static double offset(UUID id, Collection<Footprint> component) {
        if (component.size() < 2) return 0;
        var sorted = component.stream().sorted(Comparator.comparingDouble(Footprint::baseY)
                .thenComparing(Footprint::id)).toList();
        for (int i = 0; i < sorted.size(); i++) if (sorted.get(i).id().equals(id))
            return (i + 1.0) / (sorted.size() + 1.0) * MAX_RISE;
        return 0;
    }

    public static Footprint snapshot(LooseItemEntity entity) {
        LooseObjectProfile p = entity.profile();
        double hx = Math.max(entity.getBbWidth(), p.visual().spriteScaleX()) / 2.0;
        double hz = Math.max(entity.getBbWidth(), p.visual().spriteScaleY()) / 2.0;
        return new Footprint(entity.getUUID(), entity.getX(), entity.getZ(), entity.getY(), hx, hz);
    }

    /** Resolve the full transitive component through bounded, loaded-world queries; no retained cache. */
    public static double offset(LooseItemEntity root) {
        if (!root.settled() || root.level().isClientSide == false || root.isRemoved()) return 0;
        var level = root.level();
        var snapshots = new HashMap<UUID, Footprint>();
        var entities = new HashMap<UUID, LooseItemEntity>();
        var queue = new ArrayDeque<LooseItemEntity>();
        queue.add(root);
        while (!queue.isEmpty()) {
            var current = queue.removeFirst();
            if (!current.settled() || current.isRemoved() || entities.putIfAbsent(current.getUUID(), current) != null) continue;
            var here = snapshot(current); snapshots.put(here.id(), here);
            // Profile visual dimensions are bounded by the typed profile codec (<=4); query only loaded client entities.
            var box = new AABB(current.getX()-4, current.getY()-.02, current.getZ()-4,
                    current.getX()+4, current.getY()+.02, current.getZ()+4);
            for (var candidate : level.getEntitiesOfClass(LooseItemEntity.class, box,
                    e -> e != current && e.settled() && !e.isRemoved())) {
                var other = snapshot(candidate);
                if (overlaps(here, other) && !entities.containsKey(other.id())) queue.addLast(candidate);
            }
        }
        return offset(root.getUUID(), snapshots.values());
    }
}
