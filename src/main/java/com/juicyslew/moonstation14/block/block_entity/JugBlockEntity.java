package com.juicyslew.moonstation14.block.block_entity;

import com.juicyslew.moonstation14.block.ModBlockEntities;
import com.juicyslew.moonstation14.block.custom.JugBlock;
import com.juicyslew.moonstation14.component.ModDataAttachments;
import com.juicyslew.moonstation14.component.ModDataComponents;
import com.juicyslew.moonstation14.ms14.MS14Provider;
import com.juicyslew.moonstation14.ms14.reagent.ReagentAttachment;
import com.juicyslew.moonstation14.ms14.reagent.ReagentComponent;
import com.juicyslew.moonstation14.ms14.reagent.IReagentTrait;
import com.juicyslew.moonstation14.ms14.reagent.ReagentSystem;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.attachment.AttachmentType;

import javax.annotation.Nullable;

import static com.juicyslew.moonstation14.util.MapOperations.getTotal;
import static com.juicyslew.moonstation14.util.NetworkingUtils.*;

public class JugBlockEntity extends BlockEntity implements IReagentTrait {
    // Gonna probably want a generic ContainerBlockEntity.
    public JugBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.JUG.get(), pos, state);
    }

    @Override public float getCapacity() { return 200f; }

    @Override
    public ClientboundBlockEntityDataPacket getUpdatePacket() {
        // REQUIRED: Without this, the server returns null and nothing is sent.
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = super.getUpdateTag(registries);

        // Use Codec for NBT-based
        saveToTag(tag, this, ModDataAttachments.REAGENT.get());

        return tag;
    }

    @Override
    public void handleUpdateTag(CompoundTag tag, HolderLookup.Provider registries) {
        super.handleUpdateTag(tag, registries);

        // Unpack the data
        loadFromTag(tag, this, ModDataAttachments.REAGENT.get(), ReagentAttachment.CODEC);

        // Tell the renderer the data is here and it's time to draw
        if (this.level != null && this.level.isClientSide) {
            this.requestModelDataUpdate();
        }
    }

    @Override
    public void onDataPacket(Connection net, ClientboundBlockEntityDataPacket pkt, HolderLookup.Provider registries) {
        // 1. Get the tag from the packet
        CompoundTag tag = pkt.getTag();
        if (tag != null) {
            // 2. Load the data using your existing logic
            loadFromTag(tag, this, ModDataAttachments.REAGENT.get(), ReagentAttachment.CODEC);

            // 3. Trigger the rerender
            if (this.level != null && this.level.isClientSide) {
                // This is essential for ModelData and Tints
                this.requestModelDataUpdate();
                // This forces a chunk rebuild (visual redraw)
                this.level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
            }
        }
    }

    @Override
    public void setChanged() {
        super.setChanged();
        // RUNS WHENEVER DATA IS DIRTIED!
        if (this.level != null && !this.level.isClientSide) {
            updateFillLevel();
        }
    }

    public void updateFillLevel() {
        ReagentAttachment contents = MS14Provider.getDetached(this, ReagentSystem.bridge);
        float percentage = Mth.clamp(getTotal(contents.getMap()) / getCapacity(), 0f, 1f);
        int newLevel = Mth.clamp(Math.round(percentage * 6), 0, 6);

        BlockState currentState = getBlockState();
        if (currentState.getValue(JugBlock.FILL_LEVEL) != newLevel) {
            level.setBlock(worldPosition, currentState.setValue(JugBlock.FILL_LEVEL, newLevel), 3);
        }
    }

    public void saveToItem(ItemStack stack) {
        var attachmentData = this.getExistingDataOrNull(ModDataAttachments.REAGENT.get());
        if (attachmentData != null && !attachmentData.isEmpty()) {
            // Shove the non-empty map into the item's component.
            stack.set(ModDataComponents.REAGENT.get(), attachmentData.toComponent());
        } else {
            stack.remove(ModDataComponents.REAGENT.get());
        }
    }
}
