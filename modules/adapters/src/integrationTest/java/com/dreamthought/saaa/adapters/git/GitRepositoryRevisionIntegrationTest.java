package com.dreamthought.saaa.adapters.git;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class GitRepositoryRevisionIntegrationTest {
    @TempDir Path temporaryDirectory;

    @Test
    void resolvesStableIdentityAndFingerprintsTheExactWorkingTree() throws Exception {
        Path repository = repository();
        Path nested = Files.createDirectories(repository.resolve("nested/path"));
        String head = GitRepositoryRevision.head(repository);

        assertThat(GitRepositoryRevision.root(nested)).isEqualTo(repository);
        assertThat(GitRepositoryRevision.repositoryId(repository)).isEqualTo("subject-project");
        assertThat(GitRepositoryRevision.workingTree(repository)).isEqualTo(head);

        Files.writeString(repository.resolve("tracked.txt"), "changed");
        String firstDirty = GitRepositoryRevision.workingTree(repository);
        Files.writeString(repository.resolve("tracked.txt"), "changed again");
        String secondDirty = GitRepositoryRevision.workingTree(repository);
        assertThat(firstDirty).startsWith(head + "+dirty:").isNotEqualTo(secondDirty);

        Files.writeString(repository.resolve("untracked.txt"), "new evidence");
        assertThat(GitRepositoryRevision.workingTree(repository)).isNotEqualTo(secondDirty);
    }

    @Test
    void createsAndCleansAnHistoricSnapshotWithoutChangingTheCheckout() throws Exception {
        Path repository = repository();
        String revision = GitRepositoryRevision.head(repository);
        Path historicPath;

        try (var historic = GitRevisionWorkspace.open(repository, revision)) {
            historicPath = historic.path();
            assertThat(historic.revision()).isEqualTo(revision);
            assertThat(historic.backend()).isEqualTo("JGIT_SNAPSHOT");
            assertThat(historicPath.resolve("tracked.txt")).hasContent("baseline");
        }

        assertThat(historicPath).doesNotExist();
    }

    private Path repository() throws Exception {
        Path repository = temporaryDirectory.resolve("repo");
        Files.createDirectories(repository);
        git(repository, "init", "--initial-branch=main");
        git(repository, "config", "user.name", "Test");
        git(repository, "config", "user.email", "test@example.invalid");
        git(repository, "remote", "add", "origin", "git@github.com:example/subject-project.git");
        Files.writeString(repository.resolve("tracked.txt"), "baseline");
        git(repository, "add", "tracked.txt");
        git(repository, "commit", "-m", "baseline");
        return repository.toAbsolutePath().normalize();
    }

    private static String git(Path directory, String... arguments) throws Exception {
        var command = new java.util.ArrayList<String>();
        command.add("git"); command.add("-C"); command.add(directory.toString());
        command.addAll(java.util.List.of(arguments));
        Process process = new ProcessBuilder(command).redirectErrorStream(true).start();
        String output = new String(process.getInputStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
        assertThat(process.waitFor()).as("git %s%n%s", String.join(" ", arguments), output).isZero();
        return output.trim();
    }
}
