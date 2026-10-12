package com.juicyslew.moonstation14.ms14.hands.client;

import com.juicyslew.moonstation14.ms14.storage.grid.GridGeometry;
import com.juicyslew.moonstation14.ms14.storage.grid.ItemFootprints;
import com.juicyslew.moonstation14.ms14.hands.network.BodyHandActionRequest;
import com.juicyslew.moonstation14.ms14.hands.network.BodyHandStateSnapshot;
import com.juicyslew.moonstation14.ms14.hands.network.BodyWorldCrossAction;
import com.juicyslew.moonstation14.ms14.hands.network.BodyWorldGridSnapshot;
import net.minecraft.resources.ResourceLocation;

import java.util.UUID;
import com.juicyslew.moonstation14.ms14.hands.network.ItemPresentation;

/** A consumed screen binding dispatches at most once per physical press. */
final class PopupActionPlan {
    enum Action { NONE, POINTER, DROP, SELECT, BAG, BELT }
    enum GridIntent { CANCEL, TAKE, MOVE, STORE }

    static GridIntent gridIntent(StoragePopupWorkspace.Drag drag, boolean fromHand, boolean moved, int button) {
        if (button != 0 || drag == null) return GridIntent.CANCEL;
        if (fromHand) return GridIntent.STORE;
        boolean unchanged = drag.oldAnchor().equals(drag.proposedAnchor())
                && drag.oldRotation() == drag.proposedRotation();
        return unchanged ? moved ? GridIntent.CANCEL : GridIntent.TAKE : GridIntent.MOVE;
    }

    static boolean allowWorldGridFallback(StoragePopupWorkspace.HostKey source,
                                          StoragePopupWorkspace.HostKey target) {
        return target == null || target.kind() != StoragePopupWorkspace.HostKind.WORLD || target.equals(source);
    }

    static boolean moved(double startX, double startY, double x, double y) {
        return (x - startX) * (x - startX) + (y - startY) * (y - startY) >= 16;
    }

    static GridGeometry.Cell grabbedAnchor(GridGeometry.Cell pointer, int offsetX, int offsetY) {
        return new GridGeometry.Cell(pointer.x() - offsetX, pointer.y() - offsetY);
    }

    /** Proof captured at mouse-down; a later snapshot cannot silently replace the dragged child. */
    record CrossSource(UUID account, UUID body, long epoch, long revision,
                       StoragePopupWorkspace.HostKey host, String hostId, String child, String itemId, int count,
                       GridGeometry.Cell oldAnchor, GridGeometry.Rotation oldRotation, ItemPresentation presentation) {
        CrossSource(UUID account, UUID body, long epoch, long revision, StoragePopupWorkspace.HostKey host, String hostId,
                    String child, String itemId, int count, GridGeometry.Cell oldAnchor, GridGeometry.Rotation oldRotation) {
            this(account, body, epoch, revision, host, hostId, child, itemId, count, oldAnchor, oldRotation, null);
        }
    }

    /** Immutable mouse-down proof; the world nonce and both revisions cannot be substituted at release. */
    record WorldCrossSource(UUID account, UUID body, long epoch, long bodyRevision,
                            UUID worldTarget, UUID session, long worldRevision,
                             StoragePopupWorkspace.HostKey host, String hostId, String child,
                             String itemId, int count, GridGeometry.Cell oldAnchor,
                             GridGeometry.Rotation oldRotation, ItemPresentation presentation) {
        WorldCrossSource(UUID account, UUID body, long epoch, long bodyRevision, UUID worldTarget, UUID session,
                         long worldRevision, StoragePopupWorkspace.HostKey host, String hostId, String child,
                         String itemId, int count, GridGeometry.Cell oldAnchor, GridGeometry.Rotation oldRotation) {
            this(account, body, epoch, bodyRevision, worldTarget, session, worldRevision, host, hostId, child,
                    itemId, count, oldAnchor, oldRotation, null);
        }
    }

