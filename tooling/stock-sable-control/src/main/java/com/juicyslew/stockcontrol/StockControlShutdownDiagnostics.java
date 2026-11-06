package com.juicyslew.stockcontrol;

import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.GameTestServer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import org.slf4j.LoggerFactory;

import java.util.concurrent.CountDownLatch;

/** Opt-in, bounded shutdown-stack diagnostics for this exact isolated GameTest server. */
@EventBusSubscriber(modid = "sable_control", bus = EventBusSubscriber.Bus.GAME)
public final class StockControlShutdownDiagnostics {
    private static final org.slf4j.Logger LOG = LoggerFactory.getLogger("StockSableControl");
    private static GameTestServer owner;
    private static CountDownLatch stopped;
    private static boolean captureStarted;

    private StockControlShutdownDiagnostics() { }

    public static synchronized void arm(GameTestHelper helper) {
        if (!(helper.getLevel().getServer() instanceof GameTestServer server)) return;
        if (owner != null && owner != server) return;
        if (owner == server) return;
        owner = server;
        stopped = new CountDownLatch(1);
        LOG.info("[stock-control shutdown stack] ARM server={} threadId={}",
                GameTestShutdownStackCapture.bounded(server.toString(), 256), Thread.currentThread().getId());
    }

    @SubscribeEvent
    public static synchronized void serverStopping(ServerStoppingEvent event) {
        if (owner == null || event.getServer() != owner || captureStarted) return;
        captureStarted = true;
        Thread serverThread = Thread.currentThread();
        CountDownLatch serverStopped = stopped;
        LOG.info("[stock-control shutdown stack] STOPPING serverThreadId={} isServerThread={}",
                serverThread.getId(), event.getServer().isSameThread());
        Thread diagnostic = new Thread(
                () -> GameTestShutdownStackCapture.capture(serverThread, serverStopped, LOG),
                "StockControl-shutdown-stack-diagnostic");
        diagnostic.setDaemon(true);
        diagnostic.start();
    }

    @SubscribeEvent
    public static synchronized void serverStopped(ServerStoppedEvent event) {
        if (owner != null && event.getServer() == owner) stopped.countDown();
    }
}
