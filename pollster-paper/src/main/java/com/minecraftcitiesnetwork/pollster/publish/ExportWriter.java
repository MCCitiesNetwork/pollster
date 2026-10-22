package com.minecraftcitiesnetwork.pollster.publish;

import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

/** Writes a snapshot to {@code exports/<id>-<yyyyMMdd-HHmmss>.json}. */
public final class ExportWriter {

    // UTC keeps file names comparable between servers and unaffected by daylight saving changes.
    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")
            .withZone(ZoneOffset.UTC);

    public @NotNull Path write(@NotNull Path exportsDir, @NotNull String surveyId, byte[] json,
                               @NotNull Instant now) throws IOException {
        Files.createDirectories(exportsDir);
        Path file = exportsDir.resolve(surveyId + "-" + STAMP.format(now) + ".json");
        Files.write(file, json);
        return file;
    }
}
