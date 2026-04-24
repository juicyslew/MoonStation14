package com.juicyslew.moonstation14;

import com.juicyslew.moonstation14.block.ModBlocks;
import com.juicyslew.moonstation14.block.block_entity.JugBlockEntity;
import com.juicyslew.moonstation14.block.block_entity.PuddleBlockEntity;
import com.juicyslew.moonstation14.component.ModDataAttachments;
import com.juicyslew.moonstation14.component.ModDataComponents;
import com.juicyslew.moonstation14.entities.ModEntities;
import com.juicyslew.moonstation14.ms14.reagent.ReagentComponent;
import com.juicyslew.moonstation14.item.ModItems;
import com.juicyslew.moonstation14.util.ModSpecialProperties;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.ThrownItemRenderer;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterColorHandlersEvent;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;

// This class will not load on dedicated servers. Accessing client side code from here is safe.
@Mod(value = MoonStation14.MOD_ID, dist = Dist.CLIENT)
// You can use EventBusSubscriber to automatically register all static methods in the class annotated with @SubscribeEvent
@EventBusSubscriber(modid = MoonStation14.MOD_ID, value = Dist.CLIENT)
public class MoonStation14Client {
    public MoonStation14Client(ModContainer container) {
        // Allows NeoForge to create a config screen for this mod's configs.
        // The config screen is accessed by going to the Mods screen > clicking on your mod > clicking on config.
        // Do not forget to add translations for your config options to the en_us.json file.
        container.registerExtensionPoint(IConfigScreenFactory.class, ConfigurationScreen::new);
    }

    @SubscribeEvent
    static void onClientSetup(FMLClientSetupEvent event) {
        ModSpecialProperties.addCustomItemProperties(event);
        // SET ALL BLOCK RENDERTYPES THAT NEED TO BE TRANSPARENT
    }

    @SubscribeEvent
    public static void registerItemColors(RegisterColorHandlersEvent.Item event) {
        event.register((stack, tintIndex) -> {
            if (tintIndex == 1) {
                ReagentComponent data = stack.get(ModDataComponents.REAGENT.get());
                if (data != null){
                    // This is overkill. Technically BlendedData could be retrieved JUST from map data (i.e. read-only)
                    return ReagentComponent.getBlendedColor(data.contents(), Minecraft.getInstance().level);
                }
            }
            return -1; // Default (no tint)
        }, ModItems.BOTTLE.get());

        event.register((stack, tintIndex) -> {
            if (tintIndex == 1) {
                ReagentComponent data = stack.get(ModDataComponents.REAGENT.get());
                if (data != null){
                    // This is overkill. Technically BlendedData could be retrieved JUST from map data (i.e. read-only)
                    return ReagentComponent.getBlendedColor(data.contents(), Minecraft.getInstance().level);
                }
            }
            return -1; // Default (no tint)
        }, ModItems.JUG.get());
    }

    @SubscribeEvent
    public static void registerBlockColors(RegisterColorHandlersEvent.Block event) {
        event.register((state, level, pos, tintIndex) -> {
            if (tintIndex == 0){
                if (level != null && pos != null && level.getBlockEntity(pos) instanceof JugBlockEntity jug) {
                    var container = jug.getData(ModDataAttachments.REAGENT.get());
                    return ReagentComponent.getBlendedColor(container.getMap(), Minecraft.getInstance().level);
                }
            }
            return -1; // Default
        }, ModBlocks.JUG.get());
        event.register((state, level, pos, tintIndex) -> {
            if (tintIndex == 0){
                if (level != null && pos != null && level.getBlockEntity(pos) instanceof PuddleBlockEntity puddle) {
                    var container = puddle.getData(ModDataAttachments.REAGENT.get());
                    return ReagentComponent.getBlendedColor(container.getMap(), Minecraft.getInstance().level);
                }
            }
            return -1; // Default
        }, ModBlocks.PUDDLE.get());
    }

    @SubscribeEvent
    public static void onRegisterRenderers(EntityRenderersEvent.RegisterRenderers event) {
        // This is the simplest 2D renderer. It just draws the Item's icon.
        event.registerEntityRenderer(ModEntities.THROWN_JUG.get(), ThrownItemRenderer::new);
    }
}
