package com.minecraftcitiesnetwork.pollster.backend;

import com.minecraftcitiesnetwork.pollster.api.flow.Answer;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** One stored answer row. Both {@code optionKey} and {@code text} null means the question was skipped. */
public record AnswerRowInput(String questionKey, @Nullable String optionKey, @Nullable String text) {

    public static List<AnswerRowInput> fromAnswers(Map<String, Answer> answers) {
        List<AnswerRowInput> rows = new ArrayList<>();
        answers.forEach((key, answer) -> {
            switch (answer) {
                case Answer.Choice choice -> rows.add(new AnswerRowInput(key, choice.optionKey(), null));
                case Answer.Choices choices -> {
                    if (choices.optionKeys().isEmpty()) {
                        // Nothing ticked is a skip; with no row the respondent would read as "hidden".
                        rows.add(new AnswerRowInput(key, null, null));
                    }
                    for (String optionKey : choices.optionKeys()) {
                        rows.add(new AnswerRowInput(key, optionKey, null));
                    }
                }
                case Answer.Text text -> rows.add(new AnswerRowInput(key, null, text.value()));
                case Answer.Skipped skipped -> rows.add(new AnswerRowInput(key, null, null));
            }
        });
        return rows;
    }
}
