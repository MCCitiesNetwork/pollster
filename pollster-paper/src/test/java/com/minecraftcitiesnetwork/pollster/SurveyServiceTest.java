package com.minecraftcitiesnetwork.pollster;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.minecraftcitiesnetwork.pollster.SurveyService.SurveyLock;
import com.minecraftcitiesnetwork.pollster.api.flow.SurveyFlow;
import com.minecraftcitiesnetwork.pollster.api.hash.ContentHash;
import com.minecraftcitiesnetwork.pollster.api.model.Option;
import com.minecraftcitiesnetwork.pollster.api.model.Question;
import com.minecraftcitiesnetwork.pollster.api.model.QuestionType;
import com.minecraftcitiesnetwork.pollster.api.model.SurveyDefinition;
import com.minecraftcitiesnetwork.pollster.api.model.SurveyStatus;
import com.minecraftcitiesnetwork.pollster.api.result.OpenResult;
import com.minecraftcitiesnetwork.pollster.api.result.SubmitResult;
import com.minecraftcitiesnetwork.pollster.backend.PollsterBackend;
import com.minecraftcitiesnetwork.pollster.backend.ResponseSubmission;
import com.minecraftcitiesnetwork.pollster.backend.SurveyStateRecord;
import com.minecraftcitiesnetwork.pollster.store.SurveyStore;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.AbstractExecutorService;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;

class SurveyServiceTest {

    /** Runs every task on the calling thread so tests need no waiting. */
    private static final class DirectExecutorService extends AbstractExecutorService {
        @Override public void shutdown() { }
        @Override public List<Runnable> shutdownNow() { return List.of(); }
        @Override public boolean isShutdown() { return false; }
        @Override public boolean isTerminated() { return false; }
        @Override public boolean awaitTermination(long timeout, TimeUnit unit) { return true; }
        @Override public void execute(Runnable command) { command.run(); }
    }

    /** Holds database work until the test runs it, to interleave a write with another. */
    private static final class QueuedExecutorService extends AbstractExecutorService {
        final java.util.ArrayDeque<Runnable> queue = new java.util.ArrayDeque<>();
        @Override public void shutdown() { }
        @Override public List<Runnable> shutdownNow() { return List.of(); }
        @Override public boolean isShutdown() { return false; }
        @Override public boolean isTerminated() { return false; }
        @Override public boolean awaitTermination(long timeout, TimeUnit unit) { return true; }
        @Override public void execute(Runnable command) { this.queue.add(command); }
        void runAll() {
            while (!this.queue.isEmpty()) {
                this.queue.poll().run();
            }
        }
    }

    @TempDir
    Path dir;

    private final PollsterBackend backend = mock(PollsterBackend.class);
    private SurveyStore store;
    private DefaultSurveyService service;

    @BeforeEach
    void setUp() {
        Logger log = Logger.getAnonymousLogger();
        this.store = new SurveyStore(this.dir, log);
        ExecutorService direct = new DirectExecutorService();
        this.service = new DefaultSurveyService(this.store, this.backend,
                new ExecutorState(Runnable::run, direct, direct), log);
    }

    private static SurveyDefinition survey(String id, SurveyStatus status) {
        return new SurveyDefinition(id, "Name", "Desc", status, false, false, false, List.of(),
                List.of(new Question("pick", QuestionType.SINGLE, "Pick", List.of(new Option("a", "A"),
                        new Option("b", "B")), true, 0, 0, null)));
    }

    private SurveyDefinition saved(String id, SurveyStatus status) throws Exception {
        SurveyDefinition survey = survey(id, status);
        this.store.save(survey);
        return survey;
    }

    @Test
    void refreshLocks_detectsHandEdit() throws Exception {
        SurveyDefinition survey = saved("poll-one", SurveyStatus.OPEN);
        when(this.backend.state("poll-one")).thenReturn(Optional.of(
                new SurveyStateRecord("poll-one", "stale-hash", Instant.now(), null)));
        this.service.refreshLocks().join();
        assertEquals(SurveyLock.CONTENT_CHANGED, this.service.lock("poll-one"));

        when(this.backend.state("poll-one")).thenReturn(Optional.of(
                new SurveyStateRecord("poll-one", ContentHash.of(survey), Instant.now(), null)));
        this.service.refreshLocks().join();
        assertEquals(SurveyLock.OK, this.service.lock("poll-one"));
    }

    @Test
    void refreshLocks_draftIsNotOpened() throws Exception {
        saved("draft-one", SurveyStatus.DRAFT);
        this.service.refreshLocks().join();
        assertEquals(SurveyLock.NOT_OPENED, this.service.lock("draft-one"));
    }

