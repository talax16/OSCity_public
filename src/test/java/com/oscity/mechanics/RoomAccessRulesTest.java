package com.oscity.mechanics;

import com.oscity.journey.Journey;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RoomAccessRulesTest {

    @Test
    void pageFaultCorridorRequiresPageFaultTypePhaseAndLazyJourney() {
        assertTrue(RoomAccessRules.canOpenPageFaultCorridor("page_fault_type", Journey.LAZY_ALLOCATION));
        assertTrue(RoomAccessRules.canOpenPageFaultCorridor("page_fault_type", Journey.LAZY_LOADING));

        assertFalse(RoomAccessRules.canOpenPageFaultCorridor("page_fault_type", Journey.SWAPPED_OUT));
        assertFalse(RoomAccessRules.canOpenPageFaultCorridor("permission_decision", Journey.LAZY_ALLOCATION));
        assertFalse(RoomAccessRules.canOpenPageFaultCorridor("permission_decision", Journey.LAZY_LOADING));
        assertFalse(RoomAccessRules.canOpenPageFaultCorridor(null, Journey.LAZY_LOADING));
        assertFalse(RoomAccessRules.canOpenPageFaultCorridor("page_fault_type", null));
    }

    @Test
    void lazyRoomDoorsRequireMatchingJourney() {
        assertTrue(RoomAccessRules.canOpenLazyLoading(Journey.LAZY_LOADING));
        assertFalse(RoomAccessRules.canOpenLazyLoading(Journey.LAZY_ALLOCATION));
        assertFalse(RoomAccessRules.canOpenLazyLoading(null));

        assertTrue(RoomAccessRules.canOpenLazyAllocation(Journey.LAZY_ALLOCATION));
        assertFalse(RoomAccessRules.canOpenLazyAllocation(Journey.LAZY_LOADING));
        assertFalse(RoomAccessRules.canOpenLazyAllocation(null));
    }

    @Test
    void pageTableChamberRequiresCorrectFloorOrAcquiredPteAndCorrectMap() {
        assertTrue(RoomAccessRules.canUsePageTableAccessPhase("correct_floor"));
        assertTrue(RoomAccessRules.canUsePageTableAccessPhase("acquired_pte"));
        assertFalse(RoomAccessRules.canUsePageTableAccessPhase("wrong_floor"));
        assertFalse(RoomAccessRules.canUsePageTableAccessPhase(null));

        assertTrue(RoomAccessRules.canUsePageTableToChamber("correct_floor", true));
        assertTrue(RoomAccessRules.canUsePageTableToChamber("acquired_pte", true));
        assertFalse(RoomAccessRules.canUsePageTableToChamber("correct_floor", false));
        assertFalse(RoomAccessRules.canUsePageTableToChamber("acquired_pte", false));
        assertFalse(RoomAccessRules.canUsePageTableToChamber("wrong_floor", true));
        assertFalse(RoomAccessRules.canUsePageTableToChamber(null, true));
    }

    @Test
    void terminalStartRequiresJourneyChosenPhase() {
        assertTrue(RoomAccessRules.canStartJourneyFromTerminal("terminal_journey_chosen"));
        assertFalse(RoomAccessRules.canStartJourneyFromTerminal("terminal_spawn"));
        assertFalse(RoomAccessRules.canStartJourneyFromTerminal("terminal_path_select"));
        assertFalse(RoomAccessRules.canStartJourneyFromTerminal(null));
    }
}
