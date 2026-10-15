package com.juicyslew.moonstation14.ms14.botany;

import com.juicyslew.moonstation14.block.ModBlockEntities;
import com.juicyslew.moonstation14.ms14.botany.processing.GrinderProgram;
import com.juicyslew.moonstation14.ms14.botany.processing.GrindingPlan;
import com.juicyslew.moonstation14.ms14.hands.transfer.LevelItemLocationLedgers;
import com.juicyslew.moonstation14.ms14.machine.itemslots.MachineItemSlot;
import com.juicyslew.moonstation14.ms14.chemistry.DispenserCompatibleItem;
import com.juicyslew.moonstation14.component.ModDataComponents;
import com.juicyslew.moonstation14.ms14.reagent.ReagentAttachment;
import com.juicyslew.moonstation14.ms14.reagent.ReagentComponent;
import com.juicyslew.moonstation14.ms14.reagent.IReagentTrait;
import com.juicyslew.moonstation14.ms14.reagent.ReagentUnits;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Prototype-composed grinder state and shared-ledger-owned removable slots. */
public final class ReagentGrinderBlockEntity extends BlockEntity {
    private UUID identity = UUID.randomUUID(), slotOwner = UUID.randomUUID();
    private boolean initialized, invalid;
    private long stateRevision;
    private MachineItemSlot beaker;
    private final List<MachineItemSlot> chamber = new ArrayList<>();
    private final List<CompoundTag> pendingChamber = new ArrayList<>();
    private CompoundTag pendingBeaker, pendingProgram;
    private GrinderProgram program = new GrinderProgram();

