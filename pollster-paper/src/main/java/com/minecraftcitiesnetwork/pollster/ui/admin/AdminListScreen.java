package com.minecraftcitiesnetwork.pollster.ui.admin;

import com.minecraftcitiesnetwork.pollster.SurveyService;
import com.minecraftcitiesnetwork.pollster.api.model.SurveyDefinition;
import com.minecraftcitiesnetwork.pollster.api.model.SurveyStatus;
import com.minecraftcitiesnetwork.pollster.ui.Button;
import com.minecraftcitiesnetwork.pollster.ui.Screen;
import com.minecraftcitiesnetwork.pollster.ui.ScreenContext;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Map;

import static com.minecraftcitiesnetwork.pollster.config.MessageKeys.*;
import static com.minecraftcitiesnetwork.pollster.ui.admin.AdminSupport.t;
import static com.minecraftcitiesnetwork.pollster.ui.admin.AdminSupport.v;

/** The paginated list of surveys: the editor's entry screen. */
public final class AdminListScreen {

    public static final int PAGE_SIZE = 10;

    private AdminListScreen() {
    }

    /** Shows the loading notice, fetches response counts, then opens the first page. */
    public static void open(@NotNull AdminUi ui, @NotNull ScreenContext ctx) {
        open(ui, ctx, 0);
    }

    public static void open(@NotNull AdminUi ui, @NotNull ScreenContext ctx, int page) {
        ctx.open(AdminSupport.loading(ui));
        ui.service().responseCounts().whenComplete((counts, error) -> {
            if (error != null) {
                ctx.open(AdminSupport.failure(ui, error, ScreenContext::close));
                return;
            }
            ctx.open(create(ui, page, counts));
        });
    }

    public static @NotNull Screen create(@NotNull AdminUi ui, int page, @NotNull Map<String, Integer> counts) {
        List<SurveyDefinition> all = ui.service().surveys();
        int pages = Math.max(1, (all.size() + PAGE_SIZE - 1) / PAGE_SIZE);
        int current = Math.max(0, Math.min(page, pages - 1));
        Screen.Builder builder = Screen.builder(ui.style().title(t(ui, EDITOR_LIST_TITLE)))
                .body(Component.text(t(ui, EDITOR_LIST_SUMMARY, v("count", all.size()), v("page", current + 1),
                        v("pages", pages)), NamedTextColor.GRAY));

        int from = current * PAGE_SIZE;
        for (SurveyDefinition survey : all.subList(from, Math.min(all.size(), from + PAGE_SIZE))) {
            builder.button(AdminSupport.wide(ui.style().edit(label(ui, survey, counts)), ctx -> {
                // Read the survey again: the file may have changed since this list was drawn.
                ui.service().survey(survey.id()).ifPresentOrElse(
                        fresh -> ctx.open(HubScreen.create(ui, fresh, ui.service().lock(fresh.id()),
                                counts.getOrDefault(fresh.id(), 0))),
                        () -> ctx.open(AdminSupport.unavailable(ui, c -> open(ui, c, current))));
            }));
        }

        builder.button(AdminSupport.wide(ui.style().primary(t(ui, EDITOR_LIST_CREATE)),
                ctx -> ctx.open(CreateWizard.basics(ui, CreateWizard.Draft.EMPTY, null))));
        if (current > 0) {
            builder.button(AdminSupport.wide(ui.style().secondary(t(ui, EDITOR_LIST_PREVIOUS)),
                    ctx -> ctx.open(create(ui, current - 1, counts))));
        }
        if (current < pages - 1) {
            builder.button(AdminSupport.wide(ui.style().secondary(t(ui, EDITOR_LIST_NEXT)),
                    ctx -> ctx.open(create(ui, current + 1, counts))));
        }
        builder.button(AdminSupport.wide(ui.style().secondary(t(ui, EDITOR_COMMON_CLOSE)), ScreenContext::close));
        return builder.build();
    }

    private static String label(AdminUi ui, SurveyDefinition survey, Map<String, Integer> counts) {
        boolean locked = survey.status() != SurveyStatus.DRAFT
                && ui.service().lock(survey.id()) != SurveyService.SurveyLock.OK;
        return t(ui, locked ? EDITOR_LIST_ENTRY_LOCKED : EDITOR_LIST_ENTRY, v("id", survey.id()),
                v("name", AdminSupport.truncate(survey.name(), 30)), v("status", survey.status()),
                v("responses", counts.getOrDefault(survey.id(), 0)));
    }

    static @NotNull Button back(@NotNull AdminUi ui) {
        return Button.of(ui.style().secondary(t(ui, EDITOR_COMMON_BACK)), ctx -> open(ui, ctx));
    }
}
