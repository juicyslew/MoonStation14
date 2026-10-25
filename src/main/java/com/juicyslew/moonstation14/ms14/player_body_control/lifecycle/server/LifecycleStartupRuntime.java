package com.juicyslew.moonstation14.ms14.player_body_control.lifecycle.server;

import com.juicyslew.moonstation14.MoonStation14;
import com.juicyslew.moonstation14.ms14.player_body_control.lifecycle.persistence.LifecycleProfileStore;
import com.juicyslew.moonstation14.ms14.player_body_control.lifecycle.persistence.LifecycleStoreEnvelope;
import com.juicyslew.moonstation14.ms14.player_body_control.lifecycle.persistence.SavedLifecycleProfile;
import com.juicyslew.moonstation14.ms14.player_body_control.server.GhostMobHarnessControl;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.function.Predicate;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.Map;

/** M2 startup-only probe. It never hydrates registries, spawns entities, or changes modes. */
@EventBusSubscriber(modid = MoonStation14.MOD_ID)
public final class LifecycleStartupRuntime {
    private static final String STORE_DIRECTORY = "moonstation14-lifecycle";
    private static final String STORE_FILE = "profiles.json";
    private static final Map<Object, StartupSnapshot> SERVERS = new IdentityHashMap<>();
    private static final Map<Object, LifecycleServerContext> CONTEXTS = new IdentityHashMap<>();

    private LifecycleStartupRuntime() { }

    public enum State { UNINITIALIZED, EMPTY, RECOVERY_REQUIRED, DEFERRED, STOPPED }

    /** Immutable bounded status intended for a future coordinator/diagnostic surface. */
    public record StartupSnapshot(State state, String diagnostic, int profileCount) {
        public StartupSnapshot {
            Objects.requireNonNull(state, "state");
            diagnostic = bounded(diagnostic);
            if (profileCount < 0 || profileCount > 100_000) throw new IllegalArgumentException("invalid profile count");
        }
    }

    @FunctionalInterface
    interface StoreReader { Optional<java.util.List<SavedLifecycleProfile.State>> read() throws IOException; }

    @FunctionalInterface
    interface EnvelopeReader { Optional<LifecycleStoreEnvelope> read() throws IOException; }

    record StartupResult(StartupSnapshot snapshot, LifecycleServerContext context) { }

    static StartupResult start(LifecycleProfileStore store, EnvelopeReader reader) {
        try {
            Optional<LifecycleStoreEnvelope> envelope = reader.read();
            StartupSnapshot snapshot;
            if (envelope.isEmpty()) {
                snapshot = new StartupSnapshot(State.UNINITIALIZED, "no primary or backup profile store exists", 0);
            } else {
                var profiles = envelope.get().profiles();
                snapshot = classify(profiles.stream().map(SavedLifecycleProfile::state).toList());
            }
            return new StartupResult(snapshot, new LifecycleServerContext(store, envelope.orElse(null)));
        } catch (IOException | RuntimeException failure) {
            return new StartupResult(new StartupSnapshot(State.RECOVERY_REQUIRED,
                    "profile store/path recovery required: " + failure.getMessage(), 0), null);
        }
    }

    private static StartupSnapshot classify(java.util.List<SavedLifecycleProfile.State> states) {
        if (states.isEmpty()) return new StartupSnapshot(State.EMPTY, "initialized profile store is empty; first enrollment is not authorized by startup", 0);
        for (SavedLifecycleProfile.State profileState : states) {
            if (profileState != SavedLifecycleProfile.State.OFFLINE
                    && profileState != SavedLifecycleProfile.State.DEAD_CLAIM) {
                return new StartupSnapshot(State.RECOVERY_REQUIRED,
                        "profile state " + profileState + " requires operator recovery; startup will not hydrate or spawn", states.size());
            }
        }
        return new StartupSnapshot(State.DEFERRED,
                "OFFLINE/DEAD_CLAIM profiles are reserved; loaded-body proof and lifecycle activation are deferred", states.size());
    }

