package com.juicyslew.moonstation14.ms14.hands.quarantine;

import net.minecraft.core.RegistryAccess;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.Tag;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.OverlappingFileLockException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Inert single-account journal. A phase is a persisted claim, never evidence of playerdata operations.
 * Staged files are forced and the primary is decoded on readback, but a successful readback does NOT
 * establish sudden-power-loss durability: Java has no portable directory fsync after atomic rename
 * (in particular, opening a directory as a FileChannel for force is not supported on Windows).
 * This journal is not a durable quarantine proof and must not enable Creative inventory activation.
 */
public final class CreativeQuarantineStore {
    private final Path primary, backup, temporary, backupTemporary, lockPath;
    private final UUID world, account;
    private final RegistryAccess registries;
    private final FileOperations files;

    /**
     * Only server code holding the actual world root may call this factory. Neither the root nor the
     * UUIDs are authenticated here; never construct them from client claims. This does not provide
     * protection against concurrent filesystem replacement (symlink races).
     */
    public static CreativeQuarantineStore open(Path worldRoot, UUID worldUuid, UUID accountUuid,
                                               RegistryAccess registries) throws IOException {
        Objects.requireNonNull(worldUuid, "worldUuid");
        Objects.requireNonNull(accountUuid, "accountUuid");
        Path suppliedRoot = Objects.requireNonNull(worldRoot, "worldRoot");
        for (Path component : suppliedRoot)
            if (component.toString().equals("..")) throw recovery("world root contains traversal");
        Path root = suppliedRoot.toAbsolutePath().normalize();
        checkDirectoryChain(root);
        Path directory = root.resolve("moonstation14-creative-quarantine");
        if (!Files.exists(directory, LinkOption.NOFOLLOW_LINKS)) {
            try {
                Files.createDirectory(directory);
            } catch (java.nio.file.FileAlreadyExistsException ignored) {
                // A concurrent creator is acceptable only if the resulting entry passes validation.
            }
        }
        checkDirectoryChain(directory);
        Path file = directory.resolve(accountUuid + ".nbt");
        CreativeQuarantineStore store = new CreativeQuarantineStore(file, worldUuid, accountUuid, registries);
        // Check all known names before returning, including an existing journal belonging to another identity.
        store.read();
        return store;
    }

    /** Internal/test-only raw path; production callers must use open with the actual server world root. */
    CreativeQuarantineStore(Path validatedWorldSpecificPath, UUID world, UUID account,
                                   RegistryAccess registries) {
        this(validatedWorldSpecificPath, world, account, registries, NioFiles.INSTANCE);
    }

    CreativeQuarantineStore(Path validatedWorldSpecificPath, UUID world, UUID account,
                            RegistryAccess registries, FileOperations files) {
        primary = Objects.requireNonNull(validatedWorldSpecificPath, "validatedWorldSpecificPath")
                .toAbsolutePath().normalize();
        if (primary.getFileName() == null || primary.getParent() == null)
            throw new IllegalArgumentException("journal path must name a file in a world directory");
        this.world = Objects.requireNonNull(world, "world");
        this.account = Objects.requireNonNull(account, "account");
        this.registries = Objects.requireNonNull(registries, "registries");
        this.files = Objects.requireNonNull(files, "files");
        backup = primary.resolveSibling(primary.getFileName() + ".bak");
        temporary = primary.resolveSibling(primary.getFileName() + ".tmp");
        backupTemporary = primary.resolveSibling(primary.getFileName() + ".bak.tmp");
        lockPath = primary.resolveSibling(primary.getFileName() + ".lock");
    }

    public synchronized Optional<CreativeQuarantineRecord> read() throws IOException {
        try (Locked ignored = lock()) {
            return readLocked();
        }
    }

