package com.juicyslew.moonstation14.ms14.hands.quarantine;

import net.minecraft.core.RegistryAccess;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;

import java.io.IOException;
import java.lang.reflect.Modifier;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

public final class CreativeQuarantineStoreGameTests {
    private static final UUID WORLD = new UUID(1, 2);
    private static final UUID ACCOUNT = new UUID(3, 4);
    private static final UUID TRANSITION = new UUID(5, 6);

    private static CreativeInventorySnapshot snapshot() {
        return CreativeInventorySnapshot.capture(Collections.nCopies(36, ItemStack.EMPTY),
                Collections.nCopies(4, ItemStack.EMPTY), List.of(ItemStack.EMPTY), ItemStack.EMPTY, 0, List.of());
    }

    private static CreativeQuarantineRecord record(RegistryAccess registries, long revision,
                                                    CreativeQuarantineRecord.Phase phase) {
        return new CreativeQuarantineRecord(WORLD, ACCOUNT, revision, TRANSITION, 8, phase, snapshot(), registries);
    }

    private static Path path() throws IOException {
        Path base = Path.of("build", "gametest-creative-journal-store-cases");
        Files.createDirectories(base);
        return Files.createTempDirectory(base, "journal-").resolve("record.nbt");
    }

    private static CreativeQuarantineStore store(Path path, RegistryAccess registries) {
        return new CreativeQuarantineStore(path, WORLD, ACCOUNT, registries);
    }

    private static void reject(Throwing action) {
        try { action.run(); } catch (IOException | IllegalArgumentException expected) { return; }
        throw new AssertionError("unsafe journal operation accepted");
    }

    @FunctionalInterface private interface Throwing { void run() throws IOException; }

    public static void boundPathsAndUnsafeRoots(GameTestHelper helper) throws IOException {
        var registries = helper.getLevel().registryAccess();
        Path root = path().getParent();
        UUID otherAccount = new UUID(11, 12);
        Path directory = root.resolve("moonstation14-creative-quarantine");
        Path firstFile = directory.resolve(ACCOUNT + ".nbt");
        Path otherFile = directory.resolve(otherAccount + ".nbt");
        var first = CreativeQuarantineStore.open(root, WORLD, ACCOUNT, registries);
        var other = CreativeQuarantineStore.open(root, WORLD, otherAccount, registries);
        first.compareAndSwap(-1, record(registries, 0, CreativeQuarantineRecord.Phase.PREPARED));
        if (!Files.isRegularFile(firstFile) || Files.exists(otherFile) || other.read().isPresent())
            throw new AssertionError("account paths not isolated");
        other.compareAndSwap(-1, new CreativeQuarantineRecord(WORLD, otherAccount, 0, TRANSITION, 8,
                CreativeQuarantineRecord.Phase.PREPARED, snapshot(), registries));
        if (!Files.isRegularFile(otherFile) || first.read().orElseThrow().account().equals(otherAccount))
            throw new AssertionError("second account overwrote first");
        reject(() -> CreativeQuarantineStore.open(root, new UUID(13, 14), ACCOUNT, registries));
        reject(() -> CreativeQuarantineStore.open(root.resolve("missing"), WORLD, ACCOUNT, registries));
        reject(() -> CreativeQuarantineStore.open(root.resolve("..").resolve(root.getFileName()),
                WORLD, ACCOUNT, registries));

        Path fileRoot = path();
        Files.write(fileRoot, new byte[] { 1 });
        reject(() -> CreativeQuarantineStore.open(fileRoot, WORLD, ACCOUNT, registries));
        Path badChildRoot = path().getParent();
        Files.write(badChildRoot.resolve("moonstation14-creative-quarantine"), new byte[] { 1 });
        reject(() -> CreativeQuarantineStore.open(badChildRoot, WORLD, ACCOUNT, registries));
        Path linkRoot = path().getParent();
        Path link = linkRoot.resolve("moonstation14-creative-quarantine");
        if (createLinkIfSupported(link, directory))
            reject(() -> CreativeQuarantineStore.open(linkRoot, WORLD, ACCOUNT, registries));
        Path ancestorLink = path().getParent().resolve("linked-root");
        if (createLinkIfSupported(ancestorLink, root))
            reject(() -> CreativeQuarantineStore.open(ancestorLink, WORLD, ACCOUNT, registries));
        for (var constructor : CreativeQuarantineStore.class.getDeclaredConstructors())
            if (Modifier.isPublic(constructor.getModifiers())) throw new AssertionError("raw constructor public");
        helper.succeed();
    }

