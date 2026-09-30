package com.juicyslew.moonstation14.ms14.hands.quarantine;

import net.minecraft.core.RegistryAccess;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.world.item.ItemStack;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/** Fixture is isolated from the live world; all file writes are test setup, never probe behavior. */
public final class VanillaPlayerDataDiskProbeGameTests {
    private static final UUID ACCOUNT = UUID.fromString("00000000-0000-0000-0000-000000000042");

    private VanillaPlayerDataDiskProbeGameTests() { }

    public static void absencePrimaryAndBackup(GameTestHelper helper) throws IOException {
        Path root = Files.createTempDirectory("ms14-disk-probe-");
        Path dir = root.resolve("playerdata");
        Path primary = dir.resolve(ACCOUNT + ".dat");
        Path backup = dir.resolve(ACCOUNT + ".dat_old");
        try {
            RegistryAccess registries = helper.getLevel().registryAccess();
            require(probe(root, registries).status() == VanillaPlayerDataDiskProbe.Status.MISSING);
            Files.createDirectory(dir);
            require(probe(root, registries).status() == VanillaPlayerDataDiskProbe.Status.MISSING);
            byte[] a = player(8);
            Files.write(primary, a);
            require(probe(root, registries).status() == VanillaPlayerDataDiskProbe.Status.PRIMARY);
            require(probe(root, registries).primaryView().isPresent());
            require(Arrays.equals(a, Files.readAllBytes(primary)));
            byte[] b = player(7);
            Files.write(backup, b);
            requirePrimarySelection(probe(root, registries), 8);
            require(Arrays.equals(a, Files.readAllBytes(primary)) && Arrays.equals(b, Files.readAllBytes(backup)));
            // Different bytes outside inventory likewise represent ordinary successive saves.
            CompoundTag differentMetadata = base(8);
            differentMetadata.putString("fixtureNote", "previous save");
            byte[] metadataOld = compress(differentMetadata);
            require(!Arrays.equals(a, metadataOld));
            Files.write(backup, metadataOld);
            requirePrimarySelection(probe(root, registries), 8);
            require(Arrays.equals(a, Files.readAllBytes(primary)) && Arrays.equals(metadataOld, Files.readAllBytes(backup)));
            Files.write(backup, new byte[] {31, -117, 8, 0});
            require(probe(root, registries).status() == VanillaPlayerDataDiskProbe.Status.INVALID);
            require(probe(root, registries).primaryView().isEmpty());
            require(Arrays.equals(a, Files.readAllBytes(primary))
                    && Arrays.equals(new byte[] {31, -117, 8, 0}, Files.readAllBytes(backup)));
            Files.write(backup, a);
            requirePrimarySelection(probe(root, registries), 8);
            CompoundTag wrongAccount = base(8);
            wrongAccount.putUUID("UUID", UUID.fromString("00000000-0000-0000-0000-000000000043"));
            byte[] mismatched = compress(wrongAccount);
            Files.write(primary, mismatched);
            require(probe(root, registries).status() == VanillaPlayerDataDiskProbe.Status.DAT_OLD);
            require(probe(root, registries).primaryView().isEmpty());
            require(Arrays.equals(mismatched, Files.readAllBytes(primary)));
            Files.write(primary, new byte[] {31, -117, 8, 0});
            require(probe(root, registries).status() == VanillaPlayerDataDiskProbe.Status.DAT_OLD);
            require(probe(root, registries).primaryView().isEmpty());
            require(Arrays.equals(a, Files.readAllBytes(backup)));
            Files.delete(primary);
            require(probe(root, registries).status() == VanillaPlayerDataDiskProbe.Status.DAT_OLD);
            require(probe(root, registries).primaryView().isEmpty());
            helper.succeed();
        } finally {
            Files.deleteIfExists(primary);
            Files.deleteIfExists(backup);
            Files.deleteIfExists(dir);
            Files.delete(root);
        }
    }

