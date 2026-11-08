package com.juicyslew.moonstation14.ms14.atmos.machine.ui.client;

import com.juicyslew.moonstation14.ms14.atmos.machine.ui.*;
import com.juicyslew.moonstation14.ms14.ui.client.pointer.PointerOverlayRegistry;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.neoforged.neoforge.network.PacketDistributor;

/** Client presentation/lifecycle owner for admitted server atmos sessions. */
public final class AtmosMachineWorkspace {
    public static final AtmosMachineWorkspace INSTANCE=new AtmosMachineWorkspace();
    private final Map<UUID, AtmosMachineOverlay> windows=new LinkedHashMap<>();
    private AtmosMachineWorkspace(){}
    public void open(AtmosMachineSessionState state){
        if(state.open()){
            var old=windows.get(state.session()); if(old!=null)return;
            var mc=Minecraft.getInstance();var w=new AtmosMachineOverlay(this,state.session(),mc.getWindow().getGuiScaledWidth(),mc.getWindow().getGuiScaledHeight());
            windows.put(state.session(),w);PointerOverlayRegistry.register(w);PointerOverlayRegistry.bringToFront(w);
            com.juicyslew.moonstation14.ms14.hands.client.BodyHandClient.openSharedPointerWorkspace();
        }else closeLocal(state.session());
    }
    public void snapshot(AtmosMachineControlSnapshot s){var w=windows.get(s.session());if(w!=null)w.snapshot(s);}
    void closed(UUID id){PacketDistributor.sendToServer(new AtmosMachineSessionClose(id));closeLocal(id);}
    void closeLocal(UUID id){var w=windows.remove(id);if(w!=null){w.clearTextFocus();PointerOverlayRegistry.unregister(w);if(windows.isEmpty())com.juicyslew.moonstation14.ms14.hands.client.BodyHandClient.analyzerWindowClosed();}}
    public void clear(){for(var w:windows.values()){w.resetOwnedContext();PointerOverlayRegistry.unregister(w);}windows.clear();com.juicyslew.moonstation14.ms14.hands.client.BodyHandClient.analyzerWindowClosed();}
}
