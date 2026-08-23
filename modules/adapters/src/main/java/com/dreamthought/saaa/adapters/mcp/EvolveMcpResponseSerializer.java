package com.dreamthought.saaa.adapters.mcp;

import com.dreamthought.saaa.adapters.evolve.EvolveRunResult;
import com.dreamthought.saaa.domain.BenchmarkEvidence;
import com.dreamthought.saaa.domain.CheckEvidence;
import com.dreamthought.saaa.domain.FitnessForce;
import com.dreamthought.saaa.domain.FitnessResult;
import com.dreamthought.saaa.domain.FitnessSignalId;
import java.nio.file.Path;
import java.util.Map;
import java.util.Objects;

public final class EvolveMcpResponseSerializer {
    static final int CHECK_SUMMARY_LIMIT = 4_000;
    static final int ERROR_LIMIT = 8_000;
    static final int RESPONSE_LIMIT = 32_000;

    private final EvolveMcpResponseScrubber scrubber;

    public EvolveMcpResponseSerializer(EvolveMcpResponseScrubber scrubber) {
        this.scrubber = Objects.requireNonNull(scrubber, "scrubber");
    }

    public String serialize(EvolveRunResult runResult) {
        Objects.requireNonNull(runResult, "runResult");
        String serialized = serializeSuccess(runResult.fitnessResult(), runResult.journalPath());
        String scrubbed = scrubber.scrub(serialized);
        if (scrubbed.length() <= RESPONSE_LIMIT) {
            return scrubbed;
        }
        return cappedSuccessEnvelope(runResult, "response exceeded " + RESPONSE_LIMIT + " characters");
    }

    public String serializeError(Throwable failure) {
        Objects.requireNonNull(failure, "failure");
        String message = scrubber.scrub(failure.getMessage() == null ? failure.toString() : failure.getMessage());
        return cappedErrorEnvelope(message);
    }

    private static String serializeSuccess(FitnessResult result, Path journalPath) {
        var json = new StringBuilder();
        json.append('{');
        json.append("\"candidate\":{");
        json.append("\"id\":").append(quote(result.candidate().id())).append(',');
        json.append("\"mutationId\":").append(quote(result.candidate().mutationId())).append(',');
        json.append("\"branchName\":").append(quote(result.candidate().branchName())).append(',');
        json.append("\"worktreePath\":").append(quote(result.candidate().worktreePath().toString())).append(',');
        json.append("\"commitSha\":").append(quote(result.candidate().commitSha()));
        json.append("},");
        json.append("\"evidence\":{");
        json.append("\"evaluatedAt\":").append(quote(result.evidence().evaluatedAt().toString())).append(',');
        json.append("\"checks\":[");
        for (int index = 0; index < result.evidence().checks().size(); index++) {
            if (index > 0) {
                json.append(',');
            }
            appendCheck(json, result.evidence().checks().get(index));
        }
        json.append("],");
        json.append("\"benchmarks\":[");
        for (int index = 0; index < result.evidence().benchmarks().size(); index++) {
            if (index > 0) {
                json.append(',');
            }
            appendBenchmark(json, result.evidence().benchmarks().get(index));
        }
        json.append("]},");
        appendObjectives(json, result.objectives());
        json.append(',');
        appendFitnessScore(json, result);
        json.append(',');
        json.append("\"journalPath\":").append(quote(journalPath.toAbsolutePath().normalize().toString()));
        json.append('}');
        return json.toString();
    }

    private String cappedSuccessEnvelope(EvolveRunResult runResult, String capReason) {
        FitnessResult result = runResult.fitnessResult();
        var json = new StringBuilder();
        json.append('{');
        json.append("\"candidate\":{");
        json.append("\"id\":").append(quote(result.candidate().id())).append(',');
        json.append("\"mutationId\":").append(quote(result.candidate().mutationId())).append(',');
        json.append("\"branchName\":").append(quote(result.candidate().branchName())).append(',');
        json.append("\"worktreePath\":").append(quote(result.candidate().worktreePath().toString())).append(',');
        json.append("\"commitSha\":").append(quote(result.candidate().commitSha()));
        json.append("},");
        json.append("\"evidence\":{");
        json.append("\"evaluatedAt\":").append(quote(result.evidence().evaluatedAt().toString())).append(',');
        json.append("\"checks\":[],");
        json.append("\"benchmarks\":[],");
        json.append("\"truncated\":true,");
        json.append("\"truncationReason\":").append(quote(capReason));
        json.append("},");
        appendObjectives(json, result.objectives());
        json.append(',');
        appendFitnessScore(json, result);
        json.append(',');
        json.append("\"journalPath\":").append(quote(runResult.journalPath().toString()));
        json.append('}');
        String scrubbed = scrubber.scrub(json.toString());
        if (scrubbed.length() > RESPONSE_LIMIT) {
            throw new IllegalStateException("minimal MCP response exceeded response limit");
        }
        return scrubbed;
    }

