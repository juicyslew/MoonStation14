package com.juicyslew.moonstation14.ms14.combat.throwing;

import com.juicyslew.moonstation14.component.ModDataComponents;
import com.juicyslew.moonstation14.ms14.blood.BloodstreamSolutionAccess;
import com.juicyslew.moonstation14.ms14.combat.ChemicalInjectionDose;
import com.juicyslew.moonstation14.ms14.equipment.MeleeInjectionProtectionPolicy;
import com.juicyslew.moonstation14.ms14.equipment.ResolvedWornEquipment;
import com.juicyslew.moonstation14.ms14.reagent.ReagentAttachment;
import com.juicyslew.moonstation14.entities.ItemAttachment;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;

/** Transfers the committed dose of a configured embedded item into its exact attached target. */
@EventBusSubscriber
public final class ThrownItemInjectionAdapter {
    private ThrownItemInjectionAdapter() { }

    @SubscribeEvent
    public static void onEmbedded(ThrownItemEmbeddedEvent event) {
        var item = event.item();
        var target = event.target();
        var config = event.profile().injectOnEmbed().orElse(null);
        if (config == null || !(item.level() instanceof ServerLevel level)
                || level.getServer() == null || !level.getServer().isSameThread()
                || item.level() != target.level() || item.isRemoved() || !item.isAddedToLevel()
                || level.getEntity(item.getUUID()) != item || !validTarget(item, target, level)) return;
        var attachment = item.attachment().orElse(null);
        if (attachment == null || attachment.kind() != ItemAttachment.AttachmentKind.EMBEDDED
                || !attachment.targetUUID().equals(target.getUUID())) return;
        var worn = ResolvedWornEquipment.entries(target);
        if (config.blockSlots().stream().anyMatch(slot -> worn.stream().anyMatch(e -> e.slotFlags().contains(slot)))) return;
        if (MeleeInjectionProtectionPolicy.protects(worn, config.pierceArmor())) return;
        var snapshot = BloodstreamSolutionAccess.inspect(target).orElse(null);
        if (snapshot == null) return;
        ItemStack source = item.getItem();
        if (source == null || source.isEmpty()) return;
        ItemStack expected = source.copy();
        var prepared = ChemicalInjectionDose.prepare(expected, level, config.policy(), 1).orElse(null);
        if (prepared == null || item.isRemoved() || item.getItem() != source || !ItemStack.matches(source, expected)
                || !item.isAddedToLevel() || level.getEntity(item.getUUID()) != item || !validTarget(item, target, level)
                || item.attachment().filter(a -> a.kind() == ItemAttachment.AttachmentKind.EMBEDDED
                    && a.targetUUID().equals(target.getUUID())).isEmpty()) return;
        try { item.setItem(prepared.remainder()); }
        catch (RuntimeException denied) { return; }
        if (!ItemStack.matches(item.getItem(), prepared.remainder())) return;
        var delivered = efficientDose(prepared.detachedDose(), config.transferEfficiency());
        if (!delivered.isEmpty()) BloodstreamSolutionAccess.tryAddWholeReacting(snapshot, delivered);
    }

    private static boolean validTarget(com.juicyslew.moonstation14.entities.LooseItemEntity item,
                                       net.minecraft.world.entity.LivingEntity target, ServerLevel level) {
        if (target.level() != level || target.isRemoved() || !target.isAddedToLevel()
                || level.getEntity(target.getUUID()) != target) return false;
        return item.attachment().filter(a -> a.kind() == ItemAttachment.AttachmentKind.EMBEDDED
                && a.targetEntityId() == target.getId() && a.targetUUID().equals(target.getUUID())).isPresent();
    }

    static java.util.Map<net.minecraft.resources.ResourceKey<com.juicyslew.moonstation14.component.codec.json.ReagentData>, Long> efficientDose(
            java.util.Map<net.minecraft.resources.ResourceKey<com.juicyslew.moonstation14.component.codec.json.ReagentData>, Long> dose, float efficiency) {
        if (dose == null || dose.isEmpty() || !Float.isFinite(efficiency) || efficiency <= 0) return java.util.Map.of();
        if (efficiency >= 1) return java.util.Map.copyOf(dose);
        ReagentAttachment portion = new ReagentAttachment(com.juicyslew.moonstation14.ms14.reagent.ReagentComponent.fromCents(dose));
        long deliveredCents = (long) Math.floor(portion.totalUnits() * (double) efficiency);
        if (deliveredCents <= 0) return java.util.Map.of();
        return java.util.Map.copyOf(portion.splitUnits(deliveredCents));
    }
}
