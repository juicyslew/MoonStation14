package com.juicyslew.moonstation14.ms14.chemistry.chemmaster.interaction;

import java.util.function.Consumer;

/** Classloading-safe callback bridge; server/network registration owns delivery. */
public final class ChemMasterDumpClientReceiver {
    private static Consumer<ChemMasterDumpResult> handler = ignored -> { };
    private ChemMasterDumpClientReceiver() { }
    public static void install(Consumer<ChemMasterDumpResult> callback) { handler = callback == null ? ignored -> { } : callback; }
    public static void handle(ChemMasterDumpResult result) { if (result != null) handler.accept(result); }
}

