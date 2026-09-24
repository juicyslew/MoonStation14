package com.juicyslew.moonstation14.ms14;

import com.juicyslew.moonstation14.ms14.reagent.ReagentAttachment;
import com.juicyslew.moonstation14.ms14.reagent.ReagentComponent;
import com.juicyslew.moonstation14.ms14.activity.EntityActivitySystem;
import com.juicyslew.moonstation14.util.SystemLink;
import com.juicyslew.moonstation14.util.interfaces.IMS14Attachment;
import com.juicyslew.moonstation14.util.interfaces.IMS14Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.attachment.IAttachmentHolder;
import net.neoforged.neoforge.attachment.AttachmentType;

import java.util.Objects;

public class MS14Provider {

    // --- GET --- //
    public static <A extends IMS14Attachment<A, C>, C extends IMS14Component<C, A>> A get(ItemStack stack, SystemLink<A, C> link) {
        C comp = stack.get(link.component());
        return comp != null ? comp.toAttachment() : link.defaultAttachmentSupplier().get();
    }
    public static <A extends IMS14Attachment<A, C>, C extends IMS14Component<C, A>> A get(IAttachmentHolder holder, SystemLink<A, C> link) {
        AttachmentType<A> attachmentType = link.attachment().get();
        return holder.hasData(attachmentType)
                ? holder.getData(attachmentType)
                : link.defaultAttachmentSupplier().get();
    }

    /**
     * Reads an attachment without creating it when the holder has never used
     * this system.  The returned default is deliberately detached from the
     * holder; callers must use an update method to persist a mutation.
     */
    public static <A extends IMS14Attachment<A, C>, C extends IMS14Component<C, A>> A getDetached(
            IAttachmentHolder holder, SystemLink<A, C> link) {
        if (!holder.hasData(link.attachment().get())) {
            return link.defaultAttachmentSupplier().get();
        }

        // Attachment instances are mutable runtime state. Round-trip through
        // the immutable component so a caller can never mutate the holder by
        // changing the detached value returned here.
        return holder.getData(link.attachment().get()).toComponent().toAttachment();
    }

    public static <A extends IMS14Attachment<A, C>, C extends IMS14Component<C, A>, T> A getDetached(
            TraitHandler<T> handle, SystemLink<A, C> link) {
        return getDetached(handle.holder(), link);
    }

    public static <A extends IMS14Attachment<A, C>, C extends IMS14Component<C, A>> A getDetached(
            Object holder, SystemLink<A, C> link) {
        if (holder instanceof ItemStack stack) {
            return get(stack, link);
        }

        if (holder instanceof TraitHandler<?> handle) {
            return getDetached(handle.holder(), link);
        }

        if (holder instanceof IAttachmentHolder attachmentHolder) {
            return getDetached(attachmentHolder, link);
        }

        throw new IllegalArgumentException("MS14 Critical: Could not resolve detached data holder for "
                + (holder != null ? holder.getClass().getSimpleName() : "null"));
    }
    public static <A extends IMS14Attachment<A, C>, C extends IMS14Component<C, A>, T> A get(TraitHandler<T> handle, SystemLink<A, C> link) {
        return get(handle.holder(), link);
    }
    public static <A extends IMS14Attachment<A, C>, C extends IMS14Component<C, A>> A get(Object holder, SystemLink<A, C> link) {
        // Just in case a handle was cast to Object elsewhere
        if (holder instanceof ItemStack stack) {
            return get(stack, link);
        }

        if (holder instanceof IAttachmentHolder attachmentHolder) {
            return get(attachmentHolder, link);
        }

        if (holder instanceof TraitHandler handle) {
            return get(handle.holder(), link);
        }

        throw new IllegalArgumentException("MS14 Critical: Could not resolve data holder for "
                + (holder != null ? holder.getClass().getSimpleName() : "null"));
    }

    // --- UPDATE --- //
    public static <A extends IMS14Attachment<A, C>, C extends IMS14Component<C, A>> void update(ItemStack stack, SystemLink<A, C> link, A data) {
        stack.set(link.component(), data.toComponent());
    }
    public static <A extends IMS14Attachment<A, C>, C extends IMS14Component<C, A>> void update(BlockEntity be, SystemLink<A, C> link, A data) {
        be.setData(link.attachment().get(), data);
        // Replacing an existing NeoForge attachment does not reliably invoke
        // the BlockEntity change hook, which also synchronizes derived block state.
        be.setChanged();
        Level level = be.getLevel();
        if (level != null) {
            // Notifies the world that this block's data has changed (updates renderers/comparators)
            level.sendBlockUpdated(be.getBlockPos(), be.getBlockState(), be.getBlockState(), 3);
        }
    }
    public static <A extends IMS14Attachment<A, C>, C extends IMS14Component<C, A>> void update(Entity entity, SystemLink<A, C> link, A data) {
        entity.setData(link.attachment().get(), data);
        EntityActivitySystem.update(entity, link, data);
    }
    public static <A extends IMS14Attachment<A, C>, C extends IMS14Component<C, A>> void update(
            IAttachmentHolder holder, SystemLink<A, C> link, A data) {
        if (holder instanceof Entity entity) {
            update(entity, link, data);
            return;
        }
        holder.setData(link.attachment().get(), data);
    }

    public static <A extends IMS14Attachment<A, C>, C extends IMS14Component<C, A>, T> void update(TraitHandler<T> handle, SystemLink<A, C> link, A data) {
        update(handle.holder(), link, data);
    }
    public static <A extends IMS14Attachment<A, C>, C extends IMS14Component<C, A>> void update(Object holder, SystemLink<A, C> link, A data) {
        if (holder instanceof ItemStack stack) {
            update(stack, link, data);
            return;
        }

        if (holder instanceof Entity entity) {
            // Note, BlockEntity and Entity are both IAttachmentHolders.
            update(entity, link, data);
            return;
        }

        if (holder instanceof BlockEntity be) {
            // Note, BlockEntity and Entity are both IAttachmentHolders.
            update(be, link, data);
            return;
        }

        if (holder instanceof IAttachmentHolder attachmentHolder) {
            update(attachmentHolder, link, data);
            return;
        }

        if (holder instanceof TraitHandler handle) {
            update(handle.holder(), link, data);
            return;
        }

        throw new IllegalArgumentException("MS14 Critical: Could not resolve update target for "
                + (holder != null ? holder.getClass().getSimpleName() : "null"));
    }

    /** Captures a component snapshot before a mutable attachment is changed. */
    public static <A extends IMS14Attachment<A, C>, C extends IMS14Component<C, A>> C snapshot(A attachment) {
        return Objects.requireNonNull(attachment, "attachment").toComponent();
    }

    /**
     * Persists an attachment only when its immutable component differs from
     * the supplied pre-mutation snapshot.
     */
    public static <A extends IMS14Attachment<A, C>, C extends IMS14Component<C, A>> boolean updateIfChanged(
            Object holder, SystemLink<A, C> link, C before, A attachment) {
        Objects.requireNonNull(before, "before");
        Objects.requireNonNull(attachment, "attachment");
        C current = attachment.toComponent();
        if (before.equals(current)) {
            return false;
        }
        update(holder, link, attachment);
        return true;
    }

    public static <A extends IMS14Attachment<A, C>, C extends IMS14Component<C, A>, T> boolean updateIfChanged(
            TraitHandler<T> handle, SystemLink<A, C> link, C before, A attachment) {
        return updateIfChanged(handle.holder(), link, before, attachment);
    }
}
