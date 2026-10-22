package com.minecraftcitiesnetwork.pollster.api.report;

import java.util.List;
import java.util.Map;

/**
 * Stored answers for one question.
 *
 * @param answeredRespondents respondents with at least one non-skipped answer row
 * @param skipped             respondents who skipped it; respondents it was hidden from have no rows at all
 * @param optionVotes         votes by option key, in the order storage returned them
 * @param selectionsPerRespondent how many respondents picked N options, keyed by N
 */
public record QuestionTally(int answeredRespondents, int skipped, Map<String, Integer> optionVotes,
                            Map<Integer, Integer> selectionsPerRespondent, List<TextAnswer> texts) {

    public QuestionTally {
        // Copies keep iteration order, which the report shows for removed options.
        optionVotes = java.util.Collections.unmodifiableMap(new java.util.LinkedHashMap<>(optionVotes));
        selectionsPerRespondent = java.util.Collections.unmodifiableMap(
                new java.util.TreeMap<>(selectionsPerRespondent));
        texts = List.copyOf(texts);
    }

    public static QuestionTally empty() {
        return new QuestionTally(0, 0, Map.of(), Map.of(), List.of());
    }
}
