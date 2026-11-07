package com.dreamthought.saaa.adapters.git;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.dreamthought.saaa.domain.Candidate;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class GitRealisedTreeIdentityIntegrationTest {
    @Test
    void identicalCommittedFilesHaveTheSameTreeAndDifferentFilesHaveDifferentTrees(@TempDir Path repo)
            throws Exception {
        git(repo, "init", "-b", "main");
        git(repo, "config", "user.name", "Test");
        git(repo, "config", "user.email", "test@example.invalid");
        Files.writeString(repo.resolve("workflow.txt"), "first");
        git(repo, "add", ".");
        git(repo, "commit", "-m", "first");
        var first = candidate(repo, "first");
        // A second candidate: same workflow file, its own audit envelope, as GitCandidateWorkspace
        // commits one per candidate. The envelope must not make the two differ.
        Files.createDirectories(repo.resolve(".saaa/candidates"));
        Files.writeString(repo.resolve(".saaa/candidates/repeated.toon"), "candidate:\n  id: repeated\n");
        git(repo, "add", ".");
        git(repo, "commit", "-m", "same files plus an audit envelope");
        var repeated = candidate(repo, "repeated");
        Files.writeString(repo.resolve("workflow.txt"), "different");
        Files.writeString(repo.resolve(".saaa/candidates/different.toon"), "candidate:\n  id: different\n");
        git(repo, "add", ".");
        git(repo, "commit", "-m", "different files");
        var different = candidate(repo, "different");
        Path linked = repo.resolveSibling(repo.getFileName() + "-linked");
        git(repo, "worktree", "add", "--detach", linked.toString(), repeated.commitSha());
        var linkedCandidate = new Candidate("linked", "mut-linked", "main", linked, repeated.commitSha());
        // Dirty working files must not change the identity of the recorded commit.
        Files.writeString(repo.resolve("workflow.txt"), "uncommitted");
        var identity = new GitRealisedTreeIdentity();
        assertThat(first.commitSha()).isNotEqualTo(repeated.commitSha());
        assertThat(identity.treeOf(first)).isEqualTo(identity.treeOf(repeated))
                .isEqualTo(identity.treeOf(linkedCandidate));
        assertThat(git(repo, "rev-parse", first.commitSha() + "^{tree}"))
                .as("Git's own tree ids differ, because of the envelope; the identity must not")
                .isNotEqualTo(git(repo, "rev-parse", repeated.commitSha() + "^{tree}"));
        assertThat(identity.treeOf(different)).isNotEqualTo(identity.treeOf(first));
        // A file outside the audit directory that only lives under .saaa still counts.
        Files.writeString(repo.resolve(".saaa/fixture-mutation.txt"), "summary\nfirst\n");
        git(repo, "add", ".");
        git(repo, "commit", "-m", "other .saaa content");
        assertThat(identity.treeOf(candidate(repo, "other-saaa"))).isNotEqualTo(identity.treeOf(different));
    }

    @Test
    void anUnreadableCommitFailsInsteadOfInventingAnIdentity(@TempDir Path repo) {
        git(repo, "init", "-b", "main");
        var missing = new Candidate("missing", "mut", "main", repo, "a".repeat(40));
        assertThatThrownBy(() -> new GitRealisedTreeIdentity().treeOf(missing))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("missing");
    }

    private static Candidate candidate(Path repo, String id) {
        return new Candidate(id, "mut-" + id, "main", repo, git(repo, "rev-parse", "HEAD").trim());
    }

    private static String git(Path repo, String... args) {
        return GitCommand.run(repo, args).requireSuccess("integration fixture");
    }
}
