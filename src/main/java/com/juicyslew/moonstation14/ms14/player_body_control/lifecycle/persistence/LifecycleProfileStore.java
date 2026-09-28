package com.juicyslew.moonstation14.ms14.player_body_control.lifecycle.persistence;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.OverlappingFileLockException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import com.juicyslew.moonstation14.ms14.player_body_control.lifecycle.PlayerLifecycleRegistry;

/**
 * Small file-backed CAS store. This is deliberately not wired to runtime lifecycle code; the supplied
 * path must already be chosen and validated by a world-specific caller.
 */
public final class LifecycleProfileStore {
    private final Path primary;
    private final Path backup;
    private final Path temporary;
    private final Path backupTemporary;
    private final Path lockPath;
    private final FileOperations files;
    private CurrentPrimary activePrimary;

    public LifecycleProfileStore(Path validatedWorldSpecificPath) {
        this(validatedWorldSpecificPath, NioFileOperations.INSTANCE);
    }

    LifecycleProfileStore(Path validatedWorldSpecificPath, FileOperations files) {
        primary = Objects.requireNonNull(validatedWorldSpecificPath, "validatedWorldSpecificPath").toAbsolutePath().normalize();
        if (primary.getFileName() == null || primary.getParent() == null)
            throw new IllegalArgumentException("store path must name a file in a directory");
        backup = primary.resolveSibling(primary.getFileName() + ".bak");
        temporary = primary.resolveSibling(primary.getFileName() + ".tmp");
        backupTemporary = primary.resolveSibling(primary.getFileName() + ".bak.tmp");
        lockPath = primary.resolveSibling(primary.getFileName() + ".lock");
        this.files = Objects.requireNonNull(files, "files");
    }

    /** Missing files mean uninitialized; any present but invalid evidence is an error, never an empty store. */
    public synchronized Optional<LifecycleStoreEnvelope> read() throws IOException {
        rejectOrphanTemps();
        if (!files.exists(primary)) {
            if (files.exists(backup)) throw recovery("primary is missing but backup exists");
            return Optional.empty();
        }
        LifecycleStoreEnvelope current = readValid(primary);
        validateBackup(current);
        return Optional.of(current);
    }

    /**
     * Reads the current primary while holding the same OS lock used by CAS and lends its provenance
     * only for the duration of the callback. Missing stores provide no lease. Any present backup is
     * validated against the primary before a lease is issued; it is never selected or promoted.
     */
    public synchronized <T> Optional<T> withCurrentPrimary(CurrentPrimaryCallback<T> callback) throws IOException {
        Objects.requireNonNull(callback, "callback");
        if (activePrimary != null && activePrimary.isActive())
            throw new IllegalStateException("nested current-primary lease callbacks are not supported");
        try (FileChannel channel = FileChannel.open(lockPath, StandardOpenOption.CREATE, StandardOpenOption.WRITE);
             FileLock ignored = acquireLock(channel)) {
            rejectOrphanTemps();
            if (!files.exists(primary)) {
                if (files.exists(backup)) throw recovery("primary is missing but backup exists");
                return Optional.empty();
            }
            LifecycleStoreEnvelope current = readValid(primary);
            validateBackup(current);
            CurrentPrimary lease = new CurrentPrimary(this, current, Thread.currentThread());
            activePrimary = lease;
            try {
                return Optional.ofNullable(callback.apply(lease));
            } finally {
                lease.invalidate();
                activePrimary = null;
            }
        }
    }

    @FunctionalInterface
    public interface CurrentPrimaryCallback<T> {
        T apply(CurrentPrimary currentPrimary) throws IOException;
    }

    /** The store-issued, callback-scoped proof that this envelope was read from the locked primary. */
    public static final class CurrentPrimary {
        private final LifecycleProfileStore store;
        private final LifecycleStoreEnvelope envelope;
        private final Thread owner;
        private volatile boolean active = true;

        private CurrentPrimary(LifecycleProfileStore store, LifecycleStoreEnvelope envelope, Thread owner) {
            this.store = store;
            this.envelope = envelope;
            this.owner = owner;
        }

        public LifecycleStoreEnvelope envelope() { return envelope; }

