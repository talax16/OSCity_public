package com.oscity;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

class DiagnosticWarningLogsTest {

    @Test
    void missingJourneyAndAddressStateHasDiagnosticLogs() throws IOException {
        List<String> missing = new ArrayList<>();

        requireContains(
            Path.of("src/main/java/com/oscity/mechanics/PageTableManager.java"),
            "[PageTable] No journey for",
            missing
        );
        requireContains(
            Path.of("src/main/java/com/oscity/mechanics/PageTableManager.java"),
            "[PageTable] No vpnHex for",
            missing
        );
        requireContains(
            Path.of("src/main/java/com/oscity/mechanics/TLBRoomManager.java"),
            "[TLBRoom] No journey for",
            missing
        );
        requireContains(
            Path.of("src/main/java/com/oscity/mechanics/CalculatorListener.java"),
            "[Calc] askJourneyQuestion: no journey for",
            missing
        );

        assertTrue(missing.isEmpty(), "Missing journey/address diagnostic logs: " + missing);
    }

    @Test
    void missingLocationReferencesHaveDiagnosticLogs() throws IOException {
        List<String> missing = new ArrayList<>();

        requireContains(
            Path.of("src/main/java/com/oscity/world/LocationRegistry.java"),
            "No 'locations' section found in config.yml",
            missing
        );
        requireContains(
            Path.of("src/main/java/com/oscity/world/LocationRegistry.java"),
            "Location '\" + key + \"' missing 'world'",
            missing
        );
        requireContains(
            Path.of("src/main/java/com/oscity/world/LocationRegistry.java"),
            "Unknown location key:",
            missing
        );
        requireContains(
            Path.of("src/main/java/com/oscity/world/RoomRegistry.java"),
            "Room '\" + key + \"' missing 'world'",
            missing
        );
        requireContains(
            Path.of("src/main/java/com/oscity/world/RoomRegistry.java"),
            "Room '\" + key + \"' missing min/max bounds",
            missing
        );
        requireContains(
            Path.of("src/main/java/com/oscity/core/KernelGuardian.java"),
            "Cannot move NPC - location is null",
            missing
        );
        requireContains(
            Path.of("src/main/java/com/oscity/mechanics/ChoiceButtonHandler.java"),
            "ChoiceButtonHandler: no 'choiceButtons' in config.yml",
            missing
        );
        requireContains(
            Path.of("src/main/java/com/oscity/mechanics/ChoiceButtonHandler.java"),
            "[Signs] No config entry for signs.",
            missing
        );
        requireContains(
            Path.of("src/main/java/com/oscity/mechanics/ChoiceButtonHandler.java"),
            "[Signs] No sign block at signs.",
            missing
        );
        requireContains(
            Path.of("src/main/java/com/oscity/mechanics/JourneyMapManager.java"),
            "[JourneyMap] No config at chests.",
            missing
        );
        requireContains(
            Path.of("src/main/java/com/oscity/mechanics/JourneyMapManager.java"),
            "[JourneyMap] No chest block at chests.",
            missing
        );
        requireContains(
            Path.of("src/main/java/com/oscity/mechanics/DiskRoomManager.java"),
            "[DiskRoom] No config at",
            missing
        );
        requireContains(
            Path.of("src/main/java/com/oscity/mechanics/DiskRoomManager.java"),
            "[DiskRoom] No chest at",
            missing
        );
        requireContains(
            Path.of("src/main/java/com/oscity/mechanics/RAMRoomManager.java"),
            "[RAMRoom] updateSign: No sign at",
            missing
        );
        requireContains(
            Path.of("src/main/java/com/oscity/mechanics/TLBRoomManager.java"),
            "[TLBRoom] No sign at signs.tlb.vpn",
            missing
        );
        requireContains(
            Path.of("src/main/java/com/oscity/mechanics/TLBRoomManager.java"),
            "[TLBRoom] No chest at chests.tlb.chest",
            missing
        );

        assertTrue(missing.isEmpty(), "Missing location diagnostic logs: " + missing);
    }

    @Test
    void calculatorParseAndCheckpointQuestionFailuresHaveDiagnosticLogs() throws IOException {
        List<String> missing = new ArrayList<>();

        requireContains(
            Path.of("src/main/java/com/oscity/mechanics/CalculatorListener.java"),
            "parse error | phase=",
            missing
        );
        requireContains(
            Path.of("src/main/java/com/oscity/mechanics/CalculatorListener.java"),
            "[Calc] No question found for path:",
            missing
        );
        requireContains(
            Path.of("src/main/java/com/oscity/content/QuestionBank.java"),
            "QuestionBank: no question at",
            missing
        );
        requireContains(
            Path.of("src/main/java/com/oscity/mechanics/ChoiceButtonHandler.java"),
            "[Quiz] askQuestion: no question found at",
            missing
        );

        assertTrue(missing.isEmpty(), "Missing calculator/question diagnostic logs: " + missing);
    }

    @Test
    void missingDialogueAndHintContentHasDiagnosticsOrFallbacks() throws IOException {
        List<String> missing = new ArrayList<>();

        requireContains(
            Path.of("src/main/java/com/oscity/content/DialogueManager.java"),
            "DialogueManager: no content at",
            missing
        );
        requireContains(
            Path.of("src/main/java/com/oscity/core/GuardianInteractionHandler.java"),
            "[Guardian] No explanation found at:",
            missing
        );
        requireContains(
            Path.of("src/main/java/com/oscity/mechanics/HintSystem.java"),
            "[Hint] No hint found for phase=",
            missing
        );
        requireContains(
            Path.of("src/main/java/com/oscity/mechanics/HintSystem.java"),
            "feedback.hint_fallback",
            missing
        );

        assertTrue(missing.isEmpty(), "Missing dialogue/hint diagnostics: " + missing);
    }

    private static void requireContains(Path sourcePath, String expected, List<String> missing)
            throws IOException {
        String source = Files.readString(sourcePath);
        if (!source.contains(expected)) {
            missing.add(sourcePath + " -> " + expected);
        }
    }
}
