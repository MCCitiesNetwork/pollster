package com.minecraftcitiesnetwork.pollster.publish;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.zip.GZIPInputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PasteClientTest {

    private static final URI VIEWER = URI.create("https://viewer");

    private HttpServer server;
    private ExecutorService network;
    private PasteClient client;
    private URI pasteUrl;

    @BeforeEach
    void start() throws Exception {
        this.server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        this.server.setExecutor(Executors.newCachedThreadPool());
        this.network = Executors.newSingleThreadExecutor(r -> new Thread(r, "test-network"));
        this.client = new PasteClient(HttpClient.newHttpClient(), this.network);
        this.pasteUrl = URI.create("http://127.0.0.1:" + this.server.getAddress().getPort());
    }

    @AfterEach
    void stop() {
        this.server.stop(0);
        this.network.shutdownNow();
    }

    @Test
    void paste_returnsLinkFromKey() throws Exception {
        AtomicReference<String> contentEncoding = new AtomicReference<>();
        AtomicReference<String> contentType = new AtomicReference<>();
        AtomicReference<String> body = new AtomicReference<>();
        this.server.createContext("/post", exchange -> {
            contentEncoding.set(exchange.getRequestHeaders().getFirst("Content-Encoding"));
            contentType.set(exchange.getRequestHeaders().getFirst("Content-Type"));
            byte[] raw = exchange.getRequestBody().readAllBytes();
            body.set(new String(new GZIPInputStream(new ByteArrayInputStream(raw)).readAllBytes(), StandardCharsets.UTF_8));
            byte[] response = "{\"key\":\"abc\"}".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(201, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });
        this.server.start();

        PublishResult result = this.client.upload("{\"format\":1}".getBytes(StandardCharsets.UTF_8),
                this.pasteUrl, VIEWER, Duration.ofSeconds(5)).get(10, TimeUnit.SECONDS);

        assertEquals(new PublishResult.Published(URI.create("https://viewer/#abc")), result);
        assertEquals("gzip", contentEncoding.get());
        assertEquals("application/json", contentType.get());
        assertEquals("{\"format\":1}", body.get());
    }

    @Test
    void paste_prefersLocationHeaderKey() throws Exception {
        this.server.createContext("/post", exchange -> {
            exchange.getResponseHeaders().add("Location", "fromheader");
            exchange.sendResponseHeaders(201, -1);
            exchange.close();
        });
        this.server.start();

        PublishResult result = this.client.upload(new byte[]{1}, this.pasteUrl, URI.create("https://viewer/"),
                Duration.ofSeconds(5)).get(10, TimeUnit.SECONDS);

        assertEquals(new PublishResult.Published(URI.create("https://viewer/#fromheader")), result);
    }

    @Test
    void paste_non2xxFails() throws Exception {
        this.server.createContext("/post", exchange -> {
            exchange.sendResponseHeaders(500, -1);
            exchange.close();
        });
        this.server.start();

        PublishResult result = this.client.upload(new byte[]{1}, this.pasteUrl, VIEWER, Duration.ofSeconds(5))
                .get(10, TimeUnit.SECONDS);

        PublishResult.Failed failed = (PublishResult.Failed) result;
        assertTrue(failed.reason().contains("500"), failed.reason());
    }

    @Test
    void paste_timeoutFails() throws Exception {
        this.server.createContext("/post", exchange -> {
            try {
                Thread.sleep(1500);
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }
            exchange.sendResponseHeaders(201, -1);
            exchange.close();
        });
        this.server.start();

        AtomicReference<String> completedOn = new AtomicReference<>();
        long start = System.nanoTime();
        CompletableFuture<PublishResult> future = this.client.upload(new byte[]{1}, this.pasteUrl, VIEWER,
                Duration.ofMillis(200));
        long returnedAfterMs = Duration.ofNanos(System.nanoTime() - start).toMillis();
        PublishResult result = future.whenComplete((r, e) -> completedOn.set(Thread.currentThread().getName()))
                .get(10, TimeUnit.SECONDS);

        assertTrue(returnedAfterMs < 150, "upload must not block the caller, took " + returnedAfterMs);
        PublishResult.Failed failed = (PublishResult.Failed) result;
        assertTrue(failed.reason().toLowerCase().contains("time"), failed.reason());
        assertEquals("test-network", completedOn.get());
    }

    @Test
    void paste_unreachableServerFailsWithReason() throws Exception {
        PublishResult result = this.client.upload(new byte[]{1}, URI.create("http://127.0.0.1:1"), VIEWER,
                Duration.ofSeconds(2)).get(10, TimeUnit.SECONDS);
        assertTrue(result instanceof PublishResult.Failed);
    }
}
