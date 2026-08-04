package com.dreamthought.saaa.adapters.git;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.util.Comparator;
import java.util.Objects;
import java.util.Set;
import org.eclipse.jgit.lib.FileMode;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.treewalk.TreeWalk;

/**
 * Read-only historic snapshot. JGit is primary and needs no external setup; native Git linked
 * worktree creation is a diagnostic compatibility fallback.
 */
public final class GitRevisionWorkspace implements AutoCloseable {
    private enum Backend { JGIT_SNAPSHOT, NATIVE_GIT_FALLBACK }

    private final Path repositoryRoot;
    private final Path path;
    private final String revision;
    private final Backend backend;
    private boolean closed;

    public static GitRevisionWorkspace open(Path repositoryRoot, String requestedRevision) {
        Path root = GitRepositoryRevision.root(repositoryRoot);
        try {
            return openWithJGit(root, requestedRevision);
        } catch (RuntimeException | IOException exception) {
            System.err.println("warning: JGit could not materialise historic revision; using native git fallback ("
                    + exception.getClass().getSimpleName() + ")");
            return openWithNativeGit(root, requestedRevision);
        }
    }

    private static GitRevisionWorkspace openWithJGit(Path root, String requestedRevision) throws IOException {
        try (Repository repository = GitRepositoryRevision.open(root)) {
            ObjectId resolved = repository.resolve(requestedRevision + "^{commit}");
            if (resolved == null) throw new IllegalArgumentException("unknown historic revision: " + requestedRevision);
            Path path = Files.createTempDirectory("saaa-reinflate-");
            try (RevWalk revWalk = new RevWalk(repository)) {
                var commit = revWalk.parseCommit(resolved);
                try (TreeWalk tree = new TreeWalk(repository)) {
                    tree.addTree(commit.getTree());
                    tree.setRecursive(true);
                    while (tree.next()) {
                        // A gitlink names another repository commit, not a blob in this object database.
                        // Historic indexing deliberately leaves submodule expansion to a later explicit policy.
                        if (tree.getFileMode(0).equals(FileMode.GITLINK)) continue;
                        Path target = path.resolve(tree.getPathString()).normalize();
                        if (!target.startsWith(path)) throw new IllegalStateException("Git tree path escaped snapshot");
                        Files.createDirectories(target.getParent());
                        Files.write(target, repository.open(tree.getObjectId(0)).getBytes());
                        if (tree.getFileMode(0).equals(FileMode.EXECUTABLE_FILE)) makeExecutable(target);
                    }
                }
            } catch (RuntimeException | IOException exception) {
                deleteSnapshot(path);
                throw exception;
            }
            return new GitRevisionWorkspace(root, path, resolved.name(), Backend.JGIT_SNAPSHOT);
        }
    }

    private static GitRevisionWorkspace openWithNativeGit(Path root, String requestedRevision) {
        String revision = GitCommand.run(root, "rev-parse", "--verify", requestedRevision + "^{commit}")
                .requireSuccess("resolve historic repository revision").trim();
        try {
            Path path = Files.createTempDirectory("saaa-reinflate-");
            GitCommand.run(root, "worktree", "add", "--detach", path.toString(), revision)
                    .requireSuccess("create historic repository worktree");
            return new GitRevisionWorkspace(root, path, revision, Backend.NATIVE_GIT_FALLBACK);
        } catch (IOException exception) {
            throw new IllegalStateException("failed to allocate historic repository worktree", exception);
        }
    }

    private GitRevisionWorkspace(Path repositoryRoot, Path path, String revision, Backend backend) {
        this.repositoryRoot = Objects.requireNonNull(repositoryRoot, "repositoryRoot");
        this.path = Objects.requireNonNull(path, "path");
        this.revision = Objects.requireNonNull(revision, "revision");
        this.backend = Objects.requireNonNull(backend, "backend");
    }

    public Path path() { return path; }
    public String revision() { return revision; }
    public String backend() { return backend.name(); }

    @Override
    public void close() {
        if (closed) return;
        closed = true;
        if (backend == Backend.NATIVE_GIT_FALLBACK) {
            GitCommand.run(repositoryRoot, "worktree", "remove", "--force", path.toString())
                    .requireSuccess("remove historic repository worktree");
        } else {
            deleteSnapshot(path);
        }
    }

    private static void makeExecutable(Path path) throws IOException {
        try {
            Set<PosixFilePermission> permissions = Files.getPosixFilePermissions(path);
            permissions.add(PosixFilePermission.OWNER_EXECUTE);
            Files.setPosixFilePermissions(path, permissions);
        } catch (UnsupportedOperationException ignored) {
            // File contents remain usable on non-POSIX development filesystems.
        }
    }

    private static void deleteSnapshot(Path path) {
        if (!Files.exists(path)) return;
        try (var paths = Files.walk(path)) {
            for (Path current : paths.sorted(Comparator.reverseOrder()).toList()) Files.deleteIfExists(current);
        } catch (IOException exception) {
            throw new IllegalStateException("failed to remove temporary historic snapshot " + path, exception);
        }
    }
}
