package com.minecraftcitiesnetwork.pollster;

import org.jetbrains.annotations.NotNull;

import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

/**
 * The executors every collaborator uses. Database calls block, so they run on a small fixed
 * pool; network calls run on virtual threads; results hop back to the main thread.
 */
public record ExecutorState(@NotNull Executor mainThread, @NotNull ExecutorService database,
                            @NotNull ExecutorService network) {

    public void shutdown(@NotNull Logger log) {
        for (ExecutorService service : new ExecutorService[]{this.database, this.network}) {
            service.shutdown();
            try {
                if (!service.awaitTermination(30, TimeUnit.SECONDS)) {
                    log.warning("Pollster executor did not stop within 30 seconds; forcing shutdown.");
                    service.shutdownNow();
                }
            } catch (InterruptedException ex) {
                service.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
    }
}