    static StartupSnapshot inspect(StoreReader reader) {
        try {
            Optional<java.util.List<SavedLifecycleProfile.State>> states = reader.read();
            if (states.isEmpty()) return new StartupSnapshot(State.UNINITIALIZED, "no primary or backup profile store exists", 0);
            return classify(states.get());
        } catch (IOException | RuntimeException failure) {
            return new StartupSnapshot(State.RECOVERY_REQUIRED, "profile store/path recovery required: " + failure.getMessage(), 0);
        }
    }

    @SubscribeEvent
    public static void onServerStarted(ServerStartedEvent event) {
        MinecraftServer server = event.getServer();
        StartupResult result;
        try {
            Path storePath = safeStorePath(server.getWorldPath(LevelResource.ROOT));
            LifecycleProfileStore store = new LifecycleProfileStore(storePath);
            result = start(store, store::read);
        } catch (IOException | RuntimeException failure) {
            result = new StartupResult(new StartupSnapshot(State.RECOVERY_REQUIRED,
                    "profile store/path recovery required: " + failure.getMessage(), 0), null);
        }
        StartupSnapshot snapshot = result.snapshot();
        synchronized (SERVERS) {
            SERVERS.put(server, snapshot);
            if (result.context() == null) CONTEXTS.remove(server);
            else CONTEXTS.put(server, result.context());
        }
        if (snapshot.state() == State.RECOVERY_REQUIRED) {
            MoonStation14.LOGGER.error("[lifecycle startup] {}", snapshot.diagnostic());
        } else {
            MoonStation14.LOGGER.info("[lifecycle startup] state={} profiles={} ({})", snapshot.state(), snapshot.profileCount(), snapshot.diagnostic());
        }
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        forgetServer(event.getServer());
    }

    /** Returns a snapshot for this exact server instance; it performs no file or world access. */
    public static Optional<StartupSnapshot> snapshot(MinecraftServer server) {
        synchronized (SERVERS) {
            return Optional.ofNullable(SERVERS.get(server));
        }
    }

    /** Read-only in-memory owner availability check; no lifecycle store reads occur on entity ticks. */
    public static boolean isExactOfflineOwnerBody(net.minecraft.world.entity.Entity body,
                                                   com.juicyslew.moonstation14.ms14.player_body_control.lifecycle.character.PlayerCharacterBinding binding) {
        if (body == null || binding == null || !(body.level() instanceof net.minecraft.server.level.ServerLevel level)
                || level.getServer() == null || !level.getServer().isSameThread()) return false;
        LifecycleServerContext context;
        synchronized (SERVERS) { context = CONTEXTS.get(level.getServer()); }
        if (context == null) return false;
        var profile = context.lifecycle().profile(binding.accountId()).orElse(null);
        if (profile != null) return profile.state() == com.juicyslew.moonstation14.ms14.player_body_control.lifecycle.PlayerLifecycleRegistry.LifecycleState.OFFLINE
                && profile.bodyId().value().equals(body.getUUID());
        // Startup deliberately does not hydrate profiles. A durable account reservation with no
        // in-memory body proof is conservatively parked; once a current profile is known, an old
        // abandoned-body binding no longer pins a different UUID.
        return context.hasReservedClaim(binding.accountId());
    }

    /**
     * Persist the actual pose of a physically held/flight-moved OFFLINE bound body, only when its
     * recovery chunk or dimension changed. The caller's proof must establish current physical
     * custody; this method independently revalidates the exact loaded entity and binding.
     */
    public static boolean recordOfflineBodyRelocation(net.minecraft.world.entity.Entity body,
            long expectedGeneration, Predicate<net.minecraft.world.entity.Entity> physicalOwnerProof) {
        return recordOfflineBodyRelocation(body, expectedGeneration, null, physicalOwnerProof);
    }

