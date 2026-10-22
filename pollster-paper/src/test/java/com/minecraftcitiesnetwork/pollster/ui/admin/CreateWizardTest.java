package com.minecraftcitiesnetwork.pollster.ui.admin;

import com.minecraftcitiesnetwork.pollster.api.model.SurveyDefinition;
import com.minecraftcitiesnetwork.pollster.api.model.SurveyStatus;
import com.minecraftcitiesnetwork.pollster.ui.Input;
import com.minecraftcitiesnetwork.pollster.ui.Screen;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static com.minecraftcitiesnetwork.pollster.ui.admin.AdminFixture.button;
import static com.minecraftcitiesnetwork.pollster.ui.admin.AdminFixture.plain;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CreateWizardTest {

    @TempDir
    Path dir;

    private static final CreateWizard.Draft EMPTY = new CreateWizard.Draft("", "", "", false, false, false);

    @Test
    void basics_rejectsBadSlug() {
        AdminFixture f = new AdminFixture(this.dir);
        AdminFixture.FakeContext ctx = f.context().text("id", "Bad Id!").text("name", "N").text("description", "D");
        button(CreateWizard.basics(f.ui, EMPTY, null), "Next").onClick().accept(ctx);
        Screen s = ctx.last();
        assertTrue(s.inputs().stream().anyMatch(i -> i.key().equals("id")), "back on basics");
        assertTrue(plain(s).contains("Survey id"), plain(s));
        assertEquals("Bad Id!", ((Input.TextInput) s.inputs().get(0)).initial());
    }

    @Test
    void basics_rejectsExistingId() {
        AdminFixture f = new AdminFixture(this.dir);
        f.survey("taken-id", SurveyStatus.DRAFT);
        AdminFixture.FakeContext ctx = f.context().text("id", "taken-id").text("name", "N").text("description", "D");
        button(CreateWizard.basics(f.ui, EMPTY, null), "Next").onClick().accept(ctx);
        assertTrue(plain(ctx.last()).contains("already exists"), plain(ctx.last()));
    }

    @Test
    void basics_validInputGoesToSettingsAndBackKeepsDraft() {
        AdminFixture f = new AdminFixture(this.dir);
        AdminFixture.FakeContext ctx = f.context().text("id", "new-poll").text("name", "N").text("description", "D");
        button(CreateWizard.basics(f.ui, EMPTY, null), "Next").onClick().accept(ctx);
        Screen settings = ctx.last();
        assertEquals(3, settings.inputs().size());
        ctx.bools.put("anonymous", true);
        ctx.bools.put("featured", false);
        ctx.bools.put("requires-permission", true);
        button(settings, "Back").onClick().accept(ctx);
        assertEquals("new-poll", ((Input.TextInput) ctx.last().inputs().get(0)).initial());
    }

    @Test
    void confirm_createsDraftFile() {
        AdminFixture f = new AdminFixture(this.dir);
        CreateWizard.Draft draft = new CreateWizard.Draft("new-poll", "New <b>Poll</b>", "About", true, false, true);
        AdminFixture.FakeContext ctx = f.context();
        button(CreateWizard.confirm(f.ui, draft), "Create").onClick().accept(ctx);
        SurveyDefinition s = f.store.get("new-poll").orElseThrow();
        assertEquals(SurveyStatus.DRAFT, s.status());
        assertEquals("New <b>Poll</b>", s.name());
        assertEquals("About", s.description());
        assertTrue(s.anonymous());
        assertFalse(s.featured());
        assertTrue(s.requiresPermission());
        assertTrue(s.questions().isEmpty());
        // create, not save: a save of a survey with no file is refused so stale dialogs cannot revive deletes.
        org.mockito.Mockito.verify(f.service).create(org.mockito.ArgumentMatchers.any());
        // The name is staff text: the hub renders its formatting rather than showing the tags.
        assertTrue(plain(ctx.last()).contains("Survey: New Poll"), plain(ctx.last()));
    }
}