    record WorldCrossPlan(BodyWorldCrossAction.Direction direction, UUID target, boolean worn,
                          String hostId, String hostToken, String child, GridGeometry.Cell oldAnchor,
                          GridGeometry.Rotation oldRotation, GridGeometry.Cell newAnchor,
                           GridGeometry.Rotation newRotation) { }
    record WorldWorldPlan(UUID source, UUID destination, String child, GridGeometry.Cell oldAnchor,
                          GridGeometry.Rotation oldRotation, GridGeometry.Cell anchor,
                          GridGeometry.Rotation rotation) { }

    static WorldWorldPlan worldWorldPlan(WorldCrossSource captured, WorldGridClientPolicy.GridView source,
            WorldGridClientPolicy.GridView destination, BodyHandStateSnapshot proof,
            GridGeometry.Cell anchor, GridGeometry.Rotation rotation, boolean actionable, int button) {
        if (button != 0 || captured == null || source == null || destination == null || proof == null
                || anchor == null || rotation == null || !actionable || source.stale() || destination.stale()
                || source.target().equals(destination.target()) || source.session().equals(destination.session())
                || !captured.worldTarget().equals(source.target()) || !captured.session().equals(source.session())
                || captured.worldRevision() != source.hostRevision() || proof.reason() != BodyHandStateSnapshot.Reason.OK
                || !captured.account().equals(proof.accountId()) || !captured.body().equals(proof.bodyId())
                || captured.epoch() != proof.epoch() || captured.bodyRevision() != proof.revision()
                || proof.revision() != source.handRevision() || proof.revision() != destination.handRevision()) return null;
        var child = source.entries().stream().filter(e -> e.token().equals(captured.child())).findFirst().orElse(null);
        if (child == null || !child.itemId().equals(captured.itemId()) || child.count() != captured.count()
                || child.x() != captured.oldAnchor().x() || child.y() != captured.oldAnchor().y()
                || child.rotation() != captured.oldRotation().degrees() || destination.entries().size() >= GridGeometry.MAX_ENTRIES
                || destination.entries().stream().anyMatch(e -> e.token().equals(captured.child()))) return null;
        var id = ResourceLocation.tryParse(captured.itemId());
        if (id == null || !StoragePopupWorkspace.preview(destination.geometry(), null,
                ItemFootprints.clientForId(id), anchor, rotation, true).maySendAction()) return null;
        return new WorldWorldPlan(source.target(), destination.target(), child.token(), captured.oldAnchor(),
                captured.oldRotation(), anchor, rotation);
    }

    static WorldCrossSource worldCrossSource(BodyHandStateSnapshot proof, WorldGridClientPolicy.GridView world,
                                              BodyWorldGridSnapshot.Entry entry) {
        if (proof == null || proof.reason() != BodyHandStateSnapshot.Reason.OK || world == null
                || world.stale() || proof.revision() != world.handRevision() || entry == null
                || !world.entries().contains(entry)) return null;
        return new WorldCrossSource(proof.accountId(), proof.bodyId(), proof.epoch(), proof.revision(),
                world.target(), world.session(), world.hostRevision(), StoragePopupWorkspace.HostKey.world(world.target()),
                null, entry.token(), entry.itemId(), entry.count(), new GridGeometry.Cell(entry.x(), entry.y()),
                GridGeometry.Rotation.values()[entry.rotation() / 90], entry.presentation());
    }

    static StoragePopupWorkspace.Window worldWorldTarget(StoragePopupWorkspace workspace, WorldCrossSource source,
                                                           double x, double y) {
        if (workspace == null || source == null || source.host().kind() != StoragePopupWorkspace.HostKind.WORLD) return null;
        var top = workspace.topmost(x, y);
        if (top == null || top.provisional() || top.key().kind() != StoragePopupWorkspace.HostKind.WORLD
                || top.key().equals(source.host())) return null;
        var world = BodyHandClient.worldGrid(top.key().worldId());
        if (world == null) return null;
        var layout = new StoragePopupLayout(StoragePopupLayout.Kind.WORLD, top.x(), top.y(), world.width(), world.height());
        return layout.visibleCellAt((int) x, (int) y, top.width(), top.height()) == null ? null : top;
    }

