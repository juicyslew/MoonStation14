package com.juicyslew.moonstation14.ms14.character;

import com.juicyslew.moonstation14.ms14.examine.prototype.ExamineMetadata;
import net.minecraft.network.chat.Component;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

class CharacterPublicNameTest {
    @Test void resolvedHumanAndMouseMetadataProducesReadableNamesWithoutLocalizationKeyLookup() {
        assertEquals("Human", CharacterPublicName.fromMetadata(new ExamineMetadata("Human", ""),
                Component.translatable("entity.minecraft.villager")).getString());
        assertEquals("Mouse", CharacterPublicName.fromMetadata(new ExamineMetadata("Mouse", ""),
                Component.translatable("entity.moonstation14.mouse")).getString());
    }

    @Test void missingMetadataPreservesNativeComponentAndCustomNamesRemainNative() {
        Component nativeFallback = Component.translatable("entity.moonstation14.mouse");
        assertSame(nativeFallback, CharacterPublicName.fromMetadata(ExamineMetadata.EMPTY, nativeFallback));
        Component custom = Component.literal("Mittens");
        assertSame(custom, CharacterPublicName.component(new CustomNameEntity(custom)));
    }

    private static final class CustomNameEntity extends net.minecraft.world.entity.Entity {
        private CustomNameEntity(Component customName) {
            super(net.minecraft.world.entity.EntityType.PIG, null);
            setCustomName(customName);
        }
        @Override protected void defineSynchedData(net.minecraft.network.syncher.SynchedEntityData.Builder builder) { }
        @Override protected void readAdditionalSaveData(net.minecraft.nbt.CompoundTag tag) { }
        @Override protected void addAdditionalSaveData(net.minecraft.nbt.CompoundTag tag) { }
    }
}
