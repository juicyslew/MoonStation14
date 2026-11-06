package com.juicyslew.moonstation14.ms14.shuttle.fixture.command;

import com.juicyslew.moonstation14.ms14.shuttle.fixture.ShuttleManualFixture;
import com.juicyslew.moonstation14.ms14.shuttle.sable.SableFixturePlot;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.server.level.ServerLevel;
import java.util.*;

/** Builds and owns only an explicitly command-authored diagnostic cabin. Caller guarantees a loaded, empty footprint. */
public final class CommandBuiltCabinFixture implements AutoCloseable {
    private final ServerLevel level;
    private final SubLevelContainer container;
    private final ServerSubLevel sub;
    private final int width, height, depth;
    private boolean closed;

    private CommandBuiltCabinFixture(ServerLevel level, SubLevelContainer container, ServerSubLevel sub,
                                     int width, int height, int depth) {
        this.level = level; this.container = container; this.sub = sub;
        this.width = width; this.height = height; this.depth = depth;
    }
    public ServerSubLevel subLevel() { return sub; }

    public static CommandBuiltCabinFixture build(GameTestHelper helper, BlockPos origin) {
        return buildManual(helper, origin);
    }

    public static CommandBuiltCabinFixture buildManual(GameTestHelper helper, BlockPos origin) {
        ServerLevel level = helper.getLevel();
        SubLevelContainer container = SubLevelContainer.getContainer(level);
        Set<UUID> before = new HashSet<>();
        container.getAllSubLevels().forEach(s -> before.add(s.getUniqueId()));
        String command = "ms14 shuttle_fixture build " + origin.getX() + " " + origin.getY() + " " + origin.getZ();
        try {
            int result = level.getServer().getCommands().getDispatcher().execute(command,
                    level.getServer().createCommandSourceStack().withLevel(level).withPermission(2));
            if (result != 1) throw new GameTestAssertException("explicit loaded fixture build command failed");
        } catch (com.mojang.brigadier.exceptions.CommandSyntaxException e) {
            throw new GameTestAssertException("fixture build command failed: " + e.getMessage());
        }
        List<Object> created = new ArrayList<>();
        container.getAllSubLevels().forEach(s -> { if (!before.contains(s.getUniqueId())) created.add(s); });
        if (created.size() != 1) throw new GameTestAssertException("command must create exactly one Sable UUID owner; found " + created.size());
        if (!(created.get(0) instanceof ServerSubLevel serverSub))
            throw new GameTestAssertException("fixture build command did not create a server sublevel");
        var fixture = new CommandBuiltCabinFixture(level, container, serverSub,
                ShuttleManualFixture.WIDTH, ShuttleManualFixture.HEIGHT, ShuttleManualFixture.DEPTH);
        helper.testInfo.addListener(new GameTestListener() {
            public void testStructureLoaded(GameTestInfo i) { }
            public void testPassed(GameTestInfo i, GameTestRunner r) { fixture.close(); }
            public void testFailed(GameTestInfo i, GameTestRunner r) { fixture.close(); }
            public void testAddedForRerun(GameTestInfo o, GameTestInfo n, GameTestRunner r) { fixture.close(); }
        });
        return fixture;
    }

    @Override public void close() {
        if (closed) return;
        closed = true;
        if (!sub.isRemoved()) {
            SableFixturePlot.removeDisposableFootprint(level, sub, sub.getPlot().getCenterBlock(),
                    width, height, depth);
            container.removeSubLevel(sub, dev.ryanhcode.sable.sublevel.storage.SubLevelRemovalReason.REMOVED);
        }
    }
}
