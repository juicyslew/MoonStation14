package com.juicyslew.moonstation14.component;

import com.juicyslew.moonstation14.MoonStation14;
import com.juicyslew.moonstation14.component.codec.ReagentContainerData;
import com.mojang.serialization.Codec;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.items.ItemStackHandler;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

import java.util.function.Supplier;

public class ModDataAttachments {
    // Create the DeferredRegister for attachment types
    public static final DeferredRegister<AttachmentType<?>> ATTACHMENT_TYPES = DeferredRegister.create(NeoForgeRegistries.ATTACHMENT_TYPES, MoonStation14.MOD_ID);

    // Serialization via codec
    public static final Supplier<AttachmentType<ReagentContainerData>> REAGENT_CONTAINER = ATTACHMENT_TYPES.register(
            "reagent_container", () -> AttachmentType.builder(() -> new ReagentContainerData()).serialize(ReagentContainerData.CODEC).build()
    );


    // In your mod constructor, don't forget to register the DeferredRegister to your mod bus:
    public static void register(IEventBus eventBus){
        ATTACHMENT_TYPES.register(eventBus);
    }
}
