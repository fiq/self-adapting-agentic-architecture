package com.dreamthought.saaa.adapters.evolve;

import com.dreamthought.saaa.adapters.git.GitRepositoryRevision;
import com.dreamthought.saaa.domain.EvolutionContext;
import java.nio.file.Path;

/** Resolves subject and process identities without leaking environment configuration into core code. */
final class LocalEvolutionContext {
    private LocalEvolutionContext() { }

    static EvolutionContext resolve(Path subjectRoot, String subjectRevision) {
        String configured = System.getProperty("saaa.process.repository",
                System.getenv("SAAA_PROCESS_REPOSITORY"));
        Path processRoot = configured == null || configured.isBlank()
                ? subjectRoot
                : GitRepositoryRevision.root(Path.of(configured));
        return new EvolutionContext(
                GitRepositoryRevision.repositoryId(subjectRoot),
                subjectRevision,
                GitRepositoryRevision.repositoryId(processRoot),
                GitRepositoryRevision.workingTree(processRoot));
    }
}
