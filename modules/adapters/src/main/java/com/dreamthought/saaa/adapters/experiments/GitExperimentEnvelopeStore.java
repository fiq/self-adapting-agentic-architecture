package com.dreamthought.saaa.adapters.experiments;

import com.dreamthought.saaa.deterministic.EvolutionaryMemoryArchive;
import com.dreamthought.saaa.domain.EvolutionaryMemoryRecord;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Objects;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/** Git-visible append-only rebuild source for deterministic experiment outcomes. */
public final class GitExperimentEnvelopeStore implements EvolutionaryMemoryArchive {
    private final Path directory;
    private final ExperimentEnvelopeCodec codec = new ExperimentEnvelopeCodec();

    public GitExperimentEnvelopeStore(Path repositoryRoot) {
        directory = Objects.requireNonNull(repositoryRoot, "repositoryRoot")
                .toAbsolutePath().normalize().resolve("experiments/ledger");
    }

    @Override
    public void append(EvolutionaryMemoryRecord record) {
        try {
            Files.createDirectories(directory);
            Path target = directory.resolve(fileName(record.candidateId()));
            String encoded = codec.encode(record);
            if (Files.exists(target)) {
                if (Files.readString(target).equals(encoded)) return;
                throw new IllegalStateException(
                        "experiment envelope candidate id already records a different outcome: "
                                + record.candidateId());
            }
            Path staging = Files.createTempFile(directory, ".saaa-envelope-", ".tmp");
            Files.writeString(staging, encoded);
            try {
                Files.move(staging, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException exception) {
                Files.move(staging, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException exception) {
            throw new UncheckedIOException("failed to write Git experiment envelope", exception);
        }
    }

    @Override
    public List<EvolutionaryMemoryRecord> records() {
        if (!Files.isDirectory(directory)) return List.of();
        try (var paths = Files.list(directory)) {
            return paths.filter(path -> path.getFileName().toString().endsWith(".toon"))
                    .sorted().map(this::read).toList();
        } catch (IOException exception) {
            throw new UncheckedIOException("failed to read Git experiment envelopes", exception);
        }
    }

    private EvolutionaryMemoryRecord read(Path path) {
        try {
            return codec.decode(Files.readString(path));
        } catch (IOException exception) {
            throw new UncheckedIOException("failed to read experiment envelope " + path, exception);
        }
    }

    static String fileName(String value) {
        String safe = value.toLowerCase().replaceAll("[^a-z0-9._-]", "-");
        if (safe.isBlank() || safe.equals(".") || safe.equals("..")) {
            throw new IllegalArgumentException("candidate id cannot form a safe experiment envelope path");
        }
        if (safe.length() > 100) safe = safe.substring(0, 100);
        try {
            String digest = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8))).substring(0, 12);
            return safe + "-" + digest + ".toon";
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 unavailable", exception);
        }
    }
}
