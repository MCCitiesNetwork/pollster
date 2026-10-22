package com.minecraftcitiesnetwork.pollster.ui.admin;

import com.minecraftcitiesnetwork.pollster.api.model.Question;
import com.minecraftcitiesnetwork.pollster.api.model.QuestionType;
import com.minecraftcitiesnetwork.pollster.api.model.SurveyDefinition;
import com.minecraftcitiesnetwork.pollster.api.model.SurveyStatus;
import com.minecraftcitiesnetwork.pollster.ui.Screen;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static com.minecraftcitiesnetwork.pollster.ui.admin.AdminFixture.button;
import static com.minecraftcitiesnetwork.pollster.ui.admin.AdminFixture.hasButton;
import static com.minecraftcitiesnetwork.pollster.ui.admin.AdminFixture.plain;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class QuestionEditScreenTest {

    @TempDir
    Path dir;

    private SurveyDefinition draft(AdminFixture f) {
        return f.survey("poll-one", SurveyStatus.DRAFT, AdminFixture.single("pick", "a", "b"),
                AdminFixture.text("why", "answer(pick) == \"a\""));
    }

    private static AdminFixture.FakeContext form(AdminFixture f, Question q) {
        AdminFixture.FakeContext ctx = f.context().text("key", q.key()).text("text", q.text())
                .text("type", q.type().name()).text("condition", q.showIf() == null ? "" : q.showIf());
        ctx.bools.put("required", q.required());
        return ctx;
    }

    @Test
    void edit_conditionSyntaxErrorShowsColumnInline() {
        AdminFixture f = new AdminFixture(this.dir);
        SurveyDefinition s = draft(f);
        Question q = s.question("why").orElseThrow();
        AdminFixture.FakeContext ctx = form(f, q).text("condition", "answer(pick) ==");
        button(QuestionEditScreen.create(f.ui, s, q, null), "Save").onClick().accept(ctx);
        assertTrue(plain(ctx.last()).contains("column"), plain(ctx.last()));
        // Nothing was written.
        assertEquals("answer(pick) == \"a\"", f.store.get("poll-one").orElseThrow().question("why").orElseThrow().showIf());
    }

    @Test
    void edit_saveWritesChangedText() {
        AdminFixture f = new AdminFixture(this.dir);
        SurveyDefinition s = draft(f);
        Question q = s.question("why").orElseThrow();
        AdminFixture.FakeContext ctx = form(f, q).text("text", "<b>Why so?</b>");
        button(QuestionEditScreen.create(f.ui, s, q, null), "Save").onClick().accept(ctx);
        assertEquals("<b>Why so?</b>", f.store.get("poll-one").orElseThrow().question("why").orElseThrow().text());
    }

    @Test
    void edit_changingToTextClearsOptions() {
        AdminFixture f = new AdminFixture(this.dir);
        SurveyDefinition s = f.survey("poll-one", SurveyStatus.DRAFT, AdminFixture.single("pick", "a", "b"));
        Question q = s.question("pick").orElseThrow();
        AdminFixture.FakeContext ctx = form(f, q).text("type", "TEXT");
        button(QuestionEditScreen.create(f.ui, s, q, null), "Save").onClick().accept(ctx);
        Question saved = f.store.get("poll-one").orElseThrow().question("pick").orElseThrow();
        assertEquals(QuestionType.TEXT, saved.type());
        assertTrue(saved.options().isEmpty());
    }

    @Test
    void edit_changingToChoiceWithoutOptionsOpensOptionList() {
        AdminFixture f = new AdminFixture(this.dir);
        SurveyDefinition s = f.survey("poll-one", SurveyStatus.DRAFT, AdminFixture.text("free", null));
        Question q = s.question("free").orElseThrow();
        AdminFixture.FakeContext ctx = form(f, q).text("type", "SINGLE");
        button(QuestionEditScreen.create(f.ui, s, q, null), "Save").onClick().accept(ctx);
        assertTrue(hasButton(ctx.last(), "Bulk add"), plain(ctx.last()));
        assertEquals(QuestionType.TEXT, f.store.get("poll-one").orElseThrow().question("free").orElseThrow().type());
    }

    @Test
    void edit_deleteReferencedQuestionShowsReason() {
        AdminFixture f = new AdminFixture(this.dir);
        SurveyDefinition s = draft(f);
        Question q = s.question("pick").orElseThrow();
        AdminFixture.FakeContext ctx = f.context();
        button(QuestionEditScreen.create(f.ui, s, q, null), "Delete question").onClick().accept(ctx);
        button(ctx.last(), "Confirm").onClick().accept(ctx);
        assertTrue(plain(ctx.last()).contains("Referenced by why"), plain(ctx.last()));
        assertTrue(f.store.get("poll-one").orElseThrow().question("pick").isPresent());
    }

    @Test
    void edit_openSurveyIsReadOnly() {
        AdminFixture f = new AdminFixture(this.dir);
        SurveyDefinition s = f.survey("poll-one", SurveyStatus.OPEN, AdminFixture.single("pick", "a", "b"));
        Screen screen = QuestionEditScreen.create(f.ui, s, s.questions().get(0), null);
        assertTrue(screen.inputs().isEmpty());
        assertFalse(hasButton(screen, "Save"));
        assertTrue(hasButton(screen, "Back"));
    }

    @Test
    void list_marksConditionalQuestions() {
        AdminFixture f = new AdminFixture(this.dir);
        Screen s = QuestionListScreen.create(f.ui, draft(f));
        assertEquals("1. [SINGLE] Question pick", plain(s.buttons().get(0).label()));
        assertEquals("2. [TEXT] Text why ⓘ", plain(s.buttons().get(1).label()));
    }

    @Test
    void list_draftHasAddQuestion() {
        AdminFixture f = new AdminFixture(this.dir);
        Screen s = QuestionListScreen.create(f.ui, draft(f));
        assertTrue(hasButton(s, "Add question"));
    }

    @Test
    void list_openSurveyButtonsAreReadOnly() {
        AdminFixture f = new AdminFixture(this.dir);
        SurveyDefinition open = f.survey("poll-one", SurveyStatus.OPEN, AdminFixture.single("pick", "a", "b"));
        Screen list = QuestionListScreen.create(f.ui, open);
        assertFalse(hasButton(list, "Add question"));
        AdminFixture.FakeContext ctx = f.context();
        button(list, "1. [SINGLE]").onClick().accept(ctx);
        Screen view = ctx.last();
        assertTrue(view.inputs().isEmpty());
        assertFalse(hasButton(view, "Save"));
        assertFalse(hasButton(view, "Delete"));
    }

    @Test
    void options_bulkAddKeepsPendingUntilSaved() {
        AdminFixture f = new AdminFixture(this.dir);
        SurveyDefinition s = f.survey("poll-one", SurveyStatus.DRAFT, AdminFixture.text("free", null));
        Question pending = new Question("free", QuestionType.SINGLE, "Pick", java.util.List.of(), true, 0, 0, null);
        AdminFixture.FakeContext ctx = f.context();
        button(OptionListScreen.create(f.ui, s, pending), "Bulk add").onClick().accept(ctx);
        ctx.text("options", "x: Ex\nWhy");
        button(ctx.last(), "Add").onClick().accept(ctx);
        Screen list = ctx.last();
        assertTrue(plain(list).contains("Ex") || hasButton(list, "x: Ex"), plain(list));
        button(list, "Save question").onClick().accept(ctx);
        Question saved = f.store.get("poll-one").orElseThrow().question("free").orElseThrow();
        assertEquals(QuestionType.SINGLE, saved.type());
        assertEquals(java.util.List.of("x", "why"), saved.options().stream().map(o -> o.key()).toList());
    }
}
