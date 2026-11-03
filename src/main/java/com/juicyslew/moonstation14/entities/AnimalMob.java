package com.juicyslew.moonstation14.entities;

import com.juicyslew.moonstation14.ms14.character.CharacterVisualState;
import com.juicyslew.moonstation14.ms14.character.ModCharacters;
import com.juicyslew.moonstation14.ms14.character.components.AnimalAppearanceComponent;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.Level;
import com.juicyslew.moonstation14.ms14.player_body_control.character.NpcMovementDriven;

/** Shared living host for prototype-backed ordinary fauna; deliberately owns no AI/movement policy. */
public class AnimalMob extends Mob implements CharacterVisualState, NpcMovementDriven {
    private static final EntityDataAccessor<String> APPEARANCE = SynchedEntityData.defineId(AnimalMob.class, EntityDataSerializers.STRING);
    private static final EntityDataAccessor<Boolean> NPC_MOVEMENT_DRIVEN =
            SynchedEntityData.defineId(AnimalMob.class, EntityDataSerializers.BOOLEAN);
    private static final String TAG = "MoonStation14AnimalAppearance";
    private float resolvedEyeHeight = Float.NaN;
    private record Snapshot(String key) implements VisualSnapshot {
        @Override public void applyTo(net.minecraft.world.entity.LivingEntity detached) { ((AnimalMob) detached).setAppearanceKey(key); }
    }
    public AnimalMob(EntityType<? extends AnimalMob> type, Level level) { super(type, level); }
    @Override public void onAddedToLevel() {
        super.onAddedToLevel();
        refreshPresentationDimensions();
    }
    @Override public net.minecraft.world.entity.EntityDimensions getDefaultDimensions(net.minecraft.world.entity.Pose pose) {
        var anchor = resolvedAppearance();
        var dimensions = super.getDefaultDimensions(pose);
        return anchor == null || anchor.eyeHeight() == null ? dimensions
                : dimensions.withEyeHeight((float) com.juicyslew.moonstation14.ms14.animals.AnimalPresentationAnchors.eyeHeight(anchor, dimensions.eyeHeight()));
    }
    @Override protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(APPEARANCE, "");
        builder.define(NPC_MOVEMENT_DRIVEN, false);
    }
    @Override public void tick() {
        super.tick();
        refreshPresentationDimensions();
        if (!level().isClientSide) {
            ResourceLocation host = net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getKey(getType());
            AnimalAppearanceComponent appearance = resolvedAppearance();
            if (!appearance.modelIds().contains(appearanceKey()))
                setAppearanceKey(appearance.modelIds().get(getRandom().nextInt(appearance.modelIds().size())));
        }
    }
    private void refreshPresentationDimensions() {
        AnimalAppearanceComponent appearance = resolvedAppearance();
        float eye = appearance == null || appearance.eyeHeight() == null ? Float.NaN : appearance.eyeHeight().floatValue();
        if (Float.compare(eye, resolvedEyeHeight) != 0) { resolvedEyeHeight = eye; refreshDimensions(); }
    }
    private AnimalAppearanceComponent resolvedAppearance() {
        ResourceLocation host = net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getKey(getType());
        return ModCharacters.characterForHost(level(), host).map(id -> ModCharacters.require(ModCharacters.catalog(level()), id)
                .component(AnimalAppearanceComponent.class).orElse(null)).orElse(null);
    }
    public String appearanceKey() { return entityData.get(APPEARANCE); }
    public void setAppearanceKey(String key) { entityData.set(APPEARANCE, java.util.Objects.requireNonNull(key)); }
    @Override public boolean moonstation14$isNpcMovementDriven() { return entityData.get(NPC_MOVEMENT_DRIVEN); }
    @Override public void moonstation14$setNpcMovementDriven(boolean driven) {
        if (!level().isClientSide) entityData.set(NPC_MOVEMENT_DRIVEN, driven);
    }
    @Override public VisualSnapshot captureVisualState() { return new Snapshot(appearanceKey()); }
    @Override public void addAdditionalSaveData(CompoundTag tag) { super.addAdditionalSaveData(tag); if (!appearanceKey().isEmpty()) tag.putString(TAG, appearanceKey()); }
    @Override public void readAdditionalSaveData(CompoundTag tag) { super.readAdditionalSaveData(tag); if (tag.contains(TAG, CompoundTag.TAG_STRING)) setAppearanceKey(tag.getString(TAG)); }
    /** Station fauna are persistent world actors, not disposable wildlife that despawns by player distance. */
    @Override public boolean requiresCustomPersistence() { return true; }
    public static AttributeSupplier.Builder createAttributes() { return Mob.createMobAttributes().add(Attributes.MAX_HEALTH, 20.0); }
}
