package com.dreamthought.saaa.application;

import com.dreamthought.saaa.core.Mutation;
import com.dreamthought.saaa.core.ValidationResult;
import com.dreamthought.saaa.core.WorkflowGraph;

@FunctionalInterface
public interface MutationValidator {
    ValidationResult validate(WorkflowGraph baseline, Mutation mutation);
}
