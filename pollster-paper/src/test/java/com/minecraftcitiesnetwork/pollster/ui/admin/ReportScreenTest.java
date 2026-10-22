package com.minecraftcitiesnetwork.pollster.ui.admin;

import com.minecraftcitiesnetwork.pollster.api.model.Option;
import com.minecraftcitiesnetwork.pollster.api.model.Question;
import com.minecraftcitiesnetwork.pollster.api.model.QuestionType;
import com.minecraftcitiesnetwork.pollster.api.model.SurveyDefinition;
import com.minecraftcitiesnetwork.pollster.api.model.SurveyStatus;
import com.minecraftcitiesnetwork.pollster.api.report.OptionResult;
import com.minecraftcitiesnetwork.pollster.api.report.QuestionReport;
import com.minecraftcitiesnetwork.pollster.api.report.SurveyReport;
import com.minecraftcitiesnetwork.pollster.api.report.TextAnswer;
import com.minecraftcitiesnetwork.pollster.ui.Screen;
import com.minecraftcitiesnetwork.pollster.ui.Style;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.OptionalDouble;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReportScreenTest {

    private static final ReportScreen.Deps DEPS = new ReportScreen.Deps(Style.defaults(), null, null, null,
            (ctx, id) -> { });

    private static String plain(Component c) {
        return PlainTextComponentSerializer.plainText().serialize(c);
    }

    private static List<String> lines(Screen s) {
        return s.body().stream().map(ReportScreenTest::plain).toList();
    }

    private static SurveyReport report(boolean anonymous, int answers, String text) {
        Question single = new Question("tree", QuestionType.SINGLE, "Which tree?",
                List.of(new Option("a", "Alder"), new Option("b", "Birch")), true, 0, 0, null);
        Question why = new Question("why", QuestionType.TEXT, "Why?", List.of(), false, 0, 0, "tree == a");
        SurveyDefinition survey = new SurveyDefinition("poll", "Poll", "d", SurveyStatus.OPEN, anonymous, false,
                false, List.of(), List.of(single, why));
        List<TextAnswer> texts = new ArrayList<>();
        for (int i = 0; i < answers; i++) {
            texts.add(new TextAnswer(text + i, "Steve" + i, Instant.EPOCH));
        }
        return new SurveyReport(survey, 10, OptionalDouble.of(90),
                List.of(new QuestionReport(single, 10, 0,
                                List.of(new OptionResult("a", "Alder", 5, 50.0), new OptionResult("b", "Birch", 5, 50.0)),
                                Map.of(), List.of()),
                        new QuestionReport(why, 4, 0, List.of(), Map.of(), texts)));
    }

    @Test
    void report_barsUseTenBlocks() {
        assertEquals("█████░░░░░", ReportScreen.bar(50.0));
        assertEquals("░░░░░░░░░░", ReportScreen.bar(0));
        assertEquals("██████████", ReportScreen.bar(100));
        Screen screen = ReportScreen.create(DEPS, report(false, 0, "x"));
        assertTrue(lines(screen).stream().anyMatch(l -> l.contains("█████░░░░░") && l.contains("50%") && l.contains("5")));
    }

    @Test
    void report_showsShownToForConditionalQuestions() {
        Screen screen = ReportScreen.create(DEPS, report(false, 0, "x"));
        assertTrue(lines(screen).stream().anyMatch(l -> l.contains("Shown to 4 of 10")));
    }

    @Test
    void report_textAnswersHideNamesWhenAnonymous() {
        Screen anonymous = TextAnswersScreen.create(DEPS, report(true, 3, "hi"), "why", 0);
        assertTrue(lines(anonymous).stream().noneMatch(l -> l.contains("Steve")));
        Screen named = TextAnswersScreen.create(DEPS, report(false, 3, "hi"), "why", 0);
        assertTrue(lines(named).stream().anyMatch(l -> l.contains("Steve0")));
    }

    @Test
    void report_textAnswerMarkupIsLiteral() {
        Screen screen = TextAnswersScreen.create(DEPS, report(false, 1, "<bold>x"), "why", 0);
        Component line = screen.body().stream().filter(c -> plain(c).contains("<bold>x0")).findFirst().orElseThrow();
        assertFalse(line.hasDecoration(TextDecoration.BOLD));
    }

    @Test
    void report_textAnswersPageByEight() {
        SurveyReport r = report(false, 20, "a");
        long first = lines(TextAnswersScreen.create(DEPS, r, "why", 0)).stream().filter(l -> l.contains("Steve")).count();
        long last = lines(TextAnswersScreen.create(DEPS, r, "why", 2)).stream().filter(l -> l.contains("Steve")).count();
        assertEquals(8, first);
        assertEquals(4, last);
    }
}
