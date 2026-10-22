package com.minecraftcitiesnetwork.pollster.backend;

import com.minecraftcitiesnetwork.pollster.api.flow.Answer;
import com.minecraftcitiesnetwork.pollster.api.result.OpenResult;
import com.minecraftcitiesnetwork.pollster.api.result.SubmitResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MariaPollsterBackendTest extends AbstractDatabaseTest {

    private static final String HASH = "a".repeat(64);
    private static final Instant NOW = Instant.now().truncatedTo(ChronoUnit.SECONDS);

    private MariaPollsterBackend backend;

    @BeforeEach
    void createBackend() {
        this.backend = new MariaPollsterBackend(database);
    }

    private ResponseSubmission submission(String surveyId, UUID player, List<AnswerRowInput> answers) {
        return new ResponseSubmission(surveyId, HASH, player, NOW.minusSeconds(30), NOW, answers);
    }

    private int count(String sql) throws SQLException {
        try (Connection connection = database.dataSource().getConnection();
             Statement statement = connection.createStatement();
             ResultSet rs = statement.executeQuery(sql)) {
            rs.next();
            return rs.getInt(1);
        }
    }

    @Test
    void submit_savesResponseAndAnswers() throws SQLException {
        this.backend.open("s", HASH, NOW);
        Map<String, Answer> answers = new LinkedHashMap<>();
        answers.put("q1", new Answer.Choices(Set.of("a", "b")));
        answers.put("q2", new Answer.Text("hello"));
        UUID player = UUID.randomUUID();

        SubmitResult result = this.backend.submit(submission("s", player, AnswerRowInput.fromAnswers(answers)));

        assertInstanceOf(SubmitResult.Saved.class, result);
        assertEquals(3, count("SELECT COUNT(*) FROM pollster_answers"));
        assertEquals(2, count("SELECT COUNT(*) FROM pollster_answers WHERE question_key = 'q1'"));
        assertTrue(this.backend.hasResponded("s", player));
        assertEquals(Set.of("s"), this.backend.completedSurveys(player));
    }

    @Test
    void fromAnswers_skippedHasNullOptionAndText() {
        List<AnswerRowInput> rows = AnswerRowInput.fromAnswers(Map.of("q", new Answer.Skipped()));
        assertEquals(List.of(new AnswerRowInput("q", null, null)), rows);
    }

    @Test
    void submit_secondTimeReturnsAlreadyResponded() {
        this.backend.open("s", HASH, NOW);
        UUID player = UUID.randomUUID();
        this.backend.submit(submission("s", player, List.of()));

        SubmitResult second = this.backend.submit(submission("s", player, List.of()));

        assertInstanceOf(SubmitResult.AlreadyResponded.class, second);
    }

    @Test
    void submit_concurrentSamePlayer_storesOne() throws Exception {
        this.backend.open("s", HASH, NOW);
        UUID player = UUID.randomUUID();
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            List<Future<SubmitResult>> futures = List.of(
                    pool.submit(() -> {
                        start.await();
                        return this.backend.submit(submission("s", player,
                                List.of(new AnswerRowInput("q", "a", null))));
                    }),
                    pool.submit(() -> {
                        start.await();
                        return this.backend.submit(submission("s", player,
                                List.of(new AnswerRowInput("q", "a", null))));
                    }));
            start.countDown();
            long saved = 0;
            long already = 0;
            for (Future<SubmitResult> future : futures) {
                SubmitResult result = future.get();
                if (result instanceof SubmitResult.Saved) {
                    saved++;
                } else if (result instanceof SubmitResult.AlreadyResponded) {
                    already++;
                }
            }
            assertEquals(1, saved);
            assertEquals(1, already);
            assertEquals(Map.of("s", 1), this.backend.responseCounts());
            assertEquals(1, count("SELECT COUNT(*) FROM pollster_answers"));
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void submit_whenNotOpened_returnsSurveyNotOpen() {
        SubmitResult result = this.backend.submit(submission("s", UUID.randomUUID(), List.of()));
        assertInstanceOf(SubmitResult.SurveyNotOpen.class, result);
    }

    @Test
    void submit_whenClosed_returnsSurveyNotOpen() {
        this.backend.open("s", HASH, NOW);
        this.backend.close("s", NOW);
        SubmitResult result = this.backend.submit(submission("s", UUID.randomUUID(), List.of()));
        assertInstanceOf(SubmitResult.SurveyNotOpen.class, result);
    }

    @Test
    void submit_withDifferentHash_returnsContentChanged() {
        this.backend.open("s", "b".repeat(64), NOW);
        SubmitResult result = this.backend.submit(submission("s", UUID.randomUUID(), List.of()));
        assertInstanceOf(SubmitResult.ContentChanged.class, result);
    }

    @Test
    void open_twice_returnsAlreadyOpened() {
        assertInstanceOf(OpenResult.Opened.class, this.backend.open("s", HASH, NOW));
        assertInstanceOf(OpenResult.AlreadyOpened.class, this.backend.open("s", HASH, NOW));
    }

    @Test
    void reopen_clearsClosedAt() {
        this.backend.open("s", HASH, NOW);
        this.backend.close("s", NOW);
        assertEquals(NOW, this.backend.state("s").orElseThrow().closedAt());

        this.backend.reopen("s");

        assertNull(this.backend.state("s").orElseThrow().closedAt());
    }

    @Test
    void deleteSurveyData_removesEverything() throws SQLException {
        this.backend.open("s", HASH, NOW);
        this.backend.submit(submission("s", UUID.randomUUID(), List.of(new AnswerRowInput("q", "a", null))));

        this.backend.deleteSurveyData("s");

        assertEquals(0, count("SELECT COUNT(*) FROM pollster_responses"));
        assertEquals(0, count("SELECT COUNT(*) FROM pollster_answers"));
        assertTrue(this.backend.state("s").isEmpty());
    }

    @Test
    void markFeatured_upserts() {
        UUID player = UUID.randomUUID();
        assertTrue(this.backend.lastFeatured(player).isEmpty());

        this.backend.markFeatured(player, NOW);
        this.backend.markFeatured(player, NOW.plusSeconds(60));

        assertEquals(NOW.plusSeconds(60), this.backend.lastFeatured(player).orElseThrow());
    }
}
