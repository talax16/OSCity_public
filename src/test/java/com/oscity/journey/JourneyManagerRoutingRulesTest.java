package com.oscity.journey;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class JourneyManagerRoutingRulesTest {

    @Test
    void tlbHitRoutesToRamAndTlbMissRoutesToMissFlow() {
        assertEquals(JourneyManager.TlbRoute.RAM, JourneyManager.tlbRoute(Journey.LUCKY));

        assertEquals(JourneyManager.TlbRoute.MISS_FLOW, JourneyManager.tlbRoute(Journey.TLB_MISS_ALLOW));
        assertEquals(JourneyManager.TlbRoute.MISS_FLOW, JourneyManager.tlbRoute(Journey.PERMISSION_VIOLATION));
        assertEquals(JourneyManager.TlbRoute.MISS_FLOW, JourneyManager.tlbRoute(Journey.SWAPPED_OUT));
        assertEquals(JourneyManager.TlbRoute.MISS_FLOW, JourneyManager.tlbRoute(Journey.PURE_COW));
        assertEquals(JourneyManager.TlbRoute.MISS_FLOW, JourneyManager.tlbRoute(Journey.LAZY_LOADING));
        assertEquals(JourneyManager.TlbRoute.MISS_FLOW, JourneyManager.tlbRoute(Journey.LAZY_ALLOCATION));

        assertEquals(JourneyManager.TlbRoute.NONE, JourneyManager.tlbRoute(null));
    }

    @Test
    void permissionChamberBranchComesFromJourneyState() {
        assertEquals(JourneyManager.PermissionRoute.RAM, JourneyManager.permissionRoute(Journey.LUCKY));
        assertEquals(JourneyManager.PermissionRoute.RAM, JourneyManager.permissionRoute(Journey.TLB_MISS_ALLOW));

        assertEquals(JourneyManager.PermissionRoute.PAGE_FAULT, JourneyManager.permissionRoute(Journey.SWAPPED_OUT));
        assertEquals(JourneyManager.PermissionRoute.PAGE_FAULT, JourneyManager.permissionRoute(Journey.LAZY_LOADING));
        assertEquals(JourneyManager.PermissionRoute.PAGE_FAULT, JourneyManager.permissionRoute(Journey.LAZY_ALLOCATION));

        assertEquals(JourneyManager.PermissionRoute.TERMINATE, JourneyManager.permissionRoute(Journey.PERMISSION_VIOLATION));
        assertEquals(JourneyManager.PermissionRoute.COW, JourneyManager.permissionRoute(Journey.PURE_COW));
        assertEquals(JourneyManager.PermissionRoute.NONE, JourneyManager.permissionRoute(null));
    }

    @Test
    void pageFaultSubtypeRoutesToTheCorrectNextRoom() {
        assertEquals(JourneyManager.PageFaultRoute.DISK_ROOM, JourneyManager.pageFaultRoute(Journey.SWAPPED_OUT));
        assertEquals(JourneyManager.PageFaultRoute.LAZY_LOADING_ROOM, JourneyManager.pageFaultRoute(Journey.LAZY_LOADING));
        assertEquals(JourneyManager.PageFaultRoute.LAZY_ALLOCATION_ROOM, JourneyManager.pageFaultRoute(Journey.LAZY_ALLOCATION));

        assertEquals(JourneyManager.PageFaultRoute.NONE, JourneyManager.pageFaultRoute(Journey.LUCKY));
        assertEquals(JourneyManager.PageFaultRoute.NONE, JourneyManager.pageFaultRoute(Journey.TLB_MISS_ALLOW));
        assertEquals(JourneyManager.PageFaultRoute.NONE, JourneyManager.pageFaultRoute(Journey.PERMISSION_VIOLATION));
        assertEquals(JourneyManager.PageFaultRoute.NONE, JourneyManager.pageFaultRoute(Journey.PURE_COW));
        assertEquals(JourneyManager.PageFaultRoute.NONE, JourneyManager.pageFaultRoute(null));
    }

    @Test
    void segmentationFaultTerminatesAndCowUsesProtectionFaultPath() {
        assertEquals(JourneyManager.PermissionRoute.TERMINATE, JourneyManager.permissionRoute(Journey.PERMISSION_VIOLATION));
        assertEquals(JourneyManager.PageFaultRoute.NONE, JourneyManager.pageFaultRoute(Journey.PERMISSION_VIOLATION));

        assertEquals(JourneyManager.PermissionRoute.COW, JourneyManager.permissionRoute(Journey.PURE_COW));
        assertEquals(JourneyManager.PageFaultRoute.NONE, JourneyManager.pageFaultRoute(Journey.PURE_COW));
    }
}
