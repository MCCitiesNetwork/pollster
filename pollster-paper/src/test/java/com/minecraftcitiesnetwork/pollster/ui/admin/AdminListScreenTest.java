package com.minecraftcitiesnetwork.pollster.ui.admin;

import com.minecraftcitiesnetwork.pollster.SurveyService;
import com.minecraftcitiesnetwork.pollster.api.model.SurveyStatus;
import com.minecraftcitiesnetwork.pollster.ui.Screen;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mockito;

import java.nio.file.Path;
import java.util.Map;

import static com.minecraftcitiesnetwork.pollster.ui.admin.AdminFixture.plain;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AdminListScreenTest {

    @TempDir
    Path dir;

    @Test
    void list_labelsShowStatusAndCount() {
        AdminFixture f = new AdminFixture(this.dir);
        f.survey("alpha-poll", SurveyStatus.OPEN, AdminFixture.text("q", null));
        Screen s = AdminListScreen.create(f.ui, 0, Map.of("alpha-poll", 12));
        assertEquals("alpha-poll · Name of alpha-poll [OPEN] · 12 responses", plain(s.buttons().get(0).label()));
    }

    @Test
    void list_lockedShowsLocked() {
        AdminFixture f = new AdminFixture(this.dir);
        f.survey("alpha-poll", SurveyStatus.OPEN, AdminFixture.text("q", null));
        Mockito.when(f.service.lock("alpha-poll")).thenReturn(SurveyService.SurveyLock.CONTENT_CHANGED);
        Screen s = AdminListScreen.create(f.ui, 0, Map.of());
        assertTrue(plain(s.buttons().get(0).label()).contains("[LOCKED]"));
    }

    @Test
    void list_paginatesAtTen() {
        AdminFixture f = new AdminFixture(this.dir);
        for (int i = 0; i < 12; i++) {
            f.survey("survey-%02d".formatted(i), SurveyStatus.DRAFT);
        }
        Screen first = AdminListScreen.create(f.ui, 0, Map.of());
        long surveyButtons = first.buttons().stream().filter(b -> plain(b.label()).contains(" · ")).count();
        assertEquals(10, surveyButtons);
        assertTrue(AdminFixture.hasButton(first, "Next"));
        Screen second = AdminListScreen.create(f.ui, 1, Map.of());
        assertEquals(2, second.buttons().stream().filter(b -> plain(b.label()).contains(" · ")).count());
        assertTrue(plain(second.buttons().get(0).label()).startsWith("survey-10"));
        assertTrue(AdminFixture.hasButton(second, "Previous"));
    }

    @Test
    void list_hasCreateAndClose() {
        AdminFixture f = new AdminFixture(this.dir);
        Screen s = AdminListScreen.create(f.ui, 0, Map.of());
        assertTrue(AdminFixture.hasButton(s, "Create survey"));
        assertTrue(AdminFixture.hasButton(s, "Close"));
    }
}
