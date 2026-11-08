package com.juicyslew.moonstation14.ms14.item_processing;

import com.juicyslew.moonstation14.ms14.ingestion.IngestionProfile;
import com.juicyslew.moonstation14.ms14.prototype.PrototypeManager;
import com.juicyslew.moonstation14.ms14.prototype.PrototypeType;
import com.juicyslew.moonstation14.ms14.tools.ToolProfile;
import com.juicyslew.moonstation14.gametest.support.RegisteredServerPlayerFixture;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;

import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

/** Owns profile restoration, fixture closure, and callback guarding for item-processing GameTests. */
final class ItemProcessingFixtureCleanup {
    private final GameTestHelper helper;
    private final RegisteredServerPlayerFixture fixture;
    private final AtomicBoolean finished;
    private final PrototypeManager manager;
    private final PrototypeType<ItemProcessingProfile> processingType;
    private final Map<ResourceLocation, ItemProcessingProfile> processing;
    private final PrototypeType<ToolProfile> toolType;
    private final Map<ResourceLocation, ToolProfile> tools;
    private final PrototypeType<IngestionProfile> ingestionType;
    private final Map<ResourceLocation, IngestionProfile> ingestion;
    private final AtomicReference<Consumer<EntityJoinLevelEvent>> listener;
    private final AtomicBoolean closed = new AtomicBoolean();

    ItemProcessingFixtureCleanup(GameTestHelper helper, RegisteredServerPlayerFixture fixture, AtomicBoolean finished,
            PrototypeManager manager, PrototypeType<ItemProcessingProfile> processingType,
            Map<ResourceLocation, ItemProcessingProfile> processing, PrototypeType<ToolProfile> toolType,
            Map<ResourceLocation, ToolProfile> tools, PrototypeType<IngestionProfile> ingestionType,
            Map<ResourceLocation, IngestionProfile> ingestion, AtomicReference<Consumer<EntityJoinLevelEvent>> listener) {
        this.helper = helper; this.fixture = fixture; this.finished = finished; this.manager = manager;
        this.processingType = processingType; this.processing = processing; this.toolType = toolType; this.tools = tools;
        this.ingestionType = ingestionType; this.ingestion = ingestion; this.listener = listener;
        helper.testInfo.addListener(new net.minecraft.gametest.framework.GameTestListener() {
            @Override public void testStructureLoaded(net.minecraft.gametest.framework.GameTestInfo ignored) { }
            @Override public void testPassed(net.minecraft.gametest.framework.GameTestInfo info, net.minecraft.gametest.framework.GameTestRunner runner) { closeFromFramework(info); }
            @Override public void testFailed(net.minecraft.gametest.framework.GameTestInfo info, net.minecraft.gametest.framework.GameTestRunner runner) { closeFromFramework(info); }
            @Override public void testAddedForRerun(net.minecraft.gametest.framework.GameTestInfo oldTest,
                    net.minecraft.gametest.framework.GameTestInfo newTest, net.minecraft.gametest.framework.GameTestRunner runner) { closeFromFramework(oldTest); }
        });
    }

    void finish(Throwable failure) {
        if (!isOpen()) return;
        Throwable result = close(failure);
        if (!finished.compareAndSet(false, true)) return;
        if (result == null) helper.succeed(); else helper.testInfo.fail(result);
    }

    boolean isOpen() { return !closed.get() && !finished.get(); }

    void attachListener(Consumer<EntityJoinLevelEvent> value) {
        if (!isOpen()) return;
        if (listener == null) throw new IllegalStateException("This fixture has no join-listener slot");
        NeoForge.EVENT_BUS.addListener(value);
        listener.set(value);
        if (!isOpen()) {
            Consumer<EntityJoinLevelEvent> attached = listener.getAndSet(null);
            if (attached != null) NeoForge.EVENT_BUS.unregister(attached);
        }
    }

    private void closeFromFramework(net.minecraft.gametest.framework.GameTestInfo info) {
        Throwable cleanupFailure = close(null);
        if (cleanupFailure == null) return;
        Throwable existing = info.getError();
        if (existing != null) existing.addSuppressed(cleanupFailure);
        else info.fail(cleanupFailure);
    }

    private Throwable close(Throwable failure) {
        if (!closed.compareAndSet(false, true)) return failure;
        try { manager.publishDecoded(processingType, processing); } catch (Throwable e) { failure = suppress(failure, e); }
        try { manager.publishDecoded(toolType, tools); } catch (Throwable e) { failure = suppress(failure, e); }
        try { manager.publishDecoded(ingestionType, ingestion); } catch (Throwable e) { failure = suppress(failure, e); }
        if (listener != null) {
            Consumer<EntityJoinLevelEvent> registered = listener.getAndSet(null);
            if (registered != null) try { NeoForge.EVENT_BUS.unregister(registered); } catch (Throwable e) { failure = suppress(failure, e); }
        }
        try { fixture.close(); } catch (Throwable e) { failure = suppress(failure, e); }
        return failure;
    }

    private static Throwable suppress(Throwable original, Throwable cleanup) {
        if (original == null) return cleanup;
        original.addSuppressed(cleanup);
        return original;
    }
}
