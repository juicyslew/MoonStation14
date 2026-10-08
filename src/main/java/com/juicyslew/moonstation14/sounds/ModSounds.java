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
    public static final Supplier<SoundEvent> TOOL_WRENCH = registerSoundEvent("ss14/tools/ratchet");
    public static final Supplier<SoundEvent> TOOL_CROWBAR = registerSoundEvent("ss14/tools/crowbar");
    public static final Supplier<SoundEvent> TOOL_SCREWDRIVER = registerSoundEvent("ss14/tools/screwdriver");
    public static final Supplier<SoundEvent> TOOL_WELDER = registerSoundEvent("ss14/tools/welder");
    public static final Supplier<SoundEvent> APC_SWITCH = registerSoundEvent("apc/switch");
    public static final Supplier<SoundEvent> DISPOSAL_FLUSH = registerSoundEvent("disposal/flush");
    public static final Supplier<SoundEvent> DISPOSAL_PIPE_CLANG = registerSoundEvent("disposal/pipe_clang");
    public static final Supplier<SoundEvent> DISPOSAL_INSERT = registerSoundEvent("disposal/insert");
    public static final Supplier<SoundEvent> DRINK = registerSoundEvent("ss14/items/drink");    // Match the per-sample attenuation_distance in sounds.json: server delivery and client fade
    // both end at 32 blocks, independently of the quieter playback gain at each call site.
    public static final Supplier<SoundEvent> GRENADE_BEEP = registerFixedRangeSoundEvent("grenade/beep", 32.0F);
    public static final Supplier<SoundEvent> GRENADE_EXPLODE = registerFixedRangeSoundEvent("grenade/explode", 32.0F);
    public static final Supplier<SoundEvent> GRENADE_EXPLODE_FAR = registerSoundEvent("grenade/explode_far");
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

    private static Supplier<SoundEvent> registerFixedRangeSoundEvent(String name, float range) {
        ResourceLocation id = ResourceLocation.fromNamespaceAndPath(MoonStation14.MOD_ID, name);
        return SOUND_EVENTS.register(name, () -> SoundEvent.createFixedRangeEvent(id, range));
    }

    public static void register(IEventBus eventBus) {
        SOUND_EVENTS.register(eventBus);
    }
}
