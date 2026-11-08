package com.juicyslew.moonstation14.ms14.tesla;

import com.juicyslew.moonstation14.ms14.character.CharacterIdentitySystem;
import com.juicyslew.moonstation14.ms14.player_body_control.server.ActiveCharacterPolicy;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.*;

/** Transient server-side fixed-endpoint Tesla beam fixtures. */
public final class TeslaLightningBeamHazard {
    private static final Map<ServerLevel, List<Beam>> ACTIVE = new IdentityHashMap<>();
    private record Beam(Vec3 from, Vec3 to, TeslaBallProfile.Contact contact, long expires,
                        Set<UUID> touching) { }
    private TeslaLightningBeamHazard() { }

    static void create(ServerLevel level, Vec3 from, Vec3 to, TeslaBallProfile.Contact contact, UUID initial) {
        if (level == null || from == null || to == null || contact == null || from.equals(to)) return;
        Set<UUID> touching = new HashSet<>();
        double width = contact.beamHalfWidth();
        if (initial != null) {
            var entity = level.getEntity(initial);
            if (entity instanceof LivingEntity living && intersects(from, to, living.getBoundingBox(), width)) touching.add(initial);
        }
        long duration = Math.max(1, Math.round(contact.sourceLifetimeSeconds() * 20));
        ACTIVE.computeIfAbsent(level, ignored -> new ArrayList<>()).add(new Beam(from, to, contact, level.getGameTime()+duration, touching));
    }

    public static void tick(ServerLevel level) {
        List<Beam> beams = ACTIVE.get(level);
        if (beams == null) return;
        long now = level.getGameTime();
        beams.removeIf(beam -> {
            if (now >= beam.expires()) return true;
            // Broad phase only: expand degenerate axes enough for entity lookup, while
            double width = beam.contact().beamHalfWidth();
            AABB bounds = new AABB(beam.from(), beam.to()).inflate(width + 1.0e-6);
            Set<UUID> current = new HashSet<>();
            for (LivingEntity living : level.getEntitiesOfClass(LivingEntity.class, bounds,
                    e -> e.isAlive() && e.isAddedToLevel() && !e.isSpectator())) {
                if (living instanceof net.minecraft.server.level.ServerPlayer player && ActiveCharacterPolicy.isCarrier(player)) continue;
                if (CharacterIdentitySystem.resolveForActor(living, com.juicyslew.moonstation14.ms14.character.ModCharacters.catalog(level)).isEmpty()) continue;
                if (!intersects(beam.from(), beam.to(), living.getBoundingBox(), width)) continue;
                UUID id = living.getUUID(); current.add(id);
                if (!beam.touching().contains(id)) com.juicyslew.moonstation14.ms14.electrocution.ElectrocutionSystem.applySourceShock(
                        living, (float)beam.contact().shockDamage(), (float)beam.contact().siemens(), (float)beam.contact().stunSeconds());
            }
            beam.touching().clear();
            beam.touching().addAll(current);
            return false;
        });
        if (beams.isEmpty()) ACTIVE.remove(level);
    }

    public static void unload(ServerLevel level) { ACTIVE.remove(level); }

    /** Exact line segment clipping against each entity box (not the rendered sprite width). */
    public static boolean intersects(Vec3 from, Vec3 to, AABB box) {
        return intersects(from, to, box, 0);
    }
    /** Minecraft adaptation: admits a finite beam segment through the box inflated by authored half-width. */
    public static boolean intersects(Vec3 from, Vec3 to, AABB box, double halfWidth) {
        if (from == null || to == null || box == null || from.equals(to)
                || !Double.isFinite(halfWidth) || halfWidth < 0) return false;
        if (halfWidth > 0) box = box.inflate(halfWidth);
        return box.contains(from) || box.contains(to) || box.clip(from, to).isPresent();
    }
}
