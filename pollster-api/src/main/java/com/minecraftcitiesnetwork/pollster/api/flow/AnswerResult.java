package com.minecraftcitiesnetwork.pollster.api.flow;

/** The outcome of offering an answer to the current question. */
public sealed interface AnswerResult permits AnswerResult.Accepted, AnswerResult.Invalid {

    enum Reason {
        REQUIRED,
        TOO_FEW,
        TOO_MANY,
        TOO_LONG,
        NOT_AN_OPTION,
        NOT_SKIPPABLE,
        WRONG_TYPE
    }

    record Accepted() implements AnswerResult {
    }

    record Invalid(Reason reason) implements AnswerResult {
    }
}
