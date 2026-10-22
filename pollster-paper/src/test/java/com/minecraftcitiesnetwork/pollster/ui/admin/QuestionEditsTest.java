package com.minecraftcitiesnetwork.pollster.ui.admin;

import com.minecraftcitiesnetwork.pollster.api.model.Option;
import com.minecraftcitiesnetwork.pollster.api.model.Question;
import com.minecraftcitiesnetwork.pollster.api.model.SurveyDefinition;
import com.minecraftcitiesnetwork.pollster.api.model.SurveyStatus;
import com.minecraftcitiesnetwork.pollster.ui.admin.QuestionEdits.EditResult;
import com.minecraftcitiesnetwork.pollster.ui.admin.QuestionEdits.Ok;
import com.minecraftcitiesnetwork.pollster.ui.admin.QuestionEdits.Rejected;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

class QuestionEditsTest {

    private static SurveyDefinition survey(SurveyStatus status, Question... questions) {
        return new SurveyDefinition("poll-one", "Poll", "", status, false, false, false, List.of(),
                List.of(questions));
    }

    private static Question pick() {
        return AdminFixture.single("pick", "a", "b");
    }

    private static Question followUp() {
        return AdminFixture.text("why", "answer(pick) == \"a\"");
    }

    private static List<String> keys(EditResult r) {
        return ((Ok) r).s().questions().stream().map(Question::key).toList();
    }

    @Test
    void add_appendsToDraft() {
        EditResult r = QuestionEdits.add(survey(SurveyStatus.DRAFT, pick()), AdminFixture.text("extra", null));
        assertEquals(List.of("pick", "extra"), keys(r));
    }

    @Test
    void add_duplicateKeyIsRejected() {
        EditResult r = QuestionEdits.add(survey(SurveyStatus.DRAFT, pick()), pick());
        assertEquals("editor.duplicate-key", assertInstanceOf(Rejected.class, r).messageKey());
    }

    @Test
    void move_swapsWithNeighbour() {
        EditResult r = QuestionEdits.move(survey(SurveyStatus.DRAFT, pick(), AdminFixture.text("free", null)),
                "free", -1);
        assertEquals(List.of("free", "pick"), keys(r));
    }

    @Test
    void move_blockedWhenConditionWouldPrecedeReference() {
        EditResult r = QuestionEdits.move(survey(SurveyStatus.DRAFT, pick(), followUp()), "why", -1);
        Rejected rejected = assertInstanceOf(Rejected.class, r);
        assertEquals("editor.move-breaks-condition", rejected.messageKey());
        assertTrue(rejected.args().containsAll(List.of("why", "pick")), rejected.args().toString());
    }

    @Test
    void move_blockedWhenReferencedQuestionWouldFollow() {
        EditResult r = QuestionEdits.move(survey(SurveyStatus.DRAFT, pick(), followUp()), "pick", 1);
        assertEquals("editor.move-breaks-condition", assertInstanceOf(Rejected.class, r).messageKey());
    }

    @Test
    void remove_blockedWhenReferenced() {
        EditResult r = QuestionEdits.remove(survey(SurveyStatus.DRAFT, pick(), followUp()), "pick");
        Rejected rejected = assertInstanceOf(Rejected.class, r);
        assertEquals("editor.remove-referenced", rejected.messageKey());
        assertEquals(List.of("why"), rejected.args());
    }

    @Test
    void remove_dropsUnreferencedQuestion() {
        EditResult r = QuestionEdits.remove(survey(SurveyStatus.DRAFT, pick(), followUp()), "why");
        assertEquals(List.of("pick"), keys(r));
    }

    @Test
    void replace_renamingReferencedKeyIsRejected() {
        Question renamed = new Question("choice", pick().type(), pick().text(), pick().options(), true, 0, 0, null);
        EditResult r = QuestionEdits.replace(survey(SurveyStatus.DRAFT, pick(), followUp()), "pick", renamed);
        Rejected rejected = assertInstanceOf(Rejected.class, r);
        assertEquals("editor.key-referenced", rejected.messageKey());
        assertEquals(List.of("why"), rejected.args());
    }

    @Test
    void replace_keepingKeyUpdatesInPlace() {
        Question edited = new Question("pick", pick().type(), "New text", pick().options(), true, 0, 0, null);
        EditResult r = QuestionEdits.replace(survey(SurveyStatus.DRAFT, pick(), followUp()), "pick", edited);
        assertEquals("New text", ((Ok) r).s().questions().get(0).text());
    }

    @Test
    void bulkParse_mixesKeyedAndPlain() {
        List<Option> options = QuestionEdits.bulkParse("ger: German Party\nIndependent\n\nIndependent\n");
        assertEquals(List.of("ger", "independent", "independent-2"), options.stream().map(Option::key).toList());
        assertEquals("German Party", options.get(0).label());
        assertEquals("Independent", options.get(2).label());
    }

    @Test
    void bulkParse_colonInsideLabelIsNotAKey() {
        List<Option> options = QuestionEdits.bulkParse("Yes: with caveats here");
        assertEquals("yes-with-caveats-here", options.get(0).key());
        assertEquals("Yes: with caveats here", options.get(0).label());
        List<Option> whole = QuestionEdits.bulkParse("Note to self: do it");
        assertEquals("Note to self: do it", whole.get(0).label());
    }

    @Test
    void anyEdit_onOpenSurvey_rejectedLocked() {
        SurveyDefinition open = survey(SurveyStatus.OPEN, pick(), followUp());
        assertEquals("editor.locked", assertInstanceOf(Rejected.class,
                QuestionEdits.add(open, AdminFixture.text("x", null))).messageKey());
        assertEquals("editor.locked", assertInstanceOf(Rejected.class,
                QuestionEdits.replace(open, "pick", pick())).messageKey());
        assertEquals("editor.locked", assertInstanceOf(Rejected.class,
                QuestionEdits.move(open, "why", -1)).messageKey());
        assertEquals("editor.locked", assertInstanceOf(Rejected.class,
                QuestionEdits.remove(open, "why")).messageKey());
    }
}
