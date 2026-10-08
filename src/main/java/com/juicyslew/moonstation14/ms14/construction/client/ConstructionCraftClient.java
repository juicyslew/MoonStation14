package com.juicyslew.moonstation14.ms14.construction.client;

import com.juicyslew.moonstation14.ms14.hands.client.BodyHandClient;
import java.util.function.Consumer;
import com.juicyslew.moonstation14.ms14.construction.network.ConstructionCraftState;

/** Client request callback; authority and inventory proof come from the current body snapshot. */
public final class ConstructionCraftClient {
    private static volatile Consumer<ConstructionCraftState> window;
    private ConstructionCraftClient() { }
    public static void bindWindow(Consumer<ConstructionCraftState> value){window=value;ConstructionCraftState.setClientHandler(ConstructionCraftClient::state);}
    public static void request(String recipe,int quantity,boolean cancel){
        BodyHandClient.requestConstructionCraft(recipe,quantity,cancel);
    }
    private static void state(ConstructionCraftState state){var target=window;if(target!=null)target.accept(state);}
}
