package com.juicyslew.moonstation14.ms14.status_effect;

import com.juicyslew.moonstation14.block.ModBlocks;
import com.juicyslew.moonstation14.block.block_entity.PuddleBlockEntity;
import com.juicyslew.moonstation14.component.codec.json.StatusEffectData;
import com.juicyslew.moonstation14.ms14.MS14Bridges;
import com.juicyslew.moonstation14.ms14.MS14Provider;
import com.juicyslew.moonstation14.ms14.TraitHandler;
import com.juicyslew.moonstation14.ms14.reagent.IReagentTrait;
import com.juicyslew.moonstation14.ms14.reagent.ReagentAttachment;
import com.juicyslew.moonstation14.ms14.reagent.ReagentComponent;
import com.juicyslew.moonstation14.util.SystemLink;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

import static com.juicyslew.moonstation14.util.MapOperations.getTotal;

public class StatusEffectSystem {

    // --- CORE LOGIC ---
    public static SystemLink<StatusEffectAttachment, StatusEffectComponent> bridge = MS14Bridges.STATUS_EFFECT;

    public static void addTime(TraitHandler<IStatusEffectTrait> source, Level level, ResourceKey<StatusEffectData> statusEffect, float amount) {
        if (level.isClientSide) return;

        StatusEffectAttachment srcCont = MS14Provider.get(source.holder(), bridge);
        var map = srcCont.getMap();
        map.put(statusEffect, map.getOrDefault(statusEffect, 0f) + amount);


        MS14Provider.update(source.holder(), bridge, srcCont);
    }

    public static void removeTime(TraitHandler<IStatusEffectTrait> source, Level level, ResourceKey<StatusEffectData> statusEffect, float amount) {
        if (level.isClientSide) return;

        StatusEffectAttachment srcCont = MS14Provider.get(source.holder(), bridge);
        var map = srcCont.getMap();
        map.put(statusEffect, map.getOrDefault(statusEffect, 0f) - amount);


        MS14Provider.update(source.holder(), bridge, srcCont);
    }

    public static void setTime(TraitHandler<IStatusEffectTrait> source, Level level, ResourceKey<StatusEffectData> statusEffect, float amount) {
        if (level.isClientSide) return;

        StatusEffectAttachment srcCont = MS14Provider.get(source.holder(), bridge);
        var map = srcCont.getMap();
        map.put(statusEffect, amount);

        MS14Provider.update(source.holder(), bridge, srcCont);
    }
}
