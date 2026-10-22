package com.minecraftcitiesnetwork.pollster.ui.admin;

import com.minecraftcitiesnetwork.pollster.SurveyService;
import com.minecraftcitiesnetwork.pollster.api.model.SurveyDefinition;
import com.minecraftcitiesnetwork.pollster.api.model.SurveyStatus;
import com.minecraftcitiesnetwork.pollster.ui.Screen;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static com.minecraftcitiesnetwork.pollster.ui.admin.AdminFixture.button;
import static com.minecraftcitiesnetwork.pollster.ui.admin.AdminFixture.hasButton;
import static com.minecraftcitiesnetwork.pollster.ui.admin.AdminFixture.plain;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HubScreenTest {

    @TempDir
    Path dir;

    private Screen hub(AdminFixture f, SurveyDefinition s, int responses) {
        return HubScreen.create(f.ui, s, SurveyService.SurveyLock.OK, responses);
    }

    private SurveyDefinition valid(AdminFixture f, String id, SurveyStatus status) {
        return f.survey(id, status, AdminFixture.single("pick", "a", "b"));
    }

    @Test
    void hub_draftShowsOpenAction() {
        AdminFixture f = new AdminFixture(this.dir);
        Screen s = hub(f, valid(f, "poll-one", SurveyStatus.DRAFT), 0);
        assertTrue(hasButton(s, "Open survey"));
        assertFalse(hasButton(s, "Close survey"));
    }

    @Test
    void hub_openShowsCloseAction() {
        AdminFixture f = new AdminFixture(this.dir);
        Screen s = hub(f, valid(f, "poll-one", SurveyStatus.OPEN), 0);
        assertTrue(hasButton(s, "Close survey"));
    }

    @Test
    void hub_closedShowsReopen() {
        AdminFixture f = new AdminFixture(this.dir);
        Screen s = hub(f, valid(f, "poll-one", SurveyStatus.CLOSED), 0);
        assertTrue(hasButton(s, "Reopen"));
    }

    @Test
    void hub_bodyListsValues() {
        AdminFixture f = new AdminFixture(this.dir);
        Screen s = hub(f, valid(f, "poll-one", SurveyStatus.DRAFT), 3);
        String body = plain(s);
        assertTrue(body.contains("Status: DRAFT"), body);
        assertTrue(body.contains("Responses: 3"), body);
        assertTrue(body.contains("Questions: 1"), body);
    }

    @Test
    void hub_openConfirmWarnsQuestionsLock() {
        AdminFixture f = new AdminFixture(this.dir);
        AdminFixture.FakeContext ctx = f.context();
        button(hub(f, valid(f, "poll-one", SurveyStatus.DRAFT), 0), "Open survey").onClick().accept(ctx);
        assertTrue(plain(ctx.last()).contains("Opening locks the questions."), plain(ctx.last()));
        button(ctx.last(), "Confirm").onClick().accept(ctx);
        assertEquals(SurveyStatus.OPEN, f.store.get("poll-one").orElseThrow().status());
    }

    @Test
    void hub_openingInvalidSurveyShowsProblems() {
        AdminFixture f = new AdminFixture(this.dir);
        // A choice question with a single option fails validation.
        SurveyDefinition good = f.survey("poll-bad", SurveyStatus.DRAFT, AdminFixture.single("pick", "a", "b"));
        SurveyDefinition broken = good.withQuestions(List.of(AdminFixture.single("pick", "a")));
        // A file edited by hand can hold what the editor would refuse; the service hands it back as is.
        org.mockito.Mockito.when(f.service.survey("poll-bad")).thenReturn(java.util.Optional.of(broken));
        AdminFixture.FakeContext ctx = f.context();
        button(hub(f, broken, 0), "Open survey").onClick().accept(ctx);
        assertTrue(plain(ctx.last()).contains("options"), plain(ctx.last()));
        assertEquals(SurveyStatus.DRAFT, f.store.get("poll-bad").orElseThrow().status());
    }

    @Test
    void hub_openingEmptySurveyShowsProblem() {
        AdminFixture f = new AdminFixture(this.dir);
        AdminFixture.FakeContext ctx = f.context();
        button(hub(f, f.survey("poll-empty", SurveyStatus.DRAFT), 0), "Open survey").onClick().accept(ctx);
        assertTrue(plain(ctx.last()).contains("at least one question"), plain(ctx.last()));
    }

    @Test
    void hub_anonymousToggleOnlyInDraft() {
        AdminFixture f = new AdminFixture(this.dir);
        AdminFixture.FakeContext ctx = f.context();
        button(hub(f, valid(f, "poll-open", SurveyStatus.OPEN), 0), "Anonymous").onClick().accept(ctx);
        assertTrue(plain(ctx.last()).contains("Anonymous is draft only."), plain(ctx.last()));
        assertFalse(f.store.get("poll-open").orElseThrow().anonymous());

        AdminFixture.FakeContext draftCtx = f.context();
        button(hub(f, valid(f, "poll-draft", SurveyStatus.DRAFT), 0), "Anonymous").onClick().accept(draftCtx);
        assertTrue(f.store.get("poll-draft").orElseThrow().anonymous());
    }

    @Test
    void hub_featuredToggleWorksWhileOpen() {
        AdminFixture f = new AdminFixture(this.dir);
        AdminFixture.FakeContext ctx = f.context();
        button(hub(f, valid(f, "poll-open", SurveyStatus.OPEN), 0), "Featured").onClick().accept(ctx);
        assertTrue(f.store.get("poll-open").orElseThrow().featured());
    }

    @Test
    void hub_nameEditSavesLiteralText() {
        AdminFixture f = new AdminFixture(this.dir);
        AdminFixture.FakeContext ctx = f.context();
        button(hub(f, valid(f, "poll-one", SurveyStatus.DRAFT), 0), "Name").onClick().accept(ctx);
        ctx.text("value", "<red>Hot");
        button(ctx.last(), "Save").onClick().accept(ctx);
        assertEquals("<red>Hot", f.store.get("poll-one").orElseThrow().name());
    }

    @Test
    void hub_deleteWithResponsesNeedsTwoConfirms() {
        AdminFixture f = new AdminFixture(this.dir);
        AdminFixture.FakeContext ctx = f.context();
        button(hub(f, valid(f, "poll-one", SurveyStatus.OPEN), 5), "Delete").onClick().accept(ctx);
        button(ctx.last(), "Confirm").onClick().accept(ctx);
        assertTrue(f.store.exists("poll-one"), "still there after one confirm");
        button(ctx.last(), "Confirm").onClick().accept(ctx);
        assertFalse(f.store.exists("poll-one"));
    }

    @Test
    void hub_deleteDraftNeedsOne() {
        AdminFixture f = new AdminFixture(this.dir);
        AdminFixture.FakeContext ctx = f.context();
        button(hub(f, valid(f, "poll-one", SurveyStatus.DRAFT), 0), "Delete").onClick().accept(ctx);
        button(ctx.last(), "Confirm").onClick().accept(ctx);
        assertFalse(f.store.exists("poll-one"));
    }

    @Test
    void hub_duplicateAsksForIdAndCreatesDraft() {
        AdminFixture f = new AdminFixture(this.dir);
        AdminFixture.FakeContext ctx = f.context();
        button(hub(f, valid(f, "poll-open", SurveyStatus.OPEN), 0), "Duplicate").onClick().accept(ctx);
        assertTrue(ctx.last().inputs().stream().anyMatch(i -> i.key().equals("id")));
        ctx.text("id", "poll-copy");
        button(ctx.last(), "Create").onClick().accept(ctx);
        SurveyDefinition copy = f.store.get("poll-copy").orElseThrow();
        assertEquals(SurveyStatus.DRAFT, copy.status());
        assertEquals(1, copy.questions().size());
    }

    @Test
    void hub_duplicateRejectsExistingId() {
        AdminFixture f = new AdminFixture(this.dir);
        AdminFixture.FakeContext ctx = f.context();
        button(hub(f, valid(f, "poll-open", SurveyStatus.OPEN), 0), "Duplicate").onClick().accept(ctx);
        ctx.text("id", "poll-open");
        button(ctx.last(), "Create").onClick().accept(ctx);
        assertTrue(plain(ctx.last()).contains("already exists"), plain(ctx.last()));
    }

    @Test
    void hub_closingEndsActiveSessions() {
        AdminFixture f = new AdminFixture(this.dir);
        AdminFixture.FakeContext ctx = f.context();
        button(hub(f, valid(f, "poll-open", SurveyStatus.OPEN), 0), "Close survey").onClick().accept(ctx);
        assertEquals(List.of("poll-open"), f.endedSessions);
        assertEquals(SurveyStatus.CLOSED, f.store.get("poll-open").orElseThrow().status());
    }

    @Test
    void hub_previewAndReportDelegate() {
        AdminFixture f = new AdminFixture(this.dir);
        AdminFixture.FakeContext ctx = f.context();
        org.mockito.Mockito.when(f.player.hasPermission("pollster.admin.report")).thenReturn(true);
        Screen s = hub(f, valid(f, "poll-one", SurveyStatus.DRAFT), 0);
        button(s, "Preview").onClick().accept(ctx);
        button(s, "Report").onClick().accept(ctx);
        assertEquals(List.of("poll-one"), f.previews);
        assertEquals(List.of("poll-one"), f.reports);
    }

    @Test
    void hub_reportNeedsReportPermission() {
        AdminFixture f = new AdminFixture(this.dir);
        f.messages.setMessage("errors.no-permission", "You may not see reports.");
        AdminFixture.FakeContext ctx = f.context();
        Screen s = hub(f, valid(f, "poll-one", SurveyStatus.DRAFT), 0);
        button(s, "Report").onClick().accept(ctx);
        assertTrue(f.reports.isEmpty());
        assertTrue(plain(ctx.last()).contains("You may not see reports."), plain(ctx.last()));
    }
}
