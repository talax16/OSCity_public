package com.oscity;

import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertTrue;

class ResourceReferenceKeysTest {

    private static final Path MAIN_SOURCE = Path.of("src/main/java");
    private static final Path MESSAGES_YML = Path.of("src/main/resources/messages.yml");
    private static final Path DIALOGUE_YML = Path.of("src/main/resources/dialogue.yml");
    private static final Path QUESTIONS_YML = Path.of("src/main/resources/questions.yml");

    @Test
    void literalMessageKeysReferencedBySourceExist() throws IOException {
        Map<String, Object> messages = loadYaml(MESSAGES_YML);
        Set<String> keys = extractLiteralKeys(Pattern.compile("getMessage\\(\\s*\"([^\"]+)\""));

        assertAllPathsExist(messages, keys, "messages.yml");
    }

    @Test
    void literalDialogueKeysReferencedBySourceExist() throws IOException {
        Map<String, Object> dialogue = loadYaml(DIALOGUE_YML);
        Set<String> keys = extractLiteralKeys(Pattern.compile(
            "\\bdialogue(?:Manager)?\\.speak(?:Delayed|Instant)?\\(\\s*[^,]+,\\s*\"([^\"]+)\""
        ));

        assertAllPathsExist(dialogue, keys, "dialogue.yml");
    }

    @Test
    void literalQuestionKeysReferencedBySourceExist() throws IOException {
        Map<String, Object> questions = loadYaml(QUESTIONS_YML);
        Set<String> keys = new TreeSet<>();

        keys.addAll(extractLiteralKeys(Pattern.compile("\\bquestionBank\\.getQuestion\\(\\s*\"([^\"]+)\"")));
        keys.addAll(extractLiteralKeys(Pattern.compile("\\baskQuestion\\(\\s*[^,]+,\\s*\"([^\"]+)\"")));

        assertAllPathsExist(questions, keys, "questions.yml");
    }

    private static Set<String> extractLiteralKeys(Pattern pattern) throws IOException {
        Set<String> keys = new TreeSet<>();

        try (Stream<Path> paths = Files.walk(MAIN_SOURCE)) {
            for (Path path : paths.filter(p -> p.toString().endsWith(".java")).toList()) {
                Matcher matcher = pattern.matcher(Files.readString(path));
                while (matcher.find()) {
                    keys.add(matcher.group(1));
                }
            }
        }

        return keys;
    }

    private static void assertAllPathsExist(Map<String, Object> root, Set<String> keys, String resourceName) {
        List<String> missing = new ArrayList<>();

        for (String key : keys) {
            if (!hasPath(root, key)) {
                missing.add(key);
            }
        }

        assertTrue(missing.isEmpty(), "Missing keys in " + resourceName + ": " + missing);
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
