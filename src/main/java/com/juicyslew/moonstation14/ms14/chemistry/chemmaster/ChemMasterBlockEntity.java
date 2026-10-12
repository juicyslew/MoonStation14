package com.juicyslew.moonstation14.ms14.chemistry.chemmaster;

import com.juicyslew.moonstation14.block.ModBlockEntities;
import com.juicyslew.moonstation14.ms14.chemistry.DispenserCompatibleItem;
import com.juicyslew.moonstation14.ms14.chemistry.ChemMasterBuffer;
import com.juicyslew.moonstation14.component.ModDataComponents;
import com.juicyslew.moonstation14.ms14.reagent.ReagentComponent;
import com.juicyslew.moonstation14.ms14.hands.transfer.LevelItemLocationLedgers;
import com.juicyslew.moonstation14.ms14.machine.itemslots.MachineItemSlot;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.nbt.NbtOps;
import java.util.UUID;
import com.juicyslew.moonstation14.ms14.hands.ItemToken;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;

/** Persistent machine state; identity is unique per placement while slot owner survives saves. */
public final class ChemMasterBlockEntity extends BlockEntity {
    private UUID identity = UUID.randomUUID();
    private UUID slotOwner = UUID.randomUUID();
    private MachineItemSlot input;
    private ChemMasterBuffer buffer = new ChemMasterBuffer();
    private CompoundTag pendingSlot;
    private CompoundTag pendingBuffer;
    private boolean invalidSavedState;
    private long bufferRevision;
    private long stateRevision;
    private ChemMasterControlRequest.Mode mode = ChemMasterControlRequest.Mode.TRANSFER;
    private ChemMasterControlRequest.Sort sort = ChemMasterControlRequest.Sort.OLDEST_FIRST;

    public ChemMasterBlockEntity(BlockPos pos, BlockState state) { super(ModBlockEntities.CHEM_MASTER.get(), pos, state); }
    private boolean ensureState() {
        if (!(level instanceof ServerLevel server) || isRemoved()) return false;
        var chunk = server.getChunkSource().getChunkNow(worldPosition.getX() >> 4, worldPosition.getZ() >> 4);
        if (chunk == null || chunk.getBlockEntities().get(worldPosition) != this) return false;
        if (prototype() == null) return false;
        if (input == null) input = new MachineItemSlot(slotOwner, "beakerSlot", stack -> acceptsInput(stack),
                LevelItemLocationLedgers.forLevel(server));
        else if (!input.resume()) { invalidSavedState = true; return false; }
        if (pendingSlot != null) {
            var decoded = MachineItemSlot.codec().parse(server.registryAccess().createSerializationContext(NbtOps.INSTANCE), pendingSlot).result();
            if (decoded.isEmpty() || !input.restore(decoded.get())) { invalidSavedState = true; return false; }
            pendingSlot = null;
        }
        if (pendingBuffer != null) {
            var decoded = ChemMasterBuffer.SNAPSHOT_CODEC.parse(server.registryAccess().createSerializationContext(NbtOps.INSTANCE), pendingBuffer).result();
            if (decoded.isEmpty()) { invalidSavedState = true; return false; }
            buffer = new ChemMasterBuffer(decoded.get()); pendingBuffer = null;
        }
        invalidSavedState = false;
        return true;
    }
    private boolean acceptsInput(net.minecraft.world.item.ItemStack stack) {
        if (!(level instanceof ServerLevel) || stack.isEmpty()) return false;
        ChemMasterData data = prototype();
        return stack.getItem() instanceof DispenserCompatibleItem compatible
                && acceptsResolvedSlotPolicy(data, compatible.fitsInDispenser(level));
    }
    public UUID placementIdentity() { return identity; }
    public UUID ownerId() { return slotOwner; }
    public ChemMasterData prototype() { return ModChemMasters.resolved(level, ModChemMasters.CHEM_MASTER_ID); }
    public double powerLoadWatts() { return powerLoadWattsFromResolved(prototype()); }
    public int cableReach() { return cableReachFromResolved(prototype()); }
    public static double powerLoadWattsFromResolved(ChemMasterData data) { return data == null ? 0 : data.powerLoadWatts(); }
    public static int cableReachFromResolved(ChemMasterData data) { return data == null ? 0 : data.cableReach(); }
    public static boolean acceptsResolvedSlotPolicy(ChemMasterData data, boolean itemFitsInDispenser) {
        return data != null && data.inputSlot().fitsInDispenser() && itemFitsInDispenser;
    }
    public MachineItemSlot inputSlot() { ensureState(); return input; }
    public ChemMasterBuffer buffer() { ensureState(); return buffer; }
    public boolean isLoadedValid() {
        if (!(level instanceof ServerLevel server) || isRemoved()) return false;
        var chunk = server.getChunkSource().getChunkNow(worldPosition.getX() >> 4, worldPosition.getZ() >> 4);
        return chunk != null && chunk.getBlockEntities().get(worldPosition) == this
                && chunk.getBlockState(worldPosition).getBlock() instanceof ChemMasterBlock && !invalidSavedState && ensureState();
    }
    public synchronized long bufferRevision() { return bufferRevision; }
    public synchronized long stateRevision() { return stateRevision; }
    public synchronized ChemMasterControlRequest.Mode mode() { return mode; }
    public synchronized ChemMasterControlRequest.Sort sort() { return sort; }