    /** Production caller form: derive expected generation from the exact current OFFLINE row. */
    public static boolean recordOfflineBodyRelocation(net.minecraft.world.entity.Entity body,
            Predicate<net.minecraft.world.entity.Entity> physicalOwnerProof) {
        if (body == null || physicalOwnerProof == null
                || !(body.level() instanceof net.minecraft.server.level.ServerLevel level)
                || level.getServer() == null || !level.getServer().isSameThread()) return false;
        var binding = com.juicyslew.moonstation14.ms14.player_body_control.lifecycle.character
                .PlayerCharacterHarnessBinder.binding(body);
        if (binding == null) return false;
        LifecycleServerContext context;
        synchronized (SERVERS) { context = CONTEXTS.get(level.getServer()); }
        if (context == null) return false;
        final SavedLifecycleProfile row;
        try { row = context.currentAccountProfile(binding.accountId()).orElse(null); }
        catch (IOException | RuntimeException unavailable) { return false; }
        if (!offlineRelocationBindingMatches(row, binding, body.getUUID(),
                row == null ? -1 : row.connectionGeneration())) return false;
        return recordOfflineBodyRelocation(body, row.connectionGeneration(), row, physicalOwnerProof);
    }

    private static boolean recordOfflineBodyRelocation(net.minecraft.world.entity.Entity body,
            long expectedGeneration, SavedLifecycleProfile expectedRow,
            Predicate<net.minecraft.world.entity.Entity> physicalOwnerProof) {
        if (body == null || expectedGeneration < 0 || physicalOwnerProof == null
                || !(body instanceof net.minecraft.world.entity.Mob)
                || !(body.level() instanceof net.minecraft.server.level.ServerLevel level)
                || level.getServer() == null || !level.getServer().isSameThread()
                || body.isRemoved() || !body.isAddedToLevel() || level.getEntity(body.getUUID()) != body)
            return false;
        var binding = com.juicyslew.moonstation14.ms14.player_body_control.lifecycle.character
                .PlayerCharacterHarnessBinder.binding(body);
        if (binding == null) return false;
        LifecycleServerContext context;
        synchronized (SERVERS) { context = CONTEXTS.get(level.getServer()); }
        if (context == null) return false;
        var harnessId = new com.juicyslew.moonstation14.ms14.player_body_control.MobHarnessId(body.getUUID());
        var harness = context.ownership().registeredHarness(harnessId).orElse(null);
        if (harness != null && harness.kind()
                != com.juicyslew.moonstation14.ms14.player_body_control.MobHarnessKind.CHARACTER) return false;
        if (harness == null) harness = new com.juicyslew.moonstation14.ms14.player_body_control.MobHarness(
                harnessId, com.juicyslew.moonstation14.ms14.player_body_control.MobHarnessKind.CHARACTER);
        if (!(body instanceof net.minecraft.world.entity.Mob mobBody)
                || com.juicyslew.moonstation14.ms14.character.CharacterIdentitySystem.resolveForActor(mobBody).isEmpty())
            return false;
        final SavedLifecycleProfile row;
        try { row = context.currentAccountProfile(binding.accountId()).orElse(null); }
        catch (IOException | RuntimeException unavailable) { return false; }
        if (!offlineRelocationBindingMatches(row, binding, body.getUUID(), expectedGeneration)
                || !offlineRelocationRowUnchanged(expectedRow, row)) return false;
        Predicate<SavedLifecycleProfile> exactCustody = candidate -> {
            if (!row.equals(candidate) || !offlineRelocationRowUnchanged(expectedRow, candidate)
                    || body.isRemoved() || !body.isAddedToLevel()
                    || body.level() != level || level.getEntity(body.getUUID()) != body
                    || !binding.equals(com.juicyslew.moonstation14.ms14.player_body_control.lifecycle.character
                            .PlayerCharacterHarnessBinder.binding(body))) return false;
            try { return physicalOwnerProof.test(body); }
            catch (RuntimeException rejected) { return false; }
        };
        if (!exactCustody.test(row)) return false;
        var pose = new SavedLifecycleProfile.Location(body.getX(), body.getY(), body.getZ());
        try {
            return context.relocateOfflineBody(binding.accountId(), binding.mindId(), body.getUUID(),
                    expectedGeneration, harness, level.dimension().location().toString(), pose, exactCustody);
        } catch (IOException | RuntimeException unavailable) { return false; }
    }

