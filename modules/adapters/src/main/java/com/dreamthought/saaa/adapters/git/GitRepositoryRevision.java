package com.dreamthought.saaa.adapters.git;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.LinkedHashSet;
import java.util.Objects;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.Status;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;

/** JGit-first repository identity with a visible native-Git compatibility fallback. */
public final class GitRepositoryRevision {
    private GitRepositoryRevision() { }

    public static String head(Path repositoryRoot) {
        try (Repository repository = open(repositoryRoot)) {
            var head = repository.resolve(Constants.HEAD);
            if (head == null) throw new IllegalStateException("repository has no HEAD");
            return head.name();
        } catch (RuntimeException | IOException exception) {
            return fallback("read repository revision", exception,
                    () -> GitCommand.run(Objects.requireNonNull(repositoryRoot, "repositoryRoot"),
                            "rev-parse", "HEAD").requireSuccess("read repository revision").trim());
        }
    }

    public static String workingTree(Path pathInsideRepository) {
        try (Repository repository = open(pathInsideRepository); Git git = new Git(repository)) {
            String head = Objects.requireNonNull(repository.resolve(Constants.HEAD), "repository HEAD").name();
            Status status = git.status().call();
            if (status.isClean()) return head;
            MessageDigest digest = sha256();
            var paths = new LinkedHashSet<String>();
            paths.addAll(status.getAdded());
            paths.addAll(status.getChanged());
            paths.addAll(status.getConflicting());
            paths.addAll(status.getMissing());
            paths.addAll(status.getModified());
            paths.addAll(status.getRemoved());
            paths.addAll(status.getUncommittedChanges());
            paths.addAll(status.getUntracked());
            for (String relative : paths.stream().sorted().toList()) {
                digest.update(relative.getBytes(StandardCharsets.UTF_8));
                Path file = repository.getWorkTree().toPath().resolve(relative).normalize();
                if (Files.isSymbolicLink(file)) {
                    digest.update(Files.readSymbolicLink(file).toString().getBytes(StandardCharsets.UTF_8));
                } else if (Files.isRegularFile(file)) {
                    digest.update(Files.readAllBytes(file));
                } else {
                    digest.update("<absent>".getBytes(StandardCharsets.UTF_8));
                }
            }
            return head + "+dirty:" + HexFormat.of().formatHex(digest.digest());
        } catch (Exception exception) {
            return fallbackWorkingTree(pathInsideRepository, exception);
        }
    }

    public static String repositoryId(Path pathInsideRepository) {
        try (Repository repository = open(pathInsideRepository)) {
            String remote = repository.getConfig().getString("remote", "origin", "url");
            return remote == null || remote.isBlank()
                    ? repository.getWorkTree().getName()
                    : repositoryName(remote);
        } catch (RuntimeException | IOException exception) {
            return fallback("read repository identity", exception, () -> fallbackRepositoryId(pathInsideRepository));
        }
    }

    public static Path root(Path pathInsideRepository) {
        try (Repository repository = open(pathInsideRepository)) {
            if (repository.isBare()) throw new IllegalArgumentException("bare repository has no working tree");
            return repository.getWorkTree().toPath().toAbsolutePath().normalize();
        } catch (RuntimeException | IOException exception) {
            return fallback("find repository root", exception, () -> fallbackRoot(pathInsideRepository));
        }
    }

    static Repository open(Path pathInsideRepository) throws IOException {
        Path start = Objects.requireNonNull(pathInsideRepository, "pathInsideRepository")
                .toAbsolutePath().normalize();
        return new FileRepositoryBuilder().findGitDir(start.toFile()).setMustExist(true).build();
    }

    private static String fallbackWorkingTree(Path pathInsideRepository, Exception cause) {
        return fallback("fingerprint repository working tree", cause, () -> {
            Path root = fallbackRoot(pathInsideRepository);
            String head = GitCommand.run(root, "rev-parse", "HEAD")
                    .requireSuccess("read repository revision").trim();
            String status = GitCommand.run(root, "status", "--porcelain=v1", "--untracked-files=all")
                    .requireSuccess("read repository working-tree status");
            if (status.isBlank()) return head;
            try {
                MessageDigest digest = sha256();
                digest.update(GitCommand.run(root, "diff", "--binary", "HEAD")
                        .requireSuccess("read repository working-tree diff").getBytes(StandardCharsets.UTF_8));
                String untracked = GitCommand.run(root, "ls-files", "--others", "--exclude-standard")
                        .requireSuccess("list untracked repository files");
                for (String relative : untracked.lines().sorted().toList()) {
                    digest.update(relative.getBytes(StandardCharsets.UTF_8));
                    digest.update(Files.readAllBytes(root.resolve(relative).normalize()));
                }
                return head + "+dirty:" + HexFormat.of().formatHex(digest.digest());
            } catch (IOException exception) {
                throw new IllegalStateException("failed to fingerprint repository content", exception);
            }
        });
    }

    private static String fallbackRepositoryId(Path pathInsideRepository) {
        Path root = fallbackRoot(pathInsideRepository);
        GitCommand.Result remote = GitCommand.run(root, "remote", "get-url", "origin");
        return remote.exitCode() != 0 || remote.output().isBlank()
                ? root.getFileName().toString()
                : repositoryName(remote.output().trim());
    }

    private static Path fallbackRoot(Path pathInsideRepository) {
        Path start = Objects.requireNonNull(pathInsideRepository, "pathInsideRepository")
                .toAbsolutePath().normalize();
        for (Path current = start; current != null; current = current.getParent()) {
            if (Files.exists(current.resolve(".git"))) return current;
        }
        throw new IllegalArgumentException("path is not inside a Git repository: " + start);
    }

    private static String repositoryName(String remote) {
        String value = remote.trim().replace('\\', '/');
        if (value.endsWith(".git")) value = value.substring(0, value.length() - 4);
        int separator = Math.max(value.lastIndexOf('/'), value.lastIndexOf(':'));
        return separator >= 0 ? value.substring(separator + 1) : value;
    }

    private static MessageDigest sha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 unavailable", exception);
        }
    }

    private static <T> T fallback(String operation, Exception cause, java.util.function.Supplier<T> action) {
        System.err.println("warning: JGit could not " + operation + "; using native git fallback ("
                + cause.getClass().getSimpleName() + ")");
        return action.get();
    }
}