    public static void unsafePathsAndSizeBounds(GameTestHelper helper) throws IOException {
        Path root = Files.createTempDirectory("ms14-disk-probe-");
        Path dir = root.resolve("playerdata");
        Path primary = dir.resolve(ACCOUNT + ".dat");
        Path backup = dir.resolve(ACCOUNT + ".dat_old");
        Path target = root.resolve("target.dat");
        try {
            RegistryAccess registries = helper.getLevel().registryAccess();
            Files.write(dir, new byte[] {1});
            require(probe(root, registries).status() == VanillaPlayerDataDiskProbe.Status.INVALID);
            Files.delete(dir);
            Files.createDirectory(dir);
            byte[] valid = player(8);
            Files.write(target, valid);
            try {
                Files.createSymbolicLink(primary, target);
                require(probe(root, registries).status() == VanillaPlayerDataDiskProbe.Status.INVALID);
                Files.delete(primary);
                Files.createSymbolicLink(backup, target);
                Files.write(primary, valid);
                require(probe(root, registries).status() == VanillaPlayerDataDiskProbe.Status.INVALID);
                Files.delete(primary);
                Files.delete(backup);
            } catch (UnsupportedOperationException | java.nio.file.FileSystemException | SecurityException unsupported) {
                // Symlink creation may require elevated rights on Windows. Nonregular paths below are mandatory.
                Files.deleteIfExists(primary);
                Files.deleteIfExists(backup);
            }
            Files.createDirectory(primary);
            require(probe(root, registries).status() == VanillaPlayerDataDiskProbe.Status.INVALID);
            Files.delete(primary);
            byte[] tooLarge = new byte[VanillaPlayerDataDiskProbe.MAX_COMPRESSED_BYTES + 1];
            Files.write(primary, tooLarge);
            require(probe(root, registries).status() == VanillaPlayerDataDiskProbe.Status.INVALID);
            require(Arrays.equals(tooLarge, Files.readAllBytes(primary)));
            Files.delete(primary);
            CompoundTag tag = base(8);
            tag.putByteArray("bomb", new byte[VanillaPlayerDataDiskProbe.MAX_NBT_BYTES + 1]);
            byte[] bomb = compress(tag);
            require(bomb.length < VanillaPlayerDataDiskProbe.MAX_COMPRESSED_BYTES);
            Files.write(primary, bomb);
            Files.write(backup, valid);
            require(probe(root, registries).status() == VanillaPlayerDataDiskProbe.Status.DAT_OLD);
            require(Arrays.equals(bomb, Files.readAllBytes(primary)) && Arrays.equals(valid, Files.readAllBytes(backup)));
            helper.succeed();
        } finally {
            Files.deleteIfExists(primary);
            Files.deleteIfExists(backup);
            Files.deleteIfExists(target);
            Files.deleteIfExists(dir);
            Files.delete(root);
        }
    }

    private static VanillaPlayerDataDiskProbe.Result probe(Path root, RegistryAccess registries) {
        return VanillaPlayerDataDiskProbe.inspect(root, ACCOUNT, registries);
    }

    private static void requirePrimarySelection(VanillaPlayerDataDiskProbe.Result result, int selected) {
        require(result.status() == VanillaPlayerDataDiskProbe.Status.PRIMARY);
        VanillaPlayerDataInventoryView view = result.primaryView().orElseThrow();
        require(view.compareSupported(emptySnapshot(selected)) == VanillaPlayerDataInventoryView.SupportedComparison.MATCH);
        require(view.compareSupported(emptySnapshot(selected == 8 ? 7 : 8))
                == VanillaPlayerDataInventoryView.SupportedComparison.MISMATCH);
    }

    private static CreativeInventorySnapshot emptySnapshot(int selected) {
        return CreativeInventorySnapshot.capture(Collections.nCopies(36, ItemStack.EMPTY),
                Collections.nCopies(4, ItemStack.EMPTY), List.of(ItemStack.EMPTY),
                ItemStack.EMPTY, selected, List.of());
    }

    private static CompoundTag base(int selected) {
        CompoundTag tag = new CompoundTag();
        // Normal pinned vanilla player saves include UUID; the account is also bound by the filename.
        tag.putUUID("UUID", ACCOUNT);
        tag.put("Inventory", new ListTag());
        tag.put("EnderItems", new ListTag());
        tag.putInt("SelectedItemSlot", selected);
        return tag;
    }

    private static byte[] player(int selected) throws IOException { return compress(base(selected)); }

    private static byte[] compress(CompoundTag tag) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        NbtIo.writeCompressed(tag, output);
        return output.toByteArray();
    }

    private static void require(boolean condition) {
        if (!condition) throw new GameTestAssertException("disk probe did not fail closed");
    }
}
