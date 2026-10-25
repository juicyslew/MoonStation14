package com.juicyslew.moonstation14.ms14.chemistry.chemmaster;

import com.juicyslew.moonstation14.block.ModBlockEntities;
import com.juicyslew.moonstation14.ms14.chemistry.DispenserCompatibleItem;
import com.juicyslew.moonstation14.ms14.chemistry.ChemMasterBuffer;
import com.juicyslew.moonstation14.component.ModDataComponents;
import com.juicyslew.moonstation14.ms14.reagent.ReagentComponent;
import com.juicyslew.moonstation14.ms14.reagent.ReagentAttachment;
import com.juicyslew.moonstation14.ms14.reagent.IReagentTrait;
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
import com.juicyslew.moonstation14.ms14.hands.transfer.ItemLocation;
import com.juicyslew.moonstation14.ms14.hands.transfer.ItemLocationLedger;
import net.minecraft.world.item.ItemStack;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.entity.BlockEntity;

/** Persistent machine state; identity is unique per placement while slot owner survives saves. */
public final class ChemMasterBlockEntity extends BlockEntity {
    private UUID identity = UUID.randomUUID();
    private UUID slotOwner = UUID.randomUUID();
    private MachineItemSlot input;
    private MachineItemSlot output;
    private ChemMasterBuffer buffer = new ChemMasterBuffer();
    private CompoundTag pendingSlot;
    private CompoundTag pendingBuffer;
    private boolean invalidSavedState;
    private long bufferRevision;
    private long stateRevision;
    private ChemMasterControlRequest.Mode mode = ChemMasterControlRequest.Mode.TRANSFER;
    private ChemMasterControlRequest.Sort sort = ChemMasterControlRequest.Sort.OLDEST_FIRST;
    private PackagingSource drawSource = PackagingSource.BUFFER;
    private int pillType;