    private static boolean createLinkIfSupported(Path link, Path target) throws IOException {
        try {
            Files.createSymbolicLink(link, target);
            return true;
        } catch (UnsupportedOperationException | java.nio.file.FileSystemException | SecurityException unsupported) {
            // Windows may require developer mode or symlink privileges.
            return false;
        }
    }

    public static void casPhasesAndIdentity(GameTestHelper helper) throws IOException {
        var registries = helper.getLevel().registryAccess();
        Path path = path();
        var first = store(path, registries);
        if (first.read().isPresent()) throw new AssertionError("not empty");
        reject(() -> first.compareAndSwap(-1, record(registries, 0, CreativeQuarantineRecord.Phase.PARKED)));
        first.compareAndSwap(-1, record(registries, 0, CreativeQuarantineRecord.Phase.PREPARED));
        reject(() -> first.compareAndSwap(-1, record(registries, 0, CreativeQuarantineRecord.Phase.PREPARED)));
        reject(() -> first.compareAndSwap(0, record(registries, 1, CreativeQuarantineRecord.Phase.PARKED)));
        reject(() -> first.compareAndSwap(0, record(registries, 1, CreativeQuarantineRecord.Phase.PREPARED)));
        reject(() -> first.compareAndSwap(0, new CreativeQuarantineRecord(WORLD, new UUID(9, 9), 1,
                TRANSITION, 8, CreativeQuarantineRecord.Phase.CLEAR_INTENT, snapshot(), registries)));
        reject(() -> first.compareAndSwap(0, new CreativeQuarantineRecord(WORLD, ACCOUNT, 1,
                new UUID(9, 9), 8, CreativeQuarantineRecord.Phase.CLEAR_INTENT, snapshot(), registries)));
        for (int i = 1; i < CreativeQuarantineRecord.Phase.values().length; i++) {
            var phase = CreativeQuarantineRecord.Phase.values()[i];
            first.compareAndSwap(i - 1, record(registries, i, phase));
            if (store(path, registries).read().orElseThrow().phase() != phase)
                throw new AssertionError("restart lost phase");
        }
        reject(() -> first.compareAndSwap(4, record(registries, 5, CreativeQuarantineRecord.Phase.PREPARED)));
        reject(() -> new CreativeQuarantineStore(path, new UUID(10, 10), ACCOUNT, registries).read());
        reject(() -> new CreativeQuarantineStore(path, WORLD, new UUID(10, 10), registries).read());
        helper.succeed();
    }

    public static void invalidEvidenceAndWriterLock(GameTestHelper helper) throws IOException {
        var registries = helper.getLevel().registryAccess();
        Path path = path();
        var store = store(path, registries);
        store.compareAndSwap(-1, record(registries, 0, CreativeQuarantineRecord.Phase.PREPARED));
        store.compareAndSwap(0, record(registries, 1, CreativeQuarantineRecord.Phase.CLEAR_INTENT));
        Path backup = path.resolveSibling("record.nbt.bak");
        Files.delete(backup);
        reject(() -> store(path, registries).read()); // valid revision 1 without backup is not complete evidence
        reject(() -> store.compareAndSwap(1, record(registries, 2, CreativeQuarantineRecord.Phase.PARKED)));
        Files.write(backup, new byte[] { 1, 2, 3 });
        reject(() -> store(path, registries).read());
        reject(() -> store.compareAndSwap(1, record(registries, 2, CreativeQuarantineRecord.Phase.PARKED)));
        Files.copy(path, backup, StandardCopyOption.REPLACE_EXISTING);
        reject(() -> store(path, registries).read()); // same revision is not the previous revision
        Files.delete(backup);
        Files.write(path.resolveSibling("record.nbt.tmp"), new byte[] { 1 });
        reject(() -> store(path, registries).read());
        Files.delete(path.resolveSibling("record.nbt.tmp"));
        Files.move(path, backup);
        reject(() -> store(path, registries).read()); // never adopt backup as owner
        reject(() -> store.compareAndSwap(-1, record(registries, 0, CreativeQuarantineRecord.Phase.PREPARED)));

        Path corrupt = path();
        Files.write(corrupt, new byte[] { 1, 2, 3 });
        reject(() -> store(corrupt, registries).read());
        reject(() -> store(corrupt, registries).compareAndSwap(-1,
                record(registries, 0, CreativeQuarantineRecord.Phase.PREPARED)));
        Path oversized = path();
        Files.write(oversized, new byte[CreativeQuarantineRecord.MAX_BYTES + 1]);
        reject(() -> store(oversized, registries).read());
        Path orphanBackupStage = path();
        Files.write(orphanBackupStage.resolveSibling("record.nbt.bak.tmp"), new byte[] { 1 });
        reject(() -> store(orphanBackupStage, registries).read());

        Path initial = path();
        var initialStore = store(initial, registries);
        initialStore.compareAndSwap(-1, record(registries, 0, CreativeQuarantineRecord.Phase.PREPARED));
        Path initialBackup = initial.resolveSibling("record.nbt.bak");
        Files.copy(initial, initialBackup);
        reject(() -> initialStore.read()); // revision 0 must never have a backup
        reject(() -> initialStore.compareAndSwap(0, record(registries, 1, CreativeQuarantineRecord.Phase.CLEAR_INTENT)));

        Path rolledBack = path();
        var rollbackStore = store(rolledBack, registries);
        rollbackStore.compareAndSwap(-1, record(registries, 0, CreativeQuarantineRecord.Phase.PREPARED));
        rollbackStore.compareAndSwap(0, record(registries, 1, CreativeQuarantineRecord.Phase.CLEAR_INTENT));
        Path earlierPrimary = rolledBack.resolveSibling("earlier-primary.nbt");
        Files.copy(rolledBack, earlierPrimary);
        rollbackStore.compareAndSwap(1, record(registries, 2, CreativeQuarantineRecord.Phase.PARKED));
        Files.copy(earlierPrimary, rolledBack, StandardCopyOption.REPLACE_EXISTING);
        Files.delete(rolledBack.resolveSibling("record.nbt.bak"));
        reject(() -> store(rolledBack, registries).read()); // lone, valid earlier primary cannot pass
        reject(() -> rollbackStore.compareAndSwap(1, record(registries, 2, CreativeQuarantineRecord.Phase.PARKED)));

        Path other = path();
        var locked = store(other, registries);
        Path lock = other.resolveSibling("record.nbt.lock");
        try (FileChannel channel = FileChannel.open(lock, StandardOpenOption.CREATE, StandardOpenOption.WRITE);
             var hold = channel.lock()) {
            reject(() -> locked.compareAndSwap(-1, record(registries, 0, CreativeQuarantineRecord.Phase.PREPARED)));
        }
        helper.succeed();
    }

