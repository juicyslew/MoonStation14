package com.juicyslew.moonstation14.ms14.botany;

import com.juicyslew.moonstation14.component.ModDataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.ItemStack;

/** World-only appearance projection of the captured harvest potency. */
public final class ProduceWorldAppearance {
    private ProduceWorldAppearance() { }

    public static float scale(ItemStack stack) {
        var instance=stack.get(ModDataComponents.PRODUCE_INSTANCE.get());
        if(instance==null) return 1f;
        var id=BuiltInRegistries.ITEM.getKey(stack.getItem());
        if(id==null) return 1f;
        var profile=ModProduceProfiles.clientCatalog().get(id);
        if(profile==null || profile.potencyVisuals()==null) return 1f;
        var bounds=profile.potencyVisuals();
        float potency=instance.baselinePotency();
        return bounds.minimumScale()+(bounds.maximumScale()-bounds.minimumScale())*(potency/100f);
    }
}
