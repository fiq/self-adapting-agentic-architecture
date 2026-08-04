package com.dreamthought.saaa.adapters.experiments;

import com.dreamthought.saaa.domain.BenchmarkEvidence;
import com.dreamthought.saaa.domain.CheckEvidence;
import com.dreamthought.saaa.domain.CheckStatus;
import com.dreamthought.saaa.domain.EvolutionContext;
import com.dreamthought.saaa.domain.EvolutionaryMemoryRecord;
import com.dreamthought.saaa.domain.FitnessDecision;
import com.dreamthought.saaa.domain.MutationScope;
import com.dreamthought.saaa.domain.RetrievalMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Small deterministic codec for the repository's reviewable TOON experiment envelope. */
final class ExperimentEnvelopeCodec {
    static final String SCHEMA = "saaa-experiment-envelope-v2";

    String encode(EvolutionaryMemoryRecord record) {
        var out = new StringBuilder("experiment_envelope:\n");
        scalar(out, "schema_version", SCHEMA);
        scalar(out, "subject_repository_id", record.evolutionContext().subjectRepositoryId());
        scalar(out, "baseline_repository_revision", record.evolutionContext().subjectRepositoryRevision());
        scalar(out, "process_repository_id", record.evolutionContext().processRepositoryId());
        scalar(out, "process_repository_revision", record.evolutionContext().processRepositoryRevision());
        scalar(out, "memory_policy_id", record.memoryPolicyId());
        scalar(out, "mutation_id", record.mutationId());
        scalar(out, "mutation_summary", record.mutationSummary());
        scalar(out, "mutation_scope", record.mutationScope().name());
        scalar(out, "candidate_id", record.candidateId());
        scalar(out, "candidate_commit", record.candidateCommit());
        scalar(out, "retrieval_mode", record.retrievalMode().name());
        scalar(out, "retrieval_configuration_id", record.retrievalConfigurationId());
        scalar(out, "aggregate_fitness", Double.toString(record.aggregateFitness()));
        scalar(out, "decision", record.decision().name());
        scalar(out, "evaluated_at", record.evaluatedAt().toString());
        out.append("  changed_paths[").append(record.changedPaths().size()).append("]:\n");
        record.changedPaths().forEach(value -> out.append("    - ").append(csv(List.of(value))).append('\n'));
        out.append("  evidence_ids[").append(record.retrievedEvidenceIds().size()).append("]:\n");
        record.retrievedEvidenceIds().forEach(value -> out.append("    - ").append(csv(List.of(value))).append('\n'));
        out.append("  checks[").append(record.checks().size()).append("]{name,status,summary}:\n");
        record.checks().forEach(value -> out.append("    ").append(csv(List.of(
                value.name(), value.status().name(), value.summary()))).append('\n'));
        out.append("  benchmarks[").append(record.benchmarks().size()).append("]{name,value,unit}:\n");
        record.benchmarks().forEach(value -> out.append("    ").append(csv(List.of(
                value.name(), Double.toString(value.value()), value.unit()))).append('\n'));
        return out.toString();
    }

    EvolutionaryMemoryRecord decode(String value) {
        Map<String, String> scalar = new LinkedHashMap<>();
        var changedPaths = new ArrayList<String>();
        var evidence = new ArrayList<String>();
        var checks = new ArrayList<CheckEvidence>();
        var benchmarks = new ArrayList<BenchmarkEvidence>();
        String section = "";
        for (String line : value.lines().toList()) {
            String trimmed = line.trim();
            if (trimmed.isBlank() || trimmed.equals("experiment_envelope:")) continue;
            if (trimmed.startsWith("changed_paths[")) { section = "changed"; continue; }
            if (trimmed.startsWith("evidence_ids[")) { section = "evidence"; continue; }
            if (trimmed.startsWith("checks[")) { section = "checks"; continue; }
            if (trimmed.startsWith("benchmarks[")) { section = "benchmarks"; continue; }
            if (!line.startsWith("    ")) {
                int colon = trimmed.indexOf(':');
                if (colon < 1) throw new IllegalArgumentException("invalid experiment envelope scalar: " + line);
                scalar.put(trimmed.substring(0, colon), parseCsv(trimmed.substring(colon + 1).trim()).getFirst());
                section = "";
                continue;
            }
            String row = trimmed.startsWith("- ") ? trimmed.substring(2) : trimmed;
            List<String> columns = parseCsv(row);
            switch (section) {
                case "changed" -> changedPaths.add(columns.getFirst());
                case "evidence" -> evidence.add(columns.getFirst());
                case "checks" -> checks.add(new CheckEvidence(
                        columns.get(0), CheckStatus.valueOf(columns.get(1)), columns.get(2)));
                case "benchmarks" -> benchmarks.add(new BenchmarkEvidence(
                        columns.get(0), Double.parseDouble(columns.get(1)), columns.get(2)));
                default -> throw new IllegalArgumentException("experiment envelope row has no section: " + line);
            }
        }
        if (!SCHEMA.equals(required(scalar, "schema_version"))) {
            throw new IllegalArgumentException("unsupported experiment envelope schema");
        }
        return new EvolutionaryMemoryRecord(
                new EvolutionContext(required(scalar, "subject_repository_id"),
                        required(scalar, "baseline_repository_revision"),
                        required(scalar, "process_repository_id"),
                        required(scalar, "process_repository_revision")),
                required(scalar, "memory_policy_id"), required(scalar, "mutation_id"),
                required(scalar, "mutation_summary"), MutationScope.valueOf(required(scalar, "mutation_scope")),
                required(scalar, "candidate_id"), required(scalar, "candidate_commit"),
                RetrievalMode.valueOf(required(scalar, "retrieval_mode")),
                required(scalar, "retrieval_configuration_id"), changedPaths, evidence, checks, benchmarks,
                Double.parseDouble(required(scalar, "aggregate_fitness")),
                FitnessDecision.valueOf(required(scalar, "decision")),
                Instant.parse(required(scalar, "evaluated_at")));
    }

    private static void scalar(StringBuilder out, String name, String value) {
        out.append("  ").append(name).append(": ").append(csv(List.of(value))).append('\n');
    }

    private static String required(Map<String, String> values, String key) {
        String value = values.get(key);
        if (value == null || value.isBlank()) throw new IllegalArgumentException("missing experiment field: " + key);
        return value;
    }

    private static String csv(List<String> values) {
        return values.stream().map(value -> "\"" + value.replace("\"", "\"\"") + "\"")
                .reduce((left, right) -> left + "," + right).orElse("");
    }

    private static List<String> parseCsv(String row) {
        var values = new ArrayList<String>();
        var current = new StringBuilder();
        boolean quoted = false;
        for (int index = 0; index < row.length(); index++) {
            char character = row.charAt(index);
            if (character == '"') {
                if (quoted && index + 1 < row.length() && row.charAt(index + 1) == '"') {
                    current.append('"'); index++;
                } else {
                    quoted = !quoted;
                }
            } else if (character == ',' && !quoted) {
                values.add(current.toString()); current.setLength(0);
            } else {
                current.append(character);
            }
        }
        if (quoted) throw new IllegalArgumentException("unterminated quoted experiment field");
        values.add(current.toString());
        return values;
    }
}
