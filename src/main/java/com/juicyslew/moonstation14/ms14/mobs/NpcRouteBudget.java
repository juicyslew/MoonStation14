package com.juicyslew.moonstation14.ms14.mobs;

import net.minecraft.server.MinecraftServer;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;

/** The single per-server-tick work budget shared by ground and air route searches. */
final class NpcRouteBudget {
    static final int MAX_CALLS = 12;
    static final int MAX_EXPANSIONS = 7_000;
    private static MinecraftServer server;
    private static int tick = Integer.MIN_VALUE, calls, expansions;
    static {
        NeoForge.EVENT_BUS.addListener((ServerStoppedEvent event) -> reset(event.getServer()));
    }
    private NpcRouteBudget() { }
    static boolean begin(MinecraftServer owner) {
        if (server != owner || tick != owner.getTickCount()) {
            server = owner; tick = owner.getTickCount(); calls = 0; expansions = 0;
        }
        if (calls >= MAX_CALLS || expansions >= MAX_EXPANSIONS) return false;
        calls++;
        return true;
    }
    static int remainingExpansions() { return Math.max(0, MAX_EXPANSIONS - expansions); }
    static void expanded(int count) { if (count > 0) expansions = Math.min(MAX_EXPANSIONS, expansions + count); }
    static int calls(MinecraftServer owner) { return server == owner && tick == owner.getTickCount() ? calls : 0; }
    static int expansions(MinecraftServer owner) { return server == owner && tick == owner.getTickCount() ? expansions : 0; }
    private static void reset(MinecraftServer owner) {
        if (server == owner) { server = null; tick = Integer.MIN_VALUE; calls = expansions = 0; }
    }
}
