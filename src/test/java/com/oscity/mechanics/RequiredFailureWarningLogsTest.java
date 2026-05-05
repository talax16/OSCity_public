package com.oscity.mechanics;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

class RequiredFailureWarningLogsTest {

    private static final Path CHOICE_HANDLER =
        Path.of("src/main/java/com/oscity/mechanics/ChoiceButtonHandler.java");

    private static final Path TELEPORT_MANAGER =
        Path.of("src/main/java/com/oscity/mechanics/TeleportManager.java");

    private static final Path JOURNEY_MAP_MANAGER =
        Path.of("src/main/java/com/oscity/mechanics/JourneyMapManager.java");

    @Test
    void requiredRouteFailuresHaveWarningLogs() throws IOException {
        String choiceHandler = Files.readString(CHOICE_HANDLER);
        String teleportManager = Files.readString(TELEPORT_MANAGER);

        List<String> missing = new ArrayList<>();
        requireContains(choiceHandler, "tpButtons.\" + tpKey + \" missing world", missing);
        requireContains(choiceHandler, "tpButtons.\" + tpKey + \" refers to missing world", missing);
        requireContains(choiceHandler, "doorOpen.\" + key + \" missing world", missing);
        requireContains(choiceHandler, "doorOpen.\" + key + \" refers to missing world", missing);
        requireContains(choiceHandler, "TP destination not found", missing);
        requireContains(choiceHandler, "is not inside any registered room; room-entry logic skipped", missing);
        requireContains(teleportManager, "not found for tpButtons.", missing);
        requireContains(teleportManager, "is not inside any registered room; room-entry logic skipped", missing);

        assertTrue(missing.isEmpty(), "Missing required route warning logs: " + missing);
    }

    @Test
    void missingPhaseGatingQuestionsHaveWarningLogs() throws IOException {
        String choiceHandler = Files.readString(CHOICE_HANDLER);

        List<String> missing = new ArrayList<>();
        requireContains(choiceHandler, "Missing pending phase-gating question", missing);
        requireContains(choiceHandler, "askQuestion: no question found", missing);

        assertTrue(missing.isEmpty(), "Missing phase-gating question warning logs: " + missing);
    }

    @Test
    void missingRequiredInventoryContentHasWarningLogs() throws IOException {
        String choiceHandler = Files.readString(CHOICE_HANDLER);

        List<String> missing = new ArrayList<>();
        requireContains(choiceHandler, "Required swap book missing", missing);
        requireContains(choiceHandler, "Required lazy-loading book missing", missing);
        requireContains(choiceHandler, "Required PTE map missing", missing);
        requireContains(choiceHandler, "Required journey map missing", missing);

        assertTrue(missing.isEmpty(), "Missing required inventory/content warning logs: " + missing);
    }

    @Test
    void missingJourneyMapViewHasWarningLogs() throws IOException {
        String journeyMapManager = Files.readString(JOURNEY_MAP_MANAGER);

        List<String> missing = new ArrayList<>();
        requireContains(journeyMapManager, "Cannot update journey map for", missing);
        requireContains(journeyMapManager, "Cannot update journey map after calculator", missing);
        requireContains(journeyMapManager, "Cannot update journey map after TLB hit", missing);

        assertTrue(missing.isEmpty(), "Missing journey map warning logs: " + missing);
    }

    private static void requireContains(String source, String expected, List<String> missing) {
        if (!source.contains(expected)) {
            missing.add(expected);
        }
    }
}
