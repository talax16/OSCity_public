package com.oscity.mechanics;

import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertTrue;

class ChoiceButtonHandlerMessageKeysTest {

    private static final Path CHOICE_HANDLER =
        Path.of("src/main/java/com/oscity/mechanics/ChoiceButtonHandler.java");

    private static final Path MESSAGES_YML =
        Path.of("src/main/resources/messages.yml");

    private static final Path DIALOGUE_YML =
        Path.of("src/main/resources/dialogue.yml");

    @Test
    void allGetMessageKeysExistInMessagesYml() throws IOException {
        String source = Files.readString(CHOICE_HANDLER);
        Map<String, Object> messages = loadYaml(MESSAGES_YML);

        Set<String> keys = extractMessageKeys(source);
        List<String> missing = new ArrayList<>();

        for (String key : keys) {
            if (!hasPath(messages, key)) {
                missing.add(key);
            }
        }
        System.out.println("Missing message keys: " + missing);
        assertTrue(
            missing.isEmpty(),
            "Missing message keys in messages.yml: " + missing
        );
    }

    @Test
    void allDialogueKeysExistInDialogueYml() throws IOException {
        String source = Files.readString(CHOICE_HANDLER);
        Map<String, Object> dialogue = loadYaml(DIALOGUE_YML);

        Set<String> keys = extractDialogueKeys(source);
        List<String> missing = new ArrayList<>();

        for (String key : keys) {
            if (!hasPath(dialogue, key)) {
                missing.add(key);
            }
        }

        assertTrue(
            missing.isEmpty(),
            "Missing dialogue keys in dialogue.yml: " + missing
        );
    }

    private static Set<String> extractMessageKeys(String source) {
        Pattern pattern = Pattern.compile("getMessage\\(\"([^\"]+)\"");
        Matcher matcher = pattern.matcher(source);

        Set<String> keys = new TreeSet<>();
        while (matcher.find()) {
            keys.add(matcher.group(1));
        }
        return keys;
    }

    private static Set<String> extractDialogueKeys(String source) {
        Pattern pattern = Pattern.compile(
            "dialogue\\.speak(?:Instant)?\\([^,]+,\\s*\"([^\"]+)\""
        );
        Matcher matcher = pattern.matcher(source);

        Set<String> keys = new TreeSet<>();
        while (matcher.find()) {
            keys.add(matcher.group(1));
        }
        return keys;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> loadYaml(Path path) throws IOException {
        Yaml yaml = new Yaml();
        try (var reader = Files.newBufferedReader(path)) {
            Object loaded = yaml.load(reader);
            if (loaded == null) {
                return new HashMap<>();
            }
            return (Map<String, Object>) loaded;
        }
    }

    @SuppressWarnings("unchecked")
    private static boolean hasPath(Map<String, Object> root, String dottedPath) {
        Object current = root;

        for (String part : dottedPath.split("\\.")) {
            if (!(current instanceof Map<?, ?> map)) {
                return false;
            }
            if (!map.containsKey(part)) {
                return false;
            }
            current = map.get(part);
        }

        return true;
    }
}