    static WorldCrossSource bodyWorldSource(BodyHandStateSnapshot proof, WorldGridClientPolicy.GridView world,
                                             CrossSource source) {
        if (source == null || proof == null || world == null || world.stale()
                || proof.revision() != world.handRevision()) return null;
        var grid = explicitGrid(proof, source.host());
        var entry = grid == null ? null : grid.entries().stream()
                .filter(e -> e.token().equals(source.child())).findFirst().orElse(null);
        if (entry == null || !source.equals(crossSource(proof, source.host(), entry))) return null;
        return new WorldCrossSource(source.account(), source.body(), source.epoch(), source.revision(),
                world.target(), world.session(), world.hostRevision(), source.host(), source.hostId(),
                source.child(), source.itemId(), source.count(), source.oldAnchor(), source.oldRotation(), source.presentation());
    }

    /** The top window's outer frame blocks every lower grid, including a world or the source itself. */
    static StoragePopupWorkspace.Window worldCrossTarget(StoragePopupWorkspace workspace, WorldCrossSource source,
                                                           double x, double y) {
        if (workspace == null || source == null) return null;
        var top = workspace.topmost(x, y);
        if (top == null || top.provisional() || top.key().equals(source.host())
                || source.host().kind() == StoragePopupWorkspace.HostKind.WORLD && !bodyHost(top.key())
                || source.host().kind() != StoragePopupWorkspace.HostKind.WORLD
                && (top.key().kind() != StoragePopupWorkspace.HostKind.WORLD
                || !source.worldTarget().equals(top.key().worldId()))) return null;
        int columns = top.key().kind() == StoragePopupWorkspace.HostKind.BELT ? 8
                : top.width() >= StoragePopupLayout.outerWidth(8) ? 8 : 7;
        int rows = top.key().kind() == StoragePopupWorkspace.HostKind.BELT ? 2
                : top.height() >= StoragePopupLayout.outerHeight(4) ? 4 : 2;
        var layout = new StoragePopupLayout(StoragePopupLayout.Kind.valueOf(top.key().kind().name()),
                top.x(), top.y(), columns, rows);
        return layout.visibleCellAt((int) x, (int) y, top.width(), top.height()) == null ? null : top;
    }

