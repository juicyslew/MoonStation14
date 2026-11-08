package com.juicyslew.moonstation14.ms14.combat;

import com.juicyslew.moonstation14.MoonStation14;
import com.juicyslew.moonstation14.ms14.prototype.PrototypeType;
import net.minecraft.resources.ResourceLocation;

public final class ModBlockStructuralContacts {
    public static final PrototypeType<BlockStructuralContactData> TYPE = new PrototypeType<>(
            ResourceLocation.fromNamespaceAndPath(MoonStation14.MOD_ID, "block_structural_contact"),
            "moonstation14/block_structural_contact", BlockStructuralContactData.CODEC,
            catalog -> {
                var seen = new java.util.HashSet<String>();
                for (var entry : catalog.values())
                    if (!seen.add(entry.block())) throw new IllegalArgumentException("duplicate block structural contact mapping: " + entry.block());
            });
    private ModBlockStructuralContacts() { }
}
