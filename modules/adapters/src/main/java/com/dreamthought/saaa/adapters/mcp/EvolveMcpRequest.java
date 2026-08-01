package com.dreamthought.saaa.adapters.mcp;

import com.dreamthought.saaa.adapters.evolve.EvolveRunRequest;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public record EvolveMcpRequest(
        Path targetFolder,
        String profile,
        String workflowFile,
        List<String> behaviourCases,
        int maxLines
) {
    private static final Set<String> ALLOWED_FIELDS = Set.of(
            "targetFolder", "profile", "workflowFile", "behaviourCases", "maxLines");

    public EvolveMcpRequest {
        targetFolder = Objects.requireNonNull(targetFolder, "targetFolder");
        profile = requireNonBlank(profile, "profile");
        workflowFile = requireNonBlank(workflowFile, "workflowFile");
        behaviourCases = List.copyOf(Objects.requireNonNull(behaviourCases, "behaviourCases"));
        if (behaviourCases.isEmpty()) {
            throw new IllegalArgumentException("behaviourCases must contain at least one case");
        }
        if (maxLines < 1) {
            throw new IllegalArgumentException("maxLines must be positive");
        }
    }

    public static EvolveMcpRequest fromArguments(Map<String, Object> arguments) {
        Objects.requireNonNull(arguments, "arguments");
        var unknown = new ArrayList<String>();
        for (String key : arguments.keySet()) {
            if (!ALLOWED_FIELDS.contains(key)) {
                unknown.add(key);
            }
        }
        if (!unknown.isEmpty()) {
            throw new IllegalArgumentException("unsupported evolve argument(s): " + String.join(", ", unknown));
        }
        return new EvolveMcpRequest(
                Path.of(requiredString(arguments, "targetFolder")),
                optionalString(arguments, "profile", "fixture"),
                optionalString(arguments, "workflowFile", "workflow.txt"),
                requiredStringList(arguments, "behaviourCases"),
                optionalInt(arguments, "maxLines", 80));
    }

    public static Map<String, Object> inputSchema() {
        var properties = new LinkedHashMap<String, Object>();
        properties.put("targetFolder", Map.of("type", "string"));
        properties.put("profile", Map.of("type", "string", "default", "fixture"));
        properties.put("workflowFile", Map.of("type", "string", "default", "workflow.txt"));
        properties.put("behaviourCases", Map.of(
                "type", "array",
                "items", Map.of("type", "string"),
                "minItems", 1));
        properties.put("maxLines", Map.of("type", "integer", "minimum", 1, "default", 80));

        var schema = new LinkedHashMap<String, Object>();
        schema.put("type", "object");
        schema.put("additionalProperties", false);
        schema.put("required", List.of("targetFolder", "behaviourCases"));
        schema.put("properties", properties);
        return schema;
    }

    public EvolveRunRequest toRunRequest() {
        return new EvolveRunRequest(targetFolder, profile, workflowFile, behaviourCases, maxLines);
    }

    private static String requiredString(Map<String, Object> arguments, String key) {
        Object value = arguments.get(key);
        if (value instanceof String text && !text.isBlank()) {
            return text;
        }
        throw new IllegalArgumentException(key + " must be a non-blank string");
    }

    private static String optionalString(Map<String, Object> arguments, String key, String defaultValue) {
        Object value = arguments.get(key);
        if (value == null) {
            return defaultValue;
        }
        if (value instanceof String text && !text.isBlank()) {
            return text;
        }
        throw new IllegalArgumentException(key + " must be a non-blank string");
    }

    private static List<String> requiredStringList(Map<String, Object> arguments, String key) {
        Object value = arguments.get(key);
        if (!(value instanceof List<?> list) || list.isEmpty()) {
            throw new IllegalArgumentException(key + " must be a non-empty array of strings");
        }
        var strings = new ArrayList<String>(list.size());
        for (Object item : list) {
            if (item instanceof String text && !text.isBlank()) {
                strings.add(text);
            } else {
                throw new IllegalArgumentException(key + " must be a non-empty array of strings");
            }
        }
        return strings;
    }

    private static int optionalInt(Map<String, Object> arguments, String key, int defaultValue) {
        Object value = arguments.get(key);
        if (value == null) {
            return defaultValue;
        }
        if (value instanceof Number number && number.intValue() == number.doubleValue()) {
            return number.intValue();
        }
        throw new IllegalArgumentException(key + " must be an integer");
    }

    private static String requireNonBlank(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value;
    }
}
