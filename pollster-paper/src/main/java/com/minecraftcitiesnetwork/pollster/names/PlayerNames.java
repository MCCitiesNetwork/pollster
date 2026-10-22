package com.minecraftcitiesnetwork.pollster.names;

import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Looks up player names by UUID. Pollster stores only UUIDs, so names are resolved when a report
 * is built and always show the player's current name.
 */
public interface PlayerNames {

    /** Never fails; every requested id is a key; empty when unknown. Completes on any thread. */
    @NotNull CompletableFuture<Map<UUID, Optional<String>>> namesOf(@NotNull Collection<UUID> ids);
}
