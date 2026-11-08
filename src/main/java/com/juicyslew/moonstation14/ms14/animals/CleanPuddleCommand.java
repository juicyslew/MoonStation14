package com.juicyslew.moonstation14.ms14.animals;

import com.juicyslew.moonstation14.MoonStation14;
import com.juicyslew.moonstation14.ms14.player_body_control.action.BodyActionAuthority;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Mob;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

/** Controlled Moproach mop action against the canonical currently-visible puddle. */
@EventBusSubscriber(modid = MoonStation14.MOD_ID)
public final class CleanPuddleCommand {
    private CleanPuddleCommand() { }
    @SubscribeEvent public static void register(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("cleanpuddle").executes(ctx -> {
            var source = ctx.getSource();
            if (!(source.getEntity() instanceof ServerPlayer player) || player instanceof FakePlayer
                    || player.getServer() == null || !player.getServer().isSameThread()
                    || player.getServer().getPlayerList().getPlayer(player.getUUID()) != player) return 0;
            var authority = BodyActionAuthority.resolve(player).orElse(null);
            if (authority == null || !(authority.body() instanceof Mob mob)
                    || !BodyActionAuthority.revalidate(player, authority)
                    || AbsorbentCleaningService.cleanLookingAt(mob, mob.level().getGameTime(),
                    () -> BodyActionAuthority.revalidate(player, authority)) != AbsorbentCleaningService.Result.SUCCESS) {
                source.sendFailure(Component.literal("Your controlled body cannot clean that puddle now."));
                return 0;
            }
            source.sendSuccess(() -> Component.literal("Your body absorbs the puddle."), false);
            return 1;
        }));
    }
}
