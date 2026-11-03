package judge;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.lang.reflect.Method;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

import judge.core.Scenario;
import judge.core.SourceRef;

/** Runs without a target: guards the judge's own rules. */
class JudgeSelfCheckTest {

    @Test
    void everyScenarioTestCitesTheOriginalSource() throws Exception {
        List<String> missing = new ArrayList<>();
        List<String> badPath = new ArrayList<>();
        Path repo = Path.of("..").toAbsolutePath().normalize();
        int tests = 0;
        for (Class<?> c : scenarioClasses()) {
            for (Method m : c.getDeclaredMethods()) {
                if (!m.isAnnotationPresent(Test.class)) continue;
                tests++;
                SourceRef[] refs = m.getAnnotationsByType(SourceRef.class);
                if (refs.length == 0) missing.add(c.getSimpleName() + "." + m.getName());
                for (SourceRef r : refs) {
                    String file = r.value().replaceAll(":[0-9,\\-]+$", "");
                    if (!Files.isRegularFile(repo.resolve(file))) badPath.add(c.getSimpleName() + "." + m.getName() + " -> " + r.value());
                }
            }
        }
        assertTrue(tests > 0, "no scenario tests found");
        assertTrue(missing.isEmpty(), "scenario tests without @SourceRef: " + missing);
        assertTrue(badPath.isEmpty(), "@SourceRef to files that do not exist: " + badPath);
    }

    private static List<Class<?>> scenarioClasses() throws IOException, URISyntaxException, ClassNotFoundException {
        Path root = Path.of(JudgeSelfCheckTest.class.getResource("/").toURI()).resolve("judge/scenarios");
        List<Class<?>> out = new ArrayList<>();
        if (!Files.isDirectory(root)) return out;
        try (Stream<Path> s = Files.walk(root)) {
            for (Path p : s.filter(x -> x.toString().endsWith(".class") && !x.toString().contains("$")).toList()) {
                String name = "judge.scenarios." + root.relativize(p).toString().replace('/', '.').replaceAll("\\.class$", "");
                Class<?> c = Class.forName(name);
                if (Scenario.class.isAssignableFrom(c)) out.add(c);
            }
        }
        return out;
    }
}