        /** True only for a live lease issued by the exact store instance. */
        public boolean isFrom(LifecycleProfileStore expectedStore) {
            return expectedStore != null && store == expectedStore && isActive();
        }

        /** Re-reads the primary under this lease's existing OS lock; it never acquires another lock. */
        public LifecycleStoreEnvelope readCurrentPrimary() throws IOException {
            if (!isActive()) throw new IllegalStateException("current-primary lease is not active");
            return store.readCurrentPrimary(this);
        }

        /**
         * Replaces the complete primary snapshot using the OS lock already held by the enclosing
         * callback. The captured revision and epoch are the CAS expectation; this deliberately does
         * not acquire a second file lock.
         */
        public LifecycleStoreEnvelope compareAndSwap(long generationCounter,
                                                       List<SavedLifecycleProfile> profiles) throws IOException {
            if (!isActive()) throw new IllegalStateException("current-primary lease is not active");
            return store.compareAndSwapCurrentPrimary(this, generationCounter, profiles);
        }

        /** Proof-scoped exception for the facade's one exact first-enrollment promotion. */
        public LifecycleStoreEnvelope promotePreparingToActive(
                PlayerLifecycleRegistry.EnrollmentPromotionProof proof, long generationCounter,
                List<SavedLifecycleProfile> profiles) throws IOException {
            if (!isActive()) throw new IllegalStateException("current-primary lease is not active");
            return store.compareAndSwapPromotion(this, proof, generationCounter, profiles);
        }

        /** True only on the callback thread, while this store still holds its file lock. */
        public boolean isActive() {
            return active && Thread.currentThread() == owner && store.activePrimary == this;
        }

        private void invalidate() { active = false; }
    }

    private synchronized LifecycleStoreEnvelope compareAndSwapCurrentPrimary(CurrentPrimary lease,
            long generationCounter, List<SavedLifecycleProfile> profiles) throws IOException {
        if (activePrimary != lease || !lease.isActive())
            throw new IllegalStateException("current-primary lease is not active");
        return compareAndSwapLocked(lease.envelope.storeRevision(), lease.envelope.epochToken(),
                generationCounter, profiles, null);
    }

    private synchronized LifecycleStoreEnvelope readCurrentPrimary(CurrentPrimary lease) throws IOException {
        if (activePrimary != lease || !lease.isActive())
            throw new IllegalStateException("current-primary lease is not active");
        return readValid(primary);
    }

    private synchronized LifecycleStoreEnvelope compareAndSwapPromotion(CurrentPrimary lease,
            PlayerLifecycleRegistry.EnrollmentPromotionProof proof, long generationCounter,
            List<SavedLifecycleProfile> profiles) throws IOException {
        if (activePrimary != lease || !lease.isActive())
            throw new IllegalStateException("current-primary lease is not active");
        Objects.requireNonNull(proof, "proof");
        if (!proof.consume(lease.envelope, generationCounter, profiles))
            throw new IllegalArgumentException("invalid, mismatched, or already-used enrollment promotion proof");
        return compareAndSwapLocked(lease.envelope.storeRevision(), lease.envelope.epochToken(),
                generationCounter, profiles, proof);
    }

    /**
     * Replace the complete profile set iff the durable store revision equals expectedRevision. Use -1
     * only to initialize an absent store. The store epoch is immutable after initialization.
     */
    public synchronized LifecycleStoreEnvelope compareAndSwap(long expectedRevision, UUID epochToken,
                                                                long generationCounter,
                                                                List<SavedLifecycleProfile> profiles) throws IOException {
        try (FileChannel channel = FileChannel.open(lockPath, StandardOpenOption.CREATE, StandardOpenOption.WRITE);
             FileLock ignored = acquireLock(channel)) {
            return compareAndSwapLocked(expectedRevision, epochToken, generationCounter, profiles, null);
        }
    }

