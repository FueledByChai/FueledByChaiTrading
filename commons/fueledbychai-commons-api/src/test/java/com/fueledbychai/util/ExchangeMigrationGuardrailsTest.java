package com.fueledbychai.util;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

class ExchangeMigrationGuardrailsTest {

    @Test
    void legacyApiFactoriesOnlyUsedInProvidersOrFactoryClasses() throws IOException {
        List<String> violations = findReferences(
                List.of("ParadexApiFactory", "HyperliquidApiFactory", "LighterApiFactory", "BinanceApiFactory"),
                path -> path.endsWith("ApiFactory.java")
                        || path.endsWith("RestApiProvider.java")
                        || path.endsWith("WebSocketApiProvider.java"));

        assertTrue(violations.isEmpty(), "Legacy API factory references found outside provider/factory classes:\n"
                + String.join("\n", violations));
    }

    @Test
    void exchangeSpecificTickerRegistrySingletonsOnlyUsedInRegistryProviderClasses() throws IOException {
        List<String> violations = findReferences(
                List.of("ParadexTickerRegistry.getInstance(", "HyperliquidTickerRegistry.getInstance(",
                        "BinanceTickerRegistry.getInstance(", "LighterTickerRegistry.getInstance("),
                path -> path.endsWith("TickerRegistry.java")
                        || path.endsWith("TickerRegistryProvider.java"));

        assertTrue(violations.isEmpty(),
                "Exchange-specific ticker singleton usage found outside registry/provider classes:\n"
                        + String.join("\n", violations));
    }

    private List<String> findReferences(List<String> tokens, Predicate<Path> allowedFile) throws IOException {
        Path repoRoot = findRepoRoot();
        List<String> violations = new ArrayList<>();

        for (String directory : List.of("commons", "implementations", "fueledbychai-api")) {
            Path root = repoRoot.resolve(directory);
            if (!Files.isDirectory(root)) {
                continue;
            }
            try (Stream<Path> stream = Files.walk(root)) {
                stream.filter(Files::isRegularFile)
                        .filter(path -> path.toString().endsWith(".java"))
                        .filter(path -> path.toString().contains("/src/main/"))
                        .forEach(path -> {
                            try {
                                String content = Files.readString(path);
                                for (String token : tokens) {
                                    if (content.contains(token) && !allowedFile.test(path)) {
                                        violations.add(repoRoot.relativize(path).toString() + " contains " + token);
                                    }
                                }
                            } catch (IOException e) {
                                throw new RuntimeException("Failed reading " + path, e);
                            }
                        });
            }
        }

        return violations;
    }

    private Path findRepoRoot() {
        Path current = Path.of(System.getProperty("user.dir")).toAbsolutePath();
        while (current != null) {
            boolean hasCommons = Files.isDirectory(current.resolve("commons"));
            boolean hasImplementations = Files.isDirectory(current.resolve("implementations"));
            boolean hasRootPom = Files.isRegularFile(current.resolve("pom.xml"));
            if (hasCommons && hasImplementations && hasRootPom) {
                return current;
            }
            current = current.getParent();
        }
        throw new IllegalStateException("Unable to locate repository root for migration guardrail test");
    }
}
