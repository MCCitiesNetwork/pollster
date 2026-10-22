package com.minecraftcitiesnetwork.pollster.api.hash;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.minecraftcitiesnetwork.pollster.api.model.Option;
import com.minecraftcitiesnetwork.pollster.api.model.Question;
import com.minecraftcitiesnetwork.pollster.api.model.QuestionType;
import com.minecraftcitiesnetwork.pollster.api.model.Reward;
import com.minecraftcitiesnetwork.pollster.api.model.SurveyDefinition;
import com.minecraftcitiesnetwork.pollster.api.model.SurveyStatus;
import java.util.ArrayList;
import java.util.List;
import java.util.function.UnaryOperator;
import org.junit.jupiter.api.Test;

class ContentHashTest {

    private static final List<Option> OPTIONS = List.of(new Option("a", "Alpha"), new Option("b", "Beta"));

    private static Question q1() {
        return new Question("q1", QuestionType.MULTIPLE, "Pick", OPTIONS, true, 1, 2, null);
    }

    private static Question q2(String showIf) {
        return new Question("q2", QuestionType.TEXT, "Why?", List.of(), false, 0, 0, showIf);
    }

    private static SurveyDefinition base(Question... questions) {
        return new SurveyDefinition("town-vote", "Name", "Desc", SurveyStatus.DRAFT, false, false, false,
                List.of(), List.of(questions));
    }

    private static SurveyDefinition base() {
        return base(q1(), q2("answer(q1) has \"a\""));
    }

    private static String hashWithQuestion(int index, UnaryOperator<Question> change) {
        SurveyDefinition s = base();
        List<Question> questions = new ArrayList<>(s.questions());
        questions.set(index, change.apply(questions.get(index)));
        return ContentHash.of(s.withQuestions(questions));
    }

    @Test
    void of_ignoresLockedOutFields() {
        SurveyDefinition s = base();
        SurveyDefinition changed = new SurveyDefinition("town-vote", "Other", "Other desc", SurveyStatus.OPEN, false,
                true, true, List.of(new Reward.MessageReward("hi")), s.questions());
        assertEquals(ContentHash.of(s), ContentHash.of(changed));
    }

    @Test
    void of_returns64LowercaseHexChars() {
        assertTrue(ContentHash.of(base()).matches("[0-9a-f]{64}"));
    }

    @Test
    void of_changesWithQuestionText() {
        assertNotEquals(ContentHash.of(base()), hashWithQuestion(0,
                q -> new Question(q.key(), q.type(), "Pick!", q.options(), q.required(), q.min(), q.max(), q.showIf())));
    }

    @Test
    void of_changesWithOptionOrder() {
        assertNotEquals(ContentHash.of(base()), hashWithQuestion(0,
                q -> new Question(q.key(), q.type(), q.text(), List.of(OPTIONS.get(1), OPTIONS.get(0)), q.required(),
                        q.min(), q.max(), q.showIf())));
    }

    @Test
    void of_changesWithShowIf() {
        assertNotEquals(ContentHash.of(base()), hashWithQuestion(1,
                q -> new Question(q.key(), q.type(), q.text(), q.options(), q.required(), q.min(), q.max(),
                        "answer(q1) has \"b\"")));
    }

    @Test
    void of_changesWithRequired() {
        assertNotEquals(ContentHash.of(base()), hashWithQuestion(0,
                q -> new Question(q.key(), q.type(), q.text(), q.options(), false, q.min(), q.max(), q.showIf())));
    }

    @Test
    void of_changesWithMinMax() {
        assertNotEquals(ContentHash.of(base()), hashWithQuestion(0,
                q -> new Question(q.key(), q.type(), q.text(), q.options(), q.required(), 2, q.max(), q.showIf())));
        assertNotEquals(ContentHash.of(base()), hashWithQuestion(0,
                q -> new Question(q.key(), q.type(), q.text(), q.options(), q.required(), q.min(), 1, q.showIf())));
    }

    @Test
    void of_changesWithAnonymous() {
        SurveyDefinition s = base();
        SurveyDefinition anonymous = new SurveyDefinition(s.id(), s.name(), s.description(), s.status(), true,
                s.featured(), s.requiresPermission(), s.rewards(), s.questions());
        assertNotEquals(ContentHash.of(s), ContentHash.of(anonymous));
    }

    @Test
    void of_treatsWhitespaceInShowIfAsSame() {
        SurveyDefinition tight = base(q1(), q2("answer(q1) has \"a\""));
        SurveyDefinition loose = base(q1(), q2("answer( q1 )   has   \"a\""));
        assertEquals(ContentHash.of(tight), ContentHash.of(loose));
    }

    @Test
    void of_isStable() {
        assertEquals("6dda72673e2daf83923e42ccb6ffdc6a9412a95f09a9bec46b752dc600a6a9f8", ContentHash.of(base()));
    }
}
