package com.juicyslew.moonstation14.item.custom;

import com.juicyslew.moonstation14.component.ModDataComponents;
import com.juicyslew.moonstation14.ms14.botany.ModProduceProfiles;
import com.juicyslew.moonstation14.ms14.botany.ProduceInstanceData;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/** Reuses the established edible/reagent trait while allowing harvest-specific capacity. */
public final class ProduceEdibleItem extends PrototypeEdibleItem {
    public ProduceEdibleItem(Properties properties) { super(properties); }
    @Override protected com.juicyslew.moonstation14.ms14.ingestion.IngestionProfile.Edible.SourceSolution prototypeSource(Level level) {
        var inherited = super.prototypeSource(level);
        if (inherited != null) return inherited;
        var catalog = level != null && level.isClientSide ? ModProduceProfiles.clientCatalog() : ModProduceProfiles.serverCatalog();
        var profile = catalog.get(BuiltInRegistries.ITEM.getKey(this));
        return profile == null ? null : profile.sourceSolution();
    }
    @Override public float getCapacity() {
        var source = prototypeSource(null);
        return source == null ? 0f : source.capacityCents() / 100f;
    }
    @Override public float getCapacity(ItemStack stack, Level level) {
        ProduceInstanceData data = stack.get(ModDataComponents.PRODUCE_INSTANCE.get());
        if (data != null) return data.generatedCapacityCents() / 100f;
        return super.getCapacity(stack, level);
    }
}