    private LifecycleStoreEnvelope compareAndSwapLocked(long expectedRevision, UUID epochToken,
                                                           long generationCounter,
                                                           List<SavedLifecycleProfile> profiles,
                                                           PlayerLifecycleRegistry.EnrollmentPromotionProof proof) throws IOException {
        rejectOrphanTemps();
        Optional<LifecycleStoreEnvelope> loaded = read();
        if (loaded.isEmpty() != (expectedRevision == -1)
                || loaded.isPresent() && loaded.get().storeRevision() != expectedRevision)
            throw new RevisionConflictException(expectedRevision, loaded.map(LifecycleStoreEnvelope::storeRevision).orElse(-1L));
        if (loaded.isPresent()) {
            LifecycleStoreEnvelope old = loaded.get();
            if (!old.epochToken().equals(epochToken) || generationCounter < old.generationCounter())
                throw new IllegalArgumentException("store epoch cannot change and generation counter cannot move backwards");
            validatePersistentIdentities(old.profiles(), profiles);
            validateProfileGenerations(old.profiles(), profiles);
            validateInitialEnrollmentTransitions(old.profiles(), profiles, proof);
        }
        long newRevision = expectedRevision + 1;
        if (newRevision < 0) throw new IllegalStateException("store revision exhausted");
        LifecycleStoreEnvelope next = new LifecycleStoreEnvelope(LifecycleStoreEnvelope.CURRENT_SCHEMA,
                newRevision, epochToken, generationCounter, profiles);
        String encoded = LifecycleProfileCodec.encodeStore(next);

        // Stage all bytes before touching the current primary. Orphan temp files are never reused.
        files.writeNew(temporary, encoded.getBytes(StandardCharsets.UTF_8));
        try {
            forceStagedFile(temporary);
            if (loaded.isPresent()) {
                files.writeNew(backupTemporary, files.readAll(primary));
                forceStagedFile(backupTemporary);
                files.atomicMove(backupTemporary, backup, true);
            }
            files.atomicMove(temporary, primary, loaded.isPresent());
            return next;
        } catch (IOException | RuntimeException failure) {
            // Preserve temp evidence for diagnosis; never guess whether it is safe to delete/replay.
            throw failure;
        }
    }

    private static void forceStagedFile(Path path) throws IOException {
        if (!Files.isRegularFile(path))
            throw new IOException("lifecycle store staged path is not a regular file: " + path);
        try (FileChannel channel = FileChannel.open(path, StandardOpenOption.WRITE)) {
            channel.force(true);
        }
    }

    private static FileLock acquireLock(FileChannel channel) throws IOException {
        try {
            FileLock lock = channel.tryLock();
            if (lock == null) throw new IOException("lifecycle store is busy: another writer holds its lock");
            return lock;
        } catch (OverlappingFileLockException e) {
            throw new IOException("lifecycle store is busy: another writer holds its lock", e);
        }
    }

    private static void validateProfileGenerations(List<SavedLifecycleProfile> previous,
                                                   List<SavedLifecycleProfile> next) {
        Map<ProfileIdentity, Long> priorGenerations = new HashMap<>();
        for (SavedLifecycleProfile profile : previous)
            priorGenerations.put(new ProfileIdentity(profile.accountId(), profile.profileKey()), profile.connectionGeneration());
        for (SavedLifecycleProfile profile : next) {
            Long prior = priorGenerations.get(new ProfileIdentity(profile.accountId(), profile.profileKey()));
            if (prior != null && profile.connectionGeneration() < prior)
                throw new IllegalArgumentException("profile generation cannot move backwards");
        }
    }

    private static void validatePersistentIdentities(List<SavedLifecycleProfile> previous,
                                                     List<SavedLifecycleProfile> next) {
        Map<UUID, SavedLifecycleProfile> nextByAccount = new HashMap<>();
        for (SavedLifecycleProfile profile : next)
            nextByAccount.put(profile.accountId(), profile);
        for (SavedLifecycleProfile prior : previous) {
            SavedLifecycleProfile current = nextByAccount.get(prior.accountId());
            if (current == null)
                throw new IllegalArgumentException("an existing account profile cannot be removed");
            if (!prior.profileKey().equals(current.profileKey()) || !prior.mindId().equals(current.mindId()))
                throw new IllegalArgumentException("an existing account profile key and Mind identity cannot change");
        }
    }

