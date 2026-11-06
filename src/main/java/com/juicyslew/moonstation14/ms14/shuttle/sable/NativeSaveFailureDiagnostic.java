package com.juicyslew.moonstation14.ms14.shuttle.sable;

import java.util.function.Consumer;
import java.util.function.BiConsumer;
import java.util.List;
import java.util.UUID;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import net.minecraft.server.level.ServerLevel;

/** Preserves a native save fault if failure-context logging itself fails. */
public final class NativeSaveFailureDiagnostic {
    private NativeSaveFailureDiagnostic() { }

    public static <T> T invoke(Invocation<T> operation, Consumer<RuntimeException> observer) {
        try {
            return operation.call();
        } catch (RuntimeException failure) {
            try {
                observer.accept(failure);
            } catch (Throwable ignored) {
                // Diagnostics must not replace the SDK failure.
            }
            throw failure;
        }
    }

    /** Captures only cheap entry identity; all metadata failures still proceed to the supplied SDK call. */
    public static <T> T invoke(ServerSubLevel frame, List<UUID> dependencies, SerializerCall<T> operation,
                               BiConsumer<String, RuntimeException> observer) {
        String entry = "metadata=unavailable";
        try {
            java.util.UUID uuid = frame.getUniqueId();
            boolean removed = frame.isRemoved();
            ServerLevel level = frame.getLevel();
            boolean registered = SubLevelContainer.getContainer(level).getSubLevel(uuid) == frame;
            entry = "uuid=" + uuid + ",removed=" + removed + ",registered=" + registered;
        } catch (Throwable ignored) {
            // Entry metadata must never prevent Sable's serializer from running.
        }
        final String entrySnapshot = entry;
        return invoke(() -> operation.call(frame, dependencies), failure -> observer.accept(entrySnapshot, failure));
    }

    @FunctionalInterface
    public interface Invocation<T> { T call(); }

    @FunctionalInterface
    public interface SerializerCall<T> { T call(ServerSubLevel frame, List<UUID> dependencies); }
}
