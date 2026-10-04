package com.juicyslew.moonstation14.ms14.disposal.unit.ui;

import com.juicyslew.moonstation14.block.ModMenus;
import com.juicyslew.moonstation14.ms14.disposal.unit.DisposalUnitBlockEntity;
import com.juicyslew.moonstation14.ms14.disposal.containment.DisposalContainmentService;
import com.juicyslew.moonstation14.ms14.hands.HandActorAuthority;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.FullChunkStatus;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.item.ItemStack;
import java.util.UUID;

/** Body authority-bound controls for one exact disposal unit; deliberately has no inventory slots. */
public final class DisposalUnitMenu extends AbstractContainerMenu {
    private final BlockPos pos;
    private final UUID unitId, session;
    private final ServerLevel level;
    private final HandActorAuthority.Snapshot authority;
    private final ContainerData data;
    public DisposalUnitMenu(int id, Inventory inventory, FriendlyByteBuf buf) {
        this(id, buf.readBlockPos(), buf.readUUID(), buf.readUUID(), null, null, new SimpleContainerData(3));
    }
    DisposalUnitMenu(int id, BlockPos pos, UUID unitId, UUID session, ServerLevel level,
                     HandActorAuthority.Snapshot authority, ContainerData data) {
        super(ModMenus.DISPOSAL_UNIT.get(), id); this.pos=pos.immutable(); this.unitId=unitId; this.session=session;
        this.level=level; this.authority=authority; this.data=data; addDataSlots(data);
    }
    public UUID session(){return session;} public BlockPos pos(){return pos;}
    public UUID unitId(){return unitId;}
    public int state(){return data.get(0);} public int result(){return data.get(1);} public int bodies(){return data.get(2);}
    void update(int state,int result,int bodies){data.set(0,state);data.set(1,result);data.set(2,bodies);}
    public boolean stillValid(Player player) {
        if(level==null) return true;
        if(player!=authority.actor()||player.level()!=level||!HandActorAuthority.revalidateInMenu(authority.actor(),authority,this)) return false;
        var chunk=level.getChunkSource().getChunkNow(pos.getX()>>4,pos.getZ()>>4);
        if(chunk==null||!chunk.getFullStatus().isOrAfter(FullChunkStatus.FULL)
                || !(chunk.getBlockEntity(pos) instanceof DisposalUnitBlockEntity unit)
                || !unit.identity().equals(unitId)||!unit.isValid()) return false;
        var owner = DisposalContainmentService.forLevel(level).ownerOf(authority.body());
        if (owner.isPresent()) return owner.orElseThrow() == unit && unit.bodyIds().contains(authority.body().getUUID());
        return authority.body().distanceToSqr(pos.getX()+.5,pos.getY()+.5,pos.getZ()+.5)<=64;
    }
    HandActorAuthority.Snapshot authority(){return authority;}
    @Override public ItemStack quickMoveStack(Player player,int index){return ItemStack.EMPTY;}
}
