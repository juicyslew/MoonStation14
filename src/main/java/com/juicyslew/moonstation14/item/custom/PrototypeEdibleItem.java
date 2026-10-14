package com.juicyslew.moonstation14.item.custom;

import com.juicyslew.moonstation14.component.ModDataComponents;
import com.juicyslew.moonstation14.ms14.ingestion.IngestionProfile;
import com.juicyslew.moonstation14.ms14.ingestion.ModIngestionProfiles;
import com.juicyslew.moonstation14.ms14.reagent.IReagentTrait;
import com.juicyslew.moonstation14.ms14.reagent.ReagentComponent;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/** Generic item-side adapter for edible/source fields of a resolved ingestion prototype. */
public class PrototypeEdibleItem extends Item implements IReagentTrait {
    public PrototypeEdibleItem(Properties properties) { super(properties); }

    private IngestionProfile profile() {
        return ModIngestionProfiles.serverLookup(BuiltInRegistries.ITEM.getKey(this));
    }

    /** Materializes a prototype source only while the stack has never acquired reagent state. */
    public static void initializeSource(ItemStack stack, Level level) {
        if (stack.isEmpty() || stack.get(ModDataComponents.REAGENT.get()) != null
                || !(stack.getItem() instanceof PrototypeEdibleItem item)) return;
        // Provenance without its reagent component is malformed/partial saved state. Never
        // reconstruct it from a seed or prototype and thereby refill depleted product.
        if (stack.get(ModDataComponents.PRODUCE_INSTANCE.get()) != null) {
            stack.set(ModDataComponents.REAGENT.get(), new ReagentComponent());
            return;
        }
        var source = item.prototypeSource(level);
        if (source == null) return;
        var contents = new java.util.LinkedHashMap<ResourceKey<com.juicyslew.moonstation14.component.codec.json.ReagentData>, Long>();
        source.contentsCents().forEach((reagent, cents) -> contents.put(
                ResourceKey.create(com.juicyslew.moonstation14.ms14.reagent.ModReagents.REAGENT_REGISTRY_KEY, reagent), cents));
        stack.set(ModDataComponents.REAGENT.get(), ReagentComponent.fromCents(contents));
    }

    protected IngestionProfile.Edible.SourceSolution prototypeSource(Level level) {
        ResourceLocation id = BuiltInRegistries.ITEM.getKey(this);
        IngestionProfile profile = level != null && level.isClientSide
                ? ModIngestionProfiles.clientLookup(id) : ModIngestionProfiles.serverLookup(id);
        return profile == null ? null : profile.edible().sourceSolution();
    }

    @Override public float getCapacity() {
        IngestionProfile resolved = profile();
        return resolved != null && resolved.edible().sourceSolution() != null
                ? resolved.edible().sourceSolution().capacityCents() / 100f : 0f;
    }

    @Override public float getCapacity(ItemStack stack, Level level) {
        var source = prototypeSource(level);
        return source == null ? 0f : source.capacityCents() / 100f;
    }

}
