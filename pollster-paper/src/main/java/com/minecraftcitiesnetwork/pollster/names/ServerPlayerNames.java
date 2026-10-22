package com.minecraftcitiesnetwork.pollster.names;

import org.bukkit.Server;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

/**
 * Names from the server's own user cache. {@code getOfflinePlayer(UUID)} never asks Mojang, but it
 * may read player data from disk, so it runs on the given executor rather than the main thread.
 */
public final class ServerPlayerNames implements PlayerNames {

    private final Server server;
    private final Executor io;

    public ServerPlayerNames(@NotNull Server server, @NotNull Executor io) {
        this.server = server;
        this.io = io;
    }

    @Override
    public @NotNull CompletableFuture<Map<UUID, Optional<String>>> namesOf(@NotNull Collection<UUID> ids) {
        return CompletableFuture.supplyAsync(() -> {
            Map<UUID, Optional<String>> names = new HashMap<>();
            for (UUID id : ids) {
                names.put(id, lookup(id));
            }
            return names;
        }, this.io).exceptionally(error -> {
            // The contract is never to fail: if the executor refuses the work, every name is unknown.
            Map<UUID, Optional<String>> unknown = new HashMap<>();
            for (UUID id : ids) {
                unknown.put(id, Optional.empty());
            }
            return unknown;
        });
    }

    private Optional<String> lookup(UUID id) {
        try {
            return Optional.ofNullable(this.server.getOfflinePlayer(id).getName());
        } catch (RuntimeException e) {
            return Optional.empty();
        }
    }
}
