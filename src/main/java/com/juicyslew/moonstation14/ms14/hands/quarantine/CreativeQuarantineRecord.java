package com.juicyslew.moonstation14.ms14.hands.quarantine;

import net.minecraft.core.RegistryAccess;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.Tag;

import java.io.DataOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Inert, account- and store-bound journal envelope. Phase names are persisted claims/intent,
 * NOT evidence that playerdata was saved, cleared, or restored. Every phase requires external
 * recovery on reopen. This codec does not grant Creative access or body ownership.
 * QuarantineState is a separate pure protocol model, not proof of any physical operation.
 */
public final class CreativeQuarantineRecord {
    public static final int SCHEMA_VERSION = 1;
    public static final int MAX_BYTES = CreativeInventorySnapshot.MAX_BYTES + 4096;

    public enum Phase { PREPARED, CLEAR_INTENT, PARKED, RESTORE_INTENT, RESTORE_VERIFIED }

    private static final Set<String> FIELDS = Set.of("schema", "world", "account", "revision",
            "transition", "generation", "phase", "snapshot");

    private final UUID world;
    private final UUID account;
    private final long revision;
    private final UUID transition;
    private final long generation;
    private final Phase phase;
    private final CompoundTag snapshotData;

    public CreativeQuarantineRecord(UUID world, UUID account, long revision, UUID transition,
                                    long generation, Phase phase, CreativeInventorySnapshot snapshot,
                                    RegistryAccess registries) {
        this(world, account, revision, transition, generation, phase,
                Objects.requireNonNull(snapshot, "snapshot").encode(registries));
        // Enforce the envelope bound even for newly constructed records.
        encode();
    }

    private CreativeQuarantineRecord(UUID world, UUID account, long revision, UUID transition,
                                     long generation, Phase phase, CompoundTag snapshotData) {
        this.world = Objects.requireNonNull(world, "world");
        this.account = Objects.requireNonNull(account, "account");
        this.transition = Objects.requireNonNull(transition, "transition");
        this.phase = Objects.requireNonNull(phase, "phase");
        if (revision < 0 || generation < 0) throw new IllegalArgumentException("Negative journal counter");
        this.revision = revision;
        this.generation = generation;
        this.snapshotData = snapshotData.copy();
    }

    public UUID world() { return world; }
    public UUID account() { return account; }
    public long revision() { return revision; }
    public UUID transition() { return transition; }
    public long sessionGeneration() { return generation; }
    public Phase phase() { return phase; }

    /** Detached snapshot; no mutable item stack or NBT escapes the record. */
    public CreativeInventorySnapshot snapshot(RegistryAccess registries) {
        return CreativeInventorySnapshot.decode(snapshotData.copy(), registries);
    }

    public CompoundTag encode() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("schema", SCHEMA_VERSION);
        tag.putUUID("world", world);
        tag.putUUID("account", account);
        tag.putLong("revision", revision);
        tag.putUUID("transition", transition);
        tag.putLong("generation", generation);
        tag.putString("phase", phase.name());
        tag.put("snapshot", snapshotData.copy());
        checkSize(tag);
        return tag;
    }

    /**
     * Decode only against caller-held identity and ordering expectations. previousRevision is
     * -1 for the first record; subsequent revisions must strictly increase. No sequence of
     * journal records alone can prove a physical clear/restore or authorize access.
     */
    public static CreativeQuarantineRecord decode(CompoundTag tag, RegistryAccess registries,
                                                   UUID expectedWorld, UUID expectedAccount,
                                                   long previousRevision, UUID expectedTransition,
                                                   long expectedGeneration) {
        Objects.requireNonNull(tag, "tag");
        Objects.requireNonNull(registries, "registries");
        Objects.requireNonNull(expectedWorld, "expectedWorld");
        Objects.requireNonNull(expectedAccount, "expectedAccount");
        Objects.requireNonNull(expectedTransition, "expectedTransition");
        if (previousRevision < -1 || expectedGeneration < 0)
            throw new IllegalArgumentException("Invalid ordering expectation");
        checkSize(tag);
        if (!tag.getAllKeys().equals(FIELDS) || !tag.contains("schema", Tag.TAG_INT)
                || tag.getInt("schema") != SCHEMA_VERSION
                || !uuidField(tag, "world") || !uuidField(tag, "account")
                || !uuidField(tag, "transition")
                || !tag.contains("revision", Tag.TAG_LONG)
                || !tag.contains("generation", Tag.TAG_LONG)
                || !tag.contains("phase", Tag.TAG_STRING)
                || !tag.contains("snapshot", Tag.TAG_COMPOUND))
            throw new IllegalArgumentException("Invalid journal schema");
        Phase phase;
        try {
            phase = Phase.valueOf(tag.getString("phase"));
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("Unknown journal phase", exception);
        }
        long revision = tag.getLong("revision");
        long generation = tag.getLong("generation");
        if (revision < 0 || revision <= previousRevision || generation != expectedGeneration
                || !tag.getUUID("world").equals(expectedWorld)
                || !tag.getUUID("account").equals(expectedAccount)
                || !tag.getUUID("transition").equals(expectedTransition))
            throw new IllegalArgumentException("Journal identity or ordering mismatch");
        // Strict native, registry-aware validation, including slot count and item components.
        CreativeInventorySnapshot snapshot = CreativeInventorySnapshot.decode(tag.getCompound("snapshot"), registries);
        return new CreativeQuarantineRecord(expectedWorld, expectedAccount, revision,
                expectedTransition, generation, phase, snapshot.encode(registries));
    }

    private static boolean uuidField(CompoundTag tag, String name) {
        return tag.contains(name, Tag.TAG_INT_ARRAY) && tag.getIntArray(name).length == 4;
    }

    private static void checkSize(CompoundTag tag) {
        try {
            OutputStream bounded = new OutputStream() {
                private int count;
                @Override public void write(int value) throws IOException {
                    if (++count > MAX_BYTES) throw new IOException("Journal exceeds byte limit");
                }
                @Override public void write(byte[] bytes, int offset, int length) throws IOException {
                    if (length > MAX_BYTES - count) throw new IOException("Journal exceeds byte limit");
                    count += length;
                }
            };
            NbtIo.write(tag, new DataOutputStream(bounded));
        } catch (IOException exception) {
            throw new IllegalArgumentException("Invalid or oversized journal NBT", exception);
        }
    }
}
