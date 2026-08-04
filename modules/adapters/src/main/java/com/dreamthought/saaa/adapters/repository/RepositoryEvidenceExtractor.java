package com.dreamthought.saaa.adapters.repository;

import com.dreamthought.saaa.domain.EvidenceAuthority;
import com.dreamthought.saaa.domain.EvidenceDocument;
import com.dreamthought.saaa.domain.GraphEdge;
import com.dreamthought.saaa.domain.RelationshipType;
import com.dreamthought.saaa.domain.RepositoryProjection;
import com.dreamthought.saaa.domain.SourceReference;
import com.dreamthought.saaa.adapters.git.GitRepositoryRevision;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/** Small, deterministic repository fact extractor. It deliberately avoids inferred call graphs. */
public final class RepositoryEvidenceExtractor {
    public static final String SCHEMA_VERSION = "graph-schema-v1";

    private static final Pattern PACKAGE = Pattern.compile("(?m)^package\\s+([a-zA-Z0-9_.]+)\\s*;");
    private static final Pattern DECLARATION = Pattern.compile(
            "(?m)^(?:public\\s+)?(?:final\\s+|sealed\\s+|non-sealed\\s+|abstract\\s+)*"
                    + "(?:class|record|interface|enum)\\s+([A-Za-z_$][A-Za-z0-9_$]*)");
    private static final Pattern IMPORT = Pattern.compile("(?m)^import\\s+(com\\.dreamthought\\.saaa\\.[A-Za-z0-9_.$]+)\\s*;");
    private static final Set<String> RELATION_KEYS = Set.of(
            "relates_to", "depends_on", "consumes", "produces", "decisions", "risks", "open_questions");

    public RepositoryProjection extract(Path repositoryRoot, String repositoryRevision) {
        Path root = repositoryRoot.toAbsolutePath().normalize();
        return extract(root, repositoryRevision, GitRepositoryRevision.repositoryId(root));
    }

    public RepositoryProjection extract(
            Path repositoryRoot, String repositoryRevision, String repositoryId) {
        Path root = repositoryRoot.toAbsolutePath().normalize();
        var nodes = new LinkedHashMap<String, EvidenceDocument>();
        var edges = new LinkedHashSet<GraphEdge>();

        extractKnowledge(root, repositoryRevision, nodes, edges);
        extractSpecs(root, repositoryRevision, nodes, edges);
        extractJava(root, repositoryRevision, nodes, edges);

        List<GraphEdge> resolvableEdges = edges.stream()
                .filter(edge -> nodes.containsKey(edge.sourceId()) && nodes.containsKey(edge.targetId()))
                .toList();
        return new RepositoryProjection(
                repositoryId,
                repositoryRevision,
                SCHEMA_VERSION,
                List.copyOf(nodes.values()),
                resolvableEdges);
    }

    private static void extractKnowledge(
            Path root,
            String revision,
            Map<String, EvidenceDocument> nodes,
            Set<GraphEdge> edges
    ) {
        Path knowledge = root.resolve(".agents/knowledge");
        for (Path path : files(knowledge, ".md")) {
            String relative = relative(root, path);
            if (relative.contains("/templates/") || relative.endsWith("/README.md")
                    || relative.endsWith("/index.md") || relative.endsWith("/TAXONOMY.md")) {
                continue;
            }
            String text = read(path);
            Map<String, List<String>> frontmatter = frontmatter(text);
            String id = first(frontmatter, "id");
            if (id == null) {
                continue;
            }
            String type = valueOr(frontmatter, "type", "knowledge");
            String title = valueOr(frontmatter, "title", id);
            String summary = valueOr(frontmatter, "summary", title);
            String status = valueOr(frontmatter, "status", "proposed");
            EvidenceDocument document = document(
                    id,
                    id,
                    knowledgeKind(type),
                    revision,
                    "title: " + title + "\nsummary: " + summary,
                    authority(status),
                    status,
                    relative,
                    id);
            nodes.merge(id, document, RepositoryEvidenceExtractor::mergeSources);
            for (String key : RELATION_KEYS) {
                for (String target : frontmatter.getOrDefault(key, List.of())) {
                    edges.add(new GraphEdge(id, relationship(key), target, "declared " + key));
                }
            }
        }
    }

