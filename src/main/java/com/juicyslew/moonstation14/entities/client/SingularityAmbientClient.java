package com.juicyslew.moonstation14.entities.client;

import com.juicyslew.moonstation14.entities.SingularityEntity;
import com.juicyslew.moonstation14.ms14.singularity.SingularityGameplayProfile;
import com.juicyslew.moonstation14.ms14.singularity.SingularitySoundProfile;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.sounds.SoundSource;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.util.RandomSource;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.client.event.RegisterClientReloadListenersEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.api.distmarker.Dist;
import net.minecraft.server.packs.resources.SimplePreparableReloadListener;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.profiling.ProfilerFiller;

import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.Map;

/** Client-only entity-attached ambient playback; profile sync, not packets, controls it. */
@EventBusSubscriber(modid = "moonstation14", value = Dist.CLIENT)
public final class SingularityAmbientClient {
    private static final Map<SingularityEntity, Pair> ACTIVE = new IdentityHashMap<>();
    private SingularityAmbientClient() { }

    @SubscribeEvent public static void tick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.getSoundManager() == null) { clear(mc); return; }
        Iterator<Map.Entry<SingularityEntity, Pair>> iterator = ACTIVE.entrySet().iterator();
        while (iterator.hasNext()) {
            var entry = iterator.next();
            if (!valid(mc, entry.getKey())) { entry.getValue().stop(mc); iterator.remove(); }
            else {
                var resolved = entry.getKey().gravityProfileIfReady();
                if (resolved == null || resolved.sounds() == null || !resolved.sounds().equals(entry.getValue().profile)) {
                    entry.getValue().stop(mc);
                    iterator.remove();
                }
            }
        }
        for (var entity : mc.level.entitiesForRendering()) {
            if (!(entity instanceof SingularityEntity singularity) || !valid(mc, singularity) || ACTIVE.containsKey(singularity)) continue;
            SingularityGameplayProfile profile = singularity.gravityProfileIfReady();
            if (profile == null || profile.sounds() == null) continue;
            var sounds = profile.sounds();
            Pair pair = new Pair(singularity, sounds);
            pair.update(mc);
            ACTIVE.put(singularity, pair);
        }
        ACTIVE.values().forEach(p -> p.update(mc));
    }

    @SubscribeEvent public static void logout(ClientPlayerNetworkEvent.LoggingOut event) { clear(Minecraft.getInstance()); }
    @SubscribeEvent public static void unload(LevelEvent.Unload event) {
        if (event.getLevel().isClientSide()) clear(Minecraft.getInstance());
    }
    private static boolean valid(Minecraft mc, SingularityEntity entity) {
        if (entity.isRemoved() || entity.level() != mc.level || mc.player == null || mc.getCameraEntity() == null) return false;
        SingularityGameplayProfile p = entity.gravityProfileIfReady();
        if (p == null) return false;
        return true;
    }
    private static void clear(Minecraft mc) { ACTIVE.values().forEach(p -> p.stop(mc)); ACTIVE.clear(); }
    public static void registerReloadListener(RegisterClientReloadListenersEvent event) {
        event.registerReloadListener(new SimplePreparableReloadListener<Void>() {
            @Override protected Void prepare(ResourceManager manager, ProfilerFiller profiler) { return null; }
            @Override protected void apply(Void ignored, ResourceManager manager, ProfilerFiller profiler) { clear(Minecraft.getInstance()); }
        });
    }
    private static final class Pair {
        final SingularityEntity entity;
        final SingularitySoundProfile profile;
        Loop root, component;
        Pair(SingularityEntity entity, SingularitySoundProfile profile) { this.entity=entity; this.profile=profile; }
        void update(Minecraft mc) {
            if (root == null || root.stopped) root = new Loop(entity, profile.ambient(), profile.ambientDb(), profile.ambientRange());
            if (component == null || component.stopped) component = new Loop(entity, profile.componentAmbient(), profile.componentAmbientDb(), profile.componentAmbientRange());
            root.update(mc); component.update(mc);
        }
        void stop(Minecraft mc) { if(root!=null) root.stop(mc); if(component!=null) component.stop(mc); }
    }
    private static final class Loop extends AbstractTickableSoundInstance {
        private final SingularityEntity entity;
        private final float range;
        private boolean playing;
        private boolean stopped;
        Loop(SingularityEntity entity, net.minecraft.resources.ResourceLocation sound, float db, float range) {
            super(BuiltInRegistries.SOUND_EVENT.get(sound), SoundSource.AMBIENT, RandomSource.create());
            this.entity = entity; this.looping = true; this.delay = 0; this.volume = SingularitySoundProfile.gain(db);
            this.range = range;
            this.attenuation = Attenuation.LINEAR; this.x=entity.getX(); this.y=entity.getY(); this.z=entity.getZ();
        }
        void update(Minecraft mc) {
            if (entity.isRemoved() || mc.getCameraEntity() == null) { stop(mc); return; }
            x=entity.getX(); y=entity.getY(); z=entity.getZ();
            var camera = mc.getCameraEntity();
            double dx=x-camera.getX(),dy=y-camera.getY(),dz=z-camera.getZ();
            boolean near = SingularityAmbientPolicy.shouldPlay(!entity.isRemoved(), mc.level != null,
                    entity.gravityProfileIfReady() != null, dx*dx+dy*dy+dz*dz, range);
            if (near && !playing) { mc.getSoundManager().play(this); playing = true; }
            else if (!near && playing) stop(mc);
        }
        void stop(Minecraft mc) { if (playing) mc.getSoundManager().stop(this); playing=false; stopped=true; stop(); }
        @Override public void tick() { if (entity.isRemoved()) stop(); else { x=entity.getX(); y=entity.getY(); z=entity.getZ(); } }
        @Override public boolean canPlaySound() { return !entity.isRemoved(); }
    }
}
