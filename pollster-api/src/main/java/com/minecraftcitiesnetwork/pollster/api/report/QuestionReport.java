package com.minecraftcitiesnetwork.pollster.api.report;

import com.minecraftcitiesnetwork.pollster.api.model.Question;

import java.util.List;
import java.util.Map;

/** @param shownTo respondents who saw the question: answered plus skipped */
public record QuestionReport(Question question, int shownTo, int skipped, List<OptionResult> options,
                             Map<Integer, Integer> selectionsPerRespondent, List<TextAnswer> texts) {
}
