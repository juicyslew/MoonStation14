package com.juicyslew.moonstation14.block.block_entity;

import com.juicyslew.moonstation14.block.ModBlockEntities;
import com.juicyslew.moonstation14.ms14.singularity.containment.FieldGeneratorProfile;
import com.juicyslew.moonstation14.ms14.singularity.containment.FieldGeneratorState;
import com.juicyslew.moonstation14.ms14.singularity.containment.ModFieldGenerators;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import java.util.UUID;
import com.juicyslew.moonstation14.ms14.singularity.containment.FieldContainmentRuntime;
import com.juicyslew.moonstation14.block.custom.FieldGeneratorBlock;

/** Live persistence/tick adapter for the resolved field-generator domain state. */
public final class FieldGeneratorBlockEntity extends BlockEntity {
    private static final String PROFILE="Profile", CHARGE="Charge", ELAPSED="Elapsed", ENABLED="Enabled", ID="Identity";
    private ResourceLocation profileId = ModFieldGenerators.CONTAINMENT_ID;
    private FieldGeneratorState state = new FieldGeneratorState(0, 0, true, false);
    private UUID identity = UUID.randomUUID();

    public FieldGeneratorBlockEntity(BlockPos pos, BlockState blockState) { super(ModBlockEntities.FIELD_GENERATOR.get(), pos, blockState); }

    public void receiveEmitterBolt() {
        if (level == null || level.isClientSide) return;
        FieldGeneratorProfile profile = profile();
        if (profile == null) return;
        state = state.receiveValidBolt(profile);
        setChanged();
        if(level instanceof ServerLevel server) FieldContainmentRuntime.changed(server,worldPosition);
    }
    public boolean toggleEnabled() { return toggleEnabled(level instanceof ServerLevel server && FieldContainmentRuntime.hasConnection(server,this)); }
    public boolean toggleEnabled(boolean hasConnection) {
        if (level == null || level.isClientSide) return false;
        FieldGeneratorProfile profile = profile();
        if (profile == null) return false;
        FieldGeneratorState next = state.setEnabled(!state.enabled(), hasConnection);
        if (next.equals(state)) return false;
        state = next;
        setChanged();
        if(level instanceof ServerLevel server) FieldContainmentRuntime.changed(server,worldPosition);
        return true;
    }
    public net.minecraft.network.chat.Component toggleFeedback(boolean changed) {
        FieldGeneratorProfile p=profile();
        int max=p==null?state.charge():p.maxCharge();
        if(!changed && state.enabled()) return net.minecraft.network.chat.Component.translatable("message.moonstation14.field_generator.connected_cannot_disable",state.charge(),max);
        return net.minecraft.network.chat.Component.translatable(state.enabled()
                ? "message.moonstation14.field_generator.enabled" : "message.moonstation14.field_generator.disabled",
                state.charge(),max,level instanceof ServerLevel server && FieldContainmentRuntime.hasConnection(server,this));
    }
    private FieldGeneratorProfile profile() { return level == null ? null : ModFieldGenerators.catalog(level).get(profileId); }
    public FieldGeneratorState snapshot() { return state; }
    public UUID placementIdentity() { return identity; }
    public ResourceLocation dimensionId() { return level == null ? null : level.dimension().location(); }
    public ResourceLocation profileId() { return profileId; }
    public boolean connectionEligible() { FieldGeneratorProfile p=profile(); return p != null && state.anchored() && state.enabled() && state.charge() >= p.connectionThreshold(); }
    public static void tick(ServerLevel level, FieldGeneratorBlockEntity entity) {
        if (entity.isRemoved() || entity.level != level) return;
        FieldGeneratorProfile p=entity.profile(); if (p == null) return;
        FieldGeneratorState updated=entity.state.tick(p, 1);
        if (!updated.equals(entity.state)) { entity.state=updated; entity.setChanged(); }
        entity.projectVisual(level,p);
    }
    private void projectVisual(ServerLevel server, FieldGeneratorProfile profile) {
        int power = state.charge() <= 0 ? 0 : state.charge() >= profile.maxCharge() ? 3
                : state.charge() < profile.connectionThreshold() ? 1 : 2;
        long connections = FieldContainmentRuntime.currentEdges(server).stream()
                .filter(e -> e.first().equals(worldPosition) || e.second().equals(worldPosition)).count();
        int field = connections > 1 ? 3 : connections == 1 ? 2 : state.enabled() ? 1 : 0;
        boolean active = connections > 0;
        BlockState old=getBlockState();
        if (old.getValue(FieldGeneratorBlock.POWER)!=power || old.getValue(FieldGeneratorBlock.FIELD)!=field || old.getValue(FieldGeneratorBlock.ACTIVE)!=active)
            server.setBlock(worldPosition,old.setValue(FieldGeneratorBlock.POWER,power).setValue(FieldGeneratorBlock.FIELD,field).setValue(FieldGeneratorBlock.ACTIVE,active),2);
    }
    @Override public void onLoad() {
        super.onLoad();
        FieldGeneratorProfile p=profile();
        if(p!=null) state=new FieldGeneratorState(Math.min(state.charge(),p.maxCharge()),state.decayElapsedTicks()%p.decayIntervalTicks(),true,state.enabled());
        if(level instanceof ServerLevel server) FieldContainmentRuntime.changed(server,worldPosition);
    }
    @Override public void setRemoved() {
        if(level instanceof ServerLevel server) FieldContainmentRuntime.removed(server,worldPosition);
        super.setRemoved();
    }
    @Override protected void saveAdditional(CompoundTag tag, net.minecraft.core.HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries); tag.putString(PROFILE, profileId.toString()); tag.putInt(CHARGE,state.charge());
        tag.putInt(ELAPSED,state.decayElapsedTicks()); tag.putBoolean(ENABLED,state.enabled()); tag.putUUID(ID,identity);
    }
    @Override protected void loadAdditional(CompoundTag tag, net.minecraft.core.HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (tag.contains(PROFILE)) { ResourceLocation candidate=ResourceLocation.tryParse(tag.getString(PROFILE)); if(candidate!=null) profileId=candidate; }
        if(tag.hasUUID(ID)) identity=tag.getUUID(ID);
        FieldGeneratorProfile p=profile();
        int charge=Math.max(0,tag.getInt(CHARGE)), elapsed=Math.max(0,tag.getInt(ELAPSED));
        if(p!=null) { charge=Math.min(charge,p.maxCharge()); elapsed%=p.decayIntervalTicks(); }
        state=new FieldGeneratorState(charge,elapsed,true,tag.getBoolean(ENABLED));
    }
}

