package com.juicyslew.moonstation14.ms14;

/**
 * @param holder The object that HAS the data (ItemStack, BlockEntity, etc.)
 * @param trait  The object that HAS the logic/parameters (The Item, The Block, etc.)
 * @param <T>    The specific trait interface (e.g., IReagentTrait)
 *
 * NOTE: holder and trait are the same for BlockEntities and LivingEntities, but different for items (stack and item)
 */
public record TraitHandler<T>(Object holder, T trait) {
}