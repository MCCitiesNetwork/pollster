package com.minecraftcitiesnetwork.pollster.publish;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ExportWriterTest {

    private static final Instant NOW = Instant.parse("2026-10-22T12:34:56Z");

    @TempDir
    Path dir;

    @Test
    void write_namesFileAfterSurveyAndTime() throws Exception {
        Path file = new ExportWriter().write(this.dir, "poll-one", bytes("a"), NOW);
        assertEquals("poll-one-20261022-123456.json", file.getFileName().toString());
    }

    @Test
    void write_sameSecondKeepsBothExports() throws Exception {
        ExportWriter writer = new ExportWriter();
        Path first = writer.write(this.dir, "poll-one", bytes("first"), NOW);
        Path second = writer.write(this.dir, "poll-one", bytes("second"), NOW);
        Path third = writer.write(this.dir, "poll-one", bytes("third"), NOW);
        assertNotEquals(first, second);
        assertNotEquals(second, third);
        assertEquals("poll-one-20261022-123456-2.json", second.getFileName().toString());
        assertArrayEquals(bytes("first"), Files.readAllBytes(first));
        assertArrayEquals(bytes("second"), Files.readAllBytes(second));
        assertArrayEquals(bytes("third"), Files.readAllBytes(third));
    }

    private static byte[] bytes(String text) {
        return text.getBytes(StandardCharsets.UTF_8);
    }
}