    public static void injectedFaultsFailClosedAcrossRestart(GameTestHelper helper) throws IOException {
        var registries = helper.getLevel().registryAccess();
        for (String fault : List.of("stage", "flush", "backup", "replace", "readback")) {
            Path path = path();
            var normal = store(path, registries);
            normal.compareAndSwap(-1, record(registries, 0, CreativeQuarantineRecord.Phase.PREPARED));
            var failing = new CreativeQuarantineStore(path, WORLD, ACCOUNT, registries, new Fault(fault));
            reject(() -> failing.compareAndSwap(0, record(registries, 1, CreativeQuarantineRecord.Phase.CLEAR_INTENT)));
            if (fault.equals("readback")) {
                if (normal.read().orElseThrow().revision() != 1) throw new AssertionError("readback committed");
            } else {
                reject(() -> store(path, registries).read()); // retained temp or mismatched backup
                if (!Files.exists(path)) throw new AssertionError("primary lost");
            }
        }
        helper.succeed();
    }

    private static final class Fault implements CreativeQuarantineStore.FileOperations {
        private final String fault;
        private int primaryReads;
        Fault(String fault) { this.fault = fault; }
        public byte[] readBounded(Path path) throws IOException {
            if (fault.equals("readback") && path.getFileName().toString().equals("record.nbt")
                    && ++primaryReads == 3) throw new IOException("injected readback");
            byte[] bytes = Files.readAllBytes(path);
            if (bytes.length > CreativeQuarantineRecord.MAX_BYTES) throw new IOException("oversized");
            return bytes;
        }
        public void writeNew(Path path, byte[] bytes) throws IOException {
            if (fault.equals("stage") && path.getFileName().toString().equals("record.nbt.tmp")) {
                Files.write(path, new byte[] { 1 }, StandardOpenOption.CREATE_NEW);
                throw new IOException("injected partial stage");
            }
            if (fault.equals("backup") && path.getFileName().toString().endsWith(".bak.tmp")) {
                Files.write(path, new byte[] { 1 }, StandardOpenOption.CREATE_NEW);
                throw new IOException("injected backup stage");
            }
            Files.write(path, bytes, StandardOpenOption.CREATE_NEW);
        }
        public void force(Path path) throws IOException {
            if (fault.equals("flush")) throw new IOException("injected flush");
            try (FileChannel channel = FileChannel.open(path, StandardOpenOption.WRITE)) { channel.force(true); }
        }
        public void atomicMove(Path from, Path to, boolean replace) throws IOException {
            if (fault.equals("replace") && to.getFileName().toString().equals("record.nbt"))
                throw new IOException("injected atomic replace");
            Files.move(from, to, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        }
    }
}
