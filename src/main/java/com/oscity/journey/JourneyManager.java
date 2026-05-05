package com.oscity.journey;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Centralises routing decisions that depend on the active Journey.
 * Called by RoomChangeListener and ChoiceButtonHandler in place of
 * scattered {@code if (journey == Journey.X)} branches.
 */
public class JourneyManager {

    private JourneyManager() {}

    public enum TlbRoute {
        RAM,
        MISS_FLOW,
        NONE
    }

    public enum PermissionRoute {
        RAM,
        PAGE_FAULT,
        TERMINATE,
        COW,
        NONE
    }

    public enum PageFaultRoute {
        LAZY_ALLOCATION_ROOM,
        LAZY_LOADING_ROOM,
        DISK_ROOM,
        NONE
    }

    // Journey routing 

    public static TlbRoute tlbRoute(Journey journey) {
        if (journey == null) return TlbRoute.NONE;
        return journey.isTlbHit ? TlbRoute.RAM : TlbRoute.MISS_FLOW;
    }

    public static PermissionRoute permissionRoute(Journey journey) {
        if (journey == null) return PermissionRoute.NONE;

        switch (journey.permissionAnswer) {
            case "allow_access":      return PermissionRoute.RAM;
            case "page_fault":        return PermissionRoute.PAGE_FAULT;
            case "segfault":          return PermissionRoute.TERMINATE;
            case "protection_fault":  return PermissionRoute.COW;
            default:                  return PermissionRoute.NONE;
        }
    }

    public static PageFaultRoute pageFaultRoute(Journey journey) {
        if (journey == null || journey.pageFaultType == null) return PageFaultRoute.NONE;

        switch (journey.pageFaultType) {
            case "lazy_allocation": return PageFaultRoute.LAZY_ALLOCATION_ROOM;
            case "lazy_loading":    return PageFaultRoute.LAZY_LOADING_ROOM;
            case "swapped_out":     return PageFaultRoute.DISK_ROOM;
            default:                return PageFaultRoute.NONE;
        }
    }

    // RAM Room 

    /** Next phase after the player confirms a frame in the RAM Room (allow_access). */
    public static String nextPhaseAfterRamConfirm(Journey j) {
        return "ram_before_finish";
    }

    /** RAM mix-sign text [l1, l2, l3, l4] after allow_access confirm. */
    public static String[] ramSignAfterConfirm(Journey j) {
        return new String[]{"FINISH", "", "", ""};
    }

    /** After COW: true when the journey still needs a Swap District visit (Lazy Allocation). */
    public static boolean needsSwapAfterCow(Journey j) {
        return j == Journey.LAZY_ALLOCATION;
    }

    // Disk Room

    /** Phase to set on Disk Room entry for this journey, or null if not applicable. */
    public static String diskPhase(Journey j) {
        if (j == Journey.LAZY_LOADING) return "disk_lazy_loading";
        if (j == Journey.SWAPPED_OUT)  return "disk_swap_retrieval";
        return null;
    }

    /** Follow-up dialogue path shown after the initial Disk Room spawn dialogue. */
    public static String diskPromptDialogue(Journey j) {
        if (j == Journey.LAZY_LOADING) return "rooms.disk_room.lazy_loading_prompt";
        if (j == Journey.SWAPPED_OUT)  return "rooms.disk_room.swap_retrieval_prompt";
        return null;
    }

    // Swap District

    /**
     * Variable updates to apply when entering the Swap District.
     * {@code currentPfnCow} is the current value of the "pfnCow" var
     * (used only for Lazy Allocation; ignored otherwise).
     * Returns a map of varName → newValue; may be empty.
     */
    public static Map<String, String> swapEntryVarUpdates(Journey j, String currentPfnCow) {
        Map<String, String> updates = new LinkedHashMap<>();
        if (j == Journey.LAZY_ALLOCATION) {
            updates.put("pfn",  currentPfnCow);
        }
        return updates;
    }

    // RAM Room (returning from Swap)

    /** Phase to set when returning to RAM from the Swap District, or null. */
    public static String phaseAfterSwapInRam(Journey j) {
        if (j == Journey.LAZY_LOADING)    return "ram_after_swap_lazy_loading";
        if (j == Journey.LAZY_ALLOCATION) return "ram_after_swap_lazy_alloc";
        return null;
    }

    /** Dialogue path for RAM after returning from the Swap District, or null. */
    public static String dialogueAfterSwapInRam(Journey j) {
        if (j == Journey.LAZY_LOADING)    return "rooms.ram_room.after_swap_for_lazy_loading";
        if (j == Journey.LAZY_ALLOCATION) return "rooms.ram_room.after_swap_for_lazy_alloc";
        return null;
    }

}
