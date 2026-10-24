package com.juicyslew.moonstation14.ms14.hands.live;

import com.juicyslew.moonstation14.component.ModDataAttachments;
import com.juicyslew.moonstation14.ms14.hands.ItemToken;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import java.util.Optional;
import java.util.function.BooleanSupplier;

/** Exact, body-owned LiveHands stack access and component-only publication. */
public final class LiveOwnedItemAccess {
    public enum Location { HELD, EQUIPMENT }
    public record Binding(Location location, String slotId, long revision, String token) {
        public boolean valid() { return location != null && slotId != null && !slotId.isBlank() && revision >= 0
                && token != null && !token.isBlank(); }
    }
    public record OwnerView(LivingEntity body, LiveHands hands, Binding binding, ItemStack stack) {
        public OwnerView { stack = stack.copy(); }
        @Override public ItemStack stack() { return stack.copy(); }
    }
    public record Committed(LiveHands state, long revision) { }
    private LiveOwnedItemAccess() { }

    /** Pure defensive snapshot constructor used by the real owner view and focused value tests. */
    public static OwnerView snapshot(LivingEntity body, LiveHands hands, Binding binding, ItemStack stack) {
        return new OwnerView(body, hands, binding, stack);
    }
    /** Exact component commit shape, shared by production publication and policy tests. */
    public static boolean validComponentReplacement(ItemStack expected, ItemStack replacement) {
        return expected != null && !expected.isEmpty() && replacement != null && !replacement.isEmpty()
                && expected.is(replacement.getItem()) && expected.getCount() == replacement.getCount();
    }

    public static Optional<OwnerView> read(LivingEntity body, Binding binding) {
        if (body == null || binding == null || !binding.valid() || !(body.level() instanceof ServerLevel)
                || body.level().getServer() == null || !body.level().getServer().isSameThread()) return Optional.empty();
        LiveHands hands = body.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get());
        if (hands == null || !hands.compatible(body) || hands.revision() != binding.revision()) return Optional.empty();
        Optional<String> token = binding.location() == Location.HELD ? hands.token(binding.slotId())
                : LiveHands.equipmentSnapshot(body, hands).stream().flatMap(s -> s.slots().stream())
                        .filter(s -> s.id().equals(binding.slotId())).flatMap(s -> s.token().stream()).findFirst();
        ItemStack stack = (binding.location() == Location.HELD ? hands.stackCopy(binding.slotId())
                : hands.equipmentStack(binding.slotId())).orElse(ItemStack.EMPTY);
        if (stack.isEmpty() || !token.filter(binding.token()::equals).isPresent()) return Optional.empty();
        return Optional.of(snapshot(body, hands, binding, stack));
    }

    public static Optional<Committed> commitComponents(OwnerView view, ItemStack replacement, BooleanSupplier stillValid) {
        if (view == null || replacement == null || replacement.isEmpty() || stillValid == null) return Optional.empty();
        LivingEntity body = view.body(); LiveHands hands = view.hands(); Binding binding = view.binding();
        if (!(body.level() instanceof ServerLevel) || body.level().getServer() == null || !body.level().getServer().isSameThread()
                || body.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get()) != hands || !hands.compatible(body)
                || !binding.valid() || hands.revision() != binding.revision() || !stillValid.getAsBoolean()) return Optional.empty();
        ItemStack expected = view.stack();
        ItemStack current = (binding.location() == Location.HELD ? hands.stackCopy(binding.slotId())
                : hands.equipmentStack(binding.slotId())).orElse(ItemStack.EMPTY);
        Optional<String> token = binding.location() == Location.HELD ? hands.token(binding.slotId())
                : LiveHands.equipmentSnapshot(body, hands).stream().flatMap(s -> s.slots().stream())
                        .filter(s -> s.id().equals(binding.slotId())).flatMap(s -> s.token().stream()).findFirst();
        if (current.isEmpty() || !ItemStack.isSameItemSameComponents(current, expected) || current.getCount() != expected.getCount()
                || !token.filter(binding.token()::equals).isPresent() || !validComponentReplacement(expected, replacement)) return Optional.empty();
        ItemToken itemToken = new ItemToken(binding.token());
        LiveHands candidate = (binding.location() == Location.HELD
                ? hands.replaceOwnedComponents(binding.revision(), binding.slotId(), itemToken, replacement)
                : hands.replaceEquipmentOwnedComponents(binding.revision(), binding.slotId(), itemToken, replacement)).orElse(null);
        if (candidate == null) return Optional.empty();
        try { LiveHandsCommitGateway.commitOwned(body, hands, candidate); }
        catch (IllegalStateException rejected) { return Optional.empty(); }
        return Optional.of(new Committed(candidate, candidate.revision()));
    }
}
