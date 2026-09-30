package com.juicyslew.moonstation14.ms14.hands.quarantine;

import net.minecraft.core.RegistryAccess;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.zip.GZIPInputStream;

/**
 * Read-only snapshot of the two vanilla playerdata names, not proof of a completed save or
 * permission to restore inventory. The root and account MUST come from the dedicated server.
 * Filesystem changes concurrent with the probe cannot be made atomic by this API; callers must
 * fail closed if they cannot exclude concurrent saves or hostile path replacement.
 */
public final class VanillaPlayerDataDiskProbe {
    public static final int MAX_COMPRESSED_BYTES = 1_048_576;
    public static final int MAX_NBT_BYTES = CreativeInventorySnapshot.MAX_BYTES;

    public enum Status { PRIMARY, DAT_OLD, MISSING, AMBIGUOUS, INVALID }

    /** Only PRIMARY supplies a view. DAT_OLD always requires explicit recovery, never fallback permission. */
    public record Result(Status status, Optional<VanillaPlayerDataInventoryView> primaryView) {
        public Result {
            Objects.requireNonNull(status, "status");
            Objects.requireNonNull(primaryView, "primaryView");
            if (primaryView.isPresent() != (status == Status.PRIMARY))
                throw new IllegalArgumentException("only primary can expose inventory");
        }
    }

    private VanillaPlayerDataDiskProbe() { }

    public static Result inspect(Path worldRoot, UUID account, RegistryAccess registries) {
        Objects.requireNonNull(worldRoot, "trusted world root");
        Objects.requireNonNull(account, "trusted account");
        Objects.requireNonNull(registries, "server registries");
        // Reject traversal rather than normalizing it away before validating each ancestor.
        for (Path part : worldRoot)
            if (part.toString().equals("..")) return result(Status.INVALID);
        Path root = worldRoot.toAbsolutePath().normalize();
        Path directory = root.resolve("playerdata");
        try {
            for (Path part = root; part != null; part = part.getParent()) {
                if (!directory(part)) return result(Status.INVALID);
            }
            if (!Files.exists(directory, LinkOption.NOFOLLOW_LINKS)) return result(Status.MISSING);
            if (!directory(directory)) return result(Status.INVALID);
            Path primary = directory.resolve(account + ".dat");
            Path old = directory.resolve(account + ".dat_old");
            boolean hasPrimary = Files.exists(primary, LinkOption.NOFOLLOW_LINKS);
            boolean hasOld = Files.exists(old, LinkOption.NOFOLLOW_LINKS);
            if (!hasPrimary && !hasOld) return result(Status.MISSING);
            // Validate both entries even if the primary is unusable; never follow a link.
            if (hasPrimary && !regular(primary) || hasOld && !regular(old)) return result(Status.INVALID);
            byte[] primaryBytes = hasPrimary ? readIfBounded(primary) : null;
            byte[] oldBytes = hasOld ? readIfBounded(old) : null;
            VanillaPlayerDataInventoryView primaryView = primaryBytes == null ? null : decode(primaryBytes, registries, account);
            VanillaPlayerDataInventoryView oldView = oldBytes == null ? null : decode(oldBytes, registries, account);
            // PlayerDataStorage.save retains the preceding .dat as .dat_old. Different valid
            // saves are normal, not evidence of simultaneous active ownership. An existing
            // unreadable old entry still fails closed even when the primary is valid.
            if (primaryView != null && (!hasOld || oldView != null))
                return new Result(Status.PRIMARY, Optional.of(primaryView));
            if (primaryView != null) return result(Status.INVALID);
            if (oldView != null) return result(Status.DAT_OLD);
            return result(Status.INVALID);
        } catch (IOException | RuntimeException failure) {
            // Includes malformed NBT, bounded accounter failures, and unreadable paths.
            return result(Status.INVALID);
        }
    }

    private static Result result(Status status) { return new Result(status, Optional.empty()); }

    private static boolean directory(Path path) throws IOException {
        BasicFileAttributes attributes = Files.readAttributes(path, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
        return attributes.isDirectory() && !attributes.isSymbolicLink();
    }

    private static boolean regular(Path path) throws IOException {
        BasicFileAttributes attributes = Files.readAttributes(path, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
        return attributes.isRegularFile() && !attributes.isSymbolicLink();
    }

    private static byte[] readIfBounded(Path path) {
        try { return readBounded(path); }
        catch (IOException | RuntimeException failure) { return null; }
    }

    private static byte[] readBounded(Path path) throws IOException {
        try (FileChannel channel = FileChannel.open(path, StandardOpenOption.READ, LinkOption.NOFOLLOW_LINKS)) {
            long size = channel.size();
            if (size < 1 || size > MAX_COMPRESSED_BYTES) throw new IOException("invalid compressed size");
            byte[] bytes = new byte[(int) size];
            ByteBuffer buffer = ByteBuffer.wrap(bytes);
            while (buffer.hasRemaining()) {
                if (channel.read(buffer) < 0) throw new IOException("truncated playerdata");
            }
            if (channel.read(ByteBuffer.allocate(1)) != -1) throw new IOException("playerdata grew");
            return bytes;
        }
    }

    private static VanillaPlayerDataInventoryView decode(byte[] bytes, RegistryAccess registries, UUID account) {
        try (DataInputStream input = new DataInputStream(new GZIPInputStream(new ByteArrayInputStream(bytes)))) {
            // Pinned 1.21.1 NbtAccounter charges arrays/lists before their allocation. Keep
            // a strict quota and depth; the view also validates the serialized tag size.
            CompoundTag tag = NbtIo.read(input, new NbtAccounter(MAX_NBT_BYTES, 64));
            if (input.read() != -1) return null;
            return VanillaPlayerDataInventoryView.inspect(tag, registries, account);
        } catch (IOException | RuntimeException failure) {
            return null;
        }
    }
}
