package com.oscity.journey;

import org.junit.jupiter.api.Test;

import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JourneyScenarioInvariantsTest {

    @Test
    void journeyNumbersAreUniqueAndDisplayNamesArePresent() {
        Set<Integer> numbers = new HashSet<>();

        for (Journey journey : Journey.values()) {
            assertTrue(numbers.add(journey.number), "Duplicate journey number: " + journey.number);
            assertFalse(journey.displayName == null || journey.displayName.isBlank(),
                journey.name() + " must have a display name");
        }
    }

    @Test
    void addressVariablesAreInternallyConsistent() {
        for (Journey journey : Journey.values()) {
            Map<String, String> vars = varsFor(journey);

            int vpn = binary(vars.get("vpn"));
            int offset = binary(vars.get("offset"));
            int va = hex(vars.get("va"));

            assertEquals(vpn, hex(vars.get("vpnHex")), journey.name() + " vpnHex must match vpn");
            assertEquals(offset, hex(vars.get("offsetHex")), journey.name() + " offsetHex must match offset");
            assertEquals((vpn << 4) | offset, va, journey.name() + " va must equal vpn+offset");
        }
    }

    @Test
    void expectedFloorMatchesDirectoryBitsForPageTableJourneys() {
        for (Journey journey : Journey.values()) {
            Map<String, String> vars = varsFor(journey);
            String expectedFloor = vars.get("expectedFloor");

            if (journey == Journey.LUCKY) {
                assertEquals("?", expectedFloor, "LUCKY is a TLB hit and should not require page-table floor routing");
                continue;
            }

            int vpn = hex(vars.get("vpnHex"));
            int directoryBits = vpn >> 2;
            assertEquals(String.valueOf(directoryBits), expectedFloor,
                journey.name() + " expectedFloor must come from VPN DIR bits");
        }
    }

    @Test
    void journeyTypeFlagsMatchTheScenarioRoutes() {
        Set<Journey> pageFaultJourneys = EnumSet.of(
            Journey.SWAPPED_OUT,
            Journey.LAZY_LOADING,
            Journey.LAZY_ALLOCATION
        );
        Set<Journey> cowJourneys = EnumSet.of(
            Journey.PURE_COW,
            Journey.LAZY_ALLOCATION
        );
        Set<Journey> swapJourneys = EnumSet.of(
            Journey.SWAPPED_OUT,
            Journey.LAZY_LOADING,
            Journey.LAZY_ALLOCATION
        );

        for (Journey journey : Journey.values()) {
            assertEquals(journey == Journey.LUCKY, journey.isTlbHit,
                journey.name() + " TLB hit flag drifted");
            assertEquals(cowJourneys.contains(journey), journey.involvesCow(),
                journey.name() + " COW flag drifted");
            assertEquals(swapJourneys.contains(journey), journey.involvesSwap(),
                journey.name() + " swap flag drifted");

            if (pageFaultJourneys.contains(journey)) {
                assertEquals("page_fault", journey.permissionAnswer,
                    journey.name() + " page-fault journey must branch through page_fault");
                assertFalse(journey.pageFaultType == null || journey.pageFaultType.isBlank(),
                    journey.name() + " page-fault journey must define a subtype");
            } else {
                assertNull(journey.pageFaultType, journey.name() + " non-page-fault journey must not define a subtype");
            }
        }
    }

    private static Map<String, String> varsFor(Journey journey) {
        Map<String, String> vars = new HashMap<>();
        journey.initVars(vars);
        return vars;
    }

    private static int binary(String value) {
        return Integer.parseInt(value.replace(" ", ""), 2);
    }

    private static int hex(String value) {
        return Integer.parseInt(value.replace("0x", "").replace("0X", ""), 16);
    }
}
