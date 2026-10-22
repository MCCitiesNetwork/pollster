package com.minecraftcitiesnetwork.pollster.api.validation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.minecraftcitiesnetwork.pollster.api.model.Option;
import com.minecraftcitiesnetwork.pollster.api.model.Question;
import com.minecraftcitiesnetwork.pollster.api.model.QuestionType;
import com.minecraftcitiesnetwork.pollster.api.model.SurveyDefinition;
import com.minecraftcitiesnetwork.pollster.api.model.SurveyIds;
import com.minecraftcitiesnetwork.pollster.api.model.SurveyStatus;
import java.util.List;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

class SurveyValidatorTest {

    private static List<Option> options(int count) {
        return IntStream.rangeClosed(1, count).mapToObj(i -> new Option("opt-" + i, "Option " + i)).toList();
    }

    private static Question single(String key, int optionCount) {
        return new Question(key, QuestionType.SINGLE, "Pick one", options(optionCount), true, 1, 0, null);
    }

    private static Question multiple(boolean required, int min, int max) {
        return new Question("many", QuestionType.MULTIPLE, "Pick some", options(4), required, min, max, null);
    }

    private static SurveyDefinition survey(String id, Question... questions) {
        return new SurveyDefinition(id, "Name", "Description", SurveyStatus.DRAFT, false, false, false,
                List.of(), List.of(questions));
    }

    @Test
    void validate_acceptsMinimalSurvey() {
        assertTrue(SurveyValidator.validate(survey("town-vote", single("q1", 2))).isEmpty());
    }

    @Test
    void validate_rejectsBadSurveyId() {
        List<ValidationProblem> problems = SurveyValidator.validate(survey("A B", single("q1", 2)));
        assertEquals(1, problems.size());
        assertNull(problems.getFirst().questionKey());
        assertTrue(problems.getFirst().message().contains("id"));
    }

    @Test
    void validate_rejectsDuplicateQuestionKeys() {
        List<ValidationProblem> problems = SurveyValidator.validate(
                survey("town-vote", single("q1", 2), single("q1", 3)));
        assertEquals(1, problems.size());
        assertEquals("q1", problems.getFirst().questionKey());
        assertTrue(problems.getFirst().message().contains("Duplicate question"));
    }

    @Test
    void validate_rejectsDuplicateOptionKeys() {
        Question q = new Question("q1", QuestionType.SINGLE, "Pick",
                List.of(new Option("a", "A"), new Option("a", "Another A")), true, 1, 0, null);
        List<ValidationProblem> problems = SurveyValidator.validate(survey("town-vote", q));
        assertEquals(1, problems.size());
        assertEquals("q1", problems.getFirst().questionKey());
        assertTrue(problems.getFirst().message().contains("Duplicate option"));
    }

    @Test
    void validate_rejectsChoiceWithOneOption() {
        assertEquals(1, SurveyValidator.validate(survey("town-vote", single("q1", 1))).size());
    }

    @Test
    void validate_rejectsChoiceWithFiftyOneOptions() {
        assertEquals(1, SurveyValidator.validate(survey("town-vote", single("q1", 51))).size());
    }

    @Test
    void validate_acceptsChoiceWithFiftyOptions() {
        assertTrue(SurveyValidator.validate(survey("town-vote", single("q1", 50))).isEmpty());
    }

    @Test
    void validate_rejectsMultipleMinAboveMax() {
        assertEquals(1, SurveyValidator.validate(survey("town-vote", multiple(true, 3, 2))).size());
    }

    @Test
    void validate_rejectsMultipleMaxAboveOptionCount() {
        assertEquals(1, SurveyValidator.validate(survey("town-vote", multiple(true, 1, 5))).size());
    }

    @Test
    void validate_rejectsMinZeroOnRequired() {
        assertEquals(1, SurveyValidator.validate(survey("town-vote", multiple(true, 0, 2))).size());
    }

    @Test
    void validate_acceptsMinZeroOnOptional() {
        assertTrue(SurveyValidator.validate(survey("town-vote", multiple(false, 0, 2))).isEmpty());
    }

    @Test
    void validate_acceptsMultipleMaxZeroAsOptionCount() {
        assertTrue(SurveyValidator.validate(survey("town-vote", multiple(true, 1, 0))).isEmpty());
    }

    @Test
    void question_yesNoAlwaysHasYesAndNoOptions() {
        Question q = new Question("q1", QuestionType.YES_NO, "Sure?", List.of(), true, 1, 0, null);
        assertEquals(List.of(new Option("yes", "Yes"), new Option("no", "No")), q.options());
    }

    @Test
    void survey_questionFindsByKey() {
        SurveyDefinition s = survey("town-vote", single("q1", 2));
        assertTrue(s.question("q1").isPresent());
        assertTrue(s.question("nope").isEmpty());
    }

    @Test
    void slugify_makesKeysFromLabels() {
        assertEquals("town-of-oakridge", SurveyIds.slugify("Town of Oakridge!"));
        assertEquals("option", SurveyIds.slugify("  "));
    }

    @Test
    void isValidSlug_enforcesLength() {
        assertTrue(SurveyIds.isValidSlug("abc"));
        assertFalse(SurveyIds.isValidSlug("ab"));
        assertFalse(SurveyIds.isValidSlug("a".repeat(49)));
    }
}
