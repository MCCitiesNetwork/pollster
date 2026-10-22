package com.minecraftcitiesnetwork.pollster.publish;

import org.jetbrains.annotations.NotNull;

import java.net.URI;

/** Outcome of uploading a snapshot to the paste service. */
public sealed interface PublishResult permits PublishResult.Published, PublishResult.Failed {

    /** @param link the viewer link that opens the uploaded snapshot */
    record Published(@NotNull URI link) implements PublishResult {
    }

    /** @param reason a short, human-readable cause shown to the admin in chat */
    record Failed(@NotNull String reason) implements PublishResult {
    }
}
