package com.juicyslew.moonstation14.ms14.animals;

import com.juicyslew.moonstation14.component.ModDataAttachments;
import com.juicyslew.moonstation14.ms14.character.CharacterIdentitySystem;
import com.juicyslew.moonstation14.ms14.character.components.UdderComponent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;

/** Stateful production owner for prototype-resolved animal udders. */
public final class UdderSystem {
    private UdderSystem() { }

    public static java.util.Optional<UdderComponent> capability(LivingEntity entity) {
        if (entity == null || entity.isRemoved()) return java.util.Optional.empty();
        return CharacterIdentitySystem.resolveForActor(entity).flatMap(data -> data.component(UdderComponent.class));
    }

    /** Called by the server living-tick owner; state is persisted on the animal and never regenerated on read. */
    public static void tick(LivingEntity entity) {
        if (!(entity.level() instanceof ServerLevel level) || level.getServer() == null
                || !level.getServer().isSameThread() || entity.isRemoved() || !entity.isAlive()) return;
        var policy = capability(entity).orElse(null);
        if (policy == null) return;
        long now = level.getGameTime();
        UdderAttachment state = entity.getData(ModDataAttachments.UDDER.get());
        if (state.nextGrowthTick() == 0) {
            entity.setData(ModDataAttachments.UDDER.get(), new UdderAttachment(state.available(), now + delayTicks(policy), policy.solutionName()));
            return;
        }
        if (!state.solutionName().equals(policy.solutionName())) return;
        if (now < state.nextGrowthTick() || state.available() >= policy.solutionCapacity()) return;
        // SS14 consumes nutrition only when the animal has a hunger system. No hunger component means no cost.
        var hunger = entity.getExistingDataOrNull(ModDataAttachments.HUNGER.get());
        var hungerPolicy = CharacterIdentitySystem.resolveForActor(entity)
                .flatMap(data -> data.component(com.juicyslew.moonstation14.ms14.character.components.HungerPrototypeComponent.class))
                .orElse(null);
        if (hunger != null && hungerPolicy != null && hunger.hunger() < hungerPolicy.okay()) {
            entity.setData(ModDataAttachments.UDDER.get(), new UdderAttachment(state.available(), now + delayTicks(policy)));
            return;
        }
        if (hunger != null) hunger.setHunger(Math.max(0, hunger.hunger() - policy.hungerUsage()));
        entity.setData(ModDataAttachments.UDDER.get(), new UdderAttachment(
                Math.min(policy.solutionCapacity(), state.available() + policy.quantity()), now + delayTicks(policy), policy.solutionName()));
    }

    public static long delayTicks(UdderComponent component) {
        return Math.max(1L, (long) Math.ceil(component.growthDelaySeconds() * 20));
    }

    private static final String MILK_OPERATION = "udder_milking";
    private static final net.minecraft.resources.ResourceKey<com.juicyslew.moonstation14.component.codec.json.ReagentData> MILK =
            com.juicyslew.moonstation14.ms14.reagent.ModReagents.createKey("milk");