    /** -1 initializes only an absent store. A new transition requires a separate future flow. */
    public synchronized CreativeQuarantineRecord compareAndSwap(long expectedRevision,
                                                                  CreativeQuarantineRecord next) throws IOException {
        Objects.requireNonNull(next, "next");
        if (expectedRevision < -1 || expectedRevision == Long.MAX_VALUE)
            throw new IllegalArgumentException("invalid CAS revision");
        try (Locked ignored = lock()) {
            Optional<CreativeQuarantineRecord> prior = readLocked();
            long actual = prior.map(CreativeQuarantineRecord::revision).orElse(-1L);
            if (actual != expectedRevision) throw new RevisionConflictException(expectedRevision, actual);
            if (!world.equals(next.world()) || !account.equals(next.account())
                    || next.revision() != expectedRevision + 1)
                throw new IllegalArgumentException("journal identity or next revision mismatch");
            if (prior.isPresent()) {
                CreativeQuarantineRecord old = prior.get();
                if (!old.transition().equals(next.transition())
                        || old.sessionGeneration() != next.sessionGeneration()
                        || next.phase().ordinal() != old.phase().ordinal() + 1)
                    throw new IllegalArgumentException("journal transition, generation or phase step mismatch");
                // An inventory cannot be replaced during a phase progression.
                if (!old.snapshot(registries).encode(registries).equals(next.snapshot(registries).encode(registries)))
                    throw new IllegalArgumentException("journal snapshot changed");
            } else if (next.phase() != CreativeQuarantineRecord.Phase.PREPARED) {
                throw new IllegalArgumentException("first journal phase must be PREPARED");
            }
            byte[] encoded = encode(next);
            files.writeNew(temporary, encoded);
            files.force(temporary);
            if (prior.isPresent()) {
                files.writeNew(backupTemporary, files.readBounded(primary));
                files.force(backupTemporary);
                files.atomicMove(backupTemporary, backup, true);
            }
            files.atomicMove(temporary, primary, prior.isPresent());
            // A receipt is only returned after reopening the actual primary, including strict decode.
            CreativeQuarantineRecord verified = readLocked().orElseThrow(() -> recovery("primary vanished on readback"));
            if (!verified.encode().equals(next.encode()))
                throw recovery("readback differs from staged record");
            return verified;
        }
    }

    private Optional<CreativeQuarantineRecord> readLocked() throws IOException {
        checkDirectory();
        if (present(temporary) || present(backupTemporary)) throw recovery("orphan temporary evidence");
        if (!present(primary)) {
            if (present(backup)) throw recovery("primary missing with backup present");
            return Optional.empty();
        }
        CreativeQuarantineRecord current = readValid(primary);
        boolean hasBackup = present(backup);
        if (current.revision() == 0 && current.phase() == CreativeQuarantineRecord.Phase.PREPARED) {
            if (hasBackup) throw recovery("initial journal has unexpected backup");
        } else if (!hasBackup) {
            throw recovery("advanced journal missing backup");
        }
        if (hasBackup) {
            CreativeQuarantineRecord previous = readValid(backup);
            if (!previous.transition().equals(current.transition())
                    || previous.sessionGeneration() != current.sessionGeneration()
                    || previous.revision() != current.revision() - 1
                    || previous.phase().ordinal() + 1 != current.phase().ordinal()
                    || !previous.snapshot(registries).encode(registries)
                            .equals(current.snapshot(registries).encode(registries)))
                throw recovery("backup conflicts with primary");
        }
        return Optional.of(current);
    }

    private CreativeQuarantineRecord readValid(Path path) throws IOException {
        try {
            byte[] bytes = files.readBounded(path);
            CompoundTag tag;
            try (DataInputStream input = new DataInputStream(new ByteArrayInputStream(bytes))) {
                tag = NbtIo.read(input, NbtAccounter.create(CreativeQuarantineRecord.MAX_BYTES));
                if (input.available() != 0) throw recovery("trailing journal bytes");
            }
            if (tag == null || !tag.contains("transition", Tag.TAG_INT_ARRAY)
                    || tag.getIntArray("transition").length != 4
                    || !tag.contains("generation", Tag.TAG_LONG))
                throw recovery("invalid journal header");
            CreativeQuarantineRecord record = CreativeQuarantineRecord.decode(tag, registries,
                    world, account, -1, tag.getUUID("transition"), tag.getLong("generation"));
            if (!tag.equals(record.encode())) throw recovery("noncanonical journal");
            return record;
        } catch (IllegalArgumentException | IllegalStateException e) {
            throw new IOException("creative journal recovery required: invalid data at " + path, e);
        }
    }