    private static void appendCheck(StringBuilder json, CheckEvidence check) {
        json.append('{');
        json.append("\"name\":").append(quote(check.name())).append(',');
        json.append("\"status\":").append(quote(check.status().name())).append(',');
        json.append("\"summary\":").append(quote(capped(check.summary(), CHECK_SUMMARY_LIMIT)));
        json.append('}');
    }

    private static void appendFitnessScore(StringBuilder json, FitnessResult result) {
        json.append("\"fitnessScore\":{");
        json.append("\"rawMagnitude\":").append(result.fitnessScore().rawMagnitude()).append(',');
        json.append("\"decision\":").append(quote(result.fitnessScore().decision().name()));
        json.append('}');
    }

    private static void appendBenchmark(StringBuilder json, BenchmarkEvidence benchmark) {
        json.append('{');
        json.append("\"name\":").append(quote(benchmark.name())).append(',');
        json.append("\"value\":").append(benchmark.value()).append(',');
        json.append("\"unit\":").append(quote(benchmark.unit()));
        json.append('}');
    }

    private static void appendObjectives(StringBuilder json, Map<String, Double> objectives) {
        json.append("\"objectives\":{");
        boolean first = true;
        first = appendObjectives(json, first, objectives, false);
        appendObjectives(json, first, objectives, true);
        json.append('}');
    }

    private static boolean appendObjectives(
            StringBuilder json,
            boolean first,
            Map<String, Double> objectives,
            boolean invariant) {
        var ordered = objectives.entrySet().stream()
                .filter(entry -> isInvariant(entry.getKey()) == invariant)
                .map(entry -> Map.entry(FitnessSignalId.parse(entry.getKey()).canonical(), entry.getValue()))
                .sorted(Map.Entry.comparingByKey())
                .toList();
        for (var entry : ordered) {
            first = appendObjective(json, first, entry);
        }
        return first;
    }

    /**
     * The force is the discriminator for response grouping; a signal name cannot smuggle an
     * objective into the invariant group by resembling a legacy gate name.
     */
    private static boolean isInvariant(String key) {
        return FitnessSignalId.parse(key).force() == FitnessForce.INVARIANT;
    }

    private static boolean appendObjective(StringBuilder json, boolean first, Map.Entry<String, Double> entry) {
        if (!first) {
            json.append(',');
        }
        json.append(quote(entry.getKey())).append(':').append(entry.getValue());
        return false;
    }

    private static String capped(String text, int limit) {
        return text.length() <= limit ? text : text.substring(0, limit);
    }

    private static String cappedErrorEnvelope(String message) {
        int high = Math.min(message.length(), ERROR_LIMIT);
        int low = 0;
        String best = "{\"error\":\"\"}";
        while (low <= high) {
            int middle = low + ((high - low) / 2);
            String candidate = errorEnvelope(message.substring(0, middle));
            if (candidate.length() <= RESPONSE_LIMIT) {
                best = candidate;
                low = middle + 1;
            } else {
                high = middle - 1;
            }
        }
        return best;
    }

    private static String errorEnvelope(String message) {
        return "{\"error\":" + quote(message) + "}";
    }

    private static String quote(String text) {
        StringBuilder escaped = new StringBuilder(text.length() + 2);
        escaped.append('"');
        for (int index = 0; index < text.length(); index++) {
            char ch = text.charAt(index);
            switch (ch) {
                case '"' -> escaped.append("\\\"");
                case '\\' -> escaped.append("\\\\");
                case '\b' -> escaped.append("\\b");
                case '\f' -> escaped.append("\\f");
                case '\n' -> escaped.append("\\n");
                case '\r' -> escaped.append("\\r");
                case '\t' -> escaped.append("\\t");
                default -> {
                    if (ch < 0x20) {
                        escaped.append("\\u%04x".formatted((int) ch));
                    } else {
                        escaped.append(ch);
                    }
                }
            }
        }
        escaped.append('"');
        return escaped.toString();
    }
}
