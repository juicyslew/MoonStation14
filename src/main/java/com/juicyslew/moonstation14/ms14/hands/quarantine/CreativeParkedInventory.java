package com.juicyslew.moonstation14.ms14.hands.quarantine;

import com.juicyslew.moonstation14.component.ModDataAttachments;
import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.UUIDUtil;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.common.util.FakePlayer;

import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/** Server-only account-owned parked carrier; presence, even when all slots are empty, is significant. */
public final class CreativeParkedInventory {
    private static final Codec<CreativeParkedInventory> FIELDS_CODEC = RecordCodecBuilder.create(instance -> instance.group(
            UUIDUtil.CODEC.fieldOf("account").forGetter(CreativeParkedInventory::account),
            CreativeInventorySnapshot.CODEC.fieldOf("inventory").forGetter(CreativeParkedInventory::snapshot)
    ).apply(instance, CreativeParkedInventory::new));
    /** Reject unknown outer NBT fields; the inner snapshot enforces its own schema. */
    public static final Codec<CreativeParkedInventory> CODEC = new Codec<>() {
        @Override public <T> DataResult<T> encode(CreativeParkedInventory value, DynamicOps<T> ops, T prefix) {
            return FIELDS_CODEC.encode(value, ops, prefix);
        }

        @Override public <T> DataResult<Pair<CreativeParkedInventory, T>> decode(DynamicOps<T> ops, T input) {
            if (!(ops.convertTo(NbtOps.INSTANCE, input) instanceof CompoundTag tag)
                    || !tag.getAllKeys().equals(Set.of("account", "inventory")))
                return DataResult.error(() -> "Park must contain exactly account and inventory");
            return FIELDS_CODEC.decode(ops, input);
        }
    };

    private final UUID account;
    private final CreativeInventorySnapshot snapshot;

    public CreativeParkedInventory(UUID account, CreativeInventorySnapshot snapshot) {
        this.account = Objects.requireNonNull(account, "account");
        this.snapshot = Objects.requireNonNull(snapshot, "snapshot");
    }

    public UUID account() { return account; }
    public CreativeInventorySnapshot snapshot() { return snapshot; }

    /** Existing account park on the exact connected player only; not a mode-transition authorization. */
    public static Optional<CreativeParkedInventory> existingFor(ServerPlayer player) {
        if (player == null || player instanceof FakePlayer || !(player.level() instanceof ServerLevel level)
                || level.isClientSide || player.isRemoved() || !player.isAlive()) return Optional.empty();
        var server = level.getServer();
        if (server == null || !server.isSameThread()
                || server.getPlayerList().getPlayer(player.getUUID()) != player
                || level.getEntity(player.getUUID()) != player) return Optional.empty();
        CreativeParkedInventory existing = player.getExistingDataOrNull(ModDataAttachments.CREATIVE_PARKED_INVENTORY.get());
        return existing != null && existing.account().equals(player.getUUID())
                ? Optional.of(existing) : Optional.empty();
    }
}
