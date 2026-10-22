package com.minecraftcitiesnetwork.pollster.api.flow;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.minecraftcitiesnetwork.pollster.api.model.Option;
import com.minecraftcitiesnetwork.pollster.api.model.Question;
import com.minecraftcitiesnetwork.pollster.api.model.QuestionType;
import com.minecraftcitiesnetwork.pollster.api.model.SurveyDefinition;
import com.minecraftcitiesnetwork.pollster.api.model.SurveyStatus;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;
import org.junit.jupiter.api.Test;

class SurveyFlowTest {

    private static final List<Option> AB = List.of(new Option("a", "A"), new Option("b", "B"), new Option("c", "C"));

    private static Question single(String key, String showIf) {
        return new Question(key, QuestionType.SINGLE, "Pick", AB, true, 1, 0, showIf);
    }

    private static Question text(String key, boolean required) {
        return new Question(key, QuestionType.TEXT, "Say", List.of(), required, 0, 0, null);
    }

    private static Question multiple(String key, int min, int max) {
        return new Question(key, QuestionType.MULTIPLE, "Pick some", AB, true, min, max, null);
    }

    private static SurveyFlow flow(Predicate<String> permissions, Question... questions) {
        SurveyDefinition survey = new SurveyDefinition("test-survey", "Name", "Desc", SurveyStatus.OPEN, false, false,
                false, List.of(), List.of(questions));
        return new SurveyFlow(survey, permissions, Instant.EPOCH);
    }

    private static SurveyFlow flow(Question... questions) {
        return flow(node -> false, questions);
    }

    private static Answer.Choice choice(String key) {
        return new Answer.Choice(key);
    }

    private static String currentKey(SurveyFlow flow) {
        return flow.current().orElseThrow().key();
    }

    @Test
    void forward_skipsHiddenQuestions() {
        SurveyFlow flow = flow(single("q1", null), single("q2", "answer(q1) == \"a\""), single("q3", null));
        assertTrue(flow.answer(choice("b")) instanceof AnswerResult.Accepted);
        assertEquals("q3", currentKey(flow));
        assertEquals(2, flow.visibleCount());
    }

    @Test
    void back_thenChangingAnswer_dropsNowHiddenAnswers() {
        SurveyFlow flow = flow(single("q1", null), single("q2", "answer(q1) == \"a\""), single("q3", null));
        flow.answer(choice("a"));
        flow.answer(choice("c"));
        assertTrue(flow.back());
        assertTrue(flow.back());
        flow.answer(choice("b"));
        assertFalse(flow.answers().containsKey("q2"));
        assertEquals("q3", currentKey(flow));
    }

    @Test
    void back_keepsStillVisibleAnswers() {
        SurveyFlow flow = flow(single("q1", null), single("q2", null));
        flow.answer(choice("a"));
        flow.answer(choice("b"));
        flow.back();
        assertEquals(choice("b"), flow.answerFor("q2").orElseThrow());
        flow.back();
        flow.answer(choice("c"));
        assertEquals(choice("b"), flow.answerFor("q2").orElseThrow());
    }

    @Test
    void back_atFirstQuestion_returnsFalse() {
        SurveyFlow flow = flow(single("q1", null));
        assertFalse(flow.back());
    }

    @Test
    void answer_rejectsEmptyRequiredText() {
        SurveyFlow flow = flow(text("q1", true));
        assertEquals(new AnswerResult.Invalid(AnswerResult.Reason.REQUIRED), flow.answer(new Answer.Text("  ")));
    }

    @Test
    void answer_rejectsTooLongText() {
        SurveyFlow flow = flow(text("q1", true));
        assertEquals(new AnswerResult.Invalid(AnswerResult.Reason.TOO_LONG),
                flow.answer(new Answer.Text("x".repeat(256))));
        assertTrue(flow.answer(new Answer.Text("x".repeat(255))) instanceof AnswerResult.Accepted);
    }

    @Test
    void answer_rejectsUnknownOption() {
        SurveyFlow flow = flow(single("q1", null));
        assertEquals(new AnswerResult.Invalid(AnswerResult.Reason.NOT_AN_OPTION), flow.answer(choice("zzz")));
    }

    @Test
    void answer_rejectsWrongType() {
        SurveyFlow flow = flow(single("q1", null));
        assertEquals(new AnswerResult.Invalid(AnswerResult.Reason.WRONG_TYPE), flow.answer(new Answer.Text("a")));
    }

    @Test
    void answer_enforcesMinMax() {
        SurveyFlow flow = flow(multiple("q1", 2, 2));
        assertEquals(new AnswerResult.Invalid(AnswerResult.Reason.TOO_FEW),
                flow.answer(new Answer.Choices(Set.of("a"))));
        assertEquals(new AnswerResult.Invalid(AnswerResult.Reason.TOO_MANY),
                flow.answer(new Answer.Choices(new LinkedHashSet<>(List.of("a", "b", "c")))));
        assertTrue(flow.answer(new Answer.Choices(new LinkedHashSet<>(List.of("a", "b")))) instanceof
                AnswerResult.Accepted);
    }

    @Test
    void skip_onRequired_isRejected() {
        SurveyFlow flow = flow(single("q1", null));
        assertEquals(new AnswerResult.Invalid(AnswerResult.Reason.NOT_SKIPPABLE), flow.skip());
        assertEquals("q1", currentKey(flow));
    }

    @Test
    void skip_onOptional_recordsSkipped() {
        SurveyFlow flow = flow(text("q1", false), text("q2", true));
        assertTrue(flow.skip() instanceof AnswerResult.Accepted);
        assertEquals(new Answer.Skipped(), flow.answerFor("q1").orElseThrow());
        assertEquals("q2", currentKey(flow));
    }

    @Test
    void review_reachedAfterLastVisible() {
        SurveyFlow flow = flow(single("q1", null), single("q2", "answer(q1) == \"a\""));
        flow.answer(choice("b"));
        assertTrue(flow.current().isEmpty());
        assertTrue(flow.back());
        assertEquals("q1", currentKey(flow));
    }

    @Test
    void permissionCondition_usesPredicate() {
        Question gated = new Question("q2", QuestionType.TEXT, "Say", List.of(), false, 0, 0,
                "permission(\"vip\")");
        SurveyFlow without = flow(node -> false, single("q1", null), gated);
        without.answer(choice("a"));
        assertTrue(without.current().isEmpty());

        SurveyFlow with = flow(node -> node.equals("vip"), single("q1", null), gated);
        with.answer(choice("a"));
        assertEquals("q2", currentKey(with));
    }

    @Test
    void position_countsVisibleOnly() {
        SurveyFlow flow = flow(single("q1", null), single("q2", "answer(q1) == \"a\""), single("q3", null));
        assertEquals(1, flow.position());
        flow.answer(choice("b"));
        assertEquals(2, flow.position());
        assertEquals(2, flow.visibleCount());
    }

    @Test
    void step_advancesOnEveryMoveButNotOnRefusal() {
        SurveyFlow flow = flow(single("q1", null), text("q2", false));
        int start = flow.step();
        flow.answer(new Answer.Text("wrong type"));
        assertEquals(start, flow.step());
        flow.answer(choice("a"));
        assertEquals(start + 1, flow.step());
        flow.skip();
        assertEquals(start + 2, flow.step());
        flow.back();
        assertEquals(start + 3, flow.step());
    }
}