    /**
     * An initial enrollment intent is only a reservation. Without a runtime saga receipt proving
     * body and Mind persistence, CAS cannot promote it to an authorizing lifecycle state. Failed
     * or interrupted intents may be made permanently recovery-required, but never removed/reused.
     */
    private static void validateInitialEnrollmentTransitions(List<SavedLifecycleProfile> previous,
            List<SavedLifecycleProfile> next, PlayerLifecycleRegistry.EnrollmentPromotionProof proof) {
        Map<UUID, SavedLifecycleProfile> nextByAccount = new HashMap<>();
        for (SavedLifecycleProfile profile : next)
            nextByAccount.put(profile.accountId(), profile);
        for (SavedLifecycleProfile prior : previous) {
            if (prior.state() != SavedLifecycleProfile.State.PREPARING
                    && prior.state() != SavedLifecycleProfile.State.RECOVERY_REQUIRED)
                continue;
            SavedLifecycleProfile current = nextByAccount.get(prior.accountId());
            if (!prior.bodyId().equals(current.bodyId()))
                throw new IllegalArgumentException("an initial enrollment body claim cannot change");
            if (prior.state() == SavedLifecycleProfile.State.PREPARING
                    && current.state() != SavedLifecycleProfile.State.PREPARING
                    && current.state() != SavedLifecycleProfile.State.RECOVERY_REQUIRED
                    && !(current.state() == SavedLifecycleProfile.State.ACTIVE && proof != null
                    && proof.matchesProfiles(prior, current)))
                throw new IllegalArgumentException("an initial enrollment cannot be promoted without saga proof");
            if (prior.state() == SavedLifecycleProfile.State.RECOVERY_REQUIRED
                    && current.state() != SavedLifecycleProfile.State.RECOVERY_REQUIRED)
                throw new IllegalArgumentException("a recovery-required enrollment claim is irreversible");
        }
    }

    private record ProfileIdentity(UUID accountId, String profileKey) { }

    private void validateBackup(LifecycleStoreEnvelope current) throws IOException {
        if (!files.exists(backup)) return;
        LifecycleStoreEnvelope prior = readValid(backup);
        if (!prior.epochToken().equals(current.epochToken()) || prior.storeRevision() > current.storeRevision())
            throw recovery("backup metadata conflicts with primary");
    }

    private LifecycleStoreEnvelope readValid(Path path) throws IOException {
        try {
            return LifecycleProfileCodec.decodeStore(new String(files.readAll(path), StandardCharsets.UTF_8));
        } catch (IllegalArgumentException | IllegalStateException e) {
            throw new IOException("lifecycle store recovery required: invalid or unsupported data at " + path, e);
        }
    }

    private void rejectOrphanTemps() throws IOException {
        if (files.exists(temporary) || files.exists(backupTemporary))
            throw recovery("orphan temporary file exists; operator inspection required");
    }

    private IOException recovery(String reason) { return new IOException("lifecycle store recovery required: " + reason); }

    interface FileOperations {
        boolean exists(Path path);
        byte[] readAll(Path path) throws IOException;
        void writeNew(Path path, byte[] bytes) throws IOException;
        void atomicMove(Path source, Path target, boolean replace) throws IOException;
    }

    private enum NioFileOperations implements FileOperations {
        INSTANCE;
        @Override public boolean exists(Path path) { return Files.exists(path); }
        @Override public byte[] readAll(Path path) throws IOException { return Files.readAllBytes(path); }
        @Override public void writeNew(Path path, byte[] bytes) throws IOException {
            Files.write(path, bytes, java.nio.file.StandardOpenOption.CREATE_NEW, java.nio.file.StandardOpenOption.WRITE);
        }
        @Override public void atomicMove(Path source, Path target, boolean replace) throws IOException {
            try {
                if (replace) Files.move(source, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
                else Files.move(source, target, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException e) {
                throw new IOException("filesystem does not support required atomic lifecycle-store rename", e);
            }
        }
    }

    public static final class RevisionConflictException extends IOException {
        public RevisionConflictException(long expected, long actual) {
            super("lifecycle store revision conflict: expected " + expected + ", found " + actual);
        }
    }
}
