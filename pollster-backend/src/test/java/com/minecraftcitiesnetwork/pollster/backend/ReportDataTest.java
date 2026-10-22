package com.minecraftcitiesnetwork.pollster.backend;

import com.minecraftcitiesnetwork.pollster.api.report.QuestionTally;
import com.minecraftcitiesnetwork.pollster.api.report.ReportData;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReportDataTest extends AbstractDatabaseTest {

    private static final String HASH = "a".repeat(64);
    private static final Instant NOW = Instant.now().truncatedTo(ChronoUnit.SECONDS);

    private MariaPollsterBackend backend;

    @BeforeEach
    void createBackend() {
        this.backend = new MariaPollsterBackend(database);
        this.backend.open("s", HASH, NOW);
    }

    private void submit(long seconds, AnswerRowInput... answers) {
        this.backend.submit(new ResponseSubmission("s", HASH, UUID.randomUUID(), "Steve",
                NOW.minusSeconds(seconds), NOW, List.of(answers)));
    }

    @Test
    void reportData_countsVotesSkipsAndHidden() {
        submit(10, new AnswerRowInput("q", "a", null));
        submit(10, new AnswerRowInput("q", null, null));
        submit(10, new AnswerRowInput("other", "x", null));

        ReportData data = this.backend.reportData("s");

        QuestionTally tally = data.tallies().get("q");
        assertEquals(3, data.participants());
        assertEquals(Map.of("a", 1), tally.optionVotes());
        assertEquals(1, tally.skipped());
        assertEquals(1, tally.answeredRespondents());
    }

    @Test
    void reportData_selectionsPerRespondent() {
        submit(10, new AnswerRowInput("q", "a", null));
        submit(10, new AnswerRowInput("q", "a", null), new AnswerRowInput("q", "b", null));
        submit(10, new AnswerRowInput("q", "b", null), new AnswerRowInput("q", "c", null));

        QuestionTally tally = this.backend.reportData("s").tallies().get("q");

        assertEquals(Map.of(1, 1, 2, 2), tally.selectionsPerRespondent());
        assertEquals(Map.of("a", 2, "b", 2, "c", 1), tally.optionVotes());
    }

    @Test
    void reportData_collectsTextAnswers() {
        submit(10, new AnswerRowInput("t", null, "hello"));

        QuestionTally tally = this.backend.reportData("s").tallies().get("t");

        assertEquals("hello", tally.texts().getFirst().text());
        assertEquals("Steve", tally.texts().getFirst().playerName());
        assertEquals(NOW, tally.texts().getFirst().submittedAt());
        assertEquals(1, tally.answeredRespondents());
    }

    @Test
    void reportData_averageSeconds() {
        submit(60, new AnswerRowInput("q", "a", null));
        submit(120, new AnswerRowInput("q", "a", null));

        assertEquals(90.0, this.backend.reportData("s").averageSeconds().getAsDouble());
    }

    @Test
    void reportData_emptySurveyHasNoAverage() {
        ReportData data = this.backend.reportData("s");

        assertEquals(0, data.participants());
        assertTrue(data.averageSeconds().isEmpty());
        assertTrue(data.tallies().isEmpty());
    }
}
