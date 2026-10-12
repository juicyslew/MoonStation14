package com.juicyslew.moonstation14.ms14.doors;

import com.juicyslew.moonstation14.ms14.character.CharacterIdentitySystem;
import com.juicyslew.moonstation14.ms14.character.components.DoorBumpOpenerComponent;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** Server movement-contact seam. Invoked before the requested body move by the movement integration. */
public final class AirlockBumpSystem {
    static record ContactMemo(java.util.UUID root, long revision, boolean paidPowerReady) { }
    private static final java.util.Map<Entity, java.util.Map<java.util.UUID, ContactMemo>> LAST_CONTACT = java.util.Collections.synchronizedMap(new java.util.WeakHashMap<>());
    private AirlockBumpSystem() { }

    static boolean isBumpOpener(net.minecraft.world.entity.LivingEntity actor){
        return actor!=null&&CharacterIdentitySystem.resolveForActor(actor)
                .filter(data->data.component(DoorBumpOpenerComponent.class).isPresent()).isPresent();
    }

    /** Opens only the first blocking collision encountered along this move; never queries unloaded chunks. */
    public static boolean beforeMove(Entity actor, Vec3 requested) {
        if (actor == null || requested == null || !(actor.level() instanceof ServerLevel level)
                || actor.isRemoved() || actor.noPhysics || !level.getServer().isSameThread()
                || Math.abs(requested.x) > 4 || Math.abs(requested.y) > 4 || Math.abs(requested.z) > 4
                || !(actor instanceof net.minecraft.world.entity.LivingEntity living)
                || !isBumpOpener(living)) return false;

        // Vanilla resolves vertical motion separately before horizontal collision. Ignore
        // gravity's simultaneous Y component here so a supporting floor cannot mask a door.
        Vec3 horizontal = new Vec3(requested.x, 0, requested.z);
        if (horizontal.lengthSqr() < 1.0E-12) { LAST_CONTACT.remove(actor); return false; }
        AABB start = actor.getBoundingBox();
        AABB swept = start.expandTowards(horizontal).inflate(1.0E-5);
        BlockPos min = BlockPos.containing(swept.minX, swept.minY, swept.minZ);
        BlockPos max = BlockPos.containing(swept.maxX, swept.maxY, swept.maxZ);
        double first = Double.POSITIVE_INFINITY;
        java.util.Map<java.util.UUID, AirlockBlockEntity> doors = new java.util.LinkedHashMap<>();
        for (BlockPos pos : BlockPos.betweenClosed(min, max)) {
            if (!level.hasChunkAt(pos)) {
                // Treat the edge of loaded world as opaque for ordering; never activate
                // a visible door by sweeping through an unloaded candidate volume.
                AABB unloadedCell = new AABB(pos.getX(),pos.getY(),pos.getZ(),pos.getX()+1,pos.getY()+1,pos.getZ()+1);
                double t = timeOfImpact(start,horizontal,unloadedCell);
                if (t >= 0 && t < first) { first=t; doors.clear(); }
                continue;
            }
            var state = level.getBlockState(pos);
            if (state.getBlock() instanceof AirlockBlock && !state.getValue(AirlockBlock.OPEN)) {
                BlockPos root = state.getValue(AirlockBlock.HALF) == net.minecraft.world.level.block.state.properties.DoubleBlockHalf.UPPER ? pos.below() : pos;
                if (level.getBlockEntity(root) instanceof AirlockBlockEntity door) {
                    AABB shape = new AABB(root.getX(), root.getY(), root.getZ(), root.getX()+1, root.getY()+2, root.getZ()+1);
                    double t = timeOfImpact(start, horizontal, shape);
                    if (t >= 0 && t < first) { first = t; doors.clear(); doors.put(door.identity(), door); }
                    else if (t >= 0 && Math.abs(t-first) <= 1.0E-5) doors.put(door.identity(), door);
                }
            }
            if (state.getBlock() instanceof AirlockBlock) continue;
            for (AABB shape : state.getCollisionShape(level, pos).toAabbs()) {
                double t = timeOfImpact(start, horizontal, shape.move(pos));
                if (t >= 0 && t < first) { first = t; doors.clear(); }
            }
        }
        java.util.Map<java.util.UUID, ContactMemo> previous = LAST_CONTACT.getOrDefault(actor, java.util.Map.of());
        java.util.Map<java.util.UUID, ContactMemo> current = new java.util.LinkedHashMap<>();
        boolean activated = false;
        for (AirlockBlockEntity hit : doors.values()) {
            if (hit.prototype() == null || !hit.prototype().door().bumpOpen()) continue;
            var phase = hit.state().phase();
            if (phase != AirlockState.DoorPhase.CLOSED && phase != AirlockState.DoorPhase.DENYING) continue;
            var supply = com.juicyslew.moonstation14.ms14.power.runtime.PowerRuntime.airlockPower(level, hit);
            boolean paidPowerReady = supply.known()
                    && supply.deliveredWatts() == hit.prototype().apcPowerReceiver().requestedWatts();
            ContactMemo memo = new ContactMemo(hit.identity(), hit.state().revision(), paidPowerReady);
            current.put(hit.identity(), memo);
            if (memo.equals(previous.get(hit.identity()))) continue;
            hit.electrifiedContact(living, AirlockElectrificationSystem.Contact.BUMP);
            activated |= hit.bumpActivate();
        }
        if (current.isEmpty()) LAST_CONTACT.remove(actor); else LAST_CONTACT.put(actor, current);
        return activated;
    }

    /** Slab swept-AABB entry time; negative means no contact. */
    static double timeOfImpact(AABB moving, Vec3 delta, AABB target) {
        double[] x = slab(moving.minX, moving.maxX, target.minX, target.maxX, delta.x);
        double[] y = slab(moving.minY, moving.maxY, target.minY, target.maxY, delta.y);
        double[] z = slab(moving.minZ, moving.maxZ, target.minZ, target.maxZ, delta.z);
        double entry = Math.max(x[0], Math.max(y[0], z[0]));
        double exit = Math.min(x[1], Math.min(y[1], z[1]));
        return entry <= exit && exit >= 0 && entry <= 1 ? Math.max(0, entry) : -1;
    }
    private static double[] slab(double aMin, double aMax, double bMin, double bMax, double d) {
        if (Math.abs(d) < 1.0E-12) return aMax <= bMin || aMin >= bMax
                ? new double[]{Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY}
                : new double[]{Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY};
        double one = (bMin-aMax)/d, two = (bMax-aMin)/d;
        return new double[]{Math.min(one,two), Math.max(one,two)};
    }
}
