package com.juicyslew.moonstation14.gametest.support;

import com.juicyslew.moonstation14.ms14.prototype.PrototypeRuntime;
import com.juicyslew.moonstation14.ms14.prototype.PrototypeType;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.GameTestInfo;
import net.minecraft.gametest.framework.GameTestListener;
import net.minecraft.gametest.framework.GameTestRunner;
import net.minecraft.resources.ResourceLocation;

import java.util.HashMap;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.UnaryOperator;

/** Scoped ownership for one server catalog. Catalog-mutating tests need distinct GameTest batches: batches, not tags, prevent overlap. */
public final class ScopedPrototypeCatalogFixture {
    private static final Map<GameTestInfo, RestorationGroup> GROUPS = new IdentityHashMap<>();
    private ScopedPrototypeCatalogFixture() { }

    public static <T> Handle replace(GameTestHelper helper, PrototypeType<T> type,
                                     UnaryOperator<Map<ResourceLocation, T>> replacement) {
        var manager = PrototypeRuntime.serverManager();
        var original = new HashMap<>(manager.snapshot(type).asMap());
        var handle = new Handle(() -> manager.publishDecoded(type, original));
        try {
            manager.publishDecoded(type, new HashMap<>(replacement.apply(new HashMap<>(original))));
            register(helper.testInfo, handle);
            return handle;
        } catch (Throwable failure) {
            handle.restore();
            throw failure;
        }
    }

    static void register(GameTestInfo info, Handle handle) {
        var group = GROUPS.get(info);
        if (group == null) {
            group = new RestorationGroup(info);
            GROUPS.put(info, group);
            info.addListener(group);
        }
        group.handles.add(handle);
    }

    private static final class RestorationGroup implements GameTestListener {
        private final GameTestInfo info;
        private final List<Handle> handles = new ArrayList<>();
        private RestorationGroup(GameTestInfo info) { this.info = info; }
        @Override public void testStructureLoaded(GameTestInfo info) { }
        @Override public void testPassed(GameTestInfo info, GameTestRunner runner) { restore(); }
        @Override public void testFailed(GameTestInfo info, GameTestRunner runner) { restore(); }
        @Override public void testAddedForRerun(GameTestInfo oldTest, GameTestInfo newTest, GameTestRunner runner) { restore(); }
        private void restore() {
            if (GROUPS.get(info) != this) return;
            GROUPS.remove(info, this);
            for (int i = handles.size() - 1; i >= 0; i--) handles.get(i).restore();
            handles.clear();
        }
    }

    public static final class Handle {
        private final Runnable restoreAction;
        private final AtomicBoolean restored = new AtomicBoolean();
        Handle(Runnable restoreAction) { this.restoreAction = restoreAction; }
        public void restore() { if (restored.compareAndSet(false, true)) restoreAction.run(); }
    }
}
