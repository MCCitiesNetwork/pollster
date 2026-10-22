package com.minecraftcitiesnetwork.pollster.backend;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.minecraftcitiesnetwork.pollster.api.flow.Answer;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class AnswerRowInputTest {

    @Test
    void fromAnswers_emptyChoicesIsOneSkipRow() {
        Map<String, Answer> answers = new LinkedHashMap<>();
        answers.put("many", new Answer.Choices(Set.of()));
        // Without a row the respondent would look as if the question had been hidden from them.
        assertEquals(List.of(new AnswerRowInput("many", null, null)), AnswerRowInput.fromAnswers(answers));
    }

    @Test
    void fromAnswers_skippedIsOneSkipRow() {
        Map<String, Answer> answers = Map.of("q", new Answer.Skipped());
        assertEquals(List.of(new AnswerRowInput("q", null, null)), AnswerRowInput.fromAnswers(answers));
    }
}