    /** Null means no intent, for both preview and release. No active-hand lookup is involved. */
    static WorldCrossPlan worldCrossPlan(WorldCrossSource source, StoragePopupWorkspace.HostKey destination,
                                        BodyHandStateSnapshot proof, WorldGridClientPolicy.GridView world,
                                        boolean actionable, GridGeometry.Cell anchor, GridGeometry.Rotation rotation,
                                        int button) {
        if (button != 0 || source == null || destination == null || proof == null || world == null
                || anchor == null || rotation == null || !actionable || world.stale()
                || proof.reason() != BodyHandStateSnapshot.Reason.OK || !source.account().equals(proof.accountId())
                || !source.body().equals(proof.bodyId()) || source.epoch() != proof.epoch()
                || source.bodyRevision() != proof.revision() || world.handRevision() != proof.revision()
                || !source.worldTarget().equals(world.target()) || !source.session().equals(world.session())
                || source.worldRevision() != world.hostRevision() || source.host().equals(destination)) return null;
        boolean fromWorld = source.host().kind() == StoragePopupWorkspace.HostKind.WORLD;
        var bodyHost = fromWorld ? destination : source.host();
        if (!bodyHost(bodyHost) || (fromWorld ? destination.kind() == StoragePopupWorkspace.HostKind.WORLD
                : destination.kind() != StoragePopupWorkspace.HostKind.WORLD
                || !destination.worldId().equals(world.target()))) return null;
        String id = hostId(proof, bodyHost);
        var bodyGrid = explicitGrid(proof, bodyHost);
        if (id == null || bodyGrid == null || !fromWorld && !id.equals(source.hostId())) return null;
        if (fromWorld) {
            var child = world.entries().stream().filter(e -> e.token().equals(source.child())).findFirst().orElse(null);
            if (child == null || !child.itemId().equals(source.itemId()) || child.count() != source.count()
                    || child.x() != source.oldAnchor().x() || child.y() != source.oldAnchor().y()
                    || child.rotation() != source.oldRotation().degrees()) return null;
        } else {
            var child = bodyGrid.entries().stream().filter(e -> e.token().equals(source.child())).findFirst().orElse(null);
            if (child == null || !child.itemId().equals(source.itemId()) || child.count() != source.count()
                    || child.x() != source.oldAnchor().x() || child.y() != source.oldAnchor().y()
                    || child.rotation() != source.oldRotation().degrees()) return null;
        }
        if ("moonstation14:bag".equals(source.itemId()) || "moonstation14:belt".equals(source.itemId())) return null;
        var item = ResourceLocation.tryParse(source.itemId());
        if (item == null) return null;
        GridGeometry.Snapshot<String> occupancy;
        if (fromWorld) {
            if (bodyGrid.entries().size() >= GridGeometry.MAX_ENTRIES
                    || bodyGrid.entries().stream().anyMatch(e -> e.token().equals(source.child()))) return null;
            occupancy = GridGeometry.Snapshot.empty(GridGeometry.StorageMask.rectangle(bodyGrid.width(), bodyGrid.height()));
            for (var entry : bodyGrid.entries()) {
                var entryId = ResourceLocation.tryParse(entry.itemId());
                if (entryId == null) return null;
                var placed = occupancy.place(entry.token(), ItemFootprints.clientForId(entryId),
                        new GridGeometry.Cell(entry.x(), entry.y()), GridGeometry.Rotation.values()[entry.rotation() / 90]);
                if (!placed.accepted()) return null;
                occupancy = placed.snapshot();
            }
        } else {
            if (world.entries().size() >= GridGeometry.MAX_ENTRIES
                    || world.entries().stream().anyMatch(e -> e.token().equals(source.child()))) return null;
            occupancy = world.geometry();
        }
        if (!StoragePopupWorkspace.preview(occupancy, null, ItemFootprints.clientForId(item), anchor, rotation, true).maySendAction()) return null;
        return new WorldCrossPlan(fromWorld ? BodyWorldCrossAction.Direction.WORLD_TO_BODY
                : BodyWorldCrossAction.Direction.BODY_TO_WORLD, world.target(), worn(bodyHost), id,
                bodyHost.token(), source.child(), source.oldAnchor(), source.oldRotation(), anchor, rotation);
    }

    /** World child into an empty, fitting equipment slot; the world entry is the source proof. */
    static WorldCrossPlan worldToEquipmentPlan(WorldCrossSource source, String slotId,
            BodyHandStateSnapshot proof, WorldGridClientPolicy.GridView world, boolean fit,
            boolean actionable, int button) {
        if (button != 0 || source == null || slotId == null || proof == null || world == null || !fit
                || !actionable || world.stale() || proof.reason() != BodyHandStateSnapshot.Reason.OK
                || !source.account().equals(proof.accountId()) || !source.body().equals(proof.bodyId())
                || source.epoch() != proof.epoch() || source.bodyRevision() != proof.revision()
                || world.handRevision() != proof.revision() || !source.worldTarget().equals(world.target())
                || !source.session().equals(world.session()) || source.worldRevision() != world.hostRevision()) return null;
        var slots = proof.equipment().stream().filter(s -> slotId.equals(s.id())).toList();
        var slot = slots.size() == 1 ? slots.getFirst() : null;
        var child = world.entries().stream().filter(e -> source.child().equals(e.token())).findFirst().orElse(null);
        if (slot == null || slot.token() != null || child == null || !child.itemId().equals(source.itemId())
                || child.count() != source.count() || child.x() != source.oldAnchor().x()
                || child.y() != source.oldAnchor().y() || child.rotation() != source.oldRotation().degrees()) return null;
        return new WorldCrossPlan(BodyWorldCrossAction.Direction.WORLD_TO_EQUIPMENT, world.target(), true,
                slotId, null, source.child(), source.oldAnchor(), source.oldRotation(), source.oldAnchor(),
                GridGeometry.Rotation.DEG_0);
    }

