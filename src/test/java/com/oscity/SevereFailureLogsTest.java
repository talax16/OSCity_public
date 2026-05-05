package com.oscity;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

class SevereFailureLogsTest {

    @Test
    void criticalStartupAndInfrastructureFailuresUseSevereLogs() throws IOException {
        List<String> missing = new ArrayList<>();

        requireContains(
            Path.of("src/main/java/com/oscity/OSCity.java"),
            "getLogger().severe(\"Citizens not found! Kernel Guardian will not work.\")",
            missing
        );
        requireContains(
            Path.of("src/main/java/com/oscity/OSCity.java"),
            "getLogger().severe(\"Please install Citizens:",
            missing
        );
        requireContains(
            Path.of("src/main/java/com/oscity/world/WorldManager.java"),
            "plugin.getLogger().severe(\"World '\" + worldName + \"' not found! Is the world loaded?\")",
            missing
        );
        requireContains(
            Path.of("src/main/java/com/oscity/world/StructureManager.java"),
            "plugin.getLogger().severe(\"StructureManager: game world is not loaded, skipping init.\")",
            missing
        );
        requireContains(
            Path.of("src/main/java/com/oscity/core/RoomChangeListener.java"),
            "plugin.getLogger().severe(\"Cannot spawn guardian - initialSpawn not found!\")",
            missing
        );
        requireContains(
            Path.of("src/main/java/com/oscity/config/ConfigManager.java"),
            "plugin.getLogger().log(Level.SEVERE, \"Could not save \" + name, e)",
            missing
        );
        requireContains(
            Path.of("src/main/java/com/oscity/core/KernelGuardian.java"),
            "plugin.getLogger().severe(\"Failed to apply computer skin: \" + e.getMessage())",
            missing
        );

        assertTrue(missing.isEmpty(), "Missing severe-level critical failure logs: " + missing);
    }

    private static void requireContains(Path sourcePath, String expected, List<String> missing)
            throws IOException {
        String source = Files.readString(sourcePath);
        if (!source.contains(expected)) {
            missing.add(sourcePath + " -> " + expected);
        }
    }
}