    /** External committed slot handoff; call only after the slot operation actually changes state. */
    public synchronized void slotChanged() { stateChanged(); refreshAppearance(); }

    /** Projects only the committed slot contents and the power runtime's cached fresh full-delivery result. */
    public void refreshAppearance() {
        if (!(level instanceof ServerLevel server) || isRemoved() || !(getBlockState().getBlock() instanceof ChemMasterBlock)) return;
        boolean loaded = false;
        MachineItemSlot slot = inputSlot();
        if (slot != null) loaded = !slot.snapshot().stack().isEmpty();
        boolean powered = com.juicyslew.moonstation14.ms14.power.runtime.PowerRuntime
                .hasFullFreshChemMasterPower(server, this, placementIdentity());
        BlockState old = getBlockState();
        if (old.getValue(ChemMasterBlock.POWERED) != powered || old.getValue(ChemMasterBlock.LOADED) != loaded)
            server.setBlock(worldPosition, old.setValue(ChemMasterBlock.POWERED, powered)
                    .setValue(ChemMasterBlock.LOADED, loaded), 3);
    }
    /** External committed state change notification used by hand/session adapters. */
    public synchronized void stateChanged() {
        if (stateRevision == Long.MAX_VALUE) throw new IllegalStateException("ChemMaster state revision exhausted");
        stateRevision++;
        setChanged();
    }
    public synchronized boolean configure(long expectedStateRevision, ChemMasterControlRequest.Mode nextMode,
            ChemMasterControlRequest.Sort nextSort) {
        if (!isLoadedValid() || nextMode == null || nextSort == null || expectedStateRevision != stateRevision) return false;
        if (mode == nextMode && sort == nextSort) return true;
        if (stateRevision == Long.MAX_VALUE) return false;
        mode = nextMode; sort = nextSort; stateRevision++; setChanged();
        return true;
    }

    /** Stages/validates both authorities before publishing either. Caller performs selected-reagent math/reactions. */
    public synchronized boolean commitSolutions(long expectedStateRevision, long expectedSlotRevision,
            ItemToken expectedToken, ItemStack inputAfter, ChemMasterBuffer.Snapshot bufferAfter) {
        if (!(level instanceof ServerLevel server) || !server.getServer().isSameThread() || !isLoadedValid()
                || expectedStateRevision != stateRevision || stateRevision == Long.MAX_VALUE
                || input == null || inputAfter == null || bufferAfter == null) return false;
        var slot = input.snapshot();
        if (slot.revision() != expectedSlotRevision) return false;
        ChemMasterBuffer stagedBuffer;
        try { stagedBuffer = new ChemMasterBuffer(bufferAfter); }
        catch (RuntimeException invalid) { return false; }
        boolean bufferChanged = !buffer.snapshot().equals(stagedBuffer.snapshot());
        if (expectedToken == null) {
            if (slot.token() != null || !slot.stack().isEmpty() || !inputAfter.isEmpty()) return false;
        } else {
            if (slot.token() == null || !slot.token().equals(expectedToken) || inputAfter.isEmpty()
                    || !slot.stack().is(inputAfter.getItem()) || slot.stack().getCount() != inputAfter.getCount()) return false;
        }
        boolean itemChanged = false;
        if (expectedToken != null) {
            ItemStack beforeWithoutReagents = slot.stack();
            ItemStack afterWithoutReagents = inputAfter.copy();
            beforeWithoutReagents.remove(ModDataComponents.REAGENT.get());
            afterWithoutReagents.remove(ModDataComponents.REAGENT.get());
            if (!ItemStack.isSameItemSameComponents(beforeWithoutReagents, afterWithoutReagents)) return false;
            ReagentComponent beforeReagents = slot.stack().get(ModDataComponents.REAGENT.get());
            ReagentComponent afterReagents = inputAfter.get(ModDataComponents.REAGENT.get());
            itemChanged = !java.util.Objects.equals(beforeReagents, afterReagents);
        }
        if (!bufferChanged && !itemChanged) return true;
        if (stateRevision == Long.MAX_VALUE || (bufferChanged && bufferRevision == Long.MAX_VALUE)) return false;
        if (itemChanged && !input.mutate(expectedSlotRevision, expectedToken, candidate -> {
            if (inputAfter.has(ModDataComponents.REAGENT.get()))
                candidate.set(ModDataComponents.REAGENT.get(), inputAfter.get(ModDataComponents.REAGENT.get()));
            else candidate.remove(ModDataComponents.REAGENT.get());
            return true;
        })) return false;
        if (bufferChanged) { buffer = stagedBuffer; bufferRevision++; }
        stateRevision++; setChanged();
        return true;
    }

