package com.oscity.mechanics;

import com.oscity.journey.Journey;

/**
 * Pure room access rules used by {@link ChoiceButtonHandler}.
 * No Bukkit, player, plugin, inventory, sign, door, or teleport side effects belong here.
 */
final class RoomAccessRules {

    private RoomAccessRules() {}

    static boolean canOpenPageFaultCorridor(String phase, Journey journey) {
        return "page_fault_type".equals(phase)
            && (journey == Journey.LAZY_ALLOCATION || journey == Journey.LAZY_LOADING);
    }

    static boolean canOpenLazyLoading(Journey journey) {
        return journey == Journey.LAZY_LOADING;
    }

    static boolean canOpenLazyAllocation(Journey journey) {
        return journey == Journey.LAZY_ALLOCATION;
    }

    static boolean canUsePageTableAccessPhase(String phase) {
        return "correct_floor".equals(phase) || "acquired_pte".equals(phase);
    }

    static boolean canUsePageTableToChamber(String phase, boolean hasCorrectPte) {
        return hasCorrectPte && canUsePageTableAccessPhase(phase);
    }

    static boolean canStartJourneyFromTerminal(String phase) {
        return "terminal_journey_chosen".equals(phase);
    }
}