    /** Worn equipment source into a proved world grid; equipment has no source-cell geometry. */
    static WorldCrossPlan equipmentToWorldPlan(String slotId, String token, String itemId, int count,
            BodyHandStateSnapshot capturedProof, BodyHandStateSnapshot proof,
            WorldGridClientPolicy.GridView world, GridGeometry.Cell anchor, GridGeometry.Rotation rotation,
            boolean fit, boolean actionable, int button) {
        if (button != 0 || capturedProof == null || proof == null || world == null || anchor == null || rotation == null
                || !fit || !actionable || world.stale() || proof.reason() != BodyHandStateSnapshot.Reason.OK
                || capturedProof.reason() != BodyHandStateSnapshot.Reason.OK
                || !capturedProof.accountId().equals(proof.accountId()) || !capturedProof.bodyId().equals(proof.bodyId())
                || capturedProof.epoch() != proof.epoch() || capturedProof.revision() != proof.revision()
                || proof.revision() != world.handRevision()) return null;
        if (slotId == null) return null;
        var source = proof.equipment().stream().filter(s -> slotId.equals(s.id()) && token != null
                && token.equals(s.token()) && itemId != null && itemId.equals(s.itemId()) && count == s.count()).toList();
        var captured = capturedProof.equipment().stream().filter(s -> slotId.equals(s.id()) && token != null
                && token.equals(s.token()) && itemId != null && itemId.equals(s.itemId()) && count == s.count()).count();
        var id = ResourceLocation.tryParse(itemId);
        if (source.size() != 1 || captured != 1 || id == null || world.entries().size() >= GridGeometry.MAX_ENTRIES
                || world.entries().stream().anyMatch(e -> token.equals(e.token()))) return null;
        if (!StoragePopupWorkspace.preview(world.geometry(), null, ItemFootprints.clientForId(id), anchor, rotation, true).maySendAction()) return null;
        return new WorldCrossPlan(BodyWorldCrossAction.Direction.EQUIPMENT_TO_WORLD, world.target(), true,
                slotId, token, token, new GridGeometry.Cell(0, 0), GridGeometry.Rotation.DEG_0, anchor, rotation);
    }

    static CrossSource crossSource(BodyHandStateSnapshot proof, StoragePopupWorkspace.HostKey host,
                                   BodyHandStateSnapshot.GridEntry entry) {
        if (proof == null || proof.reason() != BodyHandStateSnapshot.Reason.OK || host == null || entry == null
                || !bodyHost(host)) return null;
        String hostId = hostId(proof, host);
        if (hostId == null) return null;
        var grid = explicitGrid(proof, host);
        if (grid == null || !grid.entries().contains(entry)) return null;
        return new CrossSource(proof.accountId(), proof.bodyId(), proof.epoch(), proof.revision(), host, hostId,
                entry.token(), entry.itemId(), entry.count(), new GridGeometry.Cell(entry.x(), entry.y()),
                GridGeometry.Rotation.values()[entry.rotation() / 90], entry.presentation());
    }

    static boolean worn(StoragePopupWorkspace.HostKey host) {
        return host != null && (host.kind() == StoragePopupWorkspace.HostKind.BAG
                || host.kind() == StoragePopupWorkspace.HostKind.BELT);
    }

    private static boolean bodyHost(StoragePopupWorkspace.HostKey host) {
        return worn(host) || host != null && host.kind() == StoragePopupWorkspace.HostKind.HELD;
    }

