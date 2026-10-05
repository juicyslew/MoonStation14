package com.juicyslew.moonstation14.ms14.atmos.piping.alarm.ui;

import com.juicyslew.moonstation14.block.ModMenus;
import com.juicyslew.moonstation14.ms14.atmos.piping.alarm.AirAlarmBlock;
import com.juicyslew.moonstation14.ms14.atmos.piping.alarm.AirAlarmBlockEntity;
import com.juicyslew.moonstation14.ms14.hands.HandActorAuthority;
import com.juicyslew.moonstation14.ms14.hands.network.BodyBlockUseRequest;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.FullChunkStatus;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

import java.util.UUID;

/** The menu is an exact actor/body/epoch/alarm capability, not an independently usable block coordinate. */
public final class AirAlarmMenu extends AbstractContainerMenu {
    private boolean invalidReported;
    private final BlockPos pos;
    private final UUID session;
    private final ServerLevel level;
    private final AirAlarmBlockEntity alarm;
    private final HandActorAuthority.Snapshot authority;
    private final BodyBlockUseRequest request;

    public AirAlarmMenu(int id, Inventory inventory, FriendlyByteBuf data) {
        this(id, data.readBlockPos(), data.readUUID(), null, null, null, null);
    }

    AirAlarmMenu(int id, BlockPos pos, UUID session, ServerLevel level, AirAlarmBlockEntity alarm,
                 HandActorAuthority.Snapshot authority, BodyBlockUseRequest request) {
        super(ModMenus.AIR_ALARM.get(), id);
        this.pos = pos.immutable(); this.session = session; this.level = level;
        this.alarm = alarm; this.authority = authority; this.request = request;
    }

    public UUID session() { return session; }
    AirAlarmBlockEntity alarm() { return alarm; }
    ServerLevel serverLevel() { return level; }

    @Override public boolean stillValid(Player player) {
        // The client-side menu has no server capability fields; vanilla may still query it locally.
        if (level == null) return true;
        AirAlarmMenuService.Validity failure;
        if (alarm == null || authority == null || request == null || player != authority.actor() || player.level() != level)
            failure = AirAlarmMenuService.Validity.AUTHORITY;
        else if (!level.getWorldBorder().isWithinBounds(pos) || !level.getWorldBorder().isWithinBounds(authority.body().getEyePosition()))
            failure = AirAlarmMenuService.Validity.OUT_OF_REACH;
        else failure = AirAlarmMenuService.persistentValidity(authority.actor(), level, alarm, authority, this);
        if (failure == AirAlarmMenuService.Validity.VALID) return true;
        if (!invalidReported) {
            invalidReported = true;
            var meter = alarm == null ? null : com.juicyslew.moonstation14.ms14.power.runtime.PowerRuntime.airAlarmMeter(level, alarm);
            org.slf4j.LoggerFactory.getLogger(AirAlarmMenu.class).warn("Air alarm menu invalid reason={} alarmPos={} session={} bodyEpoch={} meterKnown={} deliveredWatts={} alarmLoaded={}",
                    AirAlarmMenuService.reason(failure), pos, session, authority == null ? -1 : authority.epoch(),
                    meter != null && meter.known(), meter == null ? -1 : meter.deliveredWatts(),
                    alarm != null && !alarm.isRemoved());
            player.sendSystemMessage(net.minecraft.network.chat.Component.literal("Air alarm closed: " + AirAlarmMenuService.reason(failure)));
        }
        return false;
    }

    @Override public ItemStack quickMoveStack(Player player, int index) { return ItemStack.EMPTY; }
    @Override public void removed(Player player) {
        super.removed(player);
        AirAlarmMenuService.closed(this);
    }
}
