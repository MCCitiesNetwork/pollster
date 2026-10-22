package com.minecraftcitiesnetwork.pollster.publish;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.minecraftcitiesnetwork.pollster.api.model.Option;
import com.minecraftcitiesnetwork.pollster.api.model.Question;
import com.minecraftcitiesnetwork.pollster.api.model.QuestionType;
import com.minecraftcitiesnetwork.pollster.api.model.SurveyDefinition;
import com.minecraftcitiesnetwork.pollster.api.model.SurveyStatus;
import com.minecraftcitiesnetwork.pollster.api.report.OptionResult;
import com.minecraftcitiesnetwork.pollster.api.report.QuestionReport;
import com.minecraftcitiesnetwork.pollster.api.report.SurveyReport;
import com.minecraftcitiesnetwork.pollster.api.report.TextAnswer;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalDouble;
import java.util.Random;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SnapshotBuilderTest {

    private static final Instant NOW = Instant.parse("2026-10-22T18:00:00Z");
    private static final Instant OPENED = Instant.parse("2026-10-01T09:00:00Z");

    private static SnapshotBuilder builder(long seed) {
        return new SnapshotBuilder(Clock.fixed(NOW, ZoneOffset.UTC), new Random(seed));
    }

    private static List<TextAnswer> answers(int count) {
        List<TextAnswer> list = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            list.add(new TextAnswer("answer " + i, new UUID(0, i), NOW));
        }
        return list;
    }

    /** What the service resolves for {@link #answers}: every author but the last has a cached name. */
    private static Map<UUID, String> names(int count) {
        Map<UUID, String> names = new HashMap<>();
        for (int i = 0; i < count - 1; i++) {
            names.put(new UUID(0, i), "Player" + i);
        }
        names.put(new UUID(0, count - 1), "00000000\u2026");
        return names;
    }

    static SurveyReport report(boolean anonymous) {
        Question single = new Question("tree", QuestionType.SINGLE, "<bold>Which tree?</bold>",
                List.of(new Option("alder", "Alder"), new Option("birch", "Birch")), true, 0, 0, null);
        Question yesNo = new Question("park", QuestionType.YES_NO, "A new park?", List.of(), true, 0, 0, null);
        Question multi = new Question("issues", QuestionType.MULTIPLE, "Issues?",
                List.of(new Option("a", "A"), new Option("b", "B")), true, 1, 2, "tree == alder");
        Question text = new Question("why", QuestionType.TEXT, "Why?", List.of(), false, 0, 0, null);
        SurveyDefinition survey = new SurveyDefinition("tree-poll", "City <red>Priorities", "Help us.",
                SurveyStatus.OPEN, anonymous, false, false, List.of(),
                List.of(single, yesNo, multi, text));
        List<QuestionReport> questions = List.of(
                new QuestionReport(single, 10, 0,
                        List.of(new OptionResult("alder", "Alder", 3, 30.0), new OptionResult("birch", "Birch", 7, 70.0)),
                        Map.of(), List.of()),
                new QuestionReport(yesNo, 10, 1,
                        List.of(new OptionResult("yes", "Yes", 6, 60.0), new OptionResult("no", "No", 3, 30.0)),
                        Map.of(), List.of()),
                new QuestionReport(multi, 3, 0,
                        List.of(new OptionResult("a", "A", 3, 100.0), new OptionResult("b", "B", 1, 33.3)),
                        Map.of(1, 2, 2, 1), List.of()),
                new QuestionReport(text, 10, 0, List.of(), Map.of(), answers(12)));
        return new SurveyReport(survey, 10, OptionalDouble.of(183.6), questions,
                anonymous ? Map.of() : names(12));
    }

    private static JsonNode textAnswers(ObjectNode snapshot) {
        return snapshot.get("questions").get(3).get("answers");
    }

    @Test
    void snapshot_anonymousHasNoNamesAndShuffles() {
        ObjectNode snapshot = builder(42).build(report(true), OPENED, null);
        JsonNode answers = textAnswers(snapshot);
        List<String> order = new ArrayList<>();
        for (JsonNode answer : answers) {
            assertTrue(answer.get("player").isNull());
            order.add(answer.get("text").asText());
        }
        assertEquals(12, order.size());
        List<String> input = answers(12).stream().map(TextAnswer::text).toList();
        assertNotEquals(input, order);
        assertEquals(input.stream().sorted().toList(), order.stream().sorted().toList());
    }

    @Test
    void snapshot_namedUsesResolvedNames() {
        ObjectNode snapshot = builder(42).build(report(false), OPENED, null);
        JsonNode answers = textAnswers(snapshot);
        for (int i = 0; i < 11; i++) {
            assertEquals("answer " + i, answers.get(i).get("text").asText());
            assertEquals("Player" + i, answers.get(i).get("player").asText());
        }
        assertEquals("00000000\u2026", answers.get(11).get("player").asText());
    }

    @Test
    void snapshot_matchesSpecShape() {
        ObjectNode s = builder(1).build(report(false), OPENED, null);
        assertEquals(1, s.get("format").asInt());
        assertEquals("2026-10-22T18:00:00Z", s.get("generatedAt").asText());
        JsonNode survey = s.get("survey");
        assertEquals("tree-poll", survey.get("id").asText());
        assertEquals("City Priorities", survey.get("name").asText());
        assertEquals("Help us.", survey.get("description").asText());
        assertEquals(false, survey.get("anonymous").asBoolean());
        assertEquals("2026-10-01T09:00:00Z", survey.get("openedAt").asText());
        assertTrue(survey.get("closedAt").isNull());
        assertEquals(10, s.get("totals").get("participants").asInt());
        assertEquals(184, s.get("totals").get("averageSeconds").asInt());

        JsonNode single = s.get("questions").get(0);
        assertEquals("tree", single.get("key").asText());
        assertEquals("SINGLE", single.get("type").asText());
        assertEquals("Which tree?", single.get("text").asText());
        assertEquals(10, single.get("shownTo").asInt());
        assertEquals(0, single.get("skipped").asInt());
        assertEquals("alder", single.get("options").get(0).get("key").asText());
        assertEquals("Alder", single.get("options").get(0).get("label").asText());
        assertEquals(3, single.get("options").get(0).get("votes").asInt());
        assertTrue(single.path("answers").isMissingNode());

        JsonNode yesNo = s.get("questions").get(1);
        assertEquals("YES_NO", yesNo.get("type").asText());
        assertEquals("yes", yesNo.get("options").get(0).get("key").asText());
        assertEquals(1, yesNo.get("skipped").asInt());

        JsonNode multi = s.get("questions").get(2);
        assertEquals("MULTIPLE", multi.get("type").asText());
        assertEquals(2, multi.get("selectionsPerRespondent").get("1").asInt());
        assertEquals(1, multi.get("selectionsPerRespondent").get("2").asInt());

        JsonNode text = s.get("questions").get(3);
        assertEquals("TEXT", text.get("type").asText());
        assertEquals(10, text.get("shownTo").asInt());
        assertTrue(text.path("options").isMissingNode());
    }

    @Test
    void snapshot_closedSurveyWritesClosedAt() {
        ObjectNode s = builder(1).build(report(false), OPENED, NOW);
        assertEquals("2026-10-22T18:00:00Z", s.get("survey").get("closedAt").asText());
    }

    @Test
    void snapshot_writesViewerFixtures() throws Exception {
        String dir = System.getProperty("pollster.viewerFixtures");
        assertTrue(dir != null && !dir.isBlank(), "pollster.viewerFixtures must be set by build.gradle.kts");
        ObjectMapper mapper = new ObjectMapper();
        Path target = Path.of(dir);
        Files.createDirectories(target);
        Files.writeString(target.resolve("named.json"), mapper.writerWithDefaultPrettyPrinter()
                .writeValueAsString(builder(7).build(viewerReport(false), OPENED, null)) + "\n");
        Files.writeString(target.resolve("anonymous.json"), mapper.writerWithDefaultPrettyPrinter()
                .writeValueAsString(builder(7).build(viewerReport(true), OPENED,
                        Instant.parse("2026-10-20T09:00:00Z"))) + "\n");
    }

    private static final String[][] REASONS = {
            {"Better public transport please", "Alex"}, {"More parks near the spawn", "Bri"},
            {"I like the current mayor", "Cam"}, {"Lower taxes on small shops", "Dana"},
            {"Faster road building tools", "Eli"}, {"Keep the build limits as they are", "Finn"},
            {"Needs more events", "Gus"}, {"Please add a city hall", "Hana"},
            {"Fix the lag at the market", "Ivy"}, {"Wider sidewalks", "Jo"},
            {"Less noise from farms", "Kai"}, {"More police presence", "Lux"},
            {"Nothing to add", "Max"}, {"Great work so far", "Nia"},
            {"Let players vote more often", "Oz"}, {"Use <b>x</b> in this reply", "Pia"},
            {"Clearer plot rules", "Quin"}, {"More space for new builders", "Rae"},
            {"A tram line would be great", "Sol"}, {"Open the north district", "Tess"},
            {"Hold more build contests", "Uma"}, {"Keep the server friendly", "Vic"},
            {"Add a library", "Wren"}, {"Improve the spawn signs", "Xan"},
            {"Reduce the plot price", "Yui"}, {"More tutorials for new players", "Zed"},
            {"Celebrate city anniversaries", "Ash"}, {"Protect old buildings", "Bo"},
    };

    private static QuestionReport choice(Question question, int shownTo, int skipped, int... votes) {
        int answered = shownTo - skipped;
        List<OptionResult> results = new ArrayList<>();
        for (int i = 0; i < votes.length; i++) {
            Option option = question.options().get(i);
            results.add(new OptionResult(option.key(), option.label(), votes[i], 100.0 * votes[i] / answered));
        }
        return new QuestionReport(question, shownTo, skipped, results, Map.of(), List.of());
    }

    private static List<Option> options(String... keyAndLabel) {
        List<Option> list = new ArrayList<>();
        for (int i = 0; i < keyAndLabel.length; i += 2) {
            list.add(new Option(keyAndLabel[i], keyAndLabel[i + 1]));
        }
        return list;
    }

    /** The same sample the viewer was first written against, now produced by the real builder. */
    private static SurveyReport viewerReport(boolean anonymous) {
        Question tree = new Question("tree", QuestionType.SINGLE, "Which tree should be the city symbol?",
                options("alder", "Alder", "birch", "Birch", "cedar", "Cedar", "dogwood", "Dogwood", "elm", "Elm",
                        "fir", "Fir", "ginkgo", "Ginkgo", "hazel", "Hazel", "ironwood", "Ironwood",
                        "juniper", "Juniper", "katsura", "Katsura", "larch", "Larch"), true, 0, 0, null);
        Question issues = new Question("issues", QuestionType.MULTIPLE,
                "Which issues matter most? Pick up to three.",
                options("transit", "Public transport", "parks", "Parks and green space", "housing", "Housing",
                        "events", "Community events", "safety", "Safety"), true, 1, 3, null);
        Question reason = new Question("reason", QuestionType.TEXT, "Anything else you want the council to know?",
                List.of(), false, 0, 0, "tree == alder");
        Question again = new Question("return", QuestionType.YES_NO, "Will you keep playing next month?",
                List.of(), true, 0, 0, null);
        Question rating = new Question("rating", QuestionType.SINGLE, "How do you rate the market? (optional)",
                options("ok", "Okay", "good", "Good", "bad", "Poor"), false, 0, 0, null);

        List<OptionResult> issueResults = new ArrayList<>();
        int[] issueVotes = {31, 27, 22, 18, 9};
        for (int i = 0; i < issueVotes.length; i++) {
            Option option = issues.options().get(i);
            issueResults.add(new OptionResult(option.key(), option.label(), issueVotes[i], 100.0 * issueVotes[i] / 52));
        }
        List<TextAnswer> answers = new ArrayList<>();
        Map<UUID, String> names = new HashMap<>();
        for (int i = 0; i < REASONS.length; i++) {
            UUID player = new UUID(0, i);
            answers.add(new TextAnswer(REASONS[i][0], player, NOW));
            names.put(player, REASONS[i][1]);
        }
        List<QuestionReport> reports = List.of(
                choice(tree, 52, 0, 1, 9, 8, 11, 5, 4, 3, 2, 1, 1, 7, 0),
                new QuestionReport(issues, 52, 0, issueResults, Map.of(1, 10, 2, 25, 3, 17), List.of()),
                new QuestionReport(reason, 30, 2, List.of(), Map.of(), answers),
                choice(again, 52, 0, 44, 8),
                choice(rating, 52, 9, 13, 24, 6));
        SurveyDefinition survey = new SurveyDefinition(anonymous ? "tree-poll-anon" : "tree-poll",
                anonymous ? "City Priorities (anonymous)" : "City Priorities 2026",
                "Help shape what the city builds next. Thanks for taking part!",
                anonymous ? SurveyStatus.CLOSED : SurveyStatus.OPEN, anonymous, false, false, List.of(),
                List.of(tree, issues, reason, again, rating));
        return new SurveyReport(survey, 52, OptionalDouble.of(184), reports, anonymous ? Map.of() : names);
    }
}
