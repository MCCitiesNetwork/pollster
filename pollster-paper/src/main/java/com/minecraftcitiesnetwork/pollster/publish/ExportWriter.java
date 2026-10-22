package com.minecraftcitiesnetwork.pollster.publish;

import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

/**
 * Writes a snapshot to {@code exports/<id>-<yyyyMMdd-HHmmss>.json}. A second export in the same
 * second gets {@code -2}, {@code -3} and so on, so an earlier export is never overwritten.
 */
public final class ExportWriter {

    // UTC keeps file names comparable between servers and unaffected by daylight saving changes.
    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")
            .withZone(ZoneOffset.UTC);

    public @NotNull Path write(@NotNull Path exportsDir, @NotNull String surveyId, byte[] json,
                               @NotNull Instant now) throws IOException {
        Files.createDirectories(exportsDir);
        String base = surveyId + "-" + STAMP.format(now);
        for (int attempt = 1; ; attempt++) {
            Path file = exportsDir.resolve(attempt == 1 ? base + ".json" : base + "-" + attempt + ".json");
            try {
                // CREATE_NEW claims the name atomically, so two exports racing cannot share a file.
                Files.write(file, json, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
                return file;
            } catch (FileAlreadyExistsException taken) {
                // Try the next suffix.
            }
        }
    }
}
