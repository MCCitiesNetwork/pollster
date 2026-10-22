package com.minecraftcitiesnetwork.pollster.publish;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.jetbrains.annotations.NotNull;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.zip.GZIPOutputStream;

/** Uploads a snapshot to a bytebin-compatible paste service without blocking the caller. */
public final class PasteClient {

    private final HttpClient http;
    private final Executor network;
    private final ObjectMapper mapper = new ObjectMapper();

    public PasteClient(@NotNull HttpClient http, @NotNull Executor network) {
        this.http = http;
        this.network = network;
    }

    /**
     * Never throws and never blocks: every failure becomes a {@link PublishResult.Failed} that
     * completes on the network executor.
     */
    public @NotNull CompletableFuture<PublishResult> upload(byte[] json, @NotNull URI pasteUrl,
                                                            @NotNull URI viewerUrl, @NotNull Duration timeout) {
        HttpRequest request;
        try {
            request = HttpRequest.newBuilder(URI.create(trimSlash(pasteUrl.toString()) + "/post"))
                    .timeout(timeout)
                    .header("Content-Type", "application/json")
                    .header("Content-Encoding", "gzip")
                    .header("User-Agent", "Pollster")
                    .POST(HttpRequest.BodyPublishers.ofByteArray(gzip(json)))
                    .build();
        } catch (IOException | IllegalArgumentException e) {
            return CompletableFuture.supplyAsync(() -> new PublishResult.Failed(describe(e)), this.network);
        }
        // HttpRequest.timeout only covers the wait for headers; orTimeout also bounds a stalled body.
        return this.http.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .orTimeout(timeout.toMillis() + 500, TimeUnit.MILLISECONDS)
                .handleAsync((response, error) -> {
                    if (error != null) {
                        return new PublishResult.Failed(describe(error));
                    }
                    return interpret(response, viewerUrl);
                }, this.network);
    }

    private PublishResult interpret(HttpResponse<String> response, URI viewerUrl) {
        int status = response.statusCode();
        if (status < 200 || status > 299) {
            return new PublishResult.Failed("The paste service answered HTTP " + status);
        }
        String key = response.headers().firstValue("Location").map(PasteClient::lastSegment).orElse("");
        if (key.isBlank()) {
            try {
                JsonNode body = this.mapper.readTree(response.body());
                key = body != null && body.hasNonNull("key") ? body.get("key").asText() : "";
            } catch (IOException e) {
                key = "";
            }
        }
        if (key.isBlank()) {
            return new PublishResult.Failed("The paste service did not return a key");
        }
        return new PublishResult.Published(URI.create(trimSlash(viewerUrl.toString()) + "/#" + key));
    }

    private static String lastSegment(String location) {
        String trimmed = trimSlash(location.trim());
        return trimmed.substring(trimmed.lastIndexOf('/') + 1);
    }

    private static String trimSlash(String url) {
        String result = url;
        while (result.endsWith("/")) {
            result = result.substring(0, result.length() - 1);
        }
        return result;
    }

    private static byte[] gzip(byte[] json) throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        try (GZIPOutputStream out = new GZIPOutputStream(buffer)) {
            out.write(json);
        }
        return buffer.toByteArray();
    }

    private static String describe(Throwable error) {
        Throwable cause = error;
        while (cause instanceof CompletionException && cause.getCause() != null) {
            cause = cause.getCause();
        }
        if (cause instanceof HttpTimeoutException || cause instanceof TimeoutException) {
            return "The paste service timed out";
        }
        String message = cause.getMessage();
        return message == null || message.isBlank() ? cause.getClass().getSimpleName() : message;
    }
}
