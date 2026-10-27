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
    public static final Supplier<SoundEvent> BOTANY_GRINDER = registerSoundEvent("botany/grinder/blender");
    public static final Supplier<SoundEvent> BOTANY_GRINDER_JUICE = registerSoundEvent("botany/grinder/juicer");
    public static final Supplier<SoundEvent> BOTANY_PRODUCE_DESECRATION = registerSoundEvent("botany/produce/desecration");
    public static final Supplier<SoundEvent> BOTANY_PRODUCE_SLIME_SQUISH = registerSoundEvent("botany/produce/slime_squish");
    public static final Supplier<SoundEvent> BOTANY_PRODUCE_PACKETRIP = registerSoundEvent("botany/produce/packetrip");
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
    public static final Supplier<SoundEvent> PILL = registerSoundEvent("ss14/items/pill");
    public static final Supplier<SoundEvent> EAT = registerSoundEvent("ss14/items/eat");
    public static final Supplier<SoundEvent> FOOD_CHOP = registerSoundEvent("ss14/items/chop");
    public static final Supplier<SoundEvent> GUNSHOT_REVOLVER = registerSoundEvent("ss14/weapons/guns/gunshots/revolver");
    public static final Supplier<SoundEvent> GUN_EMPTY = registerSoundEvent("ss14/weapons/guns/empty");
    public static final Supplier<SoundEvent> GUN_REVOLVER_MAGOUT = registerSoundEvent("ss14/weapons/guns/magout/revolver_magout");
    public static final Supplier<SoundEvent> GUN_REVOLVER_MAGIN = registerSoundEvent("ss14/weapons/guns/magin/revolver_magin");
    public static final Supplier<SoundEvent> GUN_REVOLVER_SPIN = registerSoundEvent("ss14/weapons/guns/misc/revolver_spin");
    public static final Supplier<SoundEvent> GUN_BULLET_HIT = registerSoundEvent("ss14/weapons/guns/hits/bullet_hit");
    public static final Supplier<SoundEvent> GUN_BULLET_MISS = registerSoundEvent("ss14/weapons/guns/miss/bullet_miss");
    public static final Supplier<SoundEvent> SLIP = registerSoundEvent("ss14/effects/slip");
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
    public static final Supplier<SoundEvent> MOUSE_SQUEAK = registerSoundEvent("mouse_squeak");
    public static final Supplier<SoundEvent> VOCAL_HUMAN_SCREAM = registerSoundEvent("vocal_human_scream");
    public static final Supplier<SoundEvent> VOCAL_REPTILIAN_SCREAM = registerSoundEvent("vocal_reptilian_scream");
    public static final Supplier<SoundEvent> VOCAL_SCURRET_SCREAM = registerSoundEvent("vocal_scurret_scream");
    public static final Supplier<SoundEvent> VOCAL_WILHELM = registerSoundEvent("vocal_wilhelm");
    private record SpeechSounds(Supplier<SoundEvent> say, Supplier<SoundEvent> ask, Supplier<SoundEvent> exclaim) {
        SpeechSounds(Supplier<SoundEvent> all) { this(all, all, all); }
    }
    private static SpeechSounds speechCollection(String id) {
        return new SpeechSounds(registerSoundEvent("speech/" + id),
                registerSoundEvent("speech/" + id + "_ask"), registerSoundEvent("speech/" + id + "_exclaim"));
    }
    private static final java.util.Map<String, SpeechSounds> SPEECH_SOUNDS = java.util.Map.ofEntries(
            java.util.Map.entry("Squeak", new SpeechSounds(MOUSE_SQUEAK)),
            java.util.Map.entry("mouse_squeak", new SpeechSounds(MOUSE_SQUEAK)),
            java.util.Map.entry("Cluck", new SpeechSounds(registerSoundEvent("speech/cluck"))),
            java.util.Map.entry("Goat", new SpeechSounds(registerSoundEvent("speech/goat"))),
            java.util.Map.entry("Monkey", new SpeechSounds(registerSoundEvent("speech/monkey"))),
            java.util.Map.entry("Parrot", new SpeechSounds(registerSoundEvent("speech/parrot"))),
            java.util.Map.entry("Cat", new SpeechSounds(registerSoundEvent("speech/cat"))),
            java.util.Map.entry("Dog", new SpeechSounds(registerSoundEvent("speech/dog"))),
            java.util.Map.entry("Lizard", speechCollection("lizard")),
            java.util.Map.entry("Arachnid", speechCollection("arachnid")),
            java.util.Map.entry("Alto", speechCollection("alto")),
            java.util.Map.entry("Wawa", speechCollection("wawa")));

    public static boolean hasSpeechSound(String id) { return SPEECH_SOUNDS.containsKey(id); }
    /** Resolves an explicit ambient cue from registered animal voice collections. */
    public static Supplier<SoundEvent> ambientSound(String id) {
        SpeechSounds voice = SPEECH_SOUNDS.get(id);
        if (voice != null) return voice.say();
        return null;
    }
    public static Supplier<SoundEvent> speechSound(String id) {
        return speechSounds(id).say();
    }
    public static Supplier<SoundEvent> speechSound(String id, String text) {
        SpeechSounds sounds = speechSounds(id);
        if (text != null && text.stripTrailing().endsWith("?")) return sounds.ask();
        if (text != null && text.stripTrailing().endsWith("!")) return sounds.exclaim();
        return sounds.say();
    }
    private static SpeechSounds speechSounds(String id) {
        SpeechSounds sounds = SPEECH_SOUNDS.get(id);
        if (sounds == null) throw new IllegalArgumentException("Unknown registered speech sound: " + id);
        return sounds;
    }
    // Match the per-sample attenuation_distance in sounds.json: server delivery and client fade
    public static final Supplier<SoundEvent> DISPOSAL_FLUSH = registerSoundEvent("disposal/flush");
    public static final Supplier<SoundEvent> DISPOSAL_PIPE_CLANG = registerSoundEvent("disposal/pipe_clang");
    public static final Supplier<SoundEvent> DISPOSAL_INSERT = registerSoundEvent("disposal/insert");
    // both end at 32 blocks, independently of the quieter playback gain at each call site.
    public static final Supplier<SoundEvent> GRENADE_BEEP = registerFixedRangeSoundEvent("grenade/beep", 32.0F);
    public static final Supplier<SoundEvent> GRENADE_EXPLODE = registerFixedRangeSoundEvent("grenade/explode", 32.0F);
    public static final Supplier<SoundEvent> GRENADE_EXPLODE_FAR = registerSoundEvent("grenade/explode_far");
    public static final Supplier<SoundEvent> EXPLOSION_SMALL = registerFixedRangeSoundEvent("ss14/effects/explosion_small", 30.0F);
    public static final Supplier<SoundEvent> EXPLOSION_SMALL_FAR = registerSoundEvent("ss14/effects/explosion_small_far");
    public static final Supplier<SoundEvent> GLASS_BREAK = registerSoundEvent("ss14/effects/glass_break");
    public static final Supplier<SoundEvent> GLASS_STEP = registerSoundEvent("ss14/effects/glass_step");
    public static final Supplier<SoundEvent> PA_PARTICLE_HIT = registerSoundEvent("pa_particle_hit");
    public static final Supplier<SoundEvent> SINGULARITY_AMBIENT = registerFixedRangeSoundEvent("singularity/ambient", 14.0F);
    public static final Supplier<SoundEvent> SINGULARITY_COMPONENT_AMBIENT = registerFixedRangeSoundEvent("singularity/component_ambient", 20.0F);
    public static final Supplier<SoundEvent> SINGULARITY_FORM = registerSoundEvent("singularity/form");
    public static final Supplier<SoundEvent> SINGULARITY_DISSIPATE = registerSoundEvent("singularity/dissipate");
    public static final Supplier<SoundEvent> TESLA_AMBIENT = registerFixedRangeSoundEvent("tesla/ambient", 15.0F);
    public static final Supplier<SoundEvent> TESLA_SPAWN = registerSoundEvent("tesla/spawn");
    public static final Supplier<SoundEvent> TESLA_CONSUME = registerSoundEvent("tesla/consume");
    public static final Supplier<SoundEvent> TESLA_LIGHTNING_SHOCK = registerSoundEvent("tesla/lightning_shock");
    public static final Supplier<SoundEvent> EMITTER_FIRE = registerSoundEvent("emitter/fire");
    public static final Supplier<SoundEvent> PROJECTILE_FLY_BY = registerSoundEvent("projectile/fly_by");
    public static final Supplier<SoundEvent> ITEM_SLOT_INSERT = registerSoundEvent("item_slot_insert");
    public static final Supplier<SoundEvent> ITEM_SLOT_EJECT = registerSoundEvent("item_slot_eject");
    public static final Supplier<SoundEvent> GAS_VALVE_TOGGLE = registerSoundEvent("gas_valve_toggle");
    private static final java.util.Map<String, Supplier<SoundEvent>> EMOTES = java.util.Map.ofEntries(
            java.util.Map.entry("cough", registerSoundEvent("emote/cough")),
            java.util.Map.entry("crying", registerSoundEvent("emote/crying")),
            java.util.Map.entry("hew", registerSoundEvent("emote/hew")),
            java.util.Map.entry("honk", registerSoundEvent("emote/honk")),
            java.util.Map.entry("laugh", registerSoundEvent("emote/laugh")),
            java.util.Map.entry("mouse_squeak", MOUSE_SQUEAK),
            java.util.Map.entry("scream", registerSoundEvent("emote/scream")),
            java.util.Map.entry("weh", registerSoundEvent("emote/weh")),
            java.util.Map.entry("whistle", registerSoundEvent("emote/whistle")),
            java.util.Map.entry("yawn", registerSoundEvent("emote/yawn")));

    public static Supplier<SoundEvent> emote(String id) {
        Supplier<SoundEvent> sound = EMOTES.get(id);
        if (sound == null) throw new IllegalArgumentException("Unknown registered emote sound: " + id);
        return sound;
    }

    public static boolean hasEmote(String id) { return EMOTES.containsKey(id); }

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
