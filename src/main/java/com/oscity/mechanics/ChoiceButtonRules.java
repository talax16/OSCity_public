package com.oscity.mechanics;

/**
 * Pure button/phase rules used by {@link ChoiceButtonHandler}.
 * No Bukkit, player, plugin, inventory, sign, door, or teleport side effects belong here.
 */
final class ChoiceButtonRules {

    private ChoiceButtonRules() {}

    static String permissionDecisionLabel(String key) {
        switch (key) {
            case "btn1": return "Allow Access";
            case "btn2": return "Page Fault";
            case "btn3": return "Segmentation Fault";
            case "btn4": return "Protection Fault";
            default:     return null;
        }
    }

    static String pageFaultTypeLabel(String key) {
        switch (key) {
            case "btn1": return "Lazy Allocation";
            case "btn2": return "Lazy Loading";
            case "btn3": return "Swapped out";
            default:     return null;
        }
    }

    static String lazyAllocDecisionLabel(String key) {
        switch (key) {
            case "allocateLazy": return "ALLOCATE";
            case "swapLazy":     return "SWAP FROM DISK";
            default:             return null;
        }
    }

    static String lazyAllocCowLabel(String key) {
        switch (key) {
            case "cowLazyAlloc": return "COW (COPY-ON-WRITE)";
            case "btnLazyAlloc": return "DENY THE WRITE";
            case "nothing":      return "DO NOTHING";
            default:             return null;
        }
    }

    static String cowDecisionLabel(String key) {
        switch (key) {
            case "allocateCow": return "ALLOCATE & COPY";
            case "terminate":   return "TERMINATE PROCESS";
            default:            return null;
        }
    }

    static String buttonToPermissionAnswer(String buttonKey) {
        switch (buttonKey) {
            case "btn1": return "allow_access";
            case "btn2": return "page_fault";
            case "btn3": return "segfault";
            case "btn4": return "protection_fault";
            default:     return buttonKey;
        }
    }

    static String buttonToPageFaultType(String buttonKey) {
        switch (buttonKey) {
            case "btn1": return "lazy_allocation";
            case "btn2": return "lazy_loading";
            case "btn3": return "swapped_out";
            default:     return "";
        }
    }

    static boolean isPermissionDecisionPhase(String phase) {
        return "permission_decision".equals(phase);
    }

    static boolean isPageFaultTypePhase(String phase) {
        return "page_fault_type".equals(phase);
    }

    static boolean isLazyAllocDecisionPhase(String phase) {
        return "lazy_alloc_decision".equals(phase);
    }

    static boolean isLazyAllocCowPhase(String phase) {
        return "lazy_alloc_cow".equals(phase);
    }

    static boolean isCowDecisionPhase(String phase) {
        return "cow_decision".equals(phase);
    }

    static boolean canUseCowToRam(String phase) {
        return "cow_decision_after".equals(phase);
    }

    static boolean canUseLoadingTeleport(String phase) {
        return "lazy_loading_entered".equals(phase) || "lazy_loading_returned".equals(phase);
    }

    static boolean canUseSwapToRam(String phase) {
        return "swap_after_eviction".equals(phase);
    }

    static boolean isCalculatorContinueDonePhase(String phase) {
        return "calculator_from_tlb_done".equals(phase)
            || "calculator_from_lazy_loading_done".equals(phase);
    }

    static boolean isCalculatorContinueBlockedPhase(String phase) {
        return "calculator_from_tlb".equals(phase)
            || "calculator_from_lazy_loading".equals(phase);
    }
}
