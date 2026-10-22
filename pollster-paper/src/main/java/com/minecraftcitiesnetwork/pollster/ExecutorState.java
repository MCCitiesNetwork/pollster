package com.minecraftcitiesnetwork.pollster;

import org.jetbrains.annotations.NotNull;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
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

    /**
     * Completes on the main thread whether {@code stage} succeeds or fails. thenApplyAsync alone is
     * not enough: a failure skips its executor and arrives on whichever thread threw.
     */
    public <T> @NotNull CompletableFuture<T> onMainThread(@NotNull CompletableFuture<T> stage) {
        return stage.handleAsync((value, error) -> {
            if (error != null) {
                throw error instanceof CompletionException wrapped ? wrapped : new CompletionException(error);
            }
            return value;
        }, this.mainThread);
    }

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
