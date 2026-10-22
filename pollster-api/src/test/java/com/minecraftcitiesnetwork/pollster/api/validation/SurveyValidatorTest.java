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

    private static Question withShowIf(Question q, String showIf) {
        return new Question(q.key(), q.type(), q.text(), q.options(), q.required(), q.min(), q.max(), showIf);
    }

    private static Question textQuestion(String key, String showIf) {
        return new Question(key, QuestionType.TEXT, "Why?", List.of(), false, 0, 0, showIf);
    }

    private static boolean anyMessageContains(List<ValidationProblem> problems, String... parts) {
        return problems.stream().anyMatch(p -> {
            for (String part : parts) {
                if (!p.message().contains(part)) {
                    return false;
                }
            }
            return true;
        });
    }

    @Test
    void validate_rejectsForwardReference() {
        List<ValidationProblem> problems = SurveyValidator.validate(survey("town-vote",
                withShowIf(single("q1", 2), "answer(q2) == \"opt-1\""), single("q2", 2)));
        assertTrue(anyMessageContains(problems, "q2", "before"));
    }

    @Test
    void validate_rejectsSelfReference() {
        List<ValidationProblem> problems = SurveyValidator.validate(survey("town-vote",
                withShowIf(single("q1", 2), "answer(q1) == \"opt-1\"")));
        assertTrue(anyMessageContains(problems, "q1", "before"));
    }

    @Test
    void validate_rejectsUnknownQuestionInCondition() {
        List<ValidationProblem> problems = SurveyValidator.validate(survey("town-vote",
                single("q1", 2), textQuestion("q2", "answer(ghost) answered")));
        assertTrue(anyMessageContains(problems, "ghost"));
    }

    @Test
    void validate_rejectsUnknownOptionInCondition() {
        List<ValidationProblem> problems = SurveyValidator.validate(survey("town-vote",
                single("party", 2), textQuestion("q2", "answer(party) == \"zzz\"")));
        assertTrue(anyMessageContains(problems, "zzz"));
    }

    @Test
    void validate_acceptsAnyValueForTextQuestion() {
        assertTrue(SurveyValidator.validate(survey("town-vote",
                textQuestion("q1", null), textQuestion("q2", "answer(q1) == \"anything\""))).isEmpty());
    }

    @Test
    void validate_rejectsHasOnSingle() {
        List<ValidationProblem> problems = SurveyValidator.validate(survey("town-vote",
                single("party", 2), textQuestion("q2", "answer(party) has \"opt-1\"")));
        assertTrue(anyMessageContains(problems, "has"));
    }

    @Test
    void validate_acceptsHasOnMultiple() {
        assertTrue(SurveyValidator.validate(survey("town-vote",
                multiple(false, 1, 0), textQuestion("q2", "answer(many) has \"opt-1\""))).isEmpty());
    }

    @Test
    void validate_reportsConditionSyntaxWithColumn() {
        List<ValidationProblem> problems = SurveyValidator.validate(survey("town-vote",
                single("q1", 2), textQuestion("q2", "answer(q1) == ")));
        assertTrue(anyMessageContains(problems, "column"));
    }

    @Test
    void validate_rejectsKeywordQuestionKey() {
        for (String key : List.of("and", "or", "not", "in", "has", "answered", "answer", "permission")) {
            List<ValidationProblem> problems = SurveyValidator.validate(survey("town-vote", single(key, 2)));
            assertTrue(anyMessageContains(problems, key, "keyword"), key);
        }
    }

    private static SurveyDefinition raw(String name, List<com.minecraftcitiesnetwork.pollster.api.model.Reward> rewards,
                                        List<Question> questions) {
        return new SurveyDefinition("town-vote", name, "d", SurveyStatus.DRAFT, false, false, false, rewards, questions);
    }

    private static void assertReports(List<ValidationProblem> problems, String fragment) {
        assertTrue(problems.stream().anyMatch(p -> p.message().contains(fragment)), problems.toString());
    }

    @Test
    void validate_reportsBlankSurveyName() {
        assertReports(SurveyValidator.validate(raw(" ", List.of(), List.of(single("q1", 2)))), "name");
        assertReports(SurveyValidator.validate(raw(null, List.of(), List.of(single("q1", 2)))), "name");
    }

    @Test
    void validate_reportsBlankQuestionText() {
        Question q = new Question("q1", QuestionType.SINGLE, " ", options(2), true, 1, 0, null);
        assertReports(SurveyValidator.validate(raw("N", List.of(), List.of(q))), "text");
    }

    @Test
    void validate_reportsNullQuestionType() {
        Question q = new Question("q1", null, "Pick", options(2), true, 1, 0, null);
        assertReports(SurveyValidator.validate(raw("N", List.of(), List.of(q))), "type");
    }

    @Test
    void validate_reportsNullOptions() {
        Question q = new Question("q1", QuestionType.SINGLE, "Pick", null, true, 1, 0, null);
        assertReports(SurveyValidator.validate(raw("N", List.of(), List.of(q))), "options");
    }

    @Test
    void validate_reportsNullRewardsAndQuestions() {
        assertReports(SurveyValidator.validate(raw("N", null, List.of(single("q1", 2)))), "rewards");
        assertReports(SurveyValidator.validate(raw("N", List.of(), null)), "questions");
    }
}