    /** Outer bounds of the top window occlude every lower grid, even when the top has no cell here. */
    static StoragePopupWorkspace.Window crossTarget(StoragePopupWorkspace workspace, CrossSource source, double x, double y) {
        if (workspace == null || source == null) return null;
        var top = workspace.topmost(x, y);
        if (top == null || top.provisional() || !bodyHost(top.key()) || top.key().equals(source.host())
                || top.key().token().equals(source.host().token())) return null;
        // HELD can display either a bag or a belt; its window dimensions follow that host's grid.
        var layout = top.key().kind() == StoragePopupWorkspace.HostKind.HELD
                ? new StoragePopupLayout(StoragePopupLayout.Kind.HELD, top.x(), top.y(),
                top.width() >= StoragePopupLayout.outerWidth(8) ? 8 : 7,
                top.height() >= StoragePopupLayout.outerHeight(4) ? 4 : 2)
                : StoragePopupLayout.at(StoragePopupLayout.Kind.valueOf(top.key().kind().name()), top.x(), top.y());
        return layout.visibleCellAt((int) x, (int) y, top.width(), top.height()) == null ? null : top;
    }

    private static String hostId(BodyHandStateSnapshot proof, StoragePopupWorkspace.HostKey host) {
        if (host.kind() == StoragePopupWorkspace.HostKind.HELD) {
            var hands = proof.hands().stream().filter(h -> host.token().equals(h.token()) && h.count() == 1
                    && ("moonstation14:bag".equals(h.itemId()) || "moonstation14:belt".equals(h.itemId())
                    || "moonstation14:pill_canister".equals(h.itemId()))).toList();
            return hands.size() == 1 ? hands.getFirst().id() : null;
        }
        String slot = host.kind() == StoragePopupWorkspace.HostKind.BELT ? "belt" : "back";
        var slots = proof.equipment().stream().filter(e -> slot.equals(e.id()) && host.token().equals(e.token())
                && (host.kind() == StoragePopupWorkspace.HostKind.BAG ? "moonstation14:bag" : "moonstation14:belt").equals(e.itemId())).toList();
        return slots.size() == 1 ? slot : null;
    }

    private static BodyHandStateSnapshot.GridHost explicitGrid(BodyHandStateSnapshot proof, StoragePopupWorkspace.HostKey host) {
        String kind = host.kind() == StoragePopupWorkspace.HostKind.HELD
                ? proof.hands().stream().filter(h -> host.token().equals(h.token())).map(h ->
                "moonstation14:bag".equals(h.itemId()) ? "bag" : "moonstation14:belt".equals(h.itemId()) ? "belt"
                        : "moonstation14:pill_canister".equals(h.itemId()) ? "pill" : "")
                .findFirst().orElse("") : host.kind() == StoragePopupWorkspace.HostKind.BAG ? "bag" : "belt";
        var grids = proof.grids().stream().filter(g -> host.token().equals(g.hostToken())
                && kind.equals(g.kind())).toList();
        return grids.size() == 1 ? grids.getFirst() : null;
    }

