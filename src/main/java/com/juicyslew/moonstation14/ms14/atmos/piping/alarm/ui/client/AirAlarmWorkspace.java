package com.juicyslew.moonstation14.ms14.atmos.piping.alarm.ui.client;

import com.juicyslew.moonstation14.ms14.atmos.piping.alarm.ui.*;
import com.juicyslew.moonstation14.ms14.ui.client.pointer.PointerOverlayRegistry;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.neoforged.neoforge.network.PacketDistributor;

/** Client owner for server-admitted, nonmodal Air Alarm sessions. */
public final class AirAlarmWorkspace {
    public static final AirAlarmWorkspace INSTANCE = new AirAlarmWorkspace();
    private final Map<UUID, AirAlarmOverlay> windows = new LinkedHashMap<>();
    private AirAlarmWorkspace() { }
    public void open(AirAlarmSessionState state) {
        if (!state.open()) { closeLocal(state.session()); return; }
        if (windows.containsKey(state.session())) return;
        var mc = Minecraft.getInstance();
        var overlay = new AirAlarmOverlay(this, state.session(), mc.getWindow().getGuiScaledWidth(), mc.getWindow().getGuiScaledHeight());
        windows.put(state.session(), overlay); PointerOverlayRegistry.register(overlay); PointerOverlayRegistry.bringToFront(overlay);
        com.juicyslew.moonstation14.ms14.hands.client.BodyHandClient.openSharedPointerWorkspace();
    }
    public void snapshot(AirAlarmControlSnapshot snapshot) { var w=windows.get(snapshot.session()); if(w!=null)w.snapshot(snapshot); }
    void close(UUID session) { PacketDistributor.sendToServer(new AirAlarmSessionClose(session)); closeLocal(session); }
    void closeLocal(UUID session) {
        var w=windows.remove(session); if(w==null)return;
        w.clearTextFocus(); PointerOverlayRegistry.unregister(w);
        if(windows.isEmpty())com.juicyslew.moonstation14.ms14.hands.client.BodyHandClient.analyzerWindowClosed();
    }
    public void clear(){for(var w:windows.values()){w.resetOwnedContext();PointerOverlayRegistry.unregister(w);}windows.clear();com.juicyslew.moonstation14.ms14.hands.client.BodyHandClient.analyzerWindowClosed();}
}
