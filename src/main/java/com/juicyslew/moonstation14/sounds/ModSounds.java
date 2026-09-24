package com.juicyslew.moonstation14.sounds;

import com.juicyslew.moonstation14.MoonStation14;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.function.Supplier;

public class ModSounds {
    public static final DeferredRegister<SoundEvent> SOUND_EVENTS =
            DeferredRegister.create(BuiltInRegistries.SOUND_EVENT, MoonStation14.MOD_ID);

    public static final Supplier<SoundEvent> CROWBAR_USE = registerSoundEvent("crowbar_use");
    private static final java.util.Map<String, Supplier<SoundEvent>> EMOTES = java.util.Map.ofEntries(
            java.util.Map.entry("cough", registerSoundEvent("emote/cough")),
            java.util.Map.entry("crying", registerSoundEvent("emote/crying")),
            java.util.Map.entry("hew", registerSoundEvent("emote/hew")),
            java.util.Map.entry("honk", registerSoundEvent("emote/honk")),
            java.util.Map.entry("laugh", registerSoundEvent("emote/laugh")),
            java.util.Map.entry("scream", registerSoundEvent("emote/scream")),
            java.util.Map.entry("weh", registerSoundEvent("emote/weh")),
            java.util.Map.entry("whistle", registerSoundEvent("emote/whistle")),
            java.util.Map.entry("yawn", registerSoundEvent("emote/yawn")));

    public static Supplier<SoundEvent> emote(String id) {
        Supplier<SoundEvent> sound = EMOTES.get(id);
        if (sound == null) throw new IllegalArgumentException("Unknown registered emote sound: " + id);
        return sound;
    }

    private static Supplier<SoundEvent> registerSoundEvent(String name){
        ResourceLocation id = ResourceLocation.fromNamespaceAndPath(MoonStation14.MOD_ID, name);
        return SOUND_EVENTS.register(name, () -> SoundEvent.createVariableRangeEvent(id));
    }

    public static void register(IEventBus eventBus) {
        SOUND_EVENTS.register(eventBus);
    }
}
