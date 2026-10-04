package com.juicyslew.moonstation14.ms14.disposal.ui;

import com.juicyslew.moonstation14.block.ModBlocks;
import com.juicyslew.moonstation14.block.ModMenus;
import com.juicyslew.moonstation14.component.ModDataAttachments;
import com.juicyslew.moonstation14.item.ModItems;
import com.juicyslew.moonstation14.ms14.disposal.world.DisposalTubeWorldData;
import com.juicyslew.moonstation14.ms14.disposal.domain.DisposalOrientation;
import com.juicyslew.moonstation14.ms14.hands.HandActorAuthority;
import com.juicyslew.moonstation14.ms14.hands.network.BodyBlockUseRequest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.FullChunkStatus;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;

import java.util.UUID;

/** Server capability bound to the admitting actor, body epoch, and exposed host cell. */
public final class DisposalBuildMenu extends AbstractContainerMenu {
    private static final UUID NO_TUBE = new UUID(0, 0);
    private final BlockPos pos;
    private final UUID session;
    private final ServerLevel level;
    private final HandActorAuthority.Snapshot authority;
    private final BodyBlockUseRequest opening;
    private final boolean editing;
    private final UUID tubeId;
    private long tubeRevision;
    private Direction tubeDirection;
    private Direction tubeUp;
    private final boolean tubeAnchored;
    private Direction selectedDirection = Direction.NORTH;
    private final ContainerData resultData;
    private String routingTag = "", routingFilter = "";

    public DisposalBuildMenu(int id, Inventory inventory, FriendlyByteBuf buf) {
        this(id, buf.readBlockPos(), buf.readUUID(), buf.readBoolean(), buf.readUUID(), buf.readVarLong(),
                buf.readEnum(Direction.class), buf.readEnum(Direction.class), buf.readBoolean(), buf.readUtf(64), buf.readUtf(128), null, null, null, new SimpleContainerData(197));
    }

    DisposalBuildMenu(int id, BlockPos pos, UUID session, ServerLevel level,
                      HandActorAuthority.Snapshot authority, BodyBlockUseRequest opening) {
        this(id, pos, session, false, NO_TUBE, 0, Direction.NORTH, Direction.UP, false, "", "", level, authority, opening,
                new SimpleContainerData(197));
    }

    DisposalBuildMenu(int id, BlockPos pos, UUID session, UUID tubeId, long revision, DisposalOrientation frame,
                      boolean anchored, String tag, String filter, ServerLevel level, HandActorAuthority.Snapshot authority,
                      BodyBlockUseRequest opening) {
        this(id, pos, session, true, tubeId, revision, frame.forward(), frame.up(), anchored, tag, filter, level, authority, opening,
                new SimpleContainerData(197));
    }

    private DisposalBuildMenu(int id, BlockPos pos, UUID session, boolean editing, UUID tubeId,
                               long tubeRevision, Direction tubeDirection, Direction tubeUp, boolean tubeAnchored, String tag, String filter, ServerLevel level,
                              HandActorAuthority.Snapshot authority, BodyBlockUseRequest opening,
                              ContainerData resultData) {
        super(ModMenus.DISPOSAL_BUILD.get(), id);
        this.pos = pos.immutable();
        this.session = session;
        this.editing = editing;
        this.tubeId = tubeId;
        this.tubeRevision = tubeRevision;
        this.tubeDirection = tubeDirection;
        this.tubeUp = tubeUp;
        this.tubeAnchored = tubeAnchored;
        this.routingTag = tag == null ? "" : tag;
        this.routingFilter = filter == null ? "" : filter;
        this.level = level;
        this.authority = authority;
        this.opening = opening;
        this.resultData = resultData;
        resultData.set(1, tubeDirection.ordinal());
        resultData.set(196, tubeUp.ordinal());
        writeRouting();
        addDataSlots(resultData);
    }

    public UUID session() { return session; }
    BodyBlockUseRequest openingRequest() { return opening; }
    public HandActorAuthority.Snapshot authority() { return authority; }
    public BlockPos pos() { return pos; }
    public boolean editing() { return editing; }
    public boolean tubeAnchored() { return tubeAnchored; }
    UUID tubeId() { return tubeId; }
    long tubeRevision() { return tubeRevision; }
    public UUID configuredTubeId() { return tubeId; }
    public long configuredTubeRevision() { return tubeRevision; }
    public Direction tubeDirection() {
        return level == null ? Direction.values()[Math.floorMod(resultData.get(1), Direction.values().length)] : tubeDirection;
    }
    public DisposalOrientation tubeFrame() { return new DisposalOrientation(tubeDirection(), tubeUp); }
    public Direction selectedDirection() { return selectedDirection; }
    public String actionResult() {
        return switch (resultData.get(0)) {
            case 1 -> editing ? "Rotation started" : "Construction started";
            case 2 -> "Construction complete";
            case 3 -> editing ? "Rotation cancelled" : "Construction cancelled";
            case 4 -> "Construction failed";
            case 5 -> "Need 2 steel";
            case 6 -> "Target unavailable";
            case 7 -> "Tube must be unanchored";
            case 8 -> "Rotation updated";
            case 9 -> "Routing updated";
            case 10 -> "Invalid tag or filter";
            default -> editing ? "Select rotation" : "Select disposal tube";
        };
    }