    static boolean offlineRelocationBindingMatches(SavedLifecycleProfile row,
            com.juicyslew.moonstation14.ms14.player_body_control.lifecycle.character.PlayerCharacterBinding binding,
            UUID bodyId, long expectedGeneration) {
        return row != null && binding != null && bodyId != null && expectedGeneration >= 0
                && row.state() == SavedLifecycleProfile.State.OFFLINE
                && row.connectionGeneration() == expectedGeneration
                && row.accountId().equals(binding.accountId()) && row.profileKey().equals(binding.profileKey())
                && row.mindId().equals(binding.mindId()) && row.bodyId().equals(bodyId);
    }

    static boolean offlineRelocationRowUnchanged(SavedLifecycleProfile sampled,
            SavedLifecycleProfile current) {
        return current != null && (sampled == null || sampled.equals(current));
    }

    /**
     * Whether the startup-only lifecycle evidence reserves this account from the independent debug Mind.
     * Recovery-required state blocks every account because ownership cannot be determined safely.
     * This is an in-memory snapshot check only; it never accesses the store or world.
     */
    public static boolean blocksDebugMindFor(MinecraftServer server, java.util.UUID accountUUID) {
        return blocksDebugMindFor((Object) server, accountUUID);
    }

    /**
     * Publishes a just-persisted enrollment reservation to this server's debug exclusion index. The caller
     * must invoke this on the server thread while holding the successful CAS's live primary lease.
     */
    public static boolean rememberPreparingClaim(MinecraftServer server,
            LifecycleProfileStore.CurrentPrimary lease, SavedLifecycleProfile claim) {
        if (server == null || !server.isSameThread()) return false;
        LifecycleServerContext context;
        synchronized (SERVERS) {
            if (!SERVERS.containsKey(server)) return false;
            context = CONTEXTS.get(server);
        }
        if (context == null) return false;
        try {
            return context.rememberPreparingClaim(lease, claim);
        } catch (IOException | RuntimeException failure) {
            return false;
        }
    }

    /** Server-thread first-account reservation called by the exact-player lifecycle join coordinator. */
    static boolean reserveFirstProfile(MinecraftServer server, UUID authenticatedAccountId, String profileKey,
            UUID mindId, UUID bodyId, String dimension, SavedLifecycleProfile.Location location,
            Map<String, String> defaultValidatedAppearance) {
        if (server == null || !server.isSameThread() || authenticatedAccountId == null) return false;
        StartupSnapshot snapshot;
        LifecycleServerContext context;
        synchronized (SERVERS) {
            snapshot = SERVERS.get(server);
            context = CONTEXTS.get(server);
        }
        if (context == null || snapshot == null
                || snapshot.state() != State.EMPTY && snapshot.state() != State.UNINITIALIZED) return false;
        var connectedPlayer = server.getPlayerList().getPlayer(authenticatedAccountId);
        if (connectedPlayer != null && GhostMobHarnessControl.ownsDebugSession(connectedPlayer)) return false;
        try {
            return context.reserveFirstProfile(authenticatedAccountId, profileKey, mindId, bodyId,
                    dimension, location, defaultValidatedAppearance);
        } catch (IOException | RuntimeException failure) {
            // A failed atomic store operation is evidence for operator recovery, never an implicit retry.
            synchronized (SERVERS) {
                if (SERVERS.get(server) == snapshot)
                    SERVERS.put(server, new StartupSnapshot(State.RECOVERY_REQUIRED,
                            "first-account reservation failed; inspect lifecycle store evidence", snapshot.profileCount()));
            }
            return false;
        }
    }

    static boolean blocksDebugMindFor(Object serverIdentity, java.util.UUID accountUUID) {
        if (serverIdentity == null || accountUUID == null) return false;
        synchronized (SERVERS) {
            StartupSnapshot snapshot = SERVERS.get(serverIdentity);
            if (snapshot != null && snapshot.state() == State.RECOVERY_REQUIRED) return true;
            LifecycleServerContext context = CONTEXTS.get(serverIdentity);
            return context != null && context.hasReservedClaim(accountUUID);
        }
    }

