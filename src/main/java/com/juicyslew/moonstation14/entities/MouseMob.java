package com.juicyslew.moonstation14.entities;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.Level;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.nbt.CompoundTag;
import com.juicyslew.moonstation14.ms14.character.ModCharacters;
import com.juicyslew.moonstation14.ms14.character.components.MouseAppearanceComponent;

/** Player-controllable mouse Mob; no human inventory, profile, or voluntary AI. */
public final class MouseMob extends Mob implements com.juicyslew.moonstation14.ms14.character.CharacterVisualState {
    private record AppearanceSnapshot(com.juicyslew.moonstation14.ms14.character.components.MouseAppearanceComponent.Variant appearance)
            implements com.juicyslew.moonstation14.ms14.character.CharacterVisualState.VisualSnapshot {
        public void applyTo(net.minecraft.world.entity.LivingEntity detached) {
            ((MouseMob) detached).setAppearance(appearance);
        }
    }
    private static final EntityDataAccessor<Integer> APPEARANCE = SynchedEntityData.defineId(MouseMob.class, EntityDataSerializers.INT);
    private static final String APPEARANCE_TAG = "MoonStation14MouseAppearance";
    public MouseMob(EntityType<? extends MouseMob> type, Level level) {
        super(type, level);
        setNoAi(true);
    }

    @Override protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(APPEARANCE, -1);
    }

    @Override public void tick() {
        super.tick();
        if (!level().isClientSide && entityData.get(APPEARANCE) < 0) {
            var catalog = ModCharacters.catalog(level());
            var characterId = ModCharacters.characterForHost(catalog,
                    net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getKey(getType())).orElse(null);
            var character = characterId == null ? null : catalog.get(characterId);
            var allowed = character == null ? null : character.component(MouseAppearanceComponent.class).orElse(null);
            var variants = allowed == null ? MouseAppearanceComponent.DEFAULT_VARIANTS : allowed.variants();
            entityData.set(APPEARANCE, MouseAppearanceComponent.select(variants,
                    getRandom().nextInt(variants.size())).ordinal());
        }
    }

    public MouseAppearanceComponent.Variant appearance() {
        int value = entityData.get(APPEARANCE);
        var variants = MouseAppearanceComponent.Variant.values();
        return value < 0 || value >= variants.length ? MouseAppearanceComponent.DEFAULT_VARIANT : variants[value];
    }

    public void setAppearance(MouseAppearanceComponent.Variant appearance) {
        entityData.set(APPEARANCE, java.util.Objects.requireNonNull(appearance).ordinal());
    }

    @Override public com.juicyslew.moonstation14.ms14.character.CharacterVisualState.VisualSnapshot captureVisualState() { return new AppearanceSnapshot(appearance()); }

    @Override public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        if (entityData.get(APPEARANCE) >= 0) tag.putInt(APPEARANCE_TAG, entityData.get(APPEARANCE));
    }

    @Override public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        if (tag.contains(APPEARANCE_TAG, CompoundTag.TAG_INT)) {
            int value = tag.getInt(APPEARANCE_TAG);
            if (value >= 0 && value < MouseAppearanceComponent.Variant.values().length) entityData.set(APPEARANCE, value);
        }
    }

    /** A controlled or unowned station mouse is not disposable wildlife that despawns by player distance. */
    @Override
    public boolean requiresCustomPersistence() {
        return true;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes().add(Attributes.MAX_HEALTH, 20.0);
    }
}
