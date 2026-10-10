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
    public static final Supplier<SoundEvent> WIRECUTTER_USE = registerSoundEvent("tools/wirecutter");
    public static final Supplier<SoundEvent> APC_SWITCH = registerSoundEvent("apc/switch");
    public static final Supplier<SoundEvent> CHEMMASTER_CLICK = registerSoundEvent("chemistry/chemmaster/click");
    public static final Supplier<SoundEvent> CHEMMASTER_INSERT = registerSoundEvent("chemistry/chemmaster/insert");
    public static final Supplier<SoundEvent> CHEMMASTER_EJECT = registerSoundEvent("chemistry/chemmaster/eject");
    public static final Supplier<SoundEvent> CHEMMASTER_DUMP = registerSoundEvent("chemistry/chemmaster/dump");
    public static final Supplier<SoundEvent> REACTION_BUBBLES = registerSoundEvent("chemistry/reaction_bubbles");
    public static final Supplier<SoundEvent> DEVICE_LIGHTSWITCH = registerSoundEvent("device_lightswitch");
    public static final Supplier<SoundEvent> DEVICE_OVERLOAD = registerSoundEvent("device/defib_zap");
    public static final Supplier<SoundEvent> AIRLOCK_OPEN = registerSoundEvent("airlock/airlock_open");
    public static final Supplier<SoundEvent> AIRLOCK_CLOSE = registerSoundEvent("airlock/airlock_close");
    public static final Supplier<SoundEvent> AIRLOCK_DENY = registerSoundEvent("airlock/airlock_deny");
    public static final Supplier<SoundEvent> AIRLOCK_BOLTS_UP = registerSoundEvent("airlock/boltsup");
    public static final Supplier<SoundEvent> AIRLOCK_BOLTS_DOWN = registerSoundEvent("airlock/boltsdown");
    public static final Supplier<SoundEvent> AIRLOCK_PANEL_OPEN = registerSoundEvent("airlock/panel_open");
    public static final Supplier<SoundEvent> AIRLOCK_PANEL_CLOSE = registerSoundEvent("airlock/panel_close");
    public static final Supplier<SoundEvent> AIRLOCK_ELECTRIFY_ON = registerSoundEvent("airlock/airlock_electrify_on");
    public static final Supplier<SoundEvent> AIRLOCK_ELECTRIFY_OFF = registerSoundEvent("airlock/airlock_electrify_off");
    public static final Supplier<SoundEvent> AIRLOCK_SHOCK = registerSoundEvent("airlock/shock");
    public static final Supplier<SoundEvent> DRINK = registerSoundEvent("ss14/items/drink");
    public static final Supplier<SoundEvent> BODY_FALL = registerSoundEvent("body_fall");
    // Event registry IDs must exist independently of their sounds.json sample mappings.
    public static final Supplier<SoundEvent> MEDICAL_OINTMENT_BEGIN = registerSoundEvent("medical/ointment_begin");
    public static final Supplier<SoundEvent> MEDICAL_OINTMENT_END = registerSoundEvent("medical/ointment_end");
    public static final Supplier<SoundEvent> MEDICAL_BRUTEPACK_BEGIN = registerSoundEvent("medical/brutepack_begin");
    public static final Supplier<SoundEvent> MEDICAL_BRUTEPACK_END = registerSoundEvent("medical/brutepack_end");
    public static final Supplier<SoundEvent> MEDICAL_HEALTHSCANNER = registerSoundEvent("medical/healthscanner");
    // Default human gasp profile is MaleHuman (HumanUnsexed); female collection is
    // registered for a later supported voice-profile consumer.
    public static final Supplier<SoundEvent> MALE_GASP = registerSoundEvent("gasp/male");
    public static final Supplier<SoundEvent> FEMALE_GASP = registerSoundEvent("gasp/female");
    // Match the per-sample attenuation_distance in sounds.json: server delivery and client fade
    public static final Supplier<SoundEvent> DISPOSAL_FLUSH = registerSoundEvent("disposal/flush");
    public static final Supplier<SoundEvent> DISPOSAL_PIPE_CLANG = registerSoundEvent("disposal/pipe_clang");
    public static final Supplier<SoundEvent> DISPOSAL_INSERT = registerSoundEvent("disposal/insert");
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
