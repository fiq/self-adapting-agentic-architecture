package com.dreamthought.saaa.adapters.git;

import com.dreamthought.saaa.deterministic.RealisedTreeIdentity;
import com.dreamthought.saaa.domain.Candidate;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Objects;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.treewalk.TreeWalk;

/**
 * Identifies a candidate by the files its commit holds, independent of proposal ids, commit
 * metadata and uncommitted worktree edits.
 *
 * <p>Every candidate commit also carries its own audit envelope under {@code .saaa/candidates/},
 * which names the candidate and so differs between any two candidates. The envelope stays in the
 * commit, because it is the durable record of why the candidate exists; it is left out of the
 * identity instead. Everything else in the tree counts: each path, its mode and its blob id, in
 * Git's own path order, hashed with SHA-256.
 */
public final class GitRealisedTreeIdentity implements RealisedTreeIdentity {
    public static final String AUDIT_ENVELOPE_DIRECTORY = ".saaa/candidates/";

    @Override
    public String treeOf(Candidate candidate) {
        Objects.requireNonNull(candidate, "candidate");
        try (var repository = GitRepositoryRevision.open(candidate.worktreePath());
             var walk = new RevWalk(repository);
             var tree = new TreeWalk(repository)) {
            tree.addTree(walk.parseCommit(repository.resolve(candidate.commitSha())).getTree());
            tree.setRecursive(true);
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            while (tree.next()) {
                String path = tree.getPathString();
                if (path.startsWith(AUDIT_ENVELOPE_DIRECTORY)) {
                    continue;
                }
                // Git paths cannot contain NUL, but can contain newlines and arbitrary bytes.
                digest.update(tree.getFileMode(0).toString().getBytes(StandardCharsets.US_ASCII));
                digest.update((byte) 0);
                digest.update(tree.getObjectId(0).name().getBytes(StandardCharsets.US_ASCII));
                digest.update((byte) 0);
                digest.update(tree.getRawPath());
                digest.update((byte) 0);
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (IOException | NoSuchAlgorithmException | RuntimeException exception) {
            throw new IllegalStateException("failed to read realised tree for " + candidate.id(), exception);
        }
    }
}
