package com.juicyslew.moonstation14.ms14.botany.client;

import com.juicyslew.moonstation14.block.ModBlocks;
import com.juicyslew.moonstation14.ms14.botany.GrinderProcessingSoundPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import java.util.*;

public final class GrinderProcessingAudio {
    record Key(ResourceLocation dimension, net.minecraft.core.BlockPos pos, UUID placement, long start) { }
    private static final Map<Key, SoundInstance> sounds = new HashMap<>();
    private GrinderProcessingAudio() { }

    public static void accept(GrinderProcessingSoundPayload payload) {
        Minecraft mc = Minecraft.getInstance();
        Key key = new Key(payload.dimension(), payload.position(), payload.placement(), payload.startTick());
        if (payload.operation() == GrinderProcessingSoundPayload.Operation.STOP) {
            var old = sounds.remove(key);
            if (old != null) mc.getSoundManager().stop(old);
            return;
        }
        if (mc.level == null || !mc.level.dimension().location().equals(payload.dimension())) return;
        SoundEvent event = BuiltInRegistries.SOUND_EVENT.getOptional(payload.sound()).orElse(null);
        if (event == null) return;
        var duplicate = sounds.get(key);
        if (duplicate != null && !duplicate.isStopped()) return;
        // A physical grinder can only have one current job/sound at its position.
        for (var entry : new ArrayList<>(sounds.entrySet())) {
            Key existing = entry.getKey();
            if (existing.dimension().equals(key.dimension()) && existing.pos().equals(key.pos())) {
                sounds.remove(existing);
                mc.getSoundManager().stop(entry.getValue());
            }
        }
        SoundInstance sound = new SoundInstance(event, payload);
        sounds.put(key, sound);
        mc.getSoundManager().play(sound);
    }

    @net.neoforged.bus.api.SubscribeEvent
    public static void tick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            sounds.values().forEach(mc.getSoundManager()::stop);
            sounds.clear();
            return;
        }
        sounds.entrySet().removeIf(e -> {
            var s = e.getValue();
            if (!shouldKeepPlaying(mc.level, s.payload)) {
                mc.getSoundManager().stop(s);
                return true;
            }
            return false;
        });
    }

    static boolean shouldKeepPlaying(net.minecraft.client.multiplayer.ClientLevel level, GrinderProcessingSoundPayload payload) {
        return level != null
                && level.dimension().location().equals(payload.dimension())
                && level.getGameTime() < payload.endTick()
                && level.hasChunkAt(payload.position())
                && level.getBlockState(payload.position()).is(ModBlocks.REAGENT_GRINDER.get());
    }

    private static final class SoundInstance extends AbstractTickableSoundInstance {
        final GrinderProcessingSoundPayload payload;
        SoundInstance(SoundEvent event, GrinderProcessingSoundPayload p) {
            super(event, SoundSource.BLOCKS, net.minecraft.util.RandomSource.create());
            payload = p;
            x = p.position().getX() + .5; y = p.position().getY() + .5; z = p.position().getZ() + .5;
            volume = p.volume(); pitch = p.pitch(); looping = false; delay = 0; attenuation = Attenuation.LINEAR;
        }
        @Override public void tick() {
            Minecraft mc = Minecraft.getInstance();
            if (!shouldKeepPlaying(mc.level, payload)) stop();
        }
    }
}
