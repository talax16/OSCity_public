package com.oscity.mechanics;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChoiceButtonHandlerPureHelpersTest {

    @Test
    void buttonToPermissionAnswer_mapsCorrectly() {
        assertEquals("allow_access", ChoiceButtonRules.buttonToPermissionAnswer("btn1"));
        assertEquals("page_fault", ChoiceButtonRules.buttonToPermissionAnswer("btn2"));
        assertEquals("segfault", ChoiceButtonRules.buttonToPermissionAnswer("btn3"));
        assertEquals("protection_fault", ChoiceButtonRules.buttonToPermissionAnswer("btn4"));
        assertEquals("weird", ChoiceButtonRules.buttonToPermissionAnswer("weird"));
    }

    @Test
    void buttonToPageFaultType_mapsCorrectly() {
        assertEquals("lazy_allocation", ChoiceButtonRules.buttonToPageFaultType("btn1"));
        assertEquals("lazy_loading", ChoiceButtonRules.buttonToPageFaultType("btn2"));
        assertEquals("swapped_out", ChoiceButtonRules.buttonToPageFaultType("btn3"));
        assertEquals("", ChoiceButtonRules.buttonToPageFaultType("bad"));
    }

    @Test
    void permissionDecisionLabel_mapsCorrectly() {
        assertEquals("Allow Access", ChoiceButtonRules.permissionDecisionLabel("btn1"));
        assertEquals("Page Fault", ChoiceButtonRules.permissionDecisionLabel("btn2"));
        assertEquals("Segmentation Fault", ChoiceButtonRules.permissionDecisionLabel("btn3"));
        assertEquals("Protection Fault", ChoiceButtonRules.permissionDecisionLabel("btn4"));
        assertNull(ChoiceButtonRules.permissionDecisionLabel("bad"));
    }

    @Test
    void pageFaultTypeLabel_mapsCorrectly() {
        assertEquals("Lazy Allocation", ChoiceButtonRules.pageFaultTypeLabel("btn1"));
        assertEquals("Lazy Loading", ChoiceButtonRules.pageFaultTypeLabel("btn2"));
        assertEquals("Swapped out", ChoiceButtonRules.pageFaultTypeLabel("btn3"));
        assertNull(ChoiceButtonRules.pageFaultTypeLabel("btn4"));
    }

    @Test
    void lazyAllocDecisionLabel_mapsCorrectly() {
        assertEquals("ALLOCATE", ChoiceButtonRules.lazyAllocDecisionLabel("allocateLazy"));
        assertEquals("SWAP FROM DISK", ChoiceButtonRules.lazyAllocDecisionLabel("swapLazy"));
        assertNull(ChoiceButtonRules.lazyAllocDecisionLabel("bad"));
    }

    @Test
    void lazyAllocCowLabel_mapsCorrectly() {
        assertEquals("COW (COPY-ON-WRITE)", ChoiceButtonRules.lazyAllocCowLabel("cowLazyAlloc"));
        assertEquals("DENY THE WRITE", ChoiceButtonRules.lazyAllocCowLabel("btnLazyAlloc"));
        assertEquals("DO NOTHING", ChoiceButtonRules.lazyAllocCowLabel("nothing"));
        assertNull(ChoiceButtonRules.lazyAllocCowLabel("bad"));
    }

    @Test
    void cowDecisionLabel_mapsCorrectly() {
        assertEquals("ALLOCATE & COPY", ChoiceButtonRules.cowDecisionLabel("allocateCow"));
        assertEquals("TERMINATE PROCESS", ChoiceButtonRules.cowDecisionLabel("terminate"));
        assertNull(ChoiceButtonRules.cowDecisionLabel("bad"));
    }

    @Test
    void phasePredicates_acceptOnlyTheirIntendedPhases() {
        assertTrue(ChoiceButtonRules.isPermissionDecisionPhase("permission_decision"));
        assertFalse(ChoiceButtonRules.isPermissionDecisionPhase("page_fault_type"));
        assertFalse(ChoiceButtonRules.isPermissionDecisionPhase(null));

        assertTrue(ChoiceButtonRules.isPageFaultTypePhase("page_fault_type"));
        assertFalse(ChoiceButtonRules.isPageFaultTypePhase("permission_decision"));
        assertFalse(ChoiceButtonRules.isPageFaultTypePhase(null));

        assertTrue(ChoiceButtonRules.isLazyAllocDecisionPhase("lazy_alloc_decision"));
        assertFalse(ChoiceButtonRules.isLazyAllocDecisionPhase("lazy_alloc_cow"));
        assertFalse(ChoiceButtonRules.isLazyAllocDecisionPhase(null));

        assertTrue(ChoiceButtonRules.isLazyAllocCowPhase("lazy_alloc_cow"));
        assertFalse(ChoiceButtonRules.isLazyAllocCowPhase("lazy_alloc_decision"));
        assertFalse(ChoiceButtonRules.isLazyAllocCowPhase(null));

        assertTrue(ChoiceButtonRules.isCowDecisionPhase("cow_decision"));
        assertFalse(ChoiceButtonRules.isCowDecisionPhase("cow_decision_after"));
        assertFalse(ChoiceButtonRules.isCowDecisionPhase(null));
    }

    @Test
    void smartButtonPhasePredicates_acceptOnlyUnlockedPhases() {
        assertTrue(ChoiceButtonRules.canUseCowToRam("cow_decision_after"));
        assertFalse(ChoiceButtonRules.canUseCowToRam("cow_decision"));
        assertFalse(ChoiceButtonRules.canUseCowToRam(null));

        assertTrue(ChoiceButtonRules.canUseLoadingTeleport("lazy_loading_entered"));
        assertTrue(ChoiceButtonRules.canUseLoadingTeleport("lazy_loading_returned"));
        assertFalse(ChoiceButtonRules.canUseLoadingTeleport("lazy_alloc_decision"));
        assertFalse(ChoiceButtonRules.canUseLoadingTeleport(null));

        assertTrue(ChoiceButtonRules.canUseSwapToRam("swap_after_eviction"));
        assertFalse(ChoiceButtonRules.canUseSwapToRam("swap_entered"));
        assertFalse(ChoiceButtonRules.canUseSwapToRam(null));
    }

    @Test
    void calculatorContinuePhasePredicates_distinguishDoneAndBlockedPhases() {
        assertTrue(ChoiceButtonRules.isCalculatorContinueDonePhase("calculator_from_tlb_done"));
        assertTrue(ChoiceButtonRules.isCalculatorContinueDonePhase("calculator_from_lazy_loading_done"));
        assertFalse(ChoiceButtonRules.isCalculatorContinueDonePhase("calculator_from_tlb"));
        assertFalse(ChoiceButtonRules.isCalculatorContinueDonePhase(null));

        assertTrue(ChoiceButtonRules.isCalculatorContinueBlockedPhase("calculator_from_tlb"));
        assertTrue(ChoiceButtonRules.isCalculatorContinueBlockedPhase("calculator_from_lazy_loading"));
        assertFalse(ChoiceButtonRules.isCalculatorContinueBlockedPhase("calculator_from_tlb_done"));
        assertFalse(ChoiceButtonRules.isCalculatorContinueBlockedPhase(null));
    }
}