    public ChemMasterBlockEntity(BlockPos pos, BlockState state) { super(ModBlockEntities.CHEM_MASTER.get(), pos, state); }
    private boolean ensureState() {
        if (!(level instanceof ServerLevel server) || isRemoved()) return false;
        var chunk = server.getChunkSource().getChunkNow(worldPosition.getX() >> 4, worldPosition.getZ() >> 4);
        if (chunk == null || chunk.getBlockEntities().get(worldPosition) != this) return false;
        if (prototype() == null) return false;
        if (input == null) input = new MachineItemSlot(slotOwner, "beakerSlot", stack -> acceptsInput(stack),
                LevelItemLocationLedgers.forLevel(server));
        else if (!input.resume()) { invalidSavedState = true; return false; }
        if (output == null) output = new MachineItemSlot(slotOwner, "outputSlot", this::acceptsOutput,
                LevelItemLocationLedgers.forLevel(server));
        else if (!output.resume()) { invalidSavedState = true; return false; }
        if (pendingSlot != null) {
            var decoded = MachineItemSlot.codec().parse(server.registryAccess().createSerializationContext(NbtOps.INSTANCE), pendingSlot).result();
            if (decoded.isEmpty() || !input.restore(decoded.get())) { invalidSavedState = true; return false; }
            pendingSlot = null;
        }
        if (pendingOutputSlot != null) {
            var decoded = MachineItemSlot.codec().parse(server.registryAccess().createSerializationContext(NbtOps.INSTANCE), pendingOutputSlot).result();
            if (decoded.isEmpty() || !output.restore(decoded.get())) { invalidSavedState = true; return false; }
            pendingOutputSlot = null;
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
    private CompoundTag pendingOutputSlot;
    private boolean acceptsOutput(ItemStack stack) {
        if (!(level instanceof ServerLevel) || stack.isEmpty()) return false;
        var profileId = com.juicyslew.moonstation14.ms14.chemistry.output.ModChemContainers.prototype(stack);
        var machine = prototype();
        var profile = com.juicyslew.moonstation14.ms14.chemistry.output.ModChemContainers.resolve(level, profileId);
        return acceptsResolvedOutputPolicy(machine, stack, profile, profileId);
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
    public static boolean acceptsResolvedOutputPolicy(ChemMasterData machine, ItemStack stack,
            com.juicyslew.moonstation14.ms14.chemistry.output.ChemContainerData profile,
            net.minecraft.resources.ResourceLocation profileId) {
        if (machine == null || stack == null || stack.isEmpty() || profile == null || profileId == null
                || !profileId.equals(com.juicyslew.moonstation14.ms14.chemistry.output.ModChemContainers.prototype(stack))
                || !machine.outputSlot().accepts(profileId)) return false;
        if (profileId.equals(com.juicyslew.moonstation14.ms14.chemistry.output.ModChemContainers.id("bottle")))
            return profile.solution().isPresent() && profile.storage().isEmpty();
        if (profileId.equals(com.juicyslew.moonstation14.ms14.chemistry.output.ModChemContainers.id("pill_canister"))) {
            var storage = profile.storage();
            return profile.solution().isEmpty() && storage.isPresent() && storage.orElseThrow().width().isPresent()
                    && storage.orElseThrow().height().isPresent()
                    && storage.orElseThrow().whitelistItem().filter("moonstation14:pill"::equals).isPresent();
        }
        return false;
    }
    public MachineItemSlot inputSlot() { ensureState(); return input; }
    public MachineItemSlot outputSlot() { ensureState(); return output; }
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
    public synchronized PackagingSource drawSource() { return drawSource; }
    public synchronized int pillType() { return pillType; }
    public synchronized boolean configureDrawSource(long expectedRevision, PackagingSource source) {
        if (!isLoadedValid() || expectedRevision != stateRevision || source == null || stateRevision == Long.MAX_VALUE) return false;
        if (drawSource == source) return true;
        drawSource = source; stateRevision++; setChanged(); return true;
    }
    public synchronized boolean configurePillType(long expectedRevision, int type) {
        if (!isLoadedValid() || expectedRevision != stateRevision || type < 0 || type > 19 || stateRevision == Long.MAX_VALUE) return false;
        if (pillType == type) return true;
        pillType = type; stateRevision++; setChanged(); return true;
    }

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
        return commitSolutions(identity, expectedStateRevision, expectedSlotRevision, expectedToken, inputAfter, bufferAfter,
                bufferRevision, -1, null, null, java.util.Map.of());
    }

    /** Extended existing source commit for one staged output-slot mutation and its owned child claims. */
    public synchronized boolean commitSolutions(UUID expectedIdentity, long expectedStateRevision, long expectedSlotRevision,
            ItemToken expectedToken, ItemStack inputAfter, ChemMasterBuffer.Snapshot bufferAfter,
            long expectedBufferRevision, long expectedOutputRevision, ItemToken expectedOutputToken, ItemStack outputAfter,
            java.util.Map<ItemToken, ItemLocation> newChildren) {
        if (!(level instanceof ServerLevel server) || !server.getServer().isSameThread() || !isLoadedValid()
                || expectedIdentity == null || !identity.equals(expectedIdentity)
                || expectedStateRevision != stateRevision || stateRevision == Long.MAX_VALUE
                || input == null || output == null || inputAfter == null || bufferAfter == null || newChildren == null
                || expectedBufferRevision != bufferRevision
                || (outputAfter == null) != (expectedOutputToken == null)) return false;
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
        var outputSlot = output.snapshot();
        boolean outputChanged = false;
        if (outputAfter != null) {
            if (outputSlot.revision() != expectedOutputRevision || !java.util.Objects.equals(outputSlot.token(), expectedOutputToken)
                    || outputSlot.stack().isEmpty() || outputAfter.isEmpty()
                    || !outputSlot.stack().is(outputAfter.getItem()) || outputSlot.stack().getCount() != outputAfter.getCount()
                    || !acceptsOutput(outputAfter)) return false;
            ItemStack beforeOutput = outputSlot.stack(), afterOutput = outputAfter.copy();
            for (var component : java.util.List.of(ModDataComponents.REAGENT.get(), ModDataComponents.GRID_CONTENTS.get(),
                    ModDataComponents.CHEM_OUTPUT_LABEL.get(), DataComponents.CUSTOM_NAME)) {
                beforeOutput.remove(component); afterOutput.remove(component);
            }
            if (!ItemStack.isSameItemSameComponents(beforeOutput, afterOutput)) return false;
            outputChanged = !ItemStack.matches(outputSlot.stack(), outputAfter);
            if (!newChildren.isEmpty() && (outputSlot.token() == null || expectedOutputRevision == Long.MAX_VALUE)) return false;
        } else if (!newChildren.isEmpty()) return false;
        if (!bufferChanged && !itemChanged && !outputChanged) return true;
        if (stateRevision == Long.MAX_VALUE || (bufferChanged && bufferRevision == Long.MAX_VALUE)) return false;
        if (outputChanged && outputSlot.revision() == Long.MAX_VALUE) return false;
        ItemLocationLedger ledger = LevelItemLocationLedgers.forLevel(server);
        ItemLocationLedger.Result registered = null;
        if (!newChildren.isEmpty()) {
            registered = ledger.registerBatch(ledger.snapshot().revision(), newChildren);
            if (!registered.accepted()) return false;
        }
        if (outputChanged && !mutatePackageStack(output, outputSlot.revision(), outputSlot.token(), outputAfter)) {
            if (registered != null) ledger.unregisterBatch(registered.snapshot().revision(), newChildren);
            return false;
        }
        if (itemChanged && !input.mutate(expectedSlotRevision, expectedToken, candidate -> {
            var component = inputAfter.get(ModDataComponents.REAGENT.get());
            if (component == null) candidate.remove(ModDataComponents.REAGENT.get()); else candidate.set(ModDataComponents.REAGENT.get(), component);
            return true;
        })) {
            if (outputChanged) restoreOutput(outputSlot.revision() + 1, outputSlot.token(), outputSlot.stack());
            if (registered != null) ledger.unregisterBatch(ledger.snapshot().revision(), newChildren);
            return false;
        }
        if (bufferChanged) { buffer = stagedBuffer; bufferRevision++; }
        stateRevision++; setChanged();
        return true;
    }

    private static boolean mutatePackageStack(MachineItemSlot slot, long revision, ItemToken token, ItemStack after) {
        return slot.mutate(revision, token, candidate -> {
            var reagent = after.get(ModDataComponents.REAGENT.get());
            if (reagent == null) candidate.remove(ModDataComponents.REAGENT.get()); else candidate.set(ModDataComponents.REAGENT.get(), reagent);
            var grid = after.get(ModDataComponents.GRID_CONTENTS.get());
            if (grid == null) candidate.remove(ModDataComponents.GRID_CONTENTS.get()); else candidate.set(ModDataComponents.GRID_CONTENTS.get(), grid);
            var name = after.get(DataComponents.CUSTOM_NAME);
            if (name == null) candidate.remove(DataComponents.CUSTOM_NAME); else candidate.set(DataComponents.CUSTOM_NAME, name);
            var label = after.get(ModDataComponents.CHEM_OUTPUT_LABEL.get());
            if (label == null) candidate.remove(ModDataComponents.CHEM_OUTPUT_LABEL.get()); else candidate.set(ModDataComponents.CHEM_OUTPUT_LABEL.get(), label);
            return true;
        });
    }

    public enum PackagingSource { BUFFER, INPUT }
    public enum PackagingResult { SUCCESS, INVALID_REQUEST, MISSING_SOURCE, INVALID_OUTPUT, INSUFFICIENT_SOURCE,
        OUTPUT_FULL, STALE_STATE, OWNERSHIP_REJECTED }
    private record NewPill(ItemToken token, ItemStack stack, int x, int y, com.juicyslew.moonstation14.ms14.storage.grid.GridGeometry.Rotation rotation) { }

    /** Authoritative package transaction. All arguments are untrusted UI values/revision proofs. */
    public synchronized PackagingResult createPills(UUID expectedIdentity, long expectedStateRevision,
            long expectedInputRevision, long expectedBufferRevision, long expectedOutputRevision,
            ItemToken expectedInputToken, ItemToken expectedOutputToken, PackagingSource source,
            int count, int doseUnits, String label, int pillType) {
        if (!(level instanceof ServerLevel server) || !server.getServer().isSameThread() || !isLoadedValid()
                || expectedIdentity == null || !identity.equals(expectedIdentity) || stateRevision != expectedStateRevision
                || stateRevision == Long.MAX_VALUE || bufferRevision != expectedBufferRevision
                || source == null || input == null || output == null
                || source == PackagingSource.BUFFER && bufferRevision == Long.MAX_VALUE)
            return PackagingResult.STALE_STATE;
        var in = input.snapshot(); var out = output.snapshot();
        if (in.revision() != expectedInputRevision || out.revision() != expectedOutputRevision
                || !java.util.Objects.equals(in.token(), expectedInputToken)
                || !java.util.Objects.equals(out.token(), expectedOutputToken)) return PackagingResult.STALE_STATE;
        if (out.token() == null || out.stack().isEmpty()) return PackagingResult.INVALID_OUTPUT;
        ChemMasterData machine = prototype();
        if (machine == null || machine.pillDosageLimit() <= 0 || doseUnits <= 0 || doseUnits > machine.pillDosageLimit()
                || count <= 0 || pillType < 0 || pillType > 19 || label == null
                || label.length() > ChemMasterPackagingPlan.MAX_LABEL_LENGTH) return PackagingResult.INVALID_REQUEST;
        var pillProfile = com.juicyslew.moonstation14.ms14.chemistry.output.ModChemContainers.resolve(level,
                com.juicyslew.moonstation14.ms14.chemistry.output.ModChemContainers.id("pill"));
        if (pillProfile == null || pillProfile.solution().isEmpty() || pillProfile.solution().orElseThrow().canReact()
                || Math.multiplyExact((long) doseUnits, 100L)
                > com.juicyslew.moonstation14.ms14.reagent.ReagentUnits.fromDouble(pillProfile.solution().orElseThrow().capacity()))
            return PackagingResult.INVALID_REQUEST;
        var outputProfileId = com.juicyslew.moonstation14.ms14.chemistry.output.ModChemContainers.prototype(out.stack());
        var outputProfile = com.juicyslew.moonstation14.ms14.chemistry.output.ModChemContainers.resolve(level, outputProfileId);
        if (!acceptsResolvedOutputPolicy(machine, out.stack(), outputProfile, outputProfileId)
                || !out.stack().is(com.juicyslew.moonstation14.item.ModItems.PILL_CANISTER))
            return PackagingResult.INVALID_OUTPUT;
        var grid = com.juicyslew.moonstation14.ms14.storage.grid.GridContents.read(out.stack()).orElse(null);
        if (grid == null) return PackagingResult.INVALID_OUTPUT;
        var width = com.juicyslew.moonstation14.ms14.storage.grid.GridContents.width(out.stack());
        var height = com.juicyslew.moonstation14.ms14.storage.grid.GridContents.height(out.stack());
        var geometry = com.juicyslew.moonstation14.ms14.storage.grid.GridGeometry.Snapshot.<String>empty(
                com.juicyslew.moonstation14.ms14.storage.grid.GridGeometry.StorageMask.rectangle(width, height));
        for (var entry : grid.entries()) {
            var placed = geometry.place(entry.token(), com.juicyslew.moonstation14.ms14.storage.grid.ItemFootprints.forStack(entry.stack()),
                    new com.juicyslew.moonstation14.ms14.storage.grid.GridGeometry.Cell(entry.x(), entry.y()), entry.rotation());
            if (!placed.accepted()) return PackagingResult.INVALID_OUTPUT;
            geometry = placed.snapshot();
        }
        long freeCellUpperBound = (long) width * height - geometry.placements().values().stream()
                .mapToLong(p -> p.occupiedCells().size()).sum();
        if (count > freeCellUpperBound
                || count > com.juicyslew.moonstation14.ms14.storage.grid.GridGeometry.MAX_ENTRIES - grid.entries().size())
            return PackagingResult.OUTPUT_FULL;
        var pill = new ItemStack(com.juicyslew.moonstation14.item.ModItems.PILL.get());
        pill.set(com.juicyslew.moonstation14.component.ModDataComponents.PILL_TYPE.get(),
                new com.juicyslew.moonstation14.ms14.chemistry.output.PillData(pillType));
        applyPackageLabel(pill, label);
        var footprint = com.juicyslew.moonstation14.ms14.storage.grid.ItemFootprints.forStack(pill);
        var children = new java.util.ArrayList<NewPill>(Math.min(count, width * height));
        for (int i = 0; i < count; i++) {
            com.juicyslew.moonstation14.ms14.storage.grid.GridGeometry.Mutation<String> placement = null;
            for (int y = 0; y < height && placement == null; y++) for (int x = 0; x < width && placement == null; x++) {
                var attempt = geometry.place("plan:" + i, footprint,
                        new com.juicyslew.moonstation14.ms14.storage.grid.GridGeometry.Cell(x, y),
                        com.juicyslew.moonstation14.ms14.storage.grid.GridGeometry.Rotation.DEG_0);
                if (attempt.accepted()) placement = attempt;
            }
            if (placement == null) return PackagingResult.OUTPUT_FULL;
            var anchor = placement.placement().anchor();
            var token = new ItemToken(UUID.randomUUID().toString());
            children.add(new NewPill(token, pill.copy(), anchor.x(), anchor.y(), placement.placement().rotation()));
            geometry = placement.snapshot();
        }
        java.util.Map<net.minecraft.resources.ResourceKey<com.juicyslew.moonstation14.component.codec.json.ReagentData>, Long> sourceContents;
        ChemMasterBuffer stagedBuffer = new ChemMasterBuffer(buffer.snapshot());
        ItemStack inputAfter = in.stack();
        if (source == PackagingSource.BUFFER) {
            var sourceMap = stagedBuffer.quantities();
            sourceContents = sourceMap;
        } else {
            if (in.token() == null || in.stack().isEmpty() || !(in.stack().getItem() instanceof IReagentTrait))
                return PackagingResult.MISSING_SOURCE;
            var component = in.stack().get(com.juicyslew.moonstation14.component.ModDataComponents.REAGENT.get());
            ReagentAttachment sourceAttachment = component == null ? new ReagentAttachment() : component.toAttachment();
            sourceContents = sourceAttachment.snapshotUnits();
        }
        ChemMasterPackagingPlan.Pills plan;
        try { plan = ChemMasterPackagingPlan.pills(sourceContents, count, doseUnits, machine.pillDosageLimit(), pillType, label); }
        catch (RuntimeException invalid) { return sourceContents.isEmpty() ? PackagingResult.MISSING_SOURCE : PackagingResult.INSUFFICIENT_SOURCE; }
        if (source == PackagingSource.BUFFER) {
            stagedBuffer = new ChemMasterBuffer(new ChemMasterBuffer.Snapshot(
                    com.juicyslew.moonstation14.ms14.reagent.ReagentComponent.fromCents(plan.sourceRemainder()), buffer.snapshot().order()));
        } else {
            var attachment = new ReagentAttachment(com.juicyslew.moonstation14.ms14.reagent.ReagentComponent.fromCents(plan.sourceRemainder()));
            inputAfter = in.stack();
            if (attachment.isEmpty()) inputAfter.remove(com.juicyslew.moonstation14.component.ModDataComponents.REAGENT.get());
            else inputAfter.set(com.juicyslew.moonstation14.component.ModDataComponents.REAGENT.get(), attachment.toComponent());
        }
        for (int i = 0; i < children.size(); i++) {
            NewPill child = children.get(i);
            var doseAttachment = new ReagentAttachment(com.juicyslew.moonstation14.ms14.reagent.ReagentComponent.fromCents(plan.doses().get(i)));
            ItemStack filledPill = child.stack();
            filledPill.set(com.juicyslew.moonstation14.component.ModDataComponents.REAGENT.get(), doseAttachment.toComponent());
            children.set(i, new NewPill(child.token(), filledPill, child.x(), child.y(), child.rotation()));
        }
        var updated = new java.util.ArrayList<>(grid.entries());
        var additions = new java.util.LinkedHashMap<ItemToken, ItemLocation>();
        ItemToken parent = out.token();
        for (NewPill child : children) {
            updated.add(new com.juicyslew.moonstation14.ms14.storage.grid.GridContents.Entry(child.token().value(), child.stack(),
                    child.x(), child.y(), child.rotation()));
            additions.put(child.token(), new ItemLocation.StorageCell(parent, "main", ItemLocation.Orientation.UNSPECIFIED,
                    child.x() + "," + child.y()));
        }
        var gridResult = com.juicyslew.moonstation14.ms14.storage.grid.GridContents.applyCopyWithGrid(out.stack(),
                com.juicyslew.moonstation14.ms14.storage.grid.GridContents.of(updated)).orElse(null);
        if (gridResult == null) return PackagingResult.OUTPUT_FULL;
        applyPackageLabel(gridResult, label);
        if (!commitSolutions(expectedIdentity, expectedStateRevision, expectedInputRevision, expectedInputToken,
                source == PackagingSource.INPUT ? inputAfter : in.stack(),
                source == PackagingSource.BUFFER ? stagedBuffer.snapshot() : buffer.snapshot(),
                expectedBufferRevision, expectedOutputRevision, expectedOutputToken, gridResult, additions))
            return PackagingResult.STALE_STATE;
        return PackagingResult.SUCCESS;
    }

    private static void applyPackageLabel(ItemStack stack, String label) {
        if (label.isEmpty()) {
            stack.remove(DataComponents.CUSTOM_NAME);
            stack.remove(ModDataComponents.CHEM_OUTPUT_LABEL.get());
        } else {
            stack.set(ModDataComponents.CHEM_OUTPUT_LABEL.get(),
                    new com.juicyslew.moonstation14.ms14.chemistry.output.ChemOutputLabel(label));
            stack.set(DataComponents.CUSTOM_NAME, Component.translatable("item.moonstation14.chemistry.output_label",
                    stack.getItem().getName(stack), Component.literal(label)));
        }
    }
    private boolean restoreOutput(long expectedRevision, ItemToken token, ItemStack original) {
        return output.mutate(expectedRevision, token, candidate -> {
            var grid = original.get(com.juicyslew.moonstation14.component.ModDataComponents.GRID_CONTENTS.get());
            if (grid == null) candidate.remove(com.juicyslew.moonstation14.component.ModDataComponents.GRID_CONTENTS.get());
            else candidate.set(com.juicyslew.moonstation14.component.ModDataComponents.GRID_CONTENTS.get(), grid);
            var reagent = original.get(com.juicyslew.moonstation14.component.ModDataComponents.REAGENT.get());
            if (reagent == null) candidate.remove(com.juicyslew.moonstation14.component.ModDataComponents.REAGENT.get());
            else candidate.set(com.juicyslew.moonstation14.component.ModDataComponents.REAGENT.get(), reagent);
            var name = original.get(DataComponents.CUSTOM_NAME);
            if (name == null) candidate.remove(DataComponents.CUSTOM_NAME); else candidate.set(DataComponents.CUSTOM_NAME, name);
            var label = original.get(ModDataComponents.CHEM_OUTPUT_LABEL.get());
            if (label == null) candidate.remove(ModDataComponents.CHEM_OUTPUT_LABEL.get()); else candidate.set(ModDataComponents.CHEM_OUTPUT_LABEL.get(), label);
            return true;
        });
    }

    /** Authoritative single-bottle whole-mixture packaging; the existing bottle is never spawned. */
    public synchronized PackagingResult outputToBottle(UUID expectedIdentity, long expectedStateRevision,
            long expectedInputRevision, long expectedBufferRevision, long expectedOutputRevision,
            ItemToken expectedInputToken, ItemToken expectedOutputToken, PackagingSource source,
            int doseUnits, String label) {
        if (!(level instanceof ServerLevel server) || !server.getServer().isSameThread() || !isLoadedValid()
                || expectedIdentity == null || !identity.equals(expectedIdentity) || expectedStateRevision != stateRevision
                || stateRevision == Long.MAX_VALUE || expectedBufferRevision != bufferRevision || source == null
                || source == PackagingSource.BUFFER && bufferRevision == Long.MAX_VALUE || input == null || output == null)
            return PackagingResult.STALE_STATE;
        var in = input.snapshot(); var out = output.snapshot();
        if (in.revision() != expectedInputRevision || out.revision() != expectedOutputRevision
                || !java.util.Objects.equals(in.token(), expectedInputToken) || !java.util.Objects.equals(out.token(), expectedOutputToken))
            return PackagingResult.STALE_STATE;
        if (out.token() == null || out.stack().isEmpty()) return PackagingResult.INVALID_OUTPUT;
        if (doseUnits <= 0 || label == null || label.length() > ChemMasterPackagingPlan.MAX_LABEL_LENGTH)
            return PackagingResult.INVALID_REQUEST;
        var profileId = com.juicyslew.moonstation14.ms14.chemistry.output.ModChemContainers.prototype(out.stack());
        var profile = com.juicyslew.moonstation14.ms14.chemistry.output.ModChemContainers.resolve(level, profileId);
        if (!acceptsResolvedOutputPolicy(prototype(), out.stack(), profile, profileId)
                || !out.stack().is(com.juicyslew.moonstation14.item.ModItems.BOTTLE)
                || profile.solution().isEmpty()) return PackagingResult.INVALID_OUTPUT;
        if (!(out.stack().getItem() instanceof IReagentTrait bottleTrait)) return PackagingResult.INVALID_OUTPUT;
        long capacity = com.juicyslew.moonstation14.ms14.reagent.ReagentUnits.fromFloat(
                IReagentTrait.resolveCapacity(bottleTrait, out.stack(), level));
        var bottleComponent = out.stack().get(com.juicyslew.moonstation14.component.ModDataComponents.REAGENT.get());
        var bottleMix = bottleComponent == null ? new ReagentAttachment() : bottleComponent.toAttachment();
        long current = bottleMix.totalUnits();
        if (current > capacity) return PackagingResult.INVALID_OUTPUT;
        var stagedBuffer = new ChemMasterBuffer(buffer.snapshot());
        ItemStack inputAfter = in.stack();
        java.util.Map<net.minecraft.resources.ResourceKey<com.juicyslew.moonstation14.component.codec.json.ReagentData>, Long> contents;
        if (source == PackagingSource.BUFFER) contents = stagedBuffer.quantities();
        else {
            if (in.token() == null || in.stack().isEmpty() || !(in.stack().getItem() instanceof IReagentTrait))
                return PackagingResult.MISSING_SOURCE;
            var reagent = in.stack().get(com.juicyslew.moonstation14.component.ModDataComponents.REAGENT.get());
            contents = reagent == null ? java.util.Map.of() : reagent.centContents();
        }
        ChemMasterPackagingPlan.Bottle plan;
        try { plan = ChemMasterPackagingPlan.bottle(contents, doseUnits, capacity - current, label); }
        catch (RuntimeException invalid) { return contents.isEmpty() ? PackagingResult.MISSING_SOURCE : PackagingResult.INSUFFICIENT_SOURCE; }
        if (source == PackagingSource.BUFFER) {
            stagedBuffer = new ChemMasterBuffer(new ChemMasterBuffer.Snapshot(
                    com.juicyslew.moonstation14.ms14.reagent.ReagentComponent.fromCents(plan.sourceRemainder()), buffer.snapshot().order()));
        } else {
            inputAfter = in.stack();
            var leftover = com.juicyslew.moonstation14.ms14.reagent.ReagentComponent.fromCents(plan.sourceRemainder());
            if (leftover.centContents().isEmpty()) inputAfter.remove(com.juicyslew.moonstation14.component.ModDataComponents.REAGENT.get());
            else inputAfter.set(com.juicyslew.moonstation14.component.ModDataComponents.REAGENT.get(), leftover);
        }
        var incoming = new ReagentAttachment(com.juicyslew.moonstation14.ms14.reagent.ReagentComponent.fromCents(plan.mixture()));
        long admitted = ReagentAttachment.transferUnits(incoming, bottleMix, plan.withdrawalCents(), capacity);
        if (admitted != plan.withdrawalCents() || !incoming.isEmpty()) return PackagingResult.INVALID_OUTPUT;
        if (profile.solution().orElseThrow().canReact())
            bottleMix.recursiveReaction(level, com.juicyslew.moonstation14.ms14.reagent.ReagentUnits.toFloat(capacity));
        ItemStack outputAfter = out.stack();
        if (bottleMix.isEmpty()) outputAfter.remove(com.juicyslew.moonstation14.component.ModDataComponents.REAGENT.get());
        else outputAfter.set(com.juicyslew.moonstation14.component.ModDataComponents.REAGENT.get(), bottleMix.toComponent());
        applyPackageLabel(outputAfter, label);
        if (!commitSolutions(expectedIdentity, expectedStateRevision, expectedInputRevision, expectedInputToken,
                source == PackagingSource.INPUT ? inputAfter : in.stack(),
                source == PackagingSource.BUFFER ? stagedBuffer.snapshot() : buffer.snapshot(),
                expectedBufferRevision, expectedOutputRevision, expectedOutputToken, outputAfter, java.util.Map.of()))
            return PackagingResult.STALE_STATE;
        if (profile.solution().orElseThrow().canReact())
            com.juicyslew.moonstation14.ms14.reagent.ReactionAudio.playCommitted(server, worldPosition, bottleMix);
        return PackagingResult.SUCCESS;
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
        CompoundTag outputState = pendingOutputSlot;
        if (invalidSavedState && outputState != null) tag.put("outputSlot", outputState.copy());
        else if (output != null) {
            var encoded = MachineItemSlot.codec().encodeStart(registries.createSerializationContext(NbtOps.INSTANCE), output.encode()).result();
            if (encoded.isPresent()) tag.put("outputSlot", encoded.get());
            else if (outputState != null) tag.put("outputSlot", outputState.copy());
        } else if (outputState != null) tag.put("outputSlot", outputState.copy());
        if (pendingBuffer != null) tag.put("buffer", pendingBuffer.copy());
        else tag.put("buffer", ChemMasterBuffer.SNAPSHOT_CODEC.encodeStart(registries.createSerializationContext(NbtOps.INSTANCE), buffer.snapshot()).getOrThrow());
        tag.putLong("bufferRevision", bufferRevision);
        tag.putLong("stateRevision", stateRevision); tag.putString("mode", mode.name()); tag.putString("sort", sort.name());
        tag.putString("drawSource", drawSource.name()); tag.putInt("pillType", pillType);
    }
    @Override protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (tag.hasUUID("placementIdentity")) identity = tag.getUUID("placementIdentity");
        if (tag.hasUUID("slotOwner")) slotOwner = tag.getUUID("slotOwner");
        bufferRevision = tag.getLong("bufferRevision");
        stateRevision = Math.max(0, tag.getLong("stateRevision"));
        mode = parseEnum(ChemMasterControlRequest.Mode.class, tag.getString("mode"), ChemMasterControlRequest.Mode.TRANSFER);
        sort = parseEnum(ChemMasterControlRequest.Sort.class, tag.getString("sort"), ChemMasterControlRequest.Sort.OLDEST_FIRST);
        drawSource = parseEnum(PackagingSource.class, tag.getString("drawSource"), PackagingSource.BUFFER);
        pillType = Math.max(0, Math.min(19, tag.getInt("pillType")));
        pendingSlot = tag.contains("inputSlot") ? tag.getCompound("inputSlot") : null;
        pendingOutputSlot = tag.contains("outputSlot") ? tag.getCompound("outputSlot") : null;
        pendingBuffer = tag.contains("buffer") ? tag.getCompound("buffer") : null;
        input = null; output = null; buffer = new ChemMasterBuffer(); invalidSavedState = false;
    }
    @Override public void onLoad() {
        super.onLoad();
        if (ensureState() && level instanceof ServerLevel server && isLoadedValid())
            com.juicyslew.moonstation14.ms14.power.runtime.PowerRuntime.chemMasterChanged(server, worldPosition);
    }
    @Override public void clearRemoved() {
        super.clearRemoved();
        if (input != null && !input.resume()) invalidSavedState = true;
        if (output != null && !output.resume()) invalidSavedState = true;
    }
    @Override public void setRemoved() {
        if (level instanceof ServerLevel server) com.juicyslew.moonstation14.ms14.power.runtime.PowerRuntime.chemMasterRemoved(server, worldPosition);
        if (input != null && !input.suspend()) invalidSavedState = true;
        if (output != null && !output.suspend()) invalidSavedState = true;
        super.setRemoved();
    }
    private static <E extends Enum<E>> E parseEnum(Class<E> type, String value, E fallback) {
        try { return Enum.valueOf(type, value); } catch (IllegalArgumentException ignored) { return fallback; }
    }
}

