package com.juicyslew.moonstation14.util;

import com.juicyslew.moonstation14.util.interfaces.IMS14Attachment;
import com.juicyslew.moonstation14.util.interfaces.IMS14Component;
import net.minecraft.core.component.DataComponentType;
import net.neoforged.neoforge.attachment.AttachmentType;

import java.util.function.Function;
import java.util.function.Supplier;

public record SystemLink<A extends IMS14Attachment<A, C>, C extends IMS14Component<C, A>>(
        Supplier<AttachmentType<A>> attachment,
        Supplier<DataComponentType<C>> component,
        Supplier<A> defaultAttachmentSupplier // The "Constructor" goes here once
) {}
