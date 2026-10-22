package com.minecraftcitiesnetwork.pollster.names;

import com.minecraftcitiesnetwork.namebook.api.Namebook;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/** Names from the Namebook plugin, which knows names of players who have never joined this server. */
public final class NamebookPlayerNames implements PlayerNames {

    private final Namebook namebook;

    public NamebookPlayerNames(@NotNull Namebook namebook) {
        this.namebook = namebook;
    }

    @Override
    public @NotNull CompletableFuture<Map<UUID, Optional<String>>> namesOf(@NotNull Collection<UUID> ids) {
        CompletableFuture<Map<UUID, Optional<String>>> lookup;
        try {
            lookup = this.namebook.namesOf(ids);
        } catch (RuntimeException e) {
            return CompletableFuture.completedFuture(unknown(ids));
        }
        // Our contract is never to fail, whatever the other plugin does.
        return lookup.handle((names, error) -> error != null || names == null ? unknown(ids) : names);
    }

    private static Map<UUID, Optional<String>> unknown(Collection<UUID> ids) {
        Map<UUID, Optional<String>> unknown = new HashMap<>();
        for (UUID id : ids) {
            unknown.put(id, Optional.empty());
        }
        return unknown;
    }
}
