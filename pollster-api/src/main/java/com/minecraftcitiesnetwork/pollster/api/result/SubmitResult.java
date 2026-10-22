package com.minecraftcitiesnetwork.pollster.api.result;

/** The outcome of storing a finished survey. */
public sealed interface SubmitResult permits SubmitResult.Saved, SubmitResult.AlreadyResponded,
        SubmitResult.SurveyNotOpen, SubmitResult.ContentChanged {

    record Saved(long responseId) implements SubmitResult {
    }

    record AlreadyResponded() implements SubmitResult {
    }

    record SurveyNotOpen() implements SubmitResult {
    }

    /** The survey file changed after the player started, so their answers may no longer fit. */
    record ContentChanged() implements SubmitResult {
    }
}
