package com.dreamthought.saaa.deterministic;

import com.dreamthought.saaa.domain.Mutation;
import com.dreamthought.saaa.domain.ValidationResult;
import com.dreamthought.saaa.domain.WorkflowGraph;

@FunctionalInterface
public interface MutationValidator {
    ValidationResult validate(WorkflowGraph baseline, Mutation mutation);
}
