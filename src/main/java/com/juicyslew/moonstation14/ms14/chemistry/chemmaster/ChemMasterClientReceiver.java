package com.juicyslew.moonstation14.ms14.chemistry.chemmaster;

import java.util.Objects;
import java.util.function.Consumer;

/** Dedicated-server-safe receiver bridge; it has no client or Minecraft UI references. */
public final class ChemMasterClientReceiver {
    private static volatile Consumer<ChemMasterWindowState> receiver=ignored->{ };
    private ChemMasterClientReceiver() { }
    public static void install(Consumer<ChemMasterWindowState> handler){receiver=Objects.requireNonNull(handler);}
    public static void handle(ChemMasterWindowState state){receiver.accept(Objects.requireNonNull(state));}
}

