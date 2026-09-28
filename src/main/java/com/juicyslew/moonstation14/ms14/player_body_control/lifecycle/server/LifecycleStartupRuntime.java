package com.juicyslew.moonstation14.ms14.player_body_control.lifecycle.server;

import com.juicyslew.moonstation14.MoonStation14;
import com.juicyslew.moonstation14.ms14.movement.MovementStartupGate;
import com.juicyslew.moonstation14.ms14.player_body_control.lifecycle.persistence.LifecycleProfileStore;
import com.juicyslew.moonstation14.ms14.player_body_control.lifecycle.persistence.LifecycleStoreEnvelope;
import com.juicyslew.moonstation14.ms14.player_body_control.lifecycle.persistence.SavedLifecycleProfile;
import com.juicyslew.moonstation14.ms14.player_body_control.server.GhostMobHarnessControl;
import com.juicyslew.moonstation14.ms14.player_body_control.server.MindGhostStartupGate;
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

    public enum State { DISABLED, CONFLICT, UNINITIALIZED, EMPTY, RECOVERY_REQUIRED, DEFERRED, STOPPED }

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

    static StartupResult start(boolean masterEnabled, boolean movementConflict,
                                LifecycleProfileStore store, EnvelopeReader reader) {
        if (!masterEnabled) return new StartupResult(new StartupSnapshot(State.DISABLED, "shared lifecycle master gate is off", 0), null);
        if (movementConflict) return new StartupResult(new StartupSnapshot(State.CONFLICT, "legacy movement startup conflict is active", 0), null);
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

    static StartupSnapshot inspect(boolean masterEnabled, boolean movementConflict, StoreReader reader) {
        if (!masterEnabled) return new StartupSnapshot(State.DISABLED, "shared lifecycle master gate is off", 0);
        if (movementConflict) return new StartupSnapshot(State.CONFLICT, "legacy movement startup conflict is active", 0);
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
        boolean master = MindGhostStartupGate.enabledForServer();
        boolean conflict = MovementStartupGate.enabledForServer();
        StartupResult result;
        if (!master) result = start(false, conflict, null, Optional::empty);
        else if (conflict) result = start(true, true, null, Optional::empty);
        else {
            try {
                Path storePath = safeStorePath(server.getWorldPath(LevelResource.ROOT));
                LifecycleProfileStore store = new LifecycleProfileStore(storePath);
                result = start(true, false, store, store::read);
            } catch (IOException | RuntimeException failure) {
                result = new StartupResult(new StartupSnapshot(State.RECOVERY_REQUIRED,
                        "profile store/path recovery required: " + failure.getMessage(), 0), null);
            }
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
        if (server == null || !server.isSameThread() || !MindGhostStartupGate.enabledForServer()
                || MovementStartupGate.enabledForServer()) return false;
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
        if (server == null || !server.isSameThread() || authenticatedAccountId == null
                || !MindGhostStartupGate.enabledForServer() || MovementStartupGate.enabledForServer()) return false;
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