    public void result(String value) {
        resultData.set(0, switch (value) {
            case "Construction started", "Rotation started" -> 1;
            case "Rotation cancelled" -> 3;
            case "Construction complete" -> 2;
            case "Construction cancelled", "Materials changed" -> 3;
            case "Need 2 steel" -> 5;
            case "Target unavailable", "Target changed" -> 6;
            case "Tube must be unanchored" -> 7;
            case "Rotation updated" -> 8;
            case "Routing updated" -> 9;
            case "Invalid tag or filter" -> 10;
            default -> 4;
        });
    }

    public String routingTag() { return readRouting(2, 3); }
    public String routingFilter() { return readRouting(67, 68); }
    void routingUpdated(UUID expectedId, long revision, String tag, String filter) {
        if (!editing || !tubeId.equals(expectedId)) return;
        tubeRevision = revision;
        routingTag = tag;
        routingFilter = filter;
        writeRouting();
    }
    private String readRouting(int lengthIndex, int start) {
        if (level != null) return lengthIndex == 2 ? routingTag : routingFilter;
        int count = Math.max(0, Math.min(lengthIndex == 2 ? 64 : 128, resultData.get(lengthIndex)));
        StringBuilder text = new StringBuilder(count);
        for (int i=0;i<count;i++) text.append((char)resultData.get(start+i));
        return text.toString();
    }
    private void writeRouting() {
        writeRoutingString(2, 3, routingTag, 64);
        writeRoutingString(67, 68, routingFilter, 128);
    }
    private void writeRoutingString(int lengthIndex, int start, String value, int max) {
        resultData.set(lengthIndex, Math.min(value.length(), max));
        for (int i=0;i<max;i++) resultData.set(start+i, i < value.length() ? value.charAt(i) : 0);
    }

    public void rotated(UUID expectedId, long expectedRevision, DisposalOrientation frame) {
        if (!editing || !tubeId.equals(expectedId)) return;
        tubeRevision = expectedRevision;
        tubeDirection = frame.forward();
        tubeUp = frame.up();
        resultData.set(1, frame.forward().ordinal());
        resultData.set(196, frame.up().ordinal());
    }

    @Override public void setData(int index, int value) {
        super.setData(index, value);
        if (level == null && (index >= 0 && index < 2 || index == 196)) {
            resultData.set(index, value);
            if(index==196)tubeUp=Direction.values()[Math.floorMod(value,Direction.values().length)];
        }
    }

    void select(String prototype, Direction direction) { selectedDirection = direction; }

    @Override public boolean stillValid(Player player) {
        if (level == null) return true;
        if (player != authority.actor() || player.level() != level
                || !HandActorAuthority.revalidateInMenu(authority.actor(), authority, this)
                || !level.getWorldBorder().isWithinBounds(pos)) return false;
        var chunk = level.getChunkSource().getChunkNow(pos.getX() >> 4, pos.getZ() >> 4);
        if (chunk == null || !chunk.getFullStatus().isOrAfter(FullChunkStatus.FULL)) return false;
        BlockState state = chunk.getBlockState(pos);
        if (editing) {
            if (!state.is(ModBlocks.DISPOSAL_TUBE.get()) && !com.juicyslew.moonstation14.ms14.atmos.piping.SealedWallHostEligibility.defaults().eligible(level,pos)) return false;
            var data = DisposalTubeWorldData.existing(level).orElse(null);
            if (data == null) return false;
            var lookup = data.lookup(level, pos);
            return lookup.kind() == DisposalTubeWorldData.LookupKind.PRESENT && lookup.record() != null
                    && lookup.record().id().equals(tubeId) && lookup.record().revision() == tubeRevision
                    && lookup.record().anchored() == tubeAnchored;
        }
        var hands = authority.body().getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get());
        return hands != null && hands.revision() == opening.expectedRevision()
                && opening.handId().equals(hands.activeHand())
                && hands.token(opening.handId()).filter(opening.expectedToken()::equals).isPresent()
                && hands.stackCopy(opening.handId()).map(stack -> stack.is(ModItems.STEEL.get())).orElse(false);
    }

    @Override public ItemStack quickMoveStack(Player player, int index) { return ItemStack.EMPTY; }
}