    static void rememberSnapshot(Object serverIdentity, StartupSnapshot snapshot) {
        synchronized (SERVERS) {
            SERVERS.put(Objects.requireNonNull(serverIdentity, "serverIdentity"),
                    Objects.requireNonNull(snapshot, "snapshot"));
        }
    }

    static void forgetServer(Object serverIdentity) {
        synchronized (SERVERS) {
            SERVERS.remove(serverIdentity);
            CONTEXTS.remove(serverIdentity);
        }
    }

    /** Read-only startup claim check. It never performs disk I/O and is false without an active context. */
    static boolean hasReservedClaim(MinecraftServer server, java.util.UUID accountUUID) {
        synchronized (SERVERS) {
            LifecycleServerContext context = CONTEXTS.get(server);
            return context != null && context.hasReservedClaim(accountUUID);
        }
    }

    static void rememberContext(Object serverIdentity, LifecycleServerContext context) {
        synchronized (SERVERS) {
            CONTEXTS.put(Objects.requireNonNull(serverIdentity, "serverIdentity"),
                    Objects.requireNonNull(context, "context"));
        }
    }

    static void discardContext(Object serverIdentity) {
        synchronized (SERVERS) {
            CONTEXTS.remove(serverIdentity);
        }
    }

    static Optional<LifecycleServerContext> contextFor(Object serverIdentity) {
        synchronized (SERVERS) {
            return Optional.ofNullable(CONTEXTS.get(serverIdentity));
        }
    }

    static Path safeStorePath(Path worldRoot) throws IOException {
        Path root = Objects.requireNonNull(worldRoot, "worldRoot").toAbsolutePath().normalize();
        if (!Files.isDirectory(root, LinkOption.NOFOLLOW_LINKS) || isLinkOrReparse(root))
            throw new IOException("world root is missing, not a directory, or is a link/reparse point");
        Path directory = root.resolve(STORE_DIRECTORY).normalize();
        Path file = directory.resolve(STORE_FILE).normalize();
        if (!directory.startsWith(root) || !file.startsWith(root)) throw new IOException("lifecycle store escaped world root");
        verifyComponents(root, directory);
        if (!Files.exists(directory, LinkOption.NOFOLLOW_LINKS)) Files.createDirectory(directory);
        verifyComponents(root, directory);
        if (Files.exists(file, LinkOption.NOFOLLOW_LINKS) && (isLinkOrReparse(file) || !Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)))
            throw new IOException("lifecycle primary is not a regular non-link file");
        // Store siblings are evidence too; reject substitutions before LifecycleProfileStore inspects them.
        for (String suffix : new String[]{".bak", ".tmp", ".bak.tmp", ".lock"}) {
            Path sibling = file.resolveSibling(file.getFileName() + suffix);
            if (Files.exists(sibling, LinkOption.NOFOLLOW_LINKS) && (isLinkOrReparse(sibling)
                    || !Files.isRegularFile(sibling, LinkOption.NOFOLLOW_LINKS)))
                throw new IOException("lifecycle store evidence is not a regular non-link file");
        }
        return file;
    }

    private static void verifyComponents(Path root, Path target) throws IOException {
        Path current = root;
        for (Path component : root.relativize(target)) {
            current = current.resolve(component);
            if (Files.exists(current, LinkOption.NOFOLLOW_LINKS)
                    && (isLinkOrReparse(current) || !Files.isDirectory(current, LinkOption.NOFOLLOW_LINKS)))
                throw new IOException("lifecycle store directory component is not a trusted directory");
        }
    }

    private static boolean isLinkOrReparse(Path path) throws IOException {
        if (Files.isSymbolicLink(path)) return true;
        BasicFileAttributes attributes = Files.readAttributes(path, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
        // Windows reparse points commonly report as "other"; fail closed on those and on non-standard entries.
        return attributes.isOther();
    }

    private static String bounded(String value) {
        if (value == null || value.isBlank()) return "";
        return value.length() <= 240 ? value : value.substring(0, 240);
    }
}