    public ReagentGrinderBlockEntity(BlockPos pos, BlockState state) { super(ModBlockEntities.REAGENT_GRINDER.get(), pos, state); }
    public UUID placementIdentity() { return identity; }
    public MachineProfile resolvedProfile() {
        var catalog = ModBotanyMachineProfiles.serverCatalog();
        return catalog == null ? null : catalog.get(ResourceLocation.fromNamespaceAndPath("moonstation14", "grinder"));
    }
    private boolean ensureState() {
        if (!(level instanceof ServerLevel server) || isRemoved() || invalid) return false;
        MachineProfile profile = resolvedProfile();
        if (profile == null || profile.extractor() || profile.chamberCapacity() <= 0) return false;
        var chunk = server.getChunkSource().getChunkNow(worldPosition.getX() >> 4, worldPosition.getZ() >> 4);
        if (chunk == null || chunk.getBlockEntities().get(worldPosition) != this) return false;
        var ledger = LevelItemLocationLedgers.forLevel(server);
        if (beaker == null) beaker = new MachineItemSlot(slotOwner, "beakerSlot", this::acceptsBeaker, ledger);
        else if (!beaker.resume()) { invalid = true; return false; }
        if (pendingBeaker != null) {
            var data = MachineItemSlot.codec().parse(server.registryAccess().createSerializationContext(NbtOps.INSTANCE), pendingBeaker).result();
            if (data.isEmpty() || !beaker.restore(data.get())) { invalid = true; return false; }
            pendingBeaker = null;
        }
        if (chamber.isEmpty()) for (int i=0; i<profile.chamberCapacity(); i++)
            chamber.add(new MachineItemSlot(slotOwner, "chamber" + i, this::acceptsChamber, ledger));
        if (chamber.size() != profile.chamberCapacity() || pendingChamber.size() > chamber.size()) { invalid = true; return false; }
        for (MachineItemSlot slot : chamber) if (!slot.resume()) { invalid = true; return false; }
        for (int i=0; i<pendingChamber.size(); i++) {
            var data = MachineItemSlot.codec().parse(server.registryAccess().createSerializationContext(NbtOps.INSTANCE), pendingChamber.get(i)).result();
            if (data.isEmpty() || !chamber.get(i).restore(data.get())) { invalid = true; return false; }
        }
        pendingChamber.clear();
        if (pendingProgram != null) {
            var decoded = GrinderProgram.codec().parse(NbtOps.INSTANCE, pendingProgram).result();
            if (decoded.isEmpty()) { invalid = true; return false; }
            program = decoded.get(); pendingProgram = null;
        }
        if (!initialized) setChanged();
        initialized = true;
        return true;
    }
    private boolean acceptsBeaker(ItemStack stack) {
        if (stack.isEmpty() || !(level instanceof ServerLevel) || !(stack.getItem() instanceof DispenserCompatibleItem compatible)) return false;
        return compatible.fitsInDispenser(level);
    }
    private boolean acceptsChamber(ItemStack stack) {
        if (stack.isEmpty() || !(level instanceof ServerLevel)) return false;
        var catalog = ModProduceProfiles.serverCatalog();
        var produce = catalog == null ? null : catalog.get(net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem()));
        return produce != null && produce.grindable()
                || stack.getItem() instanceof DispenserCompatibleItem compatible && compatible.fitsInDispenser(level);
    }
    public boolean isLoadedValid() { return level instanceof ServerLevel server && !isRemoved() && server.getChunkSource().getChunkNow(worldPosition.getX() >> 4, worldPosition.getZ() >> 4) != null && ensureState(); }
    public MachineItemSlot beakerSlot() { return ensureState() ? beaker : null; }
    public List<MachineItemSlot> chamberSlots() { return ensureState() ? List.copyOf(chamber) : List.of(); }
    public GrinderProgram program() { return ensureState() ? program : null; }
    public synchronized long stateRevision() { return stateRevision; }
    /** Revision-bound snapshot used by trusted server callers; never accept a client-provided proof. */
    public record StartProof(UUID placement, long stateRevision, long beakerRevision,
                             List<Long> chamberRevisions) { }
    public synchronized StartProof startProof() {
        if (!ensureState()) return null;
        return new StartProof(identity, stateRevision, beaker.snapshot().revision(),
                chamber.stream().map(slot -> slot.snapshot().revision()).toList());
    }
    /** Demand for the machine's present state; active demand is not a startup prerequisite. */
    public double requiredPowerWatts() {
        MachineProfile profile = resolvedProfile();
        if (profile == null) return Double.POSITIVE_INFINITY;
        return profile.powered() ? (program.active() ? profile.activePowerWatts() : profile.powerWatts()) : 0d;
    }
    /** Zero-load/absent receivers are available; positive demand fails closed until metering exists. */
    public boolean isPowered() {
        if (!(level instanceof ServerLevel) || !isLoadedValid()) return false;
        MachineProfile profile = resolvedProfile();
        if (profile == null) return false;
        if (!profile.powered()) return true;
        return com.juicyslew.moonstation14.ms14.power.runtime.PowerRuntime.processingMeter((ServerLevel) level, this).known();
    }
    /** Domain-level trusted start. Player/UI admission remains the responsibility of its caller. */
    public synchronized boolean startTrusted(StartProof proof, long now, boolean callerValid) {
        if (!callerValid || proof == null || !ensureState() || !identity.equals(proof.placement())
                || proof.stateRevision() != stateRevision || program.active()
                || proof.beakerRevision() != beaker.snapshot().revision()
                || proof.chamberRevisions().size() != chamber.size()) return false;
        int count = 0;
        for (int i = 0; i < chamber.size(); i++) {
            var state = chamber.get(i).snapshot();
            if (state.revision() != proof.chamberRevisions().get(i)) return false;
            if (state.stack().isEmpty()) continue;
            var profiles = ModProduceProfiles.serverCatalog();
            var profile = profiles == null ? null : profiles.get(net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(state.stack().getItem()));
            if (profile == null || !profile.grindable()) return false;
            count++;
        }
        if (count == 0 || beaker.snapshot().stack().isEmpty()) return false;
        var profile = resolvedProfile();
        if (profile == null || !isPowered()) return false;
        if (!program.start(now, profile, true, count, true, true)) return false;
        playProcessingSound(profile.processingSound(), GrinderProcessingSoundPayload.Operation.START);
        stateRevision++;
        setChanged();
        com.juicyslew.moonstation14.ms14.power.runtime.PowerRuntime.processingMachineChanged((ServerLevel) level, worldPosition);
        return true;
    }
    /** Called only by successful grinder slot-transfer paths; denials never emit feedback. */
    public synchronized void slotTransferCommitted(boolean beakerSlot, boolean inserting) {
        var profile = resolvedProfile();
        playProfileSound(profile == null ? null : beakerSlot
                ? (inserting ? profile.beakerInsertSound() : profile.beakerEjectSound())
                : (inserting ? null : profile.chamberEjectSound()));
        slotChanged();
    }
    private void playProfileSound(MachineProfile.SoundCue cue) {
        if (cue == null || !(level instanceof ServerLevel server)) return;
        var event = net.minecraft.core.registries.BuiltInRegistries.SOUND_EVENT.getOptional(cue.sound()).orElse(null);
        if (event != null) {
            double x = worldPosition.getX() + .5, y = worldPosition.getY() + .5, z = worldPosition.getZ() + .5;
            var packet = new net.minecraft.network.protocol.game.ClientboundSoundPacket(
                    net.minecraft.core.registries.BuiltInRegistries.SOUND_EVENT.wrapAsHolder(event),
                    net.minecraft.sounds.SoundSource.BLOCKS, x, y, z, cue.volume(), cue.pitch(), server.getRandom().nextLong());
            for (var player : server.getServer().getPlayerList().getPlayers()) {
                double range = event.getRange(cue.volume());
                if (com.juicyslew.moonstation14.ms14.audio.BodyViewAudioAudience.canHear(server, player, x, y, z, range))
                    player.connection.send(packet);
            }
        }
    }
    public synchronized void slotChanged() { stateRevision++; setChanged(); if (level instanceof ServerLevel server) com.juicyslew.moonstation14.ms14.power.runtime.PowerRuntime.processingMachineChanged(server, worldPosition); }
    public static void tick(Level level, BlockPos pos, ReagentGrinderBlockEntity entity) {
        if (!(level instanceof ServerLevel) || !entity.isLoadedValid()) return;
        if (entity.program.due(level.getGameTime())) {
            entity.playProcessingSound(entity.resolvedProfile() == null ? null : entity.resolvedProfile().processingSound(), GrinderProcessingSoundPayload.Operation.STOP);
        }
        if (entity.program.finishIfDue(level.getGameTime())) {
            com.juicyslew.moonstation14.ms14.power.runtime.PowerRuntime.processingMachineChanged((ServerLevel) level, pos);
            entity.completeGrinding((ServerLevel) level);
        }
    }
    private synchronized void completeGrinding(ServerLevel server) {
        var target = beaker.snapshot();
        if (target.stack().isEmpty() || target.token() == null
                || !(target.stack().getItem() instanceof IReagentTrait trait)) { stateRevision++; setChanged(); return; }
        long capacity = ReagentUnits.fromFloat(trait.getCapacity(target.stack(), server));
        ReagentComponent targetData = target.stack().getOrDefault(ModDataComponents.REAGENT.get(), new ReagentComponent());
        ReagentAttachment initial = new ReagentAttachment(targetData);
        List<MachineItemSlot.Snapshot> snapshots = chamber.stream().map(MachineItemSlot::snapshot).toList();
        List<GrindingPlan.Source> sources = new ArrayList<>();
        for (MachineItemSlot.Snapshot snapshot : snapshots) {
            var stack = snapshot.stack();
            ReagentComponent data = stack.isEmpty() ? new ReagentComponent() : stack.get(ModDataComponents.REAGENT.get());
            if (data == null && !stack.isEmpty() && stack.get(ModDataComponents.PRODUCE_INSTANCE.get()) == null) {
                var profile = ModProduceProfiles.serverCatalog().get(net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem()));
                var source = profile == null ? null : profile.sourceSolution();
                if (source != null) {
                    var contents = new java.util.LinkedHashMap<net.minecraft.resources.ResourceKey<com.juicyslew.moonstation14.component.codec.json.ReagentData>, Long>();
                    source.contentsCents().forEach((id, amount) -> contents.put(net.minecraft.resources.ResourceKey.create(com.juicyslew.moonstation14.ms14.reagent.ModReagents.REAGENT_REGISTRY_KEY, id), amount));
                    data = ReagentComponent.fromCents(contents);
                }
            }
            sources.add(new GrindingPlan.Source(new ReagentAttachment(data == null ? new ReagentComponent() : data), !stack.isEmpty()));
        }
        var plan = GrindingPlan.prepare(sources, initial, capacity, staged -> staged.recursiveReaction(server, ReagentUnits.toFloat(capacity)));
        if (plan.consumedSourceIndexes().isEmpty()) { stateRevision++; setChanged(); return; }
        ItemStack targetReplacement = target.stack();
        targetReplacement.set(ModDataComponents.REAGENT.get(), plan.beaker().toComponent());
        List<MachineItemSlot.SlotChange> changes = new ArrayList<>();
        changes.add(new MachineItemSlot.SlotChange(beaker, target.revision(), target.token(), target.stack(), targetReplacement));
        for (int index : plan.consumedSourceIndexes()) {
            var source = snapshots.get(index);
            if (source.token() == null) { stateRevision++; setChanged(); return; }
            changes.add(new MachineItemSlot.SlotChange(chamber.get(index), source.revision(), source.token(), source.stack(), ItemStack.EMPTY));
        }
        var ledger = LevelItemLocationLedgers.forLevel(server);
        MachineItemSlot.commitBatch(ledger.snapshot().revision(), changes);
        stateRevision++; setChanged();
    }
    @Override public void onLoad() { super.onLoad(); ensureState(); if (level instanceof ServerLevel server) com.juicyslew.moonstation14.ms14.power.runtime.PowerRuntime.processingMachineChanged(server, worldPosition); }
    @Override public void setRemoved() {
        if (program.active() && level instanceof ServerLevel) playProcessingSound(resolvedProfile() == null ? null : resolvedProfile().processingSound(), GrinderProcessingSoundPayload.Operation.STOP);
        if (level instanceof ServerLevel server) com.juicyslew.moonstation14.ms14.power.runtime.PowerRuntime.processingMachineRemoved(server, worldPosition);
        if (beaker != null && !beaker.suspend()) invalid = true;
        for (MachineItemSlot slot : chamber) if (!slot.suspend()) invalid = true;
        super.setRemoved();
    }
    private void playProcessingSound(MachineProfile.SoundCue cue, GrinderProcessingSoundPayload.Operation operation) {
        if(cue==null || !(level instanceof ServerLevel server) || !program.active()) return;
        var payload=new GrinderProcessingSoundPayload(server.dimension().location(),worldPosition,identity,program.startTick(),program.endTick(),cue.sound(),cue.volume(),cue.pitch(),operation);
        GrinderProcessingAudioNetworking.broadcast(server,worldPosition,payload);
    }
    @Override public void clearRemoved() {
        super.clearRemoved();
        if (beaker != null && !beaker.resume()) invalid = true;
        for (MachineItemSlot slot : chamber) if (!slot.resume()) invalid = true;
    }
    @Override protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries); tag.putUUID("PlacementIdentity", identity); tag.putUUID("SlotOwner", slotOwner); tag.putBoolean("ProfileInitialized", initialized); tag.putLong("StateRevision", stateRevision);
        if (beaker != null) MachineItemSlot.codec().encodeStart(registries.createSerializationContext(NbtOps.INSTANCE), beaker.encode()).result().ifPresent(value -> tag.put("BeakerSlot", value));
        var slots = new net.minecraft.nbt.ListTag();
        for (MachineItemSlot slot : chamber) MachineItemSlot.codec().encodeStart(registries.createSerializationContext(NbtOps.INSTANCE), slot.encode()).result().ifPresent(slots::add);
        tag.put("ChamberSlots", slots);
        if (program.active()) GrinderProgram.codec().encodeStart(NbtOps.INSTANCE, program).result().ifPresent(value -> tag.put("Program", value));
    }
    @Override protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (tag.hasUUID("PlacementIdentity")) identity = tag.getUUID("PlacementIdentity");
        if (tag.hasUUID("SlotOwner")) slotOwner = tag.getUUID("SlotOwner");
        initialized = tag.getBoolean("ProfileInitialized"); stateRevision = Math.max(0, tag.getLong("StateRevision"));
        pendingBeaker = tag.contains("BeakerSlot") ? tag.getCompound("BeakerSlot") : null;
        pendingChamber.clear(); var slots = tag.getList("ChamberSlots", 10); for (int i=0;i<slots.size();i++) pendingChamber.add(slots.getCompound(i));
        pendingProgram = tag.contains("Program") ? tag.getCompound("Program") : null;
    }
}