    private static void extractSpecs(
            Path root,
            String revision,
            Map<String, EvidenceDocument> nodes,
            Set<GraphEdge> edges
    ) {
        var candidates = new ArrayList<Path>();
        candidates.addAll(files(root.resolve("specs/capabilities"), ".toon"));
        for (Path path : files(root.resolve("specs/changes"), ".toon")) {
            if (path.getFileName().toString().equals("change.toon")) {
                candidates.add(path);
            }
        }
        candidates.sort(Comparator.comparing(Path::toString));
        for (Path path : candidates) {
            String text = read(path);
            String id = firstIndentedScalar(text, "id");
            if (id == null) {
                continue;
            }
            String title = firstNonNull(firstIndentedScalar(text, "title"), firstIndentedScalar(text, "name"), id);
            String summary = firstNonNull(firstIndentedScalar(text, "intent"), firstIndentedScalar(text, "summary"), title);
            String status = firstNonNull(firstIndentedScalar(text, "status"), "proposed");
            String kind = id.startsWith("CAP-") ? "Capability" : "ChangeSpec";
            nodes.put(id, document(
                    id,
                    id,
                    kind,
                    revision,
                    "title: " + title + "\nsummary: " + summary,
                    authority(status),
                    status,
                    relative(root, path),
                    id));
            for (String target : firstList(text, "relates_to")) {
                edges.add(new GraphEdge(id, RelationshipType.RELATES_TO, target, "declared relates_to"));
            }
        }
    }

    private static void extractJava(
            Path root,
            String revision,
            Map<String, EvidenceDocument> nodes,
            Set<GraphEdge> edges
    ) {
        List<Path> javaFiles = files(root.resolve("modules"), ".java");
        var productionTypes = new LinkedHashMap<String, String>();
        var typeTexts = new LinkedHashMap<String, String>();
        var testTypes = new LinkedHashSet<String>();

        for (Path path : javaFiles) {
            String relative = relative(root, path);
            String text = read(path);
            Matcher packageMatcher = PACKAGE.matcher(text);
            Matcher declarationMatcher = DECLARATION.matcher(text);
            if (!packageMatcher.find() || !declarationMatcher.find()) {
                continue;
            }
            String packageName = packageMatcher.group(1);
            String simpleName = declarationMatcher.group(1);
            String fqcn = packageName + "." + simpleName;
            String typeId = "type:" + fqcn;
            String fileId = "file:" + relative;
            String module = relative.split("/")[1];
            String moduleId = "module:" + module;
            boolean test = relative.contains("/src/test/")
                    || relative.contains("/src/acceptanceTest/")
                    || relative.contains("/src/integrationTest/");

            nodes.putIfAbsent(moduleId, document(
                    moduleId, module, "Module", revision,
                    "Gradle module " + module, EvidenceAuthority.CANONICAL, "active",
                    "modules/" + module + "/build.gradle.kts", module));
            nodes.put(fileId, document(
                    fileId, relative, "SourceFile", revision,
                    "source file: " + relative, EvidenceAuthority.CANONICAL, "active", relative, relative));
            nodes.put(typeId, document(
                    typeId, fqcn, test ? "Test" : "Type", revision,
                    semanticTypeText(fqcn, module, relative, text),
                    EvidenceAuthority.CANONICAL, "active", relative, fqcn));
            edges.add(new GraphEdge(moduleId, RelationshipType.CONTAINS, fileId, "module owns source file"));
            edges.add(new GraphEdge(fileId, RelationshipType.DECLARES, typeId, "source file declares type"));

            typeTexts.put(typeId, text);
            if (test) {
                testTypes.add(typeId);
            } else {
                productionTypes.put(simpleName, typeId);
            }
            Matcher imports = IMPORT.matcher(text);
            while (imports.find()) {
                edges.add(new GraphEdge(typeId, RelationshipType.DEPENDS_ON,
                        "type:" + imports.group(1), "explicit Java import"));
            }
        }

        for (String testId : testTypes) {
            String text = typeTexts.get(testId);
            productionTypes.forEach((simpleName, productionId) -> {
                if (Pattern.compile("\\b" + Pattern.quote(simpleName) + "\\b").matcher(text).find()) {
                    edges.add(new GraphEdge(testId, RelationshipType.TESTS, productionId,
                            "test source references production type"));
                }
            });
        }
    }

    private static EvidenceDocument document(
            String stableId,
            String logicalId,
            String kind,
            String revision,
            String semanticText,
            EvidenceAuthority authority,
            String status,
            String path,
            String anchor
    ) {
        return new EvidenceDocument(
                stableId,
                logicalId,
                kind,
                revision,
                "sha256:" + sha256(semanticText),
                semanticText,
                authority,
                status,
                List.of(new SourceReference(path, anchor)),
                List.of());
    }

    private static EvidenceDocument mergeSources(EvidenceDocument left, EvidenceDocument right) {
        var sources = new ArrayList<>(left.sources());
        sources.addAll(right.sources());
        return new EvidenceDocument(
                left.stableId(), left.logicalId(), left.kind(), left.revision(), left.contentHash(),
                left.semanticText(), left.authority(), left.status(), sources, left.historicalOutcomes());
    }