    /** Null is a cancellation; preview and commit share the same strict, full-snapshot proof. */
    static BodyHandActionRequest crossRequest(CrossSource source, StoragePopupWorkspace.HostKey target,
                                                BodyHandStateSnapshot proof, GridGeometry.Cell anchor,
                                                GridGeometry.Rotation rotation, int button, long sequence) {
        if (button != 0 || sequence <= 0 || source == null || proof == null || anchor == null || rotation == null
                || proof.reason() != BodyHandStateSnapshot.Reason.OK || !bodyHost(target) || source.host().equals(target)
                || source.host().token().equals(target.token()) || !source.account().equals(proof.accountId())
                || !source.body().equals(proof.bodyId()) || source.epoch() != proof.epoch()
                || source.revision() != proof.revision() || !source.hostId().equals(hostId(proof, source.host()))) return null;
        String targetId = hostId(proof, target);
        if (targetId == null || source.host().kind() == StoragePopupWorkspace.HostKind.HELD
                && target.kind() == StoragePopupWorkspace.HostKind.HELD && source.hostId().equals(targetId)) return null;
        var origin = explicitGrid(proof, source.host());
        var destination = explicitGrid(proof, target);
        if (origin == null || destination == null || destination.entries().size() >= GridGeometry.MAX_ENTRIES
                || destination.entries().stream().anyMatch(e -> source.child().equals(e.token()))) return null;
        var entry = origin.entries().stream().filter(e -> source.child().equals(e.token())).findFirst().orElse(null);
        if (entry == null || !entry.itemId().equals(source.itemId()) || entry.count() != source.count()
                || entry.x() != source.oldAnchor().x() || entry.y() != source.oldAnchor().y()
                || entry.rotation() != source.oldRotation().degrees()) return null;
        var id = ResourceLocation.tryParse(entry.itemId());
        if (id == null) return null;
        var occupancy = GridGeometry.Snapshot.<String>empty(GridGeometry.StorageMask.rectangle(destination.width(), destination.height()));
        for (var child : destination.entries()) {
            var childId = ResourceLocation.tryParse(child.itemId());
            if (childId == null) return null;
            var placed = occupancy.place(child.token(), ItemFootprints.clientForId(childId),
                    new GridGeometry.Cell(child.x(), child.y()), GridGeometry.Rotation.values()[child.rotation() / 90]);
            if (!placed.accepted()) return null;
            occupancy = placed.snapshot();
        }
        if (!StoragePopupWorkspace.preview(occupancy, null, ItemFootprints.clientForId(id), anchor, rotation, true).maySendAction()) return null;
        boolean fromHeld = source.host().kind() == StoragePopupWorkspace.HostKind.HELD;
        boolean toHeld = target.kind() == StoragePopupWorkspace.HostKind.HELD;
        var action = fromHeld ? toHeld ? BodyHandActionRequest.Action.GRID_BODY_HELD_TO_HELD
                : BodyHandActionRequest.Action.GRID_BODY_HELD_TO_WORN
                : toHeld ? BodyHandActionRequest.Action.GRID_BODY_WORN_TO_HELD
                : source.host().kind() == StoragePopupWorkspace.HostKind.BELT
                ? BodyHandActionRequest.Action.GRID_CROSS_BELT_TO_BACK : BodyHandActionRequest.Action.GRID_CROSS_BACK_TO_BELT;
        return new BodyHandActionRequest(action, fromHeld ? source.hostId() : toHeld ? targetId : proof.activeHand(),
                proof.epoch(), sequence, proof.revision(), null, source.host().token(),
                source.child(), anchor.x(), anchor.y(), rotation.degrees(), source.oldAnchor().x(),
                source.oldAnchor().y(), source.oldRotation().degrees(), target.token(),
                fromHeld || toHeld ? source.hostId() : null, fromHeld || toHeld ? targetId : null);
    }

    /** Body-owned grid child into an empty equipment slot; preview and release share this proof. */
    static BodyHandActionRequest gridToEquipmentRequest(CrossSource source, String destinationSlot,
            BodyHandStateSnapshot proof, boolean fit, int button, long sequence) {
        if (button != 0 || sequence <= 0 || source == null || proof == null || destinationSlot == null
                || proof.reason() != BodyHandStateSnapshot.Reason.OK
                || !java.util.Set.of("head", "eyes", "mask", "outerClothing", "innerClothing", "back", "belt").contains(destinationSlot)
                || !source.account().equals(proof.accountId()) || !source.body().equals(proof.bodyId())
                || source.epoch() != proof.epoch() || source.revision() != proof.revision()
                || !bodyHost(source.host()) || !source.hostId().equals(hostId(proof, source.host())) || !fit) return null;
        var slot = proof.equipment().stream().filter(s -> s.id().equals(destinationSlot)).findFirst().orElse(null);
        if (slot == null || slot.token() != null) return null;
        var origin = explicitGrid(proof, source.host());
        var entry = origin == null ? null : origin.entries().stream().filter(e -> e.token().equals(source.child())).findFirst().orElse(null);
        if (entry == null || !entry.itemId().equals(source.itemId()) || entry.count() != source.count()
                || entry.x() != source.oldAnchor().x() || entry.y() != source.oldAnchor().y()
                || entry.rotation() != source.oldRotation().degrees()) return null;
        return new BodyHandActionRequest(BodyHandActionRequest.Action.GRID_BODY_TO_EQUIPMENT,
                source.host().kind() == StoragePopupWorkspace.HostKind.HELD ? source.hostId() : proof.activeHand(),
                proof.epoch(), sequence, proof.revision(), null, source.host().token(),
                source.child(), entry.x(), entry.y(), entry.rotation(), entry.x(), entry.y(), entry.rotation(),
                null, source.hostId(), destinationSlot);
    }

