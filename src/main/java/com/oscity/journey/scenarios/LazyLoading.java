package com.oscity.journey.scenarios;

import com.oscity.journey.JourneyScenario;
import java.util.Map;

public class LazyLoading implements JourneyScenario {

    @Override public String getDisplayName()      { return "Lazy Loading Journey"; }
    @Override public int    getNumber()           { return 6; }
    @Override public boolean isTlbHit()           { return false; }
    @Override public String getPermissionAnswer() { return "page_fault"; }
    @Override public String getPageFaultType()    { return "lazy_loading"; }
    @Override public boolean involvesCow()        { return false; }
    @Override public boolean involvesSwap()       { return true; }

    @Override
    public void initVars(Map<String, String> vars) {
        vars.put("va",          "0x4E");
        vars.put("vaBin",       "0100 1110");
        vars.put("process",     "Process 6");
        vars.put("operation",   "load");
        vars.put("instruction", "load mystical_grimoire.spells 0x4E");
        vars.put("vpn",         "0100");
        vars.put("vpnHex",      "0x4");
        vars.put("offset",      "1110");
        vars.put("offsetHex",   "0xE");
        vars.put("pfn",         "0x5");   // Final PFN after swap + disk load
        vars.put("file",        "mystical_grimoire.spells");
        vars.put("pageIndex",   "?");  // Revealed after second calculator visit
        vars.put("diskBlock",   "C");
        vars.put("expectedFloor", "1");
        vars.put("hex",  "0x4E");
        vars.put("optA", "0100 1101");
        vars.put("optB", "0100 1110");
        vars.put("optC", "0101 1110");
        // Page-index quiz vars (calculator visit 2)
        vars.put("optA_pg", "1");
        vars.put("optB_pg", "4");
        vars.put("optC_pg", "14");
        // Page size (revealed after entering Lazy Loading room)
        vars.put("pageSize", "?");
    }
}
