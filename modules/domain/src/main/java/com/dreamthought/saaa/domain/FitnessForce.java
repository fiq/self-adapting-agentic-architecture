package com.dreamthought.saaa.domain;

/**
 * Whether a fitness signal must hold or merely contributes.
 *
 * <p>An invariant is binary for the promote-or-discard decision and is never tradeable against an
 * objective. An objective compounds into the score and is only consulted once every invariant has
 * passed.
 */
public enum FitnessForce {
    INVARIANT,
    OBJECTIVE
}