    private static byte[] encode(CreativeQuarantineRecord record) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        NbtIo.write(record.encode(), new DataOutputStream(bytes));
        if (bytes.size() > CreativeQuarantineRecord.MAX_BYTES) throw recovery("oversized journal");
        return bytes.toByteArray();
    }

    private void checkDirectory() throws IOException {
        checkDirectoryChain(primary.getParent());
    }

    private static void checkDirectoryChain(Path directory) throws IOException {
        for (Path part = directory; part != null; part = part.getParent()) {
            BasicFileAttributes attributes = Files.readAttributes(part, BasicFileAttributes.class,
                    LinkOption.NOFOLLOW_LINKS);
            if (!attributes.isDirectory() || attributes.isSymbolicLink())
                throw recovery("non-directory or link in world directory path: " + part);
        }
    }

    private static boolean present(Path path) throws IOException {
        if (!Files.exists(path, LinkOption.NOFOLLOW_LINKS)) return false;
        if (!Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS))
            throw recovery("unsupported file type at " + path);
        return true;
    }

    private Locked lock() throws IOException {
        checkDirectory();
        present(lockPath);
        FileChannel channel = FileChannel.open(lockPath, StandardOpenOption.CREATE, StandardOpenOption.WRITE,
                LinkOption.NOFOLLOW_LINKS);
        try {
            FileLock lock = channel.tryLock();
            if (lock == null) throw recovery("journal writer busy");
            return new Locked(lock, channel);
        } catch (IOException | RuntimeException failure) {
            channel.close();
            if (failure instanceof OverlappingFileLockException)
                throw recovery("journal writer busy");
            throw failure;
        }
    }

    private record Locked(FileLock lock, FileChannel channel) implements AutoCloseable {
        public void close() throws IOException {
            try { lock.close(); } finally { channel.close(); }
        }
    }

    private static IOException recovery(String reason) { return new IOException("creative journal recovery required: " + reason); }

    interface FileOperations {
        byte[] readBounded(Path path) throws IOException;
        void writeNew(Path path, byte[] bytes) throws IOException;
        void force(Path path) throws IOException;
        void atomicMove(Path from, Path to, boolean replace) throws IOException;
    }

    private enum NioFiles implements FileOperations {
        INSTANCE;
        public byte[] readBounded(Path path) throws IOException {
            if (!present(path)) throw recovery("missing journal file: " + path);
            try (FileChannel channel = FileChannel.open(path, StandardOpenOption.READ, LinkOption.NOFOLLOW_LINKS)) {
                long size = channel.size();
                if (size > CreativeQuarantineRecord.MAX_BYTES || size < 0) throw recovery("oversized journal");
                byte[] bytes = new byte[(int) size];
                ByteBuffer buffer = ByteBuffer.wrap(bytes);
                while (buffer.hasRemaining()) {
                    if (channel.read(buffer) < 0) throw recovery("truncated journal");
                }
                if (channel.read(ByteBuffer.allocate(1)) != -1) throw recovery("journal grew during read");
                return bytes;
            }
        }
        public void writeNew(Path path, byte[] bytes) throws IOException {
            try (FileChannel channel = FileChannel.open(path, StandardOpenOption.CREATE_NEW,
                    StandardOpenOption.WRITE, LinkOption.NOFOLLOW_LINKS)) {
                ByteBuffer buffer = ByteBuffer.wrap(bytes);
                while (buffer.hasRemaining()) channel.write(buffer);
            }
        }
        public void force(Path path) throws IOException {
            if (!present(path)) throw recovery("missing staged file");
            try (FileChannel channel = FileChannel.open(path, StandardOpenOption.WRITE, LinkOption.NOFOLLOW_LINKS)) {
                channel.force(true);
            }
        }
        public void atomicMove(Path from, Path to, boolean replace) throws IOException {
            if (!present(from)) throw recovery("missing staged file");
            present(to);
            try {
                if (replace) Files.move(from, to, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
                else Files.move(from, to, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException e) {
                throw new IOException("atomic journal rename unsupported", e);
            }
        }
    }

    public static final class RevisionConflictException extends IOException {
        public RevisionConflictException(long expected, long actual) {
            super("creative journal revision conflict: expected " + expected + ", found " + actual);
        }
    }
}