    /** Start SS14-style five second milking. Caller has already resolved authoritative body-use intent. */
    public static boolean startMilking(net.minecraft.server.level.ServerPlayer actor, LivingEntity body,
            LivingEntity target, String hand, String token, long revision, long sequence) {
        if (actor == null || body == null || target == null || hand == null || token == null
                || !(body.level() instanceof ServerLevel level) || target.level() != level
                || capability(target).isEmpty() || !com.juicyslew.moonstation14.ms14.hands.live.BodyInteractionAccess.inRange(body,target,2.5)) return false;
        var authority = com.juicyslew.moonstation14.ms14.hands.HandActorAuthority.resolve(actor).orElse(null);
        if (authority == null || authority.body() != body) return false;
        var hands = body.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get());
        if (hands == null || hands.revision() != revision || !hands.activeHand().equals(hand)
                || !hands.token(hand).filter(token::equals).isPresent()) return false;
        var stack = hands.stackCopy(hand).orElse(null);
        if (stack == null || !(stack.getItem() instanceof com.juicyslew.moonstation14.ms14.reagent.IReagentTrait trait)) return false;
        var refill = trait.refillableSolution(stack, level);
        var transfer = trait.solutionTransfer(stack, level);
        if (refill == null || !refill.enabled() || transfer == null || !transfer.enabled() || !transfer.canReceive()) return false;
        if (!trait.solutionName(stack, level).equals(refill.solutionName())) return false;
        var targetUuid = target.getUUID();
        var source = new Source(target, targetUuid);
        var sourceState = target.getExistingDataOrNull(ModDataAttachments.UDDER.get());
        if (sourceState == null || sourceState.available() <= 0) return false;
        var targetId = net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getKey(target.getType()) + "/" + targetUuid;
        var start = com.juicyslew.moonstation14.ms14.timed_interaction.TimedInteractionSystem.start(
                body, actor, sequence, MILK_OPERATION, targetId, 100, true, true, 1.0, 1.0,
                () -> 100,
                () -> com.juicyslew.moonstation14.ms14.hands.HandActorAuthority.revalidate(actor, authority)
                        && level.getEntity(targetUuid) == target && target.isAlive()
                        && com.juicyslew.moonstation14.ms14.hands.live.BodyInteractionAccess.inRange(body,target,2.5)
                        && sameHeld(body, hand, token, revision, stack),
                () -> {
                    var result = com.juicyslew.moonstation14.ms14.hands.live.BodyLiquidInteraction.transferEntitySource(
                            actor, authority, hand, token, revision, source);
                    return result == com.juicyslew.moonstation14.ms14.hands.live.BodyLiquidInteraction.Result.SUCCESS
                            ? com.juicyslew.moonstation14.ms14.timed_interaction.TimedInteractionSystem.Completion.STOP
                            : com.juicyslew.moonstation14.ms14.timed_interaction.TimedInteractionSystem.Completion.DENIED;
                }, java.util.Optional.empty(), () -> { });
        return start != com.juicyslew.moonstation14.ms14.timed_interaction.TimedInteractionEngine.StartResult.DENIED;
    }

    private static boolean sameHeld(LivingEntity body, String hand, String token, long revision, net.minecraft.world.item.ItemStack original) {
        var now = body.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get());
        return now != null && now.revision() == revision && now.activeHand().equals(hand)
                && now.token(hand).filter(token::equals).isPresent()
                && now.stackCopy(hand).filter(s -> s.getCount() == original.getCount()
                    && net.minecraft.world.item.ItemStack.isSameItemSameComponents(s, original)).isPresent();
    }

    private static final class Source implements com.juicyslew.moonstation14.ms14.hands.live.BodyLiquidInteraction.EntitySolutionSource {
        private final LivingEntity entity; private final java.util.UUID uuid;
        private Source(LivingEntity entity, java.util.UUID uuid) { this.entity=entity; this.uuid=uuid; }
        private UdderAttachment state() { return entity.getExistingDataOrNull(ModDataAttachments.UDDER.get()); }
        private String solutionName() { return capability(entity).map(UdderComponent::solutionName).orElse(""); }
        @Override public com.juicyslew.moonstation14.ms14.reagent.ReagentAttachment snapshot() {
            var state=state(); return state == null || !state.solutionName().equals(solutionName()) ? null : new com.juicyslew.moonstation14.ms14.reagent.ReagentAttachment(
                    state.available() <= 0 ? java.util.Map.of() : java.util.Map.of(MILK, state.available()));
        }
        @Override public boolean valid() { return entity.isAlive() && !entity.isRemoved() && capability(entity).isPresent()
                && entity.level() instanceof ServerLevel level && level.getEntity(uuid)==entity
                && state() != null && state().solutionName().equals(solutionName()); }
        @Override public boolean matches(com.juicyslew.moonstation14.ms14.reagent.ReagentAttachment expected) {
            return valid() && snapshot().snapshotUnits().equals(expected.snapshotUnits());
        }
        @Override public boolean replace(com.juicyslew.moonstation14.ms14.reagent.ReagentAttachment expected,
                com.juicyslew.moonstation14.ms14.reagent.ReagentAttachment replacement) {
            if (!matches(expected)) return false;
            float amount = replacement.getMap().getOrDefault(MILK, 0f);
            UdderAttachment before = state();
            entity.setData(ModDataAttachments.UDDER.get(), before.withAvailable(amount));
            return snapshot().snapshotUnits().equals(replacement.snapshotUnits());
        }
    }
}
