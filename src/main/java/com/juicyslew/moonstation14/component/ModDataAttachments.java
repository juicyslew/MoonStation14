package com.juicyslew.moonstation14.component;

import com.juicyslew.moonstation14.MoonStation14;
import com.juicyslew.moonstation14.component.codec.attachment.DamageData;
import com.juicyslew.moonstation14.ms14.reagent.ReagentAttachment;
import com.juicyslew.moonstation14.ms14.reagent.ReagentComponent;
import com.juicyslew.moonstation14.ms14.status_effect.StatusEffectAttachment;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

import java.util.function.Supplier;

public class ModDataAttachments {
    // Create the DeferredRegister for attachment types
    public static final DeferredRegister<AttachmentType<?>> ATTACHMENT_TYPES = DeferredRegister.create(NeoForgeRegistries.ATTACHMENT_TYPES, MoonStation14.MOD_ID);

    public static final Supplier<AttachmentType<ReagentAttachment>> REAGENT = ATTACHMENT_TYPES.register(
            "reagent", () -> AttachmentType.builder(() -> new ReagentAttachment()).serialize(ReagentAttachment.CODEC).sync(ReagentAttachment.STREAM_CODEC).build()
    );

    public static final Supplier<AttachmentType<DamageData>> DAMAGE = ATTACHMENT_TYPES.register(
            "damage", () -> AttachmentType.builder(() -> new DamageData()).serialize(DamageData.CODEC).sync(DamageData.STREAM_CODEC).build()
    );

    public static final Supplier<AttachmentType<StatusEffectAttachment>> STATUS_EFFECT = ATTACHMENT_TYPES.register(
            "status_effect", () -> AttachmentType.builder(() -> new StatusEffectAttachment()).serialize(StatusEffectAttachment.CODEC).sync(StatusEffectAttachment.STREAM_CODEC).build()
    );


    // In your mod constructor, don't forget to register the DeferredRegister to your mod bus:
    public static void register(IEventBus eventBus){
        ATTACHMENT_TYPES.register(eventBus);
    }
}
