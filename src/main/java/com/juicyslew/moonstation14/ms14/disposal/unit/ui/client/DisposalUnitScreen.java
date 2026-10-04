package com.juicyslew.moonstation14.ms14.disposal.unit.ui.client;

import com.juicyslew.moonstation14.ms14.disposal.unit.ui.DisposalUnitAction;
import com.juicyslew.moonstation14.ms14.disposal.unit.ui.DisposalUnitMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.neoforged.neoforge.network.PacketDistributor;

/** Inventory-free unit control panel. */
public final class DisposalUnitScreen extends AbstractContainerScreen<DisposalUnitMenu>
        implements com.juicyslew.moonstation14.ms14.player_body_control.client.BodyMovementIntentScreen {
    private final com.juicyslew.moonstation14.ms14.hands.client.PopupMovementIntent movement = new com.juicyslew.moonstation14.ms14.hands.client.PopupMovementIntent();
    public DisposalUnitScreen(DisposalUnitMenu menu,Inventory inventory,Component title){super(menu,inventory,title);imageWidth=210;imageHeight=140;}
    @Override protected void init(){super.init();
        button("Climb inside",DisposalUnitAction.Operation.CLIMB_INSIDE,8,30);
        button("Engage",DisposalUnitAction.Operation.ENGAGE,108,30);
        button("Power",DisposalUnitAction.Operation.POWER,8,56);
        button("Eject",DisposalUnitAction.Operation.EJECT,108,56);
    }
    private void button(String label,DisposalUnitAction.Operation op,int x,int y){addRenderableWidget(Button.builder(Component.literal(label),b->PacketDistributor.sendToServer(new DisposalUnitAction(menu.containerId,menu.session(),op))).bounds(leftPos+x,topPos+y,92,20).build());}
    @Override public com.juicyslew.moonstation14.ms14.hands.client.PopupMovementIntent.State movementIntent(){
        var mc=net.minecraft.client.Minecraft.getInstance();long window=mc.getWindow().getWindow();
        boolean focused=mc.screen==this
                &&org.lwjgl.glfw.GLFW.glfwGetWindowAttrib(window,org.lwjgl.glfw.GLFW.GLFW_FOCUSED)==org.lwjgl.glfw.GLFW.GLFW_TRUE;
        if(!focused)return movement.sample(false,false,false,false,false,false,false,false);
        var o=mc.options;return movement.sample(true,held(o.keyUp,window),held(o.keyDown,window),held(o.keyLeft,window),held(o.keyRight,window),held(o.keyJump,window),held(o.keyShift,window),held(o.keySprint,window));
    }
    private static boolean held(net.minecraft.client.KeyMapping mapping,long window){
        var bound=mapping.getKey();
        if(bound.getType()==com.mojang.blaze3d.platform.InputConstants.Type.MOUSE)return false;
        int key=bound.getType()==com.mojang.blaze3d.platform.InputConstants.Type.SCANCODE?scanKey(bound.getValue()):bound.getValue();
        return key>=org.lwjgl.glfw.GLFW.GLFW_KEY_SPACE&&key<=org.lwjgl.glfw.GLFW.GLFW_KEY_LAST
                &&org.lwjgl.glfw.GLFW.glfwGetKey(window,key)==org.lwjgl.glfw.GLFW.GLFW_PRESS;
    }
    private static int scanKey(int scan){for(int key=org.lwjgl.glfw.GLFW.GLFW_KEY_SPACE;key<=org.lwjgl.glfw.GLFW.GLFW_KEY_LAST;key++)if(org.lwjgl.glfw.GLFW.glfwGetKeyScancode(key)==scan)return key;return -1;}
    @Override protected void renderBg(GuiGraphics g,float partial,int mx,int my){g.fill(leftPos,topPos,leftPos+imageWidth,topPos+imageHeight,0xFF20242A);}
    @Override protected void renderLabels(GuiGraphics g,int mx,int my){g.drawString(font,"Disposal unit",8,8,0xFFFFFF);String state=menu.state()==1?"Powered":"Unpowered";g.drawString(font,state+"  Bodies: "+menu.bodies(),8,88,0xDDDDDD);g.drawString(font,switch(menu.result()){case 1->"Entry pending";case 2->"Entered";case 3->"Entry interrupted";case 4->"Entry failed";default->"Ready";},8,108,0xAAAAAA);}
}
