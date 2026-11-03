package com.juicyslew.moonstation14.ms14.combat;

import com.juicyslew.moonstation14.ms14.construction.prototype.ConstructionEntityData;
import com.juicyslew.moonstation14.ms14.damage.DamageModifierData;
import com.juicyslew.moonstation14.ms14.prototype.PrototypeCatalog;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.Block;
import java.util.Map;

/** Shared block contact eligibility, resolving construction mappings first and explicit block mappings second. */
public final class StructuralContactResolver {
    private StructuralContactResolver() { }

    public static ConstructionEntityData.StructuralContactResult evaluate(Block block, MeleeWeaponData attack,
            PrototypeCatalog<ConstructionEntityData> construction,
            PrototypeCatalog<BlockStructuralContactData> contacts) {
        if (block instanceof com.juicyslew.moonstation14.block.custom.ConstructionStructureBlock structure) {
            ConstructionEntityData data = construction.get(structure.entityPrototypeId());
            if (data == null || data.structuralContact().isEmpty() || !data.block().equals(key(block)))
                return ConstructionEntityData.StructuralContactResult.NOT_OPTED_IN;
            return data.evaluateStructuralContact(attack);
        }
        BlockStructuralContactData descriptor = uniqueMapping(contacts, key(block));
        if (descriptor == null) return ConstructionEntityData.StructuralContactResult.NOT_OPTED_IN;
        return ConstructionEntityData.evaluateAdjustedContact(DamageModifierData.apply(attack.damage(), descriptor.structuralContact()));
    }

    /** The map is attack damage already composed with attack-wide scales/bonuses, before block modifiers. */
    public static ConstructionEntityData.StructuralContactResult evaluate(Block block, Map<String, Float> composedDamage,
            PrototypeCatalog<ConstructionEntityData> construction,
            PrototypeCatalog<BlockStructuralContactData> contacts) {
        if (block instanceof com.juicyslew.moonstation14.block.custom.ConstructionStructureBlock structure) {
            ConstructionEntityData data = construction.get(structure.entityPrototypeId());
            if (data == null || data.structuralContact().isEmpty() || !data.block().equals(key(block)))
                return ConstructionEntityData.StructuralContactResult.NOT_OPTED_IN;
            if (data.structuralContact().isEmpty()) return ConstructionEntityData.StructuralContactResult.NOT_OPTED_IN;
            return data.evaluateStructuralContact(DamageModifierData.apply(composedDamage, data.structuralContact().get()));
        }
        BlockStructuralContactData descriptor = uniqueMapping(contacts, key(block));
        if (descriptor == null) return ConstructionEntityData.StructuralContactResult.NOT_OPTED_IN;
        return ConstructionEntityData.evaluateAdjustedContact(DamageModifierData.apply(composedDamage, descriptor.structuralContact()));
    }
    private static BlockStructuralContactData uniqueMapping(PrototypeCatalog<BlockStructuralContactData> contacts, String block) {
        BlockStructuralContactData match = null;
        for (BlockStructuralContactData candidate : contacts.values()) {
            if (!candidate.block().equals(block)) continue;
            if (match != null) throw new IllegalStateException("ambiguous structural contact mapping for " + block);
            match = candidate;
        }
        return match;
    }
    private static String key(Block block) { return BuiltInRegistries.BLOCK.getKey(block).toString(); }
}
