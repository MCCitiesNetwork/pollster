package com.minecraftcitiesnetwork.pollster.api.report;

import com.minecraftcitiesnetwork.pollster.api.model.Option;
import com.minecraftcitiesnetwork.pollster.api.model.Question;
import com.minecraftcitiesnetwork.pollster.api.model.QuestionType;
import com.minecraftcitiesnetwork.pollster.api.model.SurveyDefinition;
import com.minecraftcitiesnetwork.pollster.api.model.SurveyStatus;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalDouble;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ReportBuilderTest {

    private static SurveyDefinition survey() {
        Question q = new Question("q", QuestionType.SINGLE, "Pick",
                List.of(new Option("a", "Alpha"), new Option("b", "Beta"), new Option("c", "Gamma")),
                false, 0, 0, null);
        return new SurveyDefinition("s", "S", "", SurveyStatus.OPEN, false, false, false, List.of(), List.of(q));
    }

    private static ReportData data(int answered, int skipped, Map<String, Integer> votes) {
        QuestionTally tally = new QuestionTally(answered, skipped, votes, Map.of(), List.of());
        return new ReportData(answered + skipped, OptionalDouble.empty(), Map.of("q", tally));
    }

    @Test
    void build_shownToIsAnsweredPlusSkipped() {
        SurveyReport report = ReportBuilder.build(survey(), data(3, 2, Map.of("a", 3)));
        assertEquals(5, report.questions().getFirst().shownTo());
        assertEquals(2, report.questions().getFirst().skipped());
    }

    @Test
    void build_percentOfShownTo() {
        SurveyReport report = ReportBuilder.build(survey(), data(1, 1, Map.of("a", 1)));
        assertEquals(50.0, report.questions().getFirst().options().getFirst().percent());
    }

    @Test
    void build_keepsZeroVoteOptionsInSurveyOrder() {
        SurveyReport report = ReportBuilder.build(survey(), data(1, 0, Map.of("b", 1)));
        List<OptionResult> options = report.questions().getFirst().options();
        assertEquals(List.of("a", "b", "c"), options.stream().map(OptionResult::key).toList());
        assertEquals(List.of(0, 1, 0), options.stream().map(OptionResult::votes).toList());
    }

    @Test
    void build_listsRemovedOptionKeysWithKeyAsLabel() {
        Map<String, Integer> votes = new LinkedHashMap<>();
        votes.put("a", 1);
        votes.put("gone", 2);
        SurveyReport report = ReportBuilder.build(survey(), data(3, 0, votes));
        OptionResult last = report.questions().getFirst().options().getLast();
        assertEquals("gone", last.key());
        assertEquals("gone (removed)", last.label());
        assertEquals(2, last.votes());
    }
}
