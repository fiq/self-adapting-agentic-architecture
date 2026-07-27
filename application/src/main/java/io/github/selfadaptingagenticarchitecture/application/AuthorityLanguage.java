package io.github.selfadaptingagenticarchitecture.application;

import java.util.regex.Pattern;

/**
 * Detects text claiming approval, scoring, promotion, discard or rollback authority. Model output
 * may propose or repair, but only deterministic policy decides candidate outcomes, so any proposal
 * carrying that language is rejected before it reaches evaluation.
 */
final class AuthorityLanguage {
    private static final String AUTHORITY_TARGET = "(candidate|mutation|result)";
    private static final String SCORING_TERM = "(score|scoring|fitness)";
    private static final String UP_TO_THREE_WORDS = "(?:\\W+\\w+){0,3}\\W+";
    private static final Pattern AUTHORITY_TEXT = Pattern.compile(
            "\\b(approve|approval|promote|promotion|discard|rollback)\\b"
                    + "|\\b" + SCORING_TERM + "\\b" + UP_TO_THREE_WORDS + "\\b" + AUTHORITY_TARGET + "\\b"
                    + "|\\b" + AUTHORITY_TARGET + "\\b" + UP_TO_THREE_WORDS + "\\b" + SCORING_TERM + "\\b",
            Pattern.CASE_INSENSITIVE
    );

    private AuthorityLanguage() {
    }

    static boolean isPresentIn(String text) {
        return AUTHORITY_TEXT.matcher(text).find();
    }
}
