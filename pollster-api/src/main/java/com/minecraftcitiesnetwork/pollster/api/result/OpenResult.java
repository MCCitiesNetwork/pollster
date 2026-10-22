package com.minecraftcitiesnetwork.pollster.api.result;

/** The outcome of opening a survey for responses. */
public sealed interface OpenResult permits OpenResult.Opened, OpenResult.AlreadyOpened {

    record Opened() implements OpenResult {
    }

    record AlreadyOpened() implements OpenResult {
    }
}
