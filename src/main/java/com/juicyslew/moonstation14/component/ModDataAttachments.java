package com.juicyslew.moonstation14.component;

import com.juicyslew.moonstation14.MoonStation14;
import com.juicyslew.moonstation14.component.codec.attachment.DamageData;
import com.juicyslew.moonstation14.ms14.reagent.ReagentAttachment;
import com.juicyslew.moonstation14.ms14.reagent.ReagentComponent;
import com.juicyslew.moonstation14.ms14.status_effect.StatusEffectAttachment;
import com.juicyslew.moonstation14.ms14.activity.EntityActivityAttachment;
import com.juicyslew.moonstation14.ms14.fire.FireStackAttachment;
import com.juicyslew.moonstation14.ms14.eye.EyeDamageAttachment;
import com.juicyslew.moonstation14.ms14.alert.AlertAttachment;
import com.juicyslew.moonstation14.ms14.hunger.HungerAttachment;
import com.juicyslew.moonstation14.ms14.thirst.ThirstAttachment;
import com.juicyslew.moonstation14.ms14.atmos.world.AtmosphereChunkData;
import com.juicyslew.moonstation14.ms14.character.CharacterIdentityAttachment;
import com.juicyslew.moonstation14.ms14.atmos.exposure.BodyTemperatureAttachment;
import com.juicyslew.moonstation14.ms14.slip.SlidingAttachment;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

import java.util.function.Supplier;

public class ModDataAttachments {
    // Create the DeferredRegister for attachment types
    public static final DeferredRegister<AttachmentType<?>> ATTACHMENT_TYPES = DeferredRegister.create(NeoForgeRegistries.ATTACHMENT_TYPES, MoonStation14.MOD_ID);

    public static final Supplier<AttachmentType<ReagentAttachment>> REAGENT = ATTACHMENT_TYPES.register(
            "reagent", () -> AttachmentType.builder(() -> new ReagentAttachment())
                    .serialize(ReagentAttachment.CODEC, attachment -> !attachment.isEmpty())
                    .sync(ReagentAttachment.STREAM_CODEC)
                    .build()
    );

    /** Character-owned digestive contents, persisted and synchronized independently from bloodstream reagents. */
    public static final Supplier<AttachmentType<ReagentAttachment>> STOMACH = ATTACHMENT_TYPES.register(
            "stomach", () -> AttachmentType.builder(() -> new ReagentAttachment())
                    .serialize(ReagentAttachment.CODEC, attachment -> !attachment.isEmpty())
                    .sync(ReagentAttachment.STREAM_CODEC)
                    .build()
    );

    public static final Supplier<AttachmentType<DamageData>> DAMAGE = ATTACHMENT_TYPES.register(
            "damage", () -> AttachmentType.builder(() -> new DamageData())
                    .serialize(DamageData.CODEC, damage -> !damage.isEmpty())
                    .sync(DamageData.STREAM_CODEC)
                    .build()
    );

    public static final Supplier<AttachmentType<StatusEffectAttachment>> STATUS_EFFECT = ATTACHMENT_TYPES.register(
            "status_effect", () -> AttachmentType.builder(() -> new StatusEffectAttachment()).serialize(StatusEffectAttachment.CODEC, attachment -> !attachment.isEmpty()).sync(StatusEffectAttachment.STREAM_CODEC).build()
    );

    public static final Supplier<AttachmentType<AlertAttachment>> ALERT = ATTACHMENT_TYPES.register(
            "alert", () -> AttachmentType.builder((Supplier<AlertAttachment>) AlertAttachment::new)
                    .serialize(AlertAttachment.CODEC, attachment -> !attachment.isEmpty())
            .sync(AlertAttachment.STREAM_CODEC).build());

    public static final Supplier<AttachmentType<HungerAttachment>> HUNGER = ATTACHMENT_TYPES.register(
            "hunger", () -> AttachmentType.builder((Supplier<HungerAttachment>) HungerAttachment::new)
                    .serialize(HungerAttachment.CODEC)
                    .sync(HungerAttachment.STREAM_CODEC).build());

