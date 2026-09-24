package com.juicyslew.moonstation14.util;

import com.juicyslew.moonstation14.util.interfaces.IMS14Attachment;
import com.juicyslew.moonstation14.util.interfaces.IMS14Component;
import com.juicyslew.moonstation14.ms14.activity.EntityActivity;
import net.minecraft.core.component.DataComponentType;
import net.neoforged.neoforge.attachment.AttachmentType;

import java.util.Objects;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.function.Supplier;

public record SystemLink<A extends IMS14Attachment<A, C>, C extends IMS14Component<C, A>>(
        Supplier<AttachmentType<A>> attachment,
        Supplier<DataComponentType<C>> component,
        Supplier<A> defaultAttachmentSupplier,
        Optional<ActivityBinding<A>> activityBinding
) {
    public SystemLink {
        Objects.requireNonNull(attachment, "attachment");
        Objects.requireNonNull(component, "component");
        Objects.requireNonNull(defaultAttachmentSupplier, "defaultAttachmentSupplier");
        activityBinding = Objects.requireNonNull(activityBinding, "activityBinding");
    }

    public SystemLink(Supplier<AttachmentType<A>> attachment,
                      Supplier<DataComponentType<C>> component,
                      Supplier<A> defaultAttachmentSupplier) {
        this(attachment, component, defaultAttachmentSupplier, Optional.empty());
    }

    public SystemLink(Supplier<AttachmentType<A>> attachment,
                      Supplier<DataComponentType<C>> component,
                      Supplier<A> defaultAttachmentSupplier,
                      ActivityBinding<A> activityBinding) {
        this(attachment, component, defaultAttachmentSupplier, Optional.of(activityBinding));
    }

    public Optional<ActivityBinding<A>> activity() {
        return activityBinding;
    }

    public record ActivityBinding<A>(EntityActivity activity, Predicate<A> needsTicking) {
        public ActivityBinding {
            Objects.requireNonNull(activity, "activity");
            Objects.requireNonNull(needsTicking, "needsTicking");
        }

        public Predicate<A> predicate() {
            return needsTicking;
        }
    }
}
