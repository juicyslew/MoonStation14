package com.juicyslew.moonstation14.ms14;

import com.juicyslew.moonstation14.ms14.reagent.ReagentAttachment;
import com.juicyslew.moonstation14.ms14.reagent.ReagentComponent;
import com.juicyslew.moonstation14.ms14.reagent.ReagentHandle;
import com.juicyslew.moonstation14.util.SystemLink;
import com.juicyslew.moonstation14.util.interfaces.IMS14Attachment;
import com.juicyslew.moonstation14.util.interfaces.IMS14Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.attachment.IAttachmentHolder;

public class MS14Provider {

    // --- GET --- //
    public static <A extends IMS14Attachment<A, C>, C extends IMS14Component<C, A>> A get(ItemStack stack, SystemLink<A, C> link) {
        C comp = stack.get(link.component());
        return comp != null ? comp.toAttachment() : link.defaultAttachmentSupplier().get();
    }
    public static <A extends IMS14Attachment<A, C>, C extends IMS14Component<C, A>> A get(IAttachmentHolder holder, SystemLink<A, C> link) {
        return holder.getData(link.attachment().get());
    }
    public static <A extends IMS14Attachment<A, C>, C extends IMS14Component<C, A>> A get(ReagentHandle handle, SystemLink<A, C> link) {
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

        if (holder instanceof ReagentHandle handle) {
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
        // be.setChanged(); // SET DATA CALLS THIS.
        Level level = be.getLevel();
        if (level != null) {
            // Notifies the world that this block's data has changed (updates renderers/comparators)
            level.sendBlockUpdated(be.getBlockPos(), be.getBlockState(), be.getBlockState(), 3);
        }
    }
    public static <A extends IMS14Attachment<A, C>, C extends IMS14Component<C, A>> void update(Entity entity, SystemLink<A, C> link, A data) {
        entity.setData(link.attachment().get(), data);
    }

    public static <A extends IMS14Attachment<A, C>, C extends IMS14Component<C, A>> void update(ReagentHandle handle, SystemLink<A, C> link, A data) {
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

        if (holder instanceof ReagentHandle handle) {
            update(handle.holder(), link, data);
            return;
        }

        throw new IllegalArgumentException("MS14 Critical: Could not resolve update target for "
                + (holder != null ? holder.getClass().getSimpleName() : "null"));
    }
}
