package com.juicyslew.moonstation14.block.custom;

import com.juicyslew.moonstation14.ms14.atmos.world.AtmosphereBlockPolicy;
import com.juicyslew.moonstation14.ms14.construction.prototype.ModConstruction;
import com.juicyslew.moonstation14.ms14.prototype.PrototypeRuntime;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;

/** Physical Minecraft structure whose explicit local prototype also defines its atmospheric policy. */
public final class ConstructionStructureBlock extends Block implements AtmosphereBlockPolicy {
    private final String entityPrototype;
    public ConstructionStructureBlock(String entityPrototype, BlockBehaviour.Properties properties) {
        super(properties);
        if (entityPrototype == null || entityPrototype.isBlank()) throw new IllegalArgumentException("blank construction entity prototype");
        this.entityPrototype = entityPrototype;
    }
    @Override public boolean atmospherePassable() {
        var data = PrototypeRuntime.serverConstructionEntities().get(ModConstruction.id(entityPrototype));
        return data != null && !data.airtight();
    }
}