    /** Conditional external-buffer commit; source setter runs only after all machine preconditions pass. */
    public synchronized boolean commitExternalBuffer(UUID expectedIdentity, long expectedStateRevision,
            long expectedBufferRevision, ChemMasterBuffer.Snapshot staged, java.util.function.BooleanSupplier sourceCommit) {
        if (!(level instanceof ServerLevel server) || !server.getServer().isSameThread() || !isLoadedValid()
                || expectedIdentity == null || !identity.equals(expectedIdentity)
                || expectedStateRevision != stateRevision || expectedBufferRevision != bufferRevision
                || stateRevision == Long.MAX_VALUE || bufferRevision == Long.MAX_VALUE || staged == null || sourceCommit == null)
            return false;
        ChemMasterBuffer replacement;
        try { replacement = new ChemMasterBuffer(staged); } catch (RuntimeException invalid) { return false; }
        try { if (!sourceCommit.getAsBoolean()) return false; } catch (RuntimeException failure) { return false; }
        buffer = replacement; bufferRevision++; stateRevision++; setChanged();
        return true;
    }
    public void resetPlacementIdentity() {
        identity = UUID.randomUUID(); setChanged();
        if (level instanceof ServerLevel server && isLoadedValid())
            com.juicyslew.moonstation14.ms14.power.runtime.PowerRuntime.chemMasterChanged(server, worldPosition);
    }
    @Override protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putUUID("placementIdentity", identity); tag.putUUID("slotOwner", slotOwner);
        CompoundTag slotState = pendingSlot;
        if (invalidSavedState && slotState != null) {
            tag.put("inputSlot", slotState.copy());
        } else if (input != null) {
            var encoded = MachineItemSlot.codec().encodeStart(registries.createSerializationContext(NbtOps.INSTANCE), input.encode()).result();
            if (encoded.isPresent()) tag.put("inputSlot", encoded.get());
            else if (slotState != null) tag.put("inputSlot", slotState.copy());
        } else if (slotState != null) tag.put("inputSlot", slotState.copy());
        if (pendingBuffer != null) tag.put("buffer", pendingBuffer.copy());
        else tag.put("buffer", ChemMasterBuffer.SNAPSHOT_CODEC.encodeStart(registries.createSerializationContext(NbtOps.INSTANCE), buffer.snapshot()).getOrThrow());
        tag.putLong("bufferRevision", bufferRevision);
        tag.putLong("stateRevision", stateRevision); tag.putString("mode", mode.name()); tag.putString("sort", sort.name());
    }
    @Override protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (tag.hasUUID("placementIdentity")) identity = tag.getUUID("placementIdentity");
        if (tag.hasUUID("slotOwner")) slotOwner = tag.getUUID("slotOwner");
        bufferRevision = tag.getLong("bufferRevision");
        stateRevision = Math.max(0, tag.getLong("stateRevision"));
        mode = parseEnum(ChemMasterControlRequest.Mode.class, tag.getString("mode"), ChemMasterControlRequest.Mode.TRANSFER);
        sort = parseEnum(ChemMasterControlRequest.Sort.class, tag.getString("sort"), ChemMasterControlRequest.Sort.OLDEST_FIRST);
        pendingSlot = tag.contains("inputSlot") ? tag.getCompound("inputSlot") : null;
        pendingBuffer = tag.contains("buffer") ? tag.getCompound("buffer") : null;
        input = null; buffer = new ChemMasterBuffer(); invalidSavedState = false;
    }
    @Override public void onLoad() {
        super.onLoad();
        if (ensureState() && level instanceof ServerLevel server && isLoadedValid())
            com.juicyslew.moonstation14.ms14.power.runtime.PowerRuntime.chemMasterChanged(server, worldPosition);
    }
    @Override public void clearRemoved() {
        super.clearRemoved();
        if (input != null && !input.resume()) invalidSavedState = true;
    }
    @Override public void setRemoved() {
        if (level instanceof ServerLevel server) com.juicyslew.moonstation14.ms14.power.runtime.PowerRuntime.chemMasterRemoved(server, worldPosition);
        if (input != null && !input.suspend()) invalidSavedState = true;
        super.setRemoved();
    }
    private static <E extends Enum<E>> E parseEnum(Class<E> type, String value, E fallback) {
        try { return Enum.valueOf(type, value); } catch (IllegalArgumentException ignored) { return fallback; }
    }
}
