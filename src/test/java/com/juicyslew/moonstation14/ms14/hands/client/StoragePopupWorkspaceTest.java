package com.juicyslew.moonstation14.ms14.hands.client;

import com.juicyslew.moonstation14.ms14.storage.grid.GridGeometry;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class StoragePopupWorkspaceTest {
    @Test void pointerLockCyclePreservesTopRightWindowAndViewportGeometry() {
        var workspace=new StoragePopupWorkspace(400,300);
        workspace.bind(UUID.randomUUID(),1);
        var host=StoragePopupWorkspace.HostKey.world(UUID.randomUUID());
        workspace.open(host,true,305,12,80,60);
        var window=workspace.window(host);
        workspace.pointerMode(true);
        workspace.pointerMode(false);
        workspace.pointerMode(true);
        assertSame(window,workspace.window(host));
        assertEquals(new StoragePopupWorkspace.Point(305,12),new StoragePopupWorkspace.Point(window.x(),window.y()));
        assertEquals(80,window.width());assertEquals(60,window.height());
    }
    @Test void authoritativeResizeKeepsPositionAndZWhileClampingBounds() {
        var workspace = new StoragePopupWorkspace(400, 300);
        var key = StoragePopupWorkspace.HostKey.world(UUID.randomUUID());
        workspace.open(key, true, 200, 100, 80, 70);
        var window = workspace.window(key);
        int z = window.z();
        workspace.resizeWindow(key, 220, 180);
        assertEquals(180, window.x());
        assertEquals(100, window.y());
        assertEquals(220, window.width());
        assertEquals(180, window.height());
        assertEquals(z, window.z());
    }
    private StoragePopupWorkspace.HostKey bag = StoragePopupWorkspace.HostKey.worn(StoragePopupWorkspace.HostKind.BAG, "bag");
    private StoragePopupWorkspace.HostKey belt = StoragePopupWorkspace.HostKey.worn(StoragePopupWorkspace.HostKind.BELT, "belt");
    private StoragePopupWorkspace.HostKey world = StoragePopupWorkspace.HostKey.world(UUID.randomUUID());

    @Test void uncoveredWindowRectanglesSubtractHigherBoundsWithoutOverlap() {
        var workspace = new StoragePopupWorkspace(500, 400);
        workspace.bind(UUID.randomUUID(), 1);
        workspace.open(bag, true, 10, 10, 100, 80);
        workspace.open(belt, true, 40, 30, 30, 20);
        var lower = workspace.window(bag);
        var full = new StoragePopupLayout.Rect(10, 10, 100, 80);
        var pieces = workspace.uncovered(full, lower);
        int area = pieces.stream().mapToInt(r -> r.width() * r.height()).sum();
        assertEquals(100 * 80 - 30 * 20, area);
        for (int i=0;i<pieces.size();i++) for (int j=i+1;j<pieces.size();j++)
            assertFalse(pieces.get(i).intersects(pieces.get(j)));
        workspace.focus(belt);
        assertEquals(area, workspace.uncovered(full, lower).stream().mapToInt(r -> r.width() * r.height()).sum());
        workspace.place(belt, 200, 200);
        assertEquals(List.of(full), workspace.uncovered(full, lower));
        assertEquals(List.of(new StoragePopupLayout.Rect(40, 30, 30, 20)),
                workspace.uncovered(new StoragePopupLayout.Rect(40, 30, 30, 20), lower));
    }


    @Test void heldHostIsDistinctAndSharesThreeWindowQuota() {
        var workspace = new StoragePopupWorkspace(400, 300);
        var body = UUID.randomUUID();
        workspace.bind(body, 1);
        var held = StoragePopupWorkspace.HostKey.held("held-one");
        var replacement = StoragePopupWorkspace.HostKey.held("held-two");
        workspace.open(bag, true, 0, 0, 186, 140);
        workspace.open(belt, true, 0, 0, 186, 140);
        assertEquals(StoragePopupWorkspace.OpenResult.OPENED, workspace.open(held, true, 0, 0, 186, 140));
        assertEquals(StoragePopupWorkspace.OpenResult.DENIED, workspace.open(replacement, true, 0, 0, 186, 140));
        workspace.snapshot(held, true, false, List.of());
        assertNull(workspace.window(held));
        assertEquals(StoragePopupWorkspace.OpenResult.OPENED, workspace.open(replacement, true, 0, 0, 186, 140));
        workspace.bind(body, 2);
        assertTrue(workspace.windows().isEmpty());
        assertThrows(IllegalArgumentException.class, () -> StoragePopupWorkspace.HostKey.worn(StoragePopupWorkspace.HostKind.HELD, "wrong"));
    }

    @Test void independentWindowsLimitFocusAndDragSelection() {
        var workspace=new StoragePopupWorkspace(320, 240);
        workspace.bind(UUID.randomUUID(), 1);
        assertEquals(StoragePopupWorkspace.OpenResult.OPENED, workspace.open(bag, true, 0, 0, 100, 80));
        assertEquals(StoragePopupWorkspace.OpenResult.OPENED, workspace.open(belt, true, 100, 0, 100, 80));
        assertEquals(StoragePopupWorkspace.OpenResult.OPENED, workspace.open(world, true, 200, 0, 100, 80));
        assertEquals(StoragePopupWorkspace.OpenResult.DENIED, workspace.open(StoragePopupWorkspace.HostKey.world(UUID.randomUUID()), true, 0, 0, 1, 1));
        int prior=workspace.window(bag).z();
        assertEquals(StoragePopupWorkspace.OpenResult.FOCUSED, workspace.open(bag, true, 0, 0, 1, 1));
        assertTrue(workspace.window(bag).z()>prior);
        workspace.select(bag, "one"); workspace.select(belt, "two");
        workspace.beginDrag(bag, new GridGeometry.Cell(0,0), GridGeometry.Rotation.DEG_0,
                new GridGeometry.Cell(1,0), GridGeometry.Rotation.DEG_90);
        assertNull(workspace.window(belt).drag());
        assertEquals("two", workspace.window(belt).selectedChild());
    }

    @Test void capturedHandSourceSurvivesGridSnapshotsUntilOwnershipDisappears() {
        var workspace = new StoragePopupWorkspace(320, 240);
        workspace.bind(UUID.randomUUID(), 1);
        workspace.open(bag, true, 0, 0, 100, 80);
        workspace.select(bag, "old-grid-selection");
        workspace.beginDrag(bag, null, null, new GridGeometry.Cell(2, 1), GridGeometry.Rotation.DEG_0,
                "held-token", true);
        workspace.snapshot(bag, true, true, List.of("stored-child"));
        assertNull(workspace.window(bag).selectedChild());
        assertEquals("held-token", workspace.window(bag).drag().sourceToken());
        assertTrue(workspace.window(bag).drag().fromHand());
        workspace.validateHandSources(List.of("held-token"));
        assertNotNull(workspace.window(bag).drag());
        workspace.validateHandSources(List.of());
        assertNull(workspace.window(bag).drag());
    }

    @Test void threeWorldHostsUseSharedVisibleQuotaAndHiddenWindowsCannotBypassIt() {
        var workspace = new StoragePopupWorkspace(320, 240);
        workspace.bind(UUID.randomUUID(), 1);
        var worldOne = StoragePopupWorkspace.HostKey.world(UUID.randomUUID());
        var worldTwo = StoragePopupWorkspace.HostKey.world(UUID.randomUUID());
        var worldThree = StoragePopupWorkspace.HostKey.world(UUID.randomUUID());
        assertEquals(StoragePopupWorkspace.OpenResult.OPENED, workspace.open(worldOne, true, 0, 0, 40, 40));
        assertEquals(StoragePopupWorkspace.OpenResult.OPENED, workspace.open(worldTwo, true, 0, 0, 40, 40));
        assertEquals(StoragePopupWorkspace.OpenResult.OPENED, workspace.open(worldThree, true, 0, 0, 40, 40));
        assertEquals(StoragePopupWorkspace.OpenResult.FOCUSED, workspace.open(worldOne, true, 0, 0, 40, 40));
        assertEquals(StoragePopupWorkspace.OpenResult.DENIED, workspace.open(bag, true, 0, 0, 40, 40));
        assertEquals(StoragePopupWorkspace.OpenResult.DENIED,
                workspace.open(StoragePopupWorkspace.HostKey.world(UUID.randomUUID()), true, 0, 0, 40, 40));

        workspace.close(worldOne);
        assertNull(workspace.window(worldOne));
        assertEquals(StoragePopupWorkspace.OpenResult.OPENED, workspace.open(bag, true, 0, 0, 40, 40));
        assertTrue(workspace.window(bag).visible());
        workspace.close(bag);
        assertEquals(StoragePopupWorkspace.OpenResult.FOCUSED, workspace.open(bag, true, 0, 0, 40, 40));
        assertFalse(workspace.toggle(worldOne, true, 0, 0, 40, 40));
        assertNull(workspace.window(worldOne));
    }

    @Test void mixedWorldHeldAndWornKeysHaveOneQuota() {
        var workspace = new StoragePopupWorkspace(320, 240);
        workspace.bind(UUID.randomUUID(), 1);
        var held = StoragePopupWorkspace.HostKey.held("held");
        assertEquals(StoragePopupWorkspace.OpenResult.OPENED, workspace.open(held, true, 0, 0, 40, 40));
        assertEquals(StoragePopupWorkspace.OpenResult.OPENED, workspace.open(world, true, 0, 0, 40, 40));
        assertEquals(StoragePopupWorkspace.OpenResult.OPENED, workspace.open(belt, true, 0, 0, 40, 40));
        assertEquals(StoragePopupWorkspace.OpenResult.DENIED, workspace.open(bag, true, 0, 0, 40, 40));
        workspace.close(world);
        assertEquals(StoragePopupWorkspace.OpenResult.OPENED, workspace.open(bag, true, 0, 0, 40, 40));
    }

    @Test void provisionalPreviewsAndGeometryAndSnapshotRetention() {
        var workspace=new StoragePopupWorkspace(100, 80);
        workspace.bind(UUID.randomUUID(), 4);
        workspace.open(bag, false, 98, 79, 40, 30);
        assertEquals(new StoragePopupWorkspace.Point(60,50), new StoragePopupWorkspace.Point(workspace.window(bag).x(), workspace.window(bag).y()));
        workspace.select(bag,"child");
        workspace.beginDrag(bag,new GridGeometry.Cell(0,0),GridGeometry.Rotation.DEG_0,new GridGeometry.Cell(1,0),GridGeometry.Rotation.DEG_0);
        assertEquals("child", workspace.window(bag).drag().sourceToken());
        workspace.snapshot(bag,false,true,List.of("child"));
        assertTrue(workspace.window(bag).provisional());
        assertEquals("child",workspace.window(bag).selectedChild());
        assertNotNull(workspace.window(bag).drag());
        assertFalse(workspace.window(bag).drag().fromHand());
        workspace.place(bag, 99, 79); workspace.resize(50, 40);
        assertEquals(10,workspace.window(bag).x()); assertEquals(10,workspace.window(bag).y());

        var grid=GridGeometry.Snapshot.<String>empty(GridGeometry.StorageMask.rectangle(2,1));
        grid=grid.place("occupied",GridGeometry.Footprint.of(List.of(new GridGeometry.Cell(0,0))),
                new GridGeometry.Cell(0,0),GridGeometry.Rotation.DEG_0).snapshot();
        var shape=GridGeometry.Footprint.of(List.of(new GridGeometry.Cell(0,0)));
        assertEquals(StoragePopupWorkspace.PreviewFlag.UNAVAILABLE, StoragePopupWorkspace.preview(grid,null,shape,new GridGeometry.Cell(1,0),GridGeometry.Rotation.DEG_0,false).flag());
        var overlap=StoragePopupWorkspace.preview(grid,null,shape,new GridGeometry.Cell(0,0),GridGeometry.Rotation.DEG_0,true);
        assertEquals(StoragePopupWorkspace.PreviewFlag.OVERLAP,overlap.flag()); assertEquals(StoragePopupWorkspace.PreviewColor.RED,overlap.color()); assertFalse(overlap.maySendAction());
        assertEquals(StoragePopupWorkspace.PreviewFlag.OUT_OF_BOUNDS,StoragePopupWorkspace.preview(grid,null,shape,new GridGeometry.Cell(2,0),GridGeometry.Rotation.DEG_0,true).flag());
        var permitted=StoragePopupWorkspace.preview(grid,"occupied",shape,new GridGeometry.Cell(1,0),GridGeometry.Rotation.DEG_0,true);
        assertEquals(StoragePopupWorkspace.PreviewColor.GREEN,permitted.color()); assertTrue(permitted.maySendAction());

        workspace.snapshot(bag,true,true,List.of("child"));
        assertFalse(workspace.window(bag).provisional());
        workspace.snapshot(bag,true,false,List.of()); assertNull(workspace.window(bag));
        workspace.open(bag,true,0,0,20,20); workspace.bind(UUID.randomUUID(),4); assertTrue(workspace.windows().isEmpty());
    }

    @Test void onlyConfirmedSnapshotsInvalidateCapturedGridAndHandSources() {
        var workspace = new StoragePopupWorkspace(320, 240);
        workspace.bind(UUID.randomUUID(), 1);
        workspace.open(bag, true, 0, 0, 100, 80);
        workspace.select(bag, "grid-token");
        workspace.beginDrag(bag, null, null, new GridGeometry.Cell(1, 0), GridGeometry.Rotation.DEG_0);
        workspace.snapshot(bag, false, true, List.of());
        assertEquals("grid-token", workspace.window(bag).drag().sourceToken());
        assertFalse(workspace.window(bag).drag().fromHand());
        assertFalse(StoragePopupWorkspace.preview(null, null, null, null, null, false).maySendAction());
        workspace.snapshot(bag, true, true, List.of());
        assertNull(workspace.window(bag).drag());

        workspace.beginDrag(bag, null, null, new GridGeometry.Cell(1, 0), GridGeometry.Rotation.DEG_0,
                "held-token", true);
        workspace.snapshot(bag, false, true, List.of());
        assertNotNull(workspace.window(bag).drag());
        workspace.validateHandSources(List.of());
        assertNull(workspace.window(bag).drag());

        workspace.beginDrag(bag, null, null, new GridGeometry.Cell(1, 0), GridGeometry.Rotation.DEG_0,
                "held-token", true);
        workspace.validateHandSources(List.of());
        assertNull(workspace.window(bag).drag());
    }

    @Test void provisionalMissingHostDoesNotRemoveWindowButConfirmedAbsenceDoes() {
        var workspace = new StoragePopupWorkspace(320, 240);
        workspace.bind(UUID.randomUUID(), 1);
        workspace.open(bag, true, 0, 0, 100, 80);
        workspace.select(bag, "child");
        workspace.beginDrag(bag, null, null, new GridGeometry.Cell(1, 0), GridGeometry.Rotation.DEG_0);
        workspace.snapshot(bag, false, false, List.of());
        assertNotNull(workspace.window(bag));
        assertTrue(workspace.window(bag).provisional());
        assertEquals("child", workspace.window(bag).selectedChild());
        assertNotNull(workspace.window(bag).drag());
        workspace.snapshot(bag, true, false, List.of());
        assertNull(workspace.window(bag));
    }

    @Test void pointerToggleKeepsIndependentWindowsButAuthorityResetRemovesBoth() {
        var workspace = new StoragePopupWorkspace(320, 240);
        var body = UUID.randomUUID();
        workspace.bind(body, 2);
        workspace.open(bag, true, 4, 4, 186, 140);
        workspace.open(belt, true, 210, 4, 186, 140);
        assertEquals(134, workspace.window(belt).x());
        workspace.pointerMode(true);
        workspace.close(bag);
        assertNotNull(workspace.window(bag));
        assertFalse(workspace.window(bag).visible());
        assertTrue(workspace.window(belt).visible());
        workspace.pointerMode(false);
        assertEquals(2, workspace.windows().size());
        workspace.toggle(bag, true, 0, 0, 186, 140);
        assertTrue(workspace.window(bag).visible());
        workspace.bind(body, 3);
        assertTrue(workspace.windows().isEmpty());
        assertFalse(workspace.pointerMode());
    }

    @Test void worldWindowAndKeySurvivePointerModeTransitionUntilExplicitClose() {
        var workspace = new StoragePopupWorkspace(320, 240);
        workspace.bind(UUID.randomUUID(), 2);
        var worldKey = StoragePopupWorkspace.HostKey.world(UUID.randomUUID());
        workspace.open(worldKey, true, 12, 18, 186, 140);

        workspace.pointerMode(true);
        workspace.pointerMode(false);

        assertNotNull(workspace.window(worldKey));
        assertTrue(workspace.window(worldKey).visible());
        assertFalse(workspace.pointerMode());
        workspace.close(worldKey);
        assertNull(workspace.window(worldKey));
    }

    @Test void closedWindowsAreEvictedAndFocusCannotResurrectWorldHosts() {
        var workspace = new StoragePopupWorkspace(320, 240);
        workspace.bind(UUID.randomUUID(), 1);
        for (int i = 0; i < 20; i++) {
            var key = StoragePopupWorkspace.HostKey.world(UUID.randomUUID());
            assertEquals(StoragePopupWorkspace.OpenResult.OPENED, workspace.open(key, true, 0, 0, 40, 40));
            workspace.close(key);
            assertNull(workspace.window(key));
            assertTrue(workspace.windows().size() <= StoragePopupWorkspace.MAX_WINDOWS);
        }

        var one = StoragePopupWorkspace.HostKey.world(UUID.randomUUID());
        var two = StoragePopupWorkspace.HostKey.world(UUID.randomUUID());
        var three = StoragePopupWorkspace.HostKey.world(UUID.randomUUID());
        var fourth = StoragePopupWorkspace.HostKey.world(UUID.randomUUID());
        workspace.open(one, true, 0, 0, 40, 40);
        workspace.open(two, true, 0, 0, 40, 40);
        workspace.open(three, true, 0, 0, 40, 40);
        workspace.close(one);
        assertEquals(StoragePopupWorkspace.OpenResult.OPENED, workspace.open(bag, true, 0, 0, 40, 40));
        workspace.focus(one);
        assertNull(workspace.window(one));
        assertEquals(StoragePopupWorkspace.OpenResult.DENIED, workspace.open(one, false, 0, 0, 40, 40));
        assertEquals(StoragePopupWorkspace.OpenResult.DENIED, workspace.open(fourth, true, 0, 0, 40, 40));
        assertEquals(3, workspace.windows().size());
    }

    @Test void resizeClipsVisibleBoundsWithoutChangingPreferredWindowGeometryOrQuota() {
        var workspace = new StoragePopupWorkspace(320, 240);
        workspace.bind(UUID.randomUUID(), 1);
        workspace.open(bag, true, 200, 100, 80, 60);
        workspace.open(belt, true, 10, 10, 40, 40);
        workspace.open(world, true, 60, 10, 40, 40);
        int beltZ = workspace.window(belt).z();
        int worldZ = workspace.window(world).z();

        workspace.resize(90, 90);
        assertEquals(80, workspace.window(bag).width());
        assertEquals(60, workspace.window(bag).height());
        assertEquals(10, workspace.window(bag).x());
        assertEquals(30, workspace.window(bag).y());
        assertEquals(StoragePopupWorkspace.OpenResult.DENIED,
                workspace.open(StoragePopupWorkspace.HostKey.world(UUID.randomUUID()), true, 0, 0, 10, 10));

        workspace.resize(320, 240);
        assertEquals(80, workspace.window(bag).width());
        assertEquals(60, workspace.window(bag).height());
        assertEquals(200, workspace.window(bag).x());
        assertEquals(100, workspace.window(bag).y());
        assertEquals(3, workspace.windows().size());
        assertEquals(beltZ, workspace.window(belt).z());
        assertEquals(worldZ, workspace.window(world).z());
    }

    @Test void compactPopupPinsStripAtOriginWhenViewportClipsGridAndRestoresPlacement() {
        var workspace = new StoragePopupWorkspace(400, 200);
        workspace.open(bag, true, 210, 90, 136, 72);
        workspace.open(belt, true, 180, 20, 152, 40);
        workspace.resize(42, 26);
        for (var window : workspace.windows()) {
            assertEquals(0, window.x()); assertEquals(0, window.y());
            assertEquals(42, window.width()); assertEquals(26, window.height());
        }
        workspace.resize(400, 200);
        assertEquals(210, workspace.window(bag).x()); assertEquals(90, workspace.window(bag).y());
        assertEquals(180, workspace.window(belt).x()); assertEquals(20, workspace.window(belt).y());
    }

    @Test void topmostWindowOccludesLowerGridEvenWhereUpperWindowHasOnlyBorder() {
        var workspace = new StoragePopupWorkspace(320, 240);
        workspace.open(bag, true, 0, 0, 164, 88);
        workspace.open(belt, true, 18, 4, 184, 48);
        assertSame(workspace.window(belt), workspace.topmost(22, 8));
        assertNull(StoragePopupLayout.at(StoragePopupLayout.Kind.BELT, 18, 4).cellAt(22, 8));
        assertSame(workspace.window(belt), workspace.topmost(40, 20));
        workspace.focus(bag);
        assertSame(workspace.window(bag), workspace.topmost(40, 20));
        assertNull(workspace.topmost(202, 52));
    }

    @Test void rehostPreservesWindowStateAndCoalescesDestinationWithoutSpendingQuota() {
        var workspace = new StoragePopupWorkspace(400, 300);
        workspace.bind(UUID.randomUUID(), 1);
        var worldHost = StoragePopupWorkspace.HostKey.world(UUID.randomUUID());
        var heldHost = StoragePopupWorkspace.HostKey.held("server-held-1");
        var bagHost = StoragePopupWorkspace.HostKey.worn(StoragePopupWorkspace.HostKind.BAG, "server-bag-1");
        var beltHost = StoragePopupWorkspace.HostKey.worn(StoragePopupWorkspace.HostKind.BELT, "server-belt-1");
        workspace.open(worldHost, true, 37, 48, 120, 90);
        workspace.open(bagHost, true, 0, 0, 80, 60);
        workspace.open(beltHost, true, 200, 0, 80, 60);
        workspace.pointerMode(true);
        workspace.select(worldHost, "child-token");
        workspace.beginDrag(worldHost, new GridGeometry.Cell(0, 0), GridGeometry.Rotation.DEG_0,
                new GridGeometry.Cell(1, 0), GridGeometry.Rotation.DEG_90);
        int z = workspace.window(worldHost).z();

        assertTrue(workspace.rekey(worldHost, heldHost));
        var moving = workspace.window(heldHost);
        assertNull(workspace.window(worldHost));
        assertEquals(new StoragePopupWorkspace.Point(37, 48), new StoragePopupWorkspace.Point(moving.x(), moving.y()));
        assertEquals(120, moving.width()); assertEquals(90, moving.height()); assertEquals(z, moving.z());
        assertTrue(moving.visible()); assertTrue(moving.provisional());
        assertNull(moving.drag()); assertEquals("child-token", moving.selectedChild());
        assertTrue(workspace.pointerMode());
        assertEquals(3, workspace.windows().size());

        assertTrue(workspace.rekey(heldHost, worldHost));
        assertNull(workspace.window(heldHost));
        assertNotNull(workspace.window(worldHost));
        assertTrue(workspace.rekey(worldHost, bagHost));
        assertNull(workspace.window(worldHost));
        assertSame(workspace.window(bagHost), moving);
        assertEquals(new StoragePopupWorkspace.Point(37, 48), new StoragePopupWorkspace.Point(moving.x(), moving.y()));
        assertEquals(2, workspace.windows().size()); // source and prior destination coalesced
        var third = StoragePopupWorkspace.HostKey.world(UUID.randomUUID());
        assertEquals(StoragePopupWorkspace.OpenResult.OPENED, workspace.open(third, true, 0, 100, 40, 40));
        assertEquals(3, workspace.windows().size());
        assertEquals(StoragePopupWorkspace.OpenResult.DENIED,
                workspace.open(StoragePopupWorkspace.HostKey.world(UUID.randomUUID()), true, 0, 0, 40, 40));
        assertTrue(workspace.pointerMode());
    }
}
