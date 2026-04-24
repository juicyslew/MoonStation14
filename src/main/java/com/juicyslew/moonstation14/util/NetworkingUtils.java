package com.juicyslew.moonstation14.util;

import com.juicyslew.moonstation14.component.ModDataAttachments;
import com.juicyslew.moonstation14.ms14.reagent.ReagentAttachment;
import com.juicyslew.moonstation14.util.interfaces.IMS14Codeced;
import com.mojang.serialization.Codec;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.attachment.IAttachmentHolder;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

import java.util.function.Supplier;

public class NetworkingUtils {
    public static <T> void markDirty(IAttachmentHolder holder, Supplier<AttachmentType<T>> dataAttachment) {
        // IAttachmentHolder works for Entities, BlockEntities, and even Levels!
        holder.setData(dataAttachment.get(), holder.getData(dataAttachment.get()));

        // CRITICAL: BlockEntities need a bit more 'shoving' than Entities to sync
        if (holder instanceof BlockEntity be) {
            be.setChanged(); // Marks for saving to disk
            Level level = be.getLevel();
            if (level != null && !level.isClientSide) {
                // Signals the server to send the update packet to clients
                level.sendBlockUpdated(be.getBlockPos(), be.getBlockState(), be.getBlockState(), 3);
            }
        }
    }

    public static <T extends IMS14Codeced<T>> void saveToTag(CompoundTag tag, IAttachmentHolder holder, AttachmentType<T> type) {
        String key = NeoForgeRegistries.ATTACHMENT_TYPES.getKey(type).toString();
        T data = holder.getData(type);
        data.getCodec().encodeStart(NbtOps.INSTANCE, data).result().ifPresent(nbt -> tag.put(key, nbt));
    }

    public static <T extends IMS14Codeced<T>> void loadFromTag(CompoundTag tag, IAttachmentHolder holder, AttachmentType<T> type, Codec<T> codec) {
        String key = NeoForgeRegistries.ATTACHMENT_TYPES.getKey(type).toString();
        if (tag.contains(key)){
            codec.parse(NbtOps.INSTANCE, tag.get(key)).result().ifPresent(data -> holder.setData(type, data));
        }
    }
}
