package com.juicyslew.moonstation14.ms14.character;

import com.juicyslew.moonstation14.ms14.examine.prototype.ExamineMetadata;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.Mockito.*;

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

    @Test void boundNativeMobUsesItsResolvedCharacterNameRatherThanOwnersLedgerName() {
        var level = mock(net.minecraft.server.level.ServerLevel.class);
        var server = mock(net.minecraft.server.MinecraftServer.class);
        when(level.getServer()).thenReturn(server);
        when(server.overworld()).thenReturn(level);
        var mob = mock(net.minecraft.world.entity.Mob.class);
        when(mob.level()).thenReturn(level);
        when(mob.hasCustomName()).thenReturn(false);
        when(mob.getDisplayName()).thenReturn(Component.literal("native mob"));

        UUID account = UUID.randomUUID();
        var binding = new com.juicyslew.moonstation14.ms14.player_body_control.lifecycle.character.PlayerCharacterBinding(
                account, "default", UUID.randomUUID());
        var key = new com.juicyslew.moonstation14.ms14.chat.identity.ChatIdentityRegistry.CharacterKey(account, "default");
        var ledger = mock(com.juicyslew.moonstation14.ms14.chat.identity.ChatIdentitySavedData.class);
        when(ledger.character(key)).thenReturn(Optional.of(
                new com.juicyslew.moonstation14.ms14.chat.identity.ChatIdentityRegistry.Entry("Nadia Hart", 0xFFFFFF)));
        var kobold = new com.juicyslew.moonstation14.component.codec.json.CharacterData(
                List.of(), List.of(), new ExamineMetadata("Kobold", ""));

        try (var binder = mockStatic(com.juicyslew.moonstation14.ms14.player_body_control.lifecycle.character.PlayerCharacterHarnessBinder.class);
             var saved = mockStatic(com.juicyslew.moonstation14.ms14.chat.identity.ChatIdentitySavedData.class);
             var identity = mockStatic(CharacterIdentitySystem.class)) {
            binder.when(() -> com.juicyslew.moonstation14.ms14.player_body_control.lifecycle.character.PlayerCharacterHarnessBinder.binding(mob))
                    .thenReturn(binding);
            saved.when(() -> com.juicyslew.moonstation14.ms14.chat.identity.ChatIdentitySavedData.existing(level))
                    .thenReturn(Optional.of(ledger));
            identity.when(() -> CharacterIdentitySystem.resolveForActor(mob)).thenReturn(Optional.of(kobold));

            assertEquals("Kobold", CharacterPublicName.component(mob).getString());
            var plan = com.juicyslew.moonstation14.ms14.effect.EmoteEffect.plan("scream", true, mob);
            assertEquals("Kobold", ((Component) ((TranslatableContents) plan.chat().getContents()).getArgs()[0]).getString());

            var harness = mock(com.juicyslew.moonstation14.ms14.player_body_control.lifecycle.character.PlayerCharacterHarnessEntity.class);
            when(harness.level()).thenReturn(level);
            when(harness.hasCustomName()).thenReturn(false);
            when(harness.isAlive()).thenReturn(false);
            binder.when(() -> com.juicyslew.moonstation14.ms14.player_body_control.lifecycle.character.PlayerCharacterHarnessBinder.binding(harness))
                    .thenReturn(binding);
            assertEquals("Nadia Hart", CharacterPublicName.component(harness).getString());

            when(mob.hasCustomName()).thenReturn(true);
            when(mob.getCustomName()).thenReturn(Component.literal("Mittens"));
            assertEquals("Mittens", CharacterPublicName.component(mob).getString());
        }
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
