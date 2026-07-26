package io.github.selfadaptingagenticarchitecture.application;

import io.github.selfadaptingagenticarchitecture.core.Mutation;
import io.github.selfadaptingagenticarchitecture.core.ValidationResult;
import io.github.selfadaptingagenticarchitecture.core.WorkflowGraph;

@FunctionalInterface
public interface MutationValidator {
    ValidationResult validate(WorkflowGraph baseline, Mutation mutation);
}
