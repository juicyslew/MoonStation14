package com.juicyslew.moonstation14.entities.client;

import com.juicyslew.moonstation14.ms14.player_body_control.client.GhostControlClient;
import com.juicyslew.moonstation14.ms14.projectile.ProjectileFlyByCuePolicy;
import com.juicyslew.moonstation14.ms14.projectile.ProjectileFlyByGeometry;
import com.juicyslew.moonstation14.ms14.projectile.ProjectileFlyByProfile;
import com.juicyslew.moonstation14.ms14.projectile.ProjectileFlyBySource;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.event.level.LevelEvent;

import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

/** Client-local cue manager for actually tracked projectile sources; never dispatches or mutates gameplay. */
@EventBusSubscriber(modid = "moonstation14", value = Dist.CLIENT)
public final class ProjectileFlyByClient {
    private static final Map<ProjectileFlyBySource, Track> TRACKED = new IdentityHashMap<>();
    private static UUID listenerId;
    private static long listenerEpoch;
    private static final RandomSource RANDOM = RandomSource.create();

    private ProjectileFlyByClient() { }

    @SubscribeEvent public static void tick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        Mob listener = GhostControlClient.ownedCharacterForPresentation();
        long epoch = GhostControlClient.committedCharacterEpoch();
        if (mc.level == null || listener == null || listener.isRemoved() || listener.level() != mc.level) {
            clear();
            return;
        }
        if (!listener.getUUID().equals(listenerId) || epoch != listenerEpoch) {
            TRACKED.clear();
            listenerId = listener.getUUID();
            listenerEpoch = epoch;
        }

        var seen = java.util.Collections.newSetFromMap(new IdentityHashMap<ProjectileFlyBySource, Boolean>());
        for (var entity : mc.level.entitiesForRendering()) {
            if (!(entity instanceof ProjectileFlyBySource source) || entity.isRemoved()) continue;
            seen.add(source);
            ProjectileFlyByProfile profile = source.flyByProfile();
            Vec3 current = source.flyByPosition();
            if (profile == null || current == null) { TRACKED.remove(source); continue; }
            Track previous = TRACKED.get(source);
            Vec3 from = previous == null ? current : previous.position;
            boolean intersects = ProjectileFlyByGeometry.intersects(from, current, listener.getBoundingBox(), profile.range());
            boolean insideNow = ProjectileFlyByGeometry.intersects(current, current, listener.getBoundingBox(), profile.range());
            boolean shooter = listener.getUUID().equals(source.flyByShooterIdentity());
            if (ProjectileFlyByCuePolicy.shouldCue(previous != null && previous.inside,
                    intersects, shooter, profile.probability(), RANDOM.nextFloat())) {
                var sound = BuiltInRegistries.SOUND_EVENT.getOptional(profile.sound()).orElse(null);
                if (sound != null) mc.level.playLocalSound(listener.getX(), listener.getY(), listener.getZ(), sound,
                        SoundSource.PLAYERS, 1f, 1f, false);
            }
            TRACKED.put(source, new Track(current, insideNow));
        }
        Iterator<ProjectileFlyBySource> iterator = TRACKED.keySet().iterator();
        while (iterator.hasNext()) if (!seen.contains(iterator.next())) iterator.remove();
    }

    @SubscribeEvent public static void logout(ClientPlayerNetworkEvent.LoggingOut event) { clear(); }
    @SubscribeEvent public static void unload(LevelEvent.Unload event) {
        if (event.getLevel().isClientSide()) clear();
    }

    private static void clear() { TRACKED.clear(); listenerId = null; listenerEpoch = 0; }
    private record Track(Vec3 position, boolean inside) { }
}