    @Test
    void submit_refusesWhenLocked() throws Exception {
        SurveyDefinition survey = saved("poll-one", SurveyStatus.OPEN);
        when(this.backend.state("poll-one")).thenReturn(Optional.of(
                new SurveyStateRecord("poll-one", "stale-hash", Instant.now(), null)));
        this.service.refreshLocks().join();
        SurveyFlow flow = new SurveyFlow(survey, node -> false, Instant.now());
        SubmitResult result = this.service.submit(flow, UUID.randomUUID(), "Steve").join();
        assertInstanceOf(SubmitResult.ContentChanged.class, result);
        verify(this.backend, never()).submit(any());
    }

    @Test
    void submit_passesThroughWhenLockOk() throws Exception {
        SurveyDefinition survey = saved("poll-one", SurveyStatus.OPEN);
        when(this.backend.state("poll-one")).thenReturn(Optional.of(
                new SurveyStateRecord("poll-one", ContentHash.of(survey), Instant.now(), null)));
        when(this.backend.submit(any())).thenReturn(new SubmitResult.Saved(7));
        this.service.refreshLocks().join();
        SurveyFlow flow = new SurveyFlow(survey, node -> false, Instant.now());
        UUID player = UUID.randomUUID();
        SubmitResult result = this.service.submit(flow, player, "Steve").join();
        assertEquals(new SubmitResult.Saved(7), result);
        ArgumentCaptor<ResponseSubmission> captor = ArgumentCaptor.forClass(ResponseSubmission.class);
        verify(this.backend).submit(captor.capture());
        assertEquals(ContentHash.of(survey), captor.getValue().contentHash());
        assertEquals(player, captor.getValue().player());
    }

    @Test
    void open_writesStatusAndHash() throws Exception {
        SurveyDefinition draft = saved("poll-one", SurveyStatus.DRAFT);
        when(this.backend.open(eq("poll-one"), eq(ContentHash.of(draft)), any(Instant.class)))
                .thenReturn(new OpenResult.Opened());
        OpenResult result = this.service.open("poll-one").join();
        assertInstanceOf(OpenResult.Opened.class, result);
        assertEquals(SurveyStatus.OPEN, this.store.get("poll-one").orElseThrow().status());
        verify(this.backend).open(eq("poll-one"), eq(ContentHash.of(draft)), any(Instant.class));
    }

    @Test
    void open_rejectsInvalidSurvey() throws Exception {
        // The store validates on load and save, so an invalid survey can only reach the service
        // through a store that does not; a mock stands in for that to prove open() checks again.
        SurveyDefinition invalid = new SurveyDefinition("poll-bad", "Name", "Desc", SurveyStatus.DRAFT,
                false, false, false, List.of(), List.of(
                new Question("pick", QuestionType.SINGLE, "Pick", List.of(), true, 0, 0, null)));
        SurveyStore mockStore = mock(SurveyStore.class);
        when(mockStore.get("poll-bad")).thenReturn(Optional.of(invalid));
        ExecutorService direct = new DirectExecutorService();
        DefaultSurveyService strict = new DefaultSurveyService(mockStore, this.backend,
                new ExecutorState(Runnable::run, direct, direct), Logger.getAnonymousLogger());
        CompletionException ex = assertThrows(CompletionException.class, () -> strict.open("poll-bad").join());
        assertInstanceOf(IllegalStateException.class, ex.getCause());
        assertFalse(ex.getCause().getMessage().isBlank());
        verify(this.backend, never()).open(any(), any(), any());
    }

    @Test
    void duplicate_createsDraftWithNewId() throws Exception {
        saved("poll-one", SurveyStatus.OPEN);
        SurveyDefinition copy = this.service.duplicate("poll-one", "poll-two").join();
        assertEquals("poll-two", copy.id());
        assertEquals(SurveyStatus.DRAFT, copy.status());
        assertTrue(this.store.exists("poll-two"));
        assertEquals(SurveyStatus.OPEN, this.store.get("poll-one").orElseThrow().status());
        assertTrue(this.service.cachedState("poll-two").isEmpty());
        verify(this.backend, never()).open(any(), any(), any());
    }

    @Test
    void duplicate_rejectsTakenId() throws Exception {
        saved("poll-one", SurveyStatus.OPEN);
        saved("poll-two", SurveyStatus.DRAFT);
        CompletionException ex = assertThrows(CompletionException.class,
                () -> this.service.duplicate("poll-one", "poll-two").join());
        assertInstanceOf(IllegalArgumentException.class, ex.getCause());
    }

    // ---- lifecycle enforcement ---------------------------------------------------------------

    private static SurveyDefinition with(SurveyDefinition s, boolean anonymous, boolean featured) {
        return new SurveyDefinition(s.id(), s.name(), s.description(), s.status(), anonymous, featured,
                s.requiresPermission(), s.rewards(), s.questions());
    }

    private static SurveyDefinition otherQuestions(SurveyDefinition s) {
        return s.withQuestions(List.of(new Question("pick", QuestionType.SINGLE, "Pick again",
                List.of(new Option("a", "A"), new Option("b", "B")), true, 0, 0, null)));
    }

