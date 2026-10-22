package com.minecraftcitiesnetwork.pollster.publish;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.minecraftcitiesnetwork.pollster.SurveyService;
import com.minecraftcitiesnetwork.pollster.api.model.Option;
import com.minecraftcitiesnetwork.pollster.api.model.Question;
import com.minecraftcitiesnetwork.pollster.api.model.QuestionType;
import com.minecraftcitiesnetwork.pollster.api.model.SurveyDefinition;
import com.minecraftcitiesnetwork.pollster.api.model.SurveyStatus;
import com.minecraftcitiesnetwork.pollster.api.report.SurveyReport;
import com.minecraftcitiesnetwork.pollster.config.ConfigLoader;
import com.minecraftcitiesnetwork.pollster.config.Messages;
import com.minecraftcitiesnetwork.pollster.config.Settings;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayDeque;
import java.util.List;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.Random;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Logger;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;

class ReportPublisherTest {

    @TempDir
    Path dir;

    private final SurveyService service = mock(SurveyService.class);
    private final PasteClient paste = mock(PasteClient.class);
    private final Audience sender = mock(Audience.class);
    /** Stands in for the server's main thread: work queued here runs only when the test says so. */
    private final ArrayDeque<Runnable> mainThread = new ArrayDeque<>();

    private ReportPublisher publisher() throws IOException {
        Messages messages = new ConfigLoader(Files.createTempDirectory("pollster-test"),
                ConfigLoader.class.getClassLoader(), Logger.getLogger("test")).loadMessages();
        Settings settings = new Settings(null, null, 0, null, null);
        Clock clock = Clock.fixed(Instant.parse("2026-10-22T12:00:00Z"), ZoneOffset.UTC);
        return new ReportPublisher(this.service, new SnapshotBuilder(clock, new Random(1)), this.paste,
                new ExportWriter(), () -> settings, () -> messages, this.dir.resolve("exports"), clock,
                Runnable::run, this.mainThread::add);
    }

    private void surveyWithReport() {
        SurveyDefinition survey = new SurveyDefinition("poll-one", "Poll", "", SurveyStatus.OPEN, true, false,
                false, List.of(), List.of(new Question("q", QuestionType.SINGLE, "Pick",
                List.of(new Option("a", "A"), new Option("b", "B")), true, 0, 0, null)));
        when(this.service.survey("poll-one")).thenReturn(Optional.of(survey));
        when(this.service.cachedState("poll-one")).thenReturn(Optional.empty());
        when(this.service.report("poll-one")).thenReturn(CompletableFuture.completedFuture(
                new SurveyReport(survey, 0, OptionalDouble.empty(), List.of())));
    }

    @Test
    void publish_failureSendsReasonOnMainThread() throws Exception {
        surveyWithReport();
        when(this.paste.upload(any(), any(), any(), any())).thenReturn(
                CompletableFuture.completedFuture(new PublishResult.Failed("paste service timed out")));
        CompletableFuture<Void> done = publisher().publish(this.sender, "poll-one");

        // Nothing is sent until the main thread runs the hand-back.
        verify(this.sender, never()).sendMessage(any(Component.class));
        assertEquals(1, this.mainThread.size());
        this.mainThread.poll().run();
        done.join();

        ArgumentCaptor<Component> sent = ArgumentCaptor.forClass(Component.class);
        verify(this.sender).sendMessage(sent.capture());
        String text = PlainTextComponentSerializer.plainText().serialize(sent.getValue());
        assertTrue(text.contains("Could not publish the report: paste service timed out"), text);
    }

    @Test
    void publish_exceptionalUploadStillReportsReason() throws Exception {
        surveyWithReport();
        when(this.paste.upload(any(), any(), any(), any())).thenReturn(
                CompletableFuture.failedFuture(new IOException("connection refused")));
        CompletableFuture<Void> done = publisher().publish(this.sender, "poll-one");
        this.mainThread.poll().run();
        done.join();

        ArgumentCaptor<Component> sent = ArgumentCaptor.forClass(Component.class);
        verify(this.sender).sendMessage(sent.capture());
        String text = PlainTextComponentSerializer.plainText().serialize(sent.getValue());
        assertTrue(text.contains("Could not publish the report: connection refused"), text);
    }
}