    /** Persisted character thirst. Its default-valued state is intentionally retained once initialized. */
    public static final Supplier<AttachmentType<ThirstAttachment>> THIRST = ATTACHMENT_TYPES.register(
            "thirst", () -> AttachmentType.builder((Supplier<ThirstAttachment>) ThirstAttachment::new)
                    .serialize(ThirstAttachment.CODEC)
                    .sync(ThirstAttachment.STREAM_CODEC).build());

    /** Persisted and synchronized entity body temperature, including the normal baseline. */
    public static final Supplier<AttachmentType<BodyTemperatureAttachment>> BODY_TEMPERATURE = ATTACHMENT_TYPES.register(
            "body_temperature", () -> AttachmentType.builder((Supplier<BodyTemperatureAttachment>) BodyTemperatureAttachment::new)
                    .serialize(BodyTemperatureAttachment.CODEC)
                    .sync(BodyTemperatureAttachment.STREAM_CODEC).build());

    /** Character-owned authoritative fire stacks; empty state is not persisted or synchronized. */
    public static final Supplier<AttachmentType<FireStackAttachment>> FIRE_STACK = ATTACHMENT_TYPES.register(
            "fire_stack", () -> AttachmentType.builder((Supplier<FireStackAttachment>) FireStackAttachment::new)
                    .serialize(FireStackAttachment.CODEC, attachment -> !attachment.isEmpty())
                    .sync(FireStackAttachment.STREAM_CODEC)
                    .build()
    );

    /** Persisted, key-only server character identity. */
    public static final Supplier<AttachmentType<CharacterIdentityAttachment>> CHARACTER_IDENTITY = ATTACHMENT_TYPES.register(
            "character_identity", () -> AttachmentType.builder((Supplier<CharacterIdentityAttachment>) CharacterIdentityAttachment::new)
                    .serialize(CharacterIdentityAttachment.CODEC, CharacterIdentityAttachment::isBound)
                    .sync(CharacterIdentityAttachment.STREAM_CODEC)
                    .build());

    /** Character-owned eye damage; its canonical mutation path removes zero and its serializer omits empty state. */
    public static final Supplier<AttachmentType<EyeDamageAttachment>> EYE_DAMAGE = ATTACHMENT_TYPES.register(
            "eye_damage", () -> AttachmentType.builder((Supplier<EyeDamageAttachment>) EyeDamageAttachment::new)
                    .serialize(EyeDamageAttachment.CODEC, attachment -> !attachment.isEmpty())
                    .sync(EyeDamageAttachment.STREAM_CODEC)
                    // Deliberately no copyOnDeath: the current player clone policy clears this state.
                    .build()
    );

    /** Derived scheduling state; it is deliberately neither persisted nor synchronized. */
    public static final Supplier<AttachmentType<EntityActivityAttachment>> ACTIVE_SYSTEMS = ATTACHMENT_TYPES.register(
            "active_systems", () -> AttachmentType.builder((Supplier<EntityActivityAttachment>) EntityActivityAttachment::new).build()
    );

    /** Volatile server-owned sliding projection: synchronized, never serialized or copied on death. */
    public static final Supplier<AttachmentType<SlidingAttachment>> SLIDING = ATTACHMENT_TYPES.register(
            "sliding", () -> AttachmentType.builder((Supplier<SlidingAttachment>) SlidingAttachment::new)
                    .sync(SlidingAttachment.STREAM_CODEC).build()
    );

    /** Sparse server-persisted chunk atmosphere state; deliberately not synchronized. */
    public static final Supplier<AttachmentType<AtmosphereChunkData>> ATMOSPHERE_CHUNK = ATTACHMENT_TYPES.register(
            "atmosphere_chunk", () -> AttachmentType.builder(AtmosphereChunkData::new)
                    .serialize(AtmosphereChunkData.CODEC, AtmosphereChunkData::hasPersistedState)
                    .build()
    );


    // In your mod constructor, don't forget to register the DeferredRegister to your mod bus:
    public static void register(IEventBus eventBus){
        ATTACHMENT_TYPES.register(eventBus);
    }
}