    private static String semanticTypeText(String fqcn, String module, String path, String text) {
        var refs = new ArrayList<String>();
        Matcher imports = IMPORT.matcher(text);
        while (imports.find()) {
            refs.add(imports.group(1));
        }
        return "kind: type\nsymbol: " + fqcn + "\nmodule: " + module + "\npath: " + path
                + "\nreferences: " + String.join(", ", refs);
    }

    private static List<Path> files(Path root, String suffix) {
        if (!Files.isDirectory(root)) {
            return List.of();
        }
        try (Stream<Path> stream = Files.walk(root)) {
            return stream.filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().endsWith(suffix))
                    .sorted()
                    .toList();
        } catch (IOException exception) {
            throw new UncheckedIOException("failed to scan " + root, exception);
        }
    }

    private static Map<String, List<String>> frontmatter(String text) {
        var result = new LinkedHashMap<String, List<String>>();
        String[] lines = text.split("\\R");
        if (lines.length == 0 || !lines[0].equals("---")) {
            return result;
        }
        String current = null;
        for (int index = 1; index < lines.length && !lines[index].equals("---"); index++) {
            String line = lines[index];
            if (!line.startsWith(" ") && line.contains(":")) {
                int colon = line.indexOf(':');
                current = line.substring(0, colon).trim();
                String value = line.substring(colon + 1).trim();
                result.computeIfAbsent(current, ignored -> new ArrayList<>());
                if (!value.isEmpty() && !value.equals("[]")) {
                    result.get(current).add(unquote(value));
                }
            } else if (current != null && line.trim().startsWith("- ")) {
                result.get(current).add(unquote(line.trim().substring(2).trim()));
            }
        }
        return result;
    }

    private static List<String> firstList(String text, String key) {
        String[] lines = text.split("\\R");
        for (int index = 0; index < lines.length; index++) {
            if (lines[index].trim().equals(key + ":")) {
                var values = new ArrayList<String>();
                for (int item = index + 1; item < lines.length && lines[item].trim().startsWith("- "); item++) {
                    values.add(unquote(lines[item].trim().substring(2).trim()));
                }
                return values;
            }
        }
        return List.of();
    }

    private static String firstIndentedScalar(String text, String key) {
        Pattern pattern = Pattern.compile("(?m)^\\s+" + Pattern.quote(key) + ":\\s*([^|>][^\\r\\n]*)$");
        Matcher matcher = pattern.matcher(text);
        return matcher.find() ? unquote(matcher.group(1).trim()) : null;
    }

    private static String first(Map<String, List<String>> map, String key) {
        List<String> values = map.get(key);
        return values == null || values.isEmpty() ? null : values.getFirst();
    }

    private static String valueOr(Map<String, List<String>> map, String key, String fallback) {
        String value = first(map, key);
        return value == null ? fallback : value;
    }

    private static String firstNonNull(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        throw new IllegalArgumentException("at least one value is required");
    }

    private static RelationshipType relationship(String key) {
        return switch (key) {
            case "depends_on", "consumes" -> RelationshipType.DEPENDS_ON;
            case "produces" -> RelationshipType.IMPLEMENTS;
            case "decisions" -> RelationshipType.GOVERNS;
            case "risks", "open_questions", "relates_to" -> RelationshipType.RELATES_TO;
            default -> throw new IllegalArgumentException("unsupported relationship key: " + key);
        };
    }

    private static String knowledgeKind(String type) {
        return switch (type.toLowerCase(Locale.ROOT)) {
            case "decision" -> "ArchitectureDecision";
            case "architecture" -> "ArchitectureKnowledge";
            case "risk" -> "Risk";
            case "question" -> "Question";
            default -> "KnowledgeEntry";
        };
    }

    private static EvidenceAuthority authority(String status) {
        return switch (status.toLowerCase(Locale.ROOT)) {
            case "canonical", "accepted", "active", "living", "delivered" -> EvidenceAuthority.CANONICAL;
            case "experimental" -> EvidenceAuthority.EXPERIMENTAL;
            case "stale", "deprecated", "superseded" -> EvidenceAuthority.STALE;
            default -> EvidenceAuthority.PROPOSED;
        };
    }

    private static String read(Path path) {
        try {
            return Files.readString(path, StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new UncheckedIOException("failed to read " + path, exception);
        }
    }

    private static String relative(Path root, Path path) {
        return root.relativize(path.toAbsolutePath().normalize()).toString().replace('\\', '/');
    }

    private static String unquote(String value) {
        if (value.length() >= 2 && ((value.startsWith("\"") && value.endsWith("\""))
                || (value.startsWith("'") && value.endsWith("'")))) {
            return value.substring(1, value.length() - 1);
        }
        return value;
    }

    private static String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 unavailable", exception);
        }
    }
}