    private static Throwable failure(Runnable call) {
        CompletionException ex = assertThrows(CompletionException.class, call::run);
        return ex.getCause();
    }

    @Test
    void save_rejectsStatusChange() throws Exception {
        SurveyDefinition draft = saved("poll-one", SurveyStatus.DRAFT);
        Throwable cause = failure(() -> this.service.save(draft.withStatus(SurveyStatus.OPEN)).join());
        assertInstanceOf(IllegalStateException.class, cause);
        assertTrue(cause.getMessage().contains("open, close and reopen"), cause.getMessage());
        assertEquals(SurveyStatus.DRAFT, this.store.get("poll-one").orElseThrow().status());
    }

    @Test
    void save_newSurveyMustBeDraft() {
        Throwable cause = failure(() -> this.service.save(survey("poll-new", SurveyStatus.OPEN)).join());
        assertInstanceOf(IllegalStateException.class, cause);
        assertFalse(this.store.exists("poll-new"));
        this.service.save(survey("poll-new", SurveyStatus.DRAFT)).join();
        assertTrue(this.store.exists("poll-new"));
    }

    @Test
    void save_draftAcceptsAnyContentChange() throws Exception {
        SurveyDefinition draft = saved("poll-one", SurveyStatus.DRAFT);
        this.service.save(with(otherQuestions(draft), true, true)).join();
        assertEquals("Pick again", this.store.get("poll-one").orElseThrow().questions().get(0).text());
    }

    @Test
    void save_openRejectsContentAndAnonymousChanges() throws Exception {
        SurveyDefinition open = saved("poll-one", SurveyStatus.OPEN);
        assertInstanceOf(IllegalStateException.class,
                failure(() -> this.service.save(otherQuestions(open)).join()));
        assertInstanceOf(IllegalStateException.class,
                failure(() -> this.service.save(with(open, true, false)).join()));
        assertEquals(open, this.store.get("poll-one").orElseThrow());
        // Featured is not content, so it may still change while the survey is open.
        this.service.save(with(open, false, true)).join();
        assertTrue(this.store.get("poll-one").orElseThrow().featured());
    }

    @Test
    void save_closedRejectsAnyChange() throws Exception {
        SurveyDefinition closed = saved("poll-one", SurveyStatus.CLOSED);
        Throwable cause = failure(() -> this.service.save(with(closed, false, true)).join());
        assertInstanceOf(IllegalStateException.class, cause);
        assertTrue(cause.getMessage().contains("closed"), cause.getMessage());
        assertEquals(closed, this.store.get("poll-one").orElseThrow());
        this.service.save(closed).join();
    }

    @Test
    void save_staleOpenSnapshotCannotUndoClose() throws Exception {
        SurveyDefinition open = saved("poll-one", SurveyStatus.OPEN);
        this.service.close("poll-one").join();
        // An editor that read the survey before the close toggles Featured afterwards.
        assertInstanceOf(IllegalStateException.class,
                failure(() -> this.service.save(with(open, false, true)).join()));
        assertEquals(SurveyStatus.CLOSED, this.store.get("poll-one").orElseThrow().status());
    }

    @Test
    void close_writesCurrentDefinitionNotSnapshot() throws Exception {
        QueuedExecutorService queued = new QueuedExecutorService();
        DefaultSurveyService deferred = new DefaultSurveyService(this.store, this.backend,
                new ExecutorState(Runnable::run, queued, queued), Logger.getAnonymousLogger());
        SurveyDefinition open = saved("poll-one", SurveyStatus.OPEN);
        var closing = deferred.close("poll-one");
        // A Featured toggle lands between the close being asked for and its write.
        this.store.save(with(open, false, true));
        queued.runAll();
        closing.join();
        SurveyDefinition result = this.store.get("poll-one").orElseThrow();
        assertEquals(SurveyStatus.CLOSED, result.status());
        assertTrue(result.featured(), "the close must not write back the pre-toggle snapshot");
    }

    @Test
    void open_requiresDraft() throws Exception {
        saved("poll-one", SurveyStatus.CLOSED);
        assertInstanceOf(IllegalStateException.class, failure(() -> this.service.open("poll-one").join()));
        verify(this.backend, never()).open(any(), any(), any());
        assertEquals(SurveyStatus.CLOSED, this.store.get("poll-one").orElseThrow().status());
    }

    @Test
    void close_requiresOpen() throws Exception {
        saved("poll-one", SurveyStatus.DRAFT);
        assertInstanceOf(IllegalStateException.class, failure(() -> this.service.close("poll-one").join()));
        verify(this.backend, never()).close(any(), any());
    }

    @Test
    void reopen_requiresClosed() throws Exception {
        saved("poll-one", SurveyStatus.DRAFT);
        assertInstanceOf(IllegalStateException.class, failure(() -> this.service.reopen("poll-one").join()));
        verify(this.backend, never()).reopen(any());
        assertEquals(SurveyStatus.DRAFT, this.store.get("poll-one").orElseThrow().status());
    }
}