    /** Worn equipment item into an explicitly nominated body grid, retaining both host proofs. */
    static BodyHandActionRequest equipmentToGridRequest(String sourceSlot, String sourceToken, String itemId,
            int count, BodyHandStateSnapshot proof, StoragePopupWorkspace.HostKey destination,
            GridGeometry.Cell anchor, GridGeometry.Rotation rotation, int button, long sequence) {
        if (button != 0 || sequence <= 0 || proof == null || proof.reason() != BodyHandStateSnapshot.Reason.OK
                || sourceSlot == null || destination == null || anchor == null || rotation == null
                || !bodyHost(destination) || !java.util.Set.of("back", "belt").contains(sourceSlot)) return null;
        var equipment = proof.equipment().stream().filter(s -> s.id().equals(sourceSlot)
                && sourceToken.equals(s.token()) && itemId.equals(s.itemId()) && s.count() == count).toList();
        if (equipment.size() != 1 || destination.kind() == StoragePopupWorkspace.HostKind.HELD
                && destination.token().equals(sourceToken)) return null;
        String destinationId = hostId(proof, destination);
        var grid = explicitGrid(proof, destination);
        var id = ResourceLocation.tryParse(itemId);
        if (destinationId == null || grid == null || id == null || grid.entries().size() >= GridGeometry.MAX_ENTRIES
                || grid.entries().stream().anyMatch(e -> e.token().equals(sourceToken))) return null;
        var occupancy = GridGeometry.Snapshot.<String>empty(GridGeometry.StorageMask.rectangle(grid.width(), grid.height()));
        for (var entry : grid.entries()) {
            var entryId = ResourceLocation.tryParse(entry.itemId()); if (entryId == null) return null;
            var placed = occupancy.place(entry.token(), ItemFootprints.clientForId(entryId),
                    new GridGeometry.Cell(entry.x(), entry.y()), GridGeometry.Rotation.values()[entry.rotation() / 90]);
            if (!placed.accepted()) return null;
            occupancy = placed.snapshot();
        }
        if (!StoragePopupWorkspace.preview(occupancy, null, ItemFootprints.clientForId(id), anchor, rotation, true).maySendAction()) return null;
        return new BodyHandActionRequest(BodyHandActionRequest.Action.GRID_EQUIPMENT_TO_BODY, proof.activeHand(),
                proof.epoch(), sequence, proof.revision(), null, sourceToken, sourceToken, anchor.x(), anchor.y(),
                rotation.degrees(), 0, 0, 0, destination.token(), sourceSlot, destinationId);
    }

    static Action plan(boolean firstPress, boolean pointer, boolean drop, boolean select, boolean storage,
                       boolean shift, boolean control, boolean otherModifier, boolean altCombo) {
        if (!firstPress) return Action.NONE;
        if (pointer) return altCombo ? Action.NONE : Action.POINTER;
        if (drop) return Action.DROP;
        if (select) return Action.SELECT;
        if (!storage || otherModifier || shift && control) return Action.NONE;
        return control ? Action.SELECT : shift ? Action.BELT : Action.BAG;
    }

    private PopupActionPlan() { }
}
