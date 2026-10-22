package com.minecraftcitiesnetwork.pollster.api.report;

import com.minecraftcitiesnetwork.pollster.api.model.Option;
import com.minecraftcitiesnetwork.pollster.api.model.Question;
import com.minecraftcitiesnetwork.pollster.api.model.SurveyDefinition;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Joins stored aggregates with the current survey definition. */
public final class ReportBuilder {

    private ReportBuilder() {
    }

    public static SurveyReport build(SurveyDefinition survey, ReportData data) {
        List<QuestionReport> questions = new ArrayList<>();
        for (Question question : survey.questions()) {
            questions.add(buildQuestion(question, data.tallies().getOrDefault(question.key(), QuestionTally.empty())));
        }
        return new SurveyReport(survey, data.participants(), data.averageSeconds(), questions);
    }

    private static QuestionReport buildQuestion(Question question, QuestionTally tally) {
        int shownTo = tally.answeredRespondents() + tally.skipped();
        List<OptionResult> options = new ArrayList<>();
        Set<String> known = new HashSet<>();
        for (Option option : question.options()) {
            known.add(option.key());
            options.add(result(option.key(), option.label(), tally.optionVotes().get(option.key()), shownTo));
        }
        // Votes for options an admin has since removed stay visible, after the current options.
        for (Map.Entry<String, Integer> vote : tally.optionVotes().entrySet()) {
            if (!known.contains(vote.getKey())) {
                options.add(result(vote.getKey(), vote.getKey() + " (removed)", vote.getValue(), shownTo));
            }
        }
        return new QuestionReport(question, shownTo, tally.skipped(), options,
                tally.selectionsPerRespondent(), tally.texts());
    }

    private static OptionResult result(String key, String label, Integer votes, int shownTo) {
        int count = votes == null ? 0 : votes;
        double percent = shownTo == 0 ? 0.0 : count * 100.0 / shownTo;
        return new OptionResult(key, label, count, percent);
    }
}
