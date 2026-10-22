package com.minecraftcitiesnetwork.pollster.publish;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.minecraftcitiesnetwork.pluginInfrastructure.configurate.MessageContainer;
import com.minecraftcitiesnetwork.pollster.SurveyService;
import com.minecraftcitiesnetwork.pollster.backend.SurveyStateRecord;
import com.minecraftcitiesnetwork.pollster.config.MessageKeys;
import com.minecraftcitiesnetwork.pollster.config.Messages;
import com.minecraftcitiesnetwork.pollster.config.Settings;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Executor;
import java.util.function.Supplier;

/**
 * Publishes or exports a survey's report and tells the admin the outcome in chat. Network and
 * file work run off the main thread; every chat line is sent back on the main thread.
 */
public final class ReportPublisher {

    private final SurveyService service;
    private final SnapshotBuilder snapshots;
    private final PasteClient paste;
    private final ExportWriter exports;
    private final Supplier<Settings> settings;
    private final Supplier<Messages> messages;
    private final Path exportsDir;
    private final Clock clock;
    private final Executor io;
    private final Executor mainThread;

    public ReportPublisher(@NotNull SurveyService service, @NotNull SnapshotBuilder snapshots,
                           @NotNull PasteClient paste, @NotNull ExportWriter exports,
                           @NotNull Supplier<Settings> settings, @NotNull Supplier<Messages> messages,
                           @NotNull Path exportsDir, @NotNull Clock clock,
                           @NotNull Executor io, @NotNull Executor mainThread) {
        this.service = service;
        this.snapshots = snapshots;
        this.paste = paste;
        this.exports = exports;
        this.settings = settings;
        this.messages = messages;
        this.exportsDir = exportsDir;
        this.clock = clock;
        this.io = io;
        this.mainThread = mainThread;
    }

    /** Uploads the report and sends {@code publish.success} (clickable link) or {@code publish.failed}. */
    public @NotNull CompletableFuture<Void> publish(@NotNull Audience sender, @NotNull String surveyId) {
        if (this.service.survey(surveyId).isEmpty()) {
            return unavailable(sender);
        }
        Settings.Publishing publishing = this.settings.get().publishing();
        return snapshotBytes(surveyId)
                .thenCompose(json -> this.paste.upload(json, URI.create(publishing.pasteUrl()),
                        URI.create(publishing.viewerUrl()), publishing.timeout()))
                .exceptionally(error -> new PublishResult.Failed(reason(error)))
                .thenAcceptAsync(result -> sender.sendMessage(switch (result) {
                    case PublishResult.Published published -> this.messages.get().messageFor(
                            MessageKeys.PUBLISH_SUCCESS, MessageContainer.markup("link", link(published.link())));
                    case PublishResult.Failed failed -> this.messages.get().messageFor(
                            MessageKeys.PUBLISH_FAILED, MessageContainer.value("reason", failed.reason()));
                }), this.mainThread);
    }

    /** Writes the report to the exports folder and sends {@code export.success} or {@code export.failed}. */
    public @NotNull CompletableFuture<Void> export(@NotNull Audience sender, @NotNull String surveyId) {
        if (this.service.survey(surveyId).isEmpty()) {
            return unavailable(sender);
        }
        return snapshotBytes(surveyId)
                .thenApplyAsync(json -> {
                    try {
                        return this.exports.write(this.exportsDir, surveyId, json, this.clock.instant());
                    } catch (IOException e) {
                        throw new CompletionException(e);
                    }
                }, this.io)
                .handleAsync((path, error) -> error == null
                        ? this.messages.get().messageFor(MessageKeys.EXPORT_SUCCESS,
                                MessageContainer.value("path", path.toString()))
                        : this.messages.get().messageFor(MessageKeys.EXPORT_FAILED,
                                MessageContainer.value("reason", reason(error))), this.mainThread)
                .thenAccept(sender::sendMessage);
    }

    private CompletableFuture<byte[]> snapshotBytes(String surveyId) {
        // report() completes on the main thread, so the cached state read here is main-thread safe.
        return this.service.report(surveyId).thenApply(report -> {
            Optional<SurveyStateRecord> state = this.service.cachedState(surveyId);
            Instant opened = state.map(SurveyStateRecord::openedAt).orElse(null);
            Instant closed = state.map(SurveyStateRecord::closedAt).orElse(null);
            ObjectNode snapshot = this.snapshots.build(report, opened, closed);
            return this.snapshots.toBytes(snapshot);
        });
    }

    private CompletableFuture<Void> unavailable(Audience sender) {
        sender.sendMessage(this.messages.get().messageFor(MessageKeys.SURVEY_UNAVAILABLE));
        return CompletableFuture.completedFuture(null);
    }

    private static Component link(URI uri) {
        String url = uri.toString();
        return Component.text(url, NamedTextColor.AQUA, TextDecoration.UNDERLINED)
                .clickEvent(ClickEvent.openUrl(url));
    }

    private static String reason(Throwable error) {
        Throwable cause = error;
        while (cause instanceof CompletionException && cause.getCause() != null) {
            cause = cause.getCause();
        }
        String message = cause.getMessage();
        return message == null || message.isBlank() ? cause.getClass().getSimpleName() : message;
    }
}
