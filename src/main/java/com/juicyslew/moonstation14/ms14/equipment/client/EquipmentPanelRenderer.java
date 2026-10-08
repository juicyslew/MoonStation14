package com.juicyslew.moonstation14.ms14.equipment.client;

import com.juicyslew.moonstation14.ms14.equipment.InventoryTemplateData;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;

/** Shared dark HUD slot chrome and SS14 slot art drawing primitives. Item
 * presentation is intentionally supplied by the caller's native item renderer. */
public final class EquipmentPanelRenderer {
    public enum ToggleArrow { SHOW, HIDE }
    private EquipmentPanelRenderer() { }
    public static ToggleArrow toggleArrow(boolean expanded) { return expanded ? ToggleArrow.HIDE : ToggleArrow.SHOW; }
    public static void drawSlot(GuiGraphics g, EquipmentPanelLayout.SlotRect slot,
                                InventoryTemplateData.Slot definition, boolean enabled,
                                boolean pendingOrStale) {
        var r = slot.rect();
        g.fill(r.x(), r.y(), r.x()+r.width(), r.y()+r.height(), 0xff11191d);
        g.fill(r.x()+1,r.y()+1,r.x()+r.width()-1,r.y()+r.height()-1,
                pendingOrStale ? 0xff554733 : 0xff35474e);
        ResourceLocation icon = definition.icon();
        g.blit(icon,r.x()+2,r.y()+2,r.width()-4,r.height()-4,0,0,32,32,32,32);
        if (!enabled) g.fill(r.x()+1,r.y()+1,r.x()+r.width()-1,r.y()+r.height()-1,0x66090f12);
    }
    public static void drawToggle(GuiGraphics g, EquipmentPanelLayout.Layout layout, boolean expanded) {
        var r=layout.toggleRect();
        g.fill(r.x(),r.y(),r.x()+r.width(),r.y()+r.height(),0xff11191d);
        int x=r.x()+2, y=r.y()+2, w=r.width()-4, h=r.height()-4;
        var texture=ResourceLocation.fromNamespaceAndPath("moonstation14","textures/gui/equipment/inventory_toggle.png");
        if (toggleArrow(expanded) == ToggleArrow.HIDE) {
            // SS14 ships one toggle arrow. Mirror its direction for the opposite action;
            // this is a UI-state adaptation, not a second upstream asset.
            // Keep destination geometry positively wound (GUI faces may be culled), and
            // reverse only the source UV endpoints to mirror the upstream sprite.
            g.blit(texture,x,y,w,h,32,0,-32,32,32,32);
        } else g.blit(texture,x,y,w,h,0,0,32,32,32,32);
    }
}